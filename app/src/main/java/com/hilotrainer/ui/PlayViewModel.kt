package com.hilotrainer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.Advice
import com.hilotrainer.engine.BasicStrategy
import com.hilotrainer.engine.BlackjackGame
import com.hilotrainer.engine.CountSystem
import com.hilotrainer.engine.CoverPlay
import com.hilotrainer.engine.Review
import com.hilotrainer.engine.BackCount
import com.hilotrainer.engine.BotStyle
import com.hilotrainer.engine.ErrorMap
import com.hilotrainer.engine.ShuffleStyle
import com.hilotrainer.engine.TeamSignals
import com.hilotrainer.engine.SpeedLadder
import com.hilotrainer.engine.Decision
import com.hilotrainer.engine.DecisionKind
import com.hilotrainer.engine.EventKind
import com.hilotrainer.engine.GameState
import com.hilotrainer.engine.Outcome
import com.hilotrainer.engine.Phase
import com.hilotrainer.engine.TrueCount
import com.hilotrainer.engine.handTotal
import com.hilotrainer.engine.upValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

enum class QuizStage { RUNNING, TRUE, DONE }

data class QuizState(
    val stage: QuizStage = QuizStage.RUNNING,
    val runningAnswer: Int = 0,
    val trueAnswer: Int = 0,
    val runningCorrect: Boolean? = null,
    val trueCorrect: Boolean? = null,
    val actualRunning: Int = 0,
    val actualTrue: Double = 0.0,
    val decksRemaining: Double = 0.0,
)

enum class BannerTone { GOOD, BAD, INFO, COUNT }

enum class BannerAction { NONE, AUDIT }

data class Banner(
    val title: String,
    val detail: String,
    val tone: BannerTone,
    val action: BannerAction = BannerAction.NONE,
    val id: Long = System.nanoTime(),
)

class PlayViewModel(private val prefs: Prefs) : ViewModel() {
    var settings by mutableStateOf(prefs.loadSettings())
        private set
    private var game = newGame(settings, 1000)

    var state by mutableStateOf(game.state())
        private set

    /** Cards with seq below this are on the table; the rest are still being dealt. */
    var visibleSeq by mutableIntStateOf(state.nextSeq)
        private set
    var shuffling by mutableStateOf(false)
        private set
    var bet by mutableIntStateOf(10)
        private set
    private var lastBet = 10
    var quiz by mutableStateOf<QuizState?>(null)
        private set
    var pendingMistake by mutableStateOf<Pair<Action, Advice>?>(null)
        private set
    var banner by mutableStateOf<Banner?>(null)
        private set
    var peeking by mutableStateOf(false)
        private set
    var roundNet by mutableStateOf<Int?>(null)
        private set

    val log = mutableStateListOf<Decision>()
    var sessionDecisions by mutableIntStateOf(0); private set
    var sessionCorrect by mutableIntStateOf(0); private set
    var sessionQuizzes by mutableIntStateOf(0); private set
    var sessionQuizCorrect by mutableIntStateOf(0); private set
    var streak by mutableIntStateOf(0); private set
    var hintsThisSession by mutableIntStateOf(0); private set

    /** Cards dealt before this seq were already on the table when the current playback began. */
    var animateFromSeq by mutableIntStateOf(state.nextSeq)
        private set

    /** Hook for sounds and vibration, set by the screen. */
    var fx: (Fx) -> Unit = {}
    /** Skip animations (used by screenshot tests). */
    var instant = false

    private var rounds = 0
    private var playback: Job? = null

    private fun newGame(s: AppSettings, bankroll: Int) = BlackjackGame(
        rules = s.rules(), startingBankroll = bankroll, botCount = s.bots,
        useDeviations = s.deviations, gradeBets = s.gradeBets,
        ramp = s.ramp, wongOutAt = s.wongOutAt, system = s.system,
        fullIndices = s.fullIndices, shuffleStyle = s.shuffleStyle,
        botStyles = if (s.realisticPlayers) realisticSeats(s.bots) else emptyList(),
    )

    /** A realistic table: every seat but one makes a common mistake. */
    private fun realisticSeats(n: Int): List<BotStyle> {
        val bad = BotStyle.entries.filter { it != BotStyle.BASIC }.shuffled()
        return List(n) { i -> if (i == n - 1 && n > 1) BotStyle.BASIC else bad[i % bad.size] }
    }

    // Back-counting and team play practice.
    var mode by mutableStateOf(TableMode.NORMAL); private set
    /** In back-count and team modes: whether you have a seat (a hand) right now. */
    var seated by mutableStateOf(true); private set
    var teamRole by mutableStateOf(TeamRole.SPOTTER); private set
    var modeCalls by mutableIntStateOf(0); private set
    var modeCorrect by mutableIntStateOf(0); private set
    private var csmNoticeShown = false

    val system: CountSystem get() = settings.system

    // ---------- v7 realism state ----------
    /** Speaks a dealer call out loud. Set by the activity. */
    var say: (String) -> Unit = {}
    var dealer by mutableStateOf(com.hilotrainer.engine.Dealers.ALL[0]); private set
    private var roundsWithDealer = 0
    /** Names and chip stacks of the other players, by seat. */
    val seatNames = mutableStateListOf<String>()
    val seatStacks = mutableStateListOf<Int>()
    /** A short reaction from each seat after the round ("Yes!"), or null. */
    val seatReactions = mutableStateListOf<String?>()
    var reactionId by mutableStateOf(0L); private set
    /** The pit boss is standing behind the dealer. */
    var pitBossWatching by mutableStateOf(false); private set
    /** The pit boss walks past this round. */
    var pitBossWalkId by mutableStateOf(0L); private set
    /** The hole card is showing because the dealer flashed it. */
    var holeFlashShowing by mutableStateOf(false); private set
    /** Set when the dealer checks the hole card for blackjack, to animate the peek. */
    var dealerPeekId by mutableStateOf(0L); private set
    /** Waiting for you to buy in. */
    var needBuyIn by mutableStateOf(false); private set
    var buyInAmount by mutableIntStateOf(1000); private set
    var colorUpText by mutableStateOf<String?>(null); private set
    var tipsThisSession by mutableIntStateOf(0); private set
    /** Shuffle stages: 0 none, 1 riffle, 2 waiting for your cut, 3 burn card. */
    var shuffleStage by mutableIntStateOf(0); internal set
    private var cutDone = false
    /** Shoes dealt at this table, which wears the cards a little. */
    var shoesAtTable by mutableIntStateOf(0); private set

    init {
        dealer = com.hilotrainer.engine.Dealers.next(null)
        syncSeats()
    }

    private fun syncSeats() {
        val n = game.seats
        while (seatNames.size < n) { seatNames.add(com.hilotrainer.engine.Seats.pick(seatNames)); seatStacks.add(kotlin.random.Random.nextInt(25, 90) * 10); seatReactions.add(null) }
        while (seatNames.size > n) { seatNames.removeAt(seatNames.size - 1); seatStacks.removeAt(seatStacks.size - 1); seatReactions.removeAt(seatReactions.size - 1) }
    }

    private fun speak(text: String) { if (settings.dealerVoice && settings.sound) say(text) }

    // ---------- v6 state ----------
    /** Career mode: the level being played, or null for normal play. */
    var careerLevel by mutableStateOf<Int?>(null); private set
    /** Joined this shoe part way through, so the early cards were never seen. */
    var joinedMidShoe by mutableStateOf(false); private set
    /** What the dealer just said, waiting for you to call it right or wrong. */
    var dealerCheck by mutableStateOf<com.hilotrainer.engine.DealerErrors.Check?>(null); private set
    /** The pit has limited you to flat bets at this table. */
    var flatBetOnly by mutableStateOf(false); private set
    /** Expected value versus luck for this session. */
    val ev = com.hilotrainer.engine.EvTracker()
    var evVersion by mutableIntStateOf(0); private set
    private var roundBetTc = 0.0
    private var decisionStartMs = 0L

    // Simulated hour-long session.
    var sessionActive by mutableStateOf(false); private set
    var sessionElapsedMs by mutableStateOf(0L); private set
    var onBreak by mutableStateOf(false); private set
    var breakLeftMs by mutableStateOf(0L); private set
    var breaksTaken by mutableIntStateOf(0); private set
    var sessionDone by mutableStateOf(false); private set
    val sessionBlocks = mutableStateListOf<com.hilotrainer.engine.SessionPlan.Block>()
    private var sessionJob: Job? = null
    private var nextBreakAtMs = 0L
    /** Test hook: advance the session clock without waiting. */
    internal fun advanceSession(ms: Long) { sessionElapsedMs += ms; checkSession() }

    // Table distractions: chatter, chip noise and drink orders.
    var distraction by mutableStateOf<Distraction?>(null); internal set
    private var distractJob: Job? = null

    // Cover play: how much attention your betting is drawing.
    var heat by mutableStateOf(0.0); private set
    var peakHeat by mutableStateOf(0.0); private set
    private var lastUnits = 0
    private var wonLast = false

    // Speed ladder dealing: correct count checks in a row at this rung.
    private var ladderRun = 0
    var ladderRung by mutableIntStateOf(prefs.ladderRung); private set

    // Session report
    val bankrollHistory = mutableStateListOf(1000)
    var sessionStartBankroll = 1000; private set
    var maxTrueCount by mutableStateOf(0.0); private set
    var minTrueCount by mutableStateOf(0.0); private set
    var roundsThisSession by mutableIntStateOf(0); private set
    var sessionBetsOk by mutableIntStateOf(0); private set
    var sessionBets by mutableIntStateOf(0); private set

    // Watch mode: rounds deal themselves and you only count.
    var watching by mutableStateOf(false); private set
    private var watchJob: Job? = null

    // Shot clock
    var clockLeftMs by mutableStateOf(0L); private set
    private var clockJob: Job? = null

    private var peekJob: Job? = null

    val animating: Boolean get() = shuffling || visibleSeq < state.nextSeq

    val visibleRunningCount: Int
        get() = state.runningCount - state.events.filter { it.counted && it.seq >= visibleSeq }.sumOf { it.tag }

    val visibleTrueCount: Double
        get() {
            val hiddenCards = state.events.count { it.kind == EventKind.DEAL && it.seq >= visibleSeq }
            val decks = (state.totalCards - state.cardsDealt + hiddenCards) / 52.0
            return system.trueCount(visibleRunningCount, decks)
        }

    /** Visible decks left, counting cards still being dealt as in the shoe. */
    val visibleDecksRemaining: Double
        get() = (state.totalCards - visibleCardsDealt) / 52.0

    val visibleCardsDealt: Int
        get() = state.cardsDealt - state.events.count { it.kind == EventKind.DEAL && it.seq >= visibleSeq }

    fun refreshSettings() {
        val base = prefs.loadSettings()
        val new = careerLevel?.let { CareerLevels.ALL[it].apply(base) } ?: base
        val tableChanged = new.rules() != settings.rules() || new.bots != settings.bots ||
            new.deviations != settings.deviations || new.gradeBets != settings.gradeBets ||
            new.ramp != settings.ramp || new.wongOut != settings.wongOut || new.systemChoice != settings.systemChoice ||
            new.fullIndices != settings.fullIndices || new.shuffleChoice != settings.shuffleChoice || new.realisticPlayers != settings.realisticPlayers
        settings = new
        if (tableChanged && (state.phase == Phase.BETTING || state.phase == Phase.ROUND_OVER)) {
            game = newGame(new, game.bankroll)
            syncSeats()
            quiz = null
            banner = null
            sync()
            visibleSeq = state.nextSeq
        }
    }

    private fun sync() {
        state = game.state()
    }

    val bettingOpen: Boolean
        get() = (state.phase == Phase.BETTING || state.phase == Phase.ROUND_OVER) && !animating &&
            (quiz == null || quiz?.stage == QuizStage.DONE) && dealerCheck == null && !onBreak

    val canDeal: Boolean get() = bettingOpen && !watching && !needBuyIn && bet in 10..state.bankroll

    private val chipHistory = mutableListOf<Int>()

    fun undoChip() {
        if (!bettingOpen || chipHistory.isEmpty()) return
        bet = (bet - chipHistory.removeAt(chipHistory.size - 1)).coerceAtLeast(0)
        fx(Fx.CHIP)
    }

    fun addChip(v: Int) {
        if (!bettingOpen) return
        val before = bet
        bet = (bet + v).coerceAtMost(state.bankroll - state.bankroll % 10)
        if (bet > before) chipHistory += bet - before
        fx(Fx.CHIP)
    }

    fun clearBet() { if (bettingOpen) { bet = 0; roundNet = null; chipHistory.clear() } }

    fun rebet() {
        if (!bettingOpen) return
        bet = lastBet.coerceAtMost(state.bankroll - state.bankroll % 10); fx(Fx.CHIP)
    }

    fun deal() {
        if (!canDeal) return
        if (mode != TableMode.NORMAL && !seated) return
        if (settings.coverPlay && settings.pitBackoff && pitStepsIn()) return
        val ok = checkStay()
        val missed = banner
        startRound(bet)
        if (!ok) banner = missed
    }

    /** Watch this round without a hand. Cards still count. */
    fun sitOut() {
        if (!bettingOpen || watching) return
        startRound(0)
    }

    private fun startRound(amount: Int) {
        quiz = null
        pendingMistake = null
        roundNet = null
        banner = null
        if (amount > 0) lastBet = amount
        roundBetTc = game.upcomingBetLevel()
        tableLifeBeforeDeal()
        game.deal(amount, if (amount > 0) settings.perfectPairs else 0)
        if (game.state().shuffledThisRound) shoesAtTable++
        for (i in seatReactions.indices) seatReactions[i] = null
        if (state.shuffledThisRound) joinedMidShoe = false
        sync()
        if (game.reshuffledByMachine && !csmNoticeShown) {
            csmNoticeShown = true
            banner = Banner("Continuous shuffler", "Every round's cards go straight back in, so the count resets each hand. Counting can't help here: just play basic strategy.", BannerTone.INFO)
        }
        state.betDecision?.let { record(it) }
        afterDealRealism()
        if (settings.coverPlay && amount > 0) coverCheck(amount / 10)
        if (settings.distractions && !instant) scheduleDistraction()
        visibleSeq = state.roundStartSeq
        play(withShuffle = state.shuffledThisRound)
    }

    private fun play(withShuffle: Boolean = false) {
        playback?.cancel()
        animateFromSeq = visibleSeq
        if (instant) {
            shuffling = false
            visibleSeq = state.nextSeq
            afterPlayback()
            return
        }
        playback = viewModelScope.launch {
            if (withShuffle) {
                shuffling = true
                val start = system.initialCount(settings.decks)
                banner = Banner("New shoe", "Shuffled. The ${system.label} running count starts over at ${signed(start)}. The burn card isn't counted.", BannerTone.INFO)
                speak("Shuffle up.")
                fx(Fx.SHUFFLE)
                if (settings.shuffleStyle == ShuffleStyle.MACHINE) {
                    shuffleStage = 1; delay(700)
                } else {
                    shuffleStage = 1; delay(1500)
                    if (settings.cutCards) {
                        cutDone = false; shuffleStage = 2
                        var waited = 0
                        while (!cutDone && waited < 6000) { delay(100); waited += 100 }
                    }
                }
                shuffleStage = 3; fx(Fx.DEAL); delay(500)
                shuffleStage = 0
                shuffling = false
            }
            val events = state.events.filter { it.seq >= visibleSeq }
            for (e in events) {
                val d = (settings.dealDelayMs(ladderRung) * dealer.pace).toLong()
                delay(if (e.kind == EventKind.FLIP) d + 120 else d)
                visibleSeq = e.seq + 1
                fx(if (e.kind == EventKind.FLIP) Fx.FLIP else Fx.DEAL)
            }
            visibleSeq = state.nextSeq
            afterPlayback()
        }
    }

    private var finishedRoundSeq = -1

    private fun afterPlayback() {
        if (state.phase == Phase.ROUND_OVER && finishedRoundSeq != state.roundStartSeq) {
            finishedRoundSeq = state.roundStartSeq
            roundFinished()
        }
        if (state.phase == Phase.PLAYER) { startClock(); decisionStartMs = System.currentTimeMillis() }
    }

    /** Tap the table to finish the deal animation at once. */
    fun skipAnimation() {
        if (!animating) return
        playback?.cancel()
        shuffling = false
        visibleSeq = state.nextSeq
        afterPlayback()
    }

    private fun startClock() {
        clockJob?.cancel()
        val secs = settings.shotClock
        if (secs <= 0 || watching) { clockLeftMs = 0; return }
        val deadline = System.currentTimeMillis() + secs * 1000L
        clockLeftMs = secs * 1000L
        if (instant) return
        clockJob = viewModelScope.launch {
            while (true) {
                delay(100)
                clockLeftMs = (deadline - System.currentTimeMillis()).coerceAtLeast(0)
                if (clockLeftMs == 0L) {
                    prefs.updateStats { it.copy(slowDecisions = it.slowDecisions + 1) }
                    banner = Banner("Too slow", "A casino dealer won't wait. Aim to decide within $secs seconds.", BannerTone.BAD)
                    fx(Fx.MISTAKE)
                    break
                }
            }
        }
    }

    private fun stopClock() { clockJob?.cancel(); clockLeftMs = 0 }

    fun insurance(take: Boolean) {
        if (animating || state.phase != Phase.INSURANCE) return
        stopClock()
        game.insurance(take)
        sync()
        dealerPeekId = System.nanoTime()
        state.lastDecision?.let { record(it); feedback(it) }
        play()
    }

    fun act(action: Action) {
        if (animating || state.phase != Phase.PLAYER) return
        val advice = state.advice ?: return
        if (settings.confirmMistakes && action != advice.action) {
            pendingMistake = action to advice
            fx(Fx.MISTAKE)
            return
        }
        doAct(action)
    }

    fun confirmMistake() {
        val a = pendingMistake?.first ?: return
        pendingMistake = null
        doAct(a)
    }

    fun takeAdvice() {
        val a = pendingMistake?.second?.action ?: return
        pendingMistake = null
        doAct(a)
    }

    private fun doAct(action: Action) {
        stopClock()
        if (settings.coverPlay && !instant && decisionStartMs > 0) {
            val slow = com.hilotrainer.engine.PitHeat.slowHeat(System.currentTimeMillis() - decisionStartMs)
            if (slow > 0) { heat += slow; if (heat > peakHeat) peakHeat = heat }
        }
        val before = state
        val heatHand = before.hands.getOrNull(before.activeHand)
        val heatUp = before.dealerCards.firstOrNull()
        val heatTc = if (system == CountSystem.HI_LO) before.trueCountExact else null
        val hand = before.hands.getOrNull(before.activeHand)
        val up = before.dealerCards.firstOrNull()
        game.act(action)
        sync()
        state.lastDecision?.let { d ->
            if (heatHand != null && heatUp != null) prefs.recordPlay(ErrorMap.cellKey(heatHand, heatUp), heatTc, d.correct)
            if (!d.correct && !d.isDeviation && hand != null && up != null) {
                Review.keyFor(hand, up)?.let { prefs.reviewMissed(it) }
            }
        }
        state.lastDecision?.let { record(it); feedback(it) }
        play()
    }

    fun hint() {
        val s = state
        hintsThisSession++
        prefs.updateStats { it.copy(hintsUsed = it.hintsUsed + 1) }
        banner = when (s.phase) {
            Phase.PLAYER -> s.advice?.let { a ->
                val basic = s.basicAdvice
                val detail = if (a.isDeviation && basic != null) "${a.reason}. Basic strategy alone would ${basic.action.label.lowercase()}." else a.reason
                Banner("Hint: ${a.action.label}", detail, if (a.isDeviation) BannerTone.COUNT else BannerTone.INFO)
            }
            Phase.INSURANCE -> Banner(
                if (s.insuranceAdvised) "Hint: Insure" else "Hint: No insurance",
                (if (system == CountSystem.HI_LO) "Insure only at true count +3 or higher." else "With ${system.label} the trainer follows basic strategy here: never insure.") +
                    " Exact value for the unseen cards: ${"%+.1f".format(insuranceEvNow() * 100)}%.", BannerTone.INFO,
            )
            else -> {
                val lvl = game.upcomingBetLevel()
                val countText = game.upcomingCountText().replaceFirstChar { it.uppercase() }
                val levelNote = if (system == CountSystem.HI_LO) "" else " (bet level ${signed1(lvl)})"
                val units = TrueCount.suggestedUnits(floor(lvl).toInt(), settings.ramp)
                if (settings.wongOutAt != null && floor(lvl).toInt() <= settings.wongOutAt!!) {
                    Banner("Hint: sit this one out", "$countText$levelNote, at or below your sit-out point of ${settings.wongOutAt}.", BannerTone.INFO)
                } else Banner(
                    "Hint: bet $units unit${if (units > 1) "s" else ""} (${money(units * 10)})",
                    "$countText$levelNote. Your ramp: ${TrueCount.describeRamp(settings.ramp)}. ${kellyNote(lvl)}", BannerTone.INFO,
                )
            }
        }
    }

    /** Tap to show the count for a few seconds; tap again to hide it. */
    fun togglePeek() {
        peekJob?.cancel()
        if (peeking) { peeking = false; return }
        prefs.updateStats { it.copy(peeks = it.peeks + 1) }
        peeking = true
        if (instant) return
        peekJob = viewModelScope.launch { delay(4000); peeking = false }
    }

    fun startWatching() {
        if (watching || !bettingOpen) return
        watching = true
        watchJob = viewModelScope.launch {
            while (watching) {
                while (watching && (animating || (quiz != null && quiz?.stage != QuizStage.DONE))) delay(150)
                if (!watching) break
                delay(if (state.dealerCards.isEmpty()) 200 else 1400L)
                if (!watching || !bettingOpen) continue
                startRound(0)
                prefs.updateStats { it.copy(watchRounds = it.watchRounds + 1) }
            }
        }
    }

    fun stopWatching() {
        watching = false
        watchJob?.cancel()
    }

    fun dismissBanner() { banner = null }

    private fun feedback(d: Decision) {
        if (d.correct) {
            if (d.isDeviation) {
                banner = Banner("Count play!", "${d.chosen} is right here: ${d.reason}.", BannerTone.COUNT)
            } else if (banner?.tone != BannerTone.COUNT) {
                banner = Banner("Good: ${d.chosen}", d.situation, BannerTone.GOOD)
            }
        } else {
            banner = Banner("Mistake: ${d.advised} was right", "${d.situation}. ${d.reason}.", BannerTone.BAD)
            fx(Fx.MISTAKE)
        }
    }

    private fun record(d: Decision) {
        prefs.addPoints(1)
        if (d.kind != DecisionKind.BET) prefs.track(decisions = 1, correct = if (d.correct) 1 else 0)
        if (d.kind == DecisionKind.BET) { sessionBets++; if (d.correct) sessionBetsOk++ }
        log.add(0, d)
        if (log.size > 200) log.removeAt(log.size - 1)
        if (d.kind != DecisionKind.BET) {
            careerLevel?.let { prefs.career = prefs.career.let { c -> c.copy(decisions = c.decisions + 1, correct = c.correct + if (d.correct) 1 else 0) } }
            sessionBlock { it.copy(decisions = it.decisions + 1, correct = it.correct + if (d.correct) 1 else 0) }
            sessionDecisions++
            if (d.correct) { sessionCorrect++; streak++ } else streak = 0
        }
        val ok = if (d.correct) 1 else 0
        prefs.updateStats {
            when (d.kind) {
                DecisionKind.BET -> it.copy(betDecisions = it.betDecisions + 1, betCorrect = it.betCorrect + ok)
                else -> it.copy(
                    decisions = it.decisions + 1, correctDecisions = it.correctDecisions + ok,
                    deviationDecisions = it.deviationDecisions + if (d.isDeviation) 1 else 0,
                    deviationCorrect = it.deviationCorrect + if (d.isDeviation) ok else 0,
                    bestStrategyStreak = maxOf(it.bestStrategyStreak, streak),
                )
            }
        }
        if (!d.correct) prefs.addMistake(MistakeEntry(d.situation, d.chosen, d.advised, d.reason, System.currentTimeMillis()))
    }

    private fun roundFinished() {
        rounds++
        roundsThisSession++
        bankrollHistory.add(state.bankroll)
        val tc = state.trueCountExact
        if (tc > maxTrueCount) maxTrueCount = tc
        if (tc < minTrueCount) minTrueCount = tc
        if (!state.satOut) prefs.updateStats { it.copy(handsPlayed = it.handsPlayed + 1) }
        val net = state.results.sumOf { it.net } + state.insuranceNet + (state.perfectPairsNet ?: 0)
        roundNet = net
        if (!state.satOut) {
            ev.add(state.hands.sumOf { it.bet }, roundBetTc, state.results.sumOf { it.net })
            evVersion++
        }
        if (!state.satOut) wonLast = net > 0
        if (settings.coverPlay) heat = (heat - CoverPlay.COOL_PER_ROUND).coerceAtLeast(0.0)
        val outcomes = state.results.map { it.outcome }
        fx(
            when {
                Outcome.BLACKJACK in outcomes -> Fx.BLACKJACK
                net > 0 -> Fx.WIN
                net < 0 -> Fx.LOSE
                else -> Fx.CHIP
            },
        )
        state.betDecision?.takeIf { !it.correct }?.let {
            if (banner == null || banner?.tone == BannerTone.GOOD) {
                banner = Banner("Bet sizing: ${it.advised} suggested", "${it.situation}, you bet ${it.chosen}.", BannerTone.BAD)
            }
        }
        state.perfectPairsResult?.let { r ->
            if (banner == null || banner?.tone == BannerTone.GOOD) {
                val n = state.perfectPairsNet ?: 0
                banner = Banner("Perfect Pairs: $r", if (n > 0) "Won ${money(n)} on the side bet." else "Lost the ${money(state.perfectPairsBet)} side bet.", if (n > 0) BannerTone.GOOD else BannerTone.INFO)
            }
        }
        if (state.botMistakes.isNotEmpty() && (banner == null || banner?.tone == BannerTone.GOOD)) {
            banner = Banner(state.botMistakes.first(), "Their bad plays change which cards come out, not how you count. Keep counting every card.", BannerTone.INFO)
        }
        if (mode == TableMode.TEAM && teamRole == TeamRole.BIG_PLAYER) teamSignalBanner()
        roundEndRealism()
        if (settings.dealerErrors && !state.satOut && state.results.isNotEmpty() && kotlin.random.Random.nextInt(100) < dealerErrorChance) {
            dealerCheck = com.hilotrainer.engine.DealerErrors.make(state.dealerCards, state.results, state.hands.map { it.bet })
        }
        careerLevel?.let { lvl ->
            val c = prefs.career
            if (c.level == lvl && CareerLevels.ready(c)) {
                prefs.career = CareerProgress(level = lvl + 1)
                banner = Banner("Promoted: ${CareerLevels.ALL[lvl + 1].name}", "You proved yourself at ${CareerLevels.ALL[lvl].name}. The next table is ready in Career mode.", BannerTone.COUNT)
                fx(Fx.BLACKJACK)
            }
        }
        if (bet > state.bankroll) bet = (state.bankroll - state.bankroll % 10).coerceAtLeast(0)
        val every = if (watching) maxOf(1, settings.quizEvery.takeIf { it > 0 } ?: 3) else settings.quizEvery
        if (every > 0 && rounds % every == 0 && !settings.showRunningCount) {
            quiz = QuizState(
                runningAnswer = system.initialCount(settings.decks).coerceAtLeast(-60),
                actualRunning = state.runningCount,
                actualTrue = system.trueCount(state.runningCount, state.decksRemaining),
                decksRemaining = state.decksRemaining,
            )
        }
    }

    /** A spoken answer: fill in the number that was heard and check it. */
    fun voiceAnswer(heard: String) {
        val q = quiz ?: return
        val n = com.hilotrainer.engine.VoiceCount.parse(heard)
        if (n == null) {
            banner = Banner("Didn't catch a number", "Heard \"$heard\". Try \"plus three\" or \"minus two\".", BannerTone.INFO)
            return
        }
        prefs.updateStats { it.copy(voiceAnswers = it.voiceAnswers + 1) }
        if (q.stage == QuizStage.RUNNING) { setRunningAnswer(n.coerceIn(-60, 60)); submitRunning() }
        else if (q.stage == QuizStage.TRUE) { setTrueAnswer(n.coerceIn(-30, 30)); submitTrue() }
    }

    fun setRunningAnswer(v: Int) { quiz = quiz?.copy(runningAnswer = v) }
    fun setTrueAnswer(v: Int) { quiz = quiz?.copy(trueAnswer = v) }

    fun submitRunning() {
        val q = quiz ?: return
        val ok = q.runningAnswer == q.actualRunning
        prefs.addPoints(2)
        sessionQuizzes++
        if (ok) sessionQuizCorrect++
        fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
        val busy = distractedSinceCheck
        distractedSinceCheck = false
        prefs.updateStats {
            val c = it.copy(countQuizzes = it.countQuizzes + 1, countCorrect = it.countCorrect + if (ok) 1 else 0)
            if (!settings.distractions) c
            else if (busy) c.copy(distractChecks = c.distractChecks + 1, distractChecksOk = c.distractChecksOk + if (ok) 1 else 0)
            else c.copy(quietChecks = c.quietChecks + 1, quietChecksOk = c.quietChecksOk + if (ok) 1 else 0)
        }
        lastCheckWasBusy = busy && settings.distractions
        prefs.track(counts = 1, countsOk = if (ok) 1 else 0)
        careerLevel?.let { prefs.career = prefs.career.let { c -> c.copy(counts = c.counts + 1, countsOk = c.countsOk + if (ok) 1 else 0) } }
        sessionBlock { it.copy(counts = it.counts + 1, countsOk = it.countsOk + if (ok) 1 else 0) }
        if (settings.dealSpeed == 3) ladderStep(ok)
        val askTrue = settings.askTrueCount && system.balanced
        quiz = q.copy(runningCorrect = ok, stage = if (askTrue) QuizStage.TRUE else QuizStage.DONE)
        if (!askTrue) quizBanner()
    }

    fun submitTrue() {
        val q = quiz ?: return
        val ok = TrueCount.isAcceptable(q.trueAnswer, q.actualTrue)
        fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
        prefs.updateStats { it.copy(trueQuizzes = it.trueQuizzes + 1, trueCorrect = it.trueCorrect + if (ok) 1 else 0) }
        quiz = q.copy(trueCorrect = ok, stage = QuizStage.DONE)
        quizBanner()
    }

    private var lastCheckWasBusy = false

    /** "With distractions 7 of 10, quiet rounds 9 of 10", once there's something to compare. */
    fun distractionCompare(): String? {
        val st = prefs.loadStats()
        if (st.distractChecks == 0) return null
        val quiet = if (st.quietChecks > 0) ", quiet rounds ${st.quietChecksOk} of ${st.quietChecks}" else ""
        return "With distractions ${st.distractChecksOk} of ${st.distractChecks} right$quiet."
    }

    private fun quizBanner() {
        val q = quiz ?: return
        val rcOk = q.runningCorrect == true
        val tcPart = q.trueCorrect?.let {
            " True count ${signed1(q.actualTrue)} (${signed(q.actualRunning)} / %.1f decks)".format(q.decksRemaining) +
                if (it) ", you were close enough." else ", you said ${signed(q.trueAnswer)}."
        } ?: ""
        val allOk = rcOk && (q.trueCorrect ?: true)
        val busyPart = if (lastCheckWasBusy) " " + (distractionCompare() ?: "") else ""
        banner = Banner(
            if (rcOk) "Running count ${signed(q.actualRunning)}, correct" else "RC was ${signed(q.actualRunning)}, you said ${signed(q.runningAnswer)}. Tap to audit",
            (tcPart + busyPart).trim(),
            if (allOk) BannerTone.GOOD else BannerTone.BAD,
            if (rcOk) BannerAction.NONE else BannerAction.AUDIT,
        )
    }

    private fun ladderStep(ok: Boolean) {
        if (!ok) {
            ladderRun = 0
            ladderRung = SpeedLadder.next(ladderRung, false)
        } else if (++ladderRun >= 3) {
            ladderRun = 0
            ladderRung = SpeedLadder.next(ladderRung, true)
        }
        prefs.ladderRung = ladderRung
        prefs.updateStats { it.copy(ladderBest = maxOf(it.ladderBest, ladderRung)) }
    }

    private fun coverCheck(units: Int) {
        val note = CoverPlay.betHeat(lastUnits, units, wonLast, state.shuffledThisRound)
        lastUnits = units
        heat += note.heat
        if (heat > peakHeat) peakHeat = heat
        banner = when {
            heat >= CoverPlay.LEAVE_AT -> Banner("Heat is high", "Back your bet down slowly, then leave at the next shuffle. Use Leave table in the menu.", BannerTone.BAD)
            note.message != null -> Banner("Cover play", note.message, if (heat >= CoverPlay.WARN_AT) BannerTone.BAD else BannerTone.INFO)
            heat >= CoverPlay.WARN_AT -> Banner("The pit is watching", "Your betting is drawing attention. Keep raises small for a while.", BannerTone.BAD)
            else -> banner
        }
    }

    /** Get up and find a new table: a fresh shoe, and the heat resets. */
    fun leaveTable() {
        if (!bettingOpen || watching) return
        game = newGame(settings, game.bankroll)
        heat = 0.0
        lastUnits = 0
        flatBetOnly = false
        joinedMidShoe = false
        dealerCheck = null
        syncSeats()
        shoesAtTable = 0
        dealer = com.hilotrainer.engine.Dealers.next(dealer)
        pitBossWatching = false
        quiz = null
        sync()
        visibleSeq = state.nextSeq
        banner = Banner("New table", "Fresh shoe. The ${system.label} running count starts at ${signed(system.initialCount(settings.decks))}.", BannerTone.INFO)
        fx(Fx.SHUFFLE)
    }

    /** Something said or seen across the table, shown as a bubble at a random spot. */
    var aside by mutableStateOf<com.hilotrainer.engine.Distractions.Event?>(null); private set
    /** Someone walking past the table, covering part of it. */
    var walkBy by mutableStateOf<com.hilotrainer.engine.Distractions.Event?>(null); private set
    /** A distraction happened since the last count check. */
    var distractedSinceCheck = false; private set
    /** Talks in another voice. Set by the activity: text, pitch, speech rate. */
    var talk: (String, Float, Float) -> Unit = { _, _, _ -> }

    private fun scheduleDistraction() {
        distractJob?.cancel()
        val t = com.hilotrainer.engine.Distractions.Table(
            decks = settings.decks, minBet = 10, dealerHitsSoft17 = settings.dealerHitsSoft17, sixToFive = settings.sixToFive,
            players = seatNames.toList(), yourBet = state.hands.sumOf { it.bet },
        )
        runDistractions(com.hilotrainer.engine.Distractions.plan(settings.distractionsReal, t))
    }

    /** Plays one round's distractions in order. Tests call it with a fixed plan. */
    internal fun runDistractions(plan: List<com.hilotrainer.engine.Distractions.Event>) {
        if (plan.isEmpty()) return
        distractedSinceCheck = true
        distractJob = viewModelScope.launch {
            var now = 0L
            for (e in plan.sortedBy { it.atMs }) {
                if (!instant) delay((e.atMs - now).coerceAtLeast(0)); now = e.atMs
                showDistraction(e)
            }
        }
    }

    private fun showDistraction(e: com.hilotrainer.engine.Distractions.Event) {
        when (e.kind) {
            com.hilotrainer.engine.Distractions.Kind.QUESTION -> {
                distraction = Distraction("${e.who}: \"${e.text}\"", needsAnswer = true, options = e.options, correct = e.correct, blocking = settings.distractionsReal)
                fx(Fx.CHATTER)
                if (settings.sound) talk(e.text, e.pitch, e.rate)
            }
            com.hilotrainer.engine.Distractions.Kind.NUMBERS, com.hilotrainer.engine.Distractions.Kind.CROWD -> {
                aside = e
                if (e.kind == com.hilotrainer.engine.Distractions.Kind.NUMBERS && settings.sound) talk(e.text, e.pitch, e.rate) else fx(Fx.CHATTER)
                clearLater(e) { if (aside?.atMs == e.atMs && aside?.text == e.text) aside = null }
            }
            com.hilotrainer.engine.Distractions.Kind.WALK_BY -> { walkBy = e; clearLater(e) { if (walkBy == e) walkBy = null } }
            com.hilotrainer.engine.Distractions.Kind.BUZZ -> {
                aside = e
                viewModelScope.launch { repeat(e.count) { fx(Fx.BUZZ); if (!instant) delay(500) } }
                clearLater(e) { if (aside == e) aside = null }
            }
            com.hilotrainer.engine.Distractions.Kind.CHIPS -> viewModelScope.launch { repeat(e.count) { fx(Fx.CHIP); if (!instant) delay(e.durationMs + kotlin.random.Random.nextLong(0, 140)) } }
        }
    }

    private fun clearLater(e: com.hilotrainer.engine.Distractions.Event, clear: () -> Unit) {
        if (instant) return
        viewModelScope.launch { delay(e.durationMs); clear() }
    }

    /** Your answer to a table question. Factual questions are graded. */
    fun answerDistraction(choice: Int = -1) {
        val d = distraction ?: return
        distraction = null
        val right = d.correct ?: return
        if (choice < 0) return
        val ok = choice == right
        prefs.updateStats { it.copy(distractQs = it.distractQs + 1, distractQsOk = it.distractQsOk + if (ok) 1 else 0) }
        if (!ok) banner = Banner("It was ${d.options[right]}", "Answer the table and keep the count going. That's the real skill.", BannerTone.BAD)
    }

    fun resetBankroll() {
        game.resetBankroll(1000)
        bankrollHistory.add(1000)
        bet = 10
        sync()
    }

    // ---------- v7: the living table ----------

    /** Between rounds: dealer shift changes and players coming and going. */
    private fun tableLifeBeforeDeal() {
        roundsWithDealer++
        if (roundsWithDealer > com.hilotrainer.engine.Dealers.SHIFT_ROUNDS) {
            roundsWithDealer = 0
            val old = dealer
            dealer = com.hilotrainer.engine.Dealers.next(old)
            banner = Banner("Dealer change", "${old.name} taps out and ${dealer.name} comes in: ${dealer.style}. Keep your count going.", BannerTone.INFO)
        }
        val max = settings.bots
        if (max > 0 && !instant && kotlin.random.Random.nextInt(100) < 7) {
            val n = game.seats
            if (n == max && n > 1) {
                val who = seatNames.last()
                game.changeSeats(n - 1); syncSeats()
                if (banner == null) banner = Banner("$who colors up and leaves", "One fewer player means you see fewer cards per round.", BannerTone.INFO)
            } else if (n < max) {
                game.changeSeats(n + 1); syncSeats()
                if (banner == null) banner = Banner("${seatNames.last()} sits down", "A new player joins mid-shoe. Their cards count like everyone's.", BannerTone.INFO)
            }
        }
    }

    /** Right after the deal: the dealer checks for blackjack and may flash the hole card. */
    private fun afterDealRealism() {
        holeFlashShowing = false
        val up = state.dealerCards.firstOrNull() ?: return
        if (up.upValue == 10) dealerPeekId = System.nanoTime()
        if (state.dealerCards.size >= 2 && up.rank.isAce) speak("Insurance is open.")
        if (state.phase == Phase.PLAYER && !state.satOut && kotlin.random.Random.nextInt(100) < flashChance()) flashHoleNow()
        else if (dealer.chatty > 0 && banner == null && kotlin.random.Random.nextInt(100) < dealer.chatty / 3 && dealer.lines.isNotEmpty()) {
            banner = Banner("${dealer.name}: \"${dealer.lines.random()}\"", "", BannerTone.INFO)
        }
    }

    /** Percent of rounds the hole card is flashed. Tests set it directly. */
    internal var flashOverride: Int? = null
    private fun flashChance(): Int = flashOverride ?: dealer.sloppy

    private fun flashHoleNow() {
        val c = game.flashHole() ?: return
        sync()
        holeFlashShowing = true
        prefs.updateStats { it.copy(holeFlashes = it.holeFlashes + 1) }
        banner = Banner(
            "The dealer flashed the hole card: ${cardName(c)}",
            "Count it now and not again when it's turned over. The coach still grades basic strategy.",
            BannerTone.COUNT,
        )
        // Show it once the deal has played out, for a moment, like a real flash.
        if (!instant) viewModelScope.launch { while (animating) delay(50); delay(1400); holeFlashShowing = false }
    }

    private fun roundEndRealism() {
        val d = state.dealerCards
        if (d.isNotEmpty() && !state.satOut || d.isNotEmpty() && state.bots.isNotEmpty()) speak(com.hilotrainer.engine.DealerCalls.result(d))
        val dealerBust = handTotal(d).first > 21
        state.botResults.forEachIndexed { i, r ->
            if (i < seatStacks.size) seatStacks[i] = (seatStacks[i] + r.net).coerceAtLeast(0)
            if (i < seatReactions.size) seatReactions[i] = when {
                r.outcome == Outcome.BLACKJACK -> "Blackjack!"
                dealerBust && r.net > 0 -> "Yes!"
                r.net < 0 && state.bots.getOrNull(i)?.total?.let { it > 21 } == true -> "Ugh."
                r.net > 0 && kotlin.random.Random.nextInt(3) == 0 -> "Nice."
                else -> null
            }
        }
        reactionId = System.nanoTime()
        pitBossWatching = settings.coverPlay && heat >= com.hilotrainer.engine.CoverPlay.WARN_AT
        if (!pitBossWatching && kotlin.random.Random.nextInt(100) < 8) pitBossWalkId = System.nanoTime()
    }

    /** Test hook. */
    internal fun forcePitWalk() { pitBossWalkId = System.nanoTime() }
    internal fun forcePitWatch() { pitBossWatching = true }

    /** You tapped where to cut the deck. */
    fun cutDeck() { cutDone = true; fx(Fx.CHIP) }

    fun askBuyIn() { if (roundsThisSession == 0 && !needBuyIn && bettingOpen) needBuyIn = true }

    /** True after you colored up, until you buy in again. */
    var justColoredUp by mutableStateOf(false); private set

    /** Sit down with the chips you already have. */
    fun keepChips() {
        needBuyIn = false
        buyInAmount = state.bankroll
        fx(Fx.CHIP)
    }

    fun buyIn(amount: Int) {
        justColoredUp = false
        game.resetBankroll(amount)
        buyInAmount = amount
        needBuyIn = false
        colorUpText = null
        bankrollHistory.clear(); bankrollHistory.add(amount)
        bet = 10
        sync()
        fx(Fx.CHIP)
        speak("Changing ${amount} dollars.")
        banner = Banner("Changing ${money(amount)}", "The dealer spreads your cash on the felt, counts it, and pushes you ${money(amount)} in chips.", BannerTone.INFO)
    }

    /** Leave the table: the dealer colors up your chips. */
    fun colorUp() {
        if (!bettingOpen) return
        val amt = state.bankroll
        val net = amt - buyInAmount
        colorUpText = "You color up ${money(amt)}: ${com.hilotrainer.engine.ColorUp.describe(amt)}. " +
            (if (net >= 0) "Up ${money(net)}" else "Down ${money(-net)}") + " on a ${money(buyInAmount)} buy-in" +
            (if (tipsThisSession > 0) ", with ${money(tipsThisSession)} in tips." else ".")
        needBuyIn = true
        justColoredUp = true
        fx(Fx.CHIP)
    }

    fun dismissColorUp() { colorUpText = null }

    /** Tip the dealer. Steady tipping looks like a recreational player, so it cools heat a little. */
    fun tipDealer(amount: Int = 5) {
        if (!bettingOpen || !game.tip(amount)) return
        tipsThisSession += amount
        prefs.updateStats { it.copy(tipsGiven = it.tipsGiven + amount) }
        if (settings.coverPlay) heat = (heat - 0.3).coerceAtLeast(0.0)
        sync()
        fx(Fx.CHIP)
        speak("Thank you.")
        banner = Banner("${dealer.name}: \"Thank you!\"", "You tipped ${money(amount)}. Tips are part of good cover, but they cost real money.", BannerTone.GOOD)
    }

    /** A hand signal on the felt: tap to hit, wave sideways to stand. */
    fun signal(hit: Boolean) {
        if (settings.signals == 0 || animating || state.phase != Phase.PLAYER) return
        // A table question has to be answered first.
        if (distraction?.blocking == true) return
        act(if (hit) Action.HIT else Action.STAND)
    }

    // ---------- v6: Kelly, mid-shoe, dealer errors, pit, career, session ----------

    /** Percent of rounds with a dealer statement to check, when dealer mistakes are on. */
    internal var dealerErrorChance = 25

    fun kellyNote(level: Double): String {
        val f = com.hilotrainer.engine.Kelly.FRACTIONS.getOrElse(settings.kellyChoice) { com.hilotrainer.engine.Kelly.FRACTIONS[1] }
        val raw = com.hilotrainer.engine.Kelly.bet(state.bankroll, level, f.first)
        return if (raw <= 0.0) "${f.second}: no edge yet, bet the minimum."
        else "${f.second} on ${money(state.bankroll)}: about ${money(com.hilotrainer.engine.Kelly.chips(state.bankroll, level, f.first))}."
    }

    /** Sit down at a table part way through a shoe, with the earlier cards unseen. */
    fun joinMidShoe() {
        if (!bettingOpen || watching) return
        game = newGame(settings, game.bankroll)
        val total = settings.decks * 52
        val maxBurn = (total * settings.penetrationPct / 100) - 78
        val burn = kotlin.random.Random.nextInt(52, maxOf(53, maxBurn))
        game.joinMidShoe(burn)
        syncSeats()
        joinedMidShoe = true
        quiz = null
        sync()
        visibleSeq = state.nextSeq
        prefs.updateStats { it.copy(midShoeJoins = it.midShoeJoins + 1) }
        val start = system.initialCount(settings.decks)
        banner = Banner(
            "Joined mid-shoe",
            "About %.1f decks are already in the tray and you never saw them. Start your count at %s: the cards you missed are as likely to be high as low.".format(burn / 52.0, signed(start)),
            BannerTone.INFO,
        )
        fx(Fx.SHUFFLE)
    }

    /** Your answer to what the dealer said: true if you think it was wrong. */
    fun callDealer(saysWrong: Boolean) {
        val c = dealerCheck ?: return
        dealerCheck = null
        val ok = saysWrong == c.wrong
        fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
        prefs.addPoints(1)
        prefs.updateStats {
            it.copy(dealerChecks = it.dealerChecks + 1, dealerChecksOk = it.dealerChecksOk + if (ok) 1 else 0, dealerCaught = it.dealerCaught + if (ok && c.wrong) 1 else 0)
        }
        banner = when {
            c.wrong && ok -> Banner("Good catch", c.explain + if (c.shortBy > 0) " You saved ${money(c.shortBy)}." else "", BannerTone.GOOD)
            c.wrong -> Banner("The dealer made a mistake", c.explain + if (c.shortBy > 0) " That would have cost you ${money(c.shortBy)}." else "", BannerTone.BAD)
            ok -> Banner("Right, the dealer was correct", c.explain, BannerTone.GOOD)
            else -> Banner("The dealer was right", c.explain, BannerTone.BAD)
        }
    }

    /**
     * The pit reacts to heat when you try to deal. Returns true if the deal is stopped:
     * first you're limited to flat bets, then raising anyway gets you backed off.
     */
    private fun pitStepsIn(): Boolean {
        if (flatBetOnly && bet > lastUnits * 10 && lastUnits > 0) {
            prefs.updateStats { it.copy(backoffs = it.backoffs + 1) }
            leaveTable()
            banner = Banner("Backed off", "The floor person asked you to stop playing blackjack. You raised after being told to flat bet. You've moved to a new table.", BannerTone.BAD)
            fx(Fx.MISTAKE)
            return true
        }
        if (!flatBetOnly && heat >= CoverPlay.LEAVE_AT) {
            flatBetOnly = true
            prefs.updateStats { it.copy(flatBets = it.flatBets + 1) }
            bet = (lastUnits * 10).coerceAtLeast(10).coerceAtMost(state.bankroll)
            banner = Banner("The pit says: flat bets only", "Locked at ${money(bet)}. Raise it and you're backed off.", BannerTone.BAD)
            fx(Fx.MISTAKE)
            return true
        }
        return false
    }

    fun startCareer(level: Int) {
        stopSession()
        if (mode != TableMode.NORMAL) stopMode()
        careerLevel = level
        val c = prefs.career
        if (c.level != level) prefs.career = CareerProgress(level = level)
        settings = CareerLevels.ALL[level].apply(prefs.loadSettings())
        game = newGame(settings, game.bankroll)
        syncSeats()
        heat = 0.0; flatBetOnly = false; quiz = null; dealerCheck = null
        sync(); visibleSeq = state.nextSeq
        val l = CareerLevels.ALL[level]
        banner = Banner("Career: ${l.name}", "${l.table}. To move up: ${l.needDecisions} plays at ${l.needAccuracy}% and ${l.needCounts} count checks at ${l.needCountAccuracy}%.", BannerTone.INFO)
    }

    fun careerProgress(): CareerProgress = prefs.career

    fun stopCareer() {
        if (careerLevel == null) return
        careerLevel = null
        refreshSettings()
    }

    private fun sessionBlock(f: (com.hilotrainer.engine.SessionPlan.Block) -> com.hilotrainer.engine.SessionPlan.Block) {
        if (!sessionActive || onBreak) return
        val i = com.hilotrainer.engine.SessionPlan.blockAt(sessionElapsedMs)
        while (sessionBlocks.size <= i) sessionBlocks.add(com.hilotrainer.engine.SessionPlan.Block())
        sessionBlocks[i] = f(sessionBlocks[i])
    }

    /** Start a full one-hour session: breaks are suggested every 20 minutes and accuracy is tracked in 10-minute blocks. */
    fun startSession() {
        stopSession()
        sessionActive = true; sessionDone = false
        sessionElapsedMs = 0; breaksTaken = 0; onBreak = false
        nextBreakAtMs = com.hilotrainer.engine.SessionPlan.BREAK_EVERY_MINUTES * 60_000L
        sessionBlocks.clear()
        bankrollHistory.clear(); bankrollHistory.add(state.bankroll)
        ev.reset(); evVersion++
        banner = Banner("One-hour session", "Play like a real trip to the casino. You'll get a break every ${com.hilotrainer.engine.SessionPlan.BREAK_EVERY_MINUTES} minutes, and the report shows if your accuracy fades.", BannerTone.INFO)
        if (instant) return
        sessionJob = viewModelScope.launch {
            var last = System.currentTimeMillis()
            while (sessionActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val dt = now - last; last = now
                if (onBreak) {
                    breakLeftMs = (breakLeftMs - dt).coerceAtLeast(0)
                    if (breakLeftMs == 0L) endBreak()
                } else {
                    sessionElapsedMs += dt
                    checkSession()
                }
            }
        }
    }

    private fun checkSession() {
        if (!sessionActive) return
        val total = com.hilotrainer.engine.SessionPlan.MINUTES * 60_000L
        if (sessionElapsedMs >= total) {
            finishSession(); return
        }
        if (sessionElapsedMs >= nextBreakAtMs && !onBreak) {
            nextBreakAtMs += com.hilotrainer.engine.SessionPlan.BREAK_EVERY_MINUTES * 60_000L
            banner = Banner("Time for a break", "Real counters rest their eyes. Take ${com.hilotrainer.engine.SessionPlan.BREAK_MINUTES} minutes from the menu, or keep going.", BannerTone.COUNT)
        }
    }

    fun takeBreak() {
        if (!sessionActive || onBreak || !bettingOpen) return
        onBreak = true; breaksTaken++
        breakLeftMs = com.hilotrainer.engine.SessionPlan.BREAK_MINUTES * 60_000L
    }

    fun endBreak() {
        if (!onBreak) return
        onBreak = false; breakLeftMs = 0
        banner = Banner("Back at the table", "The shoe kept going while you were away. Your count is still good if you remember it.", BannerTone.INFO)
    }

    private fun finishSession() {
        sessionActive = false; sessionDone = true; onBreak = false
        sessionJob?.cancel()
        val mins = (sessionElapsedMs / 60_000L).toInt()
        prefs.updateStats { it.copy(sessionsCompleted = it.sessionsCompleted + 1, bestSessionMinutes = maxOf(it.bestSessionMinutes, mins)) }
        banner = Banner("Session complete", "An hour at the table. Open the session report from the menu to see your swings and how your accuracy held up.", BannerTone.COUNT)
    }

    fun stopSession() {
        if (!sessionActive) return
        sessionActive = false; onBreak = false
        sessionJob?.cancel()
        val mins = (sessionElapsedMs / 60_000L).toInt()
        prefs.updateStats { it.copy(bestSessionMinutes = maxOf(it.bestSessionMinutes, mins)) }
    }

    // ---------- Back-counting ----------

    private val modeLevel: Double get() = game.upcomingBetLevel()
    private fun levelText(): String = game.upcomingCountText()

    private fun gradeMode(ok: Boolean, title: String, detail: String) {
        modeCalls++
        if (ok) modeCorrect++
        fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
        prefs.addPoints(1)
        prefs.updateStats {
            if (mode == TableMode.TEAM) it.copy(teamCalls = it.teamCalls + 1, teamCorrect = it.teamCorrect + if (ok) 1 else 0)
            else it.copy(backCountCalls = it.backCountCalls + 1, backCountCorrect = it.backCountCorrect + if (ok) 1 else 0)
        }
        banner = Banner(title, detail, if (ok) BannerTone.GOOD else BannerTone.BAD)
    }

    fun startBackCount() {
        if (!bettingOpen) return
        stopWatching()
        mode = TableMode.BACK_COUNT
        seated = false
        modeCalls = 0; modeCorrect = 0
        banner = Banner("Back-counting", "Stand behind the table and count. Sit down at true count +${BackCount.ENTER_AT} or better, leave when it falls to ${BackCount.LEAVE_AT}.", BannerTone.INFO)
    }

    fun keepWatching() {
        if (!bettingOpen || seated) return
        val enter = BackCount.shouldEnter(modeLevel)
        val text = levelText()
        if (mode == TableMode.TEAM) {
            startRound(0)
            gradeMode(!enter, if (!enter) "Good, keep spotting" else "Call the big player now", "${text.replaceFirstChar { it.uppercase() }}. Call in at +${BackCount.ENTER_AT} or better.")
            return
        }
        startRound(0)
        gradeMode(!enter, if (!enter) "Good, keep watching" else "You should have sat down", "${text.replaceFirstChar { it.uppercase() }}. Sit down at +${BackCount.ENTER_AT} or better.")
    }

    fun sitDown() {
        if (!bettingOpen || seated) return
        val enter = BackCount.shouldEnter(modeLevel)
        seated = true
        gradeMode(enter, if (enter) "Good time to sit down" else "Too early to sit down", "${levelText().replaceFirstChar { it.uppercase() }}. The entry point here is +${BackCount.ENTER_AT}.")
    }

    fun standUp() {
        if (!bettingOpen || !seated) return
        val leave = BackCount.shouldLeave(modeLevel)
        seated = false
        if (mode == TableMode.TEAM) teamRole = TeamRole.SPOTTER
        gradeMode(leave, if (leave) "Good time to leave" else "You left a good count", "${levelText().replaceFirstChar { it.uppercase() }}. Leave when it falls to ${BackCount.LEAVE_AT} or below.")
    }

    /** Seated in back-count or team mode and dealing a hand when the count says leave. */
    private fun checkStay(): Boolean {
        if (mode == TableMode.NORMAL || !seated) return true
        if (BackCount.shouldLeave(modeLevel)) {
            gradeMode(false, "Time to leave", "${levelText().replaceFirstChar { it.uppercase() }}. The count fell back, so get up instead of playing on.")
            return false
        }
        return true
    }

    fun stopMode() {
        mode = TableMode.NORMAL
        seated = true
        teamRole = TeamRole.SPOTTER
        banner = null
    }

    // ---------- Team play ----------

    fun startTeam() {
        if (!bettingOpen) return
        stopWatching()
        mode = TableMode.TEAM
        seated = false
        teamRole = TeamRole.SPOTTER
        modeCalls = 0; modeCorrect = 0
        banner = Banner("Team play: you're the spotter", "Count from a minimum bet seat. Call in the big player at +${BackCount.ENTER_AT} or better.", BannerTone.INFO)
    }

    fun callBigPlayer() {
        if (!bettingOpen || mode != TableMode.TEAM || teamRole != TeamRole.SPOTTER) return
        val ok = BackCount.shouldEnter(modeLevel)
        gradeMode(ok, if (ok) "Good call" else "Too early to call", "${levelText().replaceFirstChar { it.uppercase() }}. Now give the signal for the count.")
        teamRole = TeamRole.SIGNAL
    }

    fun giveSignal(i: Int) {
        if (mode != TableMode.TEAM || teamRole != TeamRole.SIGNAL) return
        val want = TeamSignals.forLevel(modeLevel)
        val picked = TeamSignals.SIGNALS[i]
        val ok = want == null && picked.minLevel == 2 || picked == want
        gradeMode(ok, if (ok) "Signal sent: ${picked.gesture}" else "Wrong signal", "Right signal: ${(want ?: TeamSignals.SIGNALS[0]).gesture} (${(want ?: TeamSignals.SIGNALS[0]).meaning}). Now you're the big player.")
        teamRole = TeamRole.BIG_PLAYER
        seated = true
        teamSignalBanner()
    }

    private fun teamSignalBanner() {
        val sig = TeamSignals.forLevel(modeLevel)
        banner = Banner(
            "Spotter: " + (sig?.gesture ?: "no signal"),
            if (sig == null) "Count is about +1. Bet small or think about leaving." else "Read the signal, then bet or leave.",
            BannerTone.COUNT,
        )
    }

    /** Exact insurance value per unit for the cards you haven't seen (the hole card counts as unseen). */
    fun insuranceEvNow(): Double = com.hilotrainer.engine.SideBets.insuranceEv(com.hilotrainer.engine.SideBets.unseen(settings.decks, visibleSeen()))

    /** Exact Perfect Pairs value per unit for the next round's first two cards. */
    fun perfectPairsEvNow(): Double = com.hilotrainer.engine.SideBets.perfectPairsEv(com.hilotrainer.engine.SideBets.unseen(settings.decks, if (animating) emptyList() else state.seenSinceShuffle))

    private fun visibleSeen(): List<com.hilotrainer.engine.Card> = if (animating) emptyList() else state.seenSinceShuffle

    fun insuranceHintText(): String = when {
        system != CountSystem.HI_LO -> "${system.label} follows basic strategy: no insurance"
        BasicStrategy.shouldInsure(visibleTrueCount) -> "true count is +3 or higher"
        else -> "true count is below +3"
    }

    val currentState: GameState get() = state
}

data class Distraction(
    val text: String,
    val needsAnswer: Boolean,
    val options: List<String> = emptyList(),
    val correct: Int? = null,
    /** Real mode: you must answer before you can play on. */
    val blocking: Boolean = false,
    val id: Long = System.nanoTime(),
)

enum class TableMode { NORMAL, BACK_COUNT, TEAM }

enum class TeamRole { SPOTTER, SIGNAL, BIG_PLAYER }
