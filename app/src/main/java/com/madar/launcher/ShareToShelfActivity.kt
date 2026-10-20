package com.madar.launcher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.madar.launcher.data.Mode
import com.madar.launcher.ui.Haptics
import com.madar.launcher.ui.LocalPalette
import com.madar.launcher.ui.MadarTheme
import com.madar.launcher.ui.OrbitMark
import com.madar.launcher.ui.Type
import com.madar.launcher.ui.matte
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share target ("مدار" in any share sheet). The shared thing is pulled magnetically into the orbit,
 * locks with a haptic thud, and lands on the transient shelf.
 */
class ShareToShelfActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MadarApp
        val haptics = Haptics(this)
        val preview = previewText(intent)
        blueprintIn(intent)?.let { bp ->
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_BLUEPRINT, com.madar.launcher.data.BlueprintCodec.encode(bp)),
            )
            finish()
            return
        }

        setContent {
            MadarTheme(Mode.FLOW) {
                val p = LocalPalette.current
                val pull = remember { Animatable(0f) }
                val dim = remember { Animatable(0f) }
                var landed by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    launch { dim.animateTo(1f, tween(180)) }
                    val stored = launch(Dispatchers.IO) { store(app, intent) }
                    delay(220)
                    // Starts slow, then the field grabs it: a low-stiffness spring that overshoots into the pivot.
                    pull.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
                    stored.join()
                    haptics.heavy()
                    landed = true
                    delay(750)
                    withContext(Dispatchers.Main) { finish() }
                }

                Box(
                    Modifier.fillMaxSize().background(p.background.copy(alpha = 0.82f * dim.value)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        OrbitMark(if (landed) p.signal else p.ink, 72.dp, spin = pull.value * 120f, pivot = p.signal)
                        Spacer(Modifier.height(28.dp))
                        Text(
                            if (landed) "على الرف · يتبخر بعد ساعتين" else "مدار",
                            style = Type.label,
                            color = if (landed) p.ink else p.muted,
                        )
                    }
                    // The item, drawn above the orbit's centre and pulled in.
                    Text(
                        preview,
                        style = Type.body,
                        color = p.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .graphicsLayer {
                                val t = pull.value
                                translationY = (1f - t) * 420.dp.toPx() - 24.dp.toPx() * t
                                scaleX = 1f - 0.92f * t
                                scaleY = 1f - 0.92f * t
                                alpha = (1.15f - t).coerceIn(0f, 1f)
                            }
                            .widthIn(max = 280.dp)
                            .matte(p, RoundedCornerShape(18.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }

    /** Shared blueprint text or a photo of its QR opens the import sheet instead of landing on the shelf. */
    private fun blueprintIn(intent: Intent): com.madar.launcher.data.Blueprint? {
        if (intent.action != Intent.ACTION_SEND) return null
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { com.madar.launcher.data.BlueprintCodec.decode(it)?.let { bp -> return bp } }
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return null
        if (intent.type?.startsWith("image/") != true) return null
        return runCatching {
            contentResolver.openInputStream(stream)?.use { input ->
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                android.graphics.BitmapFactory.decodeStream(input, null, opts)
            }?.let { com.madar.launcher.data.BlueprintCodec.fromImage(it) }
        }.getOrNull()
    }

    private fun store(app: MadarApp, intent: Intent) {
        runCatching {
            when (intent.action) {
                Intent.ACTION_SEND -> {
                    val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (stream != null) app.shelf.addStream(stream, intent.type)
                    else app.shelf.addText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
                }
                Intent.ACTION_SEND_MULTIPLE ->
                    IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                        ?.forEach { app.shelf.addStream(it, null) }
            }
        }
    }

    private fun previewText(intent: Intent): String {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val count = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.size ?: 1
        val type = intent.type.orEmpty()
        return when {
            count > 1 -> "$count عناصر"
            type.startsWith("image/") -> "صورة"
            type.startsWith("video/") -> "فيديو"
            type == "application/pdf" -> "ملف PDF"
            else -> "ملف"
        }
    }
}
