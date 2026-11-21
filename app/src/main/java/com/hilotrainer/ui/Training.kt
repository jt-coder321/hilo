package com.hilotrainer.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.BasicStrategy
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.CasinoTest
import com.hilotrainer.engine.CountSystem
import com.hilotrainer.engine.Options
import com.hilotrainer.engine.Review
import com.hilotrainer.engine.RiskOfRuin
import com.hilotrainer.engine.Shoe
import com.hilotrainer.engine.SpeedLadder
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
private fun DrillColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Heading(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)

@Composable
private fun Note(text: String) = Text(text, fontSize = 13.sp, color = Cream.copy(alpha = 0.72f))

@Composable
private fun CardStage(cards: List<Card>, minHeight: androidx.compose.ui.unit.Dp = 240.dp) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(18.dp)).background(Color(0xFF13693F)),
        contentAlignment = Alignment.Center,
    ) {
        val w = (maxWidth / (cards.size.coerceAtLeast(1) + 1.2f)).coerceAtMost(140.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards.forEach { PlayingCard(it, w) } }
    }
}

// ---------------------------------------------------------------------------
// Casino test: one deck, a few cards held back, against the clock.
// ---------------------------------------------------------------------------

private enum class TestPhase { SETUP, RUNNING, ANSWER, RESULT }

@Composable
fun CasinoTestScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    val sys = remember { prefs.loadSettings().system }
    var phase by remember { mutableStateOf(TestPhase.SETUP) }
    var groups by remember { mutableStateOf(listOf<List<Card>>()) }
    var held by remember { mutableStateOf(listOf<Card>()) }
    var shown by remember { mutableIntStateOf(0) }
    var startMs by remember { mutableLongStateOf(0L) }
    var nowMs by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var answer by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Int?>(null) }
    var stats by remember { mutableStateOf(prefs.loadStats()) }

    fun start() {
        val shoe = Shoe(1, 1.0)
        val all = List(52) { shoe.draw() }
        val k = Random.nextInt(2, 6)
        held = all.takeLast(k)
        val dealt = all.dropLast(k)
        // Deal the deck in groups of 2 to 4 cards, the way hands come off a real table.
        val out = mutableListOf<List<Card>>()
        var i = 0
        while (i < dealt.size) {
            val n = minOf(Random.nextInt(2, 5), dealt.size - i)
            out += dealt.subList(i, i + n)
            i += n
        }
        groups = out
        shown = 1
        answer = 0
        startMs = System.currentTimeMillis()
        nowMs = startMs
        phase = TestPhase.RUNNING
        fx(Fx.SHUFFLE)
    }

    LaunchedEffect(phase) {
        while (phase == TestPhase.RUNNING) { nowMs = System.currentTimeMillis(); delay(100) }
    }

    val dealtCards = groups.flatten()
    val actual = dealtCards.sumOf { sys.tag(it) }

    ScreenScaffold("Casino test", onBack) {
        DrillColumn {
            when (phase) {
                TestPhase.SETUP -> {
                    Panel {
                        Heading("Count down a deck against the clock")
                        Text("A single deck comes out in groups of 2 to 4 cards. A few cards are held back, so the final count is not a giveaway. Tap Next as fast as you can, then enter your ${sys.label} running count (start at 0).")
                        Spacer(Modifier.height(8.dp))
                        Text("Pass levels (correct count within the time):", fontWeight = FontWeight.SemiBold)
                        CasinoTest.LEVELS.forEach { Text("${it.name}: ${it.seconds} seconds or less") }
                        Spacer(Modifier.height(6.dp))
                        Note("These are this trainer's own practice targets, not an official casino standard.")
                        Spacer(Modifier.height(10.dp))
                        GoldButton("Start the test", Modifier.fillMaxWidth().height(56.dp)) { start() }
                    }
                    Panel {
                        Text("Best level passed: ${stats.casinoBest.takeIf { it >= 0 }?.let { CasinoTest.LEVELS[it].name } ?: "none yet"}")
                        Text("Tests passed: ${pct(stats.casinoPasses, stats.casinoTests)}")
                    }
                }

                TestPhase.RUNNING -> {
                    val secs = (nowMs - startMs) / 1000.0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Group $shown of ${groups.size}", modifier = Modifier.weight(1f))
                        Text("%.1fs".format(secs), fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Gold)
                    }
                    CardStage(groups[shown - 1])
                    GoldButton(if (shown < groups.size) "Next" else "Done", Modifier.fillMaxWidth().height(68.dp)) {
                        if (shown < groups.size) { shown++; fx(Fx.DEAL) } else {
                            elapsed = (System.currentTimeMillis() - startMs) / 1000.0
                            phase = TestPhase.ANSWER
                        }
                    }
                    Note("The button sits below the cards so your thumb never covers them.")
                }

                TestPhase.ANSWER -> Panel {
                    Text("Time: %.1f seconds. What's the running count?".format(elapsed), fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    NumberStepper(answer, { answer = it }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Check", Modifier.fillMaxWidth()) {
                        val ok = answer == actual
                        val level = if (ok) CasinoTest.passed(elapsed) else null
                        result = level ?: -1
                        fx(if (level != null) Fx.CORRECT else Fx.MISTAKE)
                        prefs.addPoints(3)
                        prefs.track(counts = 1, countsOk = if (ok) 1 else 0)
                        prefs.updateStats {
                            it.copy(
                                casinoTests = it.casinoTests + 1,
                                casinoPasses = it.casinoPasses + if (level != null) 1 else 0,
                                casinoBest = maxOf(it.casinoBest, level ?: -1),
                            )
                        }
                        stats = prefs.loadStats()
                        phase = TestPhase.RESULT
                    }
                }

                TestPhase.RESULT -> {
                    val level = result ?: -1
                    Panel {
                        when {
                            answer != actual -> Text("Count was ${signed(actual)}, you said ${signed(answer)}. Not a pass.", color = BadRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            level < 0 -> Text("Correct count, but %.1fs is over the Bronze time.".format(elapsed), color = BadRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            else -> Text("${CasinoTest.LEVELS[level].name} pass! Correct in %.1fs.".format(elapsed), color = GoodGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        val next = CasinoTest.LEVELS.getOrNull(level + 1)
                        if (next != null && answer == actual) Text("Next: ${next.name} at ${next.seconds} seconds.")
                        Spacer(Modifier.height(6.dp))
                        Text("Held back: ${held.joinToString(" ")} (tags ${held.joinToString(" ") { signed(sys.tag(it)) }}).", fontSize = 14.sp)
                        Note("Your count plus the held-back tags equals ${signed(sys.deckSum)}, a full ${sys.label} deck.")
                        Spacer(Modifier.height(6.dp))
                        Text("Count after each card:", fontWeight = FontWeight.SemiBold)
                        Text(runningTrail(dealtCards, sys), fontSize = 12.sp, color = Cream.copy(alpha = 0.7f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton("Try again", Modifier.weight(1f)) { start() }
                        GoldButton("Levels", Modifier.weight(1f), primary = false) { phase = TestPhase.SETUP }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Speed ladder: the dealing speed climbs as you get counts right.
// ---------------------------------------------------------------------------

@Composable
fun SpeedLadderScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    val sys = remember { prefs.loadSettings().system }
    var rung by remember { mutableIntStateOf(prefs.ladderRung) }
    var phase by remember { mutableStateOf(TestPhase.SETUP) }
    var cards by remember { mutableStateOf(listOf<Card>()) }
    var shown by remember { mutableIntStateOf(0) }
    var answer by remember { mutableIntStateOf(0) }
    var lastOk by remember { mutableStateOf<Boolean?>(null) }
    var best by remember { mutableIntStateOf(prefs.loadStats().ladderBest) }
    val n = 20

    fun start() {
        val shoe = Shoe(2, 1.0)
        cards = List(n) { shoe.draw() }
        shown = 1
        answer = 0
        phase = TestPhase.RUNNING
        fx(Fx.DEAL)
    }
    LaunchedEffect(phase) {
        while (phase == TestPhase.RUNNING) {
            delay(SpeedLadder.RUNGS_MS[rung].toLong())
            if (shown >= cards.size) phase = TestPhase.ANSWER else { shown++; fx(Fx.DEAL) }
        }
    }
    val actual = cards.sumOf { sys.tag(it) }

    ScreenScaffold("Speed ladder", onBack) {
        DrillColumn {
            when (phase) {
                TestPhase.SETUP, TestPhase.RESULT -> {
                    lastOk?.let { ok ->
                        Panel {
                            Text(
                                if (ok) "Correct, ${signed(actual)}. Up a rung!" else "Count was ${signed(actual)}, you said ${signed(answer)}. Down a rung.",
                                color = if (ok) GoodGreen else BadRed, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            )
                            Text(runningTrail(cards, sys), fontSize = 12.sp, color = Cream.copy(alpha = 0.7f))
                        }
                    }
                    Panel {
                        Heading("Rung ${rung + 1} of ${SpeedLadder.RUNGS_MS.size}: ${SpeedLadder.label(rung)} per card")
                        Text("$n cards flash by on their own. Keep the ${sys.label} running count from 0. Get it right to climb a rung, miss and you drop one.")
                        Spacer(Modifier.height(10.dp))
                        LadderGraphic(rung, best)
                        Spacer(Modifier.height(10.dp))
                        GoldButton("Deal $n cards", Modifier.fillMaxWidth().height(56.dp)) { start() }
                        Spacer(Modifier.height(6.dp))
                        Note("Set Dealing speed to Ladder in Settings and the table deals at your rung, climbing after 3 correct count checks in a row.")
                    }
                }
                TestPhase.RUNNING -> {
                    Text("Card $shown of ${cards.size} at ${SpeedLadder.label(rung)}")
                    CardStage(listOf(cards[shown - 1]), 280.dp)
                }
                TestPhase.ANSWER -> Panel {
                    Text("What's the running count?", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    NumberStepper(answer, { answer = it }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Check", Modifier.fillMaxWidth()) {
                        val ok = answer == actual
                        lastOk = ok
                        fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
                        rung = SpeedLadder.next(rung, ok)
                        prefs.ladderRung = rung
                        prefs.addPoints(2)
                        prefs.track(counts = 1, countsOk = if (ok) 1 else 0)
                        prefs.updateStats { it.copy(ladderBest = maxOf(it.ladderBest, rung), drillsRun = it.drillsRun + 1, drillsCorrect = it.drillsCorrect + if (ok) 1 else 0) }
                        best = maxOf(best, rung)
                        phase = TestPhase.RESULT
                    }
                }
            }
        }
    }
}

@Composable
private fun LadderGraphic(rung: Int, best: Int) {
    Row(Modifier.fillMaxWidth().height(144.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SpeedLadder.RUNGS_MS.indices.forEach { i ->
            val h = 30 + i * 12
            val col = when {
                i == rung -> Gold
                i <= best -> Gold.copy(alpha = 0.4f)
                else -> Cream.copy(alpha = 0.12f)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(6.dp)).background(col))
                Text(SpeedLadder.label(i), fontSize = 10.sp, color = Cream.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Pair counting: see two cards, give their combined value at a glance.
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PairDrillScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    val sys = remember { prefs.loadSettings().system }
    val shoe = remember { Shoe(6, 1.0) }
    fun newPair() = listOf(shoe.draw(), shoe.draw()).also { if (shoe.cardsRemaining < 10) shoe.shuffle() }
    var pair by remember { mutableStateOf(newPair()) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var shownAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ok by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var totalMs by remember { mutableLongStateOf(0L) }
    val actual = pair.sumOf { sys.tag(it) }
    val span = if (sys == CountSystem.ZEN) 4 else 2

    LaunchedEffect(picked) {
        if (picked != null) {
            delay(if (picked == actual) 500 else 1600)
            pair = newPair(); picked = null; shownAt = System.currentTimeMillis()
        }
    }

    ScreenScaffold("Pair counting", onBack) {
        DrillColumn {
            Note("Seeing cards in pairs is faster than adding them one at a time. Many pairs cancel to 0. Tap the pair's combined ${sys.label} value.")
            CardStage(pair, 220.dp)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (-span..span).forEach { v ->
                    val bg = when {
                        picked == null -> Ink.copy(alpha = 0.6f)
                        v == actual -> GoodGreen.copy(alpha = 0.5f)
                        v == picked -> BadRed.copy(alpha = 0.5f)
                        else -> Ink.copy(alpha = 0.4f)
                    }
                    Box(
                        Modifier.size(64.dp, 56.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                            .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .then(if (picked == null) Modifier.clickable {
                                picked = v
                                total++
                                totalMs += System.currentTimeMillis() - shownAt
                                val good = v == actual
                                if (good) { ok++; streak++ } else streak = 0
                                fx(if (good) Fx.CORRECT else Fx.MISTAKE)
                                prefs.addPoints(1)
                                prefs.track(counts = 1, countsOk = if (good) 1 else 0)
                                prefs.updateStats { it.copy(pairDrills = it.pairDrills + 1, pairCorrect = it.pairCorrect + if (good) 1 else 0) }
                            } else Modifier),
                        contentAlignment = Alignment.Center,
                    ) { Text(signed(v), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Cream) }
                }
            }
            Panel {
                Text("This session: ${pct(ok, total)}   Streak: $streak")
                if (total > 0) Text("Average answer time: %.2fs".format(totalMs / 1000.0 / total))
                Note(sys.valuesText)
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Spaced repetition review of missed plays.
// ---------------------------------------------------------------------------

@Composable
fun ReviewScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}, now: () -> Long = { System.currentTimeMillis() }) {
    val rules = remember { prefs.loadSettings().rules() }
    var items by remember { mutableStateOf(prefs.loadReview()) }
    val due = Review.due(items, now())
    val current = due.firstOrNull()
    var picked by remember { mutableStateOf<Action?>(null) }
    var doneThisSession by remember { mutableIntStateOf(0) }

    ScreenScaffold("Review missed plays", onBack) {
        DrillColumn {
            if (current == null) {
                Panel {
                    Heading(if (items.isEmpty()) "Nothing to review yet" else "All caught up")
                    Text(
                        if (items.isEmpty()) "When you miss a basic strategy play at the table or in the strategy drill, it lands here. Each one comes back after 1, 3, 7, 14 and 30 days as you get it right, and resets if you miss it again."
                        else "You've reviewed everything that's due. Missed plays come back on a schedule: 1, 3, 7, 14 and 30 days.",
                    )
                    if (doneThisSession > 0) Text("Reviewed this session: $doneThisSession", color = GoodGreen)
                }
            } else {
                val built = remember(current.key) { Review.build(current.key) }
                if (built == null) {
                    items = items.filter { it.key != current.key }; prefs.saveReview(items)
                } else {
                    val (hand, up) = built
                    val opts = Options(canDouble = true, canSplit = hand.isPair, canSurrender = rules.surrenderAllowed)
                    val advice = BasicStrategy.basic(hand, up, rules, opts)
                    Text("${due.size} due · missed ${current.misses} time${if (current.misses == 1) "" else "s"} · box ${current.box + 1} of ${Review.INTERVAL_DAYS.size}", color = Cream.copy(alpha = 0.75f))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33000000)).padding(16.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DEALER", fontSize = 12.sp, letterSpacing = 2.sp, color = Gold)
                            PlayingCard(up, 80.dp)
                            Spacer(Modifier.height(14.dp))
                            CardRow(hand.cards, 80.dp)
                            Text(Review.describe(current.key), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    val actions = buildList {
                        add(Action.HIT); add(Action.STAND); add(Action.DOUBLE)
                        if (hand.isPair) add(Action.SPLIT)
                        if (rules.surrenderAllowed) add(Action.SURRENDER)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        actions.forEach { a ->
                            GoldButton(a.label, Modifier.weight(1f), enabled = picked == null) {
                                picked = a
                                val good = a == advice.action
                                fx(if (good) Fx.CORRECT else Fx.MISTAKE)
                                prefs.addPoints(1)
                                prefs.track(decisions = 1, correct = if (good) 1 else 0)
                                prefs.updateStats { it.copy(reviews = it.reviews + 1, reviewsCorrect = it.reviewsCorrect + if (good) 1 else 0) }
                            }
                        }
                    }
                    picked?.let { p ->
                        val good = p == advice.action
                        Panel {
                            if (good) {
                                val box = (current.box + 1).coerceAtMost(Review.INTERVAL_DAYS.size - 1)
                                Text("Correct: ${advice.reason}", color = GoodGreen, fontWeight = FontWeight.SemiBold)
                                Text(if (box >= Review.INTERVAL_DAYS.size - 1) "Mastered. It won't come back." else "Next review in ${Review.INTERVAL_DAYS[box]} day${if (Review.INTERVAL_DAYS[box] == 1) "" else "s"}.")
                            } else {
                                Text("Correct play: ${advice.action.label}. ${advice.reason}.", color = BadRed, fontWeight = FontWeight.SemiBold)
                                Text("It stays in today's review until you get it.")
                            }
                            Spacer(Modifier.height(8.dp))
                            GoldButton("Next", Modifier.fillMaxWidth()) {
                                prefs.reviewAnswered(current.key, good, now())
                                items = prefs.loadReview()
                                doneThisSession++
                                picked = null
                            }
                        }
                    }
                }
            }
            if (items.isNotEmpty()) {
                Panel {
                    Heading("Your review deck")
                    items.sortedBy { it.dueMs }.take(12).forEach {
                        val days = ((it.dueMs - now()) / Review.DAY_MS.toDouble()).let { d -> kotlin.math.ceil(d).toInt() }
                        val whenText = when { it.mastered -> "mastered"; days <= 0 -> "due now"; days == 1 -> "in 1 day"; else -> "in $days days" }
                        Text("${Review.describe(it.key)}: $whenText", fontSize = 14.sp)
                    }
                    Note("Uses basic strategy for your table rules in Settings.")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cover play: camouflage and when to leave.
// ---------------------------------------------------------------------------

private data class CoverQ(val situation: String, val options: List<String>, val answer: Int, val why: String)

private val COVER_QUESTIONS = listOf(
    CoverQ(
        "You lost the last hand at 1 unit. The count just jumped and your ramp says 8 units. What draws the least attention?",
        listOf("Jump straight to 8 units", "Go to 2 or 3 units now and keep stepping up", "Stay at 1 unit for the rest of the shoe"),
        1,
        "A big jump right after a loss is a classic counter pattern. Raising in small steps gives up a little edge to look like a normal player. Never raising throws away the reason you count.",
    ),
    CoverQ(
        "You won the last hand at 2 units and the count is still rising. What's the most natural way to raise?",
        listOf("Let the win ride and bet 4 units", "Pull back to 1 unit", "Jump to 12 units"),
        0,
        "Letting a winning bet ride is something regular players do all the time, so it hides a raise well.",
    ),
    CoverQ(
        "You're betting 8 units at a high count and the dealer shuffles. What now?",
        listOf("Drop to 1 unit on the very first hand", "Come down over a couple of hands", "Keep betting 8 units all shoe"),
        1,
        "Dropping from a big bet to the minimum exactly at the shuffle tells the pit you knew the count. Backing down over a hand or two costs a little but looks natural.",
    ),
    CoverQ(
        "The count goes very negative halfway through the shoe. Your habit is to get up and change tables every time this happens. Good idea?",
        listOf("Yes, always leave on a bad count", "No, hopping every time a shoe goes bad gets noticed", "Bet bigger to make it back"),
        1,
        "Jumping from table to table every time the deck sours is a giveaway. Bet the minimum, sit out a hand, or leave at a natural break like a shuffle or a drink.",
    ),
    CoverQ(
        "The pit boss walks over, watches your bets closely and talks quietly with the dealer.",
        listOf("Back your bets down slowly but right away, then leave soon", "Raise to show you're not scared", "Ignore it and keep your full spread"),
        0,
        "When you sense attention, back down slowly but immediately and plan to leave. A long session after you've been noticed only adds risk.",
    ),
    CoverQ(
        "You notice your lips move a little as you count.",
        listOf("It's fine, nobody watches that", "Practice counting silently with a relaxed face", "Count out loud so it looks casual"),
        1,
        "Visible counting is a dead giveaway. Drill until the count runs silently while you chat, which is what the table distractions setting is for.",
    ),
    CoverQ(
        "You've been at the same table for four hours with a steady betting spread.",
        listOf("Stay, the longer the better", "Keep sessions shorter and move on at a natural break", "Switch to a bigger spread"),
        1,
        "Long sessions draw attention to counters. Shorter sessions keep you under the radar.",
    ),
)

@Composable
fun CoverDrillScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    var order by remember { mutableStateOf(COVER_QUESTIONS.indices.shuffled()) }
    var i by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var ok by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    val q = COVER_QUESTIONS[order[i % order.size]]
    ScreenScaffold("Cover play", onBack) {
        DrillColumn {
            Panel {
                Heading("Play like a counter, look like a gambler")
                Note("Cover play means giving up a little edge so your betting doesn't stand out. Turn on Cover play in Settings to get a heat meter at the table.")
            }
            Panel {
                Text("Situation ${i % order.size + 1} of ${order.size}", fontSize = 12.sp, color = Gold)
                Text(q.situation, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            q.options.forEachIndexed { k, opt ->
                val bg = when {
                    picked == null -> false to Gold
                    k == q.answer -> true to GoodGreen
                    k == picked -> true to BadRed
                    else -> false to Gold.copy(alpha = 0.3f)
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (bg.first) bg.second.copy(alpha = 0.25f) else Ink.copy(alpha = 0.55f))
                        .border(1.dp, bg.second.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .then(if (picked == null) Modifier.clickable {
                            picked = k
                            total++
                            val good = k == q.answer
                            if (good) ok++
                            fx(if (good) Fx.CORRECT else Fx.MISTAKE)
                            prefs.addPoints(1)
                            prefs.updateStats { it.copy(coverDrills = it.coverDrills + 1, coverCorrect = it.coverCorrect + if (good) 1 else 0) }
                        } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(opt, fontSize = 15.sp, color = Cream) }
            }
            picked?.let { p ->
                Panel {
                    Text(if (p == q.answer) "Good call." else "Better: ${q.options[q.answer]}.", color = if (p == q.answer) GoodGreen else BadRed, fontWeight = FontWeight.Bold)
                    Text(q.why)
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Next situation", Modifier.fillMaxWidth()) {
                        picked = null
                        i++
                        if (i % order.size == 0) order = COVER_QUESTIONS.indices.shuffled()
                    }
                }
            }
            Panel {
                Text("This session: ${pct(ok, total)}")
                Note("Tips adapted from wikiHow's How to Count Cards guide and common camouflage advice.")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bankroll and risk of ruin calculator.
// ---------------------------------------------------------------------------

@Composable
fun BankrollScreen(prefs: Prefs, onBack: () -> Unit) {
    val settings = remember { prefs.loadSettings() }
    var unit by remember { mutableIntStateOf(10) }
    var bankroll by remember { mutableIntStateOf(5000) }
    var edgeTenths by remember { mutableIntStateOf(8) } // edge in tenths of a percent
    var variance by remember { mutableDoubleStateOf(6.5) }
    var hands by remember { mutableIntStateOf(80) }
    val edge = edgeTenths / 1000.0
    val units = bankroll / unit.toDouble()
    val ror = RiskOfRuin.ror(edge, units, variance)

    ScreenScaffold("Bankroll and risk", onBack) {
        DrillColumn {
            Panel {
                Heading("Risk of ruin")
                Text(
                    if (ror >= 0.9995) "100%" else if (ror < 0.001) "under 0.1%" else "%.1f%%".format(ror * 100),
                    fontFamily = TitleFont, fontWeight = FontWeight.Black, fontSize = 44.sp,
                    color = when { ror > 0.2 -> BadRed; ror > 0.05 -> Color(0xFFFFB74D); else -> GoodGreen },
                )
                Text("Chance of losing the whole ${money(bankroll)} bankroll (%.0f units of ${money(unit)}) before it grows, if you keep playing this way.".format(units))
                Spacer(Modifier.height(8.dp))
                val r5 = RiskOfRuin.unitsFor(0.05, edge, variance)
                val r1 = RiskOfRuin.unitsFor(0.01, edge, variance)
                Text("Bankroll for 5% risk: ${money((r5 * unit).toInt())} (${"%.0f".format(r5)} units)")
                Text("Bankroll for 1% risk: ${money((r1 * unit).toInt())} (${"%.0f".format(r1)} units)")
                Spacer(Modifier.height(6.dp))
                val ev = RiskOfRuin.unitsPerHour(edge, hands) * unit
                val sd = RiskOfRuin.sdPerHour(variance, hands) * unit
                Text("Expected win: about ${money(ev.toInt())} an hour, with swings of about ${money(sd.toInt())} an hour either way (one standard deviation).")
            }
            Panel {
                Heading("Your numbers")
                ChipGroup("Unit (table minimum)", listOf("$5" to 5, "$10" to 10, "$25" to 25, "$50" to 50), unit) { unit = it }
                ChipGroup("Bankroll", listOf("$1,000" to 1000, "$2,500" to 2500, "$5,000" to 5000, "$10,000" to 10000, "$25,000" to 25000), bankroll) { bankroll = it }
                ChipGroup("Your edge per hand (share of one unit)", listOf("0.5%" to 5, "0.8%" to 8, "1.0%" to 10, "1.5%" to 15), edgeTenths) { edgeTenths = it }
                ChipGroup("Bet spread (variance per hand)", listOf("Flat bet (1.3)" to 1.3, "1 to 8 (6.5)" to 6.5, "1 to 12 (12.5)" to 12.5), variance) { variance = it }
                ChipGroup("Hands per hour", listOf("60" to 60, "80" to 80, "100" to 100), hands) { hands = it }
            }
            Panel {
                Heading("How this works")
                Text("Risk of ruin = e^(-2 x edge x bankroll / variance), with the bankroll in units and the variance in units squared per hand. It assumes you never resize your bets as the bankroll changes.", fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Note("Variance presets use the middle of the ranges in a published risk of ruin calculator: flat betting about 1.3, a 1 to 8 spread about 5 to 8, a 1 to 12 spread about 10 to 15. Your real edge depends on rules, penetration and how well you play, so treat these as estimates.")
                if (settings.sixToFive) {
                    Spacer(Modifier.height(6.dp))
                    Text("Your table is set to 6:5 blackjack, which by itself adds about 1.36% to the house edge. That's more than any edge in these presets.", color = BadRed, fontSize = 14.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Progress chart and stats export.
// ---------------------------------------------------------------------------

@Composable
fun ProgressScreen(prefs: Prefs, onBack: () -> Unit) {
    val context = LocalContext.current
    val hist = remember { prefs.history() }
    val days = remember(hist) { lastDays(hist, 30) }
    ScreenScaffold("Progress", onBack) {
        DrillColumn {
            Panel {
                Heading("Last 30 days")
                val active = days.count { it.points > 0 }
                Text("Days practiced: $active of 30", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Accuracy", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().height(170.dp).padding(top = 6.dp)) {
                    Box(Modifier.width(34.dp).fillMaxSize()) {
                        Text("100%", fontSize = 10.sp, color = Cream.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.TopStart))
                        Text("75%", fontSize = 10.sp, color = Cream.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterStart).padding(top = 28.dp))
                        Text("40%", fontSize = 10.sp, color = Cream.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.BottomStart))
                    }
                    AccuracyChart(days, Modifier.weight(1f).fillMaxSize())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(Gold, "Strategy")
                    LegendDot(Color(0xFF80CBC4), "Count")
                }
                Spacer(Modifier.height(10.dp))
                Text("Practice points per day (goal $DAILY_GOAL)", fontWeight = FontWeight.SemiBold)
                PointsChart(days, Modifier.fillMaxWidth().height(110.dp).padding(top = 6.dp))
                Note("Days with no decisions or count checks leave a gap in the accuracy lines.")
            }
            Panel {
                Heading("Export your stats")
                Text("Share a CSV of your daily totals and lifetime stats to Drive, Gmail, Keep or any app that takes text.", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                GoldButton("Export stats (CSV)", Modifier.fillMaxWidth()) {
                    val csv = statsCsv(prefs)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_SUBJECT, "Hi-Lo Trainer stats")
                        putExtra(Intent.EXTRA_TEXT, csv)
                    }
                    context.startActivity(Intent.createChooser(send, "Export stats"))
                }
                Note("Set up a daily practice reminder in Settings.")
            }
        }
    }
}

@Composable
private fun LegendDot(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(c))
        Text("  $label", fontSize = 12.sp, color = Cream.copy(alpha = 0.8f))
    }
}

/** The last [n] calendar days ending today, filling missing days with zeros. */
fun lastDays(hist: List<DayStats>, n: Int, today: java.time.LocalDate = java.time.LocalDate.now()): List<DayStats> {
    val byDate = hist.associateBy { it.date }
    return (n - 1 downTo 0).map { back -> today.minusDays(back.toLong()).toString().let { byDate[it] ?: DayStats(it) } }
}

@Composable
private fun AccuracyChart(days: List<DayStats>, modifier: Modifier) {
    Canvas(modifier) {
        val stepX = size.width / (days.size - 1).coerceAtLeast(1)
        // Scale 40% to 100% so day-to-day changes are visible.
        fun y(p: Double) = (size.height - ((p.coerceIn(40.0, 100.0) - 40.0) / 60.0) * size.height).toFloat()
        listOf(50.0, 75.0, 100.0).forEach { g ->
            drawLine(Cream.copy(alpha = 0.12f), Offset(0f, y(g)), Offset(size.width, y(g)), 1f)
        }
        fun series(get: (DayStats) -> Double?, color: Color) {
            var prev: Offset? = null
            days.forEachIndexed { i, d ->
                val v = get(d)
                if (v == null) { prev = null; return@forEachIndexed }
                val pt = Offset(i * stepX, y(v))
                prev?.let { drawLine(color, it, pt, 2.5.dp.toPx()) }
                drawCircle(color, 3.dp.toPx(), pt)
                prev = pt
            }
        }
        series({ it.strategyPct }, Gold)
        series({ it.countPct }, Color(0xFF80CBC4))
    }
}

@Composable
private fun PointsChart(days: List<DayStats>, modifier: Modifier) {
    Canvas(modifier) {
        val max = maxOf(DAILY_GOAL, days.maxOfOrNull { it.points } ?: 0).toFloat()
        val bw = size.width / days.size
        days.forEachIndexed { i, d ->
            val h = d.points / max * size.height
            drawRect(
                if (d.points >= DAILY_GOAL) Gold else Gold.copy(alpha = 0.45f),
                Offset(i * bw + bw * 0.15f, size.height - h), androidx.compose.ui.geometry.Size(bw * 0.7f, h),
            )
        }
        val gy = size.height - DAILY_GOAL / max * size.height
        drawLine(GoodGreen.copy(alpha = 0.7f), Offset(0f, gy), Offset(size.width, gy), 1.5f)
    }
}

fun statsCsv(prefs: Prefs): String {
    val sb = StringBuilder()
    sb.append("date,decisions,correct,count_checks,count_correct,points\n")
    prefs.history().forEach { sb.append("${it.date},${it.decisions},${it.correct},${it.counts},${it.countsOk},${it.points}\n") }
    sb.append("\nlifetime_stat,value\n")
    val st = prefs.loadStats()
    listOf(
        "rounds_played" to st.handsPlayed, "decisions" to st.decisions, "decisions_correct" to st.correctDecisions,
        "count_checks" to st.countQuizzes, "count_checks_correct" to st.countCorrect,
        "true_count_checks" to st.trueQuizzes, "true_count_correct" to st.trueCorrect,
        "count_drills" to st.drillsRun, "count_drills_correct" to st.drillsCorrect, "best_deck_seconds" to st.bestDeckSeconds,
        "strategy_drills" to st.strategyDrills, "strategy_drills_correct" to st.strategyDrillsCorrect,
        "tc_drills" to st.tcDrills, "tc_drills_correct" to st.tcDrillsCorrect,
        "bets" to st.betDecisions, "bets_correct" to st.betCorrect,
        "count_plays" to st.deviationDecisions, "count_plays_correct" to st.deviationCorrect,
        "hints" to st.hintsUsed, "peeks" to st.peeks, "best_streak" to st.bestStrategyStreak,
        "shot_clock_timeouts" to st.slowDecisions, "tray_drills" to st.trayDrills, "tray_correct" to st.trayCorrect,
        "watch_rounds" to st.watchRounds, "casino_tests" to st.casinoTests, "casino_passes" to st.casinoPasses,
        "casino_best_level" to st.casinoBest, "ladder_best_rung" to st.ladderBest + 1,
        "pair_drills" to st.pairDrills, "pair_correct" to st.pairCorrect,
        "reviews" to st.reviews, "reviews_correct" to st.reviewsCorrect,
        "cover_drills" to st.coverDrills, "cover_correct" to st.coverCorrect,
    ).forEach { (k, v) -> sb.append("$k,$v\n") }
    return sb.toString()
}
