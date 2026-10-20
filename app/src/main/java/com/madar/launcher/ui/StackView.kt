package com.madar.launcher.ui

import android.app.DatePickerDialog
import android.content.ClipboardManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madar.launcher.data.Stack
import com.madar.launcher.data.StackItem
import com.madar.launcher.data.newItemId
import com.madar.launcher.data.toBlueprint
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private enum class Adding(val title: String) { APP("تطبيق"), LINK("رابط"), CONTACT("جهة اتصال") }

/** A project stack: apps, links, notes, people and files for one piece of work, in one place. */
@Composable
fun StackView(c: LauncherController, id: String) {
    val p = LocalPalette.current
    val stacks by c.app.stacks.stacks.collectAsState()
    val stack = stacks.firstOrNull { it.id == id } ?: run { c.close(); return }
    var editingNote by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf<Adding?>(null) }
    // Context freezing: remember which item was in hand when the stack was last left.
    val resumeId = remember { stack.focusedItemId }
    val listState = rememberLazyListState()

    LaunchedEffect(id) {
        c.app.stacks.update(id) { it.copy(openedAt = System.currentTimeMillis()) }
        val idx = stack.items.indexOfFirst { it.id == resumeId }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    fun focus(item: StackItem) = c.app.stacks.update(id) { it.copy(focusedItemId = item.id) }

    Sheet(
        title = "",
        onClose = c::close,
        trailing = {
            TextAction("قالب", color = p.muted) { c.open(Overlay.Template(stack.toBlueprint(), importing = false)) }
            if (!stack.archived) TextAction("إنهاء وأرشفة", color = p.muted) {
                c.app.stacks.update(id) { it.copy(archived = true) }
                c.haptics.completion()
                c.close()
                c.toast("انأرشف ${stack.title}")
            }
        },
    ) {
        Column(Modifier.imePadding()) {
            StackHeader(c, stack)
            LazyColumn(state = listState, modifier = Modifier.weight(1f, fill = false).padding(top = 8.dp)) {
                items(stack.items, key = { it.id }) { item ->
                    if (item is StackItem.Note && editingNote == item.id) {
                        NoteEditor(c, id, item) { editingNote = null }
                    } else {
                        ItemRow(item, resumed = item.id == resumeId, onOpen = {
                            focus(item)
                            if (item is StackItem.Note) editingNote = item.id else c.openItem(item)
                        }, onRemove = {
                            c.haptics.heavy()
                            c.app.stacks.removeItem(id, item.id)
                        })
                    }
                }
            }

            when (val a = adding) {
                null -> Unit
                else -> AddPanel(c, a, onAdd = { item ->
                    c.app.stacks.addItem(id, item)
                    c.haptics.completion()
                    adding = null
                }, onCancel = { adding = null })
            }

            if (adding == null) {
                LazyRow(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Pill("+ ملاحظة") {
                        val note = StackItem.Note(newItemId(), "")
                        c.app.stacks.addItem(id, note)
                        editingNote = note.id
                    } }
                    items(Adding.entries) { a -> Pill("+ ${a.title}") { adding = a } }
                    item { Pill("+ ملف") {
                        c.pickDocument { uri ->
                            val name = displayName(c, uri)
                            c.app.stacks.addItem(id, StackItem.Doc(newItemId(), uri.toString(), name, c.app.contentResolver.getType(uri)))
                            c.haptics.completion()
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun StackHeader(c: LauncherController, stack: Stack) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var title by remember(stack.id) { mutableStateOf(stack.title) }
    LaunchedEffect(title) {
        delay(400)
        if (title.isNotBlank() && title != stack.title) c.app.stacks.update(stack.id) { it.copy(title = title.trim()) }
    }
    BasicTextField(
        value = title,
        onValueChange = { title = it },
        textStyle = Type.display.copy(color = p.ink),
        cursorBrush = SolidColor(p.signal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        val due = stack.dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        Text(
            if (due == null) "بدون موعد انتهاء" else "يتأرشف بعد ${due.dayOfMonth}/${due.monthValue}",
            style = Type.caption, color = p.muted,
        )
        Spacer(Modifier.width(8.dp))
        TextAction(if (due == null) "حدد موعد" else "تغيير") {
            val start = due ?: LocalDate.now().plusDays(7)
            DatePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, y, m, d ->
                val millis = LocalDate.of(y, m + 1, d).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                c.app.stacks.update(stack.id) { it.copy(dueAt = millis) }
            }, start.year, start.monthValue - 1, start.dayOfMonth).show()
        }
    }
}

@Composable
private fun ItemRow(item: StackItem, resumed: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val p = LocalPalette.current
    val (tag, title, subtitle) = when (item) {
        is StackItem.App -> Triple("تطبيق", item.label, "")
        is StackItem.Link -> Triple("رابط", item.title, item.url)
        is StackItem.Note -> Triple("ملاحظة", item.text.lineSequence().firstOrNull().orEmpty().ifBlank { "ملاحظة فارغة" }, item.text.lines().drop(1).joinToString(" "))
        is StackItem.Contact -> Triple("اتصال", item.name, item.phone)
        is StackItem.Doc -> Triple("ملف", item.name, item.mime.orEmpty())
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (resumed) Modifier.matte(p, RoundedCornerShape(16.dp), edge = p.signal.copy(alpha = 0.6f)) else Modifier.matte(p, RoundedCornerShape(16.dp)))
            .quietClick(onLongClick = onRemove, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (resumed) Text("RESUME · وصلت هنا آخر مرة", style = Type.tech, color = p.signal)
            Text(title, style = Type.title, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = Type.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Tag(tag)
    }
}

/** Notes reopen with the cursor exactly where it was left. */
@Composable
private fun NoteEditor(c: LauncherController, stackId: String, note: StackItem.Note, onDone: () -> Unit) {
    val p = LocalPalette.current
    var value by remember(note.id) {
        val cursor = note.cursor.coerceIn(0, note.text.length)
        mutableStateOf(TextFieldValue(note.text, TextRange(cursor)))
    }
    LaunchedEffect(value) {
        delay(350)
        c.app.stacks.replaceItem(stackId, note.copy(text = value.text, cursor = value.selection.start))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .matte(p, RoundedCornerShape(16.dp), edge = p.signal.copy(alpha = 0.6f))
            .padding(14.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            textStyle = Type.body.copy(color = p.ink),
            cursorBrush = SolidColor(p.signal),
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextAction("تم") {
                c.app.stacks.replaceItem(stackId, note.copy(text = value.text, cursor = value.selection.start))
                onDone()
            }
        }
    }
}

@Composable
private fun AddPanel(c: LauncherController, adding: Adding, onAdd: (StackItem) -> Unit, onCancel: () -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    var text by remember(adding) {
        val clip = if (adding == Adding.LINK) {
            context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
                ?.takeIf { it.startsWith("http") }.orEmpty()
        } else ""
        mutableStateOf(clip)
    }
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .matte(p, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = Type.body.copy(color = p.ink),
                cursorBrush = SolidColor(p.signal),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) Text(
                            when (adding) {
                                Adding.APP -> "اسم التطبيق"
                                Adding.LINK -> "https://"
                                Adding.CONTACT -> "اسم الشخص"
                            },
                            style = Type.body, color = p.faint,
                        )
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            if (adding == Adding.LINK && text.isNotBlank()) {
                TextAction("إضافة") {
                    val url = if (text.contains("://")) text.trim() else "https://${text.trim()}"
                    onAdd(StackItem.Link(newItemId(), url, Uri.parse(url).host ?: url))
                }
            }
            TextAction("إلغاء", color = p.muted, onClick = onCancel)
        }
        when (adding) {
            Adding.APP -> c.parser.matchApps(text).take(5).forEach { app ->
                TextAction(app.label, color = p.ink, style = Type.body) { onAdd(StackItem.App(newItemId(), app.key, app.label)) }
            }
            Adding.CONTACT -> c.app.contacts.search(text, 5).forEach { contact ->
                TextAction("${contact.name} · ${contact.phone}", color = p.ink, style = Type.body) {
                    onAdd(StackItem.Contact(newItemId(), contact.name, contact.phone))
                }
            }
            Adding.LINK -> Unit
        }
    }
}

private fun displayName(c: LauncherController, uri: Uri): String =
    c.app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment ?: "ملف"
