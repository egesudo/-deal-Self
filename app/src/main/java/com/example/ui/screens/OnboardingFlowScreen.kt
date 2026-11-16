package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.CharacterAppearance
import com.example.data.model.ClothingAndAccessories
import com.example.data.model.CompanionState
import com.example.data.model.DiscoveryDimensions
import com.example.ui.components.CharacterVisualState
import com.example.ui.components.PixelAssetCatalog
import com.example.ui.components.PixelCharacterSprite
import com.example.ui.theme.AppLanguage
import com.example.ui.theme.LanguageToggleChip
import com.example.ui.theme.LocalAppLanguage
import com.example.ui.theme.tr

enum class OnboardingMode {
    WELCOME_CHOICE,
    GUIDED_DISCOVERY,
    DIRECT_CREATION
}

data class DiscoveryPromptStep(
    val index: Int,
    val title: String,
    val trTitle: String,
    val companionQuestion: String,
    val trQuestion: String,
    val suggestions: List<String>,
    val trSuggestions: List<String>
)

val DISCOVERY_STEPS: List<DiscoveryPromptStep> = listOf(
    DiscoveryPromptStep(
        index = 0,
        title = "1. Ideal Personality & Presence",
        trTitle = "1. İdeal Kişilik ve Duruş",
        companionQuestion = "When you picture your most grounded ideal self, how would you describe their presence?",
        trQuestion = "En dengeli ve güçlü ideal benliğini hayal ettiğinde, onun duruşunu nasıl tanımlarsın?",
        suggestions = listOf(
            "Calm, magnetic, and deeply intentional",
            "Bold, creative, and decisively action-oriented",
            "Warm, wise, and effortlessly composed"
        ),
        trSuggestions = listOf(
            "Sakin, etkileyici ve ne istediğini bilen bir duruş",
            "Cesur, yaratıcı ve kararlılıkla harekete geçen",
            "Samimi, bilge ve her koşulda soğukkanlı"
        )
    ),
    DiscoveryPromptStep(
        index = 1,
        title = "2. Core Values",
        trTitle = "2. Temel Değerler",
        companionQuestion = "What 3–4 core values are non-negotiable for your ideal self? (Comma-separated)",
        trQuestion = "İdeal benliğin için vazgeçilmez olan 3-4 temel değer nedir? (Virgülle ayırarak)",
        suggestions = listOf(
            "Intentionality, Courage, Deep Focus, Kindness",
            "Creativity, Freedom, Mastery, Honesty",
            "Serenity, Curiosity, Integrity, Vitality"
        ),
        trSuggestions = listOf(
            "Farkındalık, Cesaret, Derin Odak, Nezaket",
            "Yaratıcılık, Özgürlük, Ustalık, Dürüstlük",
            "Dinginlik, Merak, Tutarlılık, Yaşam Enerjisi"
        )
    ),
    DiscoveryPromptStep(
        index = 2,
        title = "3. Social Energy & Humor",
        trTitle = "3. Sosyal Enerji ve Mizah",
        companionQuestion = "What kind of social energy and humor does your ideal self bring to everyday life?",
        trQuestion = "İnsanlarla iletişimde ve günlük hayatta nasıl bir enerji ve mizah taşırsın?",
        suggestions = listOf(
            "Warmly attentive listener with lighthearted observational wit",
            "Charismatic, playful energy that brings people together",
            "Thoughtful, calm presence with clever dry humor"
        ),
        trSuggestions = listOf(
            "Samimi bir dinleyici; günlük anlara sıcaklık katan kıvrak espri anlayışı",
            "İnsanları bir araya getiren enerjik, neşeli ve doğal yaklaşım",
            "Derinlikli ve sakin bir duruş; gerginliği dağıtan ince mizah"
        )
    ),
    DiscoveryPromptStep(
        index = 3,
        title = "4. Goals & Resilience",
        trTitle = "4. Hedefler ve Dayanıklılık",
        companionQuestion = "What meaningful goal are you building toward, and how do you stay steady under stress?",
        trQuestion = "Odaklandığın en önemli hedef nedir ve stres anında dengeni nasıl korursun?",
        suggestions = listOf(
            "Build meaningful creative work while staying calm and taking one clear step at a time",
            "Achieve mastery and independence by treating every setback as a lesson",
            "Lead impactful projects while protecting inner peace and daily balance"
        ),
        trSuggestions = listOf(
            "İç huzurumu koruyarak anlamlı işler üretmek; stres anında tek bir net adıma odaklanmak",
            "Aksilikleri öğrenme fırsatı görerek alanımda ustalaşmak ve bağımsızlaşmak",
            "Yaşam dengemi koruyarak etkili projeler hayata geçirmek"
        )
    ),
    DiscoveryPromptStep(
        index = 4,
        title = "5. Companion Communication Style",
        trTitle = "5. Yoldaş İletişim Tarzı",
        companionQuestion = "How should I talk with you each day as your companion?",
        trQuestion = "Seninle her gün nasıl bir tonda konuşmamı istersin?",
        suggestions = listOf(
            "Warm, encouraging, and gently direct—asking one clear question at a time",
            "Candid, insightful, and action-focused like a trusted co-founder",
            "Calm, reflective, and supportive like a close late-night friend"
        ),
        trSuggestions = listOf(
            "Samimi, destekleyici ve açık sözlü—her seferinde tek bir net soru sorarak",
            "Güvenilir bir yol arkadaşı gibi dobra, sorgulayıcı ve eylem odaklı",
            "Yakın bir dost gibi sakin, anlayışlı ve düşündürücü"
        )
    )
)

@Composable
fun OnboardingFlowScreen(
    companionState: CompanionState,
    isQuizSynthesizing: Boolean,
    pendingProposal: CompanionState?,
    onSaveDiscoveryProgress: (DiscoveryDimensions) -> Unit,
    onSynthesizeDiscovery: (DiscoveryDimensions) -> Unit,
    onApproveProposal: (CompanionState) -> Unit,
    onDiscardProposal: () -> Unit,
    onCompleteDirectCreation: (
        companionName: String,
        archetypeTitle: String,
        coreAspiration: String,
        lifePillars: List<String>,
        appearance: CharacterAppearance,
        clothing: ClothingAndAccessories
    ) -> Unit,
    onSkipOnboarding: () -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(OnboardingMode.WELCOME_CHOICE) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_flow_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (pendingProposal != null) {
                    DiscoveryProposalApprovalCard(
                        proposal = pendingProposal,
                        onApprove = onApproveProposal,
                        onBackToEdit = onDiscardProposal
                    )
                } else {
                    AnimatedContent(targetState = mode, label = "onboarding_mode") { currentMode ->
                        when (currentMode) {
                            OnboardingMode.WELCOME_CHOICE -> {
                                OnboardingWelcomeChoiceCard(
                                    companionState = companionState,
                                    onStartGuidedDiscovery = { mode = OnboardingMode.GUIDED_DISCOVERY },
                                    onStartDirectCreation = { mode = OnboardingMode.DIRECT_CREATION },
                                    onSkip = onSkipOnboarding
                                )
                            }
                            OnboardingMode.GUIDED_DISCOVERY -> {
                                GuidedDiscoveryWizard(
                                    initialDimensions = DiscoveryDimensions.fromIdealSelfProfile(
                                        companionState.idealSelfProfile,
                                        companionState.personalityTraits
                                    ),
                                    companionState = companionState,
                                    isSynthesizing = isQuizSynthesizing,
                                    onSaveProgress = onSaveDiscoveryProgress,
                                    onSynthesize = onSynthesizeDiscovery,
                                    onBackToWelcome = { mode = OnboardingMode.WELCOME_CHOICE }
                                )
                            }
                            OnboardingMode.DIRECT_CREATION -> {
                                DirectCreationStudioCard(
                                    initialState = companionState,
                                    onCompleteDirectCreation = onCompleteDirectCreation,
                                    onBack = { mode = OnboardingMode.WELCOME_CHOICE }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingWelcomeChoiceCard(
    companionState: CompanionState,
    onStartGuidedDiscovery: () -> Unit,
    onStartDirectCreation: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Text(
                    text = tr("PİKSEL YOLDAŞ STÜDYOSU", "PIXEL COMPANION STUDIO"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LanguageToggleChip(testTagName = "onboarding_language_toggle_button")
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text(tr("Şimdilik Geç", "Skip for now"))
                }
            }
        }

        PixelCharacterSprite(
            appearance = companionState.characterAppearance,
            clothing = companionState.clothingAndAccessories,
            visualState = CharacterVisualState.IDLE,
            activeEmotion = companionState.characterAppearance.expressionDefault,
            modifier = Modifier
                .size(210.dp)
                .testTag("onboarding_pixel_preview")
        )

        Text(
            text = tr("2D Piksel Sanatı ile İdeal Benliğinle Tanış", "Meet Your Ideal Self in 2D Pixel Art"),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = tr(
                "Taşımak istediğin nitelikleri, değerleri ve düşünce yapısını keşfet—ardından gerçek zamanlı ses veya metinle konuşabileceğin kişisel piksel yoldaşına dönüştür.",
                "Discover the qualities, values, and mindset you aspire to embody—then bring them to life as a personal pixel companion you can talk with through real-time voice or text."
            ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Option A: Guided Discovery
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = tr("Seçenek A · Rehberli Keşif", "Option A · Guided Discovery"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = tr(
                        "İdeal kişiliğini, değerlerini, özgüvenini, mizahını ve hedeflerini adım adım keşfet. İstediğin adımı atla veya düzenle—YZ profilini oluştursun ve uyumlu piksel görünümü önersin.",
                        "A calm, conversational exploration of your ideal personality, values, confidence, humor, and ambitions. Skip or edit any step—AI crafts your profile and recommends a matching pixel look."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onStartGuidedDiscovery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_guided_discovery_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = tr("Rehberli Keşfi Başlat", "Start Guided Discovery"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Option B: Direct Creation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = tr("Seçenek B · Doğrudan Oluşturma", "Option B · Direct Creation"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = tr(
                        "Yoldaşının kim olacağını zaten biliyor musun? Adını ver, sıra dışı bir kombin seç ve temel hedefini tek ekranda belirle.",
                        "Already know who your companion is? Name them, pick an outfit preset, and set your core aspiration directly in one screen."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onStartDirectCreation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("onboarding_direct_creation_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = tr("Doğrudan Oluştur", "Create Directly"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidedDiscoveryWizard(
    initialDimensions: DiscoveryDimensions,
    companionState: CompanionState,
    isSynthesizing: Boolean,
    onSaveProgress: (DiscoveryDimensions) -> Unit,
    onSynthesize: (DiscoveryDimensions) -> Unit,
    onBackToWelcome: (() -> Unit)? = null
) {
    var stepIndex by rememberSaveable {
        mutableIntStateOf(initialDimensions.savedStepIndex.coerceIn(0, DISCOVERY_STEPS.lastIndex))
    }

    var desiredPersonality by rememberSaveable { mutableStateOf(initialDimensions.desiredPersonality) }
    var personalValues by rememberSaveable { mutableStateOf(initialDimensions.personalValues) }
    var confidence by rememberSaveable { mutableStateOf(initialDimensions.confidence) }
    var socialBehavior by rememberSaveable { mutableStateOf(initialDimensions.socialBehavior) }
    var independence by rememberSaveable { mutableStateOf(initialDimensions.independence) }
    var humor by rememberSaveable { mutableStateOf(initialDimensions.humor) }
    var emotionalResponses by rememberSaveable { mutableStateOf(initialDimensions.emotionalResponses) }
    var worldview by rememberSaveable { mutableStateOf(initialDimensions.worldview) }
    var ambitions by rememberSaveable { mutableStateOf(initialDimensions.ambitions) }
    var relationships by rememberSaveable { mutableStateOf(initialDimensions.relationships) }
    var communicationStyle by rememberSaveable { mutableStateOf(initialDimensions.communicationStyle) }

    fun buildCurrentDimensions(targetStep: Int = stepIndex): DiscoveryDimensions = DiscoveryDimensions(
        desiredPersonality = desiredPersonality,
        personalValues = personalValues,
        confidence = confidence,
        socialBehavior = socialBehavior,
        independence = independence,
        humor = humor,
        emotionalResponses = emotionalResponses,
        worldview = worldview,
        ambitions = ambitions,
        relationships = relationships,
        communicationStyle = communicationStyle,
        savedStepIndex = targetStep.coerceIn(0, DISCOVERY_STEPS.lastIndex)
    )

    val currentStep = DISCOVERY_STEPS[stepIndex]
    val currentAnswer = when (stepIndex) {
        0 -> desiredPersonality
        1 -> personalValues
        2 -> socialBehavior
        3 -> ambitions
        else -> communicationStyle
    }

    fun updateCurrentAnswer(newValue: String) {
        when (stepIndex) {
            0 -> {
                desiredPersonality = newValue
                confidence = newValue
            }
            1 -> personalValues = newValue
            2 -> {
                socialBehavior = newValue
                humor = newValue
            }
            3 -> {
                ambitions = newValue
                emotionalResponses = newValue
            }
            else -> {
                communicationStyle = newValue
                relationships = newValue
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("guided_discovery_wizard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top bar with Back / Progress / Save Progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBackToWelcome != null) {
                    TextButton(onClick = onBackToWelcome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tr("Giriş", "Intro"))
                    }
                } else {
                    Text(
                        text = tr("İDEAL BENLİK KEŞFİ", "IDEAL SELF DISCOVERY"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = tr(
                        "Soru ${stepIndex + 1}/${DISCOVERY_STEPS.size} (Step ${stepIndex + 1} of ${DISCOVERY_STEPS.size})",
                        "Step ${stepIndex + 1} of ${DISCOVERY_STEPS.size}"
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("discovery_step_indicator")
                )

                TextButton(
                    onClick = { onSaveProgress(buildCurrentDimensions(stepIndex)) },
                    modifier = Modifier.testTag("discovery_save_progress_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = tr("İlerlemeyi Kaydet", "Save Progress"),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Kaydet", "Save"))
                }
            }

            LinearProgressIndicator(
                progress = { (stepIndex + 1).toFloat() / DISCOVERY_STEPS.size.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            // Step pill jumps so user can jump back or ahead to edit any question
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DISCOVERY_STEPS.forEach { step ->
                    val isCurrent = step.index == stepIndex
                    Surface(
                        onClick = { stepIndex = step.index },
                        shape = RoundedCornerShape(50),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.testTag("discovery_step_chip_${step.index}")
                    ) {
                        Text(
                            text = "${step.index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Companion conversational prompt bubble
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PixelCharacterSprite(
                    appearance = companionState.characterAppearance,
                    clothing = companionState.clothingAndAccessories,
                    visualState = if (isSynthesizing) CharacterVisualState.THINKING else CharacterVisualState.LISTENING,
                    activeEmotion = "curious",
                    modifier = Modifier.size(76.dp),
                    showStudioFrame = true
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = tr(currentStep.trTitle, currentStep.title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = tr(currentStep.trQuestion, currentStep.companionQuestion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Quick inspiration chips
            val activeSuggestions = if (LocalAppLanguage.current == AppLanguage.TR) {
                currentStep.trSuggestions
            } else {
                currentStep.suggestions
            }
            Text(
                text = tr("Bir ilham seçin veya kendi cevabınızı yazın:", "Tap an inspiration or write your own:"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activeSuggestions.forEachIndexed { idx, suggestion ->
                    val selected = currentAnswer.trim() == suggestion
                    Surface(
                        onClick = { updateCurrentAnswer(suggestion) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("discovery_suggestion_$idx")
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = currentAnswer,
                onValueChange = { updateCurrentAnswer(it) },
                label = { Text(tr("Cevabınız (her zaman düzenlenebilir)", "Your answer (editable anytime)")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discovery_answer_input"),
                minLines = 2
            )

            // Navigation row: Previous, Skip, Next / Finish
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { if (stepIndex > 0) stepIndex -= 1 },
                    enabled = stepIndex > 0 && !isSynthesizing,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("discovery_prev_step_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Geri", "Back"))
                }

                if (stepIndex < DISCOVERY_STEPS.lastIndex) {
                    OutlinedButton(
                        onClick = { stepIndex += 1 },
                        enabled = !isSynthesizing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("discovery_skip_step_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Skip", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tr("Atla", "Skip"))
                    }

                    Button(
                        onClick = { stepIndex += 1 },
                        enabled = !isSynthesizing,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("discovery_next_step_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(tr("İleri", "Next"))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Synthesize button (always accessible so users are never forced through all 11 if they want to finish early)
            Button(
                onClick = { onSynthesize(buildCurrentDimensions(stepIndex)) },
                enabled = !isSynthesizing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("synthesize_quiz_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isSynthesizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(tr("İdeal Benlik Profili Hazırlanıyor...", "Crafting Ideal-Self Profile..."))
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (stepIndex == DISCOVERY_STEPS.lastIndex) {
                            tr("İdeal Benlik Profilini Oluştur", "Generate Ideal-Self Profile")
                        } else {
                            tr(
                                "Profili Şimdi Oluştur (${stepIndex + 1}/${DISCOVERY_STEPS.size})",
                                "Synthesize Profile Now (${stepIndex + 1}/${DISCOVERY_STEPS.size})"
                            )
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryProposalApprovalCard(
    proposal: CompanionState,
    onApprove: (CompanionState) -> Unit,
    onBackToEdit: () -> Unit
) {
    var companionName by remember(proposal) { mutableStateOf(proposal.characterIdentity.companionName) }
    var archetypeTitle by remember(proposal) { mutableStateOf(proposal.characterIdentity.archetypeTitle) }
    var coreAspiration by remember(proposal) { mutableStateOf(proposal.idealSelfProfile.coreAspiration) }
    var signatureCatchphrase by remember(proposal) { mutableStateOf(proposal.characterIdentity.signatureCatchphrase) }
    var pillarsText by remember(proposal) {
        mutableStateOf(proposal.idealSelfProfile.lifePillars.joinToString(", "))
    }
    var draftAppearance by remember(proposal) { mutableStateOf(proposal.characterAppearance) }
    var draftClothing by remember(proposal) { mutableStateOf(proposal.clothingAndAccessories) }
    var styleVariation by remember { mutableIntStateOf(1) }

    val stylePreview = remember(proposal.idealSelfProfile, proposal.personalityTraits, draftAppearance, draftClothing, styleVariation) {
        PixelAssetCatalog.recommendVisualsForProfile(
            idealSelf = proposal.idealSelfProfile,
            traits = proposal.personalityTraits,
            currentAppearance = draftAppearance,
            currentClothing = draftClothing,
            variationIndex = styleVariation - 1
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovery_proposal_approval_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "REVIEW & APPROVE · ${stylePreview.dualityLabel.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            PixelCharacterSprite(
                appearance = draftAppearance,
                clothing = draftClothing,
                visualState = CharacterVisualState.REACTING,
                activeEmotion = draftAppearance.expressionDefault,
                modifier = Modifier
                    .size(185.dp)
                    .testTag("proposal_pixel_preview")
            )

            Text(
                text = stylePreview.rationale,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Presentation Silhouette + One-Tap Regenerate Look controls
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelAssetCatalog.presentationStyles.forEach { option ->
                    FilterChip(
                        selected = draftAppearance.presentationId == option.id,
                        onClick = { draftAppearance = draftAppearance.withPresentationId(option.id) },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("proposal_presentation_${option.id}")
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    val nextLook = PixelAssetCatalog.recommendVisualsForProfile(
                        idealSelf = proposal.idealSelfProfile,
                        traits = proposal.personalityTraits,
                        currentAppearance = draftAppearance,
                        currentClothing = draftClothing,
                        variationIndex = styleVariation
                    )
                    draftAppearance = nextLook.recommendedAppearance
                    draftClothing = nextLook.recommendedClothing
                    styleVariation += 1
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proposal_regenerate_look_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    tr(
                        "Sıra Dışı Görünümü Yeniden Üret (Varyant #$styleVariation)",
                        "Regenerate Unconventional Look (Variant #$styleVariation)"
                    )
                )
            }

            OutlinedTextField(
                value = companionName,
                onValueChange = { companionName = it },
                label = { Text(tr("Yoldaş Adı", "Companion Name")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proposal_name_input")
            )

            OutlinedTextField(
                value = archetypeTitle,
                onValueChange = { archetypeTitle = it },
                label = { Text(tr("İdeal Benlik Arketipi", "Ideal-Self Archetype")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proposal_archetype_input")
            )

            OutlinedTextField(
                value = coreAspiration,
                onValueChange = { coreAspiration = it },
                label = { Text(tr("Temel İdeal Benlik Sentezi", "Core Ideal-Self Synthesis")) },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proposal_aspiration_input")
            )

            OutlinedTextField(
                value = pillarsText,
                onValueChange = { pillarsText = it },
                label = { Text(tr("Temel Değerler (virgülle ayrılmış)", "Core Values & Pillars (comma-separated)")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proposal_pillars_input")
            )

            OutlinedTextField(
                value = signatureCatchphrase,
                onValueChange = { signatureCatchphrase = it },
                label = { Text(tr("İmza Mottosu", "Signature Motto")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proposal_motto_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBackToEdit,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("edit_discovery_answers_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Adımlara Dön", "Back to Steps"))
                }

                Button(
                    onClick = {
                        val updatedPillars = pillarsText.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .ifEmpty { proposal.idealSelfProfile.lifePillars }
                            .take(8)
                        val edited = proposal.copy(
                            characterIdentity = proposal.characterIdentity.copy(
                                companionName = companionName.trim().ifBlank { "Nova" }.take(60),
                                archetypeTitle = archetypeTitle.trim().ifBlank { "The Calm Architect" }.take(100),
                                signatureCatchphrase = signatureCatchphrase.trim().take(200)
                            ),
                            idealSelfProfile = proposal.idealSelfProfile.copy(
                                coreAspiration = coreAspiration.trim().ifBlank { proposal.idealSelfProfile.coreAspiration }.take(500),
                                lifePillars = updatedPillars
                            ),
                            characterAppearance = draftAppearance,
                            clothingAndAccessories = draftClothing
                        )
                        onApprove(edited)
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(50.dp)
                        .testTag("approve_generated_profile_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("Profili Onayla", "Approve Profile"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectCreationStudioCard(
    initialState: CompanionState,
    onCompleteDirectCreation: (
        companionName: String,
        archetypeTitle: String,
        coreAspiration: String,
        lifePillars: List<String>,
        appearance: CharacterAppearance,
        clothing: ClothingAndAccessories
    ) -> Unit,
    onBack: () -> Unit
) {
    var companionName by rememberSaveable { mutableStateOf(initialState.characterIdentity.companionName) }
    var archetypeTitle by rememberSaveable { mutableStateOf(initialState.characterIdentity.archetypeTitle) }
    var coreAspiration by rememberSaveable { mutableStateOf(initialState.idealSelfProfile.coreAspiration) }
    var pillarsInput by rememberSaveable {
        mutableStateOf(initialState.idealSelfProfile.lifePillars.joinToString(", "))
    }
    var appearance by remember { mutableStateOf(initialState.characterAppearance) }
    var clothing by remember { mutableStateOf(initialState.clothingAndAccessories) }
    var selectedComboId by rememberSaveable { mutableStateOf("rebel_counter_philosopher") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("direct_creation_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Geri", "Back"))
                }
                Text(
                    text = tr("DOĞRUDAN STÜDYO OLUŞTURMA", "DIRECT STUDIO CREATION"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            PixelCharacterSprite(
                appearance = appearance,
                clothing = clothing,
                visualState = CharacterVisualState.IDLE,
                activeEmotion = appearance.expressionDefault,
                modifier = Modifier
                    .size(180.dp)
                    .testTag("direct_creation_pixel_preview")
            )

            Text(
                text = tr("Sunum Silüeti:", "Presentation Silhouette:"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelAssetCatalog.presentationStyles.forEach { option ->
                    FilterChip(
                        selected = appearance.presentationId == option.id,
                        onClick = { appearance = appearance.withPresentationId(option.id) },
                        label = { Text(option.label) },
                        modifier = Modifier.testTag("direct_presentation_${option.id}")
                    )
                }
            }

            Text(
                text = tr("Sıra Dışı Bir Kombin Seçin:", "Pick an Unconventional Lookbook Preset:"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PixelAssetCatalog.outfitComboPresets.forEach { combo ->
                    FilterChip(
                        selected = selectedComboId == combo.id,
                        onClick = {
                            selectedComboId = combo.id
                            clothing = clothing.copy(
                                outfitId = combo.outfitId,
                                accessoryId = combo.accessoryId,
                                auraEffectId = combo.auraEffectId,
                                primaryColorHex = combo.primaryColorHex,
                                accentColorHex = combo.accentColorHex
                            )
                        },
                        label = { Text(combo.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        ),
                        modifier = Modifier.testTag("direct_combo_${combo.id}")
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
                    .testTag("direct_name_input")
            )

            OutlinedTextField(
                value = archetypeTitle,
                onValueChange = { archetypeTitle = it },
                label = { Text(tr("Arketip Başlığı", "Archetype Title")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("direct_archetype_input")
            )

            OutlinedTextField(
                value = coreAspiration,
                onValueChange = { coreAspiration = it },
                label = { Text(tr("Temel İdeal Benlik Hedefi", "Core Ideal-Self Aspiration")) },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("direct_aspiration_input")
            )

            OutlinedTextField(
                value = pillarsInput,
                onValueChange = { pillarsInput = it },
                label = { Text(tr("Kişisel Değerler (virgülle ayrılmış)", "Personal Values (comma-separated)")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("direct_pillars_input")
            )

            Button(
                onClick = {
                    val pillars = pillarsInput.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onCompleteDirectCreation(
                        companionName,
                        archetypeTitle,
                        coreAspiration,
                        pillars,
                        appearance,
                        clothing
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("direct_creation_submit_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tr("Yoldaşımı Başlat", "Launch My Companion"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
