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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import com.madar.launcher.data.Mode

@Composable
fun MadarRoot(c: LauncherController) {
    c.uiScope = rememberCoroutineScope()
    val settings by c.app.settings.state.collectAsState()
    val stacks by c.app.stacks.stacks.collectAsState()
    val digestItems by c.app.digest.items.collectAsState()
    val now by rememberNow()
    val ssid by c.app.places.ssid.collectAsState()
    val mode = remember(settings, now, ssid) { c.modeFor(settings, now) }

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
                digestCount = digestItems.count { !it.vault },
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
                    is Overlay.Palette -> Box(Modifier)
                    is Overlay.Gate -> IntentGate(c, o.app, o.outOfMode, mode)
                    is Overlay.StackView -> StackView(c, o.id)
                    is Overlay.AppMenu -> AppMenu(c, o.app)
                    Overlay.Digest -> DigestSheet(c)
                    Overlay.Settings -> SettingsSheet(c)
                    Overlay.Archive -> ArchiveSheet(c)
                    Overlay.Report -> ReportSheet(c)
                    Overlay.Vault -> VaultSheet(c)
                    is Overlay.Template -> BlueprintSheet(c, o.blueprint, o.importing)
                }
            }
            PaletteLayer(c, mode)
            Toast(c.message) { c.message = null }
        }
    }
}

/** The command bar rides on [LauncherController.pull] so it can follow the finger and spring with its velocity. */
@Composable
private fun PaletteLayer(c: LauncherController, mode: Mode) {
    val pull = c.pull.value
    val overlay = c.overlay
    var lastInitial by remember { mutableStateOf("") }
    if (overlay is Overlay.Palette) lastInitial = overlay.initial
    if (pull <= 0.001f && overlay !is Overlay.Palette) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = (1f - pull) * size.height * 0.6f
                alpha = pull.coerceIn(0f, 1f)
            },
    ) {
        CommandPalette(c, lastInitial, mode, active = overlay is Overlay.Palette && pull > 0.95f)
    }
}
