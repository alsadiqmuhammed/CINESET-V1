package com.madar.launcher.ui

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings as SystemSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.madar.launcher.data.Mode
import com.madar.launcher.data.format
import com.madar.launcher.service.DigestListenerService

@Composable
fun SettingsSheet(c: LauncherController) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val s by c.app.settings.state.collectAsState()
    // Bumped on every resume so permission rows refresh after a trip to system settings.
    val refresh = remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh.intValue++ } }

    val close = {
        c.app.settings.edit { it.copy(onboarded = true) }
        c.close()
    }

    key(refresh.intValue) { Sheet(if (s.onboarded) "إعدادات مدار" else "أهلاً بك في مدار", onClose = close) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!s.onboarded) {
                Text(
                    "شاشة رئيسية تشتغل على نيتك مو على الأيقونات. اسحب للأعلى للأوامر، لف القوس بإبهامك، واضغط مطولاً على الشاشة لهذي الإعدادات.",
                    style = Type.body, color = p.muted,
                )
            }

            SectionTitle("الأساسيات")
            Check("الشاشة الرئيسية الافتراضية", c.isDefaultHome) { c.requestHomeRole() }
            Check("الإشعارات (للتذكيرات)", granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
                c.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) { refresh.intValue++ }
            }
            Check("جهات الاتصال والاتصال المباشر", granted(context, Manifest.permission.READ_CONTACTS) && granted(context, Manifest.permission.CALL_PHONE)) {
                c.requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)) {
                    c.app.contacts.invalidate(); refresh.intValue++
                }
            }
            Check("الصور والفيديو (لأمر \"دز آخر صورة\")", granted(context, Manifest.permission.READ_MEDIA_IMAGES)) {
                c.requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)) { refresh.intValue++ }
            }
            Check("كل الملفات (لأمر \"دز آخر PDF\")", hasAllFilesAccess()) {
                c.start(Intent(SystemSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName)))
            }
            Check("المنبهات الدقيقة (لبوابة النية والبومودورو)", context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
                c.start(Intent(SystemSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
            }

            SectionTitle("كبسولة الإشعارات")
            Check("الوصول للإشعارات", notificationAccess(context)) {
                c.start(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            if (!notificationAccess(context)) {
                Hint("إذا الخيار رمادي: معلومات التطبيق ← ⋮ ← \"السماح بالإعدادات المقيدة\"، بعدين ارجع فعّله. هذا طبيعي للتطبيقات المثبتة من ملف APK.") {
                    c.start(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Pill(if (s.digestEnabled) "مفعّلة" else "متوقفة", selected = s.digestEnabled) {
                    c.app.settings.edit { it.copy(digestEnabled = !it.digestEnabled) }
                }
                s.digestTimes.forEachIndexed { i, t ->
                    Pill("الموعد ${i + 1}: ${t.format()}") {
                        pickTime(context, t) { m -> c.app.settings.edit { st -> st.copy(digestTimes = st.digestTimes.toMutableList().also { it[i] = m }) } }
                    }
                }
            }
            Text("رسائل الناس (واتساب، تليكرام، SMS) والمكالمات والمنبهات توصلك فوراً. الباقي ينتظر الملخص.", style = Type.caption, color = p.muted, modifier = Modifier.padding(top = 8.dp))

            SectionTitle("جدول الأطوار")
            TimeRow("${Mode.FOCUS.title} من", s.focusStart) { m -> c.app.settings.edit { it.copy(focusStart = m) } }
            TimeRow("${Mode.FOCUS.title} إلى", s.focusEnd) { m -> c.app.settings.edit { it.copy(focusEnd = m) } }
            TimeRow("${Mode.SANCTUARY.title} من", s.sanctuaryStart) { m -> c.app.settings.edit { it.copy(sanctuaryStart = m) } }
            TimeRow("${Mode.SANCTUARY.title} إلى", s.sanctuaryEnd) { m -> c.app.settings.edit { it.copy(sanctuaryEnd = m) } }
            Text("باقي الوقت ${Mode.FLOW.title}. التبديل اليدوي من أعلى الشاشة يبقى لحد الموعد الجاي.", style = Type.caption, color = p.muted)

            SectionTitle("القوس والتركيز")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("اليد اليمنى", selected = s.rightHanded) { c.app.settings.edit { it.copy(rightHanded = true) } }
                Pill("اليد اليسرى", selected = !s.rightHanded) { c.app.settings.edit { it.copy(rightHanded = false) } }
            }

            SectionTitle("realme UI")
            Hint("حتى realme ما يسكّر مدار والإشعارات بالخلفية: البطارية ← \"بدون قيود\"، وفعّل التشغيل التلقائي.") {
                requestBatteryExemption(c, context)
            }

            SectionTitle("أخرى")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("أرشيف المشاريع") { c.open(Overlay.Archive) }
                Pill("الملخص") { c.open(Overlay.Digest) }
            }

            Spacer(Modifier.height(24.dp))
            if (!s.onboarded) Pill("يلا نبدأ", selected = true, onClick = close)
            Text("مدار 0.1 · بدون إنترنت، كل شي يبقى بجهازك", style = Type.caption, color = p.faint, modifier = Modifier.padding(vertical = 24.dp))
        }
    } }
}

@Composable
private fun Check(label: String, ok: Boolean, onFix: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().quietClick(onClick = onFix).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(if (ok) p.accent else p.danger)
        Spacer(Modifier.width(12.dp))
        Text(label, style = Type.body, color = p.ink, modifier = Modifier.weight(1f))
        Text(if (ok) "جاهز" else "تفعيل", style = Type.label, color = if (ok) p.muted else p.accent)
    }
}

@Composable
private fun TimeRow(label: String, value: Int, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().quietClick { pickTime(context, value, onPick) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.body, color = p.ink, modifier = Modifier.weight(1f))
        Text(value.format(), style = Type.title, color = p.accent)
    }
}

@Composable
private fun Hint(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.quietClick(onClick = onClick).padding(vertical = 6.dp)) {
        Text(text, style = Type.caption, color = p.muted)
        Text("افتح الإعدادات", style = Type.label, color = p.accent, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun pickTime(context: Context, value: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(
        context, android.R.style.Theme_DeviceDefault_Dialog_Alert,
        { _, h, m -> onPick(h * 60 + m) }, value / 60, value % 60, true,
    ).show()
}

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun notificationAccess(context: Context): Boolean {
    val flat = SystemSettings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val me = ComponentName(context, DigestListenerService::class.java)
    return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
}

@android.annotation.SuppressLint("BatteryLife")
private fun requestBatteryExemption(c: LauncherController, context: Context) {
    val pm = context.getSystemService(PowerManager::class.java)
    if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
        c.start(Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)))
    } else {
        c.start(Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
    }
}
