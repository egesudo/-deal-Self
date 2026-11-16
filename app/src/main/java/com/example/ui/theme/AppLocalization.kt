package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppLanguage(
    val code: String,
    val voiceTag: String,
    val label: String,
    val shortBadge: String
) {
    TR(
        code = "tr",
        voiceTag = "tr-TR",
        label = "TR Türkçe",
        shortBadge = "TR"
    ),
    EN(
        code = "en",
        voiceTag = "en-US",
        label = "EN English",
        shortBadge = "EN"
    );

    fun toggled(): AppLanguage = if (this == TR) EN else TR
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.TR }
val LocalOnToggleLanguage = compositionLocalOf<() -> Unit> { {} }

@Composable
fun tr(trText: String, enText: String): String {
    return if (LocalAppLanguage.current == AppLanguage.TR) trText else enText
}

@Composable
fun LanguageToggleChip(
    modifier: Modifier = Modifier,
    testTagName: String = "app_language_toggle_button"
) {
    val currentLang = LocalAppLanguage.current
    val onToggle = LocalOnToggleLanguage.current

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = modifier.testTag(testTagName)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = if (currentLang == AppLanguage.TR) "Dili Değiştir (TR/EN)" else "Switch Language (TR/EN)",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = currentLang.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
