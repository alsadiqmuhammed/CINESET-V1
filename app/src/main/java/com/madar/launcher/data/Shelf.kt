package com.madar.launcher.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
enum class ShelfKind { TEXT, LINK, IMAGE, FILE }

@Serializable
data class ShelfItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: ShelfKind,
    val text: String = "",
    /** Private copy under files/shelf; shared URIs lose their grant once the sender's task ends. */
    val path: String? = null,
    val name: String = "",
    val mime: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = addedAt + LIFETIME_MS,
) {
    companion object {
        const val LIFETIME_MS = 2 * 60 * 60 * 1000L
    }
}

/**
 * The transient shelf: anything shared "to Madar" lands here and evaporates after two hours.
 * Files are copied into private storage so they stay openable for the item's lifetime.
 */
class ShelfRepository(private val context: Context) {

    private val dir = File(context.filesDir, "shelf").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<ShelfItem>> = _items.asStateFlow()

    /** Id of the most recent arrival, so the home screen can play the magnetic snap exactly once. */
    private val _arrival = MutableStateFlow<String?>(null)
    val arrival: StateFlow<String?> = _arrival.asStateFlow()

    fun consumeArrival() {
        _arrival.value = null
    }

    fun addText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val isLink = URL.matches(trimmed)
        add(ShelfItem(kind = if (isLink) ShelfKind.LINK else ShelfKind.TEXT, text = trimmed, name = if (isLink) Uri.parse(trimmed).host.orEmpty() else ""))
    }

    fun addStream(uri: Uri, mime: String?) {
        val resolver = context.contentResolver
        val type = mime ?: resolver.getType(uri)
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment ?: "ملف"
        val id = UUID.randomUUID().toString()
        val target = File(dir, id + name.substringAfterLast('.', "").let { if (it.isNotEmpty() && it.length <= 5) ".$it" else "" })
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return
        add(
            ShelfItem(
                id = id,
                kind = if (type?.startsWith("image/") == true) ShelfKind.IMAGE else ShelfKind.FILE,
                path = target.absolutePath,
                name = name,
                mime = type,
            ),
        )
    }

    fun remove(id: String) {
        _items.value.firstOrNull { it.id == id }?.path?.let { File(it).delete() }
        mutate { list -> list.filterNot { it.id == id } }
    }

    /** Silently drop anything that expired more than [graceMs] ago; recent expiries are left to dissolve on screen. */
    fun purge(now: Long = System.currentTimeMillis(), graceMs: Long = 10_000) {
        _items.value.filter { it.expiresAt + graceMs < now }.forEach { remove(it.id) }
    }

    fun reload() {
        _items.value = load()
    }

    private fun add(item: ShelfItem) {
        mutate { it + item }
        _arrival.value = item.id
    }

    private fun mutate(transform: (List<ShelfItem>) -> List<ShelfItem>) {
        _items.update(transform)
        index.writeText(json.encodeToString(ListSerializer(ShelfItem.serializer()), _items.value))
    }

    private fun load(): List<ShelfItem> = runCatching {
        json.decodeFromString(ListSerializer(ShelfItem.serializer()), index.readText())
    }.getOrDefault(emptyList())

    private companion object {
        val URL = Regex("^(https?://|www\\.)\\S+$", RegexOption.IGNORE_CASE)
    }
}
