package com.example

import com.example.ai.ConversationScenarioType
import com.example.ai.MemoryEngine
import com.example.ai.MemoryResolutionAction
import com.example.ai.PersonalityEngine
import com.example.data.model.CompanionState
import com.example.data.model.LongTermMemory
import com.example.telemetry.ProductionDiagnosticLogger
import com.example.telemetry.RateLimitAllowance
import com.example.telemetry.UsageAndCostEngine
import com.example.telemetry.UsageQuotaConfig
import com.example.voice.SentenceStreamChunker
import com.example.voice.VoiceProviderEvaluationCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `personality engine builds 12-dimension model with realistic weaknesses, challenging friendship, and Turkish detection`() {
        val state = CompanionState()
        val model = PersonalityEngine.buildStructuredModel(state)

        // Verify all 12 dimensions are populated and non-empty
        assertTrue(model.identity.contains(state.characterIdentity.companionName))
        assertTrue(model.coreValues.isNotEmpty())
        assertTrue(model.worldview.isNotBlank())
        assertTrue(model.personalityTraitsSummary.isNotBlank())
        assertTrue(model.strengths.size >= 2)
        // Must have realistic weaknesses/imperfections so the companion is never a flawless robot
        assertTrue(model.weaknesses.size >= 2)
        assertTrue(model.socialBehavior.isNotBlank())
        assertTrue(model.communicationStyle.isNotBlank())
        assertTrue(model.humor.isNotBlank())
        assertTrue(model.emotionalResponses.isNotBlank())
        assertTrue(model.relationshipPhilosophy.contains("friend", ignoreCase = true))
        assertTrue(model.boundaries.size >= 3)

        // Verify Turkish (tr-TR) language auto-detection
        assertTrue(PersonalityEngine.shouldRespondInTurkish("Bugün kafam biraz karışık, sence ne yapmalıyım?", "en-US"))
        assertTrue(PersonalityEngine.shouldRespondInTurkish("Selam dostum nasılsın", "tr-TR"))
        assertFalse(PersonalityEngine.shouldRespondInTurkish("Let's debate philosophy and science today", "en-US"))

        // Verify system directive includes Turkish directive, 12-dimension model, challenging alter-ego dynamic, and safety guardrails
        val promptTr = PersonalityEngine.buildPersonalityDirective(
            state = state,
            structuredModel = model,
            isVoiceMode = true,
            respondInTurkish = true,
            currentUserTurn = "Bence bu konuda yanılıyorsun"
        )
        assertTrue(promptTr.contains("NATURAL TURKISH - tr-TR"))
        assertTrue(promptTr.contains("12-DIMENSION STRUCTURED PERSONALITY MODEL"))
        assertTrue(promptTr.contains("CONTRASTING, CHALLENGING ALTER-EGO FRIENDSHIP"))
        assertTrue(promptTr.contains("SCENARIO [DISAGREEMENT / DEBATE]"))
        assertTrue(promptTr.contains("SAFETY & AUTONOMY GUARDRAILS"))
    }

    @Test
    fun `ai quality classifier covers all 7 conversation scenarios and validates against generic cliches or fabricated memories`() {
        assertEquals(
            ConversationScenarioType.CASUAL,
            PersonalityEngine.classifyScenario("Kahve içerken yağmuru izliyorum, keyifli bir akşam.")
        )
        assertEquals(
            ConversationScenarioType.DISAGREEMENT,
            PersonalityEngine.classifyScenario("Sana katılmıyorum, bence bu konuda tamamen yanılıyorsun.")
        )
        assertEquals(
            ConversationScenarioType.ADVICE_REQUEST,
            PersonalityEngine.classifyScenario("İki iş teklifi arasında kaldım, sence ne yapmalıyım?")
        )
        assertEquals(
            ConversationScenarioType.PHILOSOPHICAL,
            PersonalityEngine.classifyScenario("Felsefi bir soru: İnsan neden her şeyi kontrol etmeye çalışır?")
        )
        assertEquals(
            ConversationScenarioType.PREVIOUS_CONVERSATION_RECALL,
            PersonalityEngine.classifyScenario("Geçen sefer konuştuğumuz hedefimi hatırlıyor musun?")
        )
        assertEquals(
            ConversationScenarioType.EMOTIONAL_SITUATION,
            PersonalityEngine.classifyScenario("Bugün kendimi çok yalnız hissediyorum ve tükendim.")
        )
        assertEquals(
            ConversationScenarioType.UNCERTAIN_FACTUAL_QUERY,
            PersonalityEngine.classifyScenario("What is the exact statistic and stock price right now?")
        )

        // Validate clean, grounded alter-ego response passes quality gate
        val cleanValidation = PersonalityEngine.validateResponseQuality(
            reply = "Bak şimdi, her iki teklifi de aynı anda kontrol etmeye çalışıyorsun. Hangisi seni gerçekten heyecanlandırıyor?",
            verifiedMemories = emptyList()
        )
        assertTrue(cleanValidation.isValid)

        // Validate generic AI disclaimer / motivational cliché is rejected
        val clicheValidation = PersonalityEngine.validateResponseQuality(
            reply = "As an AI language model, believe in yourself and anything is possible!",
            verifiedMemories = emptyList()
        )
        assertFalse(clicheValidation.isValid)

        // Validate fabricated memory claim when memory bank is empty is rejected
        val fabricatedMemoryValidation = PersonalityEngine.validateResponseQuality(
            reply = "You told me last week that you moved to Tokyo and bought a sailboat.",
            verifiedMemories = emptyList()
        )
        assertFalse(fabricatedMemoryValidation.isValid)
    }

    @Test
    fun `memory engine enforces selective creation, relevance ranking, duplicate prevention, and opinion-shift updates`() {
        // 1. Selective creation: reject trivial Turkish & English greetings
        assertFalse(MemoryEngine.isSubstantiveTurn("merhaba"))
        assertFalse(MemoryEngine.isSubstantiveTurn("selam nasılsın"))
        assertFalse(MemoryEngine.isSubstantiveTurn("ok thanks"))
        assertTrue(MemoryEngine.isSubstantiveTurn("I decided to switch my career focus from backend to AI product engineering."))
        assertTrue(MemoryEngine.isSubstantiveTurn("Artık sabah 6'da uyanıp 45 dakika kesintisiz yazarlık yapmaya karar verdim."))

        val existingMemories = listOf(
            LongTermMemory(
                id = "mem_career",
                category = "GOAL",
                summary = "[DECISION] Wants to build a career in corporate finance and consulting",
                importance = 4
            ),
            LongTermMemory(
                id = "mem_hobby",
                category = "PREFERENCE",
                summary = "[INTEREST] Enjoys reading absurdist philosophy and Camus at night",
                importance = 3
            ),
            LongTermMemory(
                id = "mem_routine",
                category = "MILESTONE",
                summary = "[COMMITMENT] Committed to running 5km three times every week",
                importance = 5
            )
        )

        // 2. Duplicate prevention: near-identical memory is ignored
        val duplicateResolution = MemoryEngine.resolveCandidateMemory(
            shouldSave = true,
            rawSemanticType = "INTEREST",
            rawSummary = "Enjoys reading absurdist philosophy and Camus at night",
            importance = 3,
            existingMemories = existingMemories
        )
        assertTrue(duplicateResolution is MemoryResolutionAction.IgnoreTrivialOrDuplicate)

        // 3. Opinion shift / Decision revision: updates existing career memory in-place
        val opinionShiftResolution = MemoryEngine.resolveCandidateMemory(
            shouldSave = true,
            rawSemanticType = "OPINION_SHIFT",
            rawSummary = "Changed mind about corporate finance career and consulting to build indie AI products",
            importance = 5,
            existingMemories = existingMemories
        )
        assertTrue(opinionShiftResolution is MemoryResolutionAction.UpdateExisting)
        val updateAction = opinionShiftResolution as MemoryResolutionAction.UpdateExisting
        assertEquals("mem_career", updateAction.existingMemoryId)
        assertTrue(updateAction.updatedSummary.contains("[OPINION_SHIFT]"))

        // 4. Relevance ranking: query about philosophy ranks philosophy memory #1
        val rankedForPhilosophy = MemoryEngine.retrieveRankedMemories(
            allMemories = existingMemories,
            currentUserTurn = "What do you think about Camus and absurdist philosophy?",
            limit = 2
        )
        assertEquals(2, rankedForPhilosophy.size)
        assertEquals("mem_hobby", rankedForPhilosophy.first().id)
    }

    @Test
    fun `sentence stream chunker extracts speakable clauses for low-latency Turkish and English streaming voice`() {
        val chunker = SentenceStreamChunker()
        val firstBatch = chunker.extractReadyClauses("Bak şimdi, bu konuda yanılıyorsun. ", isStreamFinished = false)
        assertEquals(1, firstBatch.size)
        assertEquals("Bak şimdi, bu konuda yanılıyorsun.", firstBatch.first())

        val secondBatch = chunker.extractReadyClauses(
            "Bak şimdi, bu konuda yanılıyorsun. Her şeyi kontrol etmeye çalışmak seni sadece yorar! Biraz rahatla",
            isStreamFinished = false
        )
        assertEquals(1, secondBatch.size)
        assertEquals("Her şeyi kontrol etmeye çalışmak seni sadece yorar!", secondBatch.first())

        val tail = chunker.extractReadyClauses(
            "Bak şimdi, bu konuda yanılıyorsun. Her şeyi kontrol etmeye çalışmak seni sadece yorar! Biraz rahatla",
            isStreamFinished = true
        )
        assertEquals(listOf("Biraz rahatla"), tail)

        // Verify Voice Provider Evaluation Catalog compares providers including Turkish support
        val providers = VoiceProviderEvaluationCatalog.evaluatedProviders
        assertTrue(providers.size >= 3)
        val defaultProvider = providers.firstOrNull { it.isRecommendedDefault }
        assertNotNull(defaultProvider)
        assertTrue(defaultProvider!!.turkishQualityRating.contains("tr-TR"))
    }

    @Test
    fun `usage and cost engine enforces rate limits, tracks daily quotas, redacts PII, and estimates 100 500 1000 MAU costs`() {
        val engine = UsageAndCostEngine(
            UsageQuotaConfig(
                dailyAiMessageLimit = 5,
                dailyVoiceMinutesLimit = 10,
                minRequestIntervalMs = 1_000L,
                enforceHardLimits = true
            )
        )

        // First turn allowed at t=10_000
        assertEquals(RateLimitAllowance.Allowed, engine.checkAllowance(isVoiceMode = false, nowEpochMs = 10_000L))
        engine.recordTurnStarted(nowEpochMs = 10_000L)
        engine.recordCompletedTurn(
            userText = "Selam Nova, bugün odaklanmak istiyorum.",
            companionReply = "Harika, o zaman telefon bildirimlerini kapatıp 25 dakika tek bir işe odaklanalım.",
            isVoiceMode = true,
            memoryExtracted = true
        )

        // Burst request 200ms later is blocked by cooldown rate limiter
        val burstCheck = engine.checkAllowance(isVoiceMode = false, nowEpochMs = 10_200L)
        assertTrue(burstCheck is RateLimitAllowance.DeniedCooldown)
        assertEquals(1, engine.snapshot.value.rateLimitedBlockedCount)

        // Request after cooldown (t=12_000) is allowed
        assertEquals(RateLimitAllowance.Allowed, engine.checkAllowance(isVoiceMode = false, nowEpochMs = 12_000L))

        // Verify 100, 500, and 1,000 MAU cost estimates scale monotonically and include all components
        val tiers = UsageAndCostEngine.estimateAllStandardTiers()
        assertEquals(3, tiers.size)
        val cost100 = tiers[0]
        val cost500 = tiers[1]
        val cost1000 = tiers[2]
        assertEquals(100, cost100.activeUsers)
        assertEquals(500, cost500.activeUsers)
        assertEquals(1000, cost1000.activeUsers)
        assertTrue(cost100.totalMonthlyNativeHybridUsd > 0.0)
        assertTrue(cost500.totalMonthlyNativeHybridUsd > cost100.totalMonthlyNativeHybridUsd)
        assertTrue(cost1000.totalMonthlyNativeHybridUsd > cost500.totalMonthlyNativeHybridUsd)
        assertTrue(cost1000.totalMonthlyCloudRealtimeUsd > cost1000.totalMonthlyNativeHybridUsd)

        // Verify PII redaction in ProductionDiagnosticLogger
        val redacted = ProductionDiagnosticLogger.redactPii("User egekaya825@gmail.com failed auth")
        assertFalse(redacted.contains("egekaya825@gmail.com"))
        assertTrue(redacted.contains("[REDACTED_EMAIL]"))
    }

    @Test
    fun `character design engine translates personality dualities into unconventional full-body looks, honors locked slots, and stays within Firestore 40-char limits`() {
        val state = CompanionState()
        val rebelProfile = state.idealSelfProfile.copy(
            coreAspiration = "Intellectual philosopher with a rebellious, autonomous edge",
            growthGoals = "Build bold, unconventional craft with masculine confidence"
        )
        val rebelTraits = state.personalityTraits.copy(
            analyticalDepth = 88,
            directness = 86
        )

        val firstLook = com.example.ai.CharacterDesignEngine.synthesizeUnconventionalDesign(
            idealSelf = rebelProfile,
            traits = rebelTraits,
            currentAppearance = state.characterAppearance,
            currentClothing = state.clothingAndAccessories,
            lockedSlots = emptySet(),
            variationIndex = 0
        )

        assertEquals(
            com.example.ai.PersonalityDualityArchetype.INTELLECTUAL_REBEL,
            firstLook.dualityArchetype
        )
        assertEquals(
            com.example.data.model.PresentationStyle.MASCULINE,
            firstLook.appearance.presentationStyle
        )
        assertTrue(firstLook.detectedContradictions.isNotEmpty())
        assertTrue(firstLook.appearance.backdropThemeId.length <= 40)
        assertTrue(firstLook.clothing.outfitId.length <= 40)

        // Lock HAIR and FOOTWEAR, then regenerate variant #1
        val locked = setOf(
            com.example.data.model.CustomizableSlot.HAIR,
            com.example.data.model.CustomizableSlot.FOOTWEAR
        )
        val secondLook = com.example.ai.CharacterDesignEngine.synthesizeUnconventionalDesign(
            idealSelf = rebelProfile,
            traits = rebelTraits,
            currentAppearance = firstLook.appearance,
            currentClothing = firstLook.clothing,
            lockedSlots = locked,
            variationIndex = 1
        )

        // Locked slots must remain identical
        assertEquals(firstLook.appearance.hairStyleId, secondLook.appearance.hairStyleId)
        assertEquals(firstLook.appearance.hairColorId, secondLook.appearance.hairColorId)
        assertEquals(firstLook.clothing.footwearId, secondLook.clothing.footwearId)
        // Unlocked slots (e.g., layered top or trousers) advance to the next unconventional variant
        assertTrue(secondLook.clothing.layeredTopId != firstLook.clothing.layeredTopId)
        assertTrue(secondLook.appearance.backdropThemeId.length <= 40)
        assertTrue(secondLook.clothing.outfitId.length <= 40)
    }
}
