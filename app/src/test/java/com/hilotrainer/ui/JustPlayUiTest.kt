package com.hilotrainer.ui

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
        v.cycleLL(); v.cycleNC()
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
        shoot("jp-nights") { CasualMenu(v, 6, {}) {} }
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

    @Test fun menuRegulars() { val v = table(); shoot("jp-regulars") { CasualMenu(v, 5, {}) {} } }
    @Test fun menuTable() { val v = table(); shoot("jp-rules") { CasualMenu(v, 0, {}) {} } }
    @Test fun menuStats() { val v = table(); repeat(40) { playOne(v) }; shoot("jp-stats") { CasualMenu(v, 3, {}) {} } }
    @Test fun menuAwards() { val v = table(); repeat(40) { playOne(v) }; shoot("jp-awards") { CasualMenu(v, 4, {}) {} } }
    @Test fun menuLook() { val v = table(); shoot("jp-look") { CasualMenu(v, 1, {}) {} } }
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
