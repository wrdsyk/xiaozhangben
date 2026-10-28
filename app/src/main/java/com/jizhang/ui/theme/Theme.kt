package com.jizhang.ui.theme

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jizhang.data.ThemeId

val LocalLedgerColors = staticCompositionLocalOf { Pearl }

object LedgerTheme {
    val colors: LedgerColors
        @Composable @ReadOnlyComposable get() = LocalLedgerColors.current
}

/** Tabular figures keep amounts from jittering sideways while digits roll. */
fun numberStyle(size: TextUnit, weight: FontWeight = FontWeight.Light, tracking: Float = -0.03f) = TextStyle(
    fontSize = size,
    fontWeight = weight,
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum",
)

/**
 * Provides [LedgerColors] for [theme]. A switch swaps the whole set in one step: the frames in
 * between are hidden by [ThemeReveal], so nothing recomposes while the new theme is revealed.
 */
@Composable
fun LedgerTheme(theme: ThemeId, content: @Composable () -> Unit) {
    val colors = colorsFor(theme)
    val scheme = remember(colors) {
        if (colors.isDark) {
            darkColorScheme(
                primary = colors.accent, onPrimary = colors.onAccent,
                surface = lerp(colors.bg0, colors.bg1, .6f), onSurface = colors.ink,
                surfaceContainerHigh = colors.bg1, onSurfaceVariant = colors.sub,
                secondaryContainer = colors.accent.copy(alpha = .25f), outline = colors.sub,
            )
        } else {
            lightColorScheme(
                primary = colors.accent, onPrimary = colors.onAccent,
                surface = colors.bg0, onSurface = colors.ink,
                surfaceContainerHigh = colors.bg0, onSurfaceVariant = colors.sub,
                secondaryContainer = colors.ring1.copy(alpha = .35f), outline = colors.sub,
            )
        }
    }
    val baseTypography = MaterialTheme.typography
    val typography = remember(colors, baseTypography) {
        baseTypography.copy(bodyLarge = TextStyle(fontSize = 15.sp, color = colors.ink))
    }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Haze's glass picks its light/dark material from the configuration's night bit, so tell it
        // the app theme rather than the system one.
        val base = LocalConfiguration.current
        val night = if (colors.isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val config = remember(base, night) {
            Configuration(base).apply { uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night }
        }
        CompositionLocalProvider(LocalLedgerColors provides colors, LocalConfiguration provides config, content = content)
    }
}
