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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders key screens to PNGs in app/build/screenshots for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class ScreenshotBase {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    abstract val tag: String
    private val outDir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }

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

    private fun dealUntil(vm: PlayViewModel, cond: (PlayViewModel) -> Boolean) {
        repeat(400) {
            vm.deal()
            if (vm.state.phase == Phase.INSURANCE) vm.insurance(false)
            if (cond(vm)) return
            while (vm.state.phase == Phase.PLAYER) vm.act(vm.state.advice!!.action)
            if (vm.quiz != null) { vm.submitRunning(); vm.submitTrue() }
        }
    }

    @Test fun home() {
        val p = prefs()
        p.tourSeen = true
        repeat(30) { p.addPoints(1) }
        shoot("home") { HomeScreen(p) {} }
    }

    @Test fun tour() {
        val p = prefs()
        p.tourSeen = false
        shoot("tour") { HomeScreen(p) {} }
    }

    @Test fun peek() {
        val v = vm(prefs { it.copy(bots = 2) })
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        v.togglePeek()
        shoot("peek") { PlayScreen(v) {} }
    }

    @Test fun audit() {
        val v = vm(prefs { it.copy(quizEvery = 0) })
        repeat(3) {
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(false)
            while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        }
        shoot("audit") { CountAudit(v, Modifier.fillMaxSize()) }
    }

    @Test fun report() {
        val v = vm(prefs { it.copy(quizEvery = 0, confirmMistakes = false) })
        repeat(12) {
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(false)
            while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        }
        shoot("report") { SessionReport(v, Modifier.fillMaxSize()) }
    }

    @Test fun trayDrill() = shoot("traydrill") { TrayDrillScreen(prefs(), onBack = {}) }

    @Test fun betting() {
        val v = vm(prefs())
        v.addChip(50); v.addChip(10)
        shoot("betting") { PlayScreen(v) {} }
    }

    @Test fun playMidHand() {
        val v = vm(prefs { it.copy(showHints = true, showRunningCount = true, confirmMistakes = false, surrender = true) })
        dealUntil(v) { it.state.phase == Phase.PLAYER && it.state.hands[0].cards.size == 2 }
        val wrong = if (v.state.advice!!.action == Action.STAND) Action.HIT else Action.STAND
        if (wrong == Action.HIT) {
            v.act(Action.HIT)
        }
        shoot("play") { PlayScreen(v) {} }
    }

    @Test fun split() {
        val v = vm(prefs { it.copy(confirmMistakes = false, bots = 3) })
        dealUntil(v) { it.state.phase == Phase.PLAYER && it.state.options.canSplit }
        v.act(Action.SPLIT)
        if (v.state.phase == Phase.PLAYER && v.state.options.canSplit) v.act(Action.SPLIT)
        shoot("split") { PlayScreen(v) {} }
    }

    @Test fun quiz() {
        val v = vm(prefs())
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        shoot("quiz") { PlayScreen(v) {} }
    }

    @Test fun coachLog() {
        val v = vm(prefs { it.copy(confirmMistakes = false, quizEvery = 0) })
        repeat(6) {
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(true)
            while (v.state.phase == Phase.PLAYER) v.act(if (it % 2 == 0) Action.STAND else v.state.advice!!.action)
        }
        shoot("coach") { CoachColumn(v, Modifier.fillMaxSize()) }
    }

    @Test fun chart() = shoot("chart") { ChartScreen(prefs()) {} }
    @Test fun countDrill() = shoot("countdrill") { CountDrillScreen(prefs(), onBack = {}) }
    @Test fun strategyDrill() = shoot("strategydrill") { StrategyDrillScreen(prefs { it.copy(deviations = true) }, onBack = {}) }
    @Test fun settings() = shoot("settings") { SettingsScreen(prefs()) {} }

    // Version 4 screens
    @Test fun casinoTest() = shoot("casinotest") { CasinoTestScreen(prefs(), onBack = {}) }
    @Test fun speedLadder() {
        val p = prefs()
        p.ladderRung = 3
        p.updateStats { it.copy(ladderBest = 4) }
        shoot("ladder") { SpeedLadderScreen(p, onBack = {}) }
    }
    @Test fun pairDrill() = shoot("pairdrill") { PairDrillScreen(prefs(), onBack = {}) }
    @Test fun review() {
        val p = prefs()
        listOf("H16v10", "S18v9", "P8vA", "H12v3").forEach { p.reviewMissed(it, 0L) }
        shoot("review") { ReviewScreen(p, onBack = {}, now = { 1000L }) }
    }
    @Test fun coverDrill() = shoot("coverdrill") { CoverDrillScreen(prefs(), onBack = {}) }
    @Test fun bankroll() = shoot("bankroll") { BankrollScreen(prefs()) {} }
    @Test fun progress() {
        val p = prefs()
        val today = java.time.LocalDate.now()
        val r = kotlin.random.Random(4)
        for (d in 0 until 30) {
            if (d % 4 == 3) continue
            val dec = r.nextInt(20, 60)
            val cnt = r.nextInt(5, 20)
            val acc = 0.7 + d / 30.0 * 0.0 + (29 - d) / 29.0 * 0.25
            p.track(dec, (dec * acc).toInt(), cnt, (cnt * (acc - 0.05)).toInt(), r.nextInt(20, 90), today.minusDays(d.toLong()))
        }
        shoot("progress") { ProgressScreen(p) {} }
    }
    @Test fun koPlay() {
        val v = vm(prefs { it.copy(systemChoice = 1, showRunningCount = true, bots = 2) })
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        v.togglePeek()
        shoot("koplay") { PlayScreen(v) {} }
    }
    @Test fun distraction() {
        val v = vm(prefs { it.copy(distractions = true, coverPlay = true, bots = 2, quizEvery = 0) })
        v.addChip(10)
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        v.distraction = Distraction("Server: \"Can I get you something to drink?\"", needsAnswer = true)
        shoot("distraction") { PlayScreen(v) {} }
    }
    @Test fun heat() {
        val v = vm(prefs { it.copy(coverPlay = true, quizEvery = 0, confirmMistakes = false, gradeBets = false) })
        repeat(3) {
            v.clearBet(); v.addChip(10)
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(false)
            while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
            v.clearBet(); v.addChip(100)
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(false)
            while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
        }
        shoot("heat") { PlayScreen(v) {} }
    }
    @Test fun settingsRules() = shoot("settings2") {
        val p = prefs { it.copy(sixToFive = true, doubleAfterSplit = false, systemChoice = 2, dealSpeed = 3) }
        SettingsScreen(p) {}
    }

    // ---------- v5 ----------
    @Test fun course() {
        val p = prefs(); p.coursePassed = 3
        shoot("course") { CourseScreen(p, onBack = {}) }
    }
    @Test fun indexChart() = shoot("indexchart") { IndexChartScreen(prefs { it.copy(fullIndices = true) }) {} }
    private fun seededErrors(): Prefs {
        val p = prefs()
        val r = kotlin.random.Random(7)
        val keys = listOf("H16v10", "H12v3", "S18v9", "P9v7", "H11vA", "S17v2", "H15v10", "P2v7", "H13v2", "H9v3")
        repeat(240) {
            val k = keys[r.nextInt(keys.size)]
            val bad = r.nextDouble() < (0.08 + keys.indexOf(k) * 0.02 + if (k == "H16v10" || k == "S18v9") 0.35 else 0.0)
            p.recordPlay(k, r.nextInt(-3, 7).toDouble(), !bad)
        }
        return p
    }
    @Test fun heatMap() { val p = seededErrors(); shoot("heatmap") { HeatMapScreen(p, onBack = {}) } }
    @Test fun weakSpots() { val p = seededErrors(); shoot("weakspots") { WeakSpotScreen(p, onBack = {}) } }
    @Test fun sideBets() = shoot("sidebets") { SideBetsScreen(prefs()) {} }
    @Test fun backCount() {
        val v = vm(prefs { it.copy(bots = 3, quizEvery = 0) })
        v.startBackCount()
        shoot("backcount") { PlayScreen(v) {} }
    }
    @Test fun teamSignal() {
        val v = vm(prefs { it.copy(bots = 2, quizEvery = 0) })
        v.startTeam()
        v.callBigPlayer()
        shoot("teamsignal") { PlayScreen(v) {} }
    }
    @Test fun realistic() {
        val v = vm(prefs { it.copy(bots = 4, realisticPlayers = true, quizEvery = 0, perfectPairs = 1) })
        dealUntil(v) { it.state.botMistakes.isNotEmpty() && it.state.phase == Phase.PLAYER }
        shoot("realistic") { PlayScreen(v) {} }
    }
    @Test fun flex() {
        val v = vm(prefs { it.copy(bots = 2, showRunningCount = true) })
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        shoot("flex") {
            val h = rule.activity.window.decorView.height.toFloat()
            androidx.compose.runtime.CompositionLocalProvider(LocalFold provides FoldInfo(true, h / 2f, 0f)) { PlayScreen(v) {} }
        }
    }
    @Test fun voiceQuiz() {
        val v = vm(prefs { it.copy(voice = true, quizEvery = 1) })
        dealUntil(v) { it.quiz != null }
        shoot("voicequiz") { PlayScreen(v) {} }
    }
    @Test fun settingsV5() = shoot("settings3") {
        SettingsScreen(prefs { it.copy(fullIndices = true, realisticPlayers = true, voice = true, shuffleChoice = 2, perfectPairs = 1) }) {}
    }
}

@Config(sdk = [34], qualifiers = "w411dp-h820dp-xxhdpi")
class CoverScreenShots : ScreenshotBase() { override val tag = "cover" }

@Config(sdk = [34], qualifiers = "w832dp-h700dp-xhdpi")
class InnerScreenShots : ScreenshotBase() { override val tag = "inner" }
