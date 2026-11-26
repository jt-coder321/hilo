package com.hilotrainer.ui

import androidx.test.core.app.ApplicationProvider
import com.hilotrainer.engine.Dealers
import com.hilotrainer.engine.EventKind
import com.hilotrainer.engine.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class V7UiTest {
    private fun prefs(f: (AppSettings) -> AppSettings = { it }): Prefs {
        val p = Prefs(ApplicationProvider.getApplicationContext())
        p.saveSettings(f(AppSettings()))
        return p
    }
    private fun vm(p: Prefs) = PlayViewModel(p).apply { instant = true }

    private fun round(v: PlayViewModel) {
        v.deal()
        if (v.state.phase == Phase.INSURANCE) v.insurance(false)
        while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        if (v.quiz != null) { v.submitRunning(); v.submitTrue() }
    }

    @Test fun buyInKeepAndColorUp() {
        val v = vm(prefs { it.copy(quizEvery = 0) })
        val start = v.state.bankroll
        v.askBuyIn()
        assertTrue(v.needBuyIn)
        assertFalse("can't deal before sitting down", v.canDeal)
        v.keepChips()
        assertFalse(v.needBuyIn)
        assertEquals(start, v.state.bankroll)
        assertTrue(v.canDeal)
        round(v)
        v.colorUp()
        assertNotNull(v.colorUpText)
        assertTrue(v.colorUpText!!.contains("color up"))
        assertTrue(v.needBuyIn)
        assertTrue(v.justColoredUp)
        v.dismissColorUp()
        v.buyIn(2000)
        assertEquals(2000, v.state.bankroll)
        assertFalse(v.needBuyIn)
        assertFalse(v.justColoredUp)
    }

    @Test fun tippingCostsChipsAndCoolsHeat() {
        val p = prefs { it.copy(coverPlay = true, quizEvery = 0) }
        val v = vm(p)
        val before = v.state.bankroll
        v.tipDealer(5)
        assertEquals(before - 5, v.state.bankroll)
        assertEquals(5, v.tipsThisSession)
        assertEquals(5, p.loadStats().tipsGiven)
    }

    @Test fun handSignals() {
        val v = vm(prefs { it.copy(signals = 1, quizEvery = 0, confirmMistakes = false) })
        var g = 0
        while (g++ < 100) {
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(false)
            if (v.state.phase == Phase.PLAYER) break
            if (v.quiz != null) { v.submitRunning(); v.submitTrue() }
        }
        assertEquals(Phase.PLAYER, v.state.phase)
        val cards = v.state.hands[0].cards.size
        v.signal(hit = true)
        assertTrue(v.state.hands[0].cards.size == cards + 1 || v.state.phase != Phase.PLAYER)
        if (v.state.phase == Phase.PLAYER) { v.signal(hit = false); assertNotEquals(Phase.PLAYER, v.state.phase) }
        // Off means the felt ignores you.
        val off = vm(prefs { it.copy(signals = 0, quizEvery = 0) })
        repeat(100) { if (off.state.phase != Phase.PLAYER) { off.deal(); if (off.state.phase == Phase.INSURANCE) off.insurance(false) } }
        if (off.state.phase == Phase.PLAYER) {
            val n = off.state.hands[0].cards.size
            off.signal(true)
            assertEquals(n, off.state.hands[0].cards.size)
        }
    }

    @Test fun flashedHoleCardInTheApp() {
        val v = vm(prefs { it.copy(quizEvery = 0, bots = 0) }).apply { flashOverride = 100 }
        var g = 0
        while (g++ < 100) {
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(false)
            if (v.holeFlashShowing) break
            while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        }
        assertTrue(v.holeFlashShowing)
        assertEquals(Phase.PLAYER, v.state.phase)
        assertTrue(v.banner!!.title.startsWith("The dealer flashed"))
        while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        assertFalse(v.state.events.first { it.kind == EventKind.FLIP }.counted)
    }

    @Test fun dealerShiftChange() {
        val v = vm(prefs { it.copy(quizEvery = 0) })
        val first = v.dealer
        repeat(Dealers.SHIFT_ROUNDS + 1) { round(v) }
        assertNotEquals(first, v.dealer)
    }

    @Test fun seatsHaveNamesAndStacks() {
        val v = vm(prefs { it.copy(bots = 3, quizEvery = 0) })
        assertEquals(3, v.seatNames.size)
        assertEquals(3, v.seatNames.toSet().size)
        assertTrue(v.seatStacks.all { it > 0 })
        repeat(5) { round(v) }
        assertEquals(v.state.bots.size, v.seatNames.size)
    }

    @Test fun everyScreenIsReachableFromHome() {
        val inHubs = Hubs.ALL.flatMap { h -> h.groups.flatMap { it.second.map { i -> i.screen } } }.toSet()
        val direct = setOf(Screen.HOME, Screen.PLAY, Screen.JUST_PLAY, Screen.SETTINGS, Screen.COURSE, Screen.QUICK_DRILL) + Hubs.ALL.map { it.screen }
        // Weak spots are also opened from the heat map.
        val missing = Screen.entries.filter { it !in inHubs && it !in direct }
        assertEquals(emptyList<Screen>(), missing)
        assertTrue(Hubs.ALL.all { it.count in 4..14 })
    }

    @Test fun settingsSummaries() {
        val s = AppSettings()
        SettingsPage.entries.forEach { assertTrue(settingsSummary(it, s).isNotBlank()) }
        assertTrue(settingsSummary(SettingsPage.RULES, s.copy(decks = 5, sixToFive = true)).startsWith("Custom: 5 decks"))
        assertTrue(settingsSummary(SettingsPage.DATA, s.copy(reminderOn = true, reminderHour = 19)).contains("7 PM"))
        assertTrue(settingsSummary(SettingsPage.TABLE, s.copy(bots = 0)).startsWith("Just you"))
    }

    @Test fun tableQuestionsBlockAndAreGraded() {
        val p = prefs { it.copy(distractions = true, distractionsReal = true, quizEvery = 0) }
        val v = vm(p)
        val q = com.hilotrainer.engine.Distractions.Event(0, com.hilotrainer.engine.Distractions.Kind.QUESTION, "Ray", "What's the minimum here?", listOf("\$5", "\$10", "\$25"), 1, durationMs = 0)
        v.runDistractions(listOf(q))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val d = v.distraction!!
        assertTrue(d.blocking)
        assertEquals(3, d.options.size)
        v.answerDistraction(0)
        assertEquals(null, v.distraction)
        assertEquals(1, p.loadStats().distractQs)
        assertEquals(0, p.loadStats().distractQsOk)
        assertTrue(v.banner!!.title.contains("\$10"))
    }

    @Test fun countChecksSplitByDistraction() {
        val p = prefs { it.copy(distractions = true, quizEvery = 1, bots = 0) }
        val v = vm(p)
        val talk = com.hilotrainer.engine.Distractions.Event(0, com.hilotrainer.engine.Distractions.Kind.NUMBERS, "Ray", "I'm in room 1207.")
        v.deal(); if (v.state.phase == Phase.INSURANCE) v.insurance(false)
        v.runDistractions(listOf(talk))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNotNull(v.aside)
        while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        v.submitRunning(); v.submitTrue()
        round(v)
        val st = p.loadStats()
        assertEquals(1, st.distractChecks)
        assertEquals(1, st.quietChecks)
        assertNotNull(v.distractionCompare())
    }

    @Test fun realismSettingsSave() {
        val p = prefs { it.copy(signals = 2, seatedView = true, cardBack = 3, dealerVoice = false, cutCards = false) }
        val s = p.loadSettings()
        assertEquals(2, s.signals); assertTrue(s.seatedView); assertEquals(3, s.cardBack)
        assertFalse(s.dealerVoice); assertFalse(s.cutCards)
    }
}
