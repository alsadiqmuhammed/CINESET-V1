package com.madar.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Current time in millis, re-emitted at each [periodMs] boundary (aligned, so minutes flip on time). */
@Composable
fun rememberNow(periodMs: Long = 60_000): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            val t = System.currentTimeMillis()
            now.longValue = t
            delay(periodMs - t % periodMs + 5)
        }
    }
    return now
}

/** Click without the Material ripple; the design relies on haptics and type weight instead. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.quietClick(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier =
    combinedClickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick,
    )

@Composable
fun Tag(text: String, color: Color = LocalPalette.current.muted) {
    Text(
        text = text,
        style = Type.caption,
        color = color,
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Pill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Text(
        text = text,
        style = Type.label,
        color = if (selected) p.background else p.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) p.accent else p.surface)
            .border(1.dp, if (selected) p.accent else p.faint, RoundedCornerShape(50))
            .quietClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun TextAction(text: String, color: Color = LocalPalette.current.accent, style: TextStyle = Type.label, onClick: () -> Unit) {
    Text(text, style = style, color = color, modifier = Modifier.quietClick(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp))
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.padding(top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(14.dp).background(p.accent))
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.label, color = p.muted)
    }
}

/** Full-screen sheet used by every overlay: true black, title row, and a close affordance. */
@Composable
fun Sheet(
    title: String,
    onClose: () -> Unit,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.background)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = Type.display, color = p.ink, modifier = Modifier.weight(1f))
            trailing()
            TextAction("إغلاق", color = p.muted, onClick = onClose)
        }
        content()
    }
}

@Composable
fun Toast(message: String?, onShown: () -> Unit) {
    if (message == null) return
    val p = LocalPalette.current
    LaunchedEffect(message) {
        delay(2600)
        onShown()
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            message,
            style = Type.label,
            color = p.ink,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(p.surface)
                .border(1.dp, p.faint, RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
