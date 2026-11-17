package com.example

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CharacterAppearance
import com.example.data.model.ClothingAndAccessories
import com.example.data.model.CompanionState
import com.example.data.model.DiscoveryDimensions
import com.example.data.model.Message
import com.example.data.model.VoicePreferences
import com.example.ui.screens.CharacterScreen
import com.example.ui.screens.CharacterSubSection
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.OnboardingFlowScreen
import com.example.ui.screens.TextConversationScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AiTurnState
import com.example.voice.VoicePhase
import com.example.voice.VoiceSessionController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Ideal Self", appName)
    }

    @Test
    fun `home screen renders pixel companion, primary talk action, and triggers reaction`() {
        var voiceStarted = false
        var textStarted = false
        var reactionTriggered = false

        composeTestRule.setContent {
            MyApplicationTheme {
                HomeScreen(
                    companionState = CompanionState(),
                    conversations = emptyList(),
                    memories = emptyList(),
                    activeEmotion = "warm_smile",
                    onTriggerReaction = { reactionTriggered = true },
                    onStartVoiceSession = { voiceStarted = true },
                    onStartNewTextChat = { textStarted = true },
                    onOpenConversation = {},
                    onDeleteConversation = {},
                    onOpenSettingsAndPrivacy = {},
                    onJumpToDiscoveryQuiz = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("home_pixel_character_sprite").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_voice_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(voiceStarted)

        composeTestRule.onNodeWithTag("start_text_chat_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(textStarted)

        composeTestRule.onNodeWithTag("home_greet_reaction_button")
            .performScrollTo()
            .performClick()
        assertTrue(reactionTriggered)
    }

    @Test
    fun `onboarding flow supports guided discovery 5 steps, skip, back, save progress, and profile approval`() {
        var savedProgress: DiscoveryDimensions? = null
        var synthesizedDimensions: DiscoveryDimensions? = null
        var approvedState: CompanionState? = null
        var pendingProposal by mutableStateOf<CompanionState?>(null)

        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingFlowScreen(
                    companionState = CompanionState(),
                    isQuizSynthesizing = false,
                    pendingProposal = pendingProposal,
                    onSaveDiscoveryProgress = { savedProgress = it },
                    onSynthesizeDiscovery = { dims ->
                        synthesizedDimensions = dims
                        pendingProposal = CompanionState(
                            idealSelfProfile = dims.toIdealSelfProfile(quizCompleted = true)
                        )
                    },
                    onApproveProposal = { approvedState = it },
                    onDiscardProposal = { pendingProposal = null },
                    onCompleteDirectCreation = { _, _, _, _, _, _ -> },
                    onSkipOnboarding = {}
                )
            }
        }

        // Start Guided Discovery
        composeTestRule.onNodeWithTag("onboarding_guided_discovery_button")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithTag("discovery_step_indicator")
            .assertTextContains("Step 1 of 5", substring = true)

        // Edit answer on Step 1
        composeTestRule.onNodeWithTag("discovery_answer_input")
            .performScrollTo()
            .performTextReplacement("Calm, creative, and deeply intentional")

        // Skip to Step 2
        composeTestRule.onNodeWithTag("discovery_skip_step_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("discovery_step_indicator")
            .assertTextContains("Step 2 of 5", substring = true)

        // Go back to Step 1
        composeTestRule.onNodeWithTag("discovery_prev_step_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("discovery_step_indicator")
            .assertTextContains("Step 1 of 5", substring = true)

        // Jump to Step 5 via step pill and save progress
        composeTestRule.onNodeWithTag("discovery_step_chip_4")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("discovery_step_indicator")
            .assertTextContains("Step 5 of 5", substring = true)

        composeTestRule.onNodeWithTag("discovery_save_progress_button")
            .performScrollTo()
            .performClick()
        assertNotNull(savedProgress)
        assertEquals(4, savedProgress?.savedStepIndex)
        assertEquals("Calm, creative, and deeply intentional", savedProgress?.desiredPersonality)

        // Synthesize profile and verify Proposal Approval view appears
        composeTestRule.onNodeWithTag("synthesize_quiz_button")
            .performScrollTo()
            .performClick()
        assertNotNull(synthesizedDimensions)

        composeTestRule.onNodeWithTag("discovery_proposal_approval_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("proposal_name_input")
            .performScrollTo()
            .performTextReplacement("Aether")

        composeTestRule.onNodeWithTag("approve_generated_profile_button")
            .performScrollTo()
            .performClick()

        assertNotNull(approvedState)
        assertEquals("Aether", approvedState?.characterIdentity?.companionName)
    }

    @Test
    fun `onboarding flow supports direct creation`() {
        var directCreatedName = ""
        composeTestRule.setContent {
            MyApplicationTheme {
                OnboardingFlowScreen(
                    companionState = CompanionState(),
                    isQuizSynthesizing = false,
                    pendingProposal = null,
                    onSaveDiscoveryProgress = {},
                    onSynthesizeDiscovery = {},
                    onApproveProposal = {},
                    onDiscardProposal = {},
                    onCompleteDirectCreation = { name, _, _, _, _, _ ->
                        directCreatedName = name
                    },
                    onSkipOnboarding = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("onboarding_direct_creation_button")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithTag("direct_name_input")
            .performScrollTo()
            .performTextReplacement("Solstice")

        composeTestRule.onNodeWithTag("direct_creation_submit_button")
            .performScrollTo()
            .performClick()

        assertEquals("Solstice", directCreatedName)
    }

    @Test
    fun `character studio supports pinned preview, 5 visual categories grid, outfit combinations, and compact persona sheet`() {
        var updatedAppearance: CharacterAppearance? = null
        var updatedClothing: ClothingAndAccessories? = null
        var updatedVoicePreferences: com.example.data.model.VoicePreferences? = null
        var aiLookApplied = false

        composeTestRule.setContent {
            MyApplicationTheme {
                CharacterScreen(
                    companionState = CompanionState(),
                    initialSubSection = CharacterSubSection.APPEARANCE,
                    isQuizSynthesizing = false,
                    onSaveAppearance = { updatedAppearance = it },
                    onSaveClothing = { updatedClothing = it },
                    onSaveAppearanceAndClothing = { app, cloth, _ ->
                        updatedAppearance = app
                        updatedClothing = cloth
                    },
                    onSaveVoicePreferences = { updatedVoicePreferences = it },
                    onApplyAiVisualRecommendation = { aiLookApplied = true },
                    onSaveIdentityAndProfile = { _, _, _ -> }
                )
            }
        }

        // Verify pinned character preview is displayed at the top focal point
        composeTestRule.onNodeWithTag("character_customizer_preview").assertIsDisplayed()

        // Test visual state switcher pills
        composeTestRule.onNodeWithTag("preview_state_speaking").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("preview_state_reacting").performScrollTo().performClick()

        // Lock Hair slot and apply style suggestion
        composeTestRule.onNodeWithTag("lock_slot_hair").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("apply_ai_look_button").performClick()
        assertTrue(aiLookApplied)

        // Category 1: Silüet — select Masculine presentation card in the visual grid
        composeTestRule.onNodeWithTag("pixel_opt_masculine").performScrollTo().performClick()
        assertEquals("masculine", updatedAppearance?.presentationId)

        // Verify "Persona ile Eşleştir" matches the Masculine silhouette to DEEP_RESONANT voice tone
        composeTestRule.onNodeWithTag("match_persona_voice_button").performScrollTo().performClick()
        assertEquals(
            com.example.data.model.CompanionVoiceTone.DEEP_RESONANT,
            updatedVoicePreferences?.voiceTone
        )

        // Explicitly select VELVET_REFLECTIVE voice tone preset card
        composeTestRule.onNodeWithTag("voice_tone_card_velvet_reflective").performScrollTo().performClick()
        assertEquals(
            com.example.data.model.CompanionVoiceTone.VELVET_REFLECTIVE,
            updatedVoicePreferences?.voiceTone
        )

        // Category 2: Saç — switch to Hair tab and select a hairstyle preview card
        composeTestRule.onNodeWithTag("visual_cat_hair").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pixel_opt_starlight_bob").performScrollTo().performClick()
        assertEquals("starlight_bob", updatedAppearance?.hairStyleId)

        // Category 3: Gözlük — switch to Eyewear tab and select a face feature card
        composeTestRule.onNodeWithTag("visual_cat_eyewear").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pixel_opt_star_mark").performScrollTo().performClick()
        assertEquals("star_mark", updatedAppearance?.faceFeatureId)

        // Category 4: Kıyafet — switch to Outfit tab, apply a curated outfit combination, and customize trousers + boots
        composeTestRule.onNodeWithTag("character_tab_outfit").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("outfit_combo_midnight_scholar").performScrollTo().performClick()
        assertEquals("#38BDF8", updatedClothing?.primaryColorHex)

        composeTestRule.onNodeWithTag("pixel_opt_tartan_puk").performScrollTo().performClick()
        assertEquals("tartan_puk", updatedClothing?.trousersId)

        composeTestRule.onNodeWithTag("pixel_opt_chelsea_bt").performScrollTo().performClick()
        assertEquals("chelsea_bt", updatedClothing?.footwearId)

        // Category 5: İfadeler — switch to Expressions tab and select an expression preview card
        composeTestRule.onNodeWithTag("visual_cat_expressions").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pixel_opt_curious").performScrollTo().performClick()
        assertEquals("curious", updatedAppearance?.expressionDefault)

        // Test discreet Reset button restores character to its default appearance and clothing state
        composeTestRule.onNodeWithTag("reset_character_default_button").performClick()
        assertEquals(CharacterAppearance(), updatedAppearance)
        assertEquals(ClothingAndAccessories(), updatedClothing)

        // Open compact Kişilik sheet from the top bar and verify persona summary card is rendered
        composeTestRule.onNodeWithTag("character_tab_identity").performClick()
        composeTestRule.onNodeWithTag("structured_personality_model_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `text conversation screen renders message timestamps and supports error recovery retry`() {
        var retried = false
        var sentPrompt = ""

        val sampleMessages = listOf(
            Message(
                id = "msg_1",
                sender = "USER",
                content = "Bugün kafam biraz karışık, sence nereden başlayayım?"
            ),
            Message(
                id = "msg_2",
                sender = "COMPANION",
                content = "Önce derin bir nefes al. Her şeyi aynı anda çözmek zorunda değilsin; bugün tek bir somut adım seçelim."
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                TextConversationScreen(
                    companionState = CompanionState(),
                    messages = sampleMessages,
                    aiTurnState = AiTurnState.Error("Network timeout while streaming response"),
                    activeEmotion = "encouraging",
                    lastExtractedMemory = "[OPINION_SHIFT] Prefers single-task focus over multitasking",
                    onSendMessage = { sentPrompt = it },
                    onRetryFailedMessage = { retried = true },
                    onSwitchToVoice = {},
                    onBack = {}
                )
            }
        }

        // Verify timestamps are displayed on message bubbles
        composeTestRule.onNodeWithTag("text_message_list")
            .performScrollToNode(hasTestTag("message_timestamp_msg_1"))
        composeTestRule.onNodeWithTag("message_timestamp_msg_1").assertIsDisplayed()

        composeTestRule.onNodeWithTag("text_message_list")
            .performScrollToNode(hasTestTag("message_timestamp_msg_2"))
        composeTestRule.onNodeWithTag("message_timestamp_msg_2").assertIsDisplayed()

        // Verify memory extraction banner is displayed
        composeTestRule.onNodeWithTag("memory_extracted_banner").assertIsDisplayed()

        // Verify error recovery card and retry button work
        composeTestRule.onNodeWithTag("text_message_list")
            .performScrollToNode(hasTestTag("text_error_recovery_card"))
        composeTestRule.onNodeWithTag("text_error_recovery_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("retry_text_message_button").performClick()
        assertTrue(retried)

        // Verify Turkish conversation starter chip works
        composeTestRule.onNodeWithTag("quick_prompt_chip_0").performClick()
        assertTrue(sentPrompt.contains("Bugün kafam biraz karışık"))
    }

    @Test
    fun `voice session controller handles Turkish turn, streaming sentence chunks, barge-in interruption, and phone call audio focus loss`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var interruptedGenerationCount = 0
        var finalizedTurkishUtterance = ""

        val controller = VoiceSessionController(
            context = context,
            onInterruptActiveGeneration = { interruptedGenerationCount++ },
            onUserUtteranceFinalized = { utterance -> finalizedTurkishUtterance = utterance }
        )

        controller.onMicrophonePermissionResult(true)
        controller.startSession(VoicePreferences())
        assertEquals("tr-TR", controller.state.value.voiceLanguageTag)

        // 0. Verify partial speech capture is preserved on timeout/noise and immediately sent on tap (startListeningManual / commitPendingTranscript)
        controller.onPartialTranscriptCaptured("Bugün odaklanmakta zorlanıyorum")
        assertTrue(controller.state.value.hasPendingDraftTranscript)
        assertEquals("Bugün odaklanmakta zorlanıyorum", controller.state.value.liveTranscript)

        // Simulate noise or speech pause timeout -> partial text must be preserved, not erased
        controller.onRecognitionIncompleteOrInterrupted(android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertEquals("Bugün odaklanmakta zorlanıyorum", controller.state.value.liveTranscript)
        assertTrue(controller.state.value.hasPendingDraftTranscript)

        // Tapping primary mic button (startListeningManual) when partial text exists immediately commits & sends it
        controller.startListeningManual()
        assertEquals("Bugün odaklanmakta zorlanıyorum", finalizedTurkishUtterance)
        assertEquals(VoicePhase.THINKING, controller.state.value.phase)

        // 1. Submit another Turkish spoken turn
        controller.submitSpokenOrTypedTurn("Selam Nova, bugün neden bu kadar erteleme yapıyorum sence?")
        assertEquals("Selam Nova, bugün neden bu kadar erteleme yapıyorum sence?", finalizedTurkishUtterance)
        assertEquals(VoicePhase.THINKING, controller.state.value.phase)

        // 2. Stream first sentence chunk -> transitions immediately to SPEAKING and records latency
        controller.onStreamingChunkReceived(
            accumulatedText = "Çünkü mükemmel olmasını bekliyorsun. ",
            deltaText = "Çünkü mükemmel olmasını bekliyorsun. ",
            firstTokenLatencyMs = 210L
        )
        assertEquals(VoicePhase.SPEAKING, controller.state.value.phase)
        assertEquals(210L, controller.state.value.firstTokenLatencyMs)
        assertTrue(controller.state.value.firstAudioLatencyMs >= 1L)

        // 3. Test user barge-in interruption while companion is speaking
        val countBeforeBargeIn = interruptedGenerationCount
        controller.interruptCompanionSpeech()
        assertEquals(countBeforeBargeIn + 1, interruptedGenerationCount)
        assertEquals(1, controller.state.value.interruptionCount)

        // 4. Test incoming phone call / transient audio focus loss -> pauses cleanly (no fake active call)
        controller.handleAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        assertEquals(VoicePhase.PAUSED, controller.state.value.phase)

        // 5. Test microphone permission denial recovery state
        controller.onMicrophonePermissionResult(false)
        assertEquals(false, controller.state.value.micPermissionGranted)
        assertTrue(controller.state.value.statusMessage.contains("Mikrofon izni"))

        // 6. Test explicit voice-speed multiplier adjustment keeps bold/confident tone punchy
        val updatedVoicePrefs = controller.setSpeechSpeedMultiplier(1.20f)
        assertEquals(1.20f, controller.state.value.speechSpeedMultiplier, 0.01f)
        assertEquals(com.example.data.model.VoiceSpeedMultiplier.BOLD_120, updatedVoicePrefs.speedMultiplierPreset)

        controller.release()
    }

    @Test
    fun `settings and privacy sheet supports simplified essential controls, memory editing, deletion, and cloud data deletion`() {
        var addedMemorySummary = ""
        var updatedMemorySummary = ""
        var deletedMemoryId = ""
        var clearedAllMemories = false
        var deletedAllUserData = false
        var voicePrefsSaved = false

        val existingMemories = listOf(
            com.example.data.model.LongTermMemory(
                id = "mem_1",
                category = "GOAL",
                summary = "[GOAL] Ship native pixel companion app",
                importance = 5
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                com.example.ui.screens.SettingsAndPrivacySheet(
                    companionState = CompanionState(),
                    memories = existingMemories,
                    onUpdateVoicePreferences = { voicePrefsSaved = true },
                    onUpdatePrivacySettings = {},
                    onAddManualMemory = { _, text, _ -> addedMemorySummary = text },
                    onUpdateMemory = { _, _, text, _ -> updatedMemorySummary = text },
                    onDeleteMemory = { deletedMemoryId = it },
                    onClearAllMemories = { clearedAllMemories = true },
                    onDeleteAllUserData = { deletedAllUserData = true },
                    onSignOut = {},
                    onDismiss = {}
                )
            }
        }

        // 1. Save voice preferences
        composeTestRule.onNodeWithTag("save_voice_preferences_button")
            .performScrollTo()
            .performClick()
        assertTrue(voicePrefsSaved)

        // 2. Add manual memory
        composeTestRule.onNodeWithTag("manual_memory_input")
            .performScrollTo()
            .performTextReplacement("Practice 20 minutes of Turkish reflection daily")
        composeTestRule.onNodeWithTag("add_manual_memory_button")
            .performScrollTo()
            .performClick()
        assertEquals("Practice 20 minutes of Turkish reflection daily", addedMemorySummary)

        // 3. Edit existing memory inline and save
        composeTestRule.onNodeWithTag("edit_memory_mem_1")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("edit_memory_input_mem_1")
            .performScrollTo()
            .performTextReplacement("[GOAL] Ship v1.0 on Android and iOS")
        composeTestRule.onNodeWithTag("save_edited_memory_mem_1")
            .performScrollTo()
            .performClick()
        assertEquals("[GOAL] Ship v1.0 on Android and iOS", updatedMemorySummary)

        // 4. Delete individual memory & Clear All memories
        composeTestRule.onNodeWithTag("delete_memory_mem_1")
            .performScrollTo()
            .performClick()
        assertEquals("mem_1", deletedMemoryId)

        composeTestRule.onNodeWithTag("clear_all_memories_button")
            .performScrollTo()
            .performClick()
        assertTrue(clearedAllMemories)

        // 5. Verify Permanent Cloud Data Deletion two-step confirmation
        composeTestRule.onNodeWithTag("delete_all_user_data_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("confirm_delete_all_data_button")
            .performScrollTo()
            .performClick()
        assertTrue(deletedAllUserData)
    }

    @Test
    fun `automatic web lookup detection and fast first-clause streaming chunker optimize conversation latency`() {
        // Verify factual & current queries trigger web lookup in both TR and EN
        assertTrue(com.example.ai.PersonalityEngine.requiresWebLookup("Bugün İstanbul'da hava durumu nasıl?"))
        assertTrue(com.example.ai.PersonalityEngine.requiresWebLookup("Güncel teknoloji haberleri neler?"))
        assertTrue(com.example.ai.PersonalityEngine.requiresWebLookup("What is the latest news about space exploration?"))

        // Verify casual banter & personal reflections bypass web lookup for maximum speed
        org.junit.Assert.assertFalse(com.example.ai.PersonalityEngine.requiresWebLookup("Selam Nova, bugün kafam biraz karışık."))
        org.junit.Assert.assertFalse(com.example.ai.PersonalityEngine.requiresWebLookup("Hey, I feel a bit overwhelmed today."))

        // Verify SentenceStreamChunker flushes the very first clause on an early comma pause (>=18 chars)
        val chunker = com.example.voice.SentenceStreamChunker()
        val earlyClauses = chunker.extractReadyClauses(
            accumulatedText = "Selam dostum bakıyorum yine, her şeyi aynı anda çözmeye çalışıyorsun",
            isStreamFinished = false
        )
        assertEquals(1, earlyClauses.size)
        assertEquals("Selam dostum bakıyorum yine,", earlyClauses.first())
    }

    @Test
    fun `auth screen supports guest login quick test access and Turkish-English language toggle`() {
        var guestContinued = false
        var appLanguage by mutableStateOf(com.example.ui.theme.AppLanguage.TR)

        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                com.example.ui.theme.LocalAppLanguage provides appLanguage,
                com.example.ui.theme.LocalOnToggleLanguage provides { appLanguage = appLanguage.toggled() }
            ) {
                MyApplicationTheme {
                    com.example.ui.screens.AuthScreen(
                        onAuthSuccess = {},
                        onContinueAsGuest = { guestContinued = true }
                    )
                }
            }
        }

        // Verify both Google Sign-In and Guest Login buttons are displayed in Turkish by default
        composeTestRule.onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Google ile Giriş Yap", substring = true)

        composeTestRule.onNodeWithTag("guest_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Misafir Olarak Devam Et", substring = true)
            .performClick()

        assertTrue(guestContinued)

        // Toggle language from TR to EN and verify button text updates
        composeTestRule.onNodeWithTag("app_language_toggle_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        assertEquals(com.example.ui.theme.AppLanguage.EN, appLanguage)
        composeTestRule.onNodeWithTag("guest_sign_in_button")
            .performScrollTo()
            .assertTextContains("Continue as Guest", substring = true)
    }

    @Test
    fun `companion repository guest mode supports profile, conversations, messages, and memories without Firebase Auth`() {
        kotlinx.coroutines.runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val guestRepo = com.example.data.repository.CompanionRepository(
                context = context,
                isGuestMode = true
            )

            val initialState = guestRepo.getOrCreateCompanionState(
                displayName = "Misafir Test Kullanıcısı",
                email = "guest@idealself.local"
            ).getOrThrow()
            assertEquals(
                com.example.data.repository.CompanionRepository.GUEST_USER_ID,
                initialState.userAccount.userId
            )
            assertTrue(initialState.userAccount.onboardingCompleted)

            val convId = guestRepo.createConversation(
                title = "Misafir Test Sohbeti",
                mode = "TEXT"
            ).getOrThrow()
            assertTrue(convId.startsWith("guest_conv_"))

            val msgId = guestRepo.addMessage(
                conversationId = convId,
                sender = "USER",
                content = "Merhaba Nova, misafir modunda test yapıyorum."
            ).getOrThrow()
            assertTrue(msgId.startsWith("guest_msg_"))

            val memId = guestRepo.addMemory(
                category = "GOAL",
                summary = "[GOAL] Misafir modunda Türkçe dil desteğini test et",
                importance = 5,
                sourceConversationId = convId
            ).getOrThrow()
            assertTrue(memId.startsWith("guest_mem_"))

            guestRepo.deleteConversation(convId).getOrThrow()

            // Verify DataStore local persistence retains customized appearance & clothing across new sessions
            val customAppearance = CharacterAppearance(
                skinToneId = "golden_tan",
                hairStyleId = "starlight_bob",
                hairColorId = "crimson_flame",
                expressionDefault = "joyful"
            ).withFaceFeature("round_tortoise").withPresentationId("feminine")
            val customClothing = ClothingAndAccessories(
                outfitId = "trench_havana:tartan_puk:chelsea_bt",
                primaryColorHex = "#38BDF8",
                accentColorHex = "#818CF8",
                accessoryId = "pixel_glasses"
            )
            val customVoice = com.example.data.model.VoicePreferences().withVoiceTone(
                com.example.data.model.CompanionVoiceTone.BRIGHT_EXPRESSIVE
            )
            guestRepo.saveCompanionState(
                initialState.copy(
                    characterAppearance = customAppearance,
                    clothingAndAccessories = customClothing,
                    voicePreferences = customVoice
                )
            ).getOrThrow()

            // Simulate navigating away and returning to a fresh repository instance
            val newSessionRepo = com.example.data.repository.CompanionRepository(
                context = context,
                isGuestMode = true
            )
            val restoredState = newSessionRepo.getOrCreateCompanionState().getOrThrow()
            assertEquals("golden_tan", restoredState.characterAppearance.skinToneId)
            assertEquals("starlight_bob", restoredState.characterAppearance.hairStyleId)
            assertEquals("round_tortoise", restoredState.characterAppearance.faceFeatureId)
            assertEquals("feminine", restoredState.characterAppearance.presentationId)
            assertEquals("tartan_puk", restoredState.clothingAndAccessories.trousersId)
            assertEquals("#38BDF8", restoredState.clothingAndAccessories.primaryColorHex)
            assertEquals(
                com.example.data.model.CompanionVoiceTone.BRIGHT_EXPRESSIVE,
                restoredState.voicePreferences.voiceTone
            )

            // Clean up DataStore after test
            newSessionRepo.deleteAllUserData().getOrThrow()
        }
    }
}


