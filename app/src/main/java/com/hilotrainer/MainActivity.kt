package com.hilotrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import com.hilotrainer.ui.MistakesScreen
import com.hilotrainer.ui.SoundFx
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hilotrainer.ui.ChartScreen
import com.hilotrainer.ui.CountDrillScreen
import com.hilotrainer.ui.HiLoTheme
import com.hilotrainer.ui.HomeScreen
import com.hilotrainer.ui.LearnScreen
import com.hilotrainer.ui.PlayScreen
import com.hilotrainer.ui.PlayViewModel
import com.hilotrainer.ui.Prefs
import com.hilotrainer.ui.Screen
import com.hilotrainer.ui.SettingsScreen
import com.hilotrainer.ui.StatsScreen
import com.hilotrainer.ui.StrategyDrillScreen
import com.hilotrainer.ui.TrueCountDrillScreen

class MainActivity : ComponentActivity() {
    private var sound: SoundFx? = null
    private var open: (Screen) -> Unit = {}
    /** Screen asked for by the home screen widget. */
    private val widgetOpen = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        widgetOpen.value = intent.getStringExtra(DrillWidget.EXTRA_OPEN)
    }

    override fun onStop() {
        super.onStop()
        DrillWidget.refresh(applicationContext)
    }

    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false

    override fun onDestroy() {
        tts?.shutdown()
        sound?.release()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(applicationContext)
        widgetOpen.value = intent?.getStringExtra(DrillWidget.EXTRA_OPEN)
        sound = SoundFx(applicationContext)
        // The dealer's voice. Uses the phone's own text-to-speech engine.
        tts = try {
            android.speech.tts.TextToSpeech(applicationContext) { status ->
                ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS
                if (ttsReady) tts?.setSpeechRate(1.05f)
            }
        } catch (e: Exception) { null }
        setContent {
            // Flex mode: watch for the Fold being half open with a horizontal hinge (tabletop posture).
            val fold by androidx.compose.runtime.produceState(com.hilotrainer.ui.FoldInfo()) {
                androidx.window.layout.WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { info ->
                        val f = info.displayFeatures.filterIsInstance<androidx.window.layout.FoldingFeature>().firstOrNull()
                        value = if (f != null && f.state == androidx.window.layout.FoldingFeature.State.HALF_OPENED &&
                            f.orientation == androidx.window.layout.FoldingFeature.Orientation.HORIZONTAL
                        ) com.hilotrainer.ui.FoldInfo(true, f.bounds.top.toFloat(), f.bounds.height().toFloat()) else com.hilotrainer.ui.FoldInfo()
                    }
            }
            androidx.compose.runtime.CompositionLocalProvider(com.hilotrainer.ui.LocalFold provides fold) {
            HiLoTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
                val playVm: PlayViewModel = viewModel { PlayViewModel(prefs) }
                val casualVm: com.hilotrainer.ui.CasualViewModel = viewModel { com.hilotrainer.ui.CasualViewModel(com.hilotrainer.ui.CasualStore(applicationContext)) }
                val view = LocalView.current
                val fx = sound!!
                fx.view = view
                playVm.fx = { e ->
                    fx.soundOn = playVm.settings.sound
                    fx.hapticsOn = playVm.settings.haptics
                    fx.play(e)
                }
                casualVm.fx = { e ->
                    fx.soundOn = casualVm.settings.sound
                    fx.hapticsOn = casualVm.settings.haptics
                    fx.play(e)
                }
                playVm.say = { text ->
                    if (ttsReady) tts?.let { it.setPitch(1f); it.setSpeechRate(1.05f); it.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "dealer${System.nanoTime()}") }
                }
                // Other people at the table, each in a different random voice. They talk over the
                // dealer (QUEUE_FLUSH), the way table chatter cuts in.
                playVm.talk = { text, pitch, rate ->
                    if (ttsReady) tts?.let { it.setPitch(pitch); it.setSpeechRate(rate); it.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "talk${System.nanoTime()}") }
                }
                // Back goes to the hub you came from, then home.
                var lastHub by rememberSaveable { mutableStateOf<Screen?>(null) }
                val home = {
                    val hub = lastHub
                    screen = if (com.hilotrainer.ui.Hubs.isHub(screen) || hub == null) { lastHub = null; Screen.HOME } else hub
                }
                var quickAuto by remember { mutableStateOf(false) }
                val asked = widgetOpen.value
                androidx.compose.runtime.LaunchedEffect(asked) {
                    if (asked == Screen.QUICK_DRILL.name) { quickAuto = true; screen = Screen.QUICK_DRILL }
                    widgetOpen.value = null
                }
                open = { next ->
                    when (next) {
                        Screen.BACK_COUNT, Screen.TEAM_PLAY -> {
                            playVm.refreshSettings()
                            if (playVm.mode != com.hilotrainer.ui.TableMode.NORMAL) playVm.stopMode()
                            if (next == Screen.BACK_COUNT) playVm.startBackCount() else playVm.startTeam()
                            screen = Screen.PLAY
                        }
                        Screen.PLAY -> { playVm.stopCareer(); playVm.refreshSettings(); playVm.askBuyIn(); screen = next }
                        Screen.SESSION -> {
                            playVm.stopCareer(); playVm.refreshSettings()
                            if (playVm.mode != com.hilotrainer.ui.TableMode.NORMAL) playVm.stopMode()
                            playVm.startSession(); screen = Screen.PLAY
                        }
                        Screen.MID_SHOE -> {
                            playVm.stopCareer(); playVm.refreshSettings()
                            if (playVm.mode != com.hilotrainer.ui.TableMode.NORMAL) playVm.stopMode()
                            playVm.joinMidShoe(); screen = Screen.PLAY
                        }
                        Screen.QUICK_DRILL -> { quickAuto = false; screen = next }
                        else -> screen = next
                    }
                    if (com.hilotrainer.ui.Hubs.isHub(next)) lastHub = next
                }
                BackHandler(enabled = screen != Screen.HOME) { home() }
                Box(Modifier.fillMaxSize().background(com.hilotrainer.ui.Ink).safeDrawingPadding()) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(prefs) { next -> lastHub = null; open(next) }
                        Screen.HUB_TRAIN, Screen.HUB_LEARN, Screen.HUB_MODES, Screen.HUB_PROGRESS ->
                            com.hilotrainer.ui.HubScreen(com.hilotrainer.ui.Hubs.of(screen)!!, prefs, home) { next -> open(next) }
                        Screen.PLAY -> PlayScreen(playVm, home)
                        Screen.JUST_PLAY -> com.hilotrainer.ui.JustPlayScreen(casualVm, home)
                        Screen.ROGUE_RUN -> com.hilotrainer.ui.RunScreen(casualVm.store, home, fx = { e, pitch ->
                            fx.soundOn = casualVm.store.runSound
                            fx.hapticsOn = casualVm.settings.haptics
                            fx.play(e, pitch)
                        })
                        Screen.COUNT_DRILL -> CountDrillScreen(prefs, home, playVm.fx)
                        Screen.TRUE_DRILL -> TrueCountDrillScreen(prefs, home, playVm.fx)
                        Screen.STRATEGY_DRILL -> StrategyDrillScreen(prefs, home, playVm.fx)
                        Screen.CHART -> ChartScreen(prefs, home)
                        Screen.LEARN -> LearnScreen(home)
                        Screen.STATS -> StatsScreen(prefs, home)
                        Screen.SETTINGS -> SettingsScreen(prefs, onBack = home)
                        Screen.MISTAKES -> MistakesScreen(prefs, home)
                        Screen.TRAY_DRILL -> com.hilotrainer.ui.TrayDrillScreen(prefs, home, playVm.fx)
                        Screen.CASINO_TEST -> com.hilotrainer.ui.CasinoTestScreen(prefs, home, playVm.fx)
                        Screen.SPEED_LADDER -> com.hilotrainer.ui.SpeedLadderScreen(prefs, home, playVm.fx)
                        Screen.PAIR_DRILL -> com.hilotrainer.ui.PairDrillScreen(prefs, home, playVm.fx)
                        Screen.REVIEW -> com.hilotrainer.ui.ReviewScreen(prefs, home, playVm.fx)
                        Screen.COVER_DRILL -> com.hilotrainer.ui.CoverDrillScreen(prefs, home, playVm.fx)
                        Screen.BANKROLL -> com.hilotrainer.ui.BankrollScreen(prefs, home)
                        Screen.PROGRESS -> com.hilotrainer.ui.ProgressScreen(prefs, home)
                        Screen.COURSE -> com.hilotrainer.ui.CourseScreen(prefs, home, playVm.fx) { open(it) }
                        Screen.INDEX_CHART -> com.hilotrainer.ui.IndexChartScreen(prefs, home)
                        Screen.HEAT_MAP -> com.hilotrainer.ui.HeatMapScreen(prefs, home) { screen = Screen.WEAK_SPOTS }
                        Screen.WEAK_SPOTS -> com.hilotrainer.ui.WeakSpotScreen(prefs, home, playVm.fx)
                        Screen.SIDE_BETS -> com.hilotrainer.ui.SideBetsScreen(prefs, home)
                        Screen.BACK_COUNT, Screen.TEAM_PLAY, Screen.SESSION, Screen.MID_SHOE -> PlayScreen(playVm, home)
                        Screen.DASHBOARD -> com.hilotrainer.ui.DashboardScreen(prefs, home)
                        Screen.BADGES -> com.hilotrainer.ui.BadgesScreen(prefs, home)
                        Screen.WEEKLY -> com.hilotrainer.ui.WeeklyScreen(prefs, home)
                        Screen.QUICK_DRILL -> com.hilotrainer.ui.QuickDrillScreen(prefs, home, playVm.fx, autoStart = quickAuto)
                        Screen.HAPTIC_DRILL -> com.hilotrainer.ui.HapticDrillScreen(prefs, home, playVm.fx)
                        Screen.DECK_ESTIMATE -> com.hilotrainer.ui.DeckEstimateScreen(prefs, home, playVm.fx)
                        Screen.KELLY -> com.hilotrainer.ui.KellyScreen(prefs, home)
                        Screen.CAREER -> com.hilotrainer.ui.CareerScreen(prefs, home) { lvl -> playVm.startCareer(lvl); screen = Screen.PLAY }
                        Screen.DAILY -> com.hilotrainer.ui.DailyChallengeScreen(prefs, home, playVm.fx)
                    }
                }
            }
            }
        }
    }
}
