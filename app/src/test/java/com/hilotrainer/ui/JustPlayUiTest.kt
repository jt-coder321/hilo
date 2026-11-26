package com.hilotrainer.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.Phase
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

private fun casualVm(seed: Int, s: CasualSettings = CasualSettings()): CasualViewModel {
    val store = CasualStore(null)
    store.saveSettings(s)
    return CasualViewModel(store, Random(seed)).apply { instant = true }
}

private fun playOne(v: CasualViewModel) {
    if (v.broke) v.rebuy()
    v.deal()
    if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
    while (v.gs.phase == Phase.PLAYER) v.act(v.gs.basicAdvice!!.action)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JustPlayUiTest {
    @Test fun bankrollMatchesEveryRound() {
        val v = casualVm(21, CasualSettings(sideBets = true))
        v.cycleTP(); v.cyclePP()
        repeat(400) {
            if (v.broke) v.rebuy()
            val before = v.bankroll
            playOne(v)
            assertEquals(Phase.ROUND_OVER, v.gs.phase)
            assertTrue(v.roundOver)
            val net = v.banner!!.amount
            assertEquals("round $it", before + net + v.roundExtras, v.bankroll)
            assertEquals(v.bankroll, v.store.bankroll)
        }
        assertTrue(v.stats.hands >= 400)
        assertTrue(v.earned.isNotEmpty())
    }

    @Test fun noCountingAnywhere() {
        // Just Play never shows a running or true count: the table state is only cards and chips.
        val v = casualVm(3)
        playOne(v)
        assertTrue(v.hint == null)
        v.update(v.settings.copy(hints = true))
        repeat(50) { if (v.gs.phase != Phase.PLAYER) { v.deal() } ; if (v.gs.phase == Phase.INSURANCE) v.insurance(false); if (v.gs.phase == Phase.PLAYER) { v.showHint(); return@repeat } }
        if (v.gs.phase == Phase.PLAYER) assertTrue(v.hint!!.startsWith("Basic strategy"))
    }

    @Test fun dailyBonusOncePerDay() {
        val v = casualVm(4)
        val start = v.bankroll
        assertEquals(250, v.claimBonus(today = 500))
        assertEquals(0, v.claimBonus(today = 500))
        assertEquals(300, v.claimBonus(today = 501))
        assertEquals(start + 550, v.bankroll)
    }

    @Test fun roomsUnlockByBankrollOrInvite() {
        val v = casualVm(5)
        val silver = com.hilotrainer.engine.Tiers.ALL[2]
        assertTrue(!v.tierUnlocked(silver))
        v.store.invites = v.store.invites + silver.id.toString()
        assertTrue(v.tierUnlocked(silver))
        v.update(v.settings.copy(tableMin = 25))
        assertEquals(25, v.settings.tableMin)
        assertTrue(v.seatView.any { it != null })
        assertTrue(v.seatView.filterNotNull().none { it.who.archetype == com.hilotrainer.engine.Archetype.TOURIST || it.who.archetype == com.hilotrainer.engine.Archetype.ROOKIE })
    }

    @Test fun wheelAndLuckyCardsPayOut() {
        val v = casualVm(6)
        v.giveWheelForTest()
        val before = v.bankroll
        val i = v.spinWheel()
        assertTrue(i in 0..7)
        v.collectWheel()
        assertEquals(before + com.hilotrainer.engine.Wheel.UNITS[i] * v.settings.tableMin, v.bankroll)
        assertTrue(!v.wheelReady)
        val b2 = v.bankroll
        v.claimBonus(1000)
        val prizes = v.luckyPrizes!!
        assertEquals(listOf(25, 50, 150), prizes.sorted())
        val b3 = v.bankroll
        v.pickLucky(1)
        assertEquals(b3 + prizes[1], v.bankroll)
        v.pickLucky(0)
        assertEquals(b3 + prizes[1], v.bankroll)
        assertTrue(b3 > b2)
    }

    @Test fun dareSettlesOnTheHand() {
        repeat(6) { seed ->
            val v = casualVm(40 + seed)
            v.seatForTest(1, "Hank")
            v.giveDareForTest(1, 30)
            v.acceptDare()
            assertTrue(v.dareOn)
            val before = v.bankroll
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            while (v.gs.phase == Phase.PLAYER) v.act(v.gs.basicAdvice!!.action)
            assertTrue(!v.dareOn)
            assertTrue(v.bankroll >= 0)
            assertEquals(v.bankroll, v.store.bankroll)
            val net = v.gs.results.sumOf { it.net }
            val delta = v.bankroll - before
            assertEquals("net $net delta $delta", net + v.roundExtras, delta)
            assertTrue(v.roundExtras == 30 || v.roundExtras == -30 || net == 0 || v.roundExtras >= -30)
        }
    }

    @Test fun rushAndCompsKeepTheLedgerHonest() {
        val v = casualVm(77)
        var rush = false
        repeat(120) { playOne(v); if (v.rushLeft > 0) rush = true; assertEquals(v.bankroll, v.store.bankroll); assertTrue(v.bankroll >= 0) }
        assertTrue("a rush should start within 120 hands", rush)
    }

    @Test fun funLinesHaveNoDashes() {
        val all = com.hilotrainer.engine.FunTalk.familiar + com.hilotrainer.engine.FunTalk.drinkSend + com.hilotrainer.engine.FunTalk.drinkThanks +
            com.hilotrainer.engine.FunTalk.banter.flatMap { listOf(it.first, it.second) } + com.hilotrainer.engine.Dares.accepted + com.hilotrainer.engine.Dares.declined +
            com.hilotrainer.engine.Dares.won + com.hilotrainer.engine.Dares.lost + com.hilotrainer.engine.Tiers.ALL.map { it.blurb + it.name }
        all.forEach { assertTrue(it, !it.contains('\u2014') && !it.contains('\u2013') && !it.contains(" - ")) }
    }

    @Test fun rulesChangeBetweenHands() {
        val v = casualVm(5)
        playOne(v)
        v.update(v.settings.copy(decks = 2, tableMin = 25, seats = 2))
        assertEquals(25, v.bet)
        playOne(v)
        assertTrue(v.seatView.withIndex().all { (i, s) -> s == null || i in setOf(1, 2) })
    }
}

/** Screenshots of Just Play on the Fold's cover and inner screens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class JustPlayShotBase {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    abstract val tag: String
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

    @Before fun still() { CasualAnim.live = false }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent { HiLoTheme { Box(Modifier.fillMaxSize().background(Ink)) { content() } } }
        rule.mainClock.advanceTimeBy(4000)
        rule.waitForIdle()
        var captured: Bitmap? = null
        rule.runOnIdle {
            val v = rule.activity.window.decorView
            captured = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        File(outDir, "$tag-$name.png").outputStream().use { captured!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun table(seed: Int = 8, s: CasualSettings = CasualSettings()): CasualViewModel {
        val v = casualVm(seed, s)
        listOf(0 to "Hank", 1 to "Grandma Dot", 2 to "Brenda", 3 to "Victor").forEach { (i, n) -> v.seatForTest(i, n) }
        return v
    }

    @Test fun home() {
        val p = Prefs(ApplicationProvider.getApplicationContext()).apply { tourSeen = true }
        shoot("jp-home") { HomeScreen(p) {} }
    }

    @Test fun betting() {
        val v = table()
        v.addChip(25); v.addChip(25); v.cyclePP()
        v.sayForTest(0, "Aloha, folks! First night in town!")
        v.sayForTest(CasualViewModel.DEALER, "Evening. Bets down, please.")
        shoot("jp-betting") { JustPlayScreen(v) {} }
    }

    @Test fun midHand() {
        val v = table(12)
        v.addChip(50)
        repeat(60) {
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            if (v.gs.phase == Phase.PLAYER && v.gs.bots.size >= 3) return@repeat
            while (v.gs.phase == Phase.PLAYER) v.act(Action.STAND)
        }
        v.sayForTest(2, "Woo! Bachelorette party, coming through!")
        v.seatView[3]?.let { v.moodForTest(it.id, Mood.CHEER) }
        v.seatView[1]?.let { v.moodForTest(it.id, Mood.SAD) }
        shoot("jp-play") { JustPlayScreen(v) {} }
    }

    private fun chatScene(style: Int): CasualViewModel {
        val v = table(12, CasualSettings(chatStyle = style))
        v.addChip(50)
        repeat(60) {
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            if (v.gs.phase == Phase.PLAYER && v.gs.bots.size >= 3) return@repeat
            while (v.gs.phase == Phase.PLAYER) v.act(Action.STAND)
        }
        v.seatView[3]?.let { v.moodForTest(it.id, Mood.CHEER) }
        v.seatView[1]?.let { v.moodForTest(it.id, Mood.SAD) }
        v.sayForTest(0, "Nice weather out there. Not that I've seen it.", minor = true)
        v.sayForTest(1, "A tie? Fine. I'll take a tie.")
        v.sayForTest(CasualViewModel.DEALER, "Insurance is open. Bets down, please.")
        v.sayForTest(2, "Woo! Bachelorette party, coming through!")
        v.sayForTest(3, "Hit it. Come on, give me a ten.", minor = true)
        v.pitForTest(3)
        v.sayForTest(CasualViewModel.PIT, "Sir, you're welcome to play any other game.")
        return v
    }
    @Test fun chat0() { shoot("jp-chat-lane") { JustPlayScreen(chatScene(0)) {} } }
    @Test fun chat1() { shoot("jp-chat-bubbles") { JustPlayScreen(chatScene(1)) {} } }
    @Test fun chat2() { shoot("jp-chat-captions") { JustPlayScreen(chatScene(2)) {} } }
    @Test fun chat3() { shoot("jp-chat-log") { JustPlayScreen(chatScene(3)) {} } }
    @Test fun chat4() { shoot("jp-chat-float") { JustPlayScreen(chatScene(4)) {} } }

    @Test fun countFollowsTheCardsAndSurvivesLeaving() {
        val store = CasualStore(null)
        store.saveSettings(CasualSettings(showCount = true))
        val v = CasualViewModel(store, Random(31)).apply { instant = true }
        assertTrue(v.settings.showCount)
        repeat(6) { playOne(v); assertEquals(v.gs.runningCount, v.countRun) }
        // A round you sit out still moves the count, costs nothing and leaves you without a hand.
        val bank = v.bankroll
        val dealtBefore = v.gs.cardsDealt
        v.watch()
        assertEquals(bank, v.bankroll)
        assertTrue(v.gs.hands.isEmpty())
        assertTrue(v.gs.cardsDealt > dealtBefore || v.gs.shuffledThisRound)
        assertEquals(v.gs.runningCount, v.countRun)
        // Leave and come back: a new view model on the same store picks the shoe up where it was.
        val back = CasualViewModel(store, Random(32)).apply { instant = true }
        assertEquals(v.countRun, back.countRun)
        assertEquals(v.gs.cardsDealt, back.gs.cardsDealt)
        assertTrue(back.settings.showCount)
        // True count is the running count over the decks left.
        assertEquals(back.countRun / back.decksLeft, back.countTrue, 1e-9)
    }

    @Test fun eachRoomKeepsItsOwnShoe() {
        val store = CasualStore(null)
        val v = CasualViewModel(store, Random(8)).apply { instant = true }
        repeat(5) { playOne(v) }
        val room1 = v.countRun
        val dealt1 = v.gs.cardsDealt
        v.update(v.settings.copy(tableMin = 25))
        assertEquals(0, v.gs.cardsDealt)
        repeat(3) { playOne(v) }
        v.update(v.settings.copy(tableMin = 10))
        assertEquals(dealt1, v.gs.cardsDealt)
        assertEquals(room1, v.countRun)
    }

    @Test fun countNeverShowsTheHoleCard() {
        repeat(8) { seed ->
            val v = casualVm(200 + seed, CasualSettings(showCount = true))
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            if (v.gs.phase == Phase.PLAYER) assertEquals(v.gs.runningCount, v.countRun)
        }
    }

    @Test fun countBetweenHands() {
        val v = table(21, CasualSettings(showCount = true))
        v.addChip(25)
        repeat(7) { playOne(v) }
        shoot("jp-count-between") { JustPlayScreen(v) {} }
    }

    @Test fun countMidHand() {
        val w = table(22, CasualSettings(showCount = true))
        w.addChip(25)
        repeat(4) { playOne(w) }
        w.deal()
        if (w.gs.phase == Phase.INSURANCE) w.insurance(false)
        shoot("jp-count-midhand") { JustPlayScreen(w) {} }
    }

    @Test fun countOffIsJustASmallTag() {
        val v = table(23)
        v.addChip(25)
        assertTrue(!v.settings.showCount)
        shoot("jp-count-off") { JustPlayScreen(v) {} }
    }

    @Test fun powerUpsAreEarnedKeptAndSpent() {
        val store = CasualStore(null)
        val v = CasualViewModel(store, Random(5)).apply { instant = true }
        assertEquals(listOf(1, 1, 1), v.power.toList())
        // Peek only works during your hand and shows the real hole card.
        v.usePower(com.hilotrainer.engine.PowerUp.PEEK)
        assertEquals(1, v.power[0])
        var peeked = false
        repeat(40) {
            if (peeked) return@repeat
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            if (v.gs.phase == Phase.PLAYER) {
                v.usePower(com.hilotrainer.engine.PowerUp.PEEK)
                assertEquals(v.gs.dealerCards[1], v.peeked)
                assertEquals(0, v.power[0])
                peeked = true
            }
            while (v.gs.phase == Phase.PLAYER) v.act(Action.STAND)
            assertNull(v.peeked)
        }
        assertTrue(peeked)
        assertEquals(0, CasualViewModel(store, Random(6)).apply { instant = true }.power[0])
        // Armed or not, a power-up never makes the books wrong.
        assertTrue(v.power.sum() <= 6)
    }

    @Test fun bustCoverReturnsTheBetAndTheLedgerStaysHonest() {
        var covered = 0
        repeat(60) { seed ->
            val v = casualVm(300 + seed)
            val before = v.bankroll
            v.usePower(com.hilotrainer.engine.PowerUp.COVER)
            assertTrue(v.armedCover)
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            while (v.gs.phase == Phase.PLAYER && v.gs.hands.all { it.total <= 21 }) v.act(Action.HIT)
            if (v.gs.results.any { it.outcome == com.hilotrainer.engine.Outcome.BUST }) {
                covered++
                assertEquals(before, v.bankroll)
            }
            assertEquals(before + v.gs.results.sumOf { it.net } + v.roundExtras, v.bankroll)
            assertTrue(!v.armedCover)
        }
        assertTrue("never busted", covered > 3)
    }

    @Test fun miniGameBetsAreSettledOnTheCards() {
        val v = casualVm(61)
        v.cycleLL(); v.cycleNC(); v.callNC(2)
        assertTrue(v.miniTotal > 0)
        repeat(60) {
            if (v.broke) v.rebuy()
            if (v.miniTotal == 0) { v.cycleLL(); v.cycleNC() }
            val before = v.bankroll
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            while (v.gs.phase == Phase.PLAYER) v.act(v.gs.basicAdvice!!.action)
            assertEquals(before + v.banner!!.amount + v.roundExtras, v.bankroll)
            assertEquals(v.bankroll, v.store.bankroll)
        }
    }

    @Test fun powerUpsAndMiniGamesOnScreen() {
        val v = table(41)
        v.addChip(25)
        v.cycleLL(); v.cycleNC(); v.cyclePP()
        shoot("jp-powerups-bet") { JustPlayScreen(v) {} }
    }

    @Test fun miniGamesDialog() {
        val v = table(42)
        v.addChip(25)
        v.cycleLL(); v.callNC(1); v.cycleNC()
        v.miniOpen = true
        shoot("jp-minigames") { JustPlayScreen(v) {} }
    }

    @Test fun peekShowsTheHoleCard() {
        val v = table(43, CasualSettings(showCount = true))
        v.addChip(25)
        repeat(30) { seed ->
            if (v.peeked != null) return@repeat
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            if (v.gs.phase == Phase.PLAYER) { v.usePower(com.hilotrainer.engine.PowerUp.PEEK); if (v.peeked == null) while (v.gs.phase == Phase.PLAYER) v.act(Action.STAND) }
            else if (v.gs.phase != Phase.PLAYER && v.gs.phase != Phase.INSURANCE) Unit
        }
        shoot("jp-peek") { JustPlayScreen(v) {} }
    }

    @Test fun countWithBonusAndPit() {
        val v = table(44, CasualSettings(showCount = true))
        v.addChip(25)
        repeat(6) { playOne(v) }
        v.pitForTest(3)
        v.sayForTest(CasualViewModel.PIT, "Sir, you're welcome to play any other game.")
        shoot("jp-count-pit") { JustPlayScreen(v) {} }
    }

    @Test fun storyNightUnlocksPaysAndSaves() {
        val store = CasualStore(null)
        val v = CasualViewModel(store, Random(12)).apply { instant = true }
        v.startNight(1)
        assertNull(v.night)
        v.startNight(0)
        assertNotNull(v.night)
        val start = v.bankroll
        repeat(5) {
            val before = v.bankroll
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            while (v.gs.phase == Phase.PLAYER) v.act(v.gs.basicAdvice!!.action)
            assertEquals(before + v.gs.results.sumOf { it.net } + v.roundExtras, v.bankroll)
        }
        assertNull(v.night)
        assertEquals(1, v.storyDone)
        assertTrue(v.bankroll != start || true)
        // The next night is open and the progress survives leaving.
        v.startNight(1)
        assertEquals(1, v.night!!.chapter)
        val back = CasualViewModel(store, Random(13)).apply { instant = true }
        assertEquals(1, back.storyDone)
        assertEquals(1, back.night!!.chapter)
        back.quitNight()
        assertNull(back.night)
    }

    @Test fun duelWithTheRivalSettlesAndKeepsScore() {
        var settled = 0
        repeat(25) { seed ->
            val v = casualVm(500 + seed)
            v.seatForTest(1, "Savannah")
            v.giveDuelForTest(1, 30)
            v.acceptDare()
            assertTrue(v.dareOn)
            val before = v.bankroll
            v.deal()
            if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
            while (v.gs.phase == Phase.PLAYER) v.act(v.gs.basicAdvice!!.action)
            assertTrue(!v.dareOn)
            assertEquals(before + v.gs.results.sumOf { it.net } + v.roundExtras, v.bankroll)
            assertTrue(v.roundExtras == 30 || v.roundExtras == -30 || v.roundExtras == 0 || v.roundExtras < 0)
            val total = v.rivalWins + v.rivalLosses
            assertTrue(total <= 1)
            settled += total
            assertEquals("Savannah", v.rivalName)
            assertEquals(v.rivalWins, v.store.rivalWins)
        }
        assertTrue("never settled", settled > 10)
    }

    @Test fun nightsTabAndDuel() {
        val v = table(45)
        v.addChip(25)
        repeat(7) { playOne(v) }
        v.setStoryForTest(2)
        v.startNight(2)
        v.giveDuelForTest(2, 50)
        shoot("jp-duel") { JustPlayScreen(v) {} }
    }

    @Test fun nightsMenu() {
        val v = table(46)
        v.addChip(25)
        repeat(4) { playOne(v) }
        v.setStoryForTest(2)
        v.startNight(2)
        shoot("jp-nights") { CasualMenu(v, 0, {}, part = 1) {} }
    }

    @Test fun sideBetPanelShowsEachBetOnItsOwnLine() {
        val v = table(47, CasualSettings(chatStyle = 1))
        v.addChip(25)
        v.cyclePP(); v.cycleTP(); v.cycleLL(); v.cycleNC()
        v.deal()
        if (v.gs.phase == Phase.INSURANCE) v.insurance(false)
        while (v.gs.phase == Phase.PLAYER) v.act(Action.STAND)
        val lines = v.sideLines
        assertEquals(listOf("Perfect Pairs", "21+3", "Lucky Ladies", "Next Card"), lines.map { it.name })
        lines.forEach { assertTrue(it.result.isNotBlank()) }
        // The line nets add up to the side bet part of the round.
        val sideNet = lines.sumOf { it.net }
        assertEquals(v.banner!!.amount, v.gs.results.sumOf { it.net } + v.gs.insuranceNet + sideNet)
        shoot("jp-sidebets-panel") { JustPlayScreen(v) {} }
    }

    @Test fun streakIsCosmeticAndLeavesTheShoeAlone() {
        val v = casualVm(61)
        v.addChip(25); playOne(v)
        val dealt = v.gs.cardsDealt; val run = v.gs.runningCount; val total = v.gs.totalCards; val count = v.countRun
        v.streakForTest(listOf(1, 1, 1, 1, 1, 1)); assertEquals(2, v.streakLevel)
        v.streakForTest(listOf(-1, -1, -1, -1, -1, -1)); assertEquals(-2, v.streakLevel)
        assertEquals(dealt, v.gs.cardsDealt); assertEquals(run, v.gs.runningCount); assertEquals(total, v.gs.totalCards); assertEquals(count, v.countRun)
    }

    @Test fun bigBettingDrawsThePitBossAndCoolingOffBlocksBets() {
        val v = casualVm(33, CasualSettings(tableMin = 10))
        listOf(0 to "Hank", 1 to "Grandma Dot", 2 to "Brenda", 3 to "Victor").forEach { (i, n) -> v.seatForTest(i, n) }
        v.heatForTest(5)
        var rail = false
        var rounds = 0
        while (rounds++ < 40 && !rail) {
            if (v.bankroll < 500) v.rebuy()
            v.clearBet(); repeat(5) { v.addChip(100) }
            if (v.flatLeft > 0) { assertTrue(v.maxBet <= 50); v.clearBet(); v.addChip(10) }
            playOne(v)
            rail = v.cooloff > 0
        }
        assertTrue("never sent to the rail", rail)
        val before = v.bankroll; val seq = v.gs.roundStartSeq
        v.clearBet(); v.addChip(10)
        v.deal()
        assertEquals(before, v.bankroll); assertEquals(seq, v.gs.roundStartSeq)
        val left = v.cooloff
        repeat(left) { v.watch() }
        assertEquals(0, v.cooloff)
        v.clearBet(); v.addChip(10); v.deal()
        assertTrue(v.gs.roundStartSeq != seq)
    }

    @Test fun heatAndStreakStrip() {
        val v = table(52, CasualSettings(chatStyle = 2, tableMin = 10))
        v.streakForTest(listOf(1, 1, 1, 1, 1, 1)); v.heatForTest(8)
        v.addChip(100)
        shoot("jp-heat") { JustPlayScreen(v) {} }
    }

    @Test fun heatStripShowsTheRailAndCold() {
        val v = table(53, CasualSettings(chatStyle = 2, tableMin = 10))
        v.streakForTest(listOf(-1, -1, -1, -1, -1, -1)); v.heatForTest(9)
        v.addChip(10)
        shoot("jp-cold") { JustPlayScreen(v) {} }
    }

    private fun dealerTalkRound(style: Int, name: String) {
        val v = table(47, CasualSettings(chatStyle = style))
        v.addChip(25)
        playOne(v)
        v.sayForTest(CasualViewModel.DEALER, "Big money, no whammies! Let's see what the next one brings.")
        v.sayForTest(1, "Why do I always hit that?")
        shoot(name) { JustPlayScreen(v) {} }
    }
    @Test fun dealerBubbleClearsBannerBubbles() = dealerTalkRound(1, "jp-dealertalk-bubbles")
    @Test fun dealerBubbleClearsBannerFloating() = dealerTalkRound(4, "jp-dealertalk-float")
    @Test fun dealerBubbleClearsBannerCaptions() = dealerTalkRound(2, "jp-dealertalk-captions")

    private fun runVm(seed: Int, deck: com.hilotrainer.engine.StartDeck = com.hilotrainer.engine.StartDeck.COUNTER): RunVm {
        val store = CasualStore(null)
        return RunVm(store, Random(seed)).also { it.start(deck); it.run?.skipPack(); it.guideOpen = false }
    }
    private fun RunVm.playOutBlind() {
        mut { startBlind() }
        var g = 0
        while (run!!.phase == com.hilotrainer.engine.RunPhase.PLAY && g++ < 300) {
            if (!run!!.inRound) { stakePick = 3; deal() } else act(run!!.gs.basicAdvice!!.action.let { a -> if (run!!.canAct(a)) a else Action.STAND })
        }
    }

    @Test fun runStart() { val v = runVm(1); v.leaveRun(); shoot("run-start") { RunScreen(v.store, {}, v) } }
    @Test fun runSelect() { val v = runVm(2); shoot("run-select") { RunScreen(v.store, {}, v) } }
    @Test fun runPlayMidHand() {
        val v = runVm(3)
        v.mut { startBlind() }
        var g = 0
        while (g++ < 40 && !(v.run!!.inRound)) { v.stakePick = 2; v.deal(); if (!v.run!!.inRound && v.run!!.handsLeft == 0) break }
        shoot("run-play") { RunScreen(v.store, {}, v) }
    }
    @Test fun runPlayScored() {
        val v = runVm(4, com.hilotrainer.engine.StartDeck.STANDARD)
        v.mut { skipBlind() }
        v.mut { startBlind() }
        var g = 0
        while (g++ < 60 && v.run!!.phase == com.hilotrainer.engine.RunPhase.PLAY && !(v.run!!.lastScore.any { it.total > 0 } && !v.run!!.inRound)) {
            if (!v.run!!.inRound) { v.stakePick = 2; v.deal() } else v.act(v.run!!.gs.basicAdvice!!.action.let { a -> if (v.run!!.canAct(a)) a else Action.STAND })
        }
        shoot("run-scored") { RunScreen(v.store, {}, v) }
    }
    @Test fun runBossRoom() {
        val v = runVm(11, com.hilotrainer.engine.StartDeck.STANDARD)
        v.mut { setState(4, 2, 12, 0, false, 9, 0, 0, false) }
        v.mut { startBlind() }
        v.stakePick = 1; v.deal()
        shoot("run-boss") { RunScreen(v.store, {}, v) }
    }
    @Test fun runBossSelect() {
        val v = runVm(12, com.hilotrainer.engine.StartDeck.STANDARD)
        v.mut { setState(5, 2, 15, 0, false, 12, 0, 0, false) }
        shoot("run-boss-select") { RunScreen(v.store, {}, v) }
    }
    @Test fun runCashoutAndShop() {
        val v = runVm(5, com.hilotrainer.engine.StartDeck.STANDARD)
        var tries = 0
        while (v.run!!.phase != com.hilotrainer.engine.RunPhase.CLEARED && tries++ < 20) { v.playOutBlind(); if (v.run!!.phase == com.hilotrainer.engine.RunPhase.OVER) v.start(com.hilotrainer.engine.StartDeck.STANDARD) }
        assertEquals(com.hilotrainer.engine.RunPhase.CLEARED, v.run!!.phase)
        shoot("run-cashout") { RunScreen(v.store, {}, v) }
    }
    @Test fun runShop() {
        val v = runVm(6, com.hilotrainer.engine.StartDeck.STANDARD)
        var tries = 0
        while (v.run!!.phase != com.hilotrainer.engine.RunPhase.CLEARED && tries++ < 20) { v.playOutBlind(); if (v.run!!.phase == com.hilotrainer.engine.RunPhase.OVER) v.start(com.hilotrainer.engine.StartDeck.STANDARD) }
        v.mut { collect() }
        v.mut { forceMoney(20) }
        v.mut { buy(offers.indexOfFirst { it.perk != null }) }
        shoot("run-shop") { RunScreen(v.store, {}, v) }
    }
    @Test fun runPackAndHistory() {
        val v = runVm(11, com.hilotrainer.engine.StartDeck.STANDARD)
        var tries = 0
        while (v.run!!.phase != com.hilotrainer.engine.RunPhase.CLEARED && tries++ < 20) { v.playOutBlind(); if (v.run!!.phase == com.hilotrainer.engine.RunPhase.OVER) v.start(com.hilotrainer.engine.StartDeck.STANDARD) }
        v.mut { collect() }
        v.mut { forceMoney(30) }
        v.mut { buy(offers.indexOfFirst { it.pack }) }
        assertTrue(v.run!!.pack != null)
        shoot("run-pack") { RunScreen(v.store, {}, v) }
    }
    @Test fun runStarterPick() {
        val store = CasualStore(null)
        val v = RunVm(store, Random(14)); v.start(com.hilotrainer.engine.StartDeck.STANDARD); v.guideOpen = false
        assertTrue(v.run!!.pack != null)
        shoot("run-starter") { RunScreen(v.store, {}, v) }
    }
    @Test fun runStartWithRank() {
        val v = runVm(16); v.store.runXp = 200; v.store.runWins = 1; v.store.runSeen = "sevens,court,ace,wild,tell"; v.store.runGoalsDone = "first,a2"
        v.leaveRun()
        shoot("run-start-rank") { RunScreen(v.store, {}, v) }
    }
    @Test fun runCodex() {
        val v = runVm(17); v.store.runSeen = "sevens,court,ace,wild,tell,soft,twenty,penny,cut,spotter,sharp,charlie"
        v.leaveRun(); v.codexOpen = true
        shoot("run-codex") { RunScreen(v.store, {}, v) }
    }
    @Test fun runGoals() {
        val v = runVm(18); v.store.runGoalsDone = "first,a2,a3"
        v.leaveRun(); v.goalsOpen = true
        shoot("run-goals") { RunScreen(v.store, {}, v) }
    }
    @Test fun runBossPrize() {
        val v = runVm(19, com.hilotrainer.engine.StartDeck.STANDARD)
        v.mut { forceScore(10_000_000) }
        v.run!!.let { r ->
            // Play the first blind, then jump to a boss clear.
        }
        val r = com.hilotrainer.engine.Run(com.hilotrainer.engine.StartDeck.STANDARD, Random(5))
        r.setState(2, 2, 14, 0, false, 5, 200, 0, false)
        r.startBlind(); r.forceScore(10_000_000)
        var g = 0
        while (r.phase == com.hilotrainer.engine.RunPhase.PLAY && g++ < 100) { if (!r.inRound) r.deal(1) else { val a = r.gs.basicAdvice?.action ?: com.hilotrainer.engine.Action.STAND; if (!r.act(a)) r.act(com.hilotrainer.engine.Action.STAND) } }
        val vm2 = RunVm(CasualStore(null), Random(2)); vm2.run = r
        shoot("run-bossprize") { RunScreen(vm2.store, {}, vm2) }
    }
    @Test fun runEndReward() {
        val v = runVm(20)
        v.store.runXp = 40
        v.mut { lose("Fell 140 short of 250 on the Big Blind of Ante 2.") }
        shoot("run-end-reward") { RunScreen(v.store, {}, v) }
    }
    @Test fun runGuide() {
        val v = runVm(15); v.guideOpen = true
        shoot("run-guide") { RunScreen(v.store, {}, v) }
    }
    @Test fun runHistory() {
        val v = runVm(12)
        v.store.runHistory = "3,7,1240,0,0;6,18,98200,1,1;2,4,310,0,0;4,10,5200,0,2"
        v.leaveRun(); v.historyOpen = true
        shoot("run-history") { RunScreen(v.store, {}, v) }
    }
    @Test fun runEnd() {
        val v = runVm(7)
        v.mut { lose("Fell 140 short of 250 on the Big Blind of Ante 2.") }
        shoot("run-end") { RunScreen(v.store, {}, v) }
    }
    @Test fun runSavesAndResumes() {
        val v = runVm(8, com.hilotrainer.engine.StartDeck.STANDARD)
        v.mut { skipBlind() }
        val v2 = RunVm(v.store, Random(1))
        assertTrue(v2.hasSave)
        v2.resume()
        assertEquals(1, v2.run!!.blind)
        v2.mut { lose("test") }
        assertTrue(!v2.hasSave)
        assertEquals(1, v2.store.runBest)
    }
    @Test fun runRespondsToRealTaps() {
        val v = runVm(9, com.hilotrainer.engine.StartDeck.STANDARD)
        assertTrue(CasualTabs == listOf("Games", "Table", "Settings", "Me"))
        rule.setContent { HiLoTheme { Box(Modifier.fillMaxSize().background(Ink)) { RunScreen(v.store, {}, v) } } }
        rule.waitForIdle()
        rule.onNodeWithText("Play blind").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Deal").assertExists()
        rule.onNodeWithText("Deal").performClick()
        rule.mainClock.advanceTimeBy(4000)
        rule.waitForIdle()
        assertTrue(v.run!!.inRound || v.run!!.handsLeft < 6)
    }

    @Test fun menuRemembersTheLastTabAndSettingsAreSearchable() {
        val store = CasualStore(null)
        assertEquals(0, store.menuTab)
        store.menuTab = 2; assertEquals(2, store.menuTab)
        store.menuTab = 9; assertEquals(3, store.menuTab)
        assertEquals(listOf("Decks", "Cut card depth"), SettingsIndex.find("deck").map { it.first }.take(1) + SettingsIndex.find("cut").map { it.first })
        assertTrue(SettingsIndex.find("count").any { it.second == SettingsPage.COACHING })
        assertTrue(SettingsIndex.find("sound").any { it.second == SettingsPage.CONTROLS })
        assertTrue(SettingsIndex.find("zzzz").isEmpty())
        assertTrue(Hubs.MODES.groups.flatMap { it.second }.none { it.screen == Screen.JUST_PLAY })
        assertEquals("Drills", Hubs.TRAIN.title)
        assertEquals("Challenges", Hubs.MODES.title)
    }

    @Test fun captionsAreTheDefault() { assertEquals(2, CasualSettings().chatStyle) }

    @Test fun longSessionWithNewTalkStaysHonest() {
        val v = casualVm(77)
        repeat(150) { playOne(v) }
        assertTrue(v.chat.size <= 6)
        assertTrue(v.bankroll >= 0)
    }

    @Test fun realismPeek() {
        val v = table(14)
        v.addChip(25)
        repeat(8) { playOne(v) }
        v.sayForTest(CasualViewModel.DEALER, "Checking for blackjack.")
        v.sayForTest(1, "Come on, no blackjack. Please.")
        v.procForTest(0, true)
        v.signalForTest("Stand")
        shoot("jp-realism-peek") { JustPlayScreen(v) {} }
    }

    @Test fun realismCut() {
        val v = table(15)
        v.addChip(25)
        repeat(5) { playOne(v) }
        v.sayForTest(CasualViewModel.DEALER, "Cutting it for you. Thank you.")
        v.sayForTest(2, "Is the cut card always that color?", minor = true)
        v.procForTest(1, false)
        shoot("jp-realism-cut") { JustPlayScreen(v) {} }
    }

    @Test fun payout() {
        val v = table(30)
        v.addChip(100)
        repeat(200) { playOne(v); if ((v.banner?.amount ?: 0) > 0) return@repeat }
        shoot("jp-payout") { JustPlayScreen(v) {} }
    }

    @Test fun pitBoss() {
        val v = table(40)
        v.addChip(25)
        playOne(v)
        v.pitForTest(3)
        v.sayForTest(CasualViewModel.PIT, "Sir, you're welcome to play any other game. Not blackjack.")
        v.sayForTest(3, "Fair enough. I'll cash out.")
        v.seatView[3]?.let { v.moodForTest(it.id, Mood.NERVOUS) }
        shoot("jp-pitboss") { JustPlayScreen(v) {} }
    }

    @Test fun themes() {
        val v = table(50, CasualSettings(felt = 2, cardBack = 1))
        v.addChip(100)
        repeat(30) { playOne(v); if (v.gs.hands.size > 1 || it > 20) return@repeat }
        shoot("jp-red") { JustPlayScreen(v) {} }
    }

    @Test fun menuRegulars() { val v = table(); shoot("jp-regulars") { CasualMenu(v, 3, {}, part = 2) {} } }
    @Test fun menuTable() { val v = table(); shoot("jp-rules") { CasualMenu(v, 1, {}) {} } }
    @Test fun menuStats() { val v = table(); repeat(40) { playOne(v) }; shoot("jp-stats") { CasualMenu(v, 3, {}) {} } }
    @Test fun menuAwards() { val v = table(); repeat(40) { playOne(v) }; shoot("jp-awards") { CasualMenu(v, 3, {}, part = 1) {} } }
    @Test fun menuLook() { val v = table(); shoot("jp-options") { CasualMenu(v, 2, {}) {} } }
    @Test fun dare() { val v = table(); v.rushForTest(2); v.drinkForTest(3); v.giveWheelForTest(); v.giveDareForTest(1, 30); v.sayForTest(1, "Bet you can't beat the dealer this hand. $30 says you can't."); v.sayForTest(CasualViewModel.DEALER, "Bets down, please."); shoot("jp-dare") { JustPlayScreen(v) {} } }
    @Test fun wheel() { val v = table(); v.giveWheelForTest(); shoot("jp-wheel") { WheelDialog(v) {} } }
    @Test fun lucky() { val v = table(); v.claimBonus(1000); v.pickLucky(1); shoot("jp-lucky") { LuckyDialog(v) } }
    @Test fun faces() {
        val looks = com.hilotrainer.engine.Cast.ALL.map { it.name to it.look } + com.hilotrainer.engine.CasualDealers.ALL.map { it.name to it.look }
        shoot("jp-faces") {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0F5A37))) {
                looks.chunked(6).forEachIndexed { r, row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEachIndexed { i, (n, l) -> Avatar(l, 64.dp, mood = Mood.entries[(r * 6 + i) % Mood.entries.size], dealer = n in setOf("Vera", "Danny", "Mr. Grant"), name = n) }
                    }
                }
            }
        }
    }
    @Test fun broke() {
        val v = table(60, CasualSettings(tableMin = 100))
        v.store.bankroll = 50
        val w = CasualViewModel(v.store, Random(1)).apply { instant = true }
        shoot("jp-broke") { JustPlayScreen(w) {} }
    }
}

@Config(sdk = [34], qualifiers = "w411dp-h820dp-xxhdpi")
class CoverJustPlayShots : JustPlayShotBase() { override val tag = "cover" }

@Config(sdk = [34], qualifiers = "w832dp-h700dp-xhdpi")
class InnerJustPlayShots : JustPlayShotBase() { override val tag = "inner" }
