package com.madar.launcher.ui

import com.madar.launcher.fmt
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madar.launcher.data.AppKind
import com.madar.launcher.data.Mode
import com.madar.launcher.data.Settings
import com.madar.launcher.data.Stack
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val MONTHS = listOf(
    "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
    "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول",
)
private val DAYS = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
private val MODE_CODES = mapOf(Mode.FOCUS to "01 FOCUS", Mode.FLOW to "02 FLOW", Mode.SANCTUARY to "03 REST")

@Composable
fun HomeScreen(c: LauncherController, settings: Settings, mode: Mode, stacks: List<Stack>, digestOpen: Boolean, digestCount: Int) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val downThreshold = with(density) { 90.dp.toPx() }
    val now by rememberNow()
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())

    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .pointerInput(Unit) {
                // Swipe up drags the command bar out with the finger; the release velocity decides the rest.
                var total = 0f
                val tracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = { total = 0f; tracker.resetTracking() },
                    onVerticalDrag = { change, dy ->
                        total += dy
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (total < 0) c.pullPalette(-dy / size.height)
                    },
                    onDragEnd = {
                        val velocity = -tracker.calculateVelocity().y / size.height
                        if (total > downThreshold) c.expandNotifications() else c.releasePalette(velocity)
                    },
                    onDragCancel = { c.releasePalette(0f) },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { c.haptics.heavy(); c.open(Overlay.Settings) })
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            StatusStrip(c, mode, context)
            Spacer(Modifier.height(26.dp))

            val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm"
            Text(time.format(DateTimeFormatter.ofPattern(pattern)), style = Type.clock, color = p.ink)
            Text(
                "${DAYS[time.dayOfWeek.ordinal]}، ${time.dayOfMonth} ${MONTHS[time.monthValue - 1]}",
                style = Type.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Light),
                color = p.muted,
            )

            Spacer(Modifier.height(22.dp))
            AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "widget") { m ->
                PolymorphicWidget(c, settings, m, time)
            }

            AnimatedVisibility(digestOpen, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                DigestCapsule(digestCount) { c.open(Overlay.Digest) }
            }

            Tray(c, stacks, mode)
        }

        ThumbArc(c = c, settings = settings, mode = mode, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private enum class TrayTab { SHELF, STACKS }

/**
 * One tray, two drawers: the transient shelf and the project stacks share a single slot so the
 * home screen never grows into the thumb arc. A fresh arrival on the shelf pulls the tray to it.
 */
@Composable
private fun Tray(c: LauncherController, stacks: List<Stack>, mode: Mode) {
    val p = LocalPalette.current
    val shelf by c.app.shelf.items.collectAsState()
    val arrival by c.app.shelf.arrival.collectAsState()
    val active = if (mode == Mode.SANCTUARY) emptyList() else
        stacks.filter { !it.archived && (it.title != LauncherController.INBOX || it.items.isNotEmpty()) }
    if (shelf.isEmpty() && active.isEmpty()) return

    var tab by remember { mutableStateOf(TrayTab.SHELF) }
    if (arrival != null) tab = TrayTab.SHELF
    val shown = when {
        shelf.isEmpty() -> TrayTab.STACKS
        active.isEmpty() -> TrayTab.SHELF
        else -> tab
    }

    Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (shelf.isNotEmpty()) TrayLabel("الرف العابر", shelf.size, shown == TrayTab.SHELF, p.signal) { c.haptics.tick(); tab = TrayTab.SHELF }
        if (shelf.isNotEmpty() && active.isNotEmpty()) Spacer(Modifier.width(18.dp))
        if (active.isNotEmpty()) TrayLabel("المشاريع", active.size, shown == TrayTab.STACKS, p.ink) { c.haptics.tick(); tab = TrayTab.STACKS }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(p.stroke))
    }
    AnimatedContent(shown, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tray") { t ->
        when (t) {
            TrayTab.SHELF -> ShelfRow(c, shelf)
            TrayTab.STACKS -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(active, key = { it.id }) { s -> StackCard(s) { c.open(Overlay.StackView(s.id)) } }
            }
        }
    }
}

@Composable
private fun TrayLabel(text: String, count: Int, selected: Boolean, signal: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.quietClick(onClick = onClick).padding(vertical = 4.dp)) {
        Text(text, style = Type.label, color = if (selected) p.ink else p.faint)
        Spacer(Modifier.width(6.dp))
        Text("%02d".fmt(count), style = Type.tech, color = if (selected) signal else p.faint)
    }
}

/** Top strip: mode switch on one side, battery as instrument readout on the other. */
@Composable
private fun StatusStrip(c: LauncherController, mode: Mode, context: Context) {
    val p = LocalPalette.current
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.quietClick { c.haptics.tick(); expanded = !expanded }) {
                Dot(p.accent, 7.dp)
                Spacer(Modifier.width(10.dp))
                Text("طور ${mode.title}", style = Type.label, color = p.ink)
                Spacer(Modifier.width(10.dp))
                Text(MODE_CODES.getValue(mode), style = Type.tech, color = p.faint)
            }
            Spacer(Modifier.weight(1f))
            val battery = batteryPercent(context)
            Text("BAT ${battery.first}%", style = Type.tech, color = if (battery.second) p.signal else p.muted)
        }
        AnimatedVisibility(expanded, enter = expandVertically(spring()) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach {
                    Pill(it.title, selected = it == mode, color = paletteFor(it).accent) {
                        expanded = false
                        c.setMode(it)
                    }
                }
            }
        }
    }
}

/** One smart slot that changes its job with mode, time and context instead of a wall of widgets. */
@Composable
private fun PolymorphicWidget(c: LauncherController, settings: Settings, mode: Mode, time: LocalDateTime) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val apps by c.app.apps.apps.collectAsState()
    val minute = time.withSecond(0).withNano(0)
    val event = remember(minute) { c.app.agenda.next() }
    val nowMs = System.currentTimeMillis()
    val imminent = event?.takeIf { it.begin - nowMs <= 60 * 60_000L }
    Column(
        Modifier
            .fillMaxWidth()
            .matte(p, RoundedCornerShape(22.dp), edge = if (imminent != null) p.signal.copy(alpha = 0.55f) else p.highlight)
            .padding(20.dp),
    ) {
        when {
            // An appointment within the hour outranks everything: the slot becomes the meeting card.
            imminent != null -> MeetingCard(c, imminent, nowMs)
            mode == Mode.FOCUS -> {
                FocusWidget(c, settings)
                event?.let { NextEventLine(context, it) }
            }
            headphonesConnected(context) -> {
                Text("AUDIO · OUT", style = Type.tech, color = p.muted)
                Text("السماعة متصلة، كمّل اللي تسمعه", style = Type.title, color = p.ink)
                CalmLinks(c, apps.filter { it.kind == AppKind.CALM })
            }
            mode == Mode.SANCTUARY -> {
                Text("REST · %s".fmt(c.nextAlarm()?.let { "ALARM ${formatClock(context, it)}" } ?: "NO ALARM"), style = Type.tech, color = p.muted)
                Text("وقت السكون", style = Type.title, color = p.ink)
                val today = c.app.friction.todayStats()
                Text(
                    if (today.prompts == 0) "خلي الشاشة تهدأ ويّاك."
                    else "اليوم: %d د على المشتتات · تراجعت %d مرات".fmt(today.actualMs / 60_000, today.retreats),
                    style = Type.body, color = p.muted,
                    modifier = Modifier.quietClick { c.open(Overlay.Report) },
                )
                c.app.agenda.tomorrowFirst()?.let {
                    Text("باچر أول موعد ${formatClock(context, it.begin)} · ${it.title}", style = Type.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CalmLinks(c, apps.filter { it.kind == AppKind.CALM })
            }
            else -> {
                val greeting = when (time.hour) {
                    in 4..11 -> "صباح الخير"
                    in 12..16 -> "نهارك سعيد"
                    else -> "مساء الخير"
                }
                Text(c.nextAlarm()?.let { "NEXT ALARM · ${formatClock(context, it)}" } ?: "TODAY", style = Type.tech, color = p.muted)
                Text(greeting, style = Type.title, color = p.ink)
                val today = remember(minute.toLocalDate(), minute.hour) { c.app.agenda.today().filter { it.end > nowMs } }
                when {
                    !c.app.agenda.hasPermission -> Text("اسحب للأعلى وكول شتريد.", style = Type.body, color = p.muted)
                    today.isEmpty() -> Text("ماكو مواعيد باقية اليوم.", style = Type.body, color = p.muted)
                    else -> Text(
                        "باقي %d %s اليوم، الجاي %s".fmt(today.size, if (today.size == 1) "موعد" else "مواعيد", formatClock(context, today.first().begin)),
                        style = Type.body, color = p.muted,
                    )
                }
            }
        }
    }
}

/** The widget's meeting form: countdown in mono, one tap to join the call or open the event. */
@Composable
private fun MeetingCard(c: LauncherController, e: com.madar.launcher.data.AgendaEvent, now: Long) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val started = e.begin <= now
    Text(
        if (started) "NOW · ENDS ${formatClock(context, e.end)}" else "IN %d MIN · %s".fmt((e.begin - now + 59_999) / 60_000, formatClock(context, e.begin)),
        style = Type.tech, color = p.signal,
    )
    Text(e.title, style = Type.title, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (e.location.isNotBlank()) Text(e.location, style = Type.body, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        e.joinUrl?.let { url ->
            Pill("انضم", selected = true, color = p.signal) { c.haptics.completion(); c.start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
        }
        Pill("التفاصيل") {
            c.start(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.CalendarContract.Events.CONTENT_URI, e.eventId)))
        }
    }
}

@Composable
private fun NextEventLine(context: Context, e: com.madar.launcher.data.AgendaEvent) {
    val p = LocalPalette.current
    Text(
        "الموعد الجاي ${formatClock(context, e.begin)} · ${e.title}",
        style = Type.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun CalmLinks(c: LauncherController, calm: List<com.madar.launcher.data.AppEntry>) {
    if (calm.isEmpty()) return
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        calm.take(3).forEach { a -> Pill(a.label) { c.requestLaunch(a) } }
    }
}

@Composable
private fun FocusWidget(c: LauncherController, settings: Settings) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var task by remember(settings.focusTask) { mutableStateOf(settings.focusTask) }
    Text("TASK · الآن", style = Type.tech, color = p.muted)
    BasicTextField(
        value = task,
        onValueChange = { task = it },
        textStyle = Type.title.copy(color = p.ink),
        cursorBrush = SolidColor(p.signal),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            c.app.settings.edit { it.copy(focusTask = task.trim()) }
            focus.clearFocus()
        }),
        decorationBox = { inner ->
            Box {
                if (task.isEmpty()) Text("شنو تشتغل عليه هسه؟", style = Type.title, color = p.faint)
                inner()
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
    val running = settings.pomodoroEndsAt > System.currentTimeMillis()
    if (running) {
        val now by rememberNow(1_000)
        val left = ((settings.pomodoroEndsAt - now) / 1000).coerceAtLeast(0)
        val total = settings.pomodoroMinutes * 60f
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("%02d:%02d".fmt(left / 60, left % 60), style = Type.dataLarge, color = p.signal)
            Spacer(Modifier.weight(1f))
            TextAction("إيقاف", color = p.muted) { c.stopPomodoro() }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.stroke)) {
            Box(Modifier.fillMaxWidth((left / total).coerceIn(0f, 1f)).height(2.dp).background(p.signal))
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            listOf(25, 50).forEach { m -> Pill("$m د") { c.haptics.completion(); c.startPomodoro(m) } }
            Pill("ملاحظة") { c.open(Overlay.Palette("ملاحظة ")) }
        }
    }
}

@Composable
private fun DigestCapsule(count: Int, onOpen: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .padding(top = 14.dp)
            .matte(p, RoundedCornerShape(14.dp), edge = p.calm.copy(alpha = 0.6f))
            .quietClick(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(p.calm, 6.dp)
        Spacer(Modifier.width(10.dp))
        Text("ملخصك الهادئ جاهز", style = Type.label, color = p.ink)
        Spacer(Modifier.width(10.dp))
        Text("%02d".fmt(count), style = Type.tech, color = p.calm)
    }
}

@Composable
private fun StackCard(stack: Stack, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .widthIn(min = 128.dp, max = 200.dp)
            .matte(p, RoundedCornerShape(16.dp))
            .quietClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stack.title, style = Type.label, color = p.ink, maxLines = 1)
        Text("%02d ITEMS".fmt(stack.items.size), style = Type.tech, color = p.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

/** (percent, charging) */
private fun batteryPercent(context: Context): Pair<Int, Boolean> {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0 to false
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return level * 100 / scale to (status == BatteryManager.BATTERY_STATUS_CHARGING)
}

private fun headphonesConnected(context: Context): Boolean =
    context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type in setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }

private fun formatClock(context: Context, millis: Long): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    return t.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a", java.util.Locale.ROOT))
}
