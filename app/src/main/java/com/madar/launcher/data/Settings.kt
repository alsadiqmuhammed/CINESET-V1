package com.madar.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    val focusStart: Int = 9 * 60,
    val focusEnd: Int = 14 * 60,
    val sanctuaryStart: Int = 21 * 60 + 30,
    val sanctuaryEnd: Int = 6 * 60,
    /** Manually chosen mode and the epoch-minute at which the override lapses. */
    val modeOverride: Mode? = null,
    val overrideUntil: Long = 0,
    val rightHanded: Boolean = true,
    /** Apps pinned to the thumb arc, as flattened component names. */
    val pinned: List<String> = emptyList(),
    /** User reclassification of apps, keyed by package name. */
    val kindOverrides: Map<String, AppKind> = emptyMap(),
    val digestEnabled: Boolean = true,
    /** Packages whose notifications skip the digest and arrive immediately. */
    val instantPackages: Set<String> = emptySet(),
    /** Packages forced into the digest even though their kind would let them through. */
    val digestPackages: Set<String> = emptySet(),
    val digestTimes: List<Int> = listOf(13 * 60, 20 * 60),
    val lastDigestViewedAt: Long = 0,
    val focusTask: String = "",
    val pomodoroEndsAt: Long = 0,
    val pomodoroMinutes: Int = 25,
    val onboarded: Boolean = false,
    /** System-wide gate and time-up desaturation (needs the accessibility service). */
    val frictionEnabled: Boolean = true,
    /** Previous system colour-correction state, saved while Madar has the screen in grayscale. */
    val grayscaleHeld: Boolean = false,
    val previousDaltonizerMode: Int = -1,
    /** Ghost space: packages that exist only inside the vault. */
    val hiddenPackages: Set<String> = emptySet(),
) {
    val schedule get() = ModeSchedule(focusStart, focusEnd, sanctuaryStart, sanctuaryEnd)

    /** The mode in force right now: a live manual override, else the schedule. */
    fun modeAt(nowMillis: Long = System.currentTimeMillis()): Mode {
        if (modeOverride != null && nowMillis / 60_000 < overrideUntil) return modeOverride
        val t = java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        return schedule.modeAt(t.toDayMinute())
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("madar", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val value: Settings get() = _state.value

    fun edit(transform: (Settings) -> Settings) {
        _state.update(transform)
        prefs.edit().putString(KEY, json.encodeToString(Settings.serializer(), _state.value)).apply()
    }

    private fun load(): Settings =
        prefs.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() }
            ?: Settings()

    private companion object {
        const val KEY = "settings"
    }
}
