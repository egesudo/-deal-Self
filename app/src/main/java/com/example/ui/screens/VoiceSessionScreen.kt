package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.StreamingChunkEvent
import com.example.data.model.AudioRoutePreference
import com.example.data.model.CompanionState
import com.example.ui.components.CharacterVisualState
import com.example.ui.components.PixelCharacterSprite
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.LocalOnToggleLanguage
import com.example.ui.theme.tr
import com.example.voice.VoicePhase
import com.example.voice.VoiceSessionController
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSessionScreen(
    companionState: CompanionState,
    activeEmotion: String,
    lastExtractedMemory: String?,
    onSendVoiceTurn: (
        userUtterance: String,
        onStreamChunk: (String) -> Unit,
        onCompleteReply: (String) -> Unit
    ) -> Unit = { _, _, _ -> },
    onSendVoiceTurnWithMetrics: (
        userUtterance: String,
        preferredLanguageTag: String,
        onStreamEvent: (StreamingChunkEvent) -> Unit,
        onCompleteReply: (String) -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { utterance, _, onStreamEvent, onCompleteReply, _ ->
        onSendVoiceTurn(
            utterance,
            { partial ->
                onStreamEvent(
                    StreamingChunkEvent(
                        accumulatedText = partial,
                        deltaText = partial,
                        firstTokenLatencyMs = 180L
                    )
                )
            },
            onCompleteReply
        )
    },
    onCancelActiveGeneration: () -> Unit = {},
    onCloseSession: () -> Unit
) {
    BackHandler {
        onCancelActiveGeneration()
        onCloseSession()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val currentVoiceController = remember(context) {
        lateinit var ctrl: VoiceSessionController
        ctrl = VoiceSessionController(
            context = context,
            onInterruptActiveGeneration = onCancelActiveGeneration
        ) { finalizedUtterance ->
            val langTag = ctrl.state.value.voiceLanguageTag
            onSendVoiceTurnWithMetrics(
                finalizedUtterance,
                langTag,
                { chunkEvent ->
                    ctrl.onStreamingChunkReceived(
                        accumulatedText = chunkEvent.accumulatedText,
                        deltaText = chunkEvent.deltaText,
                        firstTokenLatencyMs = chunkEvent.firstTokenLatencyMs
                    )
                },
                { fullReply ->
                    ctrl.onStreamingReplyComplete(fullReply)
                },
                { errMsg ->
                    ctrl.onGenerationError(errMsg)
                }
            )
        }
        ctrl
    }

    val voiceState by currentVoiceController.state.collectAsStateWithLifecycle()
    var showPromptInput by rememberSaveable { mutableStateOf(false) }
    var manualSpokenInput by rememberSaveable { mutableStateOf("") }

    val characterVisualState = when (voiceState.phase) {
        VoicePhase.LISTENING -> CharacterVisualState.LISTENING
        VoicePhase.THINKING -> CharacterVisualState.THINKING
        VoicePhase.SPEAKING -> CharacterVisualState.SPEAKING
        VoicePhase.IDLE, VoicePhase.PAUSED, VoicePhase.ERROR -> {
            if (!lastExtractedMemory.isNullOrBlank()) CharacterVisualState.REACTING
            else CharacterVisualState.IDLE
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.RECORD_AUDIO] == true
        hasAudioPermission = granted
        currentVoiceController.onMicrophonePermissionResult(granted)
        if (granted) {
            currentVoiceController.startSession(companionState.voicePreferences)
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (hasAudioPermission) {
            currentVoiceController.onMicrophonePermissionResult(true)
            currentVoiceController.startSession(companionState.voicePreferences)
        } else {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(voiceState.speechRecognitionAvailable) {
        if (!voiceState.speechRecognitionAvailable) {
            showPromptInput = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> currentVoiceController.pauseForBackground()
                Lifecycle.Event.ON_RESUME -> {
                    if (hasAudioPermission) {
                        currentVoiceController.resumeFromBackground()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentVoiceController.release()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("voice_session_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Session Status Bar + Bilingual TR/EN Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(
                                    when (voiceState.phase) {
                                        VoicePhase.LISTENING -> MaterialTheme.colorScheme.tertiary
                                        VoicePhase.SPEAKING -> MaterialTheme.colorScheme.primary
                                        VoicePhase.THINKING -> MaterialTheme.colorScheme.secondary
                                        VoicePhase.ERROR -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val connectionPrefix = if (voiceState.phase == VoicePhase.ERROR || !voiceState.isNetworkAvailable) {
                            tr("OTURUM UYARISI", "SESSION ALERT")
                        } else {
                            tr("BAĞLANDI", "CONNECTED")
                        }
                        Text(
                            text = "$connectionPrefix • ${voiceState.phase.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (voiceState.phase == VoicePhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("voice_connection_status")
                        )
                    }
                    Text(
                        text = companionState.characterIdentity.companionName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                val onToggleAppLanguage = LocalOnToggleLanguage.current
                val currentAppLang = LocalAppLanguage.current
                LaunchedEffect(currentAppLang) {
                    currentVoiceController.setVoiceLocale(currentAppLang.voiceTag)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Turkish (tr-TR) / English (en-US) Language Toggle Pill
                    Surface(
                        onClick = {
                            val nextLang = if (voiceState.voiceLanguageTag.startsWith("tr")) "en-US" else "tr-TR"
                            currentVoiceController.setVoiceLocale(nextLang)
                            onToggleAppLanguage()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("voice_lang_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = tr("Ses Dilini Değiştir", "Toggle Voice Language"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (voiceState.voiceLanguageTag.startsWith("tr")) "TR Türkçe" else "EN English",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = { showPromptInput = !showPromptInput },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("toggle_voice_prompt_drawer")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = "Toggle spoken prompt input",
                            tint = if (showPromptInput) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            onCancelActiveGeneration()
                            onCloseSession()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .testTag("close_voice_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Voice Session",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // 2. Large Center Animated 2D Pixel Companion & Minimal Waveform Stage
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PixelCharacterSprite(
                    appearance = companionState.characterAppearance,
                    clothing = companionState.clothingAndAccessories,
                    visualState = characterVisualState,
                    isSpeaking = voiceState.phase == VoicePhase.SPEAKING,
                    isListening = voiceState.phase == VoicePhase.LISTENING,
                    activeEmotion = activeEmotion,
                    modifier = Modifier
                        .size(204.dp)
                        .testTag("voice_pixel_companion_sprite")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Clean User-Facing Voice Session Status Pill
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("voice_latency_badge")
                ) {
                    val isTurkishVoice = voiceState.voiceLanguageTag.startsWith("tr")
                    Text(
                        text = if (isTurkishVoice) {
                            tr("Canlı Sesli Sohbet • Türkçe", "Live Voice Chat • Turkish")
                        } else {
                            tr("Canlı Sesli Sohbet • İngilizce", "Live Voice Chat • English")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Minimal Real-Time Audio Waveform Visualizer
                VoiceWaveformVisualizer(
                    phase = voiceState.phase,
                    rmsLevel = voiceState.rmsDbLevel,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(36.dp)
                        .testTag("voice_waveform_visualizer")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = voiceState.statusMessage,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (voiceState.phase == VoicePhase.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("voice_status_message")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtle Live Subtitle Caption + One-Tap Partial Transcript Send/Edit Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (voiceState.liveTranscript.isNotBlank()) {
                            Text(
                                text = "\"${voiceState.liveTranscript}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.tertiary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("voice_live_transcript")
                            )

                            if (voiceState.phase == VoicePhase.LISTENING || voiceState.hasPendingDraftTranscript) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = {
                                            currentVoiceController.commitPendingTranscript()
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("voice_send_partial_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = tr("Hemen Gönder", "Send Now"),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            manualSpokenInput = voiceState.liveTranscript
                                            showPromptInput = true
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                        ),
                                        modifier = Modifier.testTag("voice_edit_partial_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = tr("Düzenle", "Edit"),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            currentVoiceController.clearDraftTranscriptAndListen()
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        modifier = Modifier.testTag("voice_clear_partial_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = tr("Yeniden", "Retake"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            text = voiceState.companionCaption.ifBlank {
                                "\"${companionState.characterIdentity.greetingStyle}\""
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("voice_companion_caption")
                        )
                    }
                }

                // Error or Permission Recovery Action Bar (prevents fake stuck active-call state)
                if (voiceState.phase == VoicePhase.ERROR || !hasAudioPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!hasAudioPermission) {
                            OutlinedButton(
                                onClick = {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                },
                                modifier = Modifier.testTag("voice_grant_mic_recovery_button")
                            ) {
                                Text(tr("Mikrofon İzni Ver", "Allow Mic Access"))
                            }
                        }
                        if (voiceState.liveTranscript.isNotBlank() && voiceState.phase == VoicePhase.ERROR) {
                            OutlinedButton(
                                onClick = {
                                    currentVoiceController.submitSpokenOrTypedTurn(voiceState.liveTranscript)
                                },
                                modifier = Modifier.testTag("voice_retry_turn_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tr("Son Cümleyi Tekrar Dene", "Retry Last Turn"))
                            }
                        }
                    }
                }

                if (!lastExtractedMemory.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = tr("Hafızaya Kaydedildi: $lastExtractedMemory", "Memory Saved: $lastExtractedMemory"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // 3. Bottom Call Controls (Audio Routing, Optional Prompt Drawer, Mic & End Call)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(visible = showPromptInput) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "Selam! Bugün her şeyi fazla düşünüyorum, ne dersin?",
                                "Beni biraz sorgula, sence nerede bahane üretiyorum?",
                                "Hey, challenge my perspective on work-life balance"
                            ).forEachIndexed { idx, samplePhrase ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            currentVoiceController.submitSpokenOrTypedTurn(samplePhrase)
                                        }
                                        .testTag("voice_sample_phrase_$idx")
                                ) {
                                    Text(
                                        text = samplePhrase,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                        OutlinedTextField(
                            value = manualSpokenInput,
                            onValueChange = { manualSpokenInput = it },
                            placeholder = {
                                Text(tr("Konuşun veya cümle yazın (TR / EN)...", "Speak or type phrase (TR / EN)..."))
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_fallback_input")
                        )
                        IconButton(
                            onClick = {
                                if (manualSpokenInput.isNotBlank()) {
                                    currentVoiceController.submitSpokenOrTypedTurn(manualSpokenInput)
                                    manualSpokenInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("send_voice_turn_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = tr("Sesli yanıt gönder", "Send voice turn"),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Audio Output Routing Pills (Speaker / Earpiece / Bluetooth) + Hands-Free Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AudioRoutePreference.entries.forEach { route ->
                        val selected = voiceState.activeAudioRoute == route
                        val icon = when (route) {
                            AudioRoutePreference.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                            AudioRoutePreference.EARPIECE -> Icons.Default.PhoneInTalk
                            AudioRoutePreference.BLUETOOTH -> Icons.Default.BluetoothAudio
                        }
                        val routeShortLabel = when (route) {
                            AudioRoutePreference.SPEAKER -> tr("Hoparlör", "Speakerphone")
                            AudioRoutePreference.EARPIECE -> tr("Ahize", "Earpiece")
                            AudioRoutePreference.BLUETOOTH -> "Bluetooth"
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { currentVoiceController.selectAudioRoute(route) },
                            label = { Text(routeShortLabel) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = route.label,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("audio_route_chip_${route.name.lowercase()}")
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tr("Eller Serbest", "Hands-Free"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = voiceState.handsFreeEnabled,
                        onCheckedChange = { currentVoiceController.setHandsFreeEnabled(it) },
                        modifier = Modifier.testTag("voice_hands_free_switch")
                    )
                }
            }

            // Primary Microphone / Mute / Interrupt / End-Call Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { currentVoiceController.toggleMute() },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("voice_mute_button")
                ) {
                    Icon(
                        imageVector = if (voiceState.isMuted) Icons.Default.MicOff else Icons.Default.Hearing,
                        contentDescription = if (voiceState.isMuted) tr("Mikrofonu Aç", "Unmute microphone") else tr("Mikrofonu Kapat", "Mute microphone"),
                        tint = if (voiceState.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }

                val hasReadySpokenDraft =
                    (voiceState.phase == VoicePhase.LISTENING && voiceState.liveTranscript.isNotBlank()) ||
                        voiceState.hasPendingDraftTranscript

                Button(
                    onClick = {
                        if (!hasAudioPermission) {
                            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        } else if (voiceState.phase == VoicePhase.SPEAKING || voiceState.phase == VoicePhase.THINKING) {
                            currentVoiceController.interruptCompanionSpeech()
                        } else if (hasReadySpokenDraft) {
                            currentVoiceController.commitPendingTranscript()
                        } else {
                            currentVoiceController.startListeningManual()
                        }
                    },
                    modifier = Modifier
                        .height(64.dp)
                        .width(196.dp)
                        .testTag("voice_primary_mic_button"),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            hasReadySpokenDraft -> MaterialTheme.colorScheme.primary
                            voiceState.phase == VoicePhase.LISTENING -> MaterialTheme.colorScheme.tertiary
                            voiceState.phase == VoicePhase.SPEAKING -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = when {
                            voiceState.phase == VoicePhase.SPEAKING -> Icons.Default.StopCircle
                            hasReadySpokenDraft -> Icons.AutoMirrored.Filled.Send
                            else -> Icons.Default.Mic
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            !hasAudioPermission -> tr("Mikrofon İzni", "Grant Mic")
                            voiceState.phase == VoicePhase.SPEAKING -> tr("Sözünü Kes", "Interrupt")
                            hasReadySpokenDraft -> tr("Dokun ve Gönder", "Tap to Send")
                            voiceState.phase == VoicePhase.LISTENING -> tr("Dinleniyor...", "Listening...")
                            voiceState.phase == VoicePhase.THINKING -> tr("İptal Et", "Cancel")
                            else -> tr("Konuş", "Tap to Speak")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                    IconButton(
                        onClick = {
                            onCancelActiveGeneration()
                            onCloseSession()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                            .testTag("end_voice_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Voice Session",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveformVisualizer(
    phase: VoicePhase,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val activeColor = when (phase) {
        VoicePhase.LISTENING -> MaterialTheme.colorScheme.tertiary
        VoicePhase.SPEAKING -> MaterialTheme.colorScheme.primary
        VoicePhase.THINKING -> MaterialTheme.colorScheme.secondary
        VoicePhase.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Canvas(modifier = modifier) {
        val barCount = 18
        val spacing = size.width / (barCount * 2f)
        val barWidth = spacing * 1.1f

        for (i in 0 until barCount) {
            val amplitude = when (phase) {
                VoicePhase.LISTENING -> (0.25f + rmsLevel * 0.75f) * (0.5f + 0.5f * sin(wavePhase + i * 0.55f))
                VoicePhase.SPEAKING -> 0.35f + 0.55f * kotlin.math.abs(sin(wavePhase * 1.4f + i * 0.45f))
                VoicePhase.THINKING -> 0.2f + 0.25f * kotlin.math.abs(sin(wavePhase + i * 0.3f))
                else -> 0.15f
            }.coerceIn(0.12f, 1f)

            val barHeight = size.height * amplitude
            val x = i * (barWidth + spacing) + spacing / 2f
            val y = (size.height - barHeight) / 2f

            drawRoundRect(
                color = activeColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
