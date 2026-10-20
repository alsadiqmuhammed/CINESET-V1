package com.madar.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
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

@Composable
fun HomeScreen(c: LauncherController, settings: Settings, mode: Mode, stacks: List<Stack>, digestOpen: Boolean, digestCount: Int) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val threshold = with(LocalDensity.current) { 90.dp.toPx() }
    val now by rememberNow()
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())

    Box(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = {
                        when {
                            total < -threshold -> { c.haptics.tick(); c.open(Overlay.Palette()) }
                            total > threshold -> c.expandNotifications()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { c.haptics.heavy(); c.open(Overlay.Settings) })
            },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(20.dp))
            ModeSwitcher(mode) { c.setMode(it) }
            Spacer(Modifier.height(28.dp))

            val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
            Text(time.format(DateTimeFormatter.ofPattern(pattern)), style = Type.clock, color = p.ink)
            Text(
                "${DAYS[time.dayOfWeek.ordinal]}، ${time.dayOfMonth} ${MONTHS[time.monthValue - 1]}",
                style = Type.title, color = p.muted,
            )

            Spacer(Modifier.height(28.dp))
            AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "widget") { m ->
                PolymorphicWidget(c, settings, m, time)
            }

            AnimatedVisibility(digestOpen) {
                DigestCapsule(digestCount) { c.open(Overlay.Digest) }
            }

            val active = stacks.filter { !it.archived && (it.title != LauncherController.INBOX || it.items.isNotEmpty()) }
            if (active.isNotEmpty() && mode != Mode.SANCTUARY) {
                SectionTitle("المشاريع")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(active, key = { it.id }) { s -> StackCard(s) { c.open(Overlay.StackView(s.id)) } }
                }
            }
        }

        ThumbArc(
            c = c,
            settings = settings,
            mode = mode,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ModeSwitcher(mode: Mode, onSelect: (Mode) -> Unit) {
    val p = LocalPalette.current
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.quietClick { expanded = !expanded },
        ) {
            Dot(p.accent)
            Spacer(Modifier.width(10.dp))
            Text("طور ${mode.title}", style = Type.label, color = p.ink)
            Spacer(Modifier.width(8.dp))
            Text("· ${mode.subtitle}", style = Type.caption, color = p.muted)
        }
        AnimatedVisibility(expanded) {
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach {
                    Pill(it.title, selected = it == mode) {
                        expanded = false
                        onSelect(it)
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
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.surface)
            .border(1.dp, p.faint, RoundedCornerShape(24.dp))
            .padding(20.dp),
    ) {
        when {
            mode == Mode.FOCUS -> FocusWidget(c, settings)
            headphonesConnected(context) -> {
                Text("السماعة متصلة", style = Type.caption, color = p.muted)
                Text("كمّل اللي تسمعه", style = Type.title, color = p.ink)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    apps.filter { it.kind == AppKind.CALM }.take(3).forEach { a ->
                        TextAction(a.label) { c.requestLaunch(a) }
                    }
                }
            }
            mode == Mode.SANCTUARY -> {
                Text("وقت السكون", style = Type.title, color = p.ink)
                val alarm = c.nextAlarm()
                Text(
                    alarm?.let { "منبهك الجاي ${formatClock(context, it)}" } ?: "ماكو منبه مضبوط",
                    style = Type.body, color = p.muted,
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    apps.filter { it.kind == AppKind.CALM }.take(3).forEach { a ->
                        TextAction(a.label) { c.requestLaunch(a) }
                    }
                }
            }
            else -> {
                val greeting = when (time.hour) {
                    in 4..11 -> "صباح الخير"
                    in 12..16 -> "نهارك سعيد"
                    else -> "مساء الخير"
                }
                Text(greeting, style = Type.title, color = p.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("البطارية ${batteryPercent(context)}%", style = Type.body, color = p.muted)
                    c.nextAlarm()?.let { Text("منبه ${formatClock(context, it)}", style = Type.body, color = p.muted) }
                }
            }
        }
    }
}

@Composable
private fun FocusWidget(c: LauncherController, settings: Settings) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var task by remember(settings.focusTask) { mutableStateOf(settings.focusTask) }
    Text("المهمة الحالية", style = Type.caption, color = p.muted)
    BasicTextField(
        value = task,
        onValueChange = { task = it },
        textStyle = Type.title.copy(color = p.ink),
        cursorBrush = SolidColor(p.accent),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%02d:%02d".format(left / 60, left % 60), style = Type.display, color = p.accent)
            Spacer(Modifier.weight(1f))
            TextAction("إيقاف", color = p.muted) { c.stopPomodoro() }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            listOf(25, 50).forEach { m -> Pill("ابدأ $m دقيقة") { c.haptics.completion(); c.startPomodoro(m) } }
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
            .clip(RoundedCornerShape(50))
            .background(p.surface)
            .border(1.dp, p.accent.copy(alpha = 0.5f), RoundedCornerShape(50))
            .quietClick(onClick = onOpen)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(p.accent, 6.dp)
        Spacer(Modifier.width(10.dp))
        Text("ملخصك الهادئ جاهز · $count", style = Type.label, color = p.ink)
    }
}

@Composable
private fun StackCard(stack: Stack, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .widthIn(min = 120.dp, max = 200.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(p.surface)
            .border(1.dp, p.faint, RoundedCornerShape(18.dp))
            .quietClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stack.title, style = Type.label, color = p.ink, maxLines = 1)
        Text("${stack.items.size} عناصر", style = Type.caption, color = p.muted)
    }
}

private fun batteryPercent(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    return level * 100 / scale
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
    return t.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"))
}
