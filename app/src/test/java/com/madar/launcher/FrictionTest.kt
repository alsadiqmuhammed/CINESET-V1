package com.madar.launcher

import androidx.test.core.app.ApplicationProvider
import com.madar.launcher.data.FrictionRepository
import com.madar.launcher.data.Mode
import com.madar.launcher.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FrictionTest {

    private val context = ApplicationProvider.getApplicationContext<MadarApp>()
    private val repo = FrictionRepository(context)

    @Test fun sessionLivesUntilBudgetEnds() {
        val t0 = System.currentTimeMillis()
        repo.startSession("com.instagram.android", "رد على رسالة", 3, now = t0)
        assertNotNull(repo.activeSession("com.instagram.android", now = t0 + 2 * 60_000))
        assertNull(repo.activeSession("com.instagram.android", now = t0 + 3 * 60_000))
        // The record survives past the budget so overrun can be measured.
        assertNotNull(repo.session("com.instagram.android"))
    }

    @Test fun onlyOneGraceExtension() {
        repo.startSession("com.zhiliaoapp.musically", "استراحة", 10)
        assertNotNull(repo.extend("com.zhiliaoapp.musically"))
        assertNull(repo.extend("com.zhiliaoapp.musically"))
    }

    @Test fun dailyStatsAccumulate() {
        repo.recordPrompt()
        repo.recordPrompt()
        repo.recordRetreat("com.instagram.android")
        repo.startSession("com.instagram.android", "نشر محتوى", 5)
        repo.recordUsage("com.instagram.android", 7 * 60_000L, 2 * 60_000L)
        val today = repo.todayStats()
        assertEquals(2, today.prompts)
        assertEquals(1, today.retreats)
        assertEquals(1, today.sessions)
        assertEquals(5 * 60_000L, today.intendedMs)
        assertEquals(7 * 60_000L, today.actualMs)
        assertEquals(2 * 60_000L, today.overrunMs)
        assertEquals(7, repo.lastDays(7).size)
        assertEquals(LocalDate.now().toString(), repo.lastDays(7).last().date)
    }

    @Test fun placeOutranksScheduleButNotOverride() {
        val at = { h: Int -> LocalDate.now().atTime(h, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        val s = Settings(places = listOf(com.madar.launcher.data.Place("Office", Mode.FOCUS), com.madar.launcher.data.Place("Home", Mode.SANCTUARY, from = 20 * 60, to = 6 * 60)))
        assertEquals(Mode.FOCUS, s.modeAt(at(16), "Office")) // schedule says flow at 16:00
        assertEquals(Mode.FLOW, s.modeAt(at(16), "Home")) // home only counts in the evening
        assertEquals(Mode.SANCTUARY, s.modeAt(at(20), "Home"))
        assertEquals(Mode.FLOW, s.modeAt(at(16), null))
        val overridden = s.copy(modeOverride = Mode.FLOW, overrideUntil = at(16) / 60_000 + 60)
        assertEquals(Mode.FLOW, overridden.modeAt(at(16), "Office"))
    }

    @Test fun manualOverrideExpires() {
        val noon = LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val s = Settings(modeOverride = Mode.SANCTUARY, overrideUntil = noon / 60_000 + 30)
        assertEquals(Mode.SANCTUARY, s.modeAt(noon))
        assertEquals(Mode.FOCUS, s.modeAt(noon + 31 * 60_000))
    }
}
