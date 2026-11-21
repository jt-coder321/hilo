package com.hilotrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.BasicStrategy
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.Course
import com.hilotrainer.engine.ErrorMap
import com.hilotrainer.engine.Options
import com.hilotrainer.engine.Review
import com.hilotrainer.engine.SideBets
import com.hilotrainer.engine.Suit
import com.hilotrainer.engine.TeamSignals
import kotlin.random.Random

@Composable
private fun Col(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun H(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)

@Composable
private fun Small(text: String) = Text(text, fontSize = 13.sp, color = Cream.copy(alpha = 0.72f))

// ---------------------------------------------------------------------------
// Guided course
// ---------------------------------------------------------------------------

@Composable
fun CourseScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}, onOpen: (Screen) -> Unit = {}) {
    var passed by remember { mutableIntStateOf(prefs.coursePassed) }
    var open by remember { mutableStateOf<Int?>(null) }
    val settings = remember { prefs.loadSettings() }
    val stats = remember(passed, open) { prefs.loadStats() }

    // Lessons checked against your stats complete themselves.
    fun autoPass(i: Int): Boolean = when (Course.LESSONS[i].kind) {
        Course.Kind.CASINO_TEST -> stats.casinoBest >= 0
        Course.Kind.TABLE -> stats.countQuizzes >= 20 && stats.countCorrect * 100 >= stats.countQuizzes * 80
        Course.Kind.QUIZ -> false
    }
    if (passed < Course.LESSONS.size && autoPass(passed)) { passed++; prefs.coursePassed = passed }

    ScreenScaffold("Guided course", { if (open != null) open = null else onBack() }) {
        val lesson = open
        if (lesson == null) {
            Col {
                Panel {
                    H("Step by step")
                    Text("Ten lessons from card values to a full table session. Pass each one (${Course.PASS} of ${Course.QUESTIONS}) to unlock the next.")
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Cream.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(passed / Course.LESSONS.size.toFloat()).height(10.dp).clip(RoundedCornerShape(50)).background(Gold))
                    }
                    Small("$passed of ${Course.LESSONS.size} lessons passed")
                }
                Course.LESSONS.forEachIndexed { i, l ->
                    val locked = i > passed
                    val done = i < passed
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape).background(Ink.copy(alpha = if (locked) 0.3f else 0.55f))
                            .border(1.dp, (if (done) GoodGreen else Gold).copy(alpha = if (locked) 0.15f else 0.5f), shape)
                            .clickable(enabled = !locked) { open = i }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(if (done) GoodGreen else if (locked) Cream.copy(alpha = 0.15f) else Gold),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (done) "✓" else "${i + 1}", fontWeight = FontWeight.Bold, color = Ink) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(l.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (locked) Cream.copy(alpha = 0.4f) else Cream)
                            Text(if (locked) "Locked: pass lesson $i first" else if (done) "Passed" else "Up next", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        } else {
            LessonView(prefs, lesson, settings, fx, onOpen) { ok ->
                if (ok && lesson == passed) { passed++; prefs.coursePassed = passed }
                open = null
            }
        }
    }
}

@Composable
private fun LessonView(prefs: Prefs, index: Int, settings: AppSettings, fx: (Fx) -> Unit, onOpen: (Screen) -> Unit, onDone: (Boolean) -> Unit) {
    val lesson = Course.LESSONS[index]
    var qs by remember { mutableStateOf<List<Course.Question>?>(null) }
    var i by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    Col {
        Panel {
            Text("Lesson ${index + 1}", fontSize = 12.sp, color = Gold)
            H(lesson.title)
            Text(lesson.teach)
            if (index == 0 || index == 4) Small(settings.system.valuesText)
        }
        val list = qs
        when {
            lesson.kind == Course.Kind.CASINO_TEST -> GoldButton("Open the casino test", Modifier.fillMaxWidth()) { onOpen(Screen.CASINO_TEST) }
            lesson.kind == Course.Kind.TABLE -> GoldButton("Go to the table", Modifier.fillMaxWidth()) { onOpen(Screen.PLAY) }
            list == null -> GoldButton("Start the quiz (${Course.QUESTIONS} questions)", Modifier.fillMaxWidth().height(56.dp)) {
                qs = Course.questions(index, Random.Default, settings.rules(), settings.system, settings.ramp)
                i = 0; right = 0; picked = null
            }
            i >= list.size -> {
                val ok = right >= Course.PASS
                Panel {
                    Text(if (ok) "Passed: $right of ${list.size}" else "$right of ${list.size}. You need ${Course.PASS} to pass.", color = if (ok) GoodGreen else BadRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    if (ok) GoldButton("Back to the course", Modifier.fillMaxWidth()) { onDone(true) }
                    else GoldButton("Try again", Modifier.fillMaxWidth()) {
                        qs = Course.questions(index, Random.Default, settings.rules(), settings.system, settings.ramp); i = 0; right = 0; picked = null
                    }
                }
            }
            else -> {
                val q = list[i]
                Text("Question ${i + 1} of ${list.size}", color = Cream.copy(alpha = 0.7f))
                if (q.cards.isNotEmpty() || q.dealer != null) {
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33000000)).padding(14.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            q.dealer?.let { Text("DEALER", fontSize = 11.sp, letterSpacing = 2.sp, color = Gold); PlayingCard(it, 70.dp); Spacer(Modifier.height(10.dp)) }
                            if (q.cards.isNotEmpty()) CardRow(q.cards, if (q.cards.size > 3) 56.dp else 76.dp)
                        }
                    }
                }
                Text(q.prompt, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                q.options.forEachIndexed { k, opt ->
                    val col = when { picked == null -> Gold; k == q.answer -> GoodGreen; k == picked -> BadRed; else -> Gold.copy(alpha = 0.25f) }
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (picked != null && (k == q.answer || k == picked)) col.copy(alpha = 0.22f) else Ink.copy(alpha = 0.55f))
                            .border(1.dp, col.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .clickable(enabled = picked == null) {
                                picked = k
                                val good = k == q.answer
                                if (good) right++
                                fx(if (good) Fx.CORRECT else Fx.MISTAKE)
                                prefs.addPoints(1)
                            }.padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) { Text(opt, fontSize = 16.sp) }
                }
                picked?.let { p ->
                    Panel {
                        Text(if (p == q.answer) "Correct." else "Answer: ${q.options[q.answer]}.", color = if (p == q.answer) GoodGreen else BadRed, fontWeight = FontWeight.SemiBold)
                        if (q.explain.isNotBlank()) Text(q.explain, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        GoldButton("Next", Modifier.fillMaxWidth()) { i++; picked = null }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Full Hi-Lo index chart
// ---------------------------------------------------------------------------

@Composable
fun IndexChartScreen(prefs: Prefs, onBack: () -> Unit) {
    val s = remember { prefs.loadSettings() }
    ScreenScaffold("Hi-Lo index chart", onBack) {
        Col {
            Panel {
                H("How to read it")
                Text("At a true count equal to or above the number, make the play shown. Below it, play basic strategy. Six-deck Hi-Lo values.")
                Small(if (s.deviations) (if (s.fullIndices) "The coach uses this whole chart (Settings: Count plays, Full index chart)." else "The coach uses the Illustrious 18 and Fab 4. Turn on Full index chart in Settings to add the extra plays.") else "Count plays are off in Settings, so the coach uses basic strategy.")
            }
            Panel {
                H("Illustrious 18")
                IndexRow("Insurance", "Insure", "+3")
                listOf(
                    Triple("16 vs 10", "Stand", "0"), Triple("15 vs 10", "Stand", "+4"), Triple("10,10 vs 5", "Split", "+5"),
                    Triple("10,10 vs 6", "Split", "+4"), Triple("10 vs 10", "Double", "+4"), Triple("12 vs 3", "Stand", "+2"),
                    Triple("12 vs 2", "Stand", "+3"), Triple("11 vs A", "Double", "+1"), Triple("9 vs 2", "Double", "+1"),
                    Triple("10 vs A", "Double", "+4"), Triple("9 vs 7", "Double", "+3"), Triple("16 vs 9", "Stand", "+5"),
                    Triple("13 vs 2", "Stand", "-1"), Triple("12 vs 4", "Stand", "0"), Triple("12 vs 5", "Stand", "-2"),
                    Triple("12 vs 6", "Stand", "-1"), Triple("13 vs 3", "Stand", "-2"),
                ).forEach { (h, a, i) -> IndexRow(h, a, i) }
                Small("11 vs A only matters when the dealer stands on soft 17. With hits soft 17, basic strategy already doubles it.")
            }
            Panel {
                H("Fab 4 surrenders")
                listOf(Triple("14 vs 10", "Surrender", "+3"), Triple("15 vs 10", "Surrender", "0"), Triple("15 vs 9", "Surrender", "+2"), Triple("15 vs A", "Surrender", "+1"))
                    .forEach { (h, a, i) -> IndexRow(h, a, i) }
                Small("Source for both lists: blackjackinfo.com and Wizard of Odds Hi-Lo pages.")
            }
            Panel {
                H("More index plays")
                BasicStrategy.EXTENDED.forEach { e ->
                    IndexRow(e.label, e.atOrAbove.label, if (e.index > 0) "+${e.index}" else "${e.index}", if (e.s17Only) "dealer stands on soft 17" else null)
                }
                Small("Each of these has the same number in both the blackjackstrategy.io and blackjacktrainer.fyi six-deck charts for a dealer who stands on soft 17. Where those two charts disagree I left the play out, so this isn't every index that exists. Published charts can differ by a point because of rounding.")
            }
        }
    }
}

@Composable
private fun IndexRow(hand: String, action: String, index: String, note: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(hand, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(action, modifier = Modifier.weight(1f), color = Cream.copy(alpha = 0.85f))
        Box(Modifier.width(64.dp).clip(RoundedCornerShape(8.dp)).background(Gold.copy(alpha = 0.2f)).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(index, fontWeight = FontWeight.Bold, color = Gold)
        }
    }
    note?.let { Text("  only when $it", fontSize = 11.sp, color = Cream.copy(alpha = 0.55f)) }
}

// ---------------------------------------------------------------------------
// Error heat map
// ---------------------------------------------------------------------------

private val UPS = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "A")

@Composable
fun HeatMapScreen(prefs: Prefs, onBack: () -> Unit, onPractice: () -> Unit = {}) {
    val map = remember { prefs.errorMap() }
    val byTc = remember { prefs.errorByCount() }
    ScreenScaffold("Error heat map", onBack) {
        Col {
            Panel {
                H("Where your mistakes are")
                Text("Each square is one hand against one dealer card. Darker red means you get it wrong more often. Empty squares haven't come up yet.", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                GoldButton("Practice my weak spots", Modifier.fillMaxWidth(), enabled = ErrorMap.weakSpots(map).isNotEmpty()) { onPractice() }
            }
            val rows = listOf(
                "Hard" to (17 downTo 8).map { "H$it" to "$it" },
                "Soft" to (20 downTo 13).map { "S$it" to "A,${it - 11}" },
                "Pairs" to listOf("A", "10", "9", "8", "7", "6", "5", "4", "3", "2").map { "P$it" to "$it,$it" },
            )
            rows.forEach { (title, list) ->
                Panel {
                    H(title)
                    Column(Modifier.horizontalScroll(rememberScrollState())) {
                        Row { HeatCell("", null, header = true); UPS.forEach { HeatCell(it, null, header = true) } }
                        list.forEach { (prefix, label) ->
                            Row {
                                HeatCell(label, null, header = true)
                                UPS.forEach { u -> HeatCell("", map["${prefix}v$u"]) }
                            }
                        }
                    }
                }
            }
            Panel {
                H("Mistakes by true count")
                Small("Hi-Lo true count when you made each play.")
                ErrorMap.TC_BUCKETS.forEachIndexed { i, label ->
                    val c = byTc[i.toString()]
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.width(96.dp), fontSize = 14.sp)
                        Box(Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(50)).background(Cream.copy(alpha = 0.1f))) {
                            if (c != null && c.n > 0) Box(Modifier.fillMaxWidth(c.rate.toFloat().coerceAtLeast(0.02f)).height(16.dp).clip(RoundedCornerShape(50)).background(heatColor(c.rate)))
                        }
                        Text(c?.let { "${(it.rate * 100).toInt()}% of ${it.n}" } ?: "none", fontSize = 12.sp, modifier = Modifier.width(84.dp).padding(start = 8.dp), color = Cream.copy(alpha = 0.75f))
                    }
                }
            }
        }
    }
}

private fun heatColor(rate: Double): Color = lerp(Color(0xFF2E7D4F), Color(0xFFD32F2F), (rate * 2).toFloat().coerceIn(0f, 1f))

@Composable
private fun HeatCell(text: String, cell: ErrorMap.Cell?, header: Boolean = false) {
    val bg = when {
        header -> Color.Transparent
        cell == null || cell.n == 0 -> Cream.copy(alpha = 0.06f)
        else -> heatColor(cell.rate)
    }
    Box(
        Modifier.size(width = if (header) 40.dp else 30.dp, height = 28.dp).padding(1.dp).clip(RoundedCornerShape(4.dp)).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val t = if (header) text else cell?.takeIf { it.wrong > 0 }?.wrong?.toString() ?: ""
        Text(t, fontSize = if (header) 12.sp else 11.sp, fontWeight = if (header) FontWeight.Bold else FontWeight.SemiBold, color = Cream)
    }
}

// ---------------------------------------------------------------------------
// Weak-spot practice
// ---------------------------------------------------------------------------

@Composable
fun WeakSpotScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    val settings = remember { prefs.loadSettings() }
    val rules = settings.rules()
    var spots by remember { mutableStateOf(ErrorMap.weakSpots(prefs.errorMap())) }
    fun pick(): String? {
        if (spots.isEmpty()) return null
        // Weighted by how often each spot goes wrong.
        val weights = spots.map { it.second.wrong + 1.0 }
        var r = Random.nextDouble(weights.sum())
        spots.forEachIndexed { i, s -> r -= weights[i]; if (r <= 0) return s.first }
        return spots.last().first
    }
    var key by remember { mutableStateOf(pick()) }
    var picked by remember { mutableStateOf<Action?>(null) }
    var ok by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    ScreenScaffold("Weak-spot practice", onBack) {
        Col {
            val k = key
            val built = k?.let { Review.build(it) }
            if (k == null || built == null) {
                Panel {
                    H("No weak spots yet")
                    Text("Play some hands or run the strategy drill. Every play you get wrong is mapped, and this drill keeps serving up the ones you miss most.")
                }
            } else {
                val (hand, up) = built
                val opts = Options(canDouble = true, canSplit = hand.isPair, canSurrender = rules.surrenderAllowed)
                val advice = BasicStrategy.basic(hand, up, rules, opts)
                val cell = spots.firstOrNull { it.first == k }?.second
                Text("${Review.describe(k)} · missed ${cell?.wrong ?: 0} of ${cell?.n ?: 0}", color = Cream.copy(alpha = 0.75f))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33000000)).padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("DEALER", fontSize = 12.sp, letterSpacing = 2.sp, color = Gold)
                        PlayingCard(up, 80.dp)
                        Spacer(Modifier.height(14.dp))
                        CardRow(hand.cards, 80.dp)
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
                            total++
                            val good = a == advice.action
                            if (good) ok++
                            fx(if (good) Fx.CORRECT else Fx.MISTAKE)
                            prefs.addPoints(1)
                            prefs.track(decisions = 1, correct = if (good) 1 else 0)
                            prefs.recordPlay(k, null, good)
                            prefs.updateStats { it.copy(weakSpotDrills = it.weakSpotDrills + 1, weakSpotCorrect = it.weakSpotCorrect + if (good) 1 else 0) }
                        }
                    }
                }
                picked?.let { p ->
                    Panel {
                        if (p == advice.action) Text("Correct: ${advice.reason}", color = GoodGreen, fontWeight = FontWeight.SemiBold)
                        else Text("Correct play: ${advice.action.label}. ${advice.reason}.", color = BadRed, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        GoldButton("Next", Modifier.fillMaxWidth()) {
                            spots = ErrorMap.weakSpots(prefs.errorMap())
                            key = pick(); picked = null
                        }
                    }
                }
            }
            Panel {
                Text("This session: ${pct(ok, total)}")
                Small("Built from your error heat map, weighted toward the plays you miss most. Basic strategy for your table rules.")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Side bets
// ---------------------------------------------------------------------------

@Composable
fun SideBetsScreen(prefs: Prefs, onBack: () -> Unit) {
    val decks = remember { prefs.loadSettings().decks }
    ScreenScaffold("Side bets", onBack) {
        Col {
            val ppFresh = SideBets.perfectPairsEv(SideBets.unseen(decks, emptyList()))
            val insFresh = SideBets.insuranceEv(SideBets.unseen(decks, listOf(Card(com.hilotrainer.engine.Rank.ACE, Suit.SPADES))))
            Panel {
                H("Perfect Pairs")
                Text("A side bet on your first two cards being a pair. Common pay table: same suit 25 to 1, same color 12 to 1, mixed colors 6 to 1.")
                Spacer(Modifier.height(6.dp))
                Text("House edge off the top of a $decks-deck shoe: ${"%.2f".format(-ppFresh * 100)}%", fontWeight = FontWeight.SemiBold, color = BadRed)
                Small("Worked out exactly by the app from the cards in the shoe. That's a much bigger edge than the main game. Turn it on in Settings to try it, and the coach shows its value for the cards left at any moment. It only turns positive when the shoe is unusually rich in matching cards, which is rare.")
            }
            Panel {
                H("Insurance and even money")
                Text("Insurance pays 2 to 1 if the dealer's hole card is a ten. Even money is the same bet when you have blackjack.")
                Spacer(Modifier.height(6.dp))
                Text("Value off the top with the dealer's ace out: ${"%+.2f".format(insFresh * 100)}%", fontWeight = FontWeight.SemiBold, color = BadRed)
                Small("Also worked out from the shoe. It's a losing bet until enough low cards are gone, which is why Hi-Lo players insure at true count +3 or higher. At the table, the Hint button shows the exact insurance value for the cards still unseen.")
            }
            Panel {
                H("The verdict")
                Text("Skip side bets as a rule. The main game is where a counter's edge comes from. Practice them here so you know the cost when you see them at a table.")
                Small("Perfect Pairs pay table from PokerNews, which puts its house edge at 5% to 7% depending on the casino and pay table.")
            }
            Panel {
                H("Team play signal code")
                Small("For team play practice (table menu). Real teams make their own; this is the trainer's code.")
                TeamSignals.SIGNALS.forEach { Text("${it.gesture}: ${it.meaning}", fontSize = 14.sp) }
            }
        }
    }
}
