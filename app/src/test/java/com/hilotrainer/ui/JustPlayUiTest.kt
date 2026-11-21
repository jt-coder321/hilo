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
            assertEquals("round $it", before + net, v.bankroll)
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
    @Test fun faces() {
        val looks = com.hilotrainer.engine.Cast.ALL.map { it.name to it.look } + com.hilotrainer.engine.CasualDealers.ALL.map { it.name to it.look }
        shoot("jp-faces") {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF0F5A37))) {
                looks.chunked(4).forEachIndexed { r, row ->
                    androidx.compose.foundation.layout.Row {
                        row.forEachIndexed { i, (n, l) -> Avatar(l, 100.dp, mood = Mood.entries[(r * 4 + i) % Mood.entries.size], dealer = n in setOf("Vera", "Danny", "Mr. Grant"), name = n) }
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
