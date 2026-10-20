package com.madar.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madar.launcher.data.DayStats
import com.madar.launcher.fmt
import java.time.LocalDate

private val WEEKDAY_INITIALS = listOf("ن", "ث", "ر", "خ", "ج", "س", "ح") // Mon..Sun

/** The quiet evening report: what the gate caught today, and the week as a strip of bars. */
@Composable
fun ReportSheet(c: LauncherController) {
    val p = LocalPalette.current
    val days by c.app.friction.days.collectAsState()
    val week = remember(days) { c.app.friction.lastDays(7) }
    val today = week.last()
    val apps by c.app.apps.apps.collectAsState()

    Sheet("تقرير اليوم", onClose = c::close) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("ATTENTION · ${LocalDate.now()}", style = Type.tech, color = p.muted)

            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("تراجعت", "%02d".fmt(today.retreats), "مرة", p.calm, Modifier.weight(1f))
                Metric("على المشتتات", minutes(today.actualMs), "دقيقة", p.signal, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("نويت", minutes(today.intendedMs), "دقيقة", p.ink, Modifier.weight(1f))
                Metric("زيادة عن نيتك", minutes(today.overrunMs), "دقيقة", if (today.overrunMs > 0) p.signal else p.muted, Modifier.weight(1f))
            }

            Text(verdict(today), style = Type.body, color = p.muted, modifier = Modifier.padding(top = 18.dp))

            SectionTitle("آخر 7 أيام", code = "MIN / DAY")
            WeekBars(week)

            val top = today.perApp.entries.sortedByDescending { it.value }.take(4)
            if (top.isNotEmpty()) {
                SectionTitle("وين راح الوقت", code = "TOP")
                top.forEach { (pkg, ms) ->
                    val label = apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(label, style = Type.body, color = p.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${minutes(ms)} MIN", style = Type.data, color = p.muted)
                    }
                }
            }

            if (!com.madar.launcher.service.FrictionService.running) {
                Text(
                    "الأرقام ناقصة: فعّل \"الاحتكاك الذكي\" من الإعدادات حتى مدار يحسب التطبيقات اللي تنفتح من الإشعارات أيضاً.",
                    style = Type.caption, color = p.signal, modifier = Modifier.padding(top = 20.dp),
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier.matte(p, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(label, style = Type.caption, color = p.muted)
        Text(value, style = Type.dataLarge, color = color)
        Text(unit, style = Type.caption, color = p.faint)
    }
}

/** Seven thin bars, today in signal orange, the rest etched; a hairline marks what was intended. */
@Composable
private fun WeekBars(week: List<DayStats>) {
    val p = LocalPalette.current
    val max = (week.maxOf { maxOf(it.actualMs, it.intendedMs) }).coerceAtLeast(60_000L).toFloat()
    Column(Modifier.matte(p, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / week.size
            val barW = slot * 0.34f
            week.forEachIndexed { i, d ->
                // RTL reading order: today sits on the right, like the rest of the interface.
                val x = size.width - (i + 0.5f) * slot - barW / 2
                val reversed = week.size - 1 - i
                val day = week[reversed]
                val h = size.height * (day.actualMs / max)
                drawRoundRect(
                    color = if (reversed == week.lastIndex) p.signal else p.faint,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(3f, 3f),
                )
                if (day.intendedMs > 0) {
                    val y = size.height - size.height * (day.intendedMs / max)
                    drawLine(p.calm, Offset(x - 8f, y), Offset(x + barW + 8f, y), strokeWidth = 4f)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // RTL row: the first child sits on the right, so lead with today.
            week.reversed().forEach { d ->
                val dow = LocalDate.parse(d.date).dayOfWeek.ordinal
                Text(
                    WEEKDAY_INITIALS[dow],
                    style = Type.caption,
                    color = if (d === week.last()) p.signal else p.muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.padding(top = 10.dp)) {
            Dot(p.calm, 6.dp)
            Spacer(Modifier.width(6.dp))
            Text("خط النية", style = Type.caption, color = p.muted)
        }
    }
}

private fun minutes(ms: Long) = "%02d".fmt(ms / 60_000)

private fun verdict(d: DayStats): String = when {
    d.prompts == 0 -> "ماكو محاولات اليوم. يوم هادئ."
    d.overrunMs == 0L && d.retreats > 0 -> "التزمت بكل نية حددتها، وتراجعت ${d.retreats} مرات. يوم موفق."
    d.overrunMs == 0L -> "التزمت بالوقت اللي حددته كل مرة."
    d.overrunMs < 10 * 60_000 -> "زدت شوية عن نيتك. باچر نكدر أحسن."
    else -> "الوقت هرب اليوم. جرّب تختار استراحات أقصر."
}
