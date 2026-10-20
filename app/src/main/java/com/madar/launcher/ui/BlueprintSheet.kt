package com.madar.launcher.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.madar.launcher.data.Blueprint
import com.madar.launcher.data.BlueprintCodec
import com.madar.launcher.data.StackItem
import com.madar.launcher.data.apply
import com.madar.launcher.data.format
import com.madar.launcher.data.newItemId
import java.io.File

/** Export shows the template as a QR in chalk on black; import shows what it contains before applying. */
@Composable
fun BlueprintSheet(c: LauncherController, blueprint: Blueprint, importing: Boolean) {
    val p = LocalPalette.current
    val code = remember(blueprint) { BlueprintCodec.encode(blueprint) }
    val tooBig = code.length > BlueprintCodec.MAX_LENGTH

    Sheet(if (importing) "قالب مستلم" else "مشاركة كقالب", onClose = c::close) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("BLUEPRINT · ${if (blueprint is Blueprint.Modes) "MODES" else "STACK"} · ${code.length} B", style = Type.tech, color = p.muted)
            Summary(blueprint)

            if (!importing) {
                if (tooBig) {
                    Text("القالب كبير على QR. شيل بعض الملاحظات الطويلة أو شاركه كنص.", style = Type.body, color = p.signal, modifier = Modifier.padding(top = 16.dp))
                } else {
                    QrCode(code, Modifier.padding(top = 20.dp).fillMaxWidth().aspectRatio(1f).matte(p, RoundedCornerShape(20.dp)).padding(18.dp))
                }
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("مشاركة", selected = true) { share(c, code, tooBig) }
                }
                Text("الطرف الثاني يشارك صورة الـ QR أو النص إلى \"مدار\" ويطلعله القالب للاستيراد. جهات الاتصال والملفات ما تنتقل.", style = Type.caption, color = p.muted, modifier = Modifier.padding(top = 12.dp))
            } else {
                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("استيراد", selected = true) { apply(c, blueprint) }
                    Pill("تجاهل") { c.close() }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Summary(bp: Blueprint) {
    val p = LocalPalette.current
    Column(Modifier.padding(top = 14.dp)) {
        when (bp) {
            is Blueprint.Modes -> {
                Text("إعدادات الأطوار", style = Type.title, color = p.ink)
                Line("التركيز", "${bp.focusStart.format()} → ${bp.focusEnd.format()}")
                Line("السكون", "${bp.sanctuaryStart.format()} → ${bp.sanctuaryEnd.format()}")
                Line("الملخص", bp.digestTimes.joinToString("  ") { it.format() })
                Line("البومودورو", "${bp.pomodoroMinutes} MIN")
                Line("الاحتكاك", if (bp.frictionEnabled) "ON" else "OFF")
                if (bp.kinds.isNotEmpty()) Line("تصنيفات", "${bp.kinds.size}")
            }
            is Blueprint.StackPlan -> {
                Text(bp.title, style = Type.title, color = p.ink)
                bp.items.forEach {
                    when (it) {
                        is Blueprint.PlanItem.App -> Line("تطبيق", it.label)
                        is Blueprint.PlanItem.Link -> Line("رابط", it.title)
                        is Blueprint.PlanItem.Note -> Line("ملاحظة", it.text.lineSequence().first().take(40))
                    }
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = Type.body, color = p.muted, modifier = Modifier.weight(1f))
        Text(value, style = Type.data, color = p.ink)
    }
}

/**
 * QR drawn module by module onto a chalk plate, like a printed label set into the matte card.
 * Dark-on-light on purpose: plenty of camera apps can't read inverted codes.
 */
@Composable
fun QrCode(text: String, modifier: Modifier) {
    val p = LocalPalette.current
    val matrix = remember(text) { BlueprintCodec.qr(text) }
    Canvas(modifier) {
        val quiet = 3 // modules of white margin scanners need
        val cell = size.minDimension / (matrix.width + quiet * 2)
        drawRoundRect(p.ink, cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 2))
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(p.background, Offset((x + quiet) * cell, (y + quiet) * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

private fun share(c: LauncherController, code: String, textOnly: Boolean) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code)
    if (!textOnly) {
        val m = BlueprintCodec.qr(code, 900)
        val bmp = Bitmap.createBitmap(m.width + 80, m.height + 80, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFE5E5E7.toInt()) }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) bmp.setPixel(x + 40, y + 40, 0xFF000000.toInt())
        val file = File(c.app.cacheDir, "blueprint.png").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val uri = FileProvider.getUriForFile(c.app, "com.madar.launcher.files", file)
        send.setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    c.start(Intent.createChooser(send, "قالب مدار"))
}

private fun apply(c: LauncherController, bp: Blueprint) {
    when (bp) {
        is Blueprint.Modes -> {
            c.app.settings.edit { it.apply(bp) }
            c.app.apps.reload()
            c.toast("انطبقت إعدادات الأطوار")
        }
        is Blueprint.StackPlan -> {
            val stack = c.app.stacks.create(bp.title)
            var missing = 0
            bp.items.forEach { item ->
                val added: StackItem? = when (item) {
                    is Blueprint.PlanItem.App -> c.app.apps.findPackage(item.packageName)
                        ?.let { StackItem.App(newItemId(), it.key, it.label) }
                    is Blueprint.PlanItem.Link -> StackItem.Link(newItemId(), item.url, item.title)
                    is Blueprint.PlanItem.Note -> StackItem.Note(newItemId(), item.text)
                }
                if (added == null) missing++ else c.app.stacks.addItem(stack.id, added)
            }
            c.toast(if (missing == 0) "انضاف المشروع" else "انضاف المشروع · $missing تطبيقات مو مثبتة")
            c.open(Overlay.StackView(stack.id))
            return
        }
    }
    c.haptics.completion()
    c.close()
}
