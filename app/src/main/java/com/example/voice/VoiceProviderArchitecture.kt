package com.example.voice

import com.example.data.model.AudioRoutePreference
import com.example.data.model.VoicePreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * Structured comparison of real-time voice provider architectures evaluated for
 * Turkish (`tr-TR`) and bilingual low-latency mobile companion calls.
 */
data class VoiceProviderSpec(
    val id: String,
    val name: String,
    val turkishQualityRating: String,
    val nativeSdkSupport: String,
    val typicalLatencyMs: String,
    val interruptionHandling: String,
    val costProfile: String,
    val reliabilityProfile: String,
    val privacyProfile: String,
    val isRecommendedDefault: Boolean = false
)

object VoiceProviderEvaluationCatalog {
    val evaluatedProviders: List<VoiceProviderSpec> = listOf(
        VoiceProviderSpec(
            id = "gemini_streaming_native_tr",
            name = "Gemini 3.8 Flash Streaming + Android Low-Latency TR Neural Audio",
            turkishQualityRating = "High — Idiomatic Turkish generation (Gemini 3.8 Flash) paired with Android tr-TR Neural/Network SpeechRecognizer & Voice Call TTS stream",
            nativeSdkSupport = "First-party Jetpack/Android Speech & AudioManager + Firebase AI Logic Kotlin SDK with App Check",
            typicalLatencyMs = "320–680ms Time-to-First-Clause Audio (sentence-chunked streaming)",
            interruptionHandling = "Instant hardware & UI barge-in: flushes TTS queue + cancels in-flight coroutine stream immediately",
            costProfile = "Low — No per-minute WebRTC media server relay fees; uses standard Gemini Flash token pricing + free on-device/OS speech pipeline",
            reliabilityProfile = "High — Automatic fallback between network neural voice and offline on-device tr-TR voice; survives weak mobile networks",
            privacyProfile = "Zero API keys in APK (Firebase App Check + Google Sign-In); audio processed via OS speech engine",
            isRecommendedDefault = true
        ),
        VoiceProviderSpec(
            id = "gemini_live_bidirectional",
            name = "Gemini Live Multimodal WebSocket Audio",
            turkishQualityRating = "High — Native multimodal Turkish prosody and emotion",
            nativeSdkSupport = "Firebase AI Logic / WebSocket PCM16 16kHz/24kHz audio streaming",
            typicalLatencyMs = "300–550ms end-to-end audio frame latency",
            interruptionHandling = "Server-side VAD + client audio buffer flush on user speech onset",
            costProfile = "Medium — Continuous audio token billing during active call",
            reliabilityProfile = "Sensitive to packet jitter on weak cellular connections without adaptive jitter buffering",
            privacyProfile = "Encrypted TLS WebSocket authenticated via Firebase App Check"
        ),
        VoiceProviderSpec(
            id = "openai_realtime_webrtc",
            name = "OpenAI Realtime WebRTC API",
            turkishQualityRating = "Good — Natural Turkish phrasing, occasionally slight accent drift on fast idioms",
            nativeSdkSupport = "Requires third-party WebRTC native peer connection library + ephemeral token relay server",
            typicalLatencyMs = "350–600ms over WebRTC data/audio channels",
            interruptionHandling = "Server-side VAD turn truncation events",
            costProfile = "High — High per-minute audio input/output token cost",
            reliabilityProfile = "Good on Wi-Fi/5G; requires TURN/STUN negotiation on restrictive carrier NATs",
            privacyProfile = "Requires external API key management and third-party audio transit"
        ),
        VoiceProviderSpec(
            id = "elevenlabs_conversational_ws",
            name = "ElevenLabs Turbo v2.5 + LLM WebSocket",
            turkishQualityRating = "Very High — Expressive multilingual v2.5 Turkish timbre",
            nativeSdkSupport = "Custom WebSocket PCM/MP3 chunk streaming over Android AudioTrack",
            typicalLatencyMs = "450–800ms (LLM first token + TTS chunk synthesis)",
            interruptionHandling = "Client-side AudioTrack flush + WebSocket context clear",
            costProfile = "High — Per-character TTS synthesis + LLM token billing",
            reliabilityProfile = "Dependent on two chained cloud APIs (LLM + TTS)",
            privacyProfile = "Requires third-party secret management"
        )
    )
}

/**
 * Replaceable Real-Time Voice Provider interface so the underlying speech recognition,
 * synthesis, or bidirectional audio transport can be swapped without changing UI or ViewModel logic.
 */
interface RealTimeVoiceProvider {
    val providerId: String
    val state: StateFlow<VoiceSessionUiState>

    fun startSession(prefs: VoicePreferences)
    fun stopSession()
    fun release()

    fun setVoiceLocale(languageTag: String)
    fun setHandsFreeEnabled(enabled: Boolean)
    fun toggleMute()
    fun selectAudioRoute(route: AudioRoutePreference)

    fun startListeningManual()
    fun commitPendingTranscript()
    fun updateDraftTranscript(draft: String)
    fun clearDraftTranscriptAndListen()
    fun interruptCompanionSpeech()
    fun submitSpokenOrTypedTurn(utterance: String)

    fun onStreamingChunkReceived(accumulatedText: String, deltaText: String, firstTokenLatencyMs: Long)
    fun onStreamingReplyComplete(fullReply: String)
    fun onGenerationError(errorMessage: String)

    fun pauseForBackground(reason: String = "Voice session paused in background — tap Resume")
    fun resumeFromBackground()
}

/**
 * Incrementally extracts speakable clauses/sentences from a live token stream so that
 * real-time TTS starts speaking the very first sentence while Gemini is still generating
 * the rest of the reply.
 */
class SentenceStreamChunker {
    private var spokenLength = 0

    fun reset() {
        spokenLength = 0
    }

    /**
     * Given the full accumulated streaming text so far, returns any newly completed
     * sentences/clauses that haven't been sent to the audio synthesizer yet.
     */
    fun extractReadyClauses(accumulatedText: String, isStreamFinished: Boolean = false): List<String> {
        if (accumulatedText.length <= spokenLength) {
            return emptyList()
        }
        val unspoken = accumulatedText.substring(spokenLength)
        if (isStreamFinished) {
            spokenLength = accumulatedText.length
            val cleaned = sanitizeForSpeech(unspoken)
            return if (cleaned.isNotEmpty()) listOf(cleaned) else emptyList()
        }

        val ready = mutableListOf<String>()
        var searchStart = 0
        val primaryBoundaryChars = charArrayOf('.', '!', '?', '\n', ';', ':')
        val earlyClauseChars = charArrayOf(',', '—', '–')

        while (searchStart < unspoken.length) {
            var boundaryIdx = -1
            val isVeryFirstClause = (spokenLength + searchStart) == 0
            for (i in searchStart until unspoken.length) {
                val c = unspoken[i]
                val clauseLen = i - searchStart
                if (c in primaryBoundaryChars && clauseLen >= 10) {
                    boundaryIdx = i
                    break
                }
                // Flush the first clause early on a natural comma/dash pause so audio starts immediately
                if (isVeryFirstClause && c in earlyClauseChars && clauseLen >= 18) {
                    boundaryIdx = i
                    break
                }
            }
            if (boundaryIdx == -1) break
            val rawClause = unspoken.substring(searchStart, boundaryIdx + 1)
            val cleanedClause = sanitizeForSpeech(rawClause)
            if (cleanedClause.isNotEmpty()) {
                ready.add(cleanedClause)
            }
            searchStart = boundaryIdx + 1
        }

        if (searchStart > 0) {
            spokenLength += searchStart
        }
        return ready
    }

    companion object {
        fun sanitizeForSpeech(raw: String): String {
            return raw
                .replace(Regex("[*_#`~>]"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
