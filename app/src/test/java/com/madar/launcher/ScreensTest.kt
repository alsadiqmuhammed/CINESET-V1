package com.madar.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.madar.launcher.command.Action
import com.madar.launcher.command.normalize
import com.madar.launcher.data.AppEntry
import com.madar.launcher.data.AppKind
import com.madar.launcher.data.Mode
import com.madar.launcher.data.StackItem
import com.madar.launcher.data.newItemId
import com.madar.launcher.ui.Overlay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders key screens to PNG for visual review (realme GT 8 Pro-like 1272x2800 @ 3.5x is approximated by xxhdpi). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "ar-w411dp-h900dp-xxhdpi")
class ScreensTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = rule.activity.application as MadarApp
    private val controller get() = rule.activity.javaClass.getDeclaredField("controller").apply { isAccessible = true }.get(rule.activity) as com.madar.launcher.ui.LauncherController

    private fun entry(pkg: String, label: String, kind: AppKind) =
        AppEntry(ComponentName(pkg, "$pkg.Main"), label, kind, normalize(label))

    private fun seed() {
        app.apps.replaceForTest(
            listOf(
                entry("com.google.android.dialer", "الهاتف", AppKind.COMMS),
                entry("com.whatsapp", "WhatsApp", AppKind.COMMS),
                entry("org.telegram.messenger", "Telegram", AppKind.COMMS),
                entry("com.oplus.camera", "الكاميرا", AppKind.TOOLS),
                entry("com.google.android.apps.maps", "الخرائط", AppKind.TOOLS),
                entry("com.notion.id", "Notion", AppKind.WORK),
                entry("com.google.android.calendar", "التقويم", AppKind.WORK),
                entry("com.spotify.music", "Spotify", AppKind.CALM),
                entry("com.instagram.android", "Instagram", AppKind.SOCIAL),
                entry("com.google.android.youtube", "YouTube", AppKind.ENTERTAINMENT),
                entry("com.android.settings", "الإعدادات", AppKind.TOOLS),
            ),
        )
        val s = app.stacks.create("سفرة أربيل")
        app.stacks.addItem(s.id, StackItem.Note(newItemId(), "حجز الفندق\nقبل الخميس"))
        app.stacks.addItem(s.id, StackItem.Link(newItemId(), "https://booking.com", "booking.com"))
        app.stacks.create("امتحان الإحصاء")
        app.settings.edit { it.copy(onboarded = true) }

        val img = File(rule.activity.cacheDir, "boarding.png")
        Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = android.graphics.Canvas(bmp)
            canvas.drawColor(0xFF2B3A44.toInt())
            canvas.drawCircle(150f, 130f, 70f, android.graphics.Paint().apply { color = 0xFFE9C46A.toInt() })
            img.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        app.shelf.addStream(android.net.Uri.fromFile(img), "image/png")
        app.shelf.addText("https://www.booking.com/hotel/iq/erbil-rotana")
        app.shelf.addText("رقم الحجز: 48213 — الدفع عند الوصول")
        app.shelf.consumeArrival()

        app.friction.recordPrompt(); app.friction.recordPrompt(); app.friction.recordPrompt()
        app.friction.recordRetreat("com.instagram.android"); app.friction.recordRetreat("com.instagram.android")
        app.friction.startSession("com.instagram.android", "رد على رسالة", 3)
        app.friction.recordUsage("com.instagram.android", 9 * 60_000L, 6 * 60_000L)
        app.friction.recordUsage("com.google.android.youtube", 14 * 60_000L, 0)
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        Thread.sleep(400) // let Dispatchers.IO work (palette parsing) land
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        val dir = File(System.getProperty("madar.screens") ?: "screens").apply { mkdirs() }
        var bitmap: Bitmap? = null
        rule.runOnUiThread {
            val view = rule.activity.window.decorView
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
        }
        File(dir, "$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun on(block: () -> Unit) {
        rule.runOnUiThread(block)
    }

    @Test fun renderScreens() {
        shot("0-onboarding")
        on { seed(); controller.close() }
        for (mode in Mode.entries) {
            on { controller.setMode(mode) }
            shot("1-home-${mode.name.lowercase()}")
        }
        on {
            val now = System.currentTimeMillis()
            app.agenda.fakeEvents = listOf(
                com.madar.launcher.data.AgendaEvent(1, "مراجعة خطة الإطلاق", now + 18 * 60_000, now + 78 * 60_000, "", "https://meet.google.com/abc-defg-hij"),
            )
            controller.setMode(Mode.FLOW)
        }
        shot("1-home-meeting")
        on { app.agenda.fakeEvents = null; controller.open(Overlay.Palette()) }
        shot("2-palette-empty")
        on { controller.open(Overlay.Palette("مؤقت ربع ساعة")) }
        shot("3-palette-timer")
        on { controller.execute(Action.LaunchApp(app.apps.apps.value.first { it.packageName == "com.instagram.android" })) }
        shot("4-intent-gate")
        on { controller.open(Overlay.StackView(app.stacks.stacks.value.first().id)) }
        shot("5-stack")
        on { controller.open(Overlay.Settings) }
        shot("6-settings")
        on { controller.open(Overlay.Report) }
        shot("7-report")
        on {
            app.apps.setHidden(app.apps.apps.value.first { it.packageName == "com.instagram.android" }, true)
            app.digest.add(com.madar.launcher.data.DigestItem("k1", "com.instagram.android", "Instagram", "سارة", "شفت الصورة؟", System.currentTimeMillis(), vault = true))
            controller.open(Overlay.Vault)
        }
        shot("8-vault")
    }
}
