package com.madar.launcher.data

import android.app.PendingIntent
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@Serializable
data class DigestItem(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long,
) {
    /** Only valid while the process that captured it lives; falls back to opening the app. */
    @Transient var contentIntent: PendingIntent? = null
}

/** Notifications held back for the calm digest capsule. */
class DigestRepository(context: Context, private val settings: SettingsStore) {

    private val file = File(context.filesDir, "digest.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<DigestItem>> = _items.asStateFlow()

    fun add(item: DigestItem) = mutate { list -> list.filterNot { it.key == item.key } + item }

    fun remove(key: String) = mutate { list -> list.filterNot { it.key == key } }

    fun clear() = mutate { emptyList() }

    fun markViewed() = settings.edit { it.copy(lastDigestViewedAt = System.currentTimeMillis()) }

    /** The capsule opens once the most recent digest slot has passed and hasn't been viewed since. */
    fun isCapsuleOpen(s: Settings, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (_items.value.isEmpty()) return false
        val lastSlot = lastSlotBefore(s.digestTimes, now) ?: return false
        return s.lastDigestViewedAt < lastSlot
    }

    private fun lastSlotBefore(times: List<Int>, now: ZonedDateTime): Long? {
        if (times.isEmpty()) return null
        val today = now.toLocalDate()
        return listOf(today.minusDays(1), today)
            .flatMap { day -> times.map { day.atStartOfDay(now.zone).plusMinutes(it.toLong()) } }
            .filter { !it.isAfter(now) }
            .maxOrNull()
            ?.toInstant()
            ?.toEpochMilli()
    }

    private fun mutate(transform: (List<DigestItem>) -> List<DigestItem>) {
        _items.update { transform(it).takeLast(MAX_ITEMS) }
        file.writeText(json.encodeToString(ListSerializer(DigestItem.serializer()), _items.value))
    }

    private fun load(): List<DigestItem> = runCatching {
        json.decodeFromString(ListSerializer(DigestItem.serializer()), file.readText())
    }.getOrDefault(emptyList())

    private companion object {
        const val MAX_ITEMS = 300
    }
}

fun Long.toLocalTimeLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
