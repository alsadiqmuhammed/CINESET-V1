package com.madar.launcher.ui

import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madar.launcher.MainActivity
import com.madar.launcher.data.AppEntry
import com.madar.launcher.data.toLocalTimeLabel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The orbit gesture: one finger tracing a near-closed circle anywhere on the home screen.
 * Observes without consuming, so ordinary swipes and taps are unaffected.
 */
fun Modifier.orbitGesture(onOrbit: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: continue
            val points = mutableListOf(down.position)
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.size > 1) { points.clear(); break }
                val change = event.changes.first()
                if (!change.pressed) break
                points += change.position
            }
            if (isOrbit(points, minRadius = 48.dp.toPx())) onOrbit()
        }
    }
}

/** True when the path winds ≥ 320° around its own centre with a reasonably steady radius. */
internal fun isOrbit(points: List<Offset>, minRadius: Float): Boolean {
    if (points.size < 16) return false
    val cx = points.sumOf { it.x.toDouble() } / points.size
    val cy = points.sumOf { it.y.toDouble() } / points.size
    val radii = points.map { hypot(it.x - cx, it.y - cy) }
    val mean = radii.average()
    if (mean < minRadius) return false
    if (radii.count { abs(it - mean) > mean * 0.45 } > points.size / 5) return false
    var total = 0.0
    var prev = atan2(points[0].y - cy, points[0].x - cx)
    for (i in 1 until points.size) {
        val a = atan2(points[i].y - cy, points[i].x - cx)
        var d = a - prev
        if (d > Math.PI) d -= 2 * Math.PI
        if (d < -Math.PI) d += 2 * Math.PI
        total += d
        prev = a
    }
    return abs(Math.toDegrees(total)) >= 320
}

/** Biometric (or device PIN) before the vault opens. */
fun MainActivity.unlockVault(onUnlocked: () -> Unit) {
    val prompt = BiometricPrompt.Builder(this)
        .setTitle("القبو")
        .setSubtitle("تأكيد الهوية")
        .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
    })
}

/** Ghost space: amber, out of every list, gone the moment you leave. */
@Composable
fun VaultSheet(c: LauncherController) {
    val base = LocalPalette.current
    // Amber is reserved for private states: the vault takes it as its accent.
    val p = base.copy(accent = base.privacy, signal = base.privacy)
    CompositionLocalProvider(LocalPalette provides p) {
        val hidden by c.app.apps.hidden.collectAsState()
        val visible by c.app.apps.apps.collectAsState()
        val notes by c.app.digest.items.collectAsState()
        val vaultNotes = notes.filter { it.vault }.sortedByDescending { it.postedAt }
        var picking by remember { mutableStateOf(false) }
        var query by remember { mutableStateOf("") }
        val glyphPx = with(LocalDensity.current) { 26.dp.roundToPx() }

        Sheet("القبو", onClose = c::close, trailing = { OrbitMark(p.privacy, 22.dp, modifier = Modifier.padding(end = 12.dp)) }) {
            Text("GHOST · SPACE", style = Type.tech, color = p.privacy)
            LazyColumn(Modifier.fillMaxSize()) {
                if (hidden.isEmpty() && !picking) {
                    item {
                        Text(
                            "القبو فارغ. التطبيقات اللي تخفيها هنا تختفي من القوس والأوامر والقوائم، وإشعاراتها تنتظرك هنا.",
                            style = Type.body, color = p.muted, modifier = Modifier.padding(vertical = 20.dp),
                        )
                    }
                }
                items(hidden, key = { it.key }) { app ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .quietClick(onLongClick = {
                                c.haptics.heavy()
                                c.app.apps.setHidden(app, false)
                                c.toast("${app.label} طلع من القبو")
                            }) { c.close(); c.launch(app, null) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppGlyph(app, glyphPx, Modifier.size(24.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(app.label, style = Type.title, color = p.ink, modifier = Modifier.weight(1f))
                        val unread = vaultNotes.count { it.packageName == app.packageName }
                        if (unread > 0) Text("%02d".let { String.format(java.util.Locale.ROOT, it, unread) }, style = Type.tech, color = p.privacy)
                    }
                }
                item {
                    Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(if (picking) "تم" else "+ إخفاء تطبيق", selected = picking, color = p.privacy) { picking = !picking; query = "" }
                    }
                }
                if (picking) {
                    item {
                        Box(Modifier.fillMaxWidth().matte(p, RoundedCornerShape(14.dp)).padding(14.dp)) {
                            if (query.isEmpty()) Text("ابحث عن تطبيق", style = Type.body, color = p.faint)
                            BasicTextField(query, { query = it }, singleLine = true, textStyle = Type.body.copy(color = p.ink), cursorBrush = SolidColor(p.privacy), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    val matches = if (query.isBlank()) visible else c.parser.matchApps(query)
                    items(matches.take(30), key = { "pick-" + it.key }) { app ->
                        PickRow(app, glyphPx) {
                            c.haptics.heavy()
                            c.app.apps.setHidden(app, true)
                        }
                    }
                }
                if (vaultNotes.isNotEmpty()) {
                    item { SectionTitle("رسائل القبو", code = "%02d".let { String.format(java.util.Locale.ROOT, it, vaultNotes.size) }) }
                    items(vaultNotes, key = { "n-" + it.key }) { n ->
                        Column(
                            Modifier.fillMaxWidth().quietClick {
                                c.app.digest.remove(n.key)
                                c.app.apps.findPackage(n.packageName)?.let { c.launch(it, null) }
                            }.padding(vertical = 8.dp),
                        ) {
                            Row {
                                Text("${n.appLabel} · ${n.title}", style = Type.label, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(n.postedAt.toLocalTimeLabel(), style = Type.tech, color = p.muted)
                            }
                            if (n.text.isNotBlank()) Text(n.text, style = Type.body, color = p.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item {
                    Text(
                        "ضغطة مطولة على تطبيق تطلّعه من القبو. القبو يتسكّر وحده أول ما تطلع منه.",
                        style = Type.caption, color = p.faint, modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickRow(app: AppEntry, glyphPx: Int, onPick: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().quietClick(onClick = onPick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        AppGlyph(app, glyphPx, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(app.label, style = Type.body, color = p.muted, modifier = Modifier.weight(1f))
        Text("إخفاء", style = Type.label, color = p.privacy)
    }
}
