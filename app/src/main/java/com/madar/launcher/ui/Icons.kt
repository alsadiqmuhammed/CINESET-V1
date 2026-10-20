package com.madar.launcher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Process
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.madar.launcher.data.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App icons rendered as monochrome glyphs tinted with the mode accent. Prefers the app's own
 * themed (monochrome) layer; otherwise desaturates the full icon so colour never competes with text.
 */
object IconCache {
    private val cache = LruCache<String, Pair<ImageBitmap, Boolean>>(160)

    suspend fun load(context: Context, app: AppEntry, sizePx: Int): Pair<ImageBitmap, Boolean>? {
        cache.get(app.key)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val la = context.getSystemService(LauncherApps::class.java)
                val info = la.getActivityList(app.packageName, Process.myUserHandle())
                    .firstOrNull { it.componentName == app.component } ?: return@runCatching null
                val drawable = info.getIcon(0)
                val mono = (drawable as? AdaptiveIconDrawable)?.monochrome
                val bitmap = (mono ?: drawable).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
                (bitmap to (mono != null)).also { cache.put(app.key, it) }
            }.getOrNull()
        }
    }
}

private val GRAYSCALE = ColorMatrix().apply { setToSaturation(0f) }

@Composable
fun AppGlyph(app: AppEntry, sizePx: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    var icon by remember(app.key) { mutableStateOf<Pair<ImageBitmap, Boolean>?>(null) }
    LaunchedEffect(app.key) { icon = IconCache.load(context, app, sizePx) }
    val (bitmap, isMono) = icon ?: return
    Image(
        bitmap = bitmap,
        contentDescription = app.label,
        modifier = modifier,
        colorFilter = if (isMono) ColorFilter.tint(palette.ink) else ColorFilter.colorMatrix(GRAYSCALE),
    )
}
