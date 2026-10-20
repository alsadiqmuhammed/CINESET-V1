package com.madar.launcher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Process
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toBitmap
import com.madar.launcher.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adaptive glyph pack. No colourful vendor icon ever reaches the screen: apps that ship a themed
 * (monochrome) layer are drawn as chalk line glyphs; everything else becomes a typographic monogram.
 */
object IconCache {
    private class Entry(val glyph: ImageBitmap?)

    private val cache = LruCache<String, Entry>(200)

    suspend fun monochrome(context: Context, app: AppEntry, sizePx: Int): ImageBitmap? {
        cache.get(app.key)?.let { return it.glyph }
        val glyph = withContext(Dispatchers.Default) {
            runCatching {
                val la = context.getSystemService(LauncherApps::class.java)
                val info = la.getActivityList(app.packageName, Process.myUserHandle())
                    .firstOrNull { it.componentName == app.component } ?: return@runCatching null
                val mono = (info.getIcon(0) as? AdaptiveIconDrawable)?.monochrome ?: return@runCatching null
                // Themed layers carry a 25% safe-zone margin on each side; crop it so the glyph fills the slot.
                val full = mono.toBitmap(sizePx * 3 / 2, sizePx * 3 / 2, Bitmap.Config.ARGB_8888)
                val inset = sizePx / 4
                Bitmap.createBitmap(full, inset, inset, sizePx, sizePx).asImageBitmap()
            }.getOrNull()
        }
        cache.put(app.key, Entry(glyph))
        return glyph
    }
}

@Composable
fun AppGlyph(app: AppEntry, sizePx: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = LocalPalette.current
    var glyph by remember(app.key) { mutableStateOf<ImageBitmap?>(null) }
    var loaded by remember(app.key) { mutableStateOf(false) }
    LaunchedEffect(app.key) {
        glyph = IconCache.monochrome(context, app, sizePx)
        loaded = true
    }
    val g = glyph
    when {
        g != null -> Image(g, contentDescription = app.label, modifier = modifier, colorFilter = ColorFilter.tint(p.ink))
        loaded -> Monogram(app.label, modifier)
    }
}

/** First letter of the app's name, set in the interface typeface on a matte tile. */
@Composable
fun Monogram(label: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    // Skip the Arabic article "ال": an alif alone reads as a bare stroke, the next letter carries the word.
    val core = label.trim().let { if (it.startsWith("ال") && it.length > 2) it.drop(2) else it }
    val letter = core.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·"
    BoxWithConstraints(modifier.matte(p, RoundedCornerShape(28)), contentAlignment = Alignment.Center) {
        val fontSize = with(LocalDensity.current) { (maxWidth * 0.5f).toSp() }
        Text(
            letter,
            style = Type.title.copy(fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.Light),
            color = p.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.Center),
        )
    }
}
