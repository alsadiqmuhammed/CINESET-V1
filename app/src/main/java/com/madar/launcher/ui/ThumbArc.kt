package com.madar.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.madar.launcher.data.AppEntry
import com.madar.launcher.data.Mode
import com.madar.launcher.data.Settings
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STEP_DEG = 18f
private const val VISIBLE_FROM = -2f
private const val VISIBLE_TO = 92f

/**
 * Apps laid on a quarter circle around the bottom corner under the thumb. Dragging along the arc
 * rotates it like a dial, with a detent tick for every app that passes.
 */
@Composable
fun ThumbArc(c: LauncherController, settings: Settings, mode: Mode, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val density = LocalDensity.current
    val all by c.app.apps.apps.collectAsState()
    val items = remember(all, settings.pinned, mode) { arcApps(all, settings.pinned, mode) }

    val radius = with(density) { 250.dp.toPx() }
    val inset = with(density) { 46.dp.toPx() }
    val itemSize = 76.dp
    val glyphPx = with(density) { 40.dp.roundToPx() }
    val maxRotation = ((items.size - 1) * STEP_DEG - (VISIBLE_TO - VISIBLE_FROM) + STEP_DEG).coerceAtLeast(0f)

    val rotation = remember(mode) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val right = settings.rightHanded

    Box(
        modifier
            .fillMaxWidth()
            .height(310.dp)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxSize().pointerInput(items.size, right) {
            val pivot = { Offset(if (right) size.width - inset else inset, size.height - inset) }
            fun angleOf(pos: Offset): Float {
                val pv = pivot()
                val dx = if (right) pv.x - pos.x else pos.x - pv.x
                return Math.toDegrees(atan2((pv.y - pos.y).toDouble(), dx.toDouble())).toFloat()
            }
            var last = 0f
            detectDragGestures(
                onDragStart = { last = angleOf(it) },
                onDrag = { change, _ ->
                    change.consume()
                    val a = angleOf(change.position)
                    val before = floor(rotation.value / STEP_DEG)
                    val next = (rotation.value + (a - last)).coerceIn(-STEP_DEG, maxRotation + STEP_DEG)
                    last = a
                    scope.launch { rotation.snapTo(next) }
                    if (floor(next / STEP_DEG) != before) c.haptics.tick()
                },
                onDragEnd = {
                    val target = ((rotation.value / STEP_DEG).roundToInt() * STEP_DEG).coerceIn(0f, maxRotation)
                    scope.launch { rotation.animateTo(target, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
                },
            )
        }) {
            // Guide rails.
            Canvas(Modifier.fillMaxSize()) {
                val pv = Offset(if (right) size.width - inset else inset, size.height - inset)
                listOf(radius, radius * 0.42f).forEach { r ->
                    drawArc(
                        color = p.faint,
                        startAngle = if (right) 180f else 270f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(pv.x - r, pv.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }

            items.forEachIndexed { i, app ->
                val angle = i * STEP_DEG - rotation.value
                if (angle < VISIBLE_FROM - STEP_DEG || angle > VISIBLE_TO + STEP_DEG) return@forEachIndexed
                val visibility = when {
                    angle < VISIBLE_FROM -> 1f - (VISIBLE_FROM - angle) / STEP_DEG
                    angle > VISIBLE_TO -> 1f - (angle - VISIBLE_TO) / STEP_DEG
                    else -> 1f
                }.coerceIn(0f, 1f)
                val rad = Math.toRadians(angle.toDouble())
                ArcItem(
                    app = app,
                    glyphPx = glyphPx,
                    pinned = app.key in settings.pinned,
                    modifier = Modifier
                        .align(if (right) AbsoluteAlignment.BottomRight else AbsoluteAlignment.BottomLeft)
                        .absoluteOffset {
                            val half = itemSize.toPx() / 2
                            val x = (radius * cos(rad)).toFloat()
                            val y = (radius * sin(rad)).toFloat()
                            // BottomEnd/Start anchor; shift so the item's centre sits on the arc.
                            IntOffset(
                                ((if (right) -(x + inset - half) else (x + inset - half))).roundToInt(),
                                (-(y + inset - half)).roundToInt(),
                            )
                        }
                        .graphicsLayer { alpha = visibility },
                    onClick = { bounds -> c.requestLaunch(app, bounds) },
                    onLongClick = { c.haptics.heavy(); c.open(Overlay.AppMenu(app)) },
                )
            }

            // The pivot: tap opens the command bar, right under the thumb.
            Box(
                Modifier
                    .align(if (right) AbsoluteAlignment.BottomRight else AbsoluteAlignment.BottomLeft)
                    .absoluteOffset { IntOffset(if (right) (-(inset - 30.dp.toPx())).roundToInt() else (inset - 30.dp.toPx()).roundToInt(), (-(inset - 30.dp.toPx())).roundToInt()) }
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(p.surface)
                    .border(1.dp, p.accent.copy(alpha = 0.6f), CircleShape)
                    .quietClick(onLongClick = { c.open(Overlay.Settings) }) { c.haptics.tick(); c.open(Overlay.Palette()) },
                contentAlignment = Alignment.Center,
            ) {
                Text("مدار", style = Type.caption, color = p.accent)
            }
        }
    }
}

@Composable
private fun ArcItem(
    app: AppEntry,
    glyphPx: Int,
    pinned: Boolean,
    modifier: Modifier,
    onClick: (android.graphics.Rect) -> Unit,
    onLongClick: () -> Unit,
) {
    val p = LocalPalette.current
    val bounds = remember { android.graphics.Rect() }
    Column(
        modifier
            .size(76.dp)
            .onGloballyPositioned {
                val pos = it.positionInRoot()
                bounds.set(pos.x.toInt(), pos.y.toInt(), (pos.x + it.size.width).toInt(), (pos.y + it.size.height).toInt())
            }
            .quietClick(onLongClick = onLongClick) { onClick(bounds) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppGlyph(app, glyphPx, Modifier.size(34.dp))
        Text(
            app.label,
            style = Type.caption,
            color = if (pinned) p.ink else p.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Pinned apps first (in pin order), then the rest of what this mode allows, alphabetically. */
private fun arcApps(all: List<AppEntry>, pinned: List<String>, mode: Mode): List<AppEntry> {
    val visible = all.filter { it.kind.visibleIn(mode) && !it.kind.gated }
    val byKey = visible.associateBy { it.key }
    val pins = pinned.mapNotNull { byKey[it] }
    return pins + visible.filterNot { it.key in pinned }
}
