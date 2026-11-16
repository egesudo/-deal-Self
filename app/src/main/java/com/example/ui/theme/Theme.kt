package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * 65% / 25% / 10% Signature Editorial Color Scheme:
 * - 65% Warm Cream Beige: background, surface (Main backgrounds, screens, and large surfaces)
 * - 25% Muted Sage Grey: surfaceVariant, secondary, secondaryContainer, tertiary, outline (Secondary surfaces, selection areas, small components)
 * - 10% Deep Burgundy: primary, onPrimary, primaryContainer (Talk button, active states, and important accents)
 */
private val SignatureCreamSageBurgundyColorScheme = lightColorScheme(
    // 10% — Deep Burgundy (Talk button, active states & key accents)
    primary = DeepBurgundy,
    onPrimary = OnDeepBurgundy,
    primaryContainer = DeepBurgundyContainer,
    onPrimaryContainer = OnDeepBurgundyContainer,

    // 25% — Muted Sage Grey (Secondary surfaces, selection areas & small components)
    secondary = MutedSageGrey,
    onSecondary = WarmCreamSurface,
    secondaryContainer = MutedSageContainer,
    onSecondaryContainer = EspressoTextPrimary,

    tertiary = MutedSageDeep,
    onTertiary = WarmCreamSurface,
    tertiaryContainer = MutedSageSurfaceVariant,
    onTertiaryContainer = MutedSageDeep,

    // 65% — Warm Cream Beige (Main backgrounds, screens & large surfaces)
    background = WarmCreamBeige,
    onBackground = EspressoTextPrimary,
    surface = WarmCreamSurface,
    onSurface = EspressoTextPrimary,
    surfaceVariant = MutedSageSurfaceVariant,
    onSurfaceVariant = SageSlateTextSecondary,

    outline = MutedSageBorder,
    outlineVariant = MutedSageContainer,
    error = ErrorCrimson,
    onError = OnErrorCream
)

val PixelStudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun MyApplicationTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SignatureCreamSageBurgundyColorScheme,
        typography = Typography,
        shapes = PixelStudioShapes,
        content = content
    )
}
