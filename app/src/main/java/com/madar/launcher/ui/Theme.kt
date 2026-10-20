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

/** Per-mode palette. Background is always true OLED black; only ink and accent shift. */
@Immutable
data class Palette(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val surface: Color,
    val danger: Color,
)

fun paletteFor(mode: Mode): Palette = when (mode) {
    Mode.FOCUS -> Palette(
        background = Color.Black,
        ink = Color(0xFFF2F2F2),
        muted = Color(0xFF8A8A8A),
        faint = Color(0xFF3A3A3A),
        accent = Color(0xFFFFFFFF),
        surface = Color(0xFF0E0E0E),
        danger = Color(0xFFFF6B6B),
    )
    Mode.FLOW -> Palette(
        background = Color.Black,
        ink = Color(0xFFEDEFEE),
        muted = Color(0xFF8C9A94),
        faint = Color(0xFF2B3532),
        accent = Color(0xFF3DDC97),
        surface = Color(0xFF0B1210),
        danger = Color(0xFFFF6B6B),
    )
    // No blue light: warm amber ink on black.
    Mode.SANCTUARY -> Palette(
        background = Color.Black,
        ink = Color(0xFFF3DFC1),
        muted = Color(0xFF9C8466),
        faint = Color(0xFF3A2E20),
        accent = Color(0xFFFFB547),
        surface = Color(0xFF120D07),
        danger = Color(0xFFFF8A5C),
    )
}

val LocalPalette = staticCompositionLocalOf { paletteFor(Mode.FLOW) }

object Type {
    val clock = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Thin, fontSize = 96.sp, letterSpacing = (-2).sp)
    val display = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 44.sp)
    val title = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 30.sp)
    val body = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 26.sp)
    val label = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Plex, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
}

@Composable
fun MadarTheme(mode: Mode, content: @Composable () -> Unit) {
    val target = paletteFor(mode)
    val spec = tween<Color>(durationMillis = 900)
    val ink by animateColorAsState(target.ink, spec, label = "ink")
    val muted by animateColorAsState(target.muted, spec, label = "muted")
    val faint by animateColorAsState(target.faint, spec, label = "faint")
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val surface by animateColorAsState(target.surface, spec, label = "surface")
    val palette = target.copy(ink = ink, muted = muted, faint = faint, accent = accent, surface = surface)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            background = Color.Black,
            surface = surface,
            onSurface = ink,
            onBackground = ink,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            outline = faint,
        ),
    ) {
        CompositionLocalProvider(LocalPalette provides palette, content = content)
    }
}
