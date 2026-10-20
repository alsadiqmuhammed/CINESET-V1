package com.madar.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
sealed interface StackItem {
    val id: String

    @Serializable @SerialName("app")
    data class App(override val id: String, val component: String, val label: String) : StackItem

    @Serializable @SerialName("link")
    data class Link(override val id: String, val url: String, val title: String) : StackItem

    @Serializable @SerialName("note")
    data class Note(override val id: String, val text: String, val cursor: Int = 0) : StackItem

    @Serializable @SerialName("contact")
    data class Contact(override val id: String, val name: String, val phone: String) : StackItem

    @Serializable @SerialName("file")
    data class Doc(override val id: String, val uri: String, val name: String, val mime: String?) : StackItem
}

@Serializable
data class Stack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val items: List<StackItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Optional end date (epoch millis); the stack archives itself once it passes. */
    val dueAt: Long? = null,
    val archived: Boolean = false,
    /** The item the user was last on, restored when the stack is reopened (context freezing). */
    val focusedItemId: String? = null,
    val openedAt: Long = 0,
)

/** Contextual project stacks, persisted as a single JSON file. */
class StacksRepository(context: Context) {

    private val file = File(context.filesDir, "stacks.json")
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    private val _stacks = MutableStateFlow(load())
    val stacks: StateFlow<List<Stack>> = _stacks.asStateFlow()

    init {
        archiveExpired()
    }

    fun get(id: String): Stack? = _stacks.value.firstOrNull { it.id == id }

    fun create(title: String): Stack {
        val stack = Stack(title = title.trim().ifEmpty { "مشروع جديد" })
        mutate { it + stack }
        return stack
    }

    fun update(id: String, transform: (Stack) -> Stack) =
        mutate { list -> list.map { if (it.id == id) transform(it) else it } }

    fun addItem(id: String, item: StackItem) = update(id) { it.copy(items = it.items + item) }

    fun removeItem(id: String, itemId: String) =
        update(id) { s -> s.copy(items = s.items.filterNot { it.id == itemId }) }

    fun replaceItem(id: String, item: StackItem) =
        update(id) { s -> s.copy(items = s.items.map { if (it.id == item.id) item else it }) }

    fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    fun archiveExpired(now: Long = System.currentTimeMillis()) {
        if (_stacks.value.any { !it.archived && (it.dueAt ?: Long.MAX_VALUE) < now }) {
            mutate { list -> list.map { if ((it.dueAt ?: Long.MAX_VALUE) < now) it.copy(archived = true) else it } }
        }
    }

    private fun mutate(transform: (List<Stack>) -> List<Stack>) {
        _stacks.update(transform)
        val snapshot = _stacks.value
        file.writeText(json.encodeToString(ListSerializer, snapshot))
    }

    private fun load(): List<Stack> =
        runCatching { json.decodeFromString(ListSerializer, file.readText()) }.getOrDefault(emptyList())

    private companion object {
        val ListSerializer = kotlinx.serialization.builtins.ListSerializer(Stack.serializer())
    }
}

fun newItemId(): String = UUID.randomUUID().toString()
