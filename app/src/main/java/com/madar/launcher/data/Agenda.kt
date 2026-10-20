package com.madar.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

data class AgendaEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    /** A video-call link found in the location or description, if any. */
    val joinUrl: String?,
)

/** Read-only view of the phone's calendars for the polymorphic widget. Local provider only; no network. */
class AgendaRepository(private val context: Context) {

    val hasPermission: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    @androidx.annotation.VisibleForTesting
    var fakeEvents: List<AgendaEvent>? = null

    fun between(from: Long, to: Long): List<AgendaEvent> {
        fakeEvents?.let { list -> return list.filter { it.end > from && it.begin < to } }
        if (!hasPermission) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.DESCRIPTION,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                ),
                null, null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(6) == 1) continue // all-day events aren't appointments
                        if (c.getInt(7) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                        val location = c.getString(4).orEmpty()
                        val description = c.getString(5).orEmpty()
                        add(
                            AgendaEvent(
                                eventId = c.getLong(0),
                                title = c.getString(1)?.ifBlank { null } ?: "موعد",
                                begin = c.getLong(2),
                                end = c.getLong(3),
                                location = location.takeUnless { CALL_LINK.containsMatchIn(it) }.orEmpty(),
                                joinUrl = CALL_LINK.find(location)?.value ?: CALL_LINK.find(description)?.value,
                            ),
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /** What's on now or next within [horizonMs]. */
    fun next(now: Long = System.currentTimeMillis(), horizonMs: Long = 12 * 3_600_000L): AgendaEvent? =
        between(now - 6 * 3_600_000L, now + horizonMs).firstOrNull { it.end > now }

    fun today(): List<AgendaEvent> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        return between(start, start + 86_400_000L)
    }

    fun tomorrowFirst(): AgendaEvent? {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return between(start, start + 86_400_000L).firstOrNull()
    }

    private companion object {
        val CALL_LINK = Regex(
            "https?://(?:[\\w-]+\\.)?(?:meet\\.google\\.com|zoom\\.us|teams\\.microsoft\\.com|teams\\.live\\.com|whereby\\.com|meet\\.jit\\.si)/\\S+",
            RegexOption.IGNORE_CASE,
        )
    }
}
