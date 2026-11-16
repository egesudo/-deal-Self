package com.example.ai

import com.example.data.model.CharacterAppearance
import com.example.data.model.ClothingAndAccessories
import com.example.data.model.CustomizableSlot
import com.example.data.model.IdealSelfProfile
import com.example.data.model.PersonalityTraits
import com.example.data.model.PresentationStyle

enum class PersonalityDualityArchetype(
    val id: String,
    val title: String,
    val dualityLabel: String,
    val stylingPhilosophy: String
) {
    INTELLECTUAL_REBEL(
        id = "intellectual_rebel",
        title = "The Midnight Counter-Philosopher",
        dualityLabel = "Intellectual + Rebellious",
        stylingPhilosophy = "Vintage tailoring collided with distressed punk streetwear, rose-tinted aviators, and heavy lug-sole boots."
    ),
    CALM_AMBITIOUS(
        id = "calm_ambitious",
        title = "The Quiet Vanguard",
        dualityLabel = "Calm + Ambitious",
        stylingPhilosophy = "Relaxed architectural silhouettes, wide-leg pleated trousers, and a single razor-sharp statement accent."
    ),
    ROMANTIC_INDEPENDENT(
        id = "romantic_independent",
        title = "The Velvet Nomad",
        dualityLabel = "Romantic + Independent",
        stylingPhilosophy = "Soft tactile textures and open collars layered under scuffed oxblood leather and pointed Chelsea boots."
    ),
    EMOTIONAL_STOIC(
        id = "emotional_stoic",
        title = "The Warm Iron Maverick",
        dualityLabel = "Structured + Emotionally Intelligent",
        stylingPhilosophy = "Rugged structured shearling or field outerwear balanced by warm amber tones, silver chains, and approachable eyes."
    ),
    AVANT_ECLECTIC(
        id = "avant_eclectic",
        title = "The Acid-Jazz Alchemist",
        dualityLabel = "Analytical + Unpredictable",
        stylingPhilosophy = "Asymmetric patchwork knitwear over patterned shirts, parachute volume trousers, and split-toe architectural boots."
    )
}

data class UnconventionalStyleBlueprint(
    val dualityArchetype: PersonalityDualityArchetype,
    val artDirectorRationale: String,
    val detectedContradictions: List<String>,
    val appearance: CharacterAppearance,
    val clothing: ClothingAndAccessories
)

object CharacterDesignEngine {

    private data class DualityLookTemplate(
        val hairStyles: List<String>,
        val hairColors: List<String>,
        val eyeStyles: List<String>,
        val eyeColors: List<String>,
        val faceFeatures: List<String>,
        val expressions: List<String>,
        val layeredTops: List<String>,
        val trousers: List<String>,
        val footwear: List<String>,
        val accessories: List<String>,
        val palettes: List<Pair<String, String>>,
        val auras: List<String>
    )

    private val templatesByDuality: Map<PersonalityDualityArchetype, DualityLookTemplate> = mapOf(
        PersonalityDualityArchetype.INTELLECTUAL_REBEL to DualityLookTemplate(
            hairStyles = listOf("tousled_rebel", "textured_undercut", "cyber_mullet", "spiky_hero"),
            hairColors = listOf("midnight_indigo", "crimson_flame", "silver_frost", "espresso_noir"),
            eyeStyles = listOf("calm_focused", "mystic_star", "sparkle_round"),
            eyeColors = listOf("amber_gold", "cobalt_sky", "crimson_ember"),
            faceFeatures = listOf("tinted_aviator", "round_tortoise", "brow_plaster", "sharp_stubble"),
            expressions = listOf("determined", "wink", "curious"),
            layeredTops = listOf("blazer_hoodie", "rebel_leather", "trench_havana", "cyber_jacket"),
            trousers = listOf("tartan_puk", "cargo_punk", "wide_pleat"),
            footwear = listOf("combat_boot", "moto_buckle", "lug_loafer"),
            accessories = listOf("silver_chain", "studded_belt", "crossbody_bag", "pixel_glasses"),
            palettes = listOf(
                Pair("#5D212B", "#839083"), // Signature Deep Burgundy + Muted Sage Grey
                Pair("#5D212B", "#F2EDE2"), // Deep Burgundy + Warm Cream Beige
                Pair("#1E293B", "#F43F5E")  // Charcoal tweed + punk rose
            ),
            auras = listOf("amber_halo", "starlight_fireflies", "minimal_glow")
        ),
        PersonalityDualityArchetype.CALM_AMBITIOUS to DualityLookTemplate(
            hairStyles = listOf("starlight_bob", "cyber_swept", "braided_crown", "bleached_buzz"),
            hairColors = listOf("midnight_indigo", "silver_frost", "amber_gold", "espresso_noir"),
            eyeStyles = listOf("calm_focused", "sparkle_round"),
            eyeColors = listOf("emerald_glow", "amber_gold", "cobalt_sky"),
            faceFeatures = listOf("round_tortoise", "visor_clip", "clean", "soft_blush"),
            expressions = listOf("serene", "warm_smile", "determined"),
            layeredTops = listOf("denim_turtle", "trench_havana", "scholar_vest", "patch_cardi"),
            trousers = listOf("wide_pleat", "cuff_denim", "para_drape"),
            footwear = listOf("lug_loafer", "tabi_avant", "chelsea_bt"),
            accessories = listOf("crossbody_bag", "ear_cuff_pin", "pixel_glasses", "starlight_headphones"),
            palettes = listOf(
                Pair("#38BDF8", "#818CF8"), // Cobalt architect + indigo
                Pair("#0F766E", "#FBBF24"), // Deep petrol teal + warm gold
                Pair("#334155", "#6EE7B7")  // Slate wool + mint contrast
            ),
            auras = listOf("minimal_glow", "mint_matrix", "starlight_fireflies")
        ),
        PersonalityDualityArchetype.ROMANTIC_INDEPENDENT to DualityLookTemplate(
            hairStyles = listOf("vintage_shag", "curly_cloud", "tousled_rebel", "cozy_bun"),
            hairColors = listOf("crimson_flame", "amber_gold", "midnight_indigo", "copper_rust"),
            eyeStyles = listOf("sparkle_round", "mystic_star", "cheerful_arc"),
            eyeColors = listOf("violet_dream", "amber_gold", "emerald_glow"),
            faceFeatures = listOf("tinted_aviator", "star_mark", "pixel_freckles", "soft_blush"),
            expressions = listOf("warm_smile", "wink", "curious"),
            layeredTops = listOf("velvet_mesh", "rebel_leather", "patch_cardi", "sage_kimono"),
            trousers = listOf("flare_cord", "wide_pleat", "cuff_denim"),
            footwear = listOf("chelsea_bt", "combat_boot", "tabi_avant"),
            accessories = listOf("silk_scarf", "silver_chain", "ear_cuff_pin", "amber_scarf"),
            palettes = listOf(
                Pair("#9F1239", "#FBBF24"), // Bordeaux velvet + antique gold
                Pair("#F472B6", "#C084FC"), // Vintage rose + electric orchid
                Pair("#FFB347", "#FF7E67")  // Sunset amber + coral
            ),
            auras = listOf("starlight_fireflies", "amber_halo")
        ),
        PersonalityDualityArchetype.EMOTIONAL_STOIC to DualityLookTemplate(
            hairStyles = listOf("textured_undercut", "braided_crown", "cozy_bun", "tousled_rebel"),
            hairColors = listOf("espresso_noir", "midnight_indigo", "amber_gold", "silver_frost"),
            eyeStyles = listOf("calm_focused", "sparkle_round", "cheerful_arc"),
            eyeColors = listOf("amber_gold", "emerald_glow", "cobalt_sky"),
            faceFeatures = listOf("sharp_stubble", "hero_plaster", "pixel_freckles", "tinted_aviator"),
            expressions = listOf("warm_smile", "determined", "serene"),
            layeredTops = listOf("shear_bomber", "field_flannel", "cozy_hoodie", "blazer_hoodie"),
            trousers = listOf("cuff_denim", "cargo_punk", "wide_pleat"),
            footwear = listOf("moto_buckle", "combat_boot", "retro_high"),
            accessories = listOf("crossbody_bag", "silver_chain", "starlight_headphones", "amber_scarf"),
            palettes = listOf(
                Pair("#B45309", "#FDE68A"), // Shearling cognac + cream fleece
                Pair("#3F6212", "#F97316"), // Olive field jacket + safety orange lining
                Pair("#FFB347", "#FF7E67")  // Warm amber + terracotta
            ),
            auras = listOf("starlight_fireflies", "amber_halo", "minimal_glow")
        ),
        PersonalityDualityArchetype.AVANT_ECLECTIC to DualityLookTemplate(
            hairStyles = listOf("cyber_mullet", "bleached_buzz", "curly_cloud", "vintage_shag"),
            hairColors = listOf("mint_aurora", "silver_frost", "crimson_flame", "amber_gold"),
            eyeStyles = listOf("mystic_star", "cheerful_arc", "calm_focused"),
            eyeColors = listOf("violet_dream", "emerald_glow", "amber_gold"),
            faceFeatures = listOf("visor_clip", "brow_plaster", "star_mark", "tinted_aviator"),
            expressions = listOf("joyful", "wink", "curious"),
            layeredTops = listOf("patch_cardi", "trench_havana", "velvet_mesh", "cyber_jacket"),
            trousers = listOf("para_drape", "tartan_puk", "flare_cord"),
            footwear = listOf("tabi_avant", "retro_high", "lug_loafer"),
            accessories = listOf("studded_belt", "ear_cuff_pin", "crossbody_bag", "crown_sprout"),
            palettes = listOf(
                Pair("#A78BFA", "#34D399"), // Ultraviolet + acid mint
                Pair("#6EE7B7", "#FBBF24"), // Emerald cyber + solar yellow
                Pair("#F87171", "#38BDF8")  // Neon coral + sky blue
            ),
            auras = listOf("mint_matrix", "amber_halo", "starlight_fireflies")
        )
    )

    /**
     * Analyzes the user's Ideal Self profile, personality traits, worldview, ambitions,
     * social behavior, and contradictions to detect their signature Duality Archetype.
     */
    fun detectDualityArchetype(
        idealSelf: IdealSelfProfile,
        traits: PersonalityTraits
    ): Pair<PersonalityDualityArchetype, List<String>> {
        val corpus = buildString {
            append(idealSelf.coreAspiration).append(" ")
            append(idealSelf.lifePillars.joinToString(" ")).append(" ")
            append(idealSelf.growthGoals).append(" ")
            append(idealSelf.emotionalGrounding).append(" ")
            append(idealSelf.reflectionStyle).append(" ")
            append(traits.traitKeywords.joinToString(" "))
        }.lowercase()

        val contradictions = mutableListOf<String>()

        val isIntellectual = traits.analyticalDepth >= 76 ||
            corpus.contains("philosoph") || corpus.contains("architect") ||
            corpus.contains("mastery") || corpus.contains("strateg") || corpus.contains("focus")
        val isRebellious = traits.directness >= 76 ||
            corpus.contains("unconventional") || corpus.contains("bold") ||
            corpus.contains("rebel") || corpus.contains("autonom") || corpus.contains("fearless") ||
            corpus.contains("courage")
        val isCalm = traits.warmth >= 80 && traits.directness < 78 ||
            corpus.contains("calm") || corpus.contains("seren") || corpus.contains("steady") ||
            corpus.contains("mindful") || corpus.contains("stoic")
        val isAmbitious = corpus.contains("ambiti") || corpus.contains("build") ||
            corpus.contains("lead") || corpus.contains("craft") || corpus.contains("impact")
        val isRomanticOrCreative = traits.playfulness >= 75 ||
            corpus.contains("creat") || corpus.contains("art") || corpus.contains("poet") ||
            corpus.contains("warm") || corpus.contains("heart")
        val isIndependent = corpus.contains("independ") || corpus.contains("self-directed") ||
            corpus.contains("solitude") || corpus.contains("freedom")

        if (isIntellectual && isRebellious) {
            contradictions.add("Sharp analytical rigor paired with anti-conformist candor")
        }
        if (isCalm && isAmbitious) {
            contradictions.add("Unshakable inner stillness driving relentless creative ambition")
        }
        if (isRomanticOrCreative && isIndependent) {
            contradictions.add("Deep expressive warmth guarded by fierce personal autonomy")
        }
        if (traits.warmth >= 80 && traits.directness >= 72) {
            contradictions.add("High emotional intelligence delivered with blunt, zero-fluff honesty")
        }
        if (traits.analyticalDepth >= 75 && traits.playfulness >= 72) {
            contradictions.add("Structured systems thinking mixed with irreverent, dry wit")
        }

        if (contradictions.isEmpty()) {
            contradictions.add("Grounded self-assurance balanced with unconventional creative edge")
        }

        val archetype = when {
            isIntellectual && isRebellious -> PersonalityDualityArchetype.INTELLECTUAL_REBEL
            traits.analyticalDepth >= 78 && traits.playfulness >= 76 -> PersonalityDualityArchetype.AVANT_ECLECTIC
            isRomanticOrCreative && isIndependent -> PersonalityDualityArchetype.ROMANTIC_INDEPENDENT
            isCalm && isAmbitious && traits.directness < 76 -> PersonalityDualityArchetype.CALM_AMBITIOUS
            traits.warmth >= 82 && traits.directness >= 74 -> PersonalityDualityArchetype.EMOTIONAL_STOIC
            isRebellious -> PersonalityDualityArchetype.INTELLECTUAL_REBEL
            isIntellectual -> PersonalityDualityArchetype.CALM_AMBITIOUS
            else -> PersonalityDualityArchetype.ROMANTIC_INDEPENDENT
        }

        return archetype to contradictions
    }

    /**
     * Infers the presentation style from the user's text if not explicitly locked,
     * defaulting to the user's current selection so their preference is always respected.
     */
    fun inferPresentationPreference(
        idealSelf: IdealSelfProfile,
        currentPresentation: PresentationStyle
    ): PresentationStyle {
        val text = "${idealSelf.coreAspiration} ${idealSelf.growthGoals}".lowercase()
        return when {
            text.contains("masculine") -> PresentationStyle.MASCULINE
            text.contains("feminine") -> PresentationStyle.FEMININE
            text.contains("androgynous") -> PresentationStyle.ANDROGYNOUS
            else -> currentPresentation
        }
    }

    /**
     * Synthesizes a complete, visually striking unconventional character design from the user's
     * Ideal Self profile and personality traits, while honoring any user-locked customization slots.
     */
    fun synthesizeUnconventionalDesign(
        idealSelf: IdealSelfProfile,
        traits: PersonalityTraits,
        currentAppearance: CharacterAppearance,
        currentClothing: ClothingAndAccessories,
        lockedSlots: Set<CustomizableSlot> = emptySet(),
        variationIndex: Int = 0
    ): UnconventionalStyleBlueprint {
        val (archetype, contradictions) = detectDualityArchetype(idealSelf, traits)
        val template = templatesByDuality[archetype] ?: templatesByDuality.values.first()
        val v = kotlin.math.abs(variationIndex)

        fun <T> pick(list: List<T>, offset: Int = 0): T {
            return list[(v + offset) % list.size]
        }

        val presentation = if (CustomizableSlot.PRESENTATION in lockedSlots) {
            currentAppearance.presentationStyle
        } else {
            inferPresentationPreference(idealSelf, currentAppearance.presentationStyle)
        }

        val nextHairStyle = if (CustomizableSlot.HAIR in lockedSlots) {
            currentAppearance.hairStyleId
        } else {
            pick(template.hairStyles, 0)
        }

        val nextHairColor = if (CustomizableSlot.HAIR in lockedSlots) {
            currentAppearance.hairColorId
        } else {
            pick(template.hairColors, 1)
        }

        val nextFaceFeature = if (CustomizableSlot.FACE in lockedSlots) {
            currentAppearance.faceFeatureId
        } else {
            pick(template.faceFeatures, 0)
        }

        val nextEyeStyle = if (CustomizableSlot.FACE in lockedSlots) {
            currentAppearance.eyeStyleId
        } else {
            pick(template.eyeStyles, 0)
        }

        val nextEyeColor = if (CustomizableSlot.FACE in lockedSlots) {
            currentAppearance.eyeColorId
        } else {
            pick(template.eyeColors, 1)
        }

        val nextExpression = if (CustomizableSlot.FACE in lockedSlots) {
            currentAppearance.expressionDefault
        } else {
            pick(template.expressions, 0)
        }

        val nextTop = if (CustomizableSlot.TOP in lockedSlots) {
            currentClothing.layeredTopId
        } else {
            pick(template.layeredTops, 0)
        }

        val nextTrousers = if (CustomizableSlot.TROUSERS in lockedSlots) {
            currentClothing.trousersId
        } else {
            pick(template.trousers, 1)
        }

        val nextFootwear = if (CustomizableSlot.FOOTWEAR in lockedSlots) {
            currentClothing.footwearId
        } else {
            pick(template.footwear, 2)
        }

        val nextAccessory = if (CustomizableSlot.ACCESSORY in lockedSlots) {
            currentClothing.accessoryId
        } else {
            pick(template.accessories, 0)
        }

        val (nextPrimaryHex, nextAccentHex) = if (CustomizableSlot.PALETTE in lockedSlots) {
            currentClothing.primaryColorHex to currentClothing.accentColorHex
        } else {
            pick(template.palettes, 0)
        }

        val nextAura = if (CustomizableSlot.PALETTE in lockedSlots) {
            currentClothing.auraEffectId
        } else {
            pick(template.auras, 0)
        }

        val updatedAppearance = currentAppearance
            .copy(
                hairStyleId = nextHairStyle,
                hairColorId = nextHairColor,
                eyeStyleId = nextEyeStyle,
                eyeColorId = nextEyeColor,
                expressionDefault = nextExpression
            )
            .withFaceFeature(nextFaceFeature)
            .withPresentation(presentation)

        val updatedClothing = currentClothing
            .withFullOutfit(
                topId = nextTop,
                newTrousersId = nextTrousers,
                newFootwearId = nextFootwear
            )
            .copy(
                primaryColorHex = nextPrimaryHex,
                accentColorHex = nextAccentHex,
                accessoryId = nextAccessory,
                auraEffectId = nextAura
            )

        val rationale = buildString {
            append("${archetype.dualityLabel}: ")
            append(contradictions.first())
            append(". Styled in a ${presentation.label.lowercase()} silhouette with ")
            append("${formatIdLabel(nextTop)} over ${formatIdLabel(nextTrousers)}, ")
            append("grounded by ${formatIdLabel(nextFootwear)} and ${formatIdLabel(nextFaceFeature)}.")
        }

        return UnconventionalStyleBlueprint(
            dualityArchetype = archetype,
            artDirectorRationale = rationale,
            detectedContradictions = contradictions,
            appearance = updatedAppearance,
            clothing = updatedClothing
        )
    }

    private fun formatIdLabel(rawId: String): String {
        return rawId.replace("_", " ")
    }
}
