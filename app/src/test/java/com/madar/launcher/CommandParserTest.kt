package com.madar.launcher

import com.madar.launcher.command.Action
import com.madar.launcher.command.Calc
import com.madar.launcher.command.Channel
import com.madar.launcher.command.CommandParser
import com.madar.launcher.command.normalize
import com.madar.launcher.data.ContactEntry
import com.madar.launcher.data.MediaKind
import com.madar.launcher.data.Mode
import com.madar.launcher.data.ModeSchedule
import com.madar.launcher.ui.LauncherController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    private val ahmed = ContactEntry("أحمد علي", "07701234567", normalize("أحمد علي"))
    private val parser = CommandParser(
        apps = { emptyList() },
        contacts = { q -> listOf(ahmed).filter { it.searchKey.contains(normalize(q)) && q.isNotBlank() } },
        stacks = { emptyList() },
    )

    private fun first(q: String) = parser.parse(q).first().action

    @Test fun normalizesArabicVariants() {
        assertEquals(normalize("تليكرام"), normalize("تليگرام"))
        assertEquals(normalize("تلغرام"), normalize("تلكرام"))
        assertEquals("احمد", normalize("أَحْمَد"))
        assertEquals("10", normalize("١٠"))
    }

    @Test fun calculator() {
        assertEquals("42", Calc.tryEvaluate("6*7"))
        assertEquals("2.5", Calc.tryEvaluate("10 ÷ 4"))
        assertEquals("14", Calc.tryEvaluate("2+3*4"))
        assertNull(Calc.tryEvaluate("2026"))
        assertNull(Calc.tryEvaluate("10 دقائق"))
    }

    @Test fun timers() {
        assertEquals(Action.SetTimer(600, ""), first("مؤقت 10 دقايق"))
        assertEquals(Action.SetTimer(900, ""), first("تايمر ربع ساعة"))
        assertEquals(Action.SetTimer(5400, ""), first("مؤقت ساعة ونص"))
        assertEquals(Action.SetTimer(30, ""), first("مؤقت ٣٠ ثانية"))
    }

    @Test fun reminders() {
        assertEquals(Action.SetTimer(1200, "اطفي الفرن"), first("ذكرني بعد 20 دقيقة اطفي الفرن"))
        assertEquals(Action.SetTimer(1800, "الدوا"), first("ذكرني بعد نص ساعة الدوا"))
    }

    @Test fun alarms() {
        assertEquals(Action.SetAlarm(6, 30, ""), first("منبه 6:30 الصبح"))
        assertEquals(Action.SetAlarm(19, 0, ""), first("نبهني الساعة 7 بالليل"))
        assertEquals(Action.SetAlarm(5, 30, ""), first("صحيني 5 ونص"))
    }

    @Test fun calls() {
        assertEquals(Action.Call(ahmed), first("اتصل باحمد"))
        assertEquals(Action.Call(ahmed), first("اتصل على أحمد"))
    }

    @Test fun messages() {
        assertEquals(Action.Message(ahmed, Channel.WHATSAPP, "شلونك"), first("واتساب احمد شلونك"))
        assertEquals(Action.Message(ahmed, Channel.TELEGRAM, ""), first("تليگرام احمد"))
    }

    @Test fun shareLatest() {
        assertEquals(Action.ShareLatest(MediaKind.PHOTO, Channel.WHATSAPP, ahmed), first("دز آخر صورة لأحمد بالواتساب"))
        assertEquals(Action.ShareLatest(MediaKind.PDF, Channel.TELEGRAM, ahmed), first("ارسل اخر pdf لاحمد عالتليكرام"))
        assertEquals(Action.ShareLatest(MediaKind.SCREENSHOT, null, null), first("دز آخر سكرين"))
    }

    @Test fun modesAndSearch() {
        assertEquals(Action.SwitchMode(Mode.FOCUS), first("تركيز"))
        assertEquals(Action.SwitchMode(Mode.SANCTUARY), first("هدوء"))
        assertEquals(Action.WebSearch("سعر الدولار"), first("ابحث سعر الدولار"))
        assertTrue(parser.parse("شي غريب").last().action is Action.WebSearch)
    }

    @Test fun schedule() {
        val s = ModeSchedule(focusStart = 9 * 60, focusEnd = 14 * 60, sanctuaryStart = 21 * 60 + 30, sanctuaryEnd = 6 * 60)
        assertEquals(Mode.FOCUS, s.modeAt(10 * 60))
        assertEquals(Mode.FLOW, s.modeAt(15 * 60))
        assertEquals(Mode.SANCTUARY, s.modeAt(23 * 60))
        assertEquals(Mode.SANCTUARY, s.modeAt(2 * 60))
        assertEquals(Mode.FLOW, s.modeAt(6 * 60))
        assertEquals(14 * 60, s.nextBoundaryAfter(10 * 60))
        assertEquals(6 * 60, s.nextBoundaryAfter(23 * 60))
    }

    @Test fun iraqiNumbers() {
        assertEquals("9647701234567", LauncherController.internationalDigits("07701234567"))
        assertEquals("9647701234567", LauncherController.internationalDigits("+964 770 123 4567"))
        assertEquals("9647701234567", LauncherController.internationalDigits("7701234567"))
    }
}
