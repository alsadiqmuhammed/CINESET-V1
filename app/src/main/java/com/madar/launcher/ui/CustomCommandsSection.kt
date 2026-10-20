package com.madar.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.madar.launcher.data.CommandTarget
import com.madar.launcher.data.CustomCommand
import com.madar.launcher.data.Settings

/** Settings section for the open action protocol: phrase → Tasker / broadcast / URI. */
@Composable
fun CustomCommandsSection(c: LauncherController, s: Settings) {
    val p = LocalPalette.current
    var adding by remember { mutableStateOf(false) }
    var phrase by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(CommandTarget.BROADCAST) }

    SectionTitle("أوامرك", code = "OPEN · PROTOCOL")
    s.customCommands.forEach { cmd ->
        Row(
            Modifier.fillMaxWidth().quietClick(onLongClick = {
                c.haptics.heavy()
                c.app.settings.edit { it.copy(customCommands = it.customCommands - cmd) }
            }) {}.padding(vertical = 8.dp),
        ) {
            Text(cmd.phrase, style = Type.body, color = p.ink, modifier = Modifier.weight(1f))
            Text("${cmd.target.title} · ${cmd.value}", style = Type.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (!adding) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Pill("+ أمر جديد") { adding = true }
        }
        Text(
            "كل أمر يرسل بث com.madar.launcher.COMMAND لـ Tasker، أو يشغّل مهمة Tasker بالاسم، أو يفتح رابط مثل Home Assistant. ضغطة مطولة تحذف الأمر.",
            style = Type.caption, color = p.muted, modifier = Modifier.padding(top = 6.dp),
        )
        return
    }
    Column(Modifier.padding(top = 6.dp)) {
        Field(phrase, "العبارة، مثلاً: شغل الضوء") { phrase = it }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CommandTarget.entries.forEach { t -> Pill(t.title, selected = t == target) { target = t } }
        }
        Field(
            value,
            when (target) {
                CommandTarget.BROADCAST -> "معرّف الأمر، مثلاً: light_on"
                CommandTarget.TASKER_TASK -> "اسم المهمة بـ Tasker"
                CommandTarget.URI -> "homeassistant://navigate/... أو https://... (يدعم {text})"
            },
        ) { value = it }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("حفظ", selected = phrase.isNotBlank()) {
                if (phrase.isBlank() || (target != CommandTarget.BROADCAST && value.isBlank())) return@Pill
                c.app.settings.edit { it.copy(customCommands = it.customCommands + CustomCommand(phrase.trim(), target, value.trim())) }
                c.haptics.completion()
                phrase = ""; value = ""; adding = false
            }
            Pill("إلغاء") { adding = false }
        }
    }
}

@Composable
private fun Field(text: String, hint: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().matte(p, RoundedCornerShape(14.dp)).padding(14.dp)) {
        if (text.isEmpty()) Text(hint, style = Type.body, color = p.faint)
        BasicTextField(text, onChange, singleLine = true, textStyle = Type.body.copy(color = p.ink), cursorBrush = SolidColor(p.signal), modifier = Modifier.fillMaxWidth())
    }
}
