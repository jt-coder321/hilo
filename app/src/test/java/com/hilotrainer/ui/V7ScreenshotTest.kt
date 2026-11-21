package com.hilotrainer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/** Version 7: the new home screen, hubs and the realistic table. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class V7ShotBase {
    @get:Rule val rule = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    abstract val tag: String
    open val tableOnly = false
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
        p.tourSeen = true
        return p
    }

    private fun vm(p: Prefs) = PlayViewModel(p).apply { instant = true }

    private fun dealUntil(v: PlayViewModel, cond: (PlayViewModel) -> Boolean) {
        repeat(400) {
            v.deal()
            if (v.state.phase == Phase.INSURANCE) v.insurance(false)
            if (cond(v)) return
            while (v.state.phase == Phase.PLAYER) v.act(v.state.advice!!.action)
            if (v.quiz != null) { v.submitRunning(); v.submitTrue() }
        }
    }

    private fun seeded(): Prefs {
        val p = prefs()
        val today = LocalDate.now()
        for (d in 0 until 6) p.track(points = 60, decisions = 60, correct = 55, today = today.minusDays(d.toLong()))
        p.coursePassed = 4
        p.updateStats { it.copy(handsPlayed = 300, decisions = 700, correctDecisions = 660, countQuizzes = 60, countCorrect = 52) }
        return p
    }

    @Test fun home() { assumeTrue(!tableOnly); val p = seeded(); shoot("home7") { HomeScreen(p) {} } }
    @Test fun homeNew() { assumeTrue(!tableOnly); shoot("home7new") { HomeScreen(prefs()) {} } }
    @Test fun hubTrain() { assumeTrue(!tableOnly); shoot("hub-train") { HubScreen(Hubs.TRAIN, prefs(), {}) {} } }
    @Test fun hubLearn() { assumeTrue(!tableOnly); shoot("hub-learn") { HubScreen(Hubs.LEARN, prefs(), {}) {} } }
    @Test fun hubModes() { assumeTrue(!tableOnly); shoot("hub-modes") { HubScreen(Hubs.MODES, prefs(), {}) {} } }
    @Test fun hubProgress() { assumeTrue(!tableOnly); shoot("hub-progress") { HubScreen(Hubs.PROGRESS, prefs(), {}) {} } }

    @Test fun settingsMenu() { assumeTrue(!tableOnly); shoot("settings-menu") { SettingsScreen(prefs()) {} } }
    @Test fun settingsRules() { assumeTrue(!tableOnly); shoot("settings-rules") { SettingsScreen(prefs(), SettingsPage.RULES) {} } }
    @Test fun settingsTable() { assumeTrue(!tableOnly); shoot("settings-table") { SettingsScreen(prefs(), SettingsPage.TABLE) {} } }
    @Test fun settingsCoaching() { assumeTrue(!tableOnly); shoot("settings-coaching") { SettingsScreen(prefs(), SettingsPage.COACHING) {} } }
    @Test fun settingsControls() { assumeTrue(!tableOnly); shoot("settings-controls") { SettingsScreen(prefs(), SettingsPage.CONTROLS) {} } }
    @Test fun settingsData() { assumeTrue(!tableOnly); shoot("settings-data") { SettingsScreen(prefs(), SettingsPage.DATA) {} } }

    // ---- At the table ----
    private fun distractedTable(e: com.hilotrainer.engine.Distractions.Event): PlayViewModel {
        val v = vm(prefs { it.copy(bots = 3, quizEvery = 0, distractions = true) })
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        v.runDistractions(listOf(e))
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        return v
    }
    @Test fun distractQuestion() {
        val v = distractedTable(com.hilotrainer.engine.Distractions.Event(0, com.hilotrainer.engine.Distractions.Kind.QUESTION, "Linda", "Quick, what's 7 times 8? I'm splitting the tip.", listOf("54", "56", "63"), 1, durationMs = 0))
        shoot("distract-question") { PlayScreen(v) {} }
    }
    @Test fun distractTalk() {
        val v = distractedTable(com.hilotrainer.engine.Distractions.Event(0, com.hilotrainer.engine.Distractions.Kind.NUMBERS, "Player beside you", "I'm down \$340 tonight. No, wait, \$380.", x = 0.2f, y = 0.32f))
        shoot("distract-talk") { PlayScreen(v) {} }
    }
    @Test fun distractWalkBy() {
        val v = distractedTable(com.hilotrainer.engine.Distractions.Event(0, com.hilotrainer.engine.Distractions.Kind.WALK_BY, "A server with a tray", y = 0.45f, durationMs = 1500))
        shoot("distract-walkby") { PlayScreen(v) {} }
    }

    @Test fun buyIn() {
        val v = vm(prefs { it.copy(bots = 2) })
        v.askBuyIn()
        shoot("buyin") { PlayScreen(v) {} }
    }
    @Test fun flashedHole() {
        val v = vm(prefs { it.copy(bots = 2, quizEvery = 0) }).apply { flashOverride = 100 }
        dealUntil(v) { it.holeFlashShowing }
        shoot("flash") { PlayScreen(v) {} }
    }
    @Test fun pitBoss() {
        val v = vm(prefs { it.copy(bots = 3, coverPlay = true, quizEvery = 0) })
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        v.forcePitWatch()
        shoot("pitboss") { PlayScreen(v) {} }
    }
    @Test fun roundOver() {
        val v = vm(prefs { it.copy(bots = 3, quizEvery = 0, cardBack = 2) })
        dealUntil(v) { it.state.phase == Phase.ROUND_OVER && (it.roundNet ?: 0) > 0 }
        shoot("payout") { PlayScreen(v) {} }
    }
    @Test fun seated() {
        val v = vm(prefs { it.copy(bots = 2, seatedView = true, cardBack = 1, quizEvery = 0) })
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        shoot("seated") { PlayScreen(v) {} }
    }
    @Test fun signalsOnly() {
        val v = vm(prefs { it.copy(bots = 1, signals = 2, cardBack = 3, quizEvery = 0) })
        dealUntil(v) { it.state.phase == Phase.PLAYER }
        shoot("signals") { PlayScreen(v) {} }
    }
    @Test fun cutPrompt() {
        assumeTrue(!tableOnly)
        val v = vm(prefs())
        v.shuffleStage = 2
        shoot("cut") { Box(Modifier.fillMaxSize().feltBackground(), contentAlignment = Alignment.Center) { ShuffleStages(v) } }
    }
    @Test fun colorUp() {
        val v = vm(prefs { it.copy(bots = 2, quizEvery = 0) })
        dealUntil(v) { it.state.phase == Phase.ROUND_OVER }
        v.tipDealer(5)
        v.colorUp()
        shoot("colorup") { PlayScreen(v) {} }
    }
}

@Config(sdk = [34], qualifiers = "w411dp-h820dp-xxhdpi")
class CoverV7Shots : V7ShotBase() { override val tag = "cover" }

@Config(sdk = [34], qualifiers = "w832dp-h700dp-xhdpi")
class InnerV7Shots : V7ShotBase() { override val tag = "inner" }

@Config(sdk = [34], qualifiers = "w1000dp-h620dp-xhdpi")
class LandscapeV7Shots : V7ShotBase() { override val tag = "landscape"; override val tableOnly = true }
