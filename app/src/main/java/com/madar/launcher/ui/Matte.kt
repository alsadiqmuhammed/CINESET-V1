package com.madar.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** A small tile of static film grain, generated once and tiled; costs one texture, no per-frame work. */
private object Grain {
    val tile: ImageBitmap by lazy {
        val size = 128
        val rnd = Random(0x4D41)
        val pixels = IntArray(size * size) {
            // Mixed light and dark specks so the average tone of the card stays put.
            val light = rnd.nextBoolean()
            val a = rnd.nextInt(0, 256)
            (a shl 24) or if (light) 0xFFFFFF else 0x000000
        }
        android.graphics.Bitmap.createBitmap(pixels, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/**
 * Tactile matte surface: charcoal fill, ~2% film grain, and a 1px edge lit from above that fades
 * toward the bottom so the card reads as physically raised off the black glass.
 */
fun Modifier.matte(
    palette: Palette,
    shape: Shape,
    fill: Color = palette.surface,
    edge: Color = palette.highlight,
    grain: Float = 0.05f,
): Modifier = this
    .clip(shape)
    .background(fill, shape)
    .drawWithCache {
        val brush = ShaderBrush(ImageShader(Grain.tile, TileMode.Repeated, TileMode.Repeated))
        onDrawBehind { drawRect(brush, alpha = grain) }
    }
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(0f to edge, 0.45f to palette.stroke, 1f to palette.stroke.copy(alpha = 0.35f)),
        shape = shape,
    )

/** Outline-only control: same lit edge, no fill. */
fun Modifier.etched(palette: Palette, shape: Shape, color: Color = palette.stroke): Modifier =
    this.clip(shape).border(1.dp, Brush.verticalGradient(listOf(palette.highlight, color)), shape)

/**
 * The MADAR mark: an open orbit (a 300° arc) around a fixed pivot point.
 * [spin] rotates the opening; reserved for when on-device intelligence is working.
 */
@Composable
fun OrbitMark(color: Color, size: Dp, modifier: Modifier = Modifier, spin: Float = 0f, pivot: Color = color) {
    Canvas(modifier.size(size)) {
        val stroke = this.size.minDimension * 0.07f
        val r = this.size.minDimension / 2 - stroke
        val c = Offset(this.size.width / 2, this.size.height / 2)
        drawArc(
            color = color,
            startAngle = -60f + spin,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawCircle(pivot, radius = stroke * 1.25f, center = c)
    }
}
