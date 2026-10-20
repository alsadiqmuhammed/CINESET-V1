package com.madar.launcher.ui

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madar.launcher.command.Suggestion
import com.madar.launcher.data.AppEntry
import com.madar.launcher.data.AppKind
import com.madar.launcher.data.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The command bar: type (or speak) an intention, get the result directly instead of hunting for an icon. */
@Composable
fun CommandPalette(c: LauncherController, initial: String, mode: Mode) {
    val p = LocalPalette.current
    var field by remember(initial) { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var contactsAllowed by remember { mutableStateOf(c.app.contacts.hasPermission) }
    val focus = remember { FocusRequester() }
    val apps by c.app.apps.apps.collectAsState()

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(field.text, contactsAllowed) {
        suggestions = if (field.text.isBlank()) emptyList() else withContext(Dispatchers.IO) { c.parser.parse(field.text) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(p.surface)
                .border(1.dp, p.accent.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = Type.title.copy(color = p.ink),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { suggestions.firstOrNull()?.let { c.execute(it.action) } }),
                decorationBox = { inner ->
                    Box {
                        if (field.text.isEmpty()) Text("شنو تريد تسوي؟", style = Type.title, color = p.faint)
                        inner()
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            Spacer(Modifier.width(12.dp))
            TextAction(if (field.text.isEmpty()) "صوت" else "مسح", color = p.muted) {
                if (field.text.isEmpty()) {
                    c.listen { field = TextFieldValue(it, TextRange(it.length)) }
                } else {
                    field = TextFieldValue("")
                }
            }
        }

        if (!contactsAllowed) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("اسمح بجهات الاتصال حتى تتصل وتراسل بالأوامر", style = Type.caption, color = p.muted, modifier = Modifier.weight(1f))
                TextAction("سماح") {
                    c.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)) {
                        c.app.contacts.invalidate()
                        contactsAllowed = c.app.contacts.hasPermission
                    }
                }
            }
        }

        if (field.text.isBlank()) {
            QuickActions { field = TextFieldValue(it, TextRange(it.length)) }
            AppIndex(c, apps, mode)
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(top = 12.dp)) {
                items(suggestions, key = { it.action.hashCode().toString() + it.title }) { s ->
                    SuggestionRow(s, first = s === suggestions.firstOrNull()) { c.execute(s.action) }
                }
            }
        }
    }
}

@Composable
private fun QuickActions(onPick: (String) -> Unit) {
    val starters = listOf("مؤقت ", "منبه ", "ملاحظة ", "اتصل ", "دز آخر صورة ل", "مشروع ", "ذكرني بعد ", "الملخص")
    LazyRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(starters) { s -> Pill(s.trim()) { onPick(s) } }
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, first: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val glyphPx = with(LocalDensity.current) { 28.dp.roundToPx() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (first) p.surface else p.background)
            .quietClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s.app != null) {
            AppGlyph(s.app, glyphPx, Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(s.title, style = Type.title, color = p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.subtitle, style = Type.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Tag(s.tag, if (first) p.accent else p.muted)
    }
}

/** Typography-first app index: names, grouped by what they are for, out-of-mode apps dimmed at the end. */
@Composable
private fun AppIndex(c: LauncherController, apps: List<AppEntry>, mode: Mode) {
    val p = LocalPalette.current
    val glyphPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val (inMode, outOfMode) = apps.partition { it.kind.visibleIn(mode) }
    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
        AppKind.entries.forEach { kind ->
            val group = inMode.filter { it.kind == kind }
            if (group.isEmpty()) return@forEach
            item(key = "h-$kind") { SectionTitle(kind.title) }
            items(group, key = { it.key }) { app -> AppRow(c, app, glyphPx, dim = false) }
        }
        if (outOfMode.isNotEmpty()) {
            item(key = "h-out") {
                SectionTitle("خارج طور ${mode.title} · تفتح عبر بوابة النية")
            }
            items(outOfMode, key = { "o-" + it.key }) { app -> AppRow(c, app, glyphPx, dim = true) }
        }
        item { Spacer(Modifier.padding(bottom = 40.dp)) }
    }
}

@Composable
private fun AppRow(c: LauncherController, app: AppEntry, glyphPx: Int, dim: Boolean) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .quietClick(onLongClick = { c.haptics.heavy(); c.open(Overlay.AppMenu(app)) }) { c.requestLaunch(app) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyph(app, glyphPx, Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(app.label, style = Type.title, color = if (dim) p.faint else p.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (app.kind.gated && !dim) {
            Spacer(Modifier.width(10.dp))
            Tag("بوابة")
        }
    }
}
