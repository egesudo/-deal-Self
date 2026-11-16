package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.MemoryEngine
import com.example.data.model.AudioRoutePreference
import com.example.data.model.CompanionState
import com.example.data.model.CompanionVoiceTone
import com.example.data.model.LongTermMemory
import com.example.data.model.UserPrivacySettings
import com.example.data.model.VoicePreferences
import com.example.telemetry.UsageQuotaConfig
import com.example.telemetry.UsageTelemetrySnapshot
import com.example.ui.theme.LanguageToggleChip
import com.example.ui.theme.tr
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsAndPrivacySheet(
    companionState: CompanionState,
    memories: List<LongTermMemory>,
    usageSnapshot: UsageTelemetrySnapshot = UsageTelemetrySnapshot(),
    quotaConfig: UsageQuotaConfig = UsageQuotaConfig(),
    onUpdateVoicePreferences: (VoicePreferences) -> Unit,
    onUpdatePrivacySettings: (UserPrivacySettings) -> Unit,
    onUpdateQuotaConfig: (UsageQuotaConfig) -> Unit = {},
    onAddManualMemory: (String, String, Int) -> Unit,
    onUpdateMemory: (String, String, String, Int) -> Unit = { _, _, _, _ -> },
    onDeleteMemory: (String) -> Unit,
    onClearAllMemories: () -> Unit = {},
    onDeleteAllUserData: () -> Unit = {},
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var voicePitch by remember(companionState.voicePreferences.pitch) {
        mutableFloatStateOf(companionState.voicePreferences.pitch)
    }
    var voiceRate by remember(companionState.voicePreferences.speechRate) {
        mutableFloatStateOf(companionState.voicePreferences.speechRate)
    }
    var handsFree by remember(companionState.voicePreferences.handsFreeAutoListen) {
        mutableStateOf(companionState.voicePreferences.handsFreeAutoListen)
    }
    var preferredRoute by remember(companionState.voicePreferences.preferredAudioRoute) {
        mutableStateOf(companionState.voicePreferences.preferredAudioRoute)
    }

    var autoExtract by remember(companionState.privacySettings.autoExtractMemories) {
        mutableStateOf(companionState.privacySettings.autoExtractMemories)
    }
    var saveHistory by remember(companionState.privacySettings.saveConversationHistory) {
        mutableStateOf(companionState.privacySettings.saveConversationHistory)
    }

    var showDeleteAllConfirmation by remember { mutableStateOf(false) }

    var newMemoryCategory by remember { mutableStateOf("GOAL") }
    var newMemoryText by remember { mutableStateOf("") }

    var editingMemoryId by remember { mutableStateOf<String?>(null) }
    var editingMemoryText by remember { mutableStateOf("") }
    var editingMemoryCategory by remember { mutableStateOf("GOAL") }
    var editingMemoryImportance by remember { mutableIntStateOf(4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("settings_and_privacy_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header & Language Switcher (TR / EN)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Ayarlar", "Settings"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = tr(
                            "Dil, ses, hafıza ve gizlilik tercihleri",
                            "Language, voice, memory & privacy preferences"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LanguageToggleChip(testTagName = "settings_language_toggle_button")
            }

            // 2. Voice Preferences Section (Simple & Direct)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Ses Ayarları", "Voice Settings"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    val selectedTone = CompanionVoiceTone.fromPitchAndRate(voicePitch, voiceRate)
                    Text(
                        text = tr("Karakter Ses Tonu Profilleri", "Companion Voice Tone Presets"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompanionVoiceTone.entries.forEach { tone ->
                            FilterChip(
                                selected = selectedTone == tone,
                                onClick = {
                                    voicePitch = tone.pitch
                                    voiceRate = tone.speechRate
                                },
                                label = {
                                    Text(tr(tone.trLabel, tone.enLabel))
                                },
                                modifier = Modifier.testTag("settings_voice_tone_${tone.id}")
                            )
                        }
                    }

                    Text(
                        text = tr(
                            "Konuşma Hızı: ${String.format(Locale.US, "%.2fx", voiceRate)}",
                            "Speaking Speed: ${String.format(Locale.US, "%.2fx", voiceRate)}"
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = voiceRate,
                        onValueChange = { voiceRate = it },
                        valueRange = 0.7f..1.4f
                    )

                    Text(
                        text = tr(
                            "Ses Tonu: ${String.format(Locale.US, "%.2fx", voicePitch)}",
                            "Voice Pitch: ${String.format(Locale.US, "%.2fx", voicePitch)}"
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = voicePitch,
                        onValueChange = { voicePitch = it },
                        valueRange = 0.7f..1.4f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("Eller Serbest Otomatik Dinle", "Hands-Free Auto-Listen"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(checked = handsFree, onCheckedChange = { handsFree = it })
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AudioRoutePreference.entries.forEach { route ->
                            FilterChip(
                                selected = preferredRoute == route,
                                onClick = { preferredRoute = route },
                                label = {
                                    Text(
                                        when (route) {
                                            AudioRoutePreference.SPEAKER -> tr("Hoparlör", "Speaker")
                                            AudioRoutePreference.EARPIECE -> tr("Ahize", "Earpiece")
                                            AudioRoutePreference.BLUETOOTH -> "Bluetooth"
                                        }
                                    )
                                }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onUpdateVoicePreferences(
                                VoicePreferences(
                                    pitch = voicePitch,
                                    speechRate = voiceRate,
                                    handsFreeAutoListen = handsFree,
                                    preferredAudioRoute = preferredRoute
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_voice_preferences_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(tr("Ses Ayarlarını Kaydet", "Save Voice Settings"))
                    }
                }
            }

            // 3. Shared Memory Bank Section
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr(
                                    "Hafıza Bankası (${memories.size})",
                                    "Memory Bank (${memories.size})"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (memories.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearAllMemories,
                                modifier = Modifier.testTag("clear_all_memories_button")
                            ) {
                                Text(tr("Tümünü Sil", "Clear All"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text(
                        text = tr(
                            "${companionState.characterIdentity.companionName} ile yaptığınız sohbetlerdeki önemli hedefler ve tercihler burada saklanır.",
                            "Important goals and preferences shared with ${companionState.characterIdentity.companionName} are stored here."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            placeholder = {
                                Text(tr("Yeni bir not veya hedef ekle...", "Add a note or goal..."))
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("manual_memory_input")
                        )
                        IconButton(
                            onClick = {
                                if (newMemoryText.isNotBlank()) {
                                    onAddManualMemory(newMemoryCategory, newMemoryText.trim(), 4)
                                    newMemoryText = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("add_manual_memory_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = tr("Anı Ekle", "Add Memory"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (memories.isEmpty()) {
                        Text(
                            text = tr(
                                "Henüz kayıtlı anı yok.",
                                "No saved memories yet."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        memories.take(15).forEach { mem ->
                            val isEditingThis = editingMemoryId == mem.id
                            val semanticBadge = MemoryEngine.extractSemanticBadge(mem)
                            val cleanText = MemoryEngine.stripTagPrefix(mem.summary)

                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isEditingThis) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = editingMemoryText,
                                            onValueChange = { editingMemoryText = it },
                                            singleLine = false,
                                            maxLines = 3,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("edit_memory_input_${mem.id}")
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            OutlinedButton(
                                                onClick = { editingMemoryId = null }
                                            ) {
                                                Text(tr("İptal", "Cancel"))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    if (editingMemoryText.isNotBlank()) {
                                                        onUpdateMemory(
                                                            mem.id,
                                                            editingMemoryCategory,
                                                            editingMemoryText.trim(),
                                                            editingMemoryImportance
                                                        )
                                                        editingMemoryId = null
                                                    }
                                                },
                                                modifier = Modifier.testTag("save_edited_memory_${mem.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(tr("Kaydet", "Save"))
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = semanticBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                            Text(
                                                text = cleanText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    editingMemoryId = mem.id
                                                    editingMemoryText = mem.summary
                                                    editingMemoryCategory = mem.category
                                                    editingMemoryImportance = mem.importance.toInt().coerceIn(1, 5)
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("edit_memory_${mem.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = tr("Anıyı Düzenle", "Edit Memory"),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteMemory(mem.id) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("delete_memory_${mem.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = tr("Anıyı Sil", "Delete Memory"),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Privacy & Data Deletion Controls
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tr("Gizlilik ve Veriler", "Privacy & Data"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("Önemli Anıları Otomatik Hatırla", "Auto-Remember Important Memories"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = autoExtract,
                            onCheckedChange = {
                                autoExtract = it
                                onUpdatePrivacySettings(
                                    companionState.privacySettings.copy(
                                        autoExtractMemories = it,
                                        saveConversationHistory = saveHistory
                                    )
                                )
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            tr("Sohbet Geçmişini Kaydet", "Save Conversation History"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = saveHistory,
                            onCheckedChange = {
                                saveHistory = it
                                onUpdatePrivacySettings(
                                    companionState.privacySettings.copy(
                                        autoExtractMemories = autoExtract,
                                        saveConversationHistory = it
                                    )
                                )
                            }
                        )
                    }

                    if (!showDeleteAllConfirmation) {
                        OutlinedButton(
                            onClick = { showDeleteAllConfirmation = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("delete_all_user_data_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                tr(
                                    "Tüm Verilerimi Sil",
                                    "Delete All My Data"
                                )
                            )
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = tr(
                                        "Profiliniz, tüm sohbetleriniz ve hafıza kayıtlarınız kalıcı olarak silinsin mi?",
                                        "Permanently delete your profile, conversations, and memories?"
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            showDeleteAllConfirmation = false
                                            onDeleteAllUserData()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("confirm_delete_all_data_button")
                                    ) {
                                        Text(tr("Kalıcı Silmeyi Onayla", "Confirm Delete"))
                                    }
                                    OutlinedButton(
                                        onClick = { showDeleteAllConfirmation = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(tr("İptal", "Cancel"))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // 5. Account & Sign Out
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = companionState.userAccount.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (companionState.userAccount.email.isNotBlank()) {
                        Text(
                            text = companionState.userAccount.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSignOut,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Çıkış Yap", "Sign Out"), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
