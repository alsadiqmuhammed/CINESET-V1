package com.madar.launcher.ui

import com.madar.launcher.fmt
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madar.launcher.data.AppEntry
import com.madar.launcher.data.AppKind
import com.madar.launcher.data.Mode
import com.madar.launcher.data.StackItem
import com.madar.launcher.data.newItemId
import com.madar.launcher.data.toLocalTimeLabel
import kotlinx.coroutines.delay

data class Intention(val label: String, val minutes: Int)

val INTENTIONS = listOf(
    Intention("رد على رسالة", 3),
    Intention("نشر محتوى", 5),
    Intention("استراحة", 10),
)

/** The in-launcher gate: shown before Madar itself opens an attention-heavy app. */
@Composable
fun IntentGate(c: LauncherController, app: AppEntry, outOfMode: Boolean, mode: Mode) {
    GateContent(
        app = app,
        outOfMode = outOfMode,
        mode = mode,
        haptics = c.haptics,
        onChoose = { c.launchWithIntent(app, it.label, it.minutes) },
        onRetreat = {
            c.app.friction.recordRetreat(app.packageName)
            c.close()
            c.toast("اختيار موفق")
        },
    )
}

/**
 * Micro-friction: before an attention-heavy app is used, a pause asks for the intention.
 * Backing out is instant; going in only unlocks after a beat. Shared by the launcher and the
 * system-wide overlay that catches apps opened from notifications or recents.
 */
@Composable
fun GateContent(
    app: AppEntry,
    outOfMode: Boolean,
    mode: Mode,
    haptics: Haptics,
    onChoose: (Intention) -> Unit,
    onRetreat: () -> Unit,
) {
    val p = LocalPalette.current
    var unlocked by remember { mutableStateOf(false) }
    LaunchedEffect(app.key) {
        delay(1500)
        unlocked = true
        haptics.tick()
    }
    val glyphPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    Box(
        Modifier.fillMaxSize().background(p.background.copy(alpha = 0.96f)).safeDrawingPadding().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppGlyph(app, glyphPx, Modifier.size(52.dp))
            Spacer(Modifier.height(10.dp))
            Text(app.label, style = Type.label, color = p.muted)
            Spacer(Modifier.height(28.dp))
            Text("ما غايتك الآن؟", style = Type.display, color = p.ink)
            if (outOfMode) {
                Text("أنت بطور ${mode.title}", style = Type.body, color = p.signal, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(28.dp))
            AnimatedVisibility(unlocked, enter = fadeIn(tween(700))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    INTENTIONS.forEach { i ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .matte(p, RoundedCornerShape(16.dp))
                                .quietClick { haptics.light(); onChoose(i) }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(i.label, style = Type.title, color = p.ink, modifier = Modifier.weight(1f))
                            Text("%02d MIN".fmt(i.minutes), style = Type.tech, color = p.signal)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            TextAction("تراجع", color = p.calm, style = Type.title) {
                haptics.completion()
                onRetreat()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppMenu(c: LauncherController, app: AppEntry) {
    val p = LocalPalette.current
    val settings by c.app.settings.state.collectAsState()
    val stacks by c.app.stacks.stacks.collectAsState()
    val glyphPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    Sheet(app.label, onClose = c::close) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyph(app, glyphPx, Modifier.size(36.dp))
                Spacer(Modifier.width(14.dp))
                Text(app.kind.title, style = Type.body, color = p.muted)
            }
            SectionTitle("القوس")
            val pinned = app.key in settings.pinned
            Pill(if (pinned) "إزالة من القوس" else "تثبيت على القوس", selected = pinned) { c.togglePin(app) }
            if (app.kind.gated) {
                Text("تطبيقات الترفيه والسوشيال ما تظهر على القوس؛ تفتح من شريط الأوامر عبر بوابة النية.", style = Type.caption, color = p.muted, modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle("التصنيف")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppKind.entries.forEach { k ->
                    Pill(k.title, selected = k == app.kind) {
                        c.haptics.tick()
                        c.app.apps.setKind(app, k)
                        c.open(Overlay.AppMenu(app.copy(kind = k)))
                    }
                }
            }

            SectionTitle("الإشعارات")
            val pkg = app.packageName
            val current = when (pkg) {
                in settings.instantPackages -> 1
                in settings.digestPackages -> 2
                else -> 0
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("تلقائي", "فورية", "ضمن الملخص").forEachIndexed { i, label ->
                    Pill(label, selected = current == i) {
                        c.app.settings.edit {
                            it.copy(
                                instantPackages = if (i == 1) it.instantPackages + pkg else it.instantPackages - pkg,
                                digestPackages = if (i == 2) it.digestPackages + pkg else it.digestPackages - pkg,
                            )
                        }
                    }
                }
            }

            val open = stacks.filter { !it.archived }
            if (open.isNotEmpty()) {
                SectionTitle("أضف إلى مشروع")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    open.forEach { s ->
                        Pill(s.title) {
                            c.app.stacks.addItem(s.id, StackItem.App(newItemId(), app.key, app.label))
                            c.haptics.completion()
                            c.toast("انضاف إلى ${s.title}")
                        }
                    }
                }
            }

            SectionTitle("النظام")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("معلومات التطبيق") { c.openAppInfo(app) }
                Pill("إلغاء التثبيت") { c.uninstall(app) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun DigestSheet(c: LauncherController) {
    val p = LocalPalette.current
    val all by c.app.digest.items.collectAsState()
    val items = all.filterNot { it.vault }
    LaunchedEffect(Unit) { c.app.digest.markViewed() }
    Sheet(
        "الملخص الهادئ",
        onClose = c::close,
        trailing = { if (items.isNotEmpty()) TextAction("مسح الكل", color = p.muted) { c.app.digest.clear(); c.haptics.completion() } },
    ) {
        if (items.isEmpty()) {
            Text("ماكو شي ينتظرك. استمتع بالهدوء.", style = Type.body, color = p.muted, modifier = Modifier.padding(top = 24.dp))
            return@Sheet
        }
        val grouped = items.sortedByDescending { it.postedAt }.groupBy { it.appLabel }
        LazyColumn(Modifier.fillMaxSize()) {
            grouped.forEach { (label, group) ->
                item(key = "h-$label") { SectionTitle("$label · ${group.size}") }
                items(group, key = { it.key }) { n ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .quietClick {
                                c.app.digest.remove(n.key)
                                val opened = n.contentIntent?.let { pi -> runCatching { sendFromForeground(pi) }.isSuccess } ?: false
                                if (!opened) {
                                    c.app.apps.findPackage(n.packageName)?.let { c.launch(it, null) }
                                } else {
                                    c.close()
                                }
                            }
                            .padding(vertical = 10.dp),
                    ) {
                        Row {
                            Text(n.title, style = Type.label, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(n.postedAt.toLocalTimeLabel(), style = Type.caption, color = p.muted)
                        }
                        if (n.text.isNotBlank()) Text(n.text, style = Type.body, color = p.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveSheet(c: LauncherController) {
    val p = LocalPalette.current
    val stacks by c.app.stacks.stacks.collectAsState()
    val archived = stacks.filter { it.archived }.sortedByDescending { it.openedAt }
    Sheet("الأرشيف", onClose = c::close) {
        if (archived.isEmpty()) {
            Text("ما كو مشاريع مؤرشفة.", style = Type.body, color = p.muted, modifier = Modifier.padding(top = 24.dp))
            return@Sheet
        }
        LazyColumn {
            items(archived, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).quietClick { c.open(Overlay.StackView(s.id)) }) {
                        Text(s.title, style = Type.title, color = p.ink)
                        Text("${s.items.size} عناصر", style = Type.caption, color = p.muted)
                    }
                    TextAction("استرجاع") { c.app.stacks.update(s.id) { it.copy(archived = false, dueAt = null) } }
                    Spacer(Modifier.width(8.dp))
                    TextAction("حذف", color = p.danger) { c.app.stacks.delete(s.id) }
                }
            }
        }
    }
}

/** Opens a stack item with the right system intent; returns false if nothing could open it. */
fun LauncherController.openItem(item: StackItem): Boolean = when (item) {
    is StackItem.App -> {
        android.content.ComponentName.unflattenFromString(item.component)?.let { app.apps.find(it) }
            ?.let { requestLaunch(it); true } ?: false
    }
    is StackItem.Link -> start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.url)))
    is StackItem.Contact -> {
        call(com.madar.launcher.data.ContactEntry(item.name, item.phone, item.name)); true
    }
    is StackItem.Doc -> start(
        Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(item.uri), item.mime ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
    is StackItem.Note -> true
}

/** Targeting 34+, a foreground sender must opt in for the PendingIntent to start its activity. */
private fun sendFromForeground(pi: android.app.PendingIntent) {
    val options = android.app.ActivityOptions.makeBasic()
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
    }
    pi.send(null, 0, null, null, null, null, options.toBundle())
}
