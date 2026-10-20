package com.madar.launcher

import androidx.compose.ui.geometry.Offset
import com.madar.launcher.ui.isOrbit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrbitGestureTest {

    private fun arc(degrees: Double, radius: Float = 200f, wobble: Float = 0f) = (0..60).map { i ->
        val a = Math.toRadians(degrees * i / 60)
        val r = radius + if (i % 2 == 0) wobble else -wobble
        Offset(500f + r * cos(a).toFloat(), 900f + r * sin(a).toFloat())
    }

    @Test fun fullCircleOpens() = assertTrue(isOrbit(arc(360.0), minRadius = 120f))

    @Test fun wobblyHandDrawnCircleOpens() = assertTrue(isOrbit(arc(345.0, wobble = 40f), minRadius = 120f))

    @Test fun counterClockwiseOpens() = assertTrue(isOrbit(arc(-360.0), minRadius = 120f))

    @Test fun halfCircleDoesNot() = assertFalse(isOrbit(arc(180.0), minRadius = 120f))

    @Test fun tinyCircleDoesNot() = assertFalse(isOrbit(arc(360.0, radius = 40f), minRadius = 120f))

    @Test fun straightSwipeDoesNot() =
        assertFalse(isOrbit((0..60).map { Offset(500f, 1500f - it * 20f) }, minRadius = 120f))
}
