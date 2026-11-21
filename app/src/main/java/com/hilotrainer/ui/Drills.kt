package com.hilotrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.BasicStrategy
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.Hand
import com.hilotrainer.engine.Options
import com.hilotrainer.engine.Rank
import com.hilotrainer.engine.Shoe
import com.hilotrainer.engine.Suit
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(label: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (name, value) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(name) })
        }
    }
}

private enum class DrillPhase { SETUP, RUNNING, ANSWER, RESULT }

/** Cards flash by; the player keeps the running count and reports it at the end. */
@Composable
fun CountDrillScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    var numCards by remember { mutableIntStateOf(20) }
    var speedMs by remember { mutableIntStateOf(1000) }
    var perFlash by remember { mutableIntStateOf(1) }
    var tapMode by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(DrillPhase.SETUP) }
    var cards by remember { mutableStateOf(listOf<Card>()) }
    var shown by remember { mutableIntStateOf(0) }
    var answer by remember { mutableIntStateOf(0) }
    var startMs by remember { mutableLongStateOf(0L) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    val isDeckCountdown = numCards == 51
    val sys = remember { prefs.loadSettings().system }

    fun start() {
        val deck = Shoe(1, 1.0)
        cards = List(numCards) { deck.draw() }
        shown = minOf(perFlash, cards.size)
        fx(Fx.DEAL)
        answer = 0
        startMs = System.currentTimeMillis()
        phase = DrillPhase.RUNNING
    }

    fun advance() {
        if (shown >= cards.size) {
            elapsedMs = System.currentTimeMillis() - startMs
            phase = DrillPhase.ANSWER
        } else {
            shown = minOf(shown + perFlash, cards.size)
            fx(Fx.DEAL)
        }
    }

    LaunchedEffect(phase, tapMode) {
        if (phase == DrillPhase.RUNNING && !tapMode) {
            while (phase == DrillPhase.RUNNING) {
                delay(speedMs.toLong())
                advance()
            }
        }
    }

    val actual = cards.sumOf { sys.tag(it) }

    ScreenScaffold("Count drill", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp)) {
                when (phase) {
                    DrillPhase.SETUP -> {
                        Panel {
                            Text("Keep the ${sys.label} running count as cards flash by. Start at 0.", fontSize = 16.sp)
                            Text(sys.valuesText.substringBefore(" Start at"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ChipGroup(
                                "Cards",
                                listOf("10" to 10, "20" to 20, "30" to 30, "Deck countdown (51)" to 51),
                                numCards,
                            ) { numCards = it }
                            ChipGroup("Cards per flash", listOf("1" to 1, "2" to 2, "3" to 3), perFlash) { perFlash = it }
                            ChipGroup("Advance", listOf("Automatic" to false, "Tap (timed)" to true), tapMode) { tapMode = it }
                            if (!tapMode) {
                                ChipGroup(
                                    "Speed per flash",
                                    listOf("1.5s" to 1500, "1.0s" to 1000, "0.7s" to 700, "0.5s" to 500, "0.35s" to 350),
                                    speedMs,
                                ) { speedMs = it }
                            }
                            if (isDeckCountdown) {
                                Text(
                                    "Deck countdown: count down a single deck with one card held back. " +
                                        "A full deck nets to ${signed(sys.deckSum)}, so your count tells you the last card's group.",
                                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { start() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Start") }
                        }
                        val st = prefs.loadStats()
                        Spacer(Modifier.height(12.dp))
                        Panel {
                            Text("Drill accuracy: ${pct(st.drillsCorrect, st.drillsRun)}")
                            if (st.bestDeckSeconds > 0) Text("Best correct deck countdown: ${st.bestDeckSeconds}s")
                        }
                    }

                    DrillPhase.RUNNING -> {
                        val group = cards.subList(maxOf(0, shown - perFlash), shown)
                        Text("Card $shown of ${cards.size}", modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(16.dp))
                        BoxWithConstraints(
                            Modifier.fillMaxWidth().heightIn(min = 260.dp)
                                .clip(RoundedCornerShape(18.dp)).background(Color(0xFF13693F))
                                ,
                            contentAlignment = Alignment.Center,
                        ) {
                            val w = (maxWidth / (perFlash + 1.2f)).coerceAtMost(150.dp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                group.forEach { PlayingCard(it, w) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        if (tapMode) {
                            GoldButton("Next card", Modifier.fillMaxWidth().height(64.dp)) { advance() }
                            Text("The button sits below the cards so your thumb never covers them.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }
                    }

                    DrillPhase.ANSWER -> Panel {
                        Text("What's the running count?", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Spacer(Modifier.height(10.dp))
                        NumberStepper(answer, { answer = it }, Modifier.fillMaxWidth(), range = -60..60)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            val ok = answer == actual
                            fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
                            val secs = (elapsedMs / 1000).toInt()
                            prefs.track(counts = 1, countsOk = if (ok) 1 else 0)
                            prefs.updateStats {
                                val best = if (ok && isDeckCountdown && tapMode && (it.bestDeckSeconds == 0 || secs < it.bestDeckSeconds)) secs else it.bestDeckSeconds
                                prefs.addPoints(2)
                                it.copy(drillsRun = it.drillsRun + 1, drillsCorrect = it.drillsCorrect + if (ok) 1 else 0, bestDeckSeconds = best)
                            }
                            phase = DrillPhase.RESULT
                        }, modifier = Modifier.fillMaxWidth()) { Text("Check") }
                    }

                    DrillPhase.RESULT -> {
                        Panel {
                            if (answer == actual) {
                                Text("Correct! Running count ${signed(actual)}", color = GoodGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            } else {
                                Text("Running count was ${signed(actual)} (you said ${signed(answer)})", color = BadRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            if (tapMode) Text("Time: %.1fs".format(elapsedMs / 1000.0))
                            if (isDeckCountdown) {
                                val held = heldBackCard(cards)
                                Text("Held-back card: $held (tag ${signed(sys.tag(held))}). Count plus its tag is ${signed(sys.deckSum)}.")
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Count after each card:", fontWeight = FontWeight.SemiBold)
                            Text(runningTrail(cards, sys), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { start() }, modifier = Modifier.weight(1f)) { Text("Again") }
                            Button(onClick = { phase = DrillPhase.SETUP }, modifier = Modifier.weight(1f)) { Text("Change setup") }
                        }
                    }
                }
            }
        }
    }
}

private fun heldBackCard(cards: List<Card>): Card {
    val all = Suit.entries.flatMap { s -> Rank.entries.map { Card(it, s) } }
    return all.first { it !in cards }
}

fun runningTrail(cards: List<Card>, sys: com.hilotrainer.engine.CountSystem, start: Int = 0): String {
    var rc = start
    return cards.joinToString("   ") { rc += sys.tag(it); "$it ${signed(rc)}" }
}

/** Running count plus discard tray; the player converts to a true count. */
@Composable
fun TrueCountDrillScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    data class Q(val decks: Int, val dealtHalfDecks: Int, val running: Int) {
        val remaining get() = decks - dealtHalfDecks / 2.0
        val exact get() = running / remaining
    }
    fun newQ(): Q {
        val decks = listOf(6, 8).random()
        val dealt = Random.nextInt(1, decks * 2 - 1)
        val spread = 4 + dealt
        return Q(decks, dealt, Random.nextInt(-spread, spread + 1))
    }
    var q by remember { mutableStateOf(newQ()) }
    var answer by remember { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(false) }
    var streak by remember { mutableIntStateOf(0) }
    var ok by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }

    ScreenScaffold("True count drill", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp)) {
                if (!prefs.loadSettings().system.balanced) {
                    Panel {
                        Text("You're set to KO, which skips the true count. This drill uses balanced counting like Hi-Lo or Zen, so it's still useful if you plan to switch.", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Panel {
                    Text("Running count ${signed(q.running)}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ShoeBar(q.dealtHalfDecks * 26, q.decks * 52)
                    Spacer(Modifier.height(8.dp))
                    Text("Estimate decks left, then divide. Answer as a whole number.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    NumberStepper(answer, { if (!checked) answer = it }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    if (!checked) {
                        Button(onClick = {
                            checked = true
                            val good = abs(answer - q.exact) < 1.0
                            total++
                            if (good) { ok++; streak++ } else streak = 0
                            fx(if (good) Fx.CORRECT else Fx.MISTAKE)
                            prefs.addPoints(1)
                            prefs.updateStats { it.copy(tcDrills = it.tcDrills + 1, tcDrillsCorrect = it.tcDrillsCorrect + if (good) 1 else 0) }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Check") }
                    } else {
                        val good = abs(answer - q.exact) < 1.0
                        Text(
                            (if (good) "Correct. " else "Not quite. ") +
                                "${signed(q.running)} / %.1f decks left = ${signed1(q.exact)}".format(q.remaining),
                            color = if (good) GoodGreen else BadRed, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { q = newQ(); answer = 0; checked = false }, modifier = Modifier.fillMaxWidth()) { Text("Next") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Panel {
                    Text("This session: ${pct(ok, total)}   Streak: $streak")
                    Text(
                        "Answers within 1 of the exact value count as correct, since tray reading is an estimate.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Random starting hands; pick the right play. With count plays on, each hand comes with a true count. */
@Composable
fun StrategyDrillScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    val settings = remember { prefs.loadSettings() }
    val rules = settings.rules()
    val useDev = settings.deviations && settings.system == com.hilotrainer.engine.CountSystem.HI_LO
    val opts = Options(canDouble = true, canSplit = true, canSurrender = rules.surrenderAllowed)
    data class Q(val hand: Hand, val up: Card, val tc: Double?)
    fun newQ(): Q {
        fun card(r: Rank) = Card(r, Suit.entries.random())
        val tens = listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING)
        val nonAce = Rank.entries.filter { !it.isAce }
        fun rankOf(v: Int) = if (v == 10) tens.random() else Rank.entries.first { it.value == v }
        fun hardOf(t: Int): Hand {
            var a: Int; var b: Int
            do { a = Random.nextInt(2, 11); b = t - a } while (b !in 2..10 || a == b)
            return Hand(listOf(card(rankOf(a)), card(rankOf(b))))
        }
        if (useDev && Random.nextInt(10) < 5) {
            // A situation from the Illustrious 18 / Fab 4.
            val spots = listOf(16 to 10, 15 to 10, 10 to 10, 12 to 3, 12 to 2, 11 to 11, 9 to 2, 10 to 11, 9 to 7, 16 to 9, 13 to 2, 12 to 4, 12 to 5, 12 to 6, 13 to 3, 14 to 10, 15 to 9, 15 to 11, 20 to 5, 20 to 6)
            val (t, u) = spots.random()
            val hand = if (t == 20) Hand(listOf(card(tens.random()), card(tens.random()))).let { h -> if (h.isPair) h else Hand(listOf(card(Rank.TEN), card(Rank.KING))) } else hardOf(t)
            val up = card(if (u == 11) Rank.ACE else rankOf(u))
            return Q(hand, up, Random.nextInt(-4, 8).toDouble() + listOf(0.0, 0.5).random())
        }
        val hand = when (Random.nextInt(10)) {
            0, 1 -> { val r = Rank.entries.random(); Hand(listOf(card(r), card(r))) }
            2, 3, 4 -> Hand(listOf(card(Rank.ACE), card(nonAce.filter { it.value <= 9 }.random())))
            else -> {
                var a: Rank; var b: Rank
                do { a = nonAce.random(); b = nonAce.random() } while (a.value == b.value || a.value + b.value > 20)
                Hand(listOf(card(a), card(b)))
            }
        }
        val up = card(Rank.entries.random().let { if (it in tens) tens.random() else it })
        return Q(hand, up, if (useDev) Random.nextInt(-3, 7).toDouble() else null)
    }
    var q by remember { mutableStateOf(newQ()) }
    var picked by remember { mutableStateOf<Action?>(null) }
    var ok by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    val hand = q.hand
    val up = q.up
    val advice = BasicStrategy.advise(hand, up, rules, opts, q.tc)

    ScreenScaffold("Strategy drill", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33000000)).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DEALER", fontSize = 12.sp, letterSpacing = 2.sp, color = Gold)
                        PlayingCard(up, 80.dp)
                        Spacer(Modifier.height(14.dp))
                        CardRow(hand.cards, 80.dp)
                        Text(if (hand.isSoft) "Soft ${hand.total}" else "Hard ${hand.total}", modifier = Modifier.padding(top = 4.dp))
                        q.tc?.let {
                            Text("True count ${signed1(it)}", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Gold, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val actions = buildList {
                    add(Action.HIT); add(Action.STAND); add(Action.DOUBLE)
                    if (hand.isPair) add(Action.SPLIT)
                    if (rules.surrenderAllowed) add(Action.SURRENDER)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    actions.forEach { a ->
                        GoldButton(a.label, Modifier.weight(1f), enabled = picked == null) {
                            picked = a
                            total++
                            val good = a == advice.action
                            if (good) { ok++; streak++ } else streak = 0
                            fx(if (good) Fx.CORRECT else Fx.MISTAKE)
                            prefs.addPoints(1)
                            prefs.track(decisions = 1, correct = if (good) 1 else 0)
                            if (!good && !advice.isDeviation) com.hilotrainer.engine.Review.keyFor(hand, up)?.let { prefs.reviewMissed(it) }
                            prefs.updateStats { it.copy(strategyDrills = it.strategyDrills + 1, strategyDrillsCorrect = it.strategyDrillsCorrect + if (good) 1 else 0) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                picked?.let { p ->
                    Panel {
                        if (p == advice.action) {
                            Text((if (advice.isDeviation) "Count play! " else "Correct: ") + advice.reason, color = if (advice.isDeviation) Gold else GoodGreen, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("You picked ${p.label}. Correct: ${advice.action.label}", color = BadRed, fontWeight = FontWeight.SemiBold)
                            Text(advice.reason)
                        }
                        Spacer(Modifier.height(8.dp))
                        GoldButton("Next hand", Modifier.fillMaxWidth()) { q = newQ(); picked = null }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Panel {
                    Text("This session: ${pct(ok, total)}   Streak: $streak")
                    Text(
                        "Rules from Settings: ${rules.decks} decks, dealer ${if (rules.dealerHitsSoft17) "hits" else "stands on"} soft 17, " +
                            "${if (rules.doubleAfterSplit) "double after split" else "no double after split"}, ${if (rules.surrenderAllowed) "late surrender" else "no surrender"}" +
                            if (useDev) ", count plays on." else ". Turn on Count plays in Settings to drill the Illustrious 18.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}


/** Estimate decks left in the shoe from the height of the discard tray. */
@Composable
fun TrayDrillScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    data class Q(val decks: Int, val dealtCards: Int) {
        val remaining get() = (decks * 52 - dealtCards) / 52.0
    }
    fun newQ(): Q {
        val decks = listOf(6, 8).random()
        return Q(decks, Random.nextInt(26, decks * 52 - 39))
    }
    var q by remember { mutableStateOf(newQ()) }
    var answer by remember { mutableStateOf<Double?>(null) }
    var ok by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    ScreenScaffold("Discard tray drill", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${q.decks}-deck shoe. How many decks are left to play?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    TrayGraphic(q.dealtCards, q.decks, "Discard tray")
                    if (answer != null) TrayGraphic(q.decks * 52 - q.dealtCards, q.decks, "Still in the shoe")
                }
                Spacer(Modifier.height(14.dp))
                val choices = (1..(q.decks * 2 - 1)).map { it / 2.0 }
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { c ->
                        val picked = answer == c
                        FilterChip(
                            selected = picked,
                            onClick = {
                                if (answer == null) {
                                    answer = c
                                    total++
                                    val good = abs(c - q.remaining) <= 0.5
                                    if (good) ok++
                                    fx(if (good) Fx.CORRECT else Fx.MISTAKE)
                                    prefs.addPoints(1)
                                    prefs.updateStats { it.copy(trayDrills = it.trayDrills + 1, trayCorrect = it.trayCorrect + if (good) 1 else 0) }
                                }
                            },
                            label = { Text(if (c % 1.0 == 0.0) "${c.toInt()}" else "%.1f".format(c), fontSize = 16.sp) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                answer?.let { a ->
                    val good = abs(a - q.remaining) <= 0.5
                    Panel {
                        Text(
                            (if (good) "Good estimate. " else "Not quite. ") + "%.1f decks were left (%d cards).".format(q.remaining, q.decks * 52 - q.dealtCards),
                            color = if (good) GoodGreen else BadRed, fontWeight = FontWeight.SemiBold,
                        )
                        Text("Within half a deck counts as correct.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        GoldButton("Next", Modifier.fillMaxWidth()) { q = newQ(); answer = null }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Panel { Text("This session: ${pct(ok, total)}") }
            }
        }
    }
}

@Composable
private fun TrayGraphic(cards: Int, decks: Int, label: String) {
    val maxH = 220.dp
    val h = maxH * (cards / (decks * 52f))
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp, maxH).clip(RoundedCornerShape(6.dp)).background(Color(0x55000000))
                .border(2.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(h).padding(horizontal = 6.dp)) {
                drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFBFB6A2), Color(0xFFF4EEDF), Color(0xFFBFB6A2))))
                var y = size.height
                while (y > 0) {
                    drawLine(Color(0x22000000), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                    y -= 2.5f
                }
            }
        }
        Text(label, fontSize = 12.sp, color = Cream.copy(alpha = 0.75f))
    }
}
