package com.hilotrainer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hilotrainer.engine.Phase
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Version 6 screens. Landscape runs only the table shots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class V6ShotBase {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    abstract val tag: String
    open val tableOnly = false
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
    private val today = LocalDate.of(2026, 11, 18)

    private fun shoot(name: String, content: @Composable () -> Unit) {
        rule.setContent { HiLoTheme { Box(Modifier.fillMaxSize().background(Ink)) { content() } } }
        rule.mainClock.advanceTimeBy(5000)
        rule.waitForIdle()
        var captured: Bitmap? = null
        rule.runOnIdle {
            val v = rule.activity.window.decorView
            captured = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        File(outDir, "$tag-$name.png").outputStream().use { captured!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

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

    private fun dealUntil(v: PlayViewModel, cond: (PlayViewModel) -> Boolean) {
        repeat(400) {
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(false)
            if (cond(v)) return
            while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
            if (v.quiz != null) { v.submitRunning(); v.submitTrue() }
        }
    }

    /** Six weeks of practice history with plays at many counts. */
    private fun seeded(): Prefs {
        val p = prefs()
        val r = kotlin.random.Random(11)
        for (d in 0 until 40) {
            val day = today.minusDays(d.toLong())
            if (r.nextInt(10) < 3) continue
            val pts = r.nextInt(5, 90)
            p.track(points = pts, decisions = pts, correct = (pts * 0.9).toInt(), today = day)
            repeat(12) {
                val tc = r.nextInt(-3, 7) + 0.2
                val weekFactor = d / 40.0
                val wrongChance = if (tc >= 3) 0.08 + 0.3 * weekFactor else 0.04 + 0.08 * weekFactor
                p.recordPlay("H16v10", tc, r.nextDouble() > wrongChance, day)
            }
        }
        p.updateStats { it.copy(handsPlayed = 640, decisions = 1400, correctDecisions = 1330, countQuizzes = 120, countCorrect = 109, bestStrategyStreak = 41, bestDailyStreak = 8, bestDeckSeconds = 33, casinoBest = 1, quickBest = 17, dealerCaught = 4) }
        p.coursePassed = 10
        p.saveDailyScore(today.toEpochDay(), 85)
        return p
    }

    @Test fun dashboard() { assumeTrue(!tableOnly); val p = seeded(); shoot("dashboard") { DashboardScreen(p, onBack = {}, today = today) } }
    @Test fun badges() { assumeTrue(!tableOnly); val p = seeded(); shoot("badges") { BadgesScreen(p) {} } }
    @Test fun weekly() { assumeTrue(!tableOnly); val p = seeded(); shoot("weekly") { WeeklyScreen(p, onBack = {}, today = today) } }
    @Test fun quickDrill() { assumeTrue(!tableOnly); shoot("quickdrill") { QuickDrillScreen(prefs(), onBack = {}, autoStart = true) } }
    @Test fun hapticDrill() { assumeTrue(!tableOnly); shoot("haptic") { HapticDrillScreen(prefs(), onBack = {}) } }
    @Test fun deckEstimate() { assumeTrue(!tableOnly); shoot("deckestimate") { DeckEstimateScreen(prefs(), onBack = {}, seed = 5) } }
    @Test fun kelly() { assumeTrue(!tableOnly); shoot("kelly") { KellyScreen(prefs()) {} } }
    @Test fun career() {
        assumeTrue(!tableOnly)
        val p = prefs(); p.career = CareerProgress(2, 31, 30, 4, 4)
        shoot("career") { CareerScreen(p, onBack = {}) {} }
    }
    @Test fun daily() { assumeTrue(!tableOnly); val p = seeded(); shoot("daily") { DailyChallengeScreen(p, onBack = {}, today = today) } }
    @Test fun dailyHand() { assumeTrue(!tableOnly); shoot("dailyhand") { DailyChallengeScreen(prefs(), onBack = {}, today = today, startStage = 3) } }
    @Test fun settingsProfiles() { assumeTrue(!tableOnly); shoot("settings4") { SettingsScreen(prefs { CasinoProfiles.ALL[1].apply(it) }) {} } }
    @Test fun widget() {
        assumeTrue(!tableOnly)
        val p = prefs(); p.addPoints(20)
        shoot("widget") {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx -> com.hilotrainer.DrillWidget.views(ctx).apply(ctx, android.widget.FrameLayout(ctx)) },
                modifier = Modifier.padding(24.dp).size(300.dp, 150.dp),
            )
        }
    }

    @Test fun shareCard() {
        assumeTrue(!tableOnly && tag == "cover")
        val bmp = renderShareCard(seeded())
        File(outDir, "sharecard.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---- At the table ----
    @Test fun dealerCheck() {
        val v = vm(prefs { it.copy(dealerErrors = true, quizEvery = 0, bots = 2) }).apply { dealerErrorChance = 100 }
        var g = 0
        while (v.dealerCheck == null && g++ < 60) round(v)
        shoot("dealercheck") { PlayScreen(v) {} }
    }
    @Test fun flatBet() {
        val v = vm(prefs { it.copy(coverPlay = true, pitBackoff = true, quizEvery = 0, confirmMistakes = false, gradeBets = false, bots = 2) })
        var g = 0
        while (!v.flatBetOnly && g++ < 30) { v.clearBet(); v.addChip(if (g % 2 == 0) 100 else 10); round(v) }
        shoot("flatbet") { PlayScreen(v) {} }
    }
    @Test fun session() {
        val v = vm(prefs { it.copy(quizEvery = 2, bots = 2) })
        v.startSession()
        repeat(6) { round(v); v.advanceSession(4 * 60_000L) }
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        shoot("session") { PlayScreen(v) {} }
    }
    @Test fun sessionReport() {
        assumeTrue(!tableOnly)
        val v = vm(prefs { it.copy(quizEvery = 1, bots = 1) })
        v.startSession()
        repeat(24) { v.clearBet(); v.addChip(if (it % 3 == 0) 50 else 10); round(v); v.advanceSession(2 * 60_000L + 30_000L) }
        shoot("sessionreport") { SessionReport(v, Modifier.fillMaxSize().padding(16.dp)) }
    }
    @Test fun midShoe() {
        val v = vm(prefs { it.copy(bots = 2) })
        v.joinMidShoe()
        shoot("midshoe") { PlayScreen(v) {} }
    }
    @Test fun leftyPlay() {
        val v = vm(prefs { it.copy(leftHanded = true, bots = 2, quizEvery = 0) })
        repeat(5) { round(v) }
        shoot("lefty") { PlayScreen(v) {} }
    }
    @Test fun careerTable() {
        val p = prefs()
        val v = vm(p)
        v.startCareer(2)
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        shoot("careertable") { PlayScreen(v) {} }
    }
}

@Config(sdk = [34], qualifiers = "w411dp-h820dp-xxhdpi")
class CoverV6Shots : V6ShotBase() { override val tag = "cover" }

@Config(sdk = [34], qualifiers = "w832dp-h700dp-xhdpi")
class InnerV6Shots : V6ShotBase() { override val tag = "inner" }

/** The inner screen turned sideways. */
@Config(sdk = [34], qualifiers = "w1000dp-h620dp-xhdpi")
class LandscapeV6Shots : V6ShotBase() { override val tag = "landscape"; override val tableOnly = true }
