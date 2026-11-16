package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CharacterPreferencesDataStore
import com.example.data.model.CharacterAppearance
import com.example.data.model.CharacterIdentity
import com.example.data.model.ClothingAndAccessories
import com.example.data.model.CompanionState
import com.example.data.model.CompanionVoiceTone
import com.example.data.model.CustomizableSlot
import com.example.data.model.DiscoveryDimensions
import com.example.data.model.IdealSelfProfile
import com.example.data.model.PersonalityTraits
import com.example.data.model.VoicePreferences
import com.example.ui.components.CharacterVisualState
import com.example.ui.components.PixelAssetCatalog
import com.example.ui.components.PixelCharacterSprite
import com.example.ui.components.PixelOption
import com.example.ui.theme.LanguageToggleChip
import com.example.ui.theme.tr
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class CharacterSubSection(val label: String, val trLabel: String) {
    APPEARANCE("Appearance", "Görünüm"),
    OUTFIT("Outfit", "Kıyafet"),
    IDENTITY("Ideal Self", "Kişilik"),
    DISCOVERY("Discovery", "Keşif")
}

enum class VisualStudioCategory(
    val id: String,
    val trTitle: String,
    val enTitle: String,
    val icon: ImageVector
) {
    SILHOUETTE("silhouette", "Silüet", "Silhouette", Icons.Default.AccessibilityNew),
    HAIR("hair", "Saç", "Hair", Icons.Default.Face),
    EYEWEAR("eyewear", "Gözlük", "Eyewear", Icons.Default.Visibility),
    OUTFIT("outfit", "Kıyafet", "Outfit", Icons.Default.Checkroom),
    EXPRESSIONS("expressions", "İfadeler", "Expressions", Icons.Default.EmojiEmotions)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterScreen(
    companionState: CompanionState,
    initialSubSection: CharacterSubSection = CharacterSubSection.APPEARANCE,
    isQuizSynthesizing: Boolean,
    pendingProposal: CompanionState? = null,
    onSaveAppearance: (CharacterAppearance) -> Unit,
    onSaveClothing: (ClothingAndAccessories) -> Unit,
    onSaveAppearanceAndClothing: (CharacterAppearance, ClothingAndAccessories, String) -> Unit = { app, cloth, _ ->
        onSaveAppearance(app)
        onSaveClothing(cloth)
    },
    onSaveVoicePreferences: (VoicePreferences) -> Unit = {},
    onApplyAiVisualRecommendation: () -> Unit = {},
    onSaveIdentityAndProfile: (CharacterIdentity, IdealSelfProfile, PersonalityTraits) -> Unit,
    onSaveDiscoveryProgress: (DiscoveryDimensions) -> Unit = {},
    onSynthesizeDiscoveryDimensions: (DiscoveryDimensions) -> Unit = {},
    onApproveDiscoveryProposal: (CompanionState) -> Unit = {},
    onDiscardDiscoveryProposal: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER")
    onSynthesizeFromQuiz: (String, String, String, String, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var activeVisualCategory by rememberSaveable {
        mutableStateOf(
            when (initialSubSection) {
                CharacterSubSection.OUTFIT -> VisualStudioCategory.OUTFIT
                else -> VisualStudioCategory.SILHOUETTE
            }
        )
    }
    var showPersonalitySheet by rememberSaveable {
        mutableStateOf(
            initialSubSection == CharacterSubSection.IDENTITY ||
                initialSubSection == CharacterSubSection.DISCOVERY
        )
    }
    var personalitySheetTab by rememberSaveable {
        mutableStateOf(
            if (initialSubSection == CharacterSubSection.DISCOVERY) {
                CharacterSubSection.DISCOVERY
            } else {
                CharacterSubSection.IDENTITY
            }
        )
    }

    LaunchedEffect(initialSubSection) {
        when (initialSubSection) {
            CharacterSubSection.APPEARANCE -> {
                showPersonalitySheet = false
                activeVisualCategory = VisualStudioCategory.SILHOUETTE
            }
            CharacterSubSection.OUTFIT -> {
                showPersonalitySheet = false
                activeVisualCategory = VisualStudioCategory.OUTFIT
            }
            CharacterSubSection.IDENTITY -> {
                showPersonalitySheet = true
                personalitySheetTab = CharacterSubSection.IDENTITY
            }
            CharacterSubSection.DISCOVERY -> {
                showPersonalitySheet = true
                personalitySheetTab = CharacterSubSection.DISCOVERY
            }
        }
    }

    val context = LocalContext.current
    val characterPrefs = remember(context) { CharacterPreferencesDataStore(context) }
    val coroutineScope = rememberCoroutineScope()

    var previewVisualState by rememberSaveable { mutableStateOf(CharacterVisualState.IDLE) }
    var styleVariationIndex by rememberSaveable { mutableIntStateOf(0) }
    var lockedSlots by remember { mutableStateOf(emptySet<CustomizableSlot>()) }

    var draftAppearance by remember(companionState.characterAppearance) {
        mutableStateOf(companionState.characterAppearance)
    }
    var draftClothing by remember(companionState.clothingAndAccessories) {
        mutableStateOf(companionState.clothingAndAccessories)
    }
    var draftVoicePreferences by remember(companionState.voicePreferences) {
        mutableStateOf(companionState.voicePreferences)
    }

    // Restore persisted customization from DataStore when returning to the screen
    LaunchedEffect(characterPrefs) {
        val persisted = characterPrefs.readCustomization()
        if (persisted != null) {
            if (draftAppearance == CharacterAppearance() && persisted.appearance != CharacterAppearance()) {
                draftAppearance = persisted.appearance
                onSaveAppearance(persisted.appearance)
            }
            if (draftClothing == ClothingAndAccessories() && persisted.clothing != ClothingAndAccessories()) {
                draftClothing = persisted.clothing
                onSaveClothing(persisted.clothing)
            }
            if (draftVoicePreferences == VoicePreferences() && persisted.voicePreferences != VoicePreferences()) {
                draftVoicePreferences = persisted.voicePreferences
                onSaveVoicePreferences(persisted.voicePreferences)
            }
        }
    }

    val persistAppearanceChange: (CharacterAppearance) -> Unit = { updated ->
        draftAppearance = updated
        coroutineScope.launch {
            characterPrefs.saveAppearance(updated)
        }
        onSaveAppearance(updated)
    }

    val persistClothingChange: (ClothingAndAccessories) -> Unit = { updated ->
        draftClothing = updated
        coroutineScope.launch {
            characterPrefs.saveClothing(updated)
        }
        onSaveClothing(updated)
    }

    val persistVoiceChange: (VoicePreferences) -> Unit = { updated ->
        draftVoicePreferences = updated
        coroutineScope.launch {
            characterPrefs.saveVoicePreferences(updated)
        }
        onSaveVoicePreferences(updated)
    }

    val persistBothChanges: (CharacterAppearance, ClothingAndAccessories, String) -> Unit = { app, cloth, reason ->
        draftAppearance = app
        draftClothing = cloth
        coroutineScope.launch {
            characterPrefs.saveAppearanceAndClothing(app, cloth, draftVoicePreferences)
        }
        onSaveAppearanceAndClothing(app, cloth, reason)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Clean Top Header Bar (Title + Language Chip + Single-Tap Kişilik Sheet Button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("Karakter Özelleştirme", "Character Customization"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${companionState.characterIdentity.companionName} · ${companionState.characterIdentity.archetypeTitle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LanguageToggleChip(testTagName = "character_language_toggle_button")

                    Surface(
                        onClick = {
                            if (showPersonalitySheet && personalitySheetTab == CharacterSubSection.IDENTITY) {
                                showPersonalitySheet = false
                            } else {
                                personalitySheetTab = CharacterSubSection.IDENTITY
                                showPersonalitySheet = true
                            }
                        },
                        shape = RoundedCornerShape(50),
                        color = if (showPersonalitySheet) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("character_tab_identity")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = tr("Kişilik", "Personality"),
                                tint = if (showPersonalitySheet) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = tr("Kişilik", "Persona"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (showPersonalitySheet) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }

            // 2. Pinned Character Preview Stage (Always stays fixed at the focal point of the screen)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        PixelCharacterSprite(
                            appearance = draftAppearance,
                            clothing = draftClothing,
                            visualState = previewVisualState,
                            activeEmotion = draftAppearance.expressionDefault,
                            modifier = Modifier
                                .size(124.dp)
                                .testTag("character_customizer_preview")
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick Style Generator Button + Discreet Reset Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        val nextBlueprint = PixelAssetCatalog.recommendVisualsForProfile(
                                            idealSelf = companionState.idealSelfProfile,
                                            traits = companionState.personalityTraits,
                                            currentAppearance = draftAppearance,
                                            currentClothing = draftClothing,
                                            lockedSlots = lockedSlots,
                                            variationIndex = styleVariationIndex
                                        )
                                        styleVariationIndex += 1
                                        previewVisualState = CharacterVisualState.REACTING
                                        persistBothChanges(
                                            nextBlueprint.recommendedAppearance,
                                            nextBlueprint.recommendedClothing,
                                            "Applied recommended look"
                                        )
                                        onApplyAiVisualRecommendation()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .testTag("apply_ai_look_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tr("Stil Öner", "Suggest Style"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    onClick = {
                                        val defaultAppearance = CharacterAppearance()
                                        val defaultClothing = ClothingAndAccessories()
                                        lockedSlots = emptySet()
                                        previewVisualState = CharacterVisualState.IDLE
                                        persistBothChanges(
                                            defaultAppearance,
                                            defaultClothing,
                                            "Reset character to default state"
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("reset_character_default_button")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = tr(
                                                "Varsayılana Sıfırla",
                                                "Reset to Default"
                                            ),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = tr("Sıfırla", "Reset"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Compact Lockable Slots (Horizontal Scroll)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CustomizableSlot.entries.forEach { slot ->
                                    val isLocked = slot in lockedSlots
                                    Surface(
                                        onClick = {
                                            lockedSlots = if (isLocked) lockedSlots - slot else lockedSlots + slot
                                        },
                                        shape = RoundedCornerShape(50),
                                        color = if (isLocked) {
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isLocked) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ),
                                        modifier = Modifier.testTag("lock_slot_${slot.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                                contentDescription = null,
                                                modifier = Modifier.size(11.dp),
                                                tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = localizedSlotLabel(slot),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            // Compact Animation Preview Pills (Horizontal Scroll)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CharacterVisualState.entries.forEach { vState ->
                                    val isSelected = previewVisualState == vState
                                    Surface(
                                        onClick = { previewVisualState = vState },
                                        shape = RoundedCornerShape(50),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                        },
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                        ),
                                        modifier = Modifier.testTag("preview_state_${vState.name.lowercase()}")
                                    ) {
                                        Text(
                                            text = localizedVisualStateLabel(vState),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Spring-Animated Transition Between Compact Kişilik Sheet & 5 Visual Categories Studio
            AnimatedContent(
                targetState = showPersonalitySheet,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        initialOffsetY = { fullHeight -> if (targetState) fullHeight / 6 else -fullHeight / 6 }
                    ) + fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )).togetherWith(
                        slideOutVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            targetOffsetY = { fullHeight -> if (targetState) -fullHeight / 8 else fullHeight / 8 }
                        ) + fadeOut(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium)
                        )
                    )
                },
                label = "personality_or_visual_studio_transition"
            ) { isPersonalityOpen ->
                if (isPersonalityOpen) {
                    PersonalityCompactModalPanel(
                        companionState = companionState,
                        activeTab = personalitySheetTab,
                        isQuizSynthesizing = isQuizSynthesizing,
                        pendingProposal = pendingProposal,
                        onSelectTab = { personalitySheetTab = it },
                        onClose = { showPersonalitySheet = false },
                        onSaveIdentityAndProfile = onSaveIdentityAndProfile,
                        onSaveDiscoveryProgress = onSaveDiscoveryProgress,
                        onSynthesizeDiscoveryDimensions = onSynthesizeDiscoveryDimensions,
                        onApproveDiscoveryProposal = { approved ->
                            onApproveDiscoveryProposal(approved)
                            personalitySheetTab = CharacterSubSection.IDENTITY
                        },
                        onDiscardDiscoveryProposal = onDiscardDiscoveryProposal,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 3A. Horizontal Scrollable 5 Visual Categories Bar with Spring Bounce & Color Morph
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VisualStudioCategory.entries.forEach { category ->
                                val selected = activeVisualCategory == category
                                val legacyTabTag = when (category) {
                                    VisualStudioCategory.SILHOUETTE -> "character_tab_appearance"
                                    VisualStudioCategory.OUTFIT -> "character_tab_outfit"
                                    else -> "visual_cat_${category.id}"
                                }
                                val tabScale by animateFloatAsState(
                                    targetValue = if (selected) 1.04f else 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "category_tab_scale_${category.id}"
                                )
                                val tabContainerColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "category_tab_bg_${category.id}"
                                )
                                val tabContentColor by animateColorAsState(
                                    targetValue = if (selected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "category_tab_fg_${category.id}"
                                )

                                Surface(
                                    onClick = { activeVisualCategory = category },
                                    shape = RoundedCornerShape(16.dp),
                                    color = tabContainerColor,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                        }
                                    ),
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = tabScale
                                            scaleY = tabScale
                                        }
                                        .testTag(legacyTabTag)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = tr(category.trTitle, category.enTitle),
                                            tint = tabContentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = tr(category.trTitle, category.enTitle),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = tabContentColor
                                        )
                                    }
                                }
                            }
                        }

                        // 3B. Spring Entry/Exit Animated Visual Category Panel (Character Preview above stays fixed!)
                        AnimatedContent(
                            targetState = activeVisualCategory,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            transitionSpec = {
                                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (slideInHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    initialOffsetX = { fullWidth -> direction * (fullWidth / 5) }
                                ) + fadeIn(
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                ) + scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )).togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                        targetOffsetX = { fullWidth -> -direction * (fullWidth / 6) }
                                    ) + fadeOut(
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                    ) + scaleOut(
                                        targetScale = 0.96f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                    )
                                )
                            },
                            label = "visual_category_panel_spring_transition"
                        ) { currentCategory ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                when (currentCategory) {
                                    VisualStudioCategory.SILHOUETTE -> {
                                        SilhouetteVisualGridSection(
                                            companionName = companionState.characterIdentity.companionName,
                                            appearance = draftAppearance,
                                            clothing = draftClothing,
                                            voicePreferences = draftVoicePreferences,
                                            personalityTraits = companionState.personalityTraits,
                                            onSaveCompanionName = { updatedName ->
                                                onSaveIdentityAndProfile(
                                                    companionState.characterIdentity.copy(
                                                        companionName = updatedName.trim().ifBlank { "Nova" }.take(60)
                                                    ),
                                                    companionState.idealSelfProfile,
                                                    companionState.personalityTraits
                                                )
                                            },
                                            onAppearanceChange = persistAppearanceChange,
                                            onVoicePreferencesChange = { updatedVoice ->
                                                previewVisualState = CharacterVisualState.SPEAKING
                                                persistVoiceChange(updatedVoice)
                                            }
                                        )
                                    }
                                    VisualStudioCategory.HAIR -> {
                                        HairVisualGridSection(
                                            appearance = draftAppearance,
                                            clothing = draftClothing,
                                            onAppearanceChange = persistAppearanceChange
                                        )
                                    }
                                    VisualStudioCategory.EYEWEAR -> {
                                        EyewearVisualGridSection(
                                            appearance = draftAppearance,
                                            clothing = draftClothing,
                                            onAppearanceChange = persistAppearanceChange
                                        )
                                    }
                                    VisualStudioCategory.OUTFIT -> {
                                        OutfitVisualGridSection(
                                            appearance = draftAppearance,
                                            clothing = draftClothing,
                                            onApplyPresetCombo = { combo ->
                                                val nextClothing = draftClothing.copy(
                                                    outfitId = combo.outfitId,
                                                    accessoryId = combo.accessoryId,
                                                    auraEffectId = combo.auraEffectId,
                                                    primaryColorHex = combo.primaryColorHex,
                                                    accentColorHex = combo.accentColorHex
                                                )
                                                previewVisualState = CharacterVisualState.REACTING
                                                persistBothChanges(
                                                    draftAppearance,
                                                    nextClothing,
                                                    "Applied ${combo.label} outfit combination"
                                                )
                                            },
                                            onClothingChange = persistClothingChange
                                        )
                                    }
                                    VisualStudioCategory.EXPRESSIONS -> {
                                        ExpressionsVisualGridSection(
                                            appearance = draftAppearance,
                                            clothing = draftClothing,
                                            voicePreferences = draftVoicePreferences,
                                            personalityTraits = companionState.personalityTraits,
                                            onAppearanceChange = { updated ->
                                                previewVisualState = CharacterVisualState.REACTING
                                                persistAppearanceChange(updated)
                                            },
                                            onClothingChange = persistClothingChange,
                                            onVoicePreferencesChange = { updatedVoice ->
                                                previewVisualState = CharacterVisualState.SPEAKING
                                                persistVoiceChange(updatedVoice)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SilhouetteVisualGridSection(
    companionName: String,
    appearance: CharacterAppearance,
    clothing: ClothingAndAccessories,
    voicePreferences: VoicePreferences,
    personalityTraits: PersonalityTraits,
    onSaveCompanionName: (String) -> Unit,
    onAppearanceChange: (CharacterAppearance) -> Unit,
    onVoicePreferencesChange: (VoicePreferences) -> Unit
) {
    var nameDraft by remember(companionName) { mutableStateOf(companionName) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Compact Name Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text(tr("Karakter Adı", "Character Name")) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("studio_companion_name_input")
                )
                Button(
                    onClick = { onSaveCompanionName(nameDraft) },
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("studio_save_name_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(tr("Kaydet", "Save"))
                }
            }

            // Skin Tone Circular Color Bar
            ColorSwatchRow(
                title = tr("Ten Tonu", "Skin Tone"),
                options = PixelAssetCatalog.skinTones,
                selectedId = appearance.skinToneId,
                onSelect = { onAppearanceChange(appearance.copy(skinToneId = it)) }
            )

            // 3-Column Live Mini Sprite Grid for Body Silhouette
            Text(
                text = tr("Silüet Seçimi", "Body Silhouette"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.presentationStyles,
                selectedId = appearance.presentationId,
                columns = 3,
                appearanceForOption = { optId -> appearance.withPresentationId(optId) },
                clothingForOption = { clothing },
                onSelect = { onAppearanceChange(appearance.withPresentationId(it)) }
            )

            // Companion Voice Tone Selector matched to Persona
            CompanionVoiceToneSelectorSection(
                appearance = appearance,
                personalityTraits = personalityTraits,
                voicePreferences = voicePreferences,
                onVoicePreferencesChange = onVoicePreferencesChange
            )

            // Quick Face Detail Preview Row inside Silhouette for immediate access
            Text(
                text = tr("Öne Çıkan Yüz Detayları", "Signature Face Accents"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.faceFeatures.take(6),
                selectedId = appearance.faceFeatureId,
                columns = 3,
                appearanceForOption = { optId -> appearance.withFaceFeature(optId) },
                clothingForOption = { clothing },
                onSelect = { onAppearanceChange(appearance.withFaceFeature(it)) }
            )
        }
    }
}

@Composable
private fun HairVisualGridSection(
    appearance: CharacterAppearance,
    clothing: ClothingAndAccessories,
    onAppearanceChange: (CharacterAppearance) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hair Color Swatch Row
            ColorSwatchRow(
                title = tr("Saç Rengi", "Hair Color"),
                options = PixelAssetCatalog.hairColors,
                selectedId = appearance.hairColorId,
                onSelect = { onAppearanceChange(appearance.copy(hairColorId = it)) }
            )

            // 3-Column Live Mini Sprite Grid for Hairstyles
            Text(
                text = tr("Saç Modelleri", "Hairstyles"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.hairStyles,
                selectedId = appearance.hairStyleId,
                columns = 3,
                appearanceForOption = { optId -> appearance.copy(hairStyleId = optId) },
                clothingForOption = { clothing },
                onSelect = { onAppearanceChange(appearance.copy(hairStyleId = it)) }
            )
        }
    }
}

@Composable
private fun EyewearVisualGridSection(
    appearance: CharacterAppearance,
    clothing: ClothingAndAccessories,
    onAppearanceChange: (CharacterAppearance) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Eye Iris Color Swatch Row
            ColorSwatchRow(
                title = tr("Göz Rengi", "Eye Color"),
                options = PixelAssetCatalog.eyeColors,
                selectedId = appearance.eyeColorId,
                onSelect = { onAppearanceChange(appearance.copy(eyeColorId = it)) }
            )

            // 3-Column Live Mini Sprite Grid for Eyewear & Face Details
            Text(
                text = tr("Gözlük ve Yüz Aksesuarları", "Eyewear & Face Details"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.faceFeatures,
                selectedId = appearance.faceFeatureId,
                columns = 3,
                appearanceForOption = { optId -> appearance.withFaceFeature(optId) },
                clothingForOption = { clothing },
                onSelect = { onAppearanceChange(appearance.withFaceFeature(it)) }
            )

            // Eye Shapes Grid
            Text(
                text = tr("Bakış ve Göz Şekli", "Eye Shape"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.eyeStyles,
                selectedId = appearance.eyeStyleId,
                columns = 2,
                appearanceForOption = { optId -> appearance.copy(eyeStyleId = optId) },
                clothingForOption = { clothing },
                onSelect = { onAppearanceChange(appearance.copy(eyeStyleId = it)) }
            )
        }
    }
}

@Composable
private fun OutfitVisualGridSection(
    appearance: CharacterAppearance,
    clothing: ClothingAndAccessories,
    onApplyPresetCombo: (com.example.ui.components.OutfitComboPreset) -> Unit,
    onClothingChange: (ClothingAndAccessories) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Horizontal Dual-Color Palette Swatches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("Kıyafet Renk Paleti", "Outfit Color Palette"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PixelAssetCatalog.outfitPalettes.forEachIndexed { idx, (primaryHex, accentHex) ->
                        val isSelected = clothing.primaryColorHex.equals(primaryHex, ignoreCase = true)
                        val pColor = PixelAssetCatalog.parseHexColor(primaryHex, Color(0xFFFFB347))
                        val aColor = PixelAssetCatalog.parseHexColor(accentHex, Color(0xFFFF7E67))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    }
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable {
                                    onClothingChange(
                                        clothing.copy(
                                            primaryColorHex = primaryHex,
                                            accentColorHex = accentHex
                                        )
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("outfit_palette_$idx")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(pColor)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(aColor)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Curated Lookbooks in a 2-Column Live Sprite Grid
            Text(
                text = tr("Hazır Kombinler", "Ready Lookbooks"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var lookbooksEntered by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { lookbooksEntered = true }

                PixelAssetCatalog.outfitComboPresets.chunked(2).forEachIndexed { rowIndex, rowPresets ->
                    val rowEntryScale by animateFloatAsState(
                        targetValue = if (lookbooksEntered) 1f else 0.93f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = (Spring.StiffnessMediumLow - rowIndex * 25f).coerceAtLeast(180f)
                        ),
                        label = "lookbook_row_entry_$rowIndex"
                    )
                    val rowEntryAlpha by animateFloatAsState(
                        targetValue = if (lookbooksEntered) 1f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "lookbook_row_alpha_$rowIndex"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = rowEntryScale
                                scaleY = rowEntryScale
                                alpha = rowEntryAlpha
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { combo ->
                            val isSelected = clothing.outfitId == combo.outfitId &&
                                clothing.primaryColorHex.equals(combo.primaryColorHex, ignoreCase = true)
                            val comboScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.03f else 1.0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "lookbook_card_scale_${combo.id}"
                            )
                            val previewClothing = clothing.copy(
                                outfitId = combo.outfitId,
                                accessoryId = combo.accessoryId,
                                auraEffectId = combo.auraEffectId,
                                primaryColorHex = combo.primaryColorHex,
                                accentColorHex = combo.accentColorHex
                            )
                            Surface(
                                onClick = { onApplyPresetCombo(combo) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer {
                                        scaleX = comboScale
                                        scaleY = comboScale
                                    }
                                    .testTag("outfit_combo_${combo.id}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PixelCharacterSprite(
                                        appearance = appearance,
                                        clothing = previewClothing,
                                        modifier = Modifier.size(64.dp),
                                        showStudioFrame = false
                                    )
                                    Text(
                                        text = localizedComboLabel(combo.id, combo.label),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        if (rowPresets.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 3. Layered Tops (3-Column Visual Grid)
            Text(
                text = tr("Üst Giyim ve Ceketler", "Tops & Outerwear"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.outfits,
                selectedId = clothing.layeredTopId,
                columns = 3,
                appearanceForOption = { appearance },
                clothingForOption = { optId -> clothing.withLayeredTop(optId) },
                onSelect = { onClothingChange(clothing.withLayeredTop(it)) }
            )

            // 4. Trousers (3-Column Visual Grid)
            Text(
                text = tr("Pantolonlar", "Trousers"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.trousers,
                selectedId = clothing.trousersId,
                columns = 3,
                appearanceForOption = { appearance },
                clothingForOption = { optId -> clothing.withTrousers(optId) },
                onSelect = { onClothingChange(clothing.withTrousers(it)) }
            )

            // 5. Boots & Footwear (3-Column Visual Grid)
            Text(
                text = tr("Botlar ve Ayakkabılar", "Boots & Footwear"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.footwear,
                selectedId = clothing.footwearId,
                columns = 3,
                appearanceForOption = { appearance },
                clothingForOption = { optId -> clothing.withFootwear(optId) },
                onSelect = { onClothingChange(clothing.withFootwear(it)) }
            )

            // 6. Accessories (3-Column Visual Grid)
            Text(
                text = tr("Aksesuarlar", "Accessories"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.accessories,
                selectedId = clothing.accessoryId,
                columns = 3,
                appearanceForOption = { appearance },
                clothingForOption = { optId -> clothing.copy(accessoryId = optId) },
                onSelect = { onClothingChange(clothing.copy(accessoryId = it)) }
            )
        }
    }
}

@Composable
private fun ExpressionsVisualGridSection(
    appearance: CharacterAppearance,
    clothing: ClothingAndAccessories,
    voicePreferences: VoicePreferences,
    personalityTraits: PersonalityTraits,
    onAppearanceChange: (CharacterAppearance) -> Unit,
    onClothingChange: (ClothingAndAccessories) -> Unit,
    onVoicePreferencesChange: (VoicePreferences) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = tr("Yüz İfadeleri ve Duygu Durumu", "Facial Expressions & Mood"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.expressions,
                selectedId = appearance.expressionDefault,
                columns = 3,
                appearanceForOption = { optId -> appearance.copy(expressionDefault = optId) },
                clothingForOption = { clothing },
                emotionForOption = { optId -> optId },
                onSelect = { onAppearanceChange(appearance.copy(expressionDefault = it)) }
            )

            CompanionVoiceToneSelectorSection(
                appearance = appearance,
                personalityTraits = personalityTraits,
                voicePreferences = voicePreferences,
                onVoicePreferencesChange = onVoicePreferencesChange
            )

            Text(
                text = tr("Sahne Atmosferi", "Stage Atmosphere"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            VisualSpriteGrid(
                options = PixelAssetCatalog.auraEffects,
                selectedId = clothing.auraEffectId,
                columns = 2,
                appearanceForOption = { appearance },
                clothingForOption = { optId -> clothing.copy(auraEffectId = optId) },
                onSelect = { onClothingChange(clothing.copy(auraEffectId = it)) }
            )
        }
    }
}

@Composable
private fun CompanionVoiceToneSelectorSection(
    appearance: CharacterAppearance,
    personalityTraits: PersonalityTraits,
    voicePreferences: VoicePreferences,
    onVoicePreferencesChange: (VoicePreferences) -> Unit
) {
    val activeTone = voicePreferences.voiceTone
    val recommendedTone = remember(appearance, personalityTraits) {
        CompanionVoiceTone.recommendedForPersona(appearance, personalityTraits)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = tr("Karakter Ses Tonu", "Companion Voice Tone"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                onClick = {
                    onVoicePreferencesChange(voicePreferences.withVoiceTone(recommendedTone))
                },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.testTag("match_persona_voice_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = tr("Persona ile Eşleştir", "Match Persona"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompanionVoiceTone.entries.chunked(2).forEach { rowTones ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowTones.forEach { tone ->
                        val isSelected = activeTone == tone
                        val isRecommended = recommendedTone == tone
                        val cardScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.03f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "voice_tone_scale_${tone.id}"
                        )

                        Surface(
                            onClick = {
                                onVoicePreferencesChange(voicePreferences.withVoiceTone(tone))
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    scaleX = cardScale
                                    scaleY = cardScale
                                }
                                .testTag("voice_tone_card_${tone.id}")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tr(tone.trLabel, tone.enLabel),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else if (isRecommended) {
                                        Text(
                                            text = tr("Önerilen", "Match"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                Text(
                                    text = tr(tone.trSubtitle, tone.enSubtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (rowTones.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    title: String,
    options: List<PixelOption>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEach { option ->
                val selected = option.id == selectedId
                val swatchColor = if (option.previewColor != Color.Unspecified) {
                    option.previewColor
                } else {
                    MaterialTheme.colorScheme.primary
                }
                Surface(
                    onClick = { onSelect(option.id) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    },
                    border = BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier.testTag("pixel_opt_${option.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                        )
                        Text(
                            text = localizedPixelOptionLabel(option.id, option.label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VisualSpriteGrid(
    options: List<PixelOption>,
    selectedId: String,
    columns: Int,
    appearanceForOption: (String) -> CharacterAppearance,
    clothingForOption: (String) -> ClothingAndAccessories,
    emotionForOption: (String) -> String = { appearanceForOption(it).expressionDefault },
    onSelect: (String) -> Unit
) {
    var gridEntered by remember { mutableStateOf(false) }
    LaunchedEffect(options) {
        gridEntered = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEachIndexed { rowIndex, rowItems ->
            val rowEntryScale by animateFloatAsState(
                targetValue = if (gridEntered) 1f else 0.93f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = (Spring.StiffnessMediumLow - rowIndex * 25f).coerceAtLeast(180f)
                ),
                label = "grid_row_entry_scale_$rowIndex"
            )
            val rowEntryAlpha by animateFloatAsState(
                targetValue = if (gridEntered) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "grid_row_entry_alpha_$rowIndex"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = rowEntryScale
                        scaleY = rowEntryScale
                        alpha = rowEntryAlpha
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { option ->
                    val selected = option.id == selectedId
                    val itemSelectionScale by animateFloatAsState(
                        targetValue = if (selected) 1.03f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "grid_item_selection_scale_${option.id}"
                    )
                    val cardBgColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "grid_item_bg_${option.id}"
                    )

                    Surface(
                        onClick = { onSelect(option.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = cardBgColor,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = itemSelectionScale
                                scaleY = itemSelectionScale
                            }
                            .testTag("pixel_opt_${option.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                PixelCharacterSprite(
                                    appearance = appearanceForOption(option.id),
                                    clothing = clothingForOption(option.id),
                                    activeEmotion = emotionForOption(option.id),
                                    modifier = Modifier.size(60.dp),
                                    showStudioFrame = false
                                )
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = selected,
                                    enter = scaleIn(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ) + fadeIn(),
                                    exit = scaleOut() + fadeOut()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = localizedPixelOptionLabel(option.id, option.label),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PersonalityCompactModalPanel(
    companionState: CompanionState,
    activeTab: CharacterSubSection,
    isQuizSynthesizing: Boolean,
    pendingProposal: CompanionState?,
    onSelectTab: (CharacterSubSection) -> Unit,
    onClose: () -> Unit,
    onSaveIdentityAndProfile: (CharacterIdentity, IdealSelfProfile, PersonalityTraits) -> Unit,
    onSaveDiscoveryProgress: (DiscoveryDimensions) -> Unit,
    onSynthesizeDiscoveryDimensions: (DiscoveryDimensions) -> Unit,
    onApproveDiscoveryProposal: (CompanionState) -> Unit,
    onDiscardDiscoveryProposal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row with Kişilik / Keşif switcher + Close button back to Visual Studio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == CharacterSubSection.IDENTITY,
                    onClick = { onSelectTab(CharacterSubSection.IDENTITY) },
                    label = { Text(tr("Kişilik ve Hedefler", "Persona & Goals")) },
                    leadingIcon = {
                        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    )
                )
                FilterChip(
                    selected = activeTab == CharacterSubSection.DISCOVERY,
                    onClick = { onSelectTab(CharacterSubSection.DISCOVERY) },
                    label = { Text(tr("Keşif Soruları", "Discovery Quiz")) },
                    leadingIcon = {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("character_tab_discovery")
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .testTag("close_personality_sheet_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = tr("Görsel Stüdyoya Dön", "Back to Visual Studio"),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (activeTab == CharacterSubSection.DISCOVERY) {
            if (pendingProposal != null) {
                DiscoveryProposalApprovalCard(
                    proposal = pendingProposal,
                    onApprove = onApproveDiscoveryProposal,
                    onBackToEdit = onDiscardDiscoveryProposal
                )
            } else {
                GuidedDiscoveryWizard(
                    initialDimensions = DiscoveryDimensions.fromIdealSelfProfile(
                        companionState.idealSelfProfile,
                        companionState.personalityTraits
                    ),
                    companionState = companionState,
                    isSynthesizing = isQuizSynthesizing,
                    onSaveProgress = onSaveDiscoveryProgress,
                    onSynthesize = onSynthesizeDiscoveryDimensions
                )
            }
        } else {
            IdentityAndPersonalityEditor(
                identity = companionState.characterIdentity,
                idealSelf = companionState.idealSelfProfile,
                traits = companionState.personalityTraits,
                onSave = { updatedId, updatedIdeal, updatedTraits ->
                    onSaveIdentityAndProfile(updatedId, updatedIdeal, updatedTraits)
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun IdentityAndPersonalityEditor(
    identity: CharacterIdentity,
    idealSelf: IdealSelfProfile,
    traits: PersonalityTraits,
    onSave: (CharacterIdentity, IdealSelfProfile, PersonalityTraits) -> Unit
) {
    var companionName by remember(identity.companionName) { mutableStateOf(identity.companionName) }
    var archetypeTitle by remember(identity.archetypeTitle) { mutableStateOf(identity.archetypeTitle) }
    var greetingStyle by remember(identity.greetingStyle) { mutableStateOf(identity.greetingStyle) }
    var catchphrase by remember(identity.signatureCatchphrase) { mutableStateOf(identity.signatureCatchphrase) }

    var coreAspiration by remember(idealSelf.coreAspiration) { mutableStateOf(idealSelf.coreAspiration) }
    var lifePillarsText by remember(idealSelf.lifePillars) { mutableStateOf(idealSelf.lifePillars.joinToString(", ")) }
    var growthGoals by remember(idealSelf.growthGoals) { mutableStateOf(idealSelf.growthGoals) }
    var emotionalGrounding by remember(idealSelf.emotionalGrounding) { mutableStateOf(idealSelf.emotionalGrounding) }
    var reflectionStyle by remember(idealSelf.reflectionStyle) { mutableStateOf(idealSelf.reflectionStyle) }

    var warmth by remember(traits.warmth) { mutableIntStateOf(traits.warmth) }
    var directness by remember(traits.directness) { mutableIntStateOf(traits.directness) }
    var optimism by remember(traits.optimism) { mutableIntStateOf(traits.optimism) }
    var playfulness by remember(traits.playfulness) { mutableIntStateOf(traits.playfulness) }
    var analyticalDepth by remember(traits.analyticalDepth) { mutableIntStateOf(traits.analyticalDepth) }
    var keywordsText by remember(traits.traitKeywords) { mutableStateOf(traits.traitKeywords.joinToString(", ")) }

    val structuredModel = remember(identity, idealSelf, warmth, directness, optimism, playfulness, analyticalDepth, keywordsText) {
        com.example.ai.PersonalityEngine.buildStructuredModel(
            CompanionState(
                characterIdentity = identity,
                idealSelfProfile = idealSelf,
                personalityTraits = PersonalityTraits(
                    warmth = warmth,
                    directness = directness,
                    optimism = optimism,
                    playfulness = playfulness,
                    analyticalDepth = analyticalDepth,
                    traitKeywords = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                )
            )
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = tr("Karakter Kişiliği ve İdeal Benlik", "Character Persona & Ideal Self"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Clean User-Facing Persona Summary Card (No developer/engine terminology)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("structured_personality_model_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = tr("Karakter Özeti ve İletişim Tarzı", "Persona Summary & Communication Style"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = tr(
                            "Öne Çıkan Özellikler: ${structuredModel.strengths.joinToString(" • ")}",
                            "Key Strengths: ${structuredModel.strengths.joinToString(" • ")}"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = tr(
                            "İletişim Yaklaşımı: ${structuredModel.relationshipPhilosophy}",
                            "Friendship Dynamic: ${structuredModel.relationshipPhilosophy}"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = companionName,
                onValueChange = { companionName = it },
                label = { Text(tr("Yoldaş Adı", "Companion Name")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("companion_name_input")
            )

            OutlinedTextField(
                value = archetypeTitle,
                onValueChange = { archetypeTitle = it },
                label = { Text(tr("Unvan / Rol", "Role / Title")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = coreAspiration,
                onValueChange = { coreAspiration = it },
                label = { Text(tr("Temel İdeal Benlik Hedefi", "Core Ideal-Self Goal")) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = lifePillarsText,
                onValueChange = { lifePillarsText = it },
                label = { Text(tr("Yaşam Değerleri (virgülle ayrılmış)", "Core Values (comma-separated)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = growthGoals,
                onValueChange = { growthGoals = it },
                label = { Text(tr("Günlük Gelişim Odağı", "Daily Growth Focus")) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = greetingStyle,
                onValueChange = { greetingStyle = it },
                label = { Text(tr("Karşılama Cümlesi", "Greeting Style")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = catchphrase,
                onValueChange = { catchphrase = it },
                label = { Text(tr("Motto", "Motto")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = tr("İletişim Tonu Ayarları", "Communication Tone"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            TraitSlider(label = tr("Sıcaklık ve Empati", "Warmth & Empathy"), value = warmth, onValueChange = { warmth = it })
            TraitSlider(label = tr("Doğrudanlık ve Netlik", "Directness & Clarity"), value = directness, onValueChange = { directness = it })
            TraitSlider(label = tr("İyimserlik ve Motivasyon", "Optimism & Motivation"), value = optimism, onValueChange = { optimism = it })
            TraitSlider(label = tr("Mizah ve Enerji", "Playfulness & Wit"), value = playfulness, onValueChange = { playfulness = it })
            TraitSlider(label = tr("Derinlik ve Odak", "Depth & Focus"), value = analyticalDepth, onValueChange = { analyticalDepth = it })

            OutlinedTextField(
                value = keywordsText,
                onValueChange = { keywordsText = it },
                label = { Text(tr("Kişilik Etiketleri (virgülle ayrılmış)", "Persona Tags (comma-separated)")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val pillars = lifePillarsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val kw = keywordsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onSave(
                        identity.copy(
                            companionName = companionName.trim().ifBlank { "Nova" },
                            archetypeTitle = archetypeTitle.trim().ifBlank { "The Serene Architect" },
                            greetingStyle = greetingStyle.trim(),
                            signatureCatchphrase = catchphrase.trim()
                        ),
                        idealSelf.copy(
                            coreAspiration = coreAspiration.trim().ifBlank { "Grow into my ideal self." },
                            lifePillars = if (pillars.isEmpty()) listOf("Mindful Clarity") else pillars.take(10),
                            growthGoals = growthGoals.trim(),
                            emotionalGrounding = emotionalGrounding.trim(),
                            reflectionStyle = reflectionStyle.trim().ifBlank { "Warm & Socratic" }
                        ),
                        PersonalityTraits(
                            warmth = warmth,
                            directness = directness,
                            optimism = optimism,
                            playfulness = playfulness,
                            analyticalDepth = analyticalDepth,
                            traitKeywords = if (kw.isEmpty()) listOf("Grounded", "Empathetic") else kw.take(10)
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_identity_profile_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(tr("Kişilik Profilini Kaydet", "Save Persona Profile"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TraitSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..100f
        )
    }
}

@Composable
private fun localizedSlotLabel(slot: CustomizableSlot): String = when (slot) {
    CustomizableSlot.PRESENTATION -> tr("Silüet", "Silhouette")
    CustomizableSlot.HAIR -> tr("Saç", "Hair")
    CustomizableSlot.FACE -> tr("Gözlük", "Eyewear")
    CustomizableSlot.TOP -> tr("Üst", "Top")
    CustomizableSlot.TROUSERS -> tr("Alt", "Trousers")
    CustomizableSlot.FOOTWEAR -> tr("Bot", "Boots")
    CustomizableSlot.ACCESSORY -> tr("Detay", "Accessory")
    CustomizableSlot.PALETTE -> tr("Renk", "Palette")
}

@Composable
private fun localizedVisualStateLabel(state: CharacterVisualState): String = when (state) {
    CharacterVisualState.IDLE -> tr("Duruş", "Idle")
    CharacterVisualState.LISTENING -> tr("Dinliyor", "Listening")
    CharacterVisualState.THINKING -> tr("Düşünüyor", "Thinking")
    CharacterVisualState.SPEAKING -> tr("Konuşuyor", "Speaking")
    CharacterVisualState.REACTING -> tr("Tepki", "Reacting")
}

@Composable
private fun localizedComboLabel(id: String, fallback: String): String = when (id) {
    "rebel_counter_philosopher" -> tr("Asi Düşünür", "Rebel Thinker")
    "midnight_scholar" -> tr("Gece Mimarı", "Calm Architect")
    "velvet_nomad" -> tr("Kadife Gezgin", "Velvet Drifter")
    "cozy_lofi_dreamer" -> tr("Lo-Fi Prodüktör", "Lo-Fi Producer")
    "zen_sage" -> tr("Modern Bilge", "Zen Sage")
    else -> fallback
}

@Composable
private fun localizedPixelOptionLabel(id: String, fallback: String): String = when (id) {
    // Silhouettes
    "androgynous" -> tr("Dengeli", "Balanced")
    "masculine" -> tr("Maskülen", "Masculine")
    "feminine" -> tr("Feminen", "Feminine")
    // Skin tones
    "warm_ivory" -> tr("Fildişi", "Warm Ivory")
    "golden_tan" -> tr("Bronz", "Golden Tan")
    "deep_umber" -> tr("Koyu Kehribar", "Deep Umber")
    "porcelain_rose" -> tr("Porselen", "Porcelain")
    "starlight_lilac" -> tr("Lila", "Lilac")
    // Eyewear & Face Features
    "tinted_aviator" -> tr("Pilot Gözlük", "Aviators")
    "round_tortoise" -> tr("Kemik Gözlük", "Round Frames")
    "brow_plaster" -> tr("Kaş Bandı", "Brow Plaster")
    "sharp_stubble" -> tr("Kirli Sakal", "Stubble")
    "visor_clip" -> tr("Çerçevesiz", "Rimless Clip")
    "star_mark" -> tr("Yıldız İzi", "Star Mark")
    "pixel_freckles" -> tr("Çilli", "Freckles")
    "hero_plaster" -> tr("Yara Bandı", "Plaster")
    "soft_blush" -> tr("Allık", "Warm Blush")
    "clean" -> tr("Sade Yüz", "Clean")
    // Hairstyles
    "tousled_rebel" -> tr("Dağınık Asi", "Tousled")
    "textured_undercut" -> tr("Kısa Kesim", "Undercut")
    "vintage_shag" -> tr("Katlı Kesim", "Layered Shag")
    "braided_crown" -> tr("Örgülü", "Braided")
    "bleached_buzz" -> tr("Üç Numara", "Buzz Cut")
    "cyber_mullet" -> tr("Modern Mullet", "Wolf Mullet")
    "starlight_bob" -> tr("Asimetrik Küt", "Bob Cut")
    "spiky_hero" -> tr("Rüzgarlı", "Wind-Swept")
    "cozy_bun" -> tr("Üst Topuz", "Top-Knot")
    "cyber_swept" -> tr("Yana Taralı", "Side Fringe")
    "curly_cloud" -> tr("Kıvırcık", "Curly")
    // Hair colors
    "midnight_indigo" -> tr("Gece Mavisi", "Indigo")
    "crimson_flame" -> tr("Bordo Kızıl", "Crimson")
    "amber_gold" -> tr("Bal Sarısı", "Amber Gold")
    "silver_frost" -> tr("Platin Gri", "Silver")
    "espresso_noir" -> tr("Koyu Kahve", "Espresso")
    "copper_rust" -> tr("Bakır", "Copper")
    "mint_aurora" -> tr("Mint Yeşili", "Mint")
    // Eye styles & colors
    "sparkle_round" -> tr("Canlı Yuvarlak", "Expressive")
    "calm_focused" -> tr("Odaklı", "Focused")
    "cheerful_arc" -> tr("Neşeli", "Cheerful")
    "mystic_star" -> tr("Keskin Bakış", "Sharp")
    "emerald_glow" -> tr("Zümrüt", "Emerald")
    "cobalt_sky" -> tr("Kobalt", "Cobalt")
    "violet_dream" -> tr("Menekşe", "Violet")
    "crimson_ember" -> tr("Kızıl", "Crimson")
    // Expressions
    "warm_smile" -> tr("Tebessüm", "Warm Smile")
    "determined" -> tr("Kararlı", "Focused")
    "curious" -> tr("Meraklı", "Curious")
    "serene" -> tr("Dingin", "Serene")
    "joyful" -> tr("Neşeli", "Joyful")
    "wink" -> tr("Göz Kırpma", "Wink")
    // Tops
    "rebel_leather" -> tr("Deri Ceket", "Leather Jacket")
    "trench_havana" -> tr("Trençkot", "Trench Coat")
    "blazer_hoodie" -> tr("Blazer & Kapüşon", "Blazer Hoodie")
    "patch_cardi" -> tr("Hırka", "Cardigan")
    "shear_bomber" -> tr("Bomber Ceket", "Bomber")
    "velvet_mesh" -> tr("Kadife Ceket", "Velvet Jacket")
    "field_flannel" -> tr("Oduncu Gömlek", "Field Flannel")
    "denim_turtle" -> tr("Kot & Boğazlı", "Denim & Knit")
    "cozy_hoodie" -> tr("Rahat Kapüşonlu", "Cozy Hoodie")
    "scholar_vest" -> tr("Mimar Yeleği", "Tailored Vest")
    "cyber_jacket" -> tr("Motorcu Ceketi", "Moto Jacket")
    "sage_kimono" -> tr("Kimono Ceket", "Haori Coat")
    // Trousers
    "wide_pleat" -> tr("Bol Kesim", "Wide Pleated")
    "cargo_punk" -> tr("Kargo Pantolon", "Cargo")
    "cuff_denim" -> tr("Katlı Kot", "Cuffed Denim")
    "tartan_puk" -> tr("Ekose Pantolon", "Tartan Plaid")
    "para_drape" -> tr("Paraşüt Kesim", "Parachute")
    "flare_cord" -> tr("Kadife İspanyol", "Flared Cord")
    // Footwear
    "combat_boot" -> tr("Postal Bot", "Combat Boots")
    "chelsea_bt" -> tr("Chelsea Bot", "Chelsea Boots")
    "retro_high" -> tr("Yüksek Bilek", "High-Tops")
    "lug_loafer" -> tr("Kalın Loafer", "Loafers")
    "moto_buckle" -> tr("Tokalı Bot", "Moto Boots")
    "tabi_avant" -> tr("Modern Bot", "Split-Toe")
    // Accessories & Aura
    "crossbody_bag" -> tr("Çapraz Çanta", "Crossbody Bag")
    "silver_chain" -> tr("Gümüş Zincir", "Silver Chain")
    "studded_belt" -> tr("Zımbalı Kemer", "Studded Belt")
    "silk_scarf" -> tr("İpek Fular", "Silk Scarf")
    "ear_cuff_pin" -> tr("Küpe & Rozet", "Ear Cuff")
    "pixel_glasses" -> tr("Retro Çerçeve", "Retro Frames")
    "starlight_headphones" -> tr("Kulaklık", "Headphones")
    "amber_scarf" -> tr("Örgü Atkı", "Knit Scarf")
    "crown_sprout" -> tr("Yaprak Rozeti", "Sprout Pin")
    "none" -> tr("Aksesuarsız", "None")
    "starlight_fireflies" -> tr("Bordo Parıltı", "Burgundy Glow")
    "amber_halo" -> tr("Adaçayı Halesi", "Sage Halo")
    "mint_matrix" -> tr("Krem Işıltı", "Cream Glow")
    "minimal_glow" -> tr("Sade Işık", "Subtle Glow")
    else -> fallback
}
