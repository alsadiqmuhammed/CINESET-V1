package com.madar.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.madar.launcher.data.Mode

@Composable
fun MadarRoot(c: LauncherController) {
    val settings by c.app.settings.state.collectAsState()
    val stacks by c.app.stacks.stacks.collectAsState()
    val digestItems by c.app.digest.items.collectAsState()
    val now by rememberNow()
    val mode = remember(settings, now) { c.modeFor(settings, now) }

    // Mode changes (scheduled or manual) land with a rising haptic so the morph is felt, not just seen.
    val lastMode = remember { arrayOf<Mode?>(null) }
    LaunchedEffect(mode) {
        if (lastMode[0] != null && lastMode[0] != mode) c.haptics.morph()
        lastMode[0] = mode
    }

    MadarTheme(mode) {
        Box(Modifier.fillMaxSize()) {
            HomeScreen(
                c = c,
                settings = settings,
                mode = mode,
                stacks = stacks,
                digestOpen = remember(settings, digestItems, now) { c.app.digest.isCapsuleOpen(settings) },
                digestCount = digestItems.size,
            )
            AnimatedContent(
                targetState = c.overlay,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 12 }) togetherWith fadeOut(tween(160))
                },
                contentKey = { it::class },
                label = "overlay",
            ) { o ->
                when (o) {
                    Overlay.None -> Box(Modifier)
                    is Overlay.Palette -> CommandPalette(c, o.initial, mode)
                    is Overlay.Gate -> IntentGate(c, o.app, o.outOfMode, mode)
                    is Overlay.StackView -> StackView(c, o.id)
                    is Overlay.AppMenu -> AppMenu(c, o.app)
                    Overlay.Digest -> DigestSheet(c)
                    Overlay.Settings -> SettingsSheet(c)
                    Overlay.Archive -> ArchiveSheet(c)
                }
            }
            Toast(c.message) { c.message = null }
        }
    }
}
