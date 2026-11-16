package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PresentationStyle(
    val id: String,
    val shortCode: String,
    val label: String,
    val subtitle: String
) {
    ANDROGYNOUS(
        id = "androgynous",
        shortCode = "and",
        label = "Androgynous",
        subtitle = "Angular editorial shoulders & fluid avant-garde silhouette"
    ),
    MASCULINE(
        id = "masculine",
        shortCode = "msc",
        label = "Masculine",
        subtitle = "Structured broad shoulders, boxy outerwear & sharp jaw geometry"
    ),
    FEMININE(
        id = "feminine",
        shortCode = "fem",
        label = "Feminine",
        subtitle = "Sculpted waist-to-hip contrast, cropped layering & lash contour"
    );

    companion object {
        fun fromIdOrCode(raw: String): PresentationStyle {
            val cleaned = raw.trim().lowercase()
            return entries.firstOrNull { it.id == cleaned || it.shortCode == cleaned } ?: ANDROGYNOUS
        }
    }
}

enum class CustomizableSlot(val id: String, val label: String) {
    PRESENTATION("presentation", "Silhouette"),
    HAIR("hair", "Hair"),
    FACE("face", "Face & Eyewear"),
    TOP("top", "Outerwear & Top"),
    TROUSERS("trousers", "Trousers"),
    FOOTWEAR("footwear", "Boots & Shoes"),
    ACCESSORY("accessory", "Bag, Belt & Jewelry"),
    PALETTE("palette", "Colors")
}

// 1. UserAccount
data class UserAccount(
    val userId: String = "",
    val displayName: String = "Dreamer",
    val email: String = "",
    val onboardingCompleted: Boolean = false
)

// 11-Dimension Conversational Ideal-Self Discovery Answers
data class DiscoveryDimensions(
    val desiredPersonality: String = "Calm, grounded, and quietly magnetic",
    val personalValues: String = "Integrity, creative freedom, deep presence",
    val confidence: String = "Self-assured without needing external validation",
    val socialBehavior: String = "Warm listener who speaks with intention",
    val independence: String = "Self-directed thinker who collaborates generously",
    val humor: String = "Playful, dry wit and lighthearted warmth",
    val emotionalResponses: String = "Steady, curious, and compassionate under pressure",
    val worldview: String = "Optimistic realism — every day is a canvas for growth",
    val ambitions: String = "Build meaningful craft and lasting personal mastery",
    val relationships: String = "Honest, supportive, and deeply encouraging",
    val communicationStyle: String = "Warm & Socratic",
    val savedStepIndex: Int = 0
) {
    fun toIdealSelfProfile(quizCompleted: Boolean = true): IdealSelfProfile {
        val parsedPillars = personalValues.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("Mindful Clarity", "Creative Craft", "Authentic Connection") }
            .take(8)
        return IdealSelfProfile(
            coreAspiration = "$desiredPersonality — $confidence".take(500),
            lifePillars = parsedPillars,
            growthGoals = "$ambitions | Social & Independence: $socialBehavior; $independence | Relationships: $relationships".take(500),
            emotionalGrounding = "$emotionalResponses | Worldview & Humor: $worldview; $humor".take(300),
            reflectionStyle = communicationStyle.take(100).ifBlank { "Warm & Socratic" },
            quizCompleted = quizCompleted,
            discoveryDimensions = this
        )
    }

    companion object {
        fun fromIdealSelfProfile(
            ideal: IdealSelfProfile,
            @Suppress("UNUSED_PARAMETER") traits: PersonalityTraits = PersonalityTraits()
        ): DiscoveryDimensions {
            return ideal.discoveryDimensions.copy(
                desiredPersonality = ideal.coreAspiration.substringBefore(" — ").ifBlank { ideal.discoveryDimensions.desiredPersonality },
                personalValues = ideal.lifePillars.joinToString(", ").ifBlank { ideal.discoveryDimensions.personalValues },
                communicationStyle = ideal.reflectionStyle.ifBlank { ideal.discoveryDimensions.communicationStyle }
            )
        }
    }
}

// 2. IdealSelfProfile
data class IdealSelfProfile(
    val coreAspiration: String = "Embody calm confidence, creative courage, and intentional daily presence.",
    val lifePillars: List<String> = listOf("Mindful Clarity", "Creative Craft", "Authentic Connection"),
    val growthGoals: String = "Speak with kindness and conviction; take small consistent steps toward meaningful goals.",
    val emotionalGrounding: String = "Steady, curious, and compassionate under pressure.",
    val reflectionStyle: String = "Warm & Socratic",
    val quizCompleted: Boolean = false,
    val discoveryDimensions: DiscoveryDimensions = DiscoveryDimensions()
)

// 3. CharacterIdentity
data class CharacterIdentity(
    val companionName: String = "Nova",
    val archetypeTitle: String = "The Serene Architect",
    val backstorySummary: String = "A 16-bit starlight spirit manifested from your highest personal aspirations.",
    val greetingStyle: String = "Hey! Ready to check in on what matters most today?",
    val signatureCatchphrase: String = "Every intentional pixel builds the masterpiece."
)

// 4. PersonalityTraits
data class PersonalityTraits(
    val warmth: Int = 85,
    val directness: Int = 72,
    val optimism: Int = 82,
    val playfulness: Int = 68,
    val analyticalDepth: Int = 76,
    val traitKeywords: List<String> = listOf("Empathetic", "Grounded", "Encouraging", "Insightful")
)

// 5. CharacterAppearance
data class CharacterAppearance(
    val skinToneId: String = "warm_ivory",
    val hairStyleId: String = "tousled_rebel",
    val hairColorId: String = "midnight_indigo",
    val eyeStyleId: String = "sparkle_round",
    val eyeColorId: String = "amber_gold",
    val expressionDefault: String = "warm_smile",
    val backdropThemeId: String = "midnight_studio:tinted_aviator:and"
) {
    private val backdropParts: List<String>
        get() = backdropThemeId.split(":")

    val studioBackdropId: String
        get() = backdropParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "midnight_studio"

    val faceFeatureId: String
        get() = backdropParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "soft_blush"

    val presentationStyle: PresentationStyle
        get() = PresentationStyle.fromIdOrCode(backdropParts.getOrNull(2).orEmpty())

    val presentationId: String
        get() = presentationStyle.id

    private fun packBackdrop(studio: String, face: String, presentation: PresentationStyle): String {
        val s = studio.take(15).ifBlank { "midnight_studio" }
        val f = face.take(16).ifBlank { "soft_blush" }
        val p = presentation.shortCode
        return "$s:$f:$p".take(40)
    }

    fun withFaceFeature(featureId: String): CharacterAppearance {
        return copy(backdropThemeId = packBackdrop(studioBackdropId, featureId, presentationStyle))
    }

    fun withStudioBackdrop(studioId: String): CharacterAppearance {
        return copy(backdropThemeId = packBackdrop(studioId, faceFeatureId, presentationStyle))
    }

    fun withPresentation(style: PresentationStyle): CharacterAppearance {
        return copy(backdropThemeId = packBackdrop(studioBackdropId, faceFeatureId, style))
    }

    fun withPresentationId(idOrCode: String): CharacterAppearance {
        return withPresentation(PresentationStyle.fromIdOrCode(idOrCode))
    }
}

// 6. ClothingAndAccessories
data class ClothingAndAccessories(
    val outfitId: String = "rebel_leather:wide_pleat:combat_boot",
    val primaryColorHex: String = "#5D212B",
    val accentColorHex: String = "#839083",
    val accessoryId: String = "crossbody_bag",
    val auraEffectId: String = "starlight_fireflies"
) {
    private val outfitParts: List<String>
        get() = outfitId.split(":")

    val layeredTopId: String
        get() = outfitParts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "rebel_leather"

    val trousersId: String
        get() = outfitParts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultTrousersForTop(layeredTopId)

    val footwearId: String
        get() = outfitParts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: defaultFootwearForTop(layeredTopId)

    private fun packOutfit(top: String, trousers: String, footwear: String): String {
        val t = top.take(13).ifBlank { "rebel_leather" }
        val tr = trousers.take(11).ifBlank { "wide_pleat" }
        val fw = footwear.take(11).ifBlank { "combat_boot" }
        return "$t:$tr:$fw".take(40)
    }

    fun withLayeredTop(topId: String): ClothingAndAccessories {
        return copy(outfitId = packOutfit(topId, trousersId, footwearId))
    }

    fun withTrousers(newTrousersId: String): ClothingAndAccessories {
        return copy(outfitId = packOutfit(layeredTopId, newTrousersId, footwearId))
    }

    fun withFootwear(newFootwearId: String): ClothingAndAccessories {
        return copy(outfitId = packOutfit(layeredTopId, trousersId, newFootwearId))
    }

    fun withFullOutfit(topId: String, newTrousersId: String, newFootwearId: String): ClothingAndAccessories {
        return copy(outfitId = packOutfit(topId, newTrousersId, newFootwearId))
    }

    companion object {
        fun defaultTrousersForTop(topId: String): String = when (topId) {
            "scholar_vest", "trench_havana", "blazer_hoodie" -> "wide_pleat"
            "cyber_jacket", "rebel_leather", "field_flannel" -> "cargo_punk"
            "sage_kimono", "patch_cardi" -> "para_drape"
            "velvet_mesh" -> "flare_cord"
            else -> "cuff_denim"
        }

        fun defaultFootwearForTop(topId: String): String = when (topId) {
            "scholar_vest", "blazer_hoodie" -> "lug_loafer"
            "trench_havana", "velvet_mesh" -> "chelsea_bt"
            "cozy_hoodie" -> "retro_high"
            "sage_kimono" -> "tabi_avant"
            else -> "combat_boot"
        }
    }
}

// 7. VoicePreferences & CompanionVoiceTone
enum class AudioRoutePreference(val label: String) {
    SPEAKER("Speakerphone"),
    EARPIECE("Earpiece"),
    BLUETOOTH("Bluetooth Audio")
}

enum class CompanionVoiceTone(
    val id: String,
    val trLabel: String,
    val enLabel: String,
    val trSubtitle: String,
    val enSubtitle: String,
    val pitch: Float,
    val speechRate: Float,
    val matchingPresentationId: String
) {
    WARM_BALANCED(
        id = "warm_balanced",
        trLabel = "Sıcak ve Dengeli",
        enLabel = "Warm & Balanced",
        trSubtitle = "Samimi, doğal ve dingin",
        enSubtitle = "Friendly, natural & grounded",
        pitch = 1.00f,
        speechRate = 1.00f,
        matchingPresentationId = "androgynous"
    ),
    DEEP_RESONANT(
        id = "deep_resonant",
        trLabel = "Derin ve Karizmatik",
        enLabel = "Deep & Resonant",
        trSubtitle = "Tok, güven veren bariton ton",
        enSubtitle = "Rich, reassuring low tone",
        pitch = 0.84f,
        speechRate = 0.94f,
        matchingPresentationId = "masculine"
    ),
    BRIGHT_EXPRESSIVE(
        id = "bright_expressive",
        trLabel = "Canlı ve Neşeli",
        enLabel = "Bright & Expressive",
        trSubtitle = "Enerjik, berrak ve motive edici",
        enSubtitle = "Clear, upbeat & encouraging",
        pitch = 1.16f,
        speechRate = 1.06f,
        matchingPresentationId = "feminine"
    ),
    VELVET_REFLECTIVE(
        id = "velvet_reflective",
        trLabel = "Kadife ve Bilge",
        enLabel = "Velvet & Reflective",
        trSubtitle = "Yumuşak, sakin ve düşündürücü",
        enSubtitle = "Soft, unhurried & meditative",
        pitch = 0.91f,
        speechRate = 0.89f,
        matchingPresentationId = "androgynous"
    ),
    CRISP_FOCUSED(
        id = "crisp_focused",
        trLabel = "Net ve Odaklı",
        enLabel = "Crisp & Focused",
        trSubtitle = "Dinamik, pratik ve kararlı",
        enSubtitle = "Sharp, articulate & action-ready",
        pitch = 1.05f,
        speechRate = 1.12f,
        matchingPresentationId = "masculine"
    );

    companion object {
        fun fromId(id: String): CompanionVoiceTone {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WARM_BALANCED
        }

        fun fromPitchAndRate(pitch: Float, speechRate: Float): CompanionVoiceTone {
            return entries.minByOrNull { tone ->
                val pitchDiff = kotlin.math.abs(tone.pitch - pitch)
                val rateDiff = kotlin.math.abs(tone.speechRate - speechRate)
                pitchDiff * 1.4f + rateDiff
            } ?: WARM_BALANCED
        }

        fun recommendedForPersona(
            appearance: CharacterAppearance,
            traits: PersonalityTraits = PersonalityTraits()
        ): CompanionVoiceTone {
            return when {
                appearance.expressionDefault == "joy" || appearance.expressionDefault == "sparkle" -> BRIGHT_EXPRESSIVE
                appearance.expressionDefault == "thoughtful" && traits.analyticalDepth >= 80 -> VELVET_REFLECTIVE
                appearance.expressionDefault == "focused" || traits.directness >= 82 -> CRISP_FOCUSED
                appearance.presentationId == "masculine" -> DEEP_RESONANT
                appearance.presentationId == "feminine" -> BRIGHT_EXPRESSIVE
                else -> WARM_BALANCED
            }
        }
    }
}

data class VoicePreferences(
    val pitch: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val handsFreeAutoListen: Boolean = true,
    val preferredAudioRoute: AudioRoutePreference = AudioRoutePreference.SPEAKER
) {
    val voicePitchMilli: Long get() = (pitch * 1000f).toLong().coerceIn(500L, 2000L)
    val voiceRateMilli: Long get() = (speechRate * 1000f).toLong().coerceIn(500L, 2000L)

    val voiceTone: CompanionVoiceTone
        get() = CompanionVoiceTone.fromPitchAndRate(pitch, speechRate)

    fun withVoiceTone(tone: CompanionVoiceTone): VoicePreferences = copy(
        pitch = tone.pitch,
        speechRate = tone.speechRate
    )
}

// 8. UserPrivacySettings
data class UserPrivacySettings(
    val autoExtractMemories: Boolean = true,
    val saveConversationHistory: Boolean = true,
    val contextWindowMemoryLimit: Int = 15
)

// Combined Domain State for the User's Ideal Self Companion
data class CompanionState(
    val userAccount: UserAccount = UserAccount(),
    val idealSelfProfile: IdealSelfProfile = IdealSelfProfile(),
    val characterIdentity: CharacterIdentity = CharacterIdentity(),
    val personalityTraits: PersonalityTraits = PersonalityTraits(),
    val characterAppearance: CharacterAppearance = CharacterAppearance(),
    val clothingAndAccessories: ClothingAndAccessories = ClothingAndAccessories(),
    val voicePreferences: VoicePreferences = VoicePreferences(),
    val privacySettings: UserPrivacySettings = UserPrivacySettings(),
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

// Firestore Document Representation at /users/{userId}
@IgnoreExtraProperties
data class UserCompanionDocument(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Dreamer",
    val email: String = "",
    val onboardingCompleted: Boolean = false,
    val coreAspiration: String = "Embody calm confidence, creative courage, and intentional daily presence.",
    val lifePillars: List<String> = listOf("Mindful Clarity", "Creative Craft", "Authentic Connection"),
    val growthGoals: String = "Speak with kindness and conviction; take small consistent steps toward meaningful goals.",
    val emotionalGrounding: String = "Steady, curious, and compassionate under pressure.",
    val reflectionStyle: String = "Warm & Socratic",
    val quizCompleted: Boolean = false,
    val companionName: String = "Nova",
    val archetypeTitle: String = "The Serene Architect",
    val backstorySummary: String = "A 16-bit starlight spirit manifested from your highest personal aspirations.",
    val greetingStyle: String = "Hey! Ready to check in on what matters most today?",
    val signatureCatchphrase: String = "Every intentional pixel builds the masterpiece.",
    val warmth: Long = 85L,
    val directness: Long = 72L,
    val optimism: Long = 82L,
    val playfulness: Long = 68L,
    val analyticalDepth: Long = 76L,
    val traitKeywords: List<String> = listOf("Empathetic", "Grounded", "Encouraging", "Insightful"),
    val skinToneId: String = "warm_ivory",
    val hairStyleId: String = "tousled_rebel",
    val hairColorId: String = "midnight_indigo",
    val eyeStyleId: String = "sparkle_round",
    val eyeColorId: String = "amber_gold",
    val expressionDefault: String = "warm_smile",
    val backdropThemeId: String = "midnight_studio:tinted_aviator:and",
    val outfitId: String = "rebel_leather:wide_pleat:combat_boot",
    val primaryColorHex: String = "#5D212B",
    val accentColorHex: String = "#839083",
    val accessoryId: String = "crossbody_bag",
    val auraEffectId: String = "starlight_fireflies",
    val voicePitchMilli: Long = 1000L,
    val voiceRateMilli: Long = 1000L,
    val handsFreeAutoListen: Boolean = true,
    val preferredAudioRoute: String = "SPEAKER",
    val autoExtractMemories: Boolean = true,
    val saveConversationHistory: Boolean = true,
    val contextWindowMemoryLimit: Long = 15L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCompanionState(): CompanionState {
        val route = try {
            AudioRoutePreference.valueOf(preferredAudioRoute)
        } catch (_: Exception) {
            AudioRoutePreference.SPEAKER
        }
        return CompanionState(
            userAccount = UserAccount(
                userId = userId,
                displayName = displayName,
                email = email,
                onboardingCompleted = onboardingCompleted
            ),
            idealSelfProfile = IdealSelfProfile(
                coreAspiration = coreAspiration,
                lifePillars = lifePillars,
                growthGoals = growthGoals,
                emotionalGrounding = emotionalGrounding,
                reflectionStyle = reflectionStyle,
                quizCompleted = quizCompleted,
                discoveryDimensions = DiscoveryDimensions(
                    desiredPersonality = coreAspiration,
                    personalValues = lifePillars.joinToString(", "),
                    emotionalResponses = emotionalGrounding,
                    ambitions = growthGoals,
                    communicationStyle = reflectionStyle
                )
            ),
            characterIdentity = CharacterIdentity(
                companionName = companionName,
                archetypeTitle = archetypeTitle,
                backstorySummary = backstorySummary,
                greetingStyle = greetingStyle,
                signatureCatchphrase = signatureCatchphrase
            ),
            personalityTraits = PersonalityTraits(
                warmth = warmth.toInt().coerceIn(0, 100),
                directness = directness.toInt().coerceIn(0, 100),
                optimism = optimism.toInt().coerceIn(0, 100),
                playfulness = playfulness.toInt().coerceIn(0, 100),
                analyticalDepth = analyticalDepth.toInt().coerceIn(0, 100),
                traitKeywords = traitKeywords
            ),
            characterAppearance = CharacterAppearance(
                skinToneId = skinToneId,
                hairStyleId = hairStyleId,
                hairColorId = hairColorId,
                eyeStyleId = eyeStyleId,
                eyeColorId = eyeColorId,
                expressionDefault = expressionDefault,
                backdropThemeId = backdropThemeId
            ),
            clothingAndAccessories = ClothingAndAccessories(
                outfitId = outfitId,
                primaryColorHex = primaryColorHex,
                accentColorHex = accentColorHex,
                accessoryId = accessoryId,
                auraEffectId = auraEffectId
            ),
            voicePreferences = VoicePreferences(
                pitch = (voicePitchMilli.toFloat() / 1000f).coerceIn(0.5f, 2.0f),
                speechRate = (voiceRateMilli.toFloat() / 1000f).coerceIn(0.5f, 2.0f),
                handsFreeAutoListen = handsFreeAutoListen,
                preferredAudioRoute = route
            ),
            privacySettings = UserPrivacySettings(
                autoExtractMemories = autoExtractMemories,
                saveConversationHistory = saveConversationHistory,
                contextWindowMemoryLimit = contextWindowMemoryLimit.toInt().coerceIn(1, 50)
            ),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

fun CompanionState.toCreateMap(uid: String): Map<String, Any> {
    return mapOf(
        "userId" to uid,
        "displayName" to userAccount.displayName.take(100).ifBlank { "Dreamer" },
        "email" to userAccount.email.take(150),
        "onboardingCompleted" to userAccount.onboardingCompleted,
        "coreAspiration" to idealSelfProfile.coreAspiration.take(500).ifBlank { "Grow into my ideal self." },
        "lifePillars" to idealSelfProfile.lifePillars.take(10).map { it.take(100) },
        "growthGoals" to idealSelfProfile.growthGoals.take(500),
        "emotionalGrounding" to idealSelfProfile.emotionalGrounding.take(300),
        "reflectionStyle" to idealSelfProfile.reflectionStyle.take(100).ifBlank { "Warm & Socratic" },
        "quizCompleted" to idealSelfProfile.quizCompleted,
        "companionName" to characterIdentity.companionName.take(60).ifBlank { "Nova" },
        "archetypeTitle" to characterIdentity.archetypeTitle.take(100).ifBlank { "The Serene Architect" },
        "backstorySummary" to characterIdentity.backstorySummary.take(600),
        "greetingStyle" to characterIdentity.greetingStyle.take(300),
        "signatureCatchphrase" to characterIdentity.signatureCatchphrase.take(200),
        "warmth" to personalityTraits.warmth.toLong().coerceIn(0L, 100L),
        "directness" to personalityTraits.directness.toLong().coerceIn(0L, 100L),
        "optimism" to personalityTraits.optimism.toLong().coerceIn(0L, 100L),
        "playfulness" to personalityTraits.playfulness.toLong().coerceIn(0L, 100L),
        "analyticalDepth" to personalityTraits.analyticalDepth.toLong().coerceIn(0L, 100L),
        "traitKeywords" to personalityTraits.traitKeywords.take(10).map { it.take(100) },
        "skinToneId" to characterAppearance.skinToneId.take(40).ifBlank { "warm_ivory" },
        "hairStyleId" to characterAppearance.hairStyleId.take(40).ifBlank { "tousled_rebel" },
        "hairColorId" to characterAppearance.hairColorId.take(40).ifBlank { "midnight_indigo" },
        "eyeStyleId" to characterAppearance.eyeStyleId.take(40).ifBlank { "sparkle_round" },
        "eyeColorId" to characterAppearance.eyeColorId.take(40).ifBlank { "amber_gold" },
        "expressionDefault" to characterAppearance.expressionDefault.take(40).ifBlank { "warm_smile" },
        "backdropThemeId" to characterAppearance.backdropThemeId.take(40).ifBlank { "midnight_studio:tinted_aviator:and" },
        "outfitId" to clothingAndAccessories.outfitId.take(40).ifBlank { "rebel_leather:wide_pleat:combat_boot" },
        "primaryColorHex" to clothingAndAccessories.primaryColorHex.take(16).ifBlank { "#E11D48" },
        "accentColorHex" to clothingAndAccessories.accentColorHex.take(16).ifBlank { "#FBBF24" },
        "accessoryId" to clothingAndAccessories.accessoryId.take(40).ifBlank { "crossbody_bag" },
        "auraEffectId" to clothingAndAccessories.auraEffectId.take(40).ifBlank { "starlight_fireflies" },
        "voicePitchMilli" to voicePreferences.voicePitchMilli,
        "voiceRateMilli" to voicePreferences.voiceRateMilli,
        "handsFreeAutoListen" to voicePreferences.handsFreeAutoListen,
        "preferredAudioRoute" to voicePreferences.preferredAudioRoute.name,
        "autoExtractMemories" to privacySettings.autoExtractMemories,
        "saveConversationHistory" to privacySettings.saveConversationHistory,
        "contextWindowMemoryLimit" to privacySettings.contextWindowMemoryLimit.toLong().coerceIn(1L, 50L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

fun CompanionState.toUpdateMap(): Map<String, Any> {
    return mapOf(
        "displayName" to userAccount.displayName.take(100).ifBlank { "Dreamer" },
        "email" to userAccount.email.take(150),
        "onboardingCompleted" to userAccount.onboardingCompleted,
        "coreAspiration" to idealSelfProfile.coreAspiration.take(500).ifBlank { "Grow into my ideal self." },
        "lifePillars" to idealSelfProfile.lifePillars.take(10).map { it.take(100) },
        "growthGoals" to idealSelfProfile.growthGoals.take(500),
        "emotionalGrounding" to idealSelfProfile.emotionalGrounding.take(300),
        "reflectionStyle" to idealSelfProfile.reflectionStyle.take(100).ifBlank { "Warm & Socratic" },
        "quizCompleted" to idealSelfProfile.quizCompleted,
        "companionName" to characterIdentity.companionName.take(60).ifBlank { "Nova" },
        "archetypeTitle" to characterIdentity.archetypeTitle.take(100).ifBlank { "The Serene Architect" },
        "backstorySummary" to characterIdentity.backstorySummary.take(600),
        "greetingStyle" to characterIdentity.greetingStyle.take(300),
        "signatureCatchphrase" to characterIdentity.signatureCatchphrase.take(200),
        "warmth" to personalityTraits.warmth.toLong().coerceIn(0L, 100L),
        "directness" to personalityTraits.directness.toLong().coerceIn(0L, 100L),
        "optimism" to personalityTraits.optimism.toLong().coerceIn(0L, 100L),
        "playfulness" to personalityTraits.playfulness.toLong().coerceIn(0L, 100L),
        "analyticalDepth" to personalityTraits.analyticalDepth.toLong().coerceIn(0L, 100L),
        "traitKeywords" to personalityTraits.traitKeywords.take(10).map { it.take(100) },
        "skinToneId" to characterAppearance.skinToneId.take(40).ifBlank { "warm_ivory" },
        "hairStyleId" to characterAppearance.hairStyleId.take(40).ifBlank { "tousled_rebel" },
        "hairColorId" to characterAppearance.hairColorId.take(40).ifBlank { "midnight_indigo" },
        "eyeStyleId" to characterAppearance.eyeStyleId.take(40).ifBlank { "sparkle_round" },
        "eyeColorId" to characterAppearance.eyeColorId.take(40).ifBlank { "amber_gold" },
        "expressionDefault" to characterAppearance.expressionDefault.take(40).ifBlank { "warm_smile" },
        "backdropThemeId" to characterAppearance.backdropThemeId.take(40).ifBlank { "midnight_studio:tinted_aviator:and" },
        "outfitId" to clothingAndAccessories.outfitId.take(40).ifBlank { "rebel_leather:wide_pleat:combat_boot" },
        "primaryColorHex" to clothingAndAccessories.primaryColorHex.take(16).ifBlank { "#E11D48" },
        "accentColorHex" to clothingAndAccessories.accentColorHex.take(16).ifBlank { "#FBBF24" },
        "accessoryId" to clothingAndAccessories.accessoryId.take(40).ifBlank { "crossbody_bag" },
        "auraEffectId" to clothingAndAccessories.auraEffectId.take(40).ifBlank { "starlight_fireflies" },
        "voicePitchMilli" to voicePreferences.voicePitchMilli,
        "voiceRateMilli" to voicePreferences.voiceRateMilli,
        "handsFreeAutoListen" to voicePreferences.handsFreeAutoListen,
        "preferredAudioRoute" to voicePreferences.preferredAudioRoute.name,
        "autoExtractMemories" to privacySettings.autoExtractMemories,
        "saveConversationHistory" to privacySettings.saveConversationHistory,
        "contextWindowMemoryLimit" to privacySettings.contextWindowMemoryLimit.toLong().coerceIn(1L, 50L),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

// 9. Conversation
@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Studio Check-In",
    val mode: String = "TEXT", // "VOICE" or "TEXT"
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

// 10. Message
@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val sender: String = "USER", // "USER" or "COMPANION"
    val content: String = "",
    val emotionTag: String = "warm_smile",
    @ServerTimestamp val createdAt: Timestamp? = null
)

// 11. LongTermMemory
@IgnoreExtraProperties
data class LongTermMemory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "INSIGHT", // "GOAL", "VALUE", "PREFERENCE", "MILESTONE", "INSIGHT"
    val summary: String = "",
    val importance: Long = 3L,
    val sourceConversationId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
