package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CompanionState
import com.example.data.model.Message
import com.example.ui.components.CharacterVisualState
import com.example.ui.components.PixelCharacterSprite
import com.example.ui.theme.LanguageToggleChip
import com.example.ui.theme.tr
import com.example.viewmodel.AiTurnState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextConversationScreen(
    companionState: CompanionState,
    messages: List<Message>,
    aiTurnState: AiTurnState,
    activeEmotion: String,
    lastExtractedMemory: String?,
    isSearchingWeb: Boolean = false,
    lastWebGroundedSources: String? = null,
    onSendMessage: (String) -> Unit,
    onRetryFailedMessage: () -> Unit = {},
    onSwitchToVoice: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val headerVisualState = when (aiTurnState) {
        is AiTurnState.Thinking -> CharacterVisualState.THINKING
        is AiTurnState.Streaming -> CharacterVisualState.SPEAKING
        is AiTurnState.Error -> CharacterVisualState.IDLE
        AiTurnState.Idle -> {
            if (!lastExtractedMemory.isNullOrBlank()) CharacterVisualState.REACTING
            else CharacterVisualState.IDLE
        }
    }

    LaunchedEffect(messages.size, aiTurnState) {
        if (messages.size > 2) {
            listState.animateScrollToItem((messages.size - 2).coerceAtLeast(0))
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("text_conversation_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Focused Conversation Header with Pixel Avatar & Switch to Voice
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("back_from_text_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        PixelCharacterSprite(
                            appearance = companionState.characterAppearance,
                            clothing = companionState.clothingAndAccessories,
                            visualState = headerVisualState,
                            isSpeaking = aiTurnState is AiTurnState.Streaming,
                            activeEmotion = activeEmotion,
                            modifier = Modifier.size(48.dp),
                            showStudioFrame = true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = companionState.characterIdentity.companionName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tr(
                                    "${companionState.characterIdentity.archetypeTitle} • Mod: ${activeEmotion.replace('_', ' ')}",
                                    "${companionState.characterIdentity.archetypeTitle} • Mood: ${activeEmotion.replace('_', ' ')}"
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageToggleChip(testTagName = "text_chat_language_toggle_button")

                        IconButton(
                            onClick = onSwitchToVoice,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .testTag("switch_to_voice_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = tr("Sesli Sohbete Geç", "Switch to Full-Screen Voice Session"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Memory Extraction / Update Notification Pill
            if (!lastExtractedMemory.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_extracted_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr("Hatırlandı: $lastExtractedMemory", "Remembered: $lastExtractedMemory"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Live Web Grounding Status / Sources Pill
            if (isSearchingWeb || !lastWebGroundedSources.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("web_grounded_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isSearchingWeb) {
                                tr("Canlı web üzerinde güncel bilgiler aranıyor...", "Searching live web for up-to-date information...")
                            } else {
                                tr("Canlı Web ile Doğrulandı ($lastWebGroundedSources)", "Grounded with Live Web ($lastWebGroundedSources)")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. Message Thread List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("text_message_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = tr(
                                        "${companionState.characterIdentity.companionName} diyor ki:",
                                        "${companionState.characterIdentity.companionName} says:"
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = companionState.characterIdentity.greetingStyle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                items(messages, key = { it.id.ifBlank { it.content.hashCode().toString() } }) { msg ->
                    MessageBubble(
                        message = msg,
                        companionName = companionState.characterIdentity.companionName
                    )
                }

                // Streaming, Thinking, or Error Recovery Card
                when (aiTurnState) {
                    is AiTurnState.Thinking -> {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .testTag("text_thinking_indicator")
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isSearchingWeb) {
                                        tr(
                                            "${companionState.characterIdentity.companionName} web'de araştırıyor...",
                                            "${companionState.characterIdentity.companionName} is searching the web..."
                                        )
                                    } else {
                                        tr(
                                            "${companionState.characterIdentity.companionName} düşünüyor...",
                                            "${companionState.characterIdentity.companionName} is thinking..."
                                        )
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is AiTurnState.Streaming -> {
                        item {
                            MessageBubble(
                                message = Message(
                                    id = "streaming_reply",
                                    sender = "COMPANION",
                                    content = aiTurnState.partialReply,
                                    emotionTag = activeEmotion
                                ),
                                companionName = companionState.characterIdentity.companionName
                            )
                        }
                    }
                    is AiTurnState.Error -> {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .testTag("text_error_recovery_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = aiTurnState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Button(
                                        onClick = onRetryFailedMessage,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.testTag("retry_text_message_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = tr("Tekrar dene", "Retry message"),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(tr("Yanıtı Tekrar Dene", "Retry Response"), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                    AiTurnState.Idle -> {}
                }
            }

            // 3. Conversation Starters (Turkish & English, Philosophy, Psychology, Alter-Ego Debate) + Composer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Bugün kafam biraz karışık, sence nereden başlayayım?",
                        "Güncel teknoloji haberleri neler?",
                        "Challenge my current assumption",
                        "Felsefi bir soru: İnsan neden her şeyi kontrol etmeye çalışır?"
                    ).forEachIndexed { idx, prompt ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSendMessage(prompt) }
                                .testTag("quick_prompt_chip_$idx")
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                tr(
                                    "${companionState.characterIdentity.companionName} ile yazış (TR / EN)...",
                                    "Message ${companionState.characterIdentity.companionName} (TR / EN)..."
                                )
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("text_chat_input")
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val toSend = inputText
                                inputText = ""
                                onSendMessage(toSend)
                            }
                        },
                        enabled = inputText.isNotBlank() && aiTurnState !is AiTurnState.Thinking && aiTurnState !is AiTurnState.Streaming,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("send_text_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = tr("Mesaj Gönder", "Send Message"),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    companionName: String
) {
    val isUser = message.sender == "USER"
    val timestampText = remember(message.createdAt) {
        val date = message.createdAt?.toDate() ?: Date()
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .border(
                    width = 1.dp,
                    color = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) tr("Sen", "You") else companionName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = timestampText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("message_timestamp_${message.id.ifBlank { "item" }}")
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
