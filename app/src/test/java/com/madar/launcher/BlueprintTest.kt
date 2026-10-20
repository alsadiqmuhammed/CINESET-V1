package com.madar.launcher

import android.graphics.Bitmap
import com.madar.launcher.data.AppKind
import com.madar.launcher.data.Blueprint
import com.madar.launcher.data.BlueprintCodec
import com.madar.launcher.data.Settings
import com.madar.launcher.data.toBlueprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BlueprintTest {

    private val modes = Settings(focusStart = 8 * 60, kindOverrides = mapOf("com.reddit.frontpage" to AppKind.ENTERTAINMENT)).toBlueprint()
    private val stack = Blueprint.StackPlan(
        "سفرة أربيل",
        listOf(
            Blueprint.PlanItem.App("com.google.android.apps.maps", "الخرائط"),
            Blueprint.PlanItem.Link("https://booking.com", "booking.com"),
            Blueprint.PlanItem.Note("جواز السفر\nشاحن"),
        ),
    )

    @Test fun textRoundTrip() {
        for (bp in listOf(modes, stack)) {
            val code = BlueprintCodec.encode(bp)
            assertTrue(code.startsWith(BlueprintCodec.PREFIX))
            assertTrue(code.length < BlueprintCodec.MAX_LENGTH)
            assertEquals(bp, BlueprintCodec.decode(code))
        }
    }

    @Test fun rejectsForeignText() {
        assertNull(BlueprintCodec.decode("hello"))
        assertNull(BlueprintCodec.decode("MADAR1:not-base64!!"))
    }

    @Test fun qrImageRoundTrip() {
        val code = BlueprintCodec.encode(stack)
        val m = BlueprintCodec.qr(code, 600)
        val bmp = Bitmap.createBitmap(m.width + 40, m.height + 40, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFFFFFF.toInt()) }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) bmp.setPixel(x + 20, y + 20, 0xFF000000.toInt())
        assertEquals(stack, BlueprintCodec.fromImage(bmp))
        // Inverted (light modules on dark) still reads.
        val inv = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) inv.setPixel(x, y, bmp.getPixel(x, y) xor 0x00FFFFFF)
        assertEquals(stack, BlueprintCodec.fromImage(inv))
    }
}
