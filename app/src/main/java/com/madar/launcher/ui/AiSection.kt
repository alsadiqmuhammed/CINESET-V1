package com.madar.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.madar.launcher.ai.AiState
import com.madar.launcher.fmt

/** Settings for the on-device model: bring the file in once, then it lives (and stays) on the phone. */
@Composable
fun AiSection(c: LauncherController) {
    val p = LocalPalette.current
    val state by c.app.ai.state.collectAsState()

    SectionTitle("الذكاء المحلي", code = "ON-DEVICE")
    when (val s = state) {
        AiState.Missing -> {
            Text(
                "مدار يكدر يشغّل نموذج لغة على معالج موبايلك حتى يفهم الطلبات المركّبة، مثل \"رتبلي سفرة أربيل باچر\". النموذج ما يدخل الإنترنت، ومدار نفسه ما عنده إذن إنترنت.",
                style = Type.caption, color = p.muted,
            )
            Text(
                "١. نزّل من المتصفح ملف Gemma 3 1B بصيغة .task (مثلاً gemma3-1b-it-int4.task من صفحة litert-community على Hugging Face، تقريباً 550MB).\n٢. اضغط استيراد واختار الملف. بعدها تكدر تمسحه من التنزيلات.",
                style = Type.caption, color = p.ink, modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 10.dp)) { Pill("استيراد ملف النموذج", selected = true) { c.pickDocument { c.app.ai.importModel(it) } } }
        }
        is AiState.Importing -> {
            Text("IMPORTING · %d%%".fmt((s.progress * 100).toInt()), style = Type.tech, color = p.signal)
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp).background(p.stroke)) {
                Box(Modifier.fillMaxWidth(s.progress.coerceIn(0f, 1f)).height(2.dp).background(p.signal))
            }
        }
        is AiState.Failed -> {
            Text(s.reason, style = Type.body, color = p.signal)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("استيراد ملف آخر") { c.pickDocument { c.app.ai.importModel(it) } }
                Pill("حذف") { c.app.ai.deleteModel() }
            }
        }
        else -> {
            Text("MODEL · %d MB · %s".fmt(c.app.ai.modelSizeMb, if (s == AiState.Idle) "SLEEPING" else "AWAKE"), style = Type.tech, color = p.calm)
            Text("يصحى بس لما تطلب منه بشريط الأوامر، وينام بعد دقيقة ونص حتى ما يحجز الرام.", style = Type.caption, color = p.muted)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("استبدال النموذج") { c.pickDocument { c.app.ai.importModel(it) } }
                Pill("حذف") { c.app.ai.deleteModel() }
            }
        }
    }
}
