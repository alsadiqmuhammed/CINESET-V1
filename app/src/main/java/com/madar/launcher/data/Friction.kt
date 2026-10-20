package com.madar.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/** A declared intention: "I'm opening Instagram to reply to a message, for 3 minutes". */
@Serializable
data class Session(
    val packageName: String,
    val intention: String,
    val minutes: Int,
    val startedAt: Long,
    val endsAt: Long,
    val extended: Boolean = false,
)

/** One day of attention, kept for the quiet evening report. */
@Serializable
data class DayStats(
    val date: String,
    /** Times the gate asked "ما غايتك الآن؟". */
    val prompts: Int = 0,
    /** Times the user backed out instead. */
    val retreats: Int = 0,
    val sessions: Int = 0,
    val intendedMs: Long = 0,
    /** Real foreground time in gated apps. */
    val actualMs: Long = 0,
    /** Time spent past the declared budget. */
    val overrunMs: Long = 0,
    val perApp: Map<String, Long> = emptyMap(),
)

class FrictionRepository(context: Context) {

    private val sessionsFile = File(context.filesDir, "sessions.json")
    private val daysFile = File(context.filesDir, "attention.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionsSerializer = MapSerializer(String.serializer(), Session.serializer())

    private val sessions: MutableMap<String, Session> =
        runCatching { json.decodeFromString(sessionsSerializer, sessionsFile.readText()).toMutableMap() }.getOrDefault(mutableMapOf())

    private val _days = MutableStateFlow(
        runCatching { json.decodeFromString(ListSerializer(DayStats.serializer()), daysFile.readText()) }.getOrDefault(emptyList()),
    )
    val days: StateFlow<List<DayStats>> = _days.asStateFlow()

    @Synchronized
    fun session(pkg: String): Session? = sessions[pkg]

    /** A session still inside its budget; returning to the app within it doesn't re-ask. */
    @Synchronized
    fun activeSession(pkg: String, now: Long = System.currentTimeMillis()): Session? = sessions[pkg]?.takeIf { it.endsAt > now }

    @Synchronized
    fun startSession(pkg: String, intention: String, minutes: Int, now: Long = System.currentTimeMillis()): Session {
        val s = Session(pkg, intention, minutes, now, now + minutes * 60_000L)
        sessions[pkg] = s
        saveSessions()
        today { it.copy(sessions = it.sessions + 1, intendedMs = it.intendedMs + minutes * 60_000L) }
        return s
    }

    /** One grace extension per session; a second one would just be the endless scroll again. */
    @Synchronized
    fun extend(pkg: String, minutes: Int = 5, now: Long = System.currentTimeMillis()): Session? {
        val s = sessions[pkg]?.takeIf { !it.extended } ?: return null
        val next = s.copy(endsAt = now + minutes * 60_000L, extended = true)
        sessions[pkg] = next
        saveSessions()
        today { it.copy(intendedMs = it.intendedMs + minutes * 60_000L) }
        return next
    }

    @Synchronized
    fun endSession(pkg: String) {
        if (sessions.remove(pkg) != null) saveSessions()
    }

    fun recordPrompt() = today { it.copy(prompts = it.prompts + 1) }

    fun recordRetreat(@Suppress("UNUSED_PARAMETER") pkg: String) = today { it.copy(retreats = it.retreats + 1) }

    fun recordUsage(pkg: String, ms: Long, overrunMs: Long) {
        if (ms <= 0) return
        today {
            it.copy(
                actualMs = it.actualMs + ms,
                overrunMs = it.overrunMs + overrunMs.coerceAtLeast(0),
                perApp = it.perApp + (pkg to (it.perApp[pkg] ?: 0) + ms),
            )
        }
    }

    fun todayStats(): DayStats = _days.value.lastOrNull { it.date == LocalDate.now().toString() } ?: DayStats(LocalDate.now().toString())

    /** The last [count] days, oldest first, with empty days filled in so charts keep their rhythm. */
    fun lastDays(count: Int = 7): List<DayStats> {
        val byDate = _days.value.associateBy { it.date }
        val today = LocalDate.now()
        return (count - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong()).toString()
            byDate[d] ?: DayStats(d)
        }
    }

    private fun today(transform: (DayStats) -> DayStats) {
        val date = LocalDate.now().toString()
        _days.update { list ->
            val current = list.lastOrNull { it.date == date } ?: DayStats(date)
            (list.filterNot { it.date == date } + transform(current)).takeLast(KEEP_DAYS)
        }
        daysFile.writeText(json.encodeToString(ListSerializer(DayStats.serializer()), _days.value))
    }

    private fun saveSessions() {
        val now = System.currentTimeMillis()
        // Sessions older than a day carry no meaning; drop them as we go.
        sessions.entries.removeAll { now - it.value.endsAt > 86_400_000L }
        sessionsFile.writeText(json.encodeToString(sessionsSerializer, sessions))
    }

    private companion object {
        const val KEEP_DAYS = 30
    }
}
