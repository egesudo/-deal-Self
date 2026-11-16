package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ai.CharacterDesignEngine
import com.example.ai.PersonalityDualityArchetype
import com.example.data.model.CharacterAppearance
import com.example.data.model.ClothingAndAccessories
import com.example.data.model.CustomizableSlot
import com.example.data.model.IdealSelfProfile
import com.example.data.model.PersonalityTraits
import com.example.data.model.PresentationStyle
import kotlin.math.sin

// 5 Core Character Visual States required by the Character Visual System
enum class CharacterVisualState(val label: String) {
    IDLE("Idle"),
    LISTENING("Listening"),
    THINKING("Thinking"),
    SPEAKING("Speaking"),
    REACTING("Reacting")
}

data class PixelOption(
    val id: String,
    val label: String,
    val previewColor: Color = Color.Unspecified
)

data class OutfitComboPreset(
    val id: String,
    val label: String,
    val description: String,
    val outfitId: String,
    val primaryColorHex: String,
    val accentColorHex: String,
    val accessoryId: String,
    val auraEffectId: String
)

data class AiVisualRecommendation(
    val rationale: String,
    val dualityTitle: String = PersonalityDualityArchetype.INTELLECTUAL_REBEL.title,
    val dualityLabel: String = PersonalityDualityArchetype.INTELLECTUAL_REBEL.dualityLabel,
    val detectedContradictions: List<String> = emptyList(),
    val recommendedAppearance: CharacterAppearance,
    val recommendedClothing: ClothingAndAccessories
)

object PixelAssetCatalog {
    val presentationStyles = PresentationStyle.entries.map {
        PixelOption(it.id, it.label)
    }

    val skinTones = listOf(
        PixelOption("warm_ivory", "Warm Ivory", Color(0xFFFFDFC4)),
        PixelOption("golden_tan", "Golden Tan", Color(0xFFE0AC69)),
        PixelOption("deep_umber", "Deep Umber", Color(0xFF8D5524)),
        PixelOption("porcelain_rose", "Porcelain Rose", Color(0xFFFAD2CC)),
        PixelOption("starlight_lilac", "Starlight Lilac", Color(0xFFD8C8F0))
    )

    val faceFeatures = listOf(
        PixelOption("tinted_aviator", "Rose-Tinted Aviators", Color(0xFFF43F5E)),
        PixelOption("round_tortoise", "Tortoise Frames", Color(0xFFD97706)),
        PixelOption("brow_plaster", "Brow Slit + Plaster", Color(0xFFFDE68A)),
        PixelOption("sharp_stubble", "Sculpted Jaw & Stubble", Color(0xFF475569)),
        PixelOption("visor_clip", "Amber Rimless Clip", Color(0xFFFFB347)),
        PixelOption("star_mark", "Starlight Cheek Mark", Color(0xFFFFB347)),
        PixelOption("pixel_freckles", "Sunlit Freckles", Color(0xFFB45309)),
        PixelOption("hero_plaster", "Adventurer Plaster", Color(0xFFFDE68A)),
        PixelOption("soft_blush", "Warm Blush", Color(0xFFFF8FA3)),
        PixelOption("clean", "Crisp Minimal")
    )

    val hairStyles = listOf(
        PixelOption("tousled_rebel", "Tousled Rebel Spikes"),
        PixelOption("textured_undercut", "Disconnected Undercut"),
        PixelOption("vintage_shag", "70s Layered Shag"),
        PixelOption("braided_crown", "Braids & Silver Cuffs"),
        PixelOption("bleached_buzz", "Platinum Razor Buzz"),
        PixelOption("cyber_mullet", "Modern Wolf Mullet"),
        PixelOption("starlight_bob", "Asymmetric Bob"),
        PixelOption("spiky_hero", "Wind-Swept Spikes"),
        PixelOption("cozy_bun", "Painter Top-Knot"),
        PixelOption("cyber_swept", "Razor Side Fringe"),
        PixelOption("curly_cloud", "Textured Halo Curls")
    )

    val hairColors = listOf(
        PixelOption("midnight_indigo", "Midnight Indigo", Color(0xFF2B3252)),
        PixelOption("crimson_flame", "Oxblood Flame", Color(0xFFE11D48)),
        PixelOption("amber_gold", "Bleached Honey", Color(0xFFFFB347)),
        PixelOption("silver_frost", "Platinum Ash", Color(0xFFE2E8F0)),
        PixelOption("espresso_noir", "Espresso Noir", Color(0xFF1E1B18)),
        PixelOption("copper_rust", "Copper Rust", Color(0xFFC2410C)),
        PixelOption("mint_aurora", "Acid Mint", Color(0xFF5CE6B5))
    )

    val eyeStyles = listOf(
        PixelOption("sparkle_round", "Expressive Round"),
        PixelOption("calm_focused", "Half-Lid Focused"),
        PixelOption("cheerful_arc", "Wry Arc"),
        PixelOption("mystic_star", "Sharp Starlight")
    )

    val eyeColors = listOf(
        PixelOption("amber_gold", "Amber Gold", Color(0xFFFFB347)),
        PixelOption("emerald_glow", "Emerald", Color(0xFF34D399)),
        PixelOption("cobalt_sky", "Cobalt", Color(0xFF60A5FA)),
        PixelOption("violet_dream", "Violet", Color(0xFFA78BFA)),
        PixelOption("crimson_ember", "Crimson Ember", Color(0xFFF43F5E))
    )

    val expressions = listOf(
        PixelOption("warm_smile", "Confident Smirk"),
        PixelOption("determined", "Deadpan Focused"),
        PixelOption("curious", "Raised-Brow Curious"),
        PixelOption("serene", "Unbothered Calm"),
        PixelOption("joyful", "Wild Grin"),
        PixelOption("wink", "Knowing Wink")
    )

    val outfits = listOf(
        PixelOption("rebel_leather", "Cropped Leather + Graphic Tee", Color(0xFFE11D48)),
        PixelOption("trench_havana", "Oversized Trench + Havana Shirt", Color(0xFFD97706)),
        PixelOption("blazer_hoodie", "Vintage Blazer + Street Hoodie", Color(0xFF64748B)),
        PixelOption("patch_cardi", "Patchwork Cardigan + Tank", Color(0xFFA78BFA)),
        PixelOption("shear_bomber", "Shearling Bomber + Henley", Color(0xFFB45309)),
        PixelOption("velvet_mesh", "Velvet Smoking Jacket + Mesh", Color(0xFF9F1239)),
        PixelOption("field_flannel", "Field Jacket + Open Plaid", Color(0xFF4D7C0F)),
        PixelOption("denim_turtle", "Acid-Wash Denim + Turtleneck", Color(0xFF38BDF8)),
        PixelOption("cozy_hoodie", "Lo-Fi Layered Hoodie", Color(0xFFFFB347)),
        PixelOption("scholar_vest", "Tailored Architect Vest", Color(0xFF38BDF8)),
        PixelOption("cyber_jacket", "Asymmetric Moto Tech", Color(0xFFF43F5E)),
        PixelOption("sage_kimono", "Draped Haori Coat", Color(0xFF6EE7B7))
    )

    val trousers = listOf(
        PixelOption("wide_pleat", "Wide-Leg Pleated Wool", Color(0xFF334155)),
        PixelOption("cargo_punk", "Distressed Multi-Pocket Cargo", Color(0xFF3F6212)),
        PixelOption("cuff_denim", "Raw Selvedge Cuffed Denim", Color(0xFF1E3A8A)),
        PixelOption("tartan_puk", "Split-Plaid Tartan Punk", Color(0xFF991B1B)),
        PixelOption("para_drape", "Volume Parachute Trousers", Color(0xFF475569)),
        PixelOption("flare_cord", "70s Flared Corduroy", Color(0xFF78350F))
    )

    val footwear = listOf(
        PixelOption("combat_boot", "Heavy Lug-Sole Combat Boots", Color(0xFF18181B)),
        PixelOption("chelsea_bt", "Scuffed Pointed Chelsea Boots", Color(0xFF451A03)),
        PixelOption("retro_high", "80s Color-Blocked High-Tops", Color(0xFFE11D48)),
        PixelOption("lug_loafer", "Platform Loafers + Socks", Color(0xFF1E293B)),
        PixelOption("moto_buckle", "Buckled Engineer Moto Boots", Color(0xFF27272A)),
        PixelOption("tabi_avant", "Architectural Split-Toe Boots", Color(0xFF0F172A))
    )

    val outfitPalettes = listOf(
        Pair("#5D212B", "#839083"), // Signature Deep Burgundy + Muted Sage Grey
        Pair("#38BDF8", "#818CF8"), // Cobalt Architect + Indigo
        Pair("#5D212B", "#F2EDE2"), // Deep Burgundy + Warm Cream Beige
        Pair("#839083", "#F2EDE2"), // Muted Sage Grey + Warm Cream Beige
        Pair("#B45309", "#FDE68A"), // Shearling Cognac + Cream Fleece
        Pair("#9F1239", "#F472B6"), // Bordeaux Velvet + Rose Tint
        Pair("#1E293B", "#F43F5E"), // Charcoal Tweed + Punk Rose
        Pair("#A78BFA", "#34D399")  // Ultraviolet + Emerald
    )

    val accessories = listOf(
        PixelOption("crossbody_bag", "Leather Crossbody Bag"),
        PixelOption("silver_chain", "Heavy Silver Curb Chain"),
        PixelOption("studded_belt", "Double-Studded Belt + Chain"),
        PixelOption("silk_scarf", "Patterned Silk Neck Scarf"),
        PixelOption("ear_cuff_pin", "Brass Ear Cuff + Lapel Pin"),
        PixelOption("pixel_glasses", "Retro Tinted Frames"),
        PixelOption("starlight_headphones", "Brushed-Steel Headphones"),
        PixelOption("amber_scarf", "Oversized Draped Knit Scarf"),
        PixelOption("crown_sprout", "Botanical Rebel Pin"),
        PixelOption("none", "Clean Silhouette")
    )

    val auraEffects = listOf(
        PixelOption("starlight_fireflies", "Burgundy Embers", Color(0xFF5D212B)),
        PixelOption("amber_halo", "Sage Studio Halo", Color(0xFF839083)),
        PixelOption("mint_matrix", "Warm Cream Glow", Color(0xFFD6CFC0)),
        PixelOption("minimal_glow", "Subtle Rimlight", Color(0xFF6F7D70))
    )

    val outfitComboPresets = listOf(
        OutfitComboPreset(
            id = "rebel_counter_philosopher",
            label = "Counter-Culture Rebel",
            description = "Deep burgundy cropped leather + sage tee + wide pleated trousers + combat boots",
            outfitId = "rebel_leather:wide_pleat:combat_boot",
            primaryColorHex = "#5D212B",
            accentColorHex = "#839083",
            accessoryId = "silver_chain",
            auraEffectId = "amber_halo"
        ),
        OutfitComboPreset(
            id = "midnight_scholar",
            label = "Calm Architect",
            description = "Tailored architect layering + pleated wool trousers + platform loafers",
            outfitId = "scholar_vest:wide_pleat:lug_loafer",
            primaryColorHex = "#38BDF8",
            accentColorHex = "#818CF8",
            accessoryId = "pixel_glasses",
            auraEffectId = "minimal_glow"
        ),
        OutfitComboPreset(
            id = "velvet_nomad",
            label = "Velvet Drifter",
            description = "Oversized trench + patterned Havana shirt + flared corduroy + Chelsea boots",
            outfitId = "trench_havana:flare_cord:chelsea_bt",
            primaryColorHex = "#B45309",
            accentColorHex = "#FDE68A",
            accessoryId = "crossbody_bag",
            auraEffectId = "starlight_fireflies"
        ),
        OutfitComboPreset(
            id = "cozy_lofi_dreamer",
            label = "Afterhours Vinyl Producer",
            description = "Vintage tweed blazer over street hoodie + cuffed selvedge denim + retro high-tops",
            outfitId = "blazer_hoodie:cuff_denim:retro_high",
            primaryColorHex = "#FFB347",
            accentColorHex = "#FF7E67",
            accessoryId = "starlight_headphones",
            auraEffectId = "starlight_fireflies"
        ),
        OutfitComboPreset(
            id = "zen_sage",
            label = "Avant-Garde Alchemist",
            description = "Asymmetric patchwork cardigan + parachute trousers + split-toe boots",
            outfitId = "patch_cardi:para_drape:tabi_avant",
            primaryColorHex = "#6EE7B7",
            accentColorHex = "#FBBF24",
            accessoryId = "studded_belt",
            auraEffectId = "mint_matrix"
        ),
        OutfitComboPreset(
            id = "solar_vanguard",
            label = "Industrial Punk Maverick",
            description = "Moto jacket + split-plaid Tartan trousers + buckled engineer boots",
            outfitId = "cyber_jacket:tartan_puk:moto_buckle",
            primaryColorHex = "#F87171",
            accentColorHex = "#FBBF24",
            accessoryId = "silver_chain",
            auraEffectId = "amber_halo"
        )
    )

    /**
     * Generates an AI-informed unconventional visual style recommendation based on the user's
     * Ideal-Self personality traits, dualities, and aspirations.
     */
    fun recommendVisualsForProfile(
        idealSelf: IdealSelfProfile,
        traits: PersonalityTraits,
        currentAppearance: CharacterAppearance,
        currentClothing: ClothingAndAccessories = ClothingAndAccessories(),
        lockedSlots: Set<CustomizableSlot> = emptySet(),
        variationIndex: Int = 0
    ): AiVisualRecommendation {
        val blueprint = CharacterDesignEngine.synthesizeUnconventionalDesign(
            idealSelf = idealSelf,
            traits = traits,
            currentAppearance = currentAppearance,
            currentClothing = currentClothing,
            lockedSlots = lockedSlots,
            variationIndex = variationIndex
        )
        return AiVisualRecommendation(
            rationale = blueprint.artDirectorRationale,
            dualityTitle = blueprint.dualityArchetype.title,
            dualityLabel = blueprint.dualityArchetype.dualityLabel,
            detectedContradictions = blueprint.detectedContradictions,
            recommendedAppearance = blueprint.appearance,
            recommendedClothing = blueprint.clothing
        )
    }

    fun resolveSkinColor(id: String): Color =
        skinTones.find { it.id == id }?.previewColor ?: Color(0xFFFFDFC4)

    fun resolveHairColor(id: String): Color =
        hairColors.find { it.id == id }?.previewColor ?: Color(0xFF2B3252)

    fun resolveEyeColor(id: String): Color =
        eyeColors.find { it.id == id }?.previewColor ?: Color(0xFFFFB347)

    fun parseHexColor(hex: String, fallback: Color): Color {
        return try {
            val cleaned = hex.removePrefix("#")
            val longVal = cleaned.toLong(16)
            if (cleaned.length == 6) {
                Color(0xFF000000 or longVal)
            } else {
                fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}

@Composable
fun PixelCharacterSprite(
    appearance: CharacterAppearance,
    clothing: ClothingAndAccessories,
    modifier: Modifier = Modifier,
    visualState: CharacterVisualState? = null,
    isSpeaking: Boolean = false,
    isListening: Boolean = false,
    activeEmotion: String? = null,
    showStudioFrame: Boolean = true
) {
    val resolvedVisualState = visualState ?: when {
        isSpeaking -> CharacterVisualState.SPEAKING
        isListening -> CharacterVisualState.LISTENING
        else -> CharacterVisualState.IDLE
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pixel_companion_anim")
    val animPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val talkFrame by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 320, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "talk_frame"
    )

    val skinColor = PixelAssetCatalog.resolveSkinColor(appearance.skinToneId)
    val skinShadow = Color(
        red = (skinColor.red * 0.84f).coerceIn(0f, 1f),
        green = (skinColor.green * 0.79f).coerceIn(0f, 1f),
        blue = (skinColor.blue * 0.77f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val hairColor = PixelAssetCatalog.resolveHairColor(appearance.hairColorId)
    val hairShadow = Color(
        red = (hairColor.red * 0.72f).coerceIn(0f, 1f),
        green = (hairColor.green * 0.72f).coerceIn(0f, 1f),
        blue = (hairColor.blue * 0.75f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val hairHighlight = Color(
        red = (hairColor.red + 0.18f).coerceIn(0f, 1f),
        green = (hairColor.green + 0.18f).coerceIn(0f, 1f),
        blue = (hairColor.blue + 0.20f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val eyeColor = PixelAssetCatalog.resolveEyeColor(appearance.eyeColorId)
    val outfitPrimary = PixelAssetCatalog.parseHexColor(clothing.primaryColorHex, Color(0xFF5D212B))
    val outfitPrimaryDark = Color(
        red = (outfitPrimary.red * 0.72f).coerceIn(0f, 1f),
        green = (outfitPrimary.green * 0.72f).coerceIn(0f, 1f),
        blue = (outfitPrimary.blue * 0.75f).coerceIn(0f, 1f),
        alpha = 1f
    )
    val outfitAccent = PixelAssetCatalog.parseHexColor(clothing.accentColorHex, Color(0xFF839083))
    val expression = activeEmotion ?: appearance.expressionDefault
    val faceFeature = appearance.faceFeatureId
    val presentation = appearance.presentationStyle
    val layeredTop = clothing.layeredTopId
    val trousersId = clothing.trousersId
    val footwearId = clothing.footwearId

    val containerModifier = if (showStudioFrame) {
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF8F5EE),
                        Color(0xFFE4EAE3),
                        Color(0xFFCFD8CE)
                    )
                )
            )
            .border(1.5.dp, Color(0xFF839083), RoundedCornerShape(20.dp))
    } else {
        modifier
    }

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 32x32 Full-Body Indie RPG Pixel Grid
            val gridCount = 32
            val minDim = size.minDimension
            val cell = minDim / gridCount
            val originX = (size.width - minDim) / 2f
            val originY = (size.height - minDim) / 2f

            val bobOffsetCells = when (resolvedVisualState) {
                CharacterVisualState.REACTING -> if ((animPhase.toInt() / 8) % 2 == 0) -1 else 0
                CharacterVisualState.SPEAKING -> if ((talkFrame.toInt() % 2) == 0) 0 else 1
                else -> if ((animPhase.toInt() / 25) % 2 == 0) 0 else 1
            }
            val isBlinking = (animPhase.toInt() % 52) in 49..51

            fun drawPx(
                gx: Int,
                gy: Int,
                w: Int = 1,
                h: Int = 1,
                color: Color,
                applyBob: Boolean = true
            ) {
                val finalY = gy + if (applyBob) bobOffsetCells else 0
                drawRect(
                    color = color,
                    topLeft = Offset(originX + gx * cell, originY + finalY * cell),
                    size = Size(w * cell + 0.65f, h * cell + 0.65f)
                )
            }

            // 1. Background Aura & Visual State Indicators
            val auraColor = when (clothing.auraEffectId) {
                "amber_halo" -> Color(0xFF839083)
                "mint_matrix" -> Color(0xFF4F5D50)
                "minimal_glow" -> Color(0xFF839083)
                else -> Color(0xFF5D212B)
            }

            when (resolvedVisualState) {
                CharacterVisualState.LISTENING -> {
                    val pulseRadius = minDim * (0.40f + 0.04f * sin(animPhase * 0.25f))
                    drawCircle(
                        color = Color(0xFF839083).copy(alpha = 0.28f),
                        radius = pulseRadius,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                    val waveTick = (animPhase.toInt() / 6) % 2
                    drawPx(4, 9 + waveTick, 1, 4, Color(0xFF4F5D50), applyBob = false)
                    drawPx(27, 9 + (1 - waveTick), 1, 4, Color(0xFF4F5D50), applyBob = false)
                }
                CharacterVisualState.SPEAKING -> {
                    val pulseRadius = minDim * (0.42f + 0.04f * sin(animPhase * 0.35f))
                    drawCircle(
                        color = Color(0xFF5D212B).copy(alpha = 0.20f),
                        radius = pulseRadius,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
                CharacterVisualState.THINKING -> {
                    val dotCount = ((animPhase.toInt() / 10) % 3) + 1
                    drawPx(23, 5, 1, 1, Color.White.copy(alpha = 0.75f), applyBob = false)
                    drawPx(24, 2, 6, 3, Color.White.copy(alpha = 0.92f), applyBob = false)
                    for (d in 0 until dotCount) {
                        drawPx(25 + d, 3, 1, 1, Color(0xFF1E293B), applyBob = false)
                    }
                }
                CharacterVisualState.REACTING -> {
                    val reactColor = Color(0xFFFF7E67)
                    drawPx(24, 2, 1, 1, reactColor, applyBob = false)
                    drawPx(26, 2, 1, 1, reactColor, applyBob = false)
                    drawPx(24, 3, 3, 1, reactColor, applyBob = false)
                    drawPx(25, 4, 1, 1, reactColor, applyBob = false)
                }
                CharacterVisualState.IDLE -> {}
            }

            if (clothing.auraEffectId == "amber_halo") {
                drawPx(10, 2, 12, 1, auraColor.copy(alpha = 0.78f), applyBob = false)
                drawPx(9, 3, 1, 1, auraColor.copy(alpha = 0.6f), applyBob = false)
                drawPx(22, 3, 1, 1, auraColor.copy(alpha = 0.6f), applyBob = false)
            } else if (clothing.auraEffectId == "starlight_fireflies" || clothing.auraEffectId == "mint_matrix") {
                val sparkShift = (animPhase.toInt() / 12) % 2
                drawPx(4, 6 + sparkShift, 1, 1, auraColor.copy(alpha = 0.85f), applyBob = false)
                drawPx(27, 8 - sparkShift, 1, 1, auraColor.copy(alpha = 0.85f), applyBob = false)
                drawPx(5, 20 - sparkShift, 1, 1, auraColor.copy(alpha = 0.75f), applyBob = false)
                drawPx(26, 19 + sparkShift, 1, 1, auraColor.copy(alpha = 0.75f), applyBob = false)
            }

            // Ground shadow under boots (row 30)
            drawPx(9, 30, 14, 1, Color(0x66000000), applyBob = false)

            // 2. Back Hair Silhouette (rows 4..15)
            when (appearance.hairStyleId) {
                "vintage_shag", "cyber_mullet" -> {
                    drawPx(9, 6, 14, 10, hairShadow)
                    drawPx(8, 10, 2, 6, hairColor)
                    drawPx(22, 10, 2, 6, hairColor)
                }
                "curly_cloud" -> {
                    drawPx(8, 5, 16, 11, hairShadow)
                    drawPx(7, 7, 18, 7, hairColor)
                }
                "braided_crown" -> {
                    drawPx(9, 6, 14, 9, hairShadow)
                    drawPx(9, 13, 2, 4, hairColor)
                    drawPx(21, 13, 2, 4, hairColor)
                    // Metallic braid cuffs
                    drawPx(9, 15, 2, 1, Color(0xFFFBBF24))
                    drawPx(21, 15, 2, 1, Color(0xFFFBBF24))
                }
                "cozy_bun" -> {
                    drawPx(13, 2, 6, 3, hairColor)
                    drawPx(14, 2, 3, 1, hairHighlight)
                    drawPx(10, 6, 12, 8, hairShadow)
                }
                "starlight_bob" -> {
                    drawPx(9, 6, 14, 9, hairColor)
                    drawPx(8, 8, 16, 6, hairShadow)
                }
                else -> {
                    drawPx(10, 6, 12, 7, hairShadow)
                }
            }

            // 3. Trousers & Lower Silhouette (rows 22..27)
            val trouserBase = when (trousersId) {
                "wide_pleat" -> Color(0xFF27303F)
                "cargo_punk" -> Color(0xFF364829)
                "cuff_denim" -> Color(0xFF1E3A5F)
                "tartan_puk" -> Color(0xFF7F1D1D)
                "para_drape" -> Color(0xFF334155)
                "flare_cord" -> Color(0xFF6B3410)
                else -> Color(0xFF27303F)
            }
            val trouserShadow = Color(
                red = (trouserBase.red * 0.76f).coerceIn(0f, 1f),
                green = (trouserBase.green * 0.76f).coerceIn(0f, 1f),
                blue = (trouserBase.blue * 0.78f).coerceIn(0f, 1f),
                alpha = 1f
            )

            // Waistband & hip block
            drawPx(11, 22, 10, 1, trouserShadow)

            when (trousersId) {
                "wide_pleat" -> {
                    // Wide-leg architectural wool trousers with front crease
                    drawPx(10, 23, 5, 4, trouserBase)
                    drawPx(17, 23, 5, 4, trouserBase)
                    drawPx(12, 23, 1, 4, trouserShadow)
                    drawPx(19, 23, 1, 4, trouserShadow)
                }
                "cargo_punk" -> {
                    // Multi-pocket cargo pants with side pocket volume
                    drawPx(11, 23, 4, 4, trouserBase)
                    drawPx(17, 23, 4, 4, trouserBase)
                    drawPx(9, 24, 2, 2, trouserShadow)
                    drawPx(21, 24, 2, 2, trouserShadow)
                    // Knee strap accent
                    drawPx(11, 25, 4, 1, outfitAccent.copy(alpha = 0.75f))
                }
                "cuff_denim" -> {
                    // Raw selvedge denim with high turn-up cuffs & red selvedge tick
                    drawPx(11, 23, 4, 3, trouserBase)
                    drawPx(17, 23, 4, 3, trouserBase)
                    drawPx(11, 26, 4, 1, Color(0xFFCBD5E1))
                    drawPx(17, 26, 4, 1, Color(0xFFCBD5E1))
                    drawPx(12, 26, 1, 1, Color(0xFFE11D48))
                    drawPx(19, 26, 1, 1, Color(0xFFE11D48))
                }
                "tartan_puk" -> {
                    // Split-plaid punk trousers
                    drawPx(11, 23, 4, 4, trouserBase)
                    drawPx(17, 23, 4, 4, Color(0xFF1E293B))
                    drawPx(12, 23, 1, 4, Color(0xFFFBBF24).copy(alpha = 0.7f))
                    drawPx(11, 25, 4, 1, Color(0xFFFBBF24).copy(alpha = 0.7f))
                    drawPx(18, 24, 2, 1, Color(0xFFE11D48))
                }
                "para_drape" -> {
                    // Volume parachute trousers
                    drawPx(9, 23, 6, 3, trouserBase)
                    drawPx(17, 23, 6, 3, trouserBase)
                    drawPx(11, 26, 4, 1, trouserShadow)
                    drawPx(17, 26, 4, 1, trouserShadow)
                }
                "flare_cord" -> {
                    // 70s flared corduroy trousers
                    drawPx(11, 23, 4, 2, trouserBase)
                    drawPx(17, 23, 4, 2, trouserBase)
                    drawPx(10, 25, 5, 2, trouserBase)
                    drawPx(17, 25, 5, 2, trouserBase)
                    drawPx(12, 24, 1, 3, trouserShadow)
                    drawPx(19, 24, 1, 3, trouserShadow)
                }
            }

            // 4. Boots & Distinctive Footwear (rows 27..29, grounded on floor)
            when (footwearId) {
                "combat_boot" -> {
                    // Chunky lug-sole lace-up combat boots
                    drawPx(10, 27, 5, 2, Color(0xFF18181B), applyBob = false)
                    drawPx(17, 27, 5, 2, Color(0xFF18181B), applyBob = false)
                    drawPx(9, 29, 6, 1, Color(0xFF09090B), applyBob = false)
                    drawPx(17, 29, 6, 1, Color(0xFF09090B), applyBob = false)
                    // Silver eyelets
                    drawPx(12, 27, 1, 2, Color(0xFFCBD5E1), applyBob = false)
                    drawPx(19, 27, 1, 2, Color(0xFFCBD5E1), applyBob = false)
                }
                "chelsea_bt" -> {
                    // Pointed leather Chelsea boots with heel block
                    drawPx(10, 27, 5, 2, Color(0xFF451A03), applyBob = false)
                    drawPx(17, 27, 5, 2, Color(0xFF451A03), applyBob = false)
                    drawPx(9, 28, 1, 1, Color(0xFF451A03), applyBob = false)
                    drawPx(22, 28, 1, 1, Color(0xFF451A03), applyBob = false)
                    drawPx(9, 29, 6, 1, Color(0xFF1C1917), applyBob = false)
                    drawPx(17, 29, 6, 1, Color(0xFF1C1917), applyBob = false)
                }
                "retro_high" -> {
                    // Color-blocked 80s high-top sneakers
                    drawPx(10, 27, 5, 2, Color.White, applyBob = false)
                    drawPx(17, 27, 5, 2, Color.White, applyBob = false)
                    drawPx(11, 27, 3, 1, outfitPrimary, applyBob = false)
                    drawPx(18, 27, 3, 1, outfitPrimary, applyBob = false)
                    drawPx(9, 29, 6, 1, outfitAccent, applyBob = false)
                    drawPx(17, 29, 6, 1, outfitAccent, applyBob = false)
                }
                "lug_loafer" -> {
                    // Contrast socks + platform penny loafers
                    drawPx(11, 27, 4, 1, Color.White, applyBob = false)
                    drawPx(17, 27, 4, 1, Color.White, applyBob = false)
                    drawPx(10, 28, 5, 1, Color(0xFF1E293B), applyBob = false)
                    drawPx(17, 28, 5, 1, Color(0xFF1E293B), applyBob = false)
                    drawPx(9, 29, 6, 1, Color(0xFF0F172A), applyBob = false)
                    drawPx(17, 29, 6, 1, Color(0xFF0F172A), applyBob = false)
                }
                "moto_buckle" -> {
                    // Buckled engineer moto boots
                    drawPx(10, 27, 5, 2, Color(0xFF27272A), applyBob = false)
                    drawPx(17, 27, 5, 2, Color(0xFF27272A), applyBob = false)
                    drawPx(11, 28, 2, 1, Color(0xFFFBBF24), applyBob = false)
                    drawPx(19, 28, 2, 1, Color(0xFFFBBF24), applyBob = false)
                    drawPx(9, 29, 6, 1, Color(0xFF09090B), applyBob = false)
                    drawPx(17, 29, 6, 1, Color(0xFF09090B), applyBob = false)
                }
                "tabi_avant" -> {
                    // Architectural split-toe boots
                    drawPx(10, 27, 5, 2, Color(0xFF0F172A), applyBob = false)
                    drawPx(17, 27, 5, 2, Color(0xFF0F172A), applyBob = false)
                    drawPx(10, 29, 2, 1, Color(0xFFE2E8F0), applyBob = false)
                    drawPx(13, 29, 2, 1, Color(0xFFE2E8F0), applyBob = false)
                    drawPx(17, 29, 2, 1, Color(0xFFE2E8F0), applyBob = false)
                    drawPx(20, 29, 2, 1, Color(0xFFE2E8F0), applyBob = false)
                }
            }

            // 5. Upper Torso, Presentation Silhouette & Layered Outerwear (rows 15..22)
            // Neck & collarbone shadow
            drawPx(14, 15, 4, 1, skinShadow)

            // Presentation silhouette widths
            val torsoLeft = when (presentation) {
                PresentationStyle.MASCULINE -> 9
                PresentationStyle.FEMININE -> 10
                PresentationStyle.ANDROGYNOUS -> 10
            }
            val torsoWidth = when (presentation) {
                PresentationStyle.MASCULINE -> 14
                PresentationStyle.FEMININE -> 12
                PresentationStyle.ANDROGYNOUS -> 12
            }

            // Base torso & sleeves
            drawPx(torsoLeft, 16, torsoWidth, 6, outfitPrimary)
            if (presentation == PresentationStyle.FEMININE) {
                // Sculpted waist-to-hip taper on row 20..21
                drawPx(11, 20, 10, 2, outfitPrimaryDark)
            }
            // Left & right sleeves + hands
            drawPx(torsoLeft - 2, 16, 2, 5, outfitPrimaryDark)
            drawPx(torsoLeft + torsoWidth, 16, 2, 5, outfitPrimaryDark)
            drawPx(torsoLeft - 2, 21, 2, 1, skinColor)
            drawPx(torsoLeft + torsoWidth, 21, 2, 1, skinColor)

            // Layered Upper-Wear Styling Details
            when (layeredTop) {
                "rebel_leather", "cyber_jacket" -> {
                    // Inner graphic tee layer
                    drawPx(13, 16, 6, 6, Color(0xFF18181B))
                    drawPx(14, 17, 4, 2, outfitAccent) // Graphic print on tee
                    // Asymmetrical moto lapels & silver studs
                    drawPx(11, 16, 2, 4, outfitPrimaryDark)
                    drawPx(19, 16, 2, 4, outfitPrimaryDark)
                    drawPx(11, 17, 1, 1, Color(0xFFE2E8F0))
                    drawPx(20, 17, 1, 1, Color(0xFFE2E8F0))
                }
                "trench_havana" -> {
                    // Open patterned Havana shirt inside
                    drawPx(13, 16, 6, 6, outfitAccent)
                    drawPx(14, 16, 4, 2, skinColor) // Open V-neck collar
                    drawPx(13, 19, 1, 1, outfitPrimaryDark)
                    drawPx(17, 18, 1, 1, outfitPrimaryDark)
                    // Long trench coat tails extending over trousers (rows 22..25)
                    drawPx(9, 22, 3, 4, outfitPrimary)
                    drawPx(20, 22, 3, 4, outfitPrimary)
                }
                "blazer_hoodie" -> {
                    // Inner street hoodie + drawstrings + tailored blazer lapels
                    drawPx(13, 16, 6, 6, outfitAccent)
                    drawPx(12, 15, 8, 1, outfitAccent) // Hoodie collar roll
                    drawPx(14, 17, 1, 3, Color.White)
                    drawPx(17, 17, 1, 3, Color.White)
                    drawPx(11, 16, 2, 6, outfitPrimaryDark)
                    drawPx(19, 16, 2, 6, outfitPrimaryDark)
                }
                "patch_cardi", "sage_kimono" -> {
                    // Asymmetric patchwork cardigan over fitted tank
                    drawPx(14, 16, 4, 6, Color(0xFFF8FAFC))
                    drawPx(10, 16, 4, 6, outfitPrimary)
                    drawPx(18, 16, 4, 6, outfitAccent)
                    drawPx(10, 19, 3, 2, outfitAccent)
                    drawPx(19, 17, 2, 2, outfitPrimary)
                }
                "shear_bomber" -> {
                    // Cream shearling collar + thermal henley
                    drawPx(11, 15, 10, 2, Color(0xFFFEF3C7))
                    drawPx(14, 17, 4, 5, Color(0xFFE2E8F0))
                    drawPx(11, 21, 10, 1, Color(0xFFFEF3C7))
                }
                "velvet_mesh" -> {
                    // High-neck dark mesh top + velvet smoking lapels
                    drawPx(13, 15, 6, 7, Color(0xFF1E1B4B))
                    drawPx(12, 16, 2, 5, outfitAccent)
                    drawPx(18, 16, 2, 5, outfitAccent)
                }
                "field_flannel" -> {
                    // Open plaid flannel shirt + utility field pockets
                    drawPx(13, 16, 6, 6, outfitAccent)
                    drawPx(14, 17, 4, 1, Color(0xFF1E293B))
                    drawPx(14, 19, 4, 1, Color(0xFF1E293B))
                    drawPx(10, 18, 2, 2, outfitPrimaryDark)
                    drawPx(20, 18, 2, 2, outfitPrimaryDark)
                }
                "denim_turtle", "scholar_vest" -> {
                    // Architect high turtleneck + structured outer vest/denim
                    drawPx(13, 15, 6, 7, Color(0xFF0F172A))
                    drawPx(12, 16, 2, 6, outfitAccent)
                    drawPx(18, 16, 2, 6, outfitAccent)
                }
                else -> {
                    // cozy_hoodie
                    drawPx(11, 16, 10, 1, outfitAccent)
                    drawPx(14, 17, 1, 3, Color.White)
                    drawPx(17, 17, 1, 3, Color.White)
                    drawPx(12, 19, 8, 2, outfitAccent.copy(alpha = 0.75f))
                }
            }

            // 6. Waist Belts, Bags & Body Accessories
            when (clothing.accessoryId) {
                "crossbody_bag" -> {
                    val leatherBag = Color(0xFF78350F)
                    val buckle = Color(0xFFFBBF24)
                    // Diagonal strap from left shoulder to right hip
                    drawPx(11, 16, 2, 1, leatherBag)
                    drawPx(13, 17, 2, 1, leatherBag)
                    drawPx(15, 18, 2, 1, leatherBag)
                    drawPx(16, 19, 1, 1, buckle)
                    drawPx(17, 20, 2, 1, leatherBag)
                    // Hip satchel
                    drawPx(19, 21, 4, 3, leatherBag)
                    drawPx(20, 22, 1, 1, buckle)
                }
                "silver_chain" -> {
                    val silver = Color(0xFFE2E8F0)
                    // Layered curb chain at collar
                    drawPx(13, 16, 6, 1, silver)
                    drawPx(14, 17, 4, 1, Color(0xFF94A3B8))
                    // Hanging wallet chain on trouser hip
                    drawPx(18, 22, 1, 3, silver)
                    drawPx(19, 24, 2, 1, silver)
                }
                "studded_belt" -> {
                    drawPx(11, 22, 10, 1, Color(0xFF18181B))
                    drawPx(12, 22, 1, 1, Color(0xFFFBBF24))
                    drawPx(15, 22, 2, 1, Color(0xFFE2E8F0))
                    drawPx(19, 22, 1, 1, Color(0xFFFBBF24))
                    // Hanging belt tail
                    drawPx(13, 23, 1, 3, Color(0xFF18181B))
                }
                "silk_scarf", "amber_scarf" -> {
                    drawPx(11, 15, 10, 2, outfitAccent)
                    drawPx(18, 17, 2, 3, outfitPrimary)
                }
            }

            // 7. Head & Face Base (rows 6..14)
            drawPx(10, 6, 12, 9, skinColor)
            drawPx(9, 8, 1, 5, skinColor)
            drawPx(22, 8, 1, 5, skinColor)
            drawPx(10, 14, 12, 1, skinShadow)

            if (presentation == PresentationStyle.MASCULINE) {
                // Stronger angular jaw shadow
                drawPx(10, 13, 2, 1, skinShadow)
                drawPx(20, 13, 2, 1, skinShadow)
            }

            // 8. Eyes Layer (rows 9..11)
            if (isBlinking || expression == "serene") {
                drawPx(11, 10, 3, 1, Color(0xFF1E293B))
                drawPx(18, 10, 3, 1, Color(0xFF1E293B))
            } else if (expression == "wink") {
                drawPx(11, 9, 3, 2, eyeColor)
                drawPx(11, 9, 1, 1, Color.White)
                drawPx(18, 10, 3, 1, Color(0xFF1E293B))
            } else if (resolvedVisualState == CharacterVisualState.THINKING) {
                drawPx(11, 9, 3, 2, eyeColor)
                drawPx(13, 9, 1, 1, Color.White)
                drawPx(18, 9, 3, 2, eyeColor)
                drawPx(20, 9, 1, 1, Color.White)
            } else {
                when (appearance.eyeStyleId) {
                    "cheerful_arc" -> {
                        drawPx(11, 9, 3, 1, eyeColor)
                        drawPx(11, 10, 1, 1, eyeColor)
                        drawPx(13, 10, 1, 1, eyeColor)
                        drawPx(18, 9, 3, 1, eyeColor)
                        drawPx(18, 10, 1, 1, eyeColor)
                        drawPx(20, 10, 1, 1, eyeColor)
                    }
                    "calm_focused" -> {
                        drawPx(11, 9, 3, 1, Color(0xFF1E293B))
                        drawPx(12, 10, 2, 1, eyeColor)
                        drawPx(18, 9, 3, 1, Color(0xFF1E293B))
                        drawPx(18, 10, 2, 1, eyeColor)
                    }
                    else -> {
                        drawPx(11, 9, 3, 2, eyeColor)
                        drawPx(11, 9, 1, 1, Color.White)
                        drawPx(18, 9, 3, 2, eyeColor)
                        drawPx(18, 9, 1, 1, Color.White)
                    }
                }
                if (presentation == PresentationStyle.FEMININE) {
                    // Expressive outer lash wing pixel
                    drawPx(10, 9, 1, 1, Color(0xFF1E293B))
                    drawPx(21, 9, 1, 1, Color(0xFF1E293B))
                }
            }

            // 9. Face Markings & Statement Eyewear
            when (faceFeature) {
                "tinted_aviator" -> {
                    // Rose-amber tinted vintage aviators + gold bridge
                    val lensTint = Color(0xFFE11D48).copy(alpha = 0.62f)
                    val goldWire = Color(0xFFFBBF24)
                    drawPx(10, 9, 5, 3, lensTint)
                    drawPx(17, 9, 5, 3, lensTint)
                    drawPx(10, 8, 12, 1, goldWire)
                    drawPx(15, 9, 2, 1, goldWire)
                }
                "round_tortoise" -> {
                    val frame = Color(0xFF92400E)
                    drawPx(10, 8, 5, 1, frame)
                    drawPx(17, 8, 5, 1, frame)
                    drawPx(10, 11, 5, 1, frame)
                    drawPx(17, 11, 5, 1, frame)
                    drawPx(15, 9, 2, 1, frame)
                }
                "visor_clip" -> {
                    drawPx(10, 9, 12, 2, Color(0xFFFFB347).copy(alpha = 0.68f))
                    drawPx(15, 9, 2, 1, Color.White)
                }
                "brow_plaster", "hero_plaster" -> {
                    // Nose-bridge plaster strip + razor brow slit
                    drawPx(14, 10, 4, 1, Color(0xFFFDE68A))
                    drawPx(19, 8, 2, 1, Color(0xFF1E293B))
                }
                "sharp_stubble" -> {
                    val stubble = Color(0xFF334155).copy(alpha = 0.55f)
                    drawPx(11, 13, 10, 1, stubble)
                    drawPx(10, 12, 1, 1, stubble)
                    drawPx(21, 12, 1, 1, stubble)
                }
                "soft_blush" -> {
                    drawPx(10, 11, 2, 1, Color(0xFFFF8FA3).copy(alpha = 0.6f))
                    drawPx(20, 11, 2, 1, Color(0xFFFF8FA3).copy(alpha = 0.6f))
                }
                "pixel_freckles" -> {
                    val freckle = Color(0xFFB45309).copy(alpha = 0.7f)
                    drawPx(10, 11, 1, 1, freckle)
                    drawPx(12, 11, 1, 1, freckle)
                    drawPx(19, 11, 1, 1, freckle)
                    drawPx(21, 11, 1, 1, freckle)
                }
                "star_mark" -> {
                    drawPx(20, 11, 2, 1, Color(0xFFFFB347))
                    drawPx(10, 11, 2, 1, Color(0xFFFF8FA3).copy(alpha = 0.45f))
                }
            }

            // 10. Mouth & Lip-Sync Layer (row 12..13)
            if (resolvedVisualState == CharacterVisualState.SPEAKING) {
                val frameIdx = talkFrame.toInt() % 3
                when (frameIdx) {
                    0 -> drawPx(14, 12, 4, 1, Color(0xFFE11D48))
                    1 -> {
                        drawPx(14, 12, 4, 2, Color(0xFF9F1239))
                        drawPx(15, 12, 2, 1, Color(0xFFFDA4AF))
                    }
                    else -> drawPx(15, 12, 2, 1, Color(0xFFE11D48))
                }
            } else {
                when (expression) {
                    "determined" -> drawPx(14, 12, 4, 1, Color(0xFF7C2D12))
                    "curious" -> drawPx(15, 12, 2, 1, Color(0xFFE11D48))
                    "joyful" -> {
                        drawPx(13, 12, 6, 1, Color(0xFF9F1239))
                        drawPx(14, 13, 4, 1, Color(0xFFFDA4AF))
                    }
                    else -> {
                        // Asymmetric confident smirk
                        drawPx(14, 12, 4, 1, Color(0xFFD97706))
                        drawPx(18, 11, 1, 1, Color(0xFFD97706))
                    }
                }
            }

            // 11. Front Hair & Expressive Fringe (rows 3..8)
            when (appearance.hairStyleId) {
                "tousled_rebel", "spiky_hero" -> {
                    drawPx(9, 4, 14, 3, hairColor)
                    drawPx(8, 5, 2, 2, hairColor)
                    drawPx(22, 5, 2, 2, hairColor)
                    drawPx(11, 3, 3, 1, hairHighlight)
                    drawPx(17, 3, 4, 1, hairHighlight)
                    // Asymmetric rebel bang falling between eyes
                    drawPx(15, 7, 2, 2, hairColor)
                    drawPx(10, 7, 2, 1, hairColor)
                    drawPx(20, 7, 2, 1, hairColor)
                }
                "textured_undercut" -> {
                    drawPx(10, 3, 12, 4, hairColor)
                    drawPx(12, 4, 6, 1, hairHighlight)
                    // Shaved temple line on left, swept fringe on right
                    drawPx(16, 7, 5, 1, hairColor)
                }
                "bleached_buzz" -> {
                    drawPx(10, 4, 12, 3, hairColor)
                    drawPx(11, 4, 10, 1, hairHighlight)
                    // Razor slit on temple
                    drawPx(19, 5, 2, 1, skinColor)
                }
                "vintage_shag", "cyber_mullet" -> {
                    drawPx(9, 4, 14, 3, hairColor)
                    drawPx(11, 4, 8, 1, hairHighlight)
                    drawPx(9, 7, 3, 3, hairColor)
                    drawPx(20, 7, 3, 3, hairColor)
                }
                "cyber_swept" -> {
                    drawPx(9, 4, 14, 3, hairColor)
                    drawPx(10, 7, 9, 1, hairColor)
                    drawPx(11, 5, 6, 1, hairHighlight)
                }
                "curly_cloud" -> {
                    drawPx(8, 3, 16, 4, hairColor)
                    drawPx(11, 3, 10, 1, hairHighlight)
                    drawPx(8, 7, 4, 2, hairColor)
                    drawPx(20, 7, 4, 2, hairColor)
                }
                else -> {
                    drawPx(9, 4, 14, 3, hairColor)
                    drawPx(11, 5, 10, 1, hairHighlight)
                    drawPx(9, 7, 2, 6, hairColor)
                    drawPx(21, 7, 2, 6, hairColor)
                }
            }

            // 12. Head & Ear Accessories
            when (clothing.accessoryId) {
                "pixel_glasses" -> {
                    val frameColor = Color(0xFFFFB347)
                    drawPx(10, 8, 5, 1, frameColor)
                    drawPx(17, 8, 5, 1, frameColor)
                    drawPx(15, 9, 2, 1, frameColor)
                    drawPx(10, 11, 5, 1, frameColor)
                    drawPx(17, 11, 5, 1, frameColor)
                }
                "starlight_headphones" -> {
                    val hpColor = Color(0xFF94A3B8)
                    drawPx(9, 3, 14, 1, hpColor)
                    drawPx(7, 8, 2, 4, hpColor)
                    drawPx(23, 8, 2, 4, hpColor)
                    drawPx(8, 9, 1, 2, outfitAccent)
                    drawPx(23, 9, 1, 2, outfitAccent)
                }
                "ear_cuff_pin" -> {
                    // Statement brass ear cuff + collar pin
                    drawPx(8, 9, 1, 2, Color(0xFFFBBF24))
                    drawPx(23, 9, 1, 1, Color(0xFFFBBF24))
                    drawPx(12, 17, 1, 1, Color(0xFFFBBF24))
                }
                "crown_sprout" -> {
                    drawPx(15, 2, 1, 2, Color(0xFF34D399))
                    drawPx(13, 1, 2, 1, Color(0xFF6EE7B7))
                    drawPx(16, 1, 2, 1, Color(0xFF6EE7B7))
                }
            }
        }
    }
}
