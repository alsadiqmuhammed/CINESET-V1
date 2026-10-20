package com.madar.launcher.ui

import com.madar.launcher.fmt
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.madar.launcher.data.ShelfItem
import com.madar.launcher.data.ShelfKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val CARD = 104.dp

/** The transient shelf on the home screen: shared things, each with a visible two-hour fuse. */
@Composable
fun ShelfRow(c: LauncherController, items: List<ShelfItem>) {
    val arrival by c.app.shelf.arrival.collectAsState()
    if (items.isEmpty()) return
    val now by rememberNow(1_000)

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items.sortedByDescending { it.addedAt }, key = { it.id }) { item ->
            ShelfCard(
                c = c,
                item = item,
                now = now,
                arriving = item.id == arrival,
                onArrived = { c.app.shelf.consumeArrival() },
            )
        }
    }
}

@Composable
private fun ShelfCard(c: LauncherController, item: ShelfItem, now: Long, arriving: Boolean, onArrived: () -> Unit) {
    val p = LocalPalette.current
    val remaining = (item.expiresAt - now).coerceAtLeast(0)
    val expired = remaining == 0L

    // Magnetic arrival: drops in from above, overshoots, and locks with a thud.
    val arrive = remember(item.id) { Animatable(if (arriving) 0f else 1f) }
    LaunchedEffect(arriving) {
        if (arriving) {
            arrive.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
            c.haptics.heavy()
            onArrived()
        }
    }

    // Ink dissolve once the fuse burns out while the shelf is on screen.
    val dissolve = remember(item.id) { Animatable(0f) }
    LaunchedEffect(expired) {
        if (expired) {
            dissolve.animateTo(1.1f, tween(1600))
            c.app.shelf.remove(item.id)
        }
    }
    val shader = remember { inkShader() }

    Column(
        Modifier
            .size(CARD, CARD + 8.dp)
            .graphicsLayer {
                val t = arrive.value
                translationY = (1f - t) * -64.dp.toPx()
                scaleX = 0.6f + 0.4f * t
                scaleY = 0.6f + 0.4f * t
                alpha = t.coerceIn(0f, 1f)
                if (dissolve.value > 0f && shader != null) {
                    shader.setFloatUniform("progress", dissolve.value)
                    renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
                } else if (dissolve.value > 0f) {
                    alpha = 1f - dissolve.value.coerceAtMost(1f)
                }
            }
            .matte(p, RoundedCornerShape(18.dp))
            .quietClick(onLongClick = { c.haptics.heavy(); shareOut(c, item) }) { open(c, item) },
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (item.kind) {
                ShelfKind.IMAGE -> Thumbnail(item.path, Modifier.fillMaxSize())
                ShelfKind.LINK -> Column(Modifier.padding(12.dp)) {
                    Text("LINK", style = Type.tech, color = p.muted)
                    Text(item.name.ifEmpty { item.text }, style = Type.label, color = p.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                ShelfKind.TEXT -> Text(
                    item.text, style = Type.caption, color = p.ink, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp),
                )
                ShelfKind.FILE -> Column(Modifier.padding(12.dp)) {
                    Text(item.name.substringAfterLast('.', "FILE").uppercase().take(5), style = Type.tech, color = p.muted)
                    Text(item.name.substringBeforeLast('.'), style = Type.label, color = p.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // The fuse: time left in mono and a burning orange line.
        val fraction = remaining.toFloat() / ShelfItem.LIFETIME_MS
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.stroke)) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(p.signal))
        }
        Text(
            "%d:%02d".fmt(remaining / 3_600_000, remaining / 60_000 % 60),
            style = Type.tech, color = p.signal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun Thumbnail(path: String?, modifier: Modifier) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) { path?.let(::decodeThumbnail) }
    }
    bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))) }
}

private fun decodeThumbnail(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sample = generateSequence(1) { it * 2 }.first { bounds.outWidth / it <= 360 }
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

private fun uriFor(c: LauncherController, item: ShelfItem): Uri? =
    item.path?.let { FileProvider.getUriForFile(c.app, "com.madar.launcher.files", File(it)) }

private fun open(c: LauncherController, item: ShelfItem) {
    when (item.kind) {
        ShelfKind.TEXT -> {
            c.app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("madar", item.text))
            c.haptics.completion()
            c.toast("انتسخ النص")
        }
        ShelfKind.LINK -> c.start(Intent(Intent.ACTION_VIEW, Uri.parse(if (item.text.startsWith("www")) "https://${item.text}" else item.text)))
        ShelfKind.IMAGE, ShelfKind.FILE -> uriFor(c, item)?.let {
            c.start(Intent(Intent.ACTION_VIEW).setDataAndType(it, item.mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
}

/** Long press hands the item on to any app — the shelf is a waypoint, not a destination. */
private fun shareOut(c: LauncherController, item: ShelfItem) {
    val send = Intent(Intent.ACTION_SEND)
    when (item.kind) {
        ShelfKind.TEXT, ShelfKind.LINK -> send.setType("text/plain").putExtra(Intent.EXTRA_TEXT, item.text)
        else -> send.setType(item.mime ?: "*/*").putExtra(Intent.EXTRA_STREAM, uriFor(c, item)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    c.start(Intent.createChooser(send, item.name.ifEmpty { "مشاركة" }))
}

private const val INK_DISSOLVE = """
uniform shader content;
uniform float progress;

float hash(float2 p) { return fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453); }
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1, 0)), u.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}
half4 main(float2 xy) {
    float n = noise(xy / 14.0) * 0.6 + noise(xy / 4.0) * 0.4;
    // Ink lifts and spreads as it evaporates.
    float2 drift = float2(sin(n * 6.28) * 6.0, -18.0) * progress * n;
    half4 c = content.eval(xy + drift);
    float keep = smoothstep(progress - 0.12, progress + 0.02, n);
    return c * keep;
}
"""

private fun inkShader(): RuntimeShader? = runCatching { RuntimeShader(INK_DISSOLVE) }.getOrNull()
