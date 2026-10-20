package com.madar.launcher.ui

import android.Manifest
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.madar.launcher.data.Mode
import com.madar.launcher.data.Place
import com.madar.launcher.data.Settings
import com.madar.launcher.data.format

/** Wi-Fi places: link the network you're on now to a mode, optionally only between two times. */
@Composable
fun PlacesSection(c: LauncherController, s: Settings) {
    val p = LocalPalette.current
    val context = LocalContext.current
    val ssid by c.app.places.ssid.collectAsState()

    SectionTitle("الأماكن", code = "WI-FI")
    if (!c.app.places.hasPermission) {
        Text("مدار يعرف مكانك من اسم شبكة الواي فاي بس، بدون GPS. أندرويد يشترط إذن الموقع حتى يكشف اسم الشبكة.", style = Type.caption, color = p.muted)
        Row(Modifier.padding(top = 8.dp)) {
            Pill("سماح") {
                c.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { c.app.places.start() }
            }
        }
        return
    }

    Text(
        ssid?.let { "متصل الآن بـ  $it" } ?: "ما متصل بواي فاي (أو خدمة الموقع مطفية)",
        style = Type.body, color = if (ssid != null) p.ink else p.muted,
    )

    s.places.forEach { place ->
        Row(
            Modifier.fillMaxWidth().quietClick(onLongClick = {
                c.haptics.heavy()
                c.app.settings.edit { it.copy(places = it.places - place) }
            }) {}.padding(vertical = 8.dp),
        ) {
            Text(place.ssid, style = Type.data, color = if (place.ssid == ssid) p.accent else p.ink, modifier = Modifier.weight(1f))
            Text(
                "طور ${place.mode.title}" + if (place.from != place.to) " · ${place.from.format()}–${place.to.format()}" else "",
                style = Type.caption, color = p.muted,
            )
        }
    }

    val current = ssid
    if (current != null) {
        val existing = s.places.firstOrNull { it.ssid == current }
        Text(if (existing == null) "اربط هذي الشبكة بطور:" else "غيّر طور هذي الشبكة:", style = Type.caption, color = p.muted, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mode.entries.forEach { m ->
                Pill(m.title, selected = existing?.mode == m, color = paletteFor(m).accent) {
                    c.haptics.completion()
                    c.app.settings.edit { st ->
                        st.copy(places = st.places.filterNot { it.ssid == current } + (existing?.copy(mode = m) ?: Place(current, m)))
                    }
                }
            }
        }
        if (existing != null) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (existing.from == existing.to) "طول اليوم" else "${existing.from.format()}–${existing.to.format()}") {
                    // Pick start, then end; picking the same time twice means all day.
                    TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, h1, m1 ->
                        TimePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert, { _, h2, m2 ->
                            c.app.settings.edit { st ->
                                st.copy(places = st.places.map { if (it.ssid == current) it.copy(from = h1 * 60 + m1, to = h2 * 60 + m2) else it })
                            }
                        }, 6, 0, true).apply { setTitle("إلى") }.show()
                    }, 20, 0, true).apply { setTitle("من") }.show()
                }
            }
        }
    }
    Text("الترتيب: اختيارك اليدوي، بعدين المكان، بعدين الجدول. ضغطة مطولة تحذف المكان.", style = Type.caption, color = p.faint, modifier = Modifier.padding(top = 8.dp))
}
