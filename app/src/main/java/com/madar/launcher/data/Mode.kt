package com.madar.launcher.data

import com.madar.launcher.fmt
import java.time.LocalTime

/** The three cognitive modes the home screen morphs between. */
enum class Mode(val title: String, val subtitle: String) {
    FOCUS("التركيز العميق", "المهمة الحالية فقط"),
    FLOW("التدفق اليومي", "كل شي بمكانه"),
    SANCTUARY("السكون المسائي", "قراءة، صوت، ومن تحب"),
}

/** Minutes since midnight, the unit every schedule in the app is stored in. */
typealias DayMinute = Int

fun LocalTime.toDayMinute(): DayMinute = hour * 60 + minute

fun DayMinute.format(): String = "%02d:%02d".fmt(this / 60, this % 60)

data class ModeSchedule(
    val focusStart: DayMinute = 9 * 60,
    val focusEnd: DayMinute = 14 * 60,
    val sanctuaryStart: DayMinute = 21 * 60 + 30,
    val sanctuaryEnd: DayMinute = 6 * 60,
) {
    fun modeAt(now: DayMinute): Mode = when {
        now.inWindow(sanctuaryStart, sanctuaryEnd) -> Mode.SANCTUARY
        now.inWindow(focusStart, focusEnd) -> Mode.FOCUS
        else -> Mode.FLOW
    }

    /** The next minute at which the scheduled mode changes; manual overrides expire there. */
    fun nextBoundaryAfter(now: DayMinute): DayMinute =
        listOf(focusStart, focusEnd, sanctuaryStart, sanctuaryEnd)
            .minBy { (it - now - 1).mod(MINUTES_PER_DAY) }

    private fun DayMinute.inWindow(start: DayMinute, end: DayMinute): Boolean =
        if (start <= end) this in start until end else this >= start || this < end
}

const val MINUTES_PER_DAY = 24 * 60
