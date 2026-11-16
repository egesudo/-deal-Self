package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.ai.PersonalityEngine
import com.example.data.model.AudioRoutePreference
import com.example.data.model.VoicePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "VoiceSessionCtrl"

enum class VoicePhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    PAUSED,
    ERROR
}

data class VoiceSessionUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val providerId: String = "gemini_streaming_native_tr",
    val voiceLanguageTag: String = "tr-TR",
    val liveTranscript: String = "",
    val hasPendingDraftTranscript: Boolean = false,
    val companionCaption: String = "",
    val rmsDbLevel: Float = 0f,
    val activeAudioRoute: AudioRoutePreference = AudioRoutePreference.SPEAKER,
    val availableRoutes: List<AudioRoutePreference> = listOf(
        AudioRoutePreference.SPEAKER,
        AudioRoutePreference.EARPIECE,
        AudioRoutePreference.BLUETOOTH
    ),
    val bluetoothConnected: Boolean = false,
    val handsFreeEnabled: Boolean = true,
    val isMuted: Boolean = false,
    val micPermissionGranted: Boolean = true,
    val isNetworkAvailable: Boolean = true,
    val speechRecognitionAvailable: Boolean = true,
    val firstTokenLatencyMs: Long = 0L,
    val firstAudioLatencyMs: Long = 0L,
    val interruptionCount: Int = 0,
    val statusMessage: String = "Sesli görüşmeye hazır • Ready for voice call"
)

class VoiceSessionController(
    context: Context,
    private val onInterruptActiveGeneration: () -> Unit = {},
    private val onUserUtteranceFinalized: (String) -> Unit
) : RealTimeVoiceProvider {

    override val providerId: String = "gemini_streaming_native_tr"

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentPrefs = VoicePreferences()
    private var sessionActive = false
    private var wasPausedByTransientFocusLoss = false
    private var lastCapturedPartialText: String = ""
    private var busyRetryCount = 0

    private val sentenceChunker = SentenceStreamChunker()
    private val pendingUtteranceCount = AtomicInteger(0)
    private var turnStartRealtimeMs: Long = 0L
    private var hasRecordedFirstAudioThisTurn = false

    private val _state = MutableStateFlow(VoiceSessionUiState())
    override val state: StateFlow<VoiceSessionUiState> = _state.asStateFlow()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            mainHandler.post {
                val hadBt = _state.value.bluetoothConnected
                refreshAudioDevices()
                if (!hadBt && _state.value.bluetoothConnected && sessionActive) {
                    selectAudioRoute(AudioRoutePreference.BLUETOOTH)
                    _state.value = _state.value.copy(
                        statusMessage = "Bluetooth kulaklık bağlandı • Bluetooth audio routed"
                    )
                }
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            mainHandler.post {
                val hadBt = _state.value.bluetoothConnected
                refreshAudioDevices()
                if (hadBt && !_state.value.bluetoothConnected && sessionActive) {
                    selectAudioRoute(AudioRoutePreference.SPEAKER)
                    _state.value = _state.value.copy(
                        statusMessage = "Bluetooth ayrıldı, hoparlöre geçildi • Switched to Speaker"
                    )
                }
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post {
                val wasOffline = !_state.value.isNetworkAvailable
                _state.value = _state.value.copy(isNetworkAvailable = true)
                if (wasOffline && sessionActive) {
                    _state.value = _state.value.copy(
                        phase = VoicePhase.IDLE,
                        statusMessage = "Bağlantı yenilendi • Network recovered"
                    )
                    if (_state.value.handsFreeEnabled &&
                        !_state.value.isMuted &&
                        _state.value.micPermissionGranted &&
                        !_state.value.hasPendingDraftTranscript
                    ) {
                        startListeningInternal()
                    }
                }
            }
        }

        override fun onLost(network: Network) {
            mainHandler.post {
                val stillConnected = checkActiveNetworkConnected()
                _state.value = _state.value.copy(isNetworkAvailable = stillConnected)
                if (!stillConnected && sessionActive) {
                    stopListeningInternal(destroyInstance = false)
                    tts?.stop()
                    val preserved = _state.value.liveTranscript.trim().ifEmpty { lastCapturedPartialText.trim() }
                    _state.value = _state.value.copy(
                        phase = VoicePhase.ERROR,
                        liveTranscript = preserved,
                        hasPendingDraftTranscript = preserved.isNotEmpty(),
                        rmsDbLevel = 0f,
                        statusMessage = "Ağ bağlantısı zayıf veya koptu — bekleniyor • Network lost, waiting to reconnect"
                    )
                }
            }
        }
    }

    init {
        initializeTts()
        refreshAudioDevices()
        registerDeviceAndNetworkCallbacks()
    }

    private fun registerDeviceAndNetworkCallbacks() {
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "AudioDeviceCallback registration skipped: ${e.message}")
        }
        try {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            _state.value = _state.value.copy(isNetworkAvailable = checkActiveNetworkConnected())
        } catch (e: Exception) {
            Log.w(TAG, "NetworkCallback registration skipped: ${e.message}")
        }
    }

    private fun checkActiveNetworkConnected(): Boolean {
        val cm = connectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun initializeTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                configureTtsLocaleAndVoice(_state.value.voiceLanguageTag)
                applyVoicePreferences(currentPrefs)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            if (!hasRecordedFirstAudioThisTurn && turnStartRealtimeMs > 0L) {
                                hasRecordedFirstAudioThisTurn = true
                                val audioLat = (SystemClock.elapsedRealtime() - turnStartRealtimeMs).coerceAtLeast(1L)
                                _state.value = _state.value.copy(firstAudioLatencyMs = audioLat)
                            }
                            _state.value = _state.value.copy(
                                phase = VoicePhase.SPEAKING,
                                statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                                    "Konuşuyor... (Bölmek için dokun veya konuş)"
                                } else {
                                    "Companion is speaking... (Tap or speak to interrupt)"
                                }
                            )
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        val remaining = pendingUtteranceCount.decrementAndGet().coerceAtLeast(0)
                        pendingUtteranceCount.set(remaining)
                        if (remaining == 0) {
                            mainHandler.post {
                                onAllSpeechClausesFinished()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        val remaining = pendingUtteranceCount.decrementAndGet().coerceAtLeast(0)
                        pendingUtteranceCount.set(remaining)
                        if (remaining == 0) {
                            mainHandler.post {
                                onAllSpeechClausesFinished()
                            }
                        }
                    }
                })
            } else {
                Log.w(TAG, "TextToSpeech initialization returned status=$status")
            }
        }
    }

    private fun onAllSpeechClausesFinished() {
        if (!sessionActive) return
        releaseSpeechOutputAudioMode()
        if (_state.value.handsFreeEnabled && !_state.value.isMuted && _state.value.micPermissionGranted) {
            startListeningInternal()
        } else {
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                    "Yanıtlamak için mikrofona dokun"
                } else {
                    "Tap microphone or speak to reply"
                }
            )
        }
    }

    private fun configureTtsLocaleAndVoice(languageTag: String) {
        val engine = tts ?: return
        if (!isTtsReady) return
        try {
            val targetLocale = Locale.forLanguageTag(languageTag)
            val availability = engine.isLanguageAvailable(targetLocale)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = targetLocale
                val bestVoice: Voice? = engine.voices
                    ?.filter { it.locale.language.equals(targetLocale.language, ignoreCase = true) }
                    ?.maxByOrNull { v ->
                        var score = v.quality
                        if (!v.isNetworkConnectionRequired) score += 50
                        score
                    }
                if (bestVoice != null) {
                    engine.voice = bestVoice
                }
            } else {
                engine.language = Locale.US
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to configure TTS locale $languageTag: ${e.message}")
        }
    }

    override fun setVoiceLocale(languageTag: String) {
        val normalized = if (languageTag.startsWith("en", ignoreCase = true)) "en-US" else "tr-TR"
        _state.value = _state.value.copy(
            voiceLanguageTag = normalized,
            statusMessage = if (normalized == "tr-TR") {
                "Dil: Türkçe (tr-TR) aktif"
            } else {
                "Language: English (en-US) active"
            }
        )
        configureTtsLocaleAndVoice(normalized)
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        _state.value = _state.value.copy(micPermissionGranted = granted)
        if (!granted) {
            stopListeningInternal(destroyInstance = false)
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                rmsDbLevel = 0f,
                statusMessage = "Mikrofon izni reddedildi — sesli konuşmak için izin verin veya yazılı girişi kullanın • Mic permission denied"
            )
        } else if (sessionActive && !_state.value.isMuted) {
            startListeningInternal()
        }
    }

    fun applyVoicePreferences(prefs: VoicePreferences) {
        currentPrefs = prefs
        tts?.setPitch(prefs.pitch.coerceIn(0.5f, 2.0f))
        tts?.setSpeechRate(prefs.speechRate.coerceIn(0.5f, 2.0f))
        _state.value = _state.value.copy(
            handsFreeEnabled = prefs.handsFreeAutoListen,
            activeAudioRoute = prefs.preferredAudioRoute
        )
        if (sessionActive && _state.value.phase == VoicePhase.SPEAKING) {
            routeAudioForSpeaking(prefs.preferredAudioRoute)
        }
    }

    fun refreshAudioDevices() {
        try {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasBt = devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            _state.value = _state.value.copy(bluetoothConnected = hasBt)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enumerate audio devices: ${e.message}")
        }
    }

    override fun startSession(prefs: VoicePreferences) {
        sessionActive = true
        sentenceChunker.reset()
        pendingUtteranceCount.set(0)
        lastCapturedPartialText = ""
        busyRetryCount = 0
        applyVoicePreferences(prefs)

        val recognitionAvailable = SpeechRecognizer.isRecognitionAvailable(appContext)
        _state.value = _state.value.copy(
            speechRecognitionAvailable = recognitionAvailable,
            isNetworkAvailable = checkActiveNetworkConnected(),
            hasPendingDraftTranscript = false
        )
        if (!_state.value.micPermissionGranted) {
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                statusMessage = "Mikrofon izni gerekli • Grant microphone permission to start talking"
            )
            return
        }
        if (recognitionAvailable && !_state.value.isMuted) {
            startListeningInternal()
        } else {
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                statusMessage = if (recognitionAvailable) {
                    "Görüşme hazır — konuşmak için dokunun"
                } else {
                    "Donanım mikrofon tanıma kullanılamıyor; aşağıdaki hızlı sesli ileti çubuğunu kullanabilirsiniz."
                }
            )
        }
    }

    override fun setHandsFreeEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(handsFreeEnabled = enabled)
    }

    override fun toggleMute() {
        val nextMuted = !_state.value.isMuted
        _state.value = _state.value.copy(isMuted = nextMuted)
        if (nextMuted) {
            stopListeningInternal(destroyInstance = false)
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                rmsDbLevel = 0f,
                statusMessage = "Mikrofon sessize alındı • Microphone muted"
            )
        } else {
            startListeningInternal()
        }
    }

    override fun selectAudioRoute(route: AudioRoutePreference) {
        _state.value = _state.value.copy(activeAudioRoute = route)
        if (_state.value.phase == VoicePhase.SPEAKING) {
            routeAudioForSpeaking(route)
        }
    }

    /**
     * Releases communication mode and exclusive audio focus before SpeechRecognizer starts
     * so Android's system RecognitionService has unrestricted access to the device microphone.
     */
    @Suppress("DEPRECATION")
    private fun prepareAudioForMicrophoneRecognition() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Audio mode release for microphone recognition skipped: ${e.message}")
        }
    }

    private fun releaseSpeechOutputAudioMode() {
        prepareAudioForMicrophoneRecognition()
    }

    @Suppress("DEPRECATION")
    private fun routeAudioForSpeaking(route: AudioRoutePreference) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            refreshAudioDevices()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetDevice = when (route) {
                    AudioRoutePreference.BLUETOOTH -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    AudioRoutePreference.EARPIECE -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    AudioRoutePreference.SPEAKER -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

                if (targetDevice != null) {
                    audioManager.setCommunicationDevice(targetDevice)
                }
            } else {
                when (route) {
                    AudioRoutePreference.BLUETOOTH -> {
                        audioManager.isSpeakerphoneOn = false
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }
                    AudioRoutePreference.EARPIECE -> {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.isSpeakerphoneOn = false
                    }
                    AudioRoutePreference.SPEAKER -> {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.isSpeakerphoneOn = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio routing adjustment failed: ${e.message}")
        }
    }

    private fun requestSpeakingAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { change ->
                        mainHandler.post {
                            handleAudioFocusChange(change)
                        }
                    }
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed: ${e.message}")
        }
    }

    internal fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPausedByTransientFocusLoss = (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                onInterruptActiveGeneration()
                pauseForBackground(
                    reason = "Telefon çağrısı veya ses odağı nedeniyle duraklatıldı • Paused for incoming call / audio focus"
                )
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                tts?.setSpeechRate((currentPrefs.speechRate * 0.9f).coerceIn(0.5f, 2.0f))
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                tts?.setSpeechRate(currentPrefs.speechRate.coerceIn(0.5f, 2.0f))
                if (wasPausedByTransientFocusLoss && sessionActive) {
                    wasPausedByTransientFocusLoss = false
                    resumeFromBackground()
                }
            }
        }
    }

    /**
     * Hybrid Tap-to-Send & Manual Listen trigger:
     * - If the companion is speaking/thinking, interrupts immediately and starts listening.
     * - If the user has already spoken words (`liveTranscript` or `lastCapturedPartialText` is non-blank),
     *   tapping the primary button immediately commits and transmits the spoken turn to the companion!
     * - Otherwise starts listening cleanly.
     */
    override fun startListeningManual() {
        if (_state.value.phase == VoicePhase.SPEAKING || _state.value.phase == VoicePhase.THINKING) {
            interruptCompanionSpeech()
            return
        }
        val activeSpoken = _state.value.liveTranscript.trim().ifEmpty { lastCapturedPartialText.trim() }
        if (activeSpoken.isNotEmpty()) {
            commitPendingTranscript()
            return
        }
        _state.value = _state.value.copy(isMuted = false, hasPendingDraftTranscript = false)
        startListeningInternal()
    }

    override fun commitPendingTranscript() {
        val candidate = _state.value.liveTranscript.trim().ifEmpty { lastCapturedPartialText.trim() }
        if (candidate.isEmpty()) return
        stopListeningInternal(destroyInstance = false)
        lastCapturedPartialText = ""
        finalizeSpokenInput(candidate)
    }

    override fun updateDraftTranscript(draft: String) {
        val trimmed = draft.trim()
        lastCapturedPartialText = trimmed
        _state.value = _state.value.copy(
            liveTranscript = draft,
            hasPendingDraftTranscript = trimmed.isNotEmpty()
        )
    }

    override fun clearDraftTranscriptAndListen() {
        lastCapturedPartialText = ""
        _state.value = _state.value.copy(
            liveTranscript = "",
            hasPendingDraftTranscript = false,
            isMuted = false
        )
        startListeningInternal()
    }

    /**
     * Captures partial speech recognition results and preserves them in `lastCapturedPartialText`
     * so they are never lost if background noise or a breath pause interrupts recognition.
     */
    internal fun onPartialTranscriptCaptured(partial: String) {
        val cleaned = partial.trim()
        if (cleaned.isNotEmpty()) {
            lastCapturedPartialText = cleaned
            _state.value = _state.value.copy(
                liveTranscript = cleaned,
                hasPendingDraftTranscript = true,
                statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                    "Algılanıyor... (Hemen göndermek için butona dokunabilirsin)"
                } else {
                    "Capturing speech... (Tap button to send immediately)"
                }
            )
        }
    }

    /**
     * Preserves any captured partial speech when recognition pauses or encounters noise/timeout
     * instead of discarding it, allowing one-tap Send or Edit.
     */
    internal fun onRecognitionIncompleteOrInterrupted(errorCode: Int = SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
        val preserved = _state.value.liveTranscript.trim().ifEmpty { lastCapturedPartialText.trim() }
        val isTr = _state.value.voiceLanguageTag.startsWith("tr")
        if (preserved.isNotEmpty()) {
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                liveTranscript = preserved,
                hasPendingDraftTranscript = true,
                rmsDbLevel = 0f,
                statusMessage = if (isTr) {
                    "Konuşma korundu — tek dokunuşla gönderebilir veya düzenleyebilirsiniz"
                } else {
                    "Speech preserved — tap Send or Edit below"
                }
            )
            return
        }

        val friendly = when (errorCode) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                if (isTr) "Dinleme durakladı — konuşmak için dokunun" else "Listening paused — tap mic to speak"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                if (isTr) "Mikrofon izni gerekli" else "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                if (isTr) "Ağ gecikmesi — tekrar denemek için dokunun" else "Network audio error — tap mic to retry"
            else ->
                if (isTr) "Konuşmak için mikrofona dokunun" else "Tap microphone to speak"
        }
        _state.value = _state.value.copy(
            phase = VoicePhase.IDLE,
            rmsDbLevel = 0f,
            statusMessage = friendly
        )
    }

    private fun ensureSpeechRecognizer(): SpeechRecognizer {
        val existing = speechRecognizer
        if (existing != null) {
            return existing
        }
        val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                busyRetryCount = 0
                val existingDraft = _state.value.liveTranscript.takeIf { _state.value.hasPendingDraftTranscript }.orEmpty()
                _state.value = _state.value.copy(
                    phase = VoicePhase.LISTENING,
                    liveTranscript = existingDraft,
                    statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                        "Seni dinliyorum... doğal konuşabilirsin"
                    } else {
                        "Listening... speak naturally"
                    }
                )
            }

            override fun onBeginningOfSpeech() {
                if (pendingUtteranceCount.get() > 0) {
                    tts?.stop()
                    pendingUtteranceCount.set(0)
                    onInterruptActiveGeneration()
                }
                _state.value = _state.value.copy(
                    phase = VoicePhase.LISTENING,
                    statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                        "Seni duyuyorum... (Bitince dokunup hemen gönderebilirsin)"
                    } else {
                        "Hearing you... (Tap anytime to send immediately)"
                    }
                )
            }

            override fun onRmsChanged(rmsdB: Float) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                _state.value = _state.value.copy(rmsDbLevel = normalized)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(
                    rmsDbLevel = 0f,
                    statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                        "Konuşma tamamlanıyor..."
                    } else {
                        "Finalizing speech..."
                    }
                )
            }

            override fun onError(error: Int) {
                if ((error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) &&
                    busyRetryCount < 2 &&
                    sessionActive &&
                    !_state.value.isMuted
                ) {
                    busyRetryCount += 1
                    try {
                        speechRecognizer?.destroy()
                    } catch (_: Exception) {
                    }
                    speechRecognizer = null
                    mainHandler.postDelayed({
                        startListeningInternal()
                    }, 260L)
                    return
                }
                onRecognitionIncompleteOrInterrupted(error)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()?.trim().orEmpty()
                    .ifEmpty { lastCapturedPartialText.trim() }
                    .ifEmpty { _state.value.liveTranscript.trim() }

                if (spoken.isNotEmpty()) {
                    lastCapturedPartialText = ""
                    finalizeSpokenInput(spoken)
                } else {
                    onRecognitionIncompleteOrInterrupted(SpeechRecognizer.ERROR_NO_MATCH)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (partial.isNotBlank()) {
                    onPartialTranscriptCaptured(partial)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer = recognizer
        return recognizer
    }

    private fun startListeningInternal() {
        mainHandler.post {
            if (!sessionActive || _state.value.isMuted || !_state.value.micPermissionGranted) return@post
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _state.value = _state.value.copy(
                    phase = VoicePhase.IDLE,
                    speechRecognitionAvailable = false,
                    statusMessage = "Sesli yanıt duymak için aşağıdan Türkçe veya İngilizce yazabilirsiniz"
                )
                return@post
            }

            try {
                prepareAudioForMicrophoneRecognition()
                val recognizer = ensureSpeechRecognizer()
                try {
                    recognizer.cancel()
                } catch (_: Exception) {
                }

                val langTag = _state.value.voiceLanguageTag
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3500L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
                }
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {
                }
                speechRecognizer = null
                _state.value = _state.value.copy(
                    phase = VoicePhase.IDLE,
                    statusMessage = "Konuşmak için mikrofona dokunun veya yazılı girişi kullanın"
                )
            }
        }
    }

    override fun submitSpokenOrTypedTurn(utterance: String) {
        val cleaned = utterance.trim()
        if (cleaned.isEmpty()) return
        if (_state.value.phase == VoicePhase.SPEAKING || _state.value.phase == VoicePhase.THINKING) {
            tts?.stop()
            pendingUtteranceCount.set(0)
            onInterruptActiveGeneration()
        }
        stopListeningInternal(destroyInstance = false)
        lastCapturedPartialText = ""
        finalizeSpokenInput(cleaned)
    }

    private fun finalizeSpokenInput(utterance: String) {
        val detectedTurkish = PersonalityEngine.shouldRespondInTurkish(
            userMessage = utterance,
            preferredLanguageTag = _state.value.voiceLanguageTag
        )
        val targetLang = if (detectedTurkish) "tr-TR" else "en-US"
        if (targetLang != _state.value.voiceLanguageTag) {
            configureTtsLocaleAndVoice(targetLang)
        }

        sentenceChunker.reset()
        pendingUtteranceCount.set(0)
        lastCapturedPartialText = ""
        turnStartRealtimeMs = SystemClock.elapsedRealtime()
        hasRecordedFirstAudioThisTurn = false

        _state.value = _state.value.copy(
            phase = VoicePhase.THINKING,
            voiceLanguageTag = targetLang,
            liveTranscript = utterance,
            hasPendingDraftTranscript = false,
            companionCaption = "",
            rmsDbLevel = 0f,
            statusMessage = if (detectedTurkish) "Düşünüyor..." else "Companion is thinking..."
        )
        onUserUtteranceFinalized(utterance)
    }

    override fun onStreamingChunkReceived(
        accumulatedText: String,
        deltaText: String,
        firstTokenLatencyMs: Long
    ) {
        _state.value = _state.value.copy(
            companionCaption = accumulatedText,
            firstTokenLatencyMs = firstTokenLatencyMs
        )
        val readyClauses = sentenceChunker.extractReadyClauses(
            accumulatedText = accumulatedText,
            isStreamFinished = false
        )
        for (clause in readyClauses) {
            enqueueSpeechClause(clause)
        }
    }

    override fun onStreamingReplyComplete(fullReply: String) {
        val cleaned = fullReply.trim()
        if (cleaned.isEmpty()) return
        _state.value = _state.value.copy(companionCaption = cleaned)

        val remainingClauses = sentenceChunker.extractReadyClauses(
            accumulatedText = cleaned,
            isStreamFinished = true
        )
        for (clause in remainingClauses) {
            enqueueSpeechClause(clause)
        }

        if (pendingUtteranceCount.get() == 0 && _state.value.phase != VoicePhase.SPEAKING) {
            enqueueSpeechClause(SentenceStreamChunker.sanitizeForSpeech(cleaned))
        }
    }

    private fun enqueueSpeechClause(clause: String) {
        if (clause.isBlank()) return
        stopListeningInternal(destroyInstance = false)

        val isFirstClause = pendingUtteranceCount.getAndIncrement() == 0
        if (isFirstClause) {
            requestSpeakingAudioFocus()
            routeAudioForSpeaking(_state.value.activeAudioRoute)
        }
        if (!hasRecordedFirstAudioThisTurn && turnStartRealtimeMs > 0L) {
            hasRecordedFirstAudioThisTurn = true
            val firstAudioMs = (SystemClock.elapsedRealtime() - turnStartRealtimeMs).coerceAtLeast(1L)
            _state.value = _state.value.copy(firstAudioLatencyMs = firstAudioMs)
        }

        _state.value = _state.value.copy(
            phase = VoicePhase.SPEAKING,
            statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                "Konuşuyor... (Araya girmek için dokun)"
            } else {
                "Speaking... (Tap to interrupt)"
            }
        )

        if (isTtsReady && tts != null) {
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_VOICE_CALL)
            }
            val queueMode = if (isFirstClause) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(clause, queueMode, params, "clause_${UUID.randomUUID()}")
        } else {
            val estimatedMs = (clause.split(" ").size * 220L).coerceIn(900L, 4000L)
            mainHandler.postDelayed({
                val rem = pendingUtteranceCount.decrementAndGet().coerceAtLeast(0)
                pendingUtteranceCount.set(rem)
                if (rem == 0) {
                    onAllSpeechClausesFinished()
                }
            }, estimatedMs)
        }
    }

    fun updateStreamingCaption(caption: String) {
        onStreamingChunkReceived(
            accumulatedText = caption,
            deltaText = "",
            firstTokenLatencyMs = _state.value.firstTokenLatencyMs
        )
    }

    fun speakCompanionReply(replyText: String) {
        onStreamingReplyComplete(replyText)
    }

    override fun interruptCompanionSpeech() {
        tts?.stop()
        pendingUtteranceCount.set(0)
        sentenceChunker.reset()
        onInterruptActiveGeneration()
        releaseSpeechOutputAudioMode()
        val nextCount = _state.value.interruptionCount + 1
        _state.value = _state.value.copy(
            phase = VoicePhase.IDLE,
            isMuted = false,
            interruptionCount = nextCount,
            statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                "Söz kesildi — seni dinliyorum..."
            } else {
                "Interrupted — listening to you..."
            }
        )
        startListeningInternal()
    }

    override fun onGenerationError(errorMessage: String) {
        tts?.stop()
        pendingUtteranceCount.set(0)
        sentenceChunker.reset()
        releaseSpeechOutputAudioMode()
        val preserved = _state.value.liveTranscript.trim().ifEmpty { lastCapturedPartialText.trim() }
        _state.value = _state.value.copy(
            phase = VoicePhase.ERROR,
            liveTranscript = preserved,
            hasPendingDraftTranscript = preserved.isNotEmpty(),
            rmsDbLevel = 0f,
            statusMessage = errorMessage
        )
    }

    override fun pauseForBackground(reason: String) {
        if (!sessionActive) return
        stopListeningInternal(destroyInstance = false)
        tts?.stop()
        pendingUtteranceCount.set(0)
        releaseSpeechOutputAudioMode()
        _state.value = _state.value.copy(
            phase = VoicePhase.PAUSED,
            rmsDbLevel = 0f,
            statusMessage = reason
        )
    }

    override fun resumeFromBackground() {
        if (!sessionActive) return
        if (_state.value.handsFreeEnabled &&
            !_state.value.isMuted &&
            _state.value.micPermissionGranted &&
            !_state.value.hasPendingDraftTranscript
        ) {
            startListeningInternal()
        } else {
            _state.value = _state.value.copy(
                phase = VoicePhase.IDLE,
                statusMessage = if (_state.value.voiceLanguageTag.startsWith("tr")) {
                    "Görüşme devam ediyor — konuşmak için dokunun"
                } else {
                    "Session resumed — tap mic to speak"
                }
            )
        }
    }

    private fun stopListeningInternal(destroyInstance: Boolean = false) {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                if (destroyInstance) {
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun stopSession() {
        sessionActive = false
        pendingUtteranceCount.set(0)
        sentenceChunker.reset()
        lastCapturedPartialText = ""
        stopListeningInternal(destroyInstance = true)
        try {
            tts?.stop()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up audio session: ${e.message}")
        }
        _state.value = VoiceSessionUiState()
    }

    override fun release() {
        stopSession()
        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {
        }
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        }
        try {
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }
}
