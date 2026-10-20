package com.madar.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import com.madar.launcher.R
import com.madar.launcher.data.Mode

val Plex = FontFamily(
    Font(R.font.plex_arabic_thin, FontWeight.Thin),
    Font(R.font.plex_arabic_light, FontWeight.Light),
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
)

/** Technical monospace for every number: clock, timers, percentages, countdowns. */
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_thin, FontWeight.Thin),
    Font(R.font.jetbrains_mono_light, FontWeight.Light),
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
)

/**
 * Functional colour coding. The interface is ~90% monochrome; colour only appears as a state signal.
 *  - [signal]: International Orange — something is running (timers, the shelf, active controls).
 *  - [calm]: Sage — low energy, the quiet digest.
 *  - [privacy]: Amber — guarded / private states.
 * [accent] is the mode's own signal: orange in focus, sage in sanctuary, chalk (no colour) in flow.
 */
@Immutable
data class Palette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val stroke: Color,
    val highlight: Color,
    val accent: Color,
    val signal: Color,
    val calm: Color,
    val privacy: Color,
    val danger: Color,
)

object Tokens {
    val Black = Color(0xFF000000)
    val Charcoal = Color(0xFF121214)
    val CharcoalHigh = Color(0xFF161618)
    val Chalk = Color(0xFFE5E5E7)
    val Graphite = Color(0xFF8E8E93)
    val Ash = Color(0xFF3A3A3D)
    val Stroke = Color(0xFF242427)
    val Highlight = Color(0x38FFFFFF)
    val InternationalOrange = Color(0xFFFF5722)
    val Sage = Color(0xFF81B29A)
    val Amber = Color(0xFFFFB703)
}

fun paletteFor(mode: Mode): Palette {
    val base = Palette(
        background = Tokens.Black,
        surface = Tokens.Charcoal,
        surfaceHigh = Tokens.CharcoalHigh,
        ink = Tokens.Chalk,
        muted = Tokens.Graphite,
        faint = Tokens.Ash,
        stroke = Tokens.Stroke,
        highlight = Tokens.Highlight,
        accent = Tokens.Chalk,
        signal = Tokens.InternationalOrange,
        calm = Tokens.Sage,
        privacy = Tokens.Amber,
        danger = Tokens.InternationalOrange,
    )
    return when (mode) {
        Mode.FOCUS -> base.copy(accent = Tokens.InternationalOrange)
        Mode.FLOW -> base
        // Evening: chalk dims a touch so nothing glares in the dark.
        Mode.SANCTUARY -> base.copy(accent = Tokens.Sage, ink = Color(0xFFD6D6D2), muted = Color(0xFF7C7F7A))
    }
}

val LocalPalette = staticCompositionLocalOf { paletteFor(Mode.FLOW) }

object Type {
    val clock = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Thin, fontSize = 76.sp, lineHeight = 84.sp, letterSpacing = (-3).sp, textDirection = TextDirection.Ltr)
    val display = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 44.sp)
    val title = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 30.sp)
    val body = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp)
    val label = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

    /** Engineering labels: tiny uppercase mono, like silkscreen on hardware. */
    val tech = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.5.sp, textDirection = TextDirection.Ltr)
    val data = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Light, fontSize = 15.sp, textDirection = TextDirection.Ltr)
    val dataLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Thin, fontSize = 44.sp, letterSpacing = (-1).sp, textDirection = TextDirection.Ltr)
}

@Composable
fun MadarTheme(mode: Mode, content: @Composable () -> Unit) {
    val target = paletteFor(mode)
    val spec = tween<Color>(durationMillis = 900)
    val ink by animateColorAsState(target.ink, spec, label = "ink")
    val muted by animateColorAsState(target.muted, spec, label = "muted")
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val palette = target.copy(ink = ink, muted = muted, accent = accent)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Tokens.Black,
            background = Tokens.Black,
            surface = target.surface,
            onSurface = ink,
            onBackground = ink,
            surfaceContainer = target.surface,
            surfaceContainerHigh = target.surfaceHigh,
            outline = target.stroke,
        ),
    ) {
        CompositionLocalProvider(LocalPalette provides palette, content = content)
    }
}
