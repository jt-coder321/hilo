package com.hilotrainer.ui

import androidx.test.core.app.ApplicationProvider
import com.hilotrainer.engine.CoverPlay
import com.hilotrainer.engine.ErrorMap
import com.hilotrainer.engine.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V6UiTest {
    private fun prefs(f: (AppSettings) -> AppSettings = { it }): Prefs {
        val p = Prefs(ApplicationProvider.getApplicationContext())
        p.saveSettings(f(AppSettings()))
        return p
    }

    private fun playRound(v: PlayViewModel) {
        v.deal()
        if (v.state.phase == Phase.INSURANCE) v.insurance(false)
        while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        if (v.quiz != null) { v.submitRunning(); v.submitTrue() }
    }

    @Test fun backupRoundTrip() {
        val p = prefs { it.copy(decks = 8, leftHanded = true) }
        p.updateStats { it.copy(handsPlayed = 42, bestDailyStreak = 9) }
        p.career = CareerProgress(2, 10, 9, 3, 3)
        val json = p.exportJson()
        p.resetStats(); p.saveSettings(AppSettings())
        assertEquals(0, p.loadStats().handsPlayed)
        assertTrue(p.importJson(json))
        assertEquals(42, p.loadStats().handsPlayed)
        assertEquals(8, p.loadSettings().decks)
        assertTrue(p.loadSettings().leftHanded)
        assertEquals(2, p.career.level)
        assertFalse(p.importJson("{\"app\":\"other\"}"))
        assertFalse(p.importJson("not json"))
    }

    @Test fun weeklyCalendarAndTrend() {
        val today = LocalDate.of(2026, 11, 18) // a Wednesday
        assertEquals(LocalDate.of(2026, 11, 16), Weekly.weekStart(today))
        val hist = listOf(
            DayStats("2026-11-09", points = 20), DayStats("2026-11-10", points = 20), DayStats("2026-11-11", points = 20),
            DayStats("2026-11-16", points = 60), DayStats("2026-11-17", points = 5),
        )
        assertTrue(Weekly.week(hist, LocalDate.of(2026, 11, 9), 3).met)
        assertEquals(1, Weekly.week(hist, LocalDate.of(2026, 11, 16), 3).practiceDays)
        assertEquals(1, Weekly.weekStreak(hist, today, 3))
        val cal = Weekly.calendar(hist, today)
        assertEquals(6, cal.size)
        assertEquals(60, cal.last()[0].second)
        val p = prefs()
        p.recordPlay("H16v10", 3.5, false, today)
        p.recordPlay("H16v10", 3.2, true, today)
        p.recordPlay("H12v3", -2.0, true, today.minusWeeks(2))
        val s = TcTrend.series(p.errorByWeek(), today)
        assertEquals(0.5, s[2].last()!!, 1e-9)
        assertEquals(1.0, s[0][5]!!, 1e-9)
        assertNull(s[1].last())
    }

    @Test fun profilesCareerAndBadges() {
        val s = CasinoProfiles.ALL[0].apply(AppSettings())
        assertEquals(0, CasinoProfiles.matching(s))
        assertTrue(CasinoProfiles.ALL[4].apply(AppSettings()).sixToFive)
        assertEquals(2, CasinoProfiles.ALL[5].apply(AppSettings()).shuffleChoice)
        assertFalse(CareerLevels.ready(CareerProgress(0, 39, 39, 5, 5)))
        assertTrue(CareerLevels.ready(CareerProgress(0, 40, 37, 5, 4)))
        assertFalse(CareerLevels.ready(CareerProgress(CareerLevels.ALL.size - 1, 999, 999, 99, 99)))
        val ctx = BadgeContext(LifetimeStats(handsPlayed = 150, bestStrategyStreak = 30))
        val ids = Badges.earned(ctx).map { it.id }
        assertTrue("h100" in ids && "run25" in ids && "first" in ids)
        assertFalse("h1000" in ids)
    }

    @Test fun pitFlatBetsThenBacksOff() {
        val v = PlayViewModel(prefs { it.copy(coverPlay = true, pitBackoff = true, quizEvery = 0, confirmMistakes = false, gradeBets = false, bots = 0) }).apply { instant = true }
        // Big jumps build heat until the pit steps in.
        var guard = 0
        while (!v.flatBetOnly && guard++ < 30) {
            v.clearBet(); v.addChip(if (guard % 2 == 0) 100 else 10)
            playRound(v)
        }
        assertTrue(v.flatBetOnly)
        assertTrue(v.heat >= CoverPlay.LEAVE_AT)
        // Playing at the locked bet is fine.
        val locked = v.bet
        playRound(v)
        assertTrue(v.flatBetOnly)
        // Raising gets you backed off and sent to a new table.
        v.clearBet(); v.addChip(locked + 100)
        v.deal()
        assertFalse(v.flatBetOnly)
        assertEquals("Backed off", v.banner?.title)
        assertEquals(0.0, v.heat, 1e-9)
    }

    @Test fun dealerChecksBlockBettingUntilAnswered() {
        val p = prefs { it.copy(dealerErrors = true, quizEvery = 0, confirmMistakes = false, bots = 0) }
        val v = PlayViewModel(p).apply { instant = true; dealerErrorChance = 100 }
        var guard = 0
        while (v.dealerCheck == null && guard++ < 50) playRound(v)
        val chk = v.dealerCheck
        assertNotNull(chk)
        assertFalse(v.bettingOpen)
        v.callDealer(chk!!.wrong)
        assertNull(v.dealerCheck)
        assertTrue(v.bettingOpen)
        assertEquals(1, p.loadStats().dealerChecksOk)
    }

    @Test fun careerPromotesOnAccuracy() {
        val p = prefs { it.copy(confirmMistakes = false) }
        p.career = CareerProgress(0, 39, 39, 5, 5)
        val v = PlayViewModel(p).apply { instant = true }
        v.startCareer(0)
        assertEquals(1, v.settings.bots)
        assertEquals(0, v.settings.dealSpeed)
        var guard = 0
        while (p.career.level == 0 && guard++ < 20) playRound(v)
        assertEquals(1, p.career.level)
        v.stopCareer()
        assertNull(v.careerLevel)
    }

    @Test fun midShoeAndSession() {
        val p = prefs { it.copy(quizEvery = 0, confirmMistakes = false) }
        val v = PlayViewModel(p).apply { instant = true }
        v.joinMidShoe()
        assertTrue(v.joinedMidShoe)
        assertTrue(v.state.cardsDealt >= 52)
        assertEquals(0, v.state.runningCount)
        v.startSession()
        assertTrue(v.sessionActive)
        repeat(3) { playRound(v) }
        v.advanceSession(21 * 60_000L)
        assertEquals("Time for a break", v.banner?.title)
        v.takeBreak()
        assertTrue(v.onBreak)
        assertFalse(v.canDeal)
        v.endBreak()
        v.advanceSession(40 * 60_000L)
        assertFalse(v.sessionActive)
        assertTrue(v.sessionDone)
        assertEquals(1, p.loadStats().sessionsCompleted)
        assertTrue(v.ev.hands >= 3)
    }

    @Test fun errorBucketsStillWork() {
        assertEquals(5, ErrorMap.tcBucket(3.4))
    }
}
