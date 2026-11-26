package com.hilotrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hilotrainer.engine.Rules
import com.hilotrainer.engine.Suit
import com.hilotrainer.engine.TrueCount

enum class Screen(val title: String) {
    HOME("Hi-Lo Trainer"), PLAY("Play blackjack"), COUNT_DRILL("Count drill"),
    TRUE_DRILL("True count drill"), STRATEGY_DRILL("Strategy drill"),
    CHART("Strategy chart"), LEARN("How Hi-Lo works"), STATS("Stats"), SETTINGS("Settings"),
    MISTAKES("Mistake review"), TRAY_DRILL("Discard tray drill"),
    CASINO_TEST("Casino test"), SPEED_LADDER("Speed ladder"), PAIR_DRILL("Pair counting"),
    REVIEW("Review missed plays"), COVER_DRILL("Cover play"), BANKROLL("Bankroll and risk"), PROGRESS("Progress"),
    COURSE("Guided course"), INDEX_CHART("Hi-Lo index chart"), HEAT_MAP("Error heat map"), WEAK_SPOTS("Weak-spot practice"),
    SIDE_BETS("Side bets"), BACK_COUNT("Back-counting"), TEAM_PLAY("Team play"),
    DASHBOARD("Stats dashboard"), BADGES("Badges and bests"), WEEKLY("Weekly goal"), QUICK_DRILL("1-minute drill"),
    HAPTIC_DRILL("Haptic count drill"), DECK_ESTIMATE("Deck estimation"), KELLY("Kelly bet sizing"),
    CAREER("Career mode"), DAILY("Daily challenge"), SESSION("One-hour session"), MID_SHOE("Join mid-shoe"),
    JUST_PLAY("Just Play"), HUB_TRAIN("Drills"), HUB_LEARN("Learn"), HUB_MODES("Challenges"), HUB_PROGRESS("Progress"),
}


@Composable
internal fun QuickTour(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        "Welcome to Hi-Lo Trainer" to "Practice blackjack basic strategy and Hi-Lo card counting at a realistic table. Your mistakes are coached, never punished.",
        "At the table" to "Tap Peek count at the top to see the count for a few seconds. The strip above the buttons shows coaching and a Hint button. Tap the table to skip the dealing animation.",
        "Counting practice" to "Count every card, including other players' cards and the dealer's hole card when it flips. After rounds you'll be asked the count. If you miss, open the Count audit to see where it slipped.",
        "Build the habit" to "Start with the strategy drill and count drill, then play with the count hidden. Earn 50 practice points a day to keep your streak going.",
    )
    AlertDialog(
        onDismissRequest = {},
        containerColor = FeltDark,
        title = { Text(pages[page].first, fontFamily = TitleFont, color = Gold) },
        text = {
            Column {
                Text(pages[page].second, color = Cream)
                Spacer(Modifier.height(10.dp))
                Text("${page + 1} of ${pages.size}", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
            }
        },
        confirmButton = {
            GoldButton(if (page == pages.size - 1) "Let's play" else "Next", Modifier.width(130.dp)) {
                if (page == pages.size - 1) onDone() else page++
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Skip", color = Cream) } },
    )
}

@Composable
private fun MenuTile(title: String, sub: String, suit: Suit, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.height(68.dp).clip(shape).background(Ink.copy(alpha = 0.55f)).border(1.dp, Gold.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Cream), contentAlignment = Alignment.Center) {
            SuitIcon(suit, 20.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Cream)
            Text(sub, fontSize = 12.sp, color = Cream.copy(alpha = 0.7f), maxLines = 1)
        }
    }
}

@Composable
fun LearnScreen(onBack: () -> Unit) {
    ScreenScaffold("How counting works", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel {
                    Text("1. Card values", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("2, 3, 4, 5, 6 count +1\n7, 8, 9 count 0\n10, J, Q, K, A count -1")
                    Text("A full deck adds up to 0. A positive count means more tens and aces are left, which helps the player.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel {
                    Text("2. Running count", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Start at 0 after the shuffle and add the value of every card you see, including the dealer's hole card when it flips and other players' cards.")
                    Text("Tip: cancel pairs. A 5 and a K together are 0, so you can skip them.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel {
                    Text("3. True count", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("True count = running count / decks left in the shoe. Estimate decks left from the discard tray. Example: running count +6 with 3 decks left is a true count of +2.")
                }
                Panel {
                    Text("4. Betting", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Bet the minimum when the true count is +1 or lower, then raise your bet as it climbs. The trainer's bet hint uses a common 1 to 8 unit ramp: +2 is 2 units, +3 is 4, +4 is 6, +5 or more is 8. Treat it as practice, not a guarantee.")
                }
                Panel {
                    Text("5. Insurance", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Basic strategy says never take insurance. With Hi-Lo, take it when the true count is +3 or higher. This is the most valuable count-based change to basic strategy.")
                }
                Panel {
                    Text("Count plays: Illustrious 18 and Fab 4", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("At certain true counts the best play changes. Turn on Count plays in Settings to be coached on these. Six-deck Hi-Lo values:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "Insurance: take at +3\n16 vs 10: stand at 0 or more\n15 vs 10: stand at +4\n10,10 vs 5: split at +5\n10,10 vs 6: split at +4\n" +
                            "10 vs 10: double at +4\n12 vs 3: stand at +2\n12 vs 2: stand at +3\n11 vs A: double at +1 (dealer stands on soft 17)\n" +
                            "9 vs 2: double at +1\n10 vs A: double at +4\n9 vs 7: double at +3\n16 vs 9: stand at +5\n13 vs 2: stand at -1 or more\n" +
                            "12 vs 4: stand at 0 or more\n12 vs 5: stand at -2 or more\n12 vs 6: stand at -1 or more\n13 vs 3: stand at -2 or more",
                        fontSize = 14.sp,
                    )
                    Text("Fab 4 surrenders (when allowed): 14 vs 10 at +3, 15 vs 10 at 0, 15 vs 9 at +2, 15 vs A at +1.", fontSize = 14.sp)
                    Text("Source: blackjackinfo.com Illustrious 18 and Fab 4 chart.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel {
                    Text("Other counts: KO and Zen", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Pick your count in Settings. Drills, the table, the count audit and bet coaching all follow it.")
                    Text("KO (Knock-Out): " + com.hilotrainer.engine.CountSystem.KO.valuesText + " A 6-deck shoe starts at -20, an 8-deck shoe at -28. Because 7s count +1, a full deck adds up to +4, so the count drifts up and you bet straight off the running count with no division.", fontSize = 14.sp)
                    Text("Zen: " + com.hilotrainer.engine.CountSystem.ZEN.valuesText + " It's balanced like Hi-Lo, so you still convert to a true count, but the numbers run about twice as big.", fontSize = 14.sp)
                    Text(
                        "How the trainer coaches bets: KO uses (running count - 4) / decks left + 4, so a KO count of +4 is about the same at any depth. Zen uses half the Zen true count. Both are mapped onto your bet ramp. The count plays (Illustrious 18, Fab 4) and insurance index are Hi-Lo numbers, so with KO or Zen the trainer coaches basic strategy for playing decisions.",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Card values checked against the counting system tables at ether-ore.github.io and wikisage.org. KO starting count from Knock-Out Blackjack (Vancura and Fuchs).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel {
                    Text("Table rules and the house edge", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("6:5 blackjack adds about 1.36% to the house edge by itself. A dealer who hits soft 17 adds about 0.21%. Change the rules in Settings and the strategy chart, drills and coaching all switch to match.", fontSize = 14.sp)
                    Text("Source: Las Vegas Advisor, Question of the Day on blackjack options.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Panel {
                    Text("6. Practice plan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Learn the strategy chart with the strategy drill until you're nearly perfect. Then use the count drill and the casino test until you pass Gold, and climb the speed ladder. Then play full games with the count hidden, quizzes on, and table distractions on.")
                }
            }
        }
    }
}

@Composable
fun ChartScreen(prefs: Prefs, onBack: () -> Unit) {
    val rules = remember { prefs.loadSettings().rules() }
    val ups = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.ACE)
    fun c(r: Rank) = Card(r, Suit.SPADES)
    fun rankOf(v: Int) = Rank.entries.first { it.value == v }
    fun hardHand(t: Int): Hand {
        val a = minOf(10, t - 2).let { if (t - it == it) it - 1 else it }
        return Hand(listOf(c(rankOf(a)), c(rankOf(t - a))))
    }
    val sections = listOf(
        "Hard totals" to (17 downTo 8).map { t -> (if (t == 17) "17+" else "$t") to hardHand(t) },
        "Soft totals" to (9 downTo 2).map { v -> "A,$v" to Hand(listOf(c(Rank.ACE), c(rankOf(v)))) },
        "Pairs" to listOf(11, 10, 9, 8, 7, 6, 5, 4, 3, 2).map { v ->
            val l = if (v == 11) "A,A" else "$v,$v"
            l to Hand(listOf(c(rankOf(v)), c(rankOf(v))))
        },
    )
    ScreenScaffold("Strategy chart", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text(
                "${rules.decks} decks, dealer ${if (rules.dealerHitsSoft17) "hits" else "stands on"} soft 17, " +
                    (if (rules.doubleAfterSplit) "double after split" else "no double after split") +
                    (if (rules.surrenderAllowed) ", late surrender" else ", no surrender") +
                    (if (rules.sixToFive) ", blackjack pays 6:5 (doesn't change the chart, but costs you about 1.36%)" else ", blackjack pays 3:2"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
            )
            Text(
                "H hit · S stand · D double (else hit) · Ds double (else stand) · P split · R surrender (else hit)",
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                sections.forEach { (title, rows) ->
                    Text(title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    Row {
                        ChartCell("", Color.Transparent, header = true)
                        ups.forEach { ChartCell(it.label.let { l -> if (l == "10") "10" else l }, Color.Transparent, header = true) }
                    }
                    rows.forEach { (label, hand) ->
                        Row {
                            ChartCell(label, Color.Transparent, header = true)
                            ups.forEach { up -> val (txt, col) = chartCode(hand, c(up), rules); ChartCell(txt, col) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Chart follows the Wizard of Odds 4 to 8 deck basic strategy, including its hits-soft-17 changes. Change the rules in Settings to see the chart switch.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun chartCode(hand: Hand, up: Card, rules: Rules): Pair<String, Color> {
    val full = Options(canDouble = true, canSplit = true, canSurrender = rules.surrenderAllowed)
    val a = BasicStrategy.advise(hand, up, rules, full)
    return when (a.action) {
        Action.HIT -> "H" to Color(0xFFECEFF1)
        Action.STAND -> "S" to Color(0xFFFFE082)
        Action.SPLIT -> "P" to Color(0xFF90CAF9)
        Action.SURRENDER -> "R" to Color(0xFFEF9A9A)
        Action.DOUBLE -> {
            val fallback = BasicStrategy.advise(hand, up, rules, full.copy(canDouble = false, canSplit = false)).action
            (if (fallback == Action.STAND) "Ds" else "D") to Color(0xFFA5D6A7)
        }
    }
}

@Composable
private fun ChartCell(text: String, bg: Color, header: Boolean = false) {
    Box(
        Modifier.size(width = if (header) 36.dp else 32.dp, height = 30.dp).padding(1.dp).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.SemiBold,
            color = if (header) Color.White else Color(0xFF1B1B1B),
        )
    }
}


@Composable
fun StatsScreen(prefs: Prefs, onBack: () -> Unit) {
    var st by remember { mutableStateOf(prefs.loadStats()) }
    ScreenScaffold("Stats", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel {
                    Text("Play", fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)
                    Text("Rounds played: ${st.handsPlayed}")
                    Text("Strategy decisions: ${pct(st.correctDecisions, st.decisions)}")
                    Text("Count plays (deviations): ${pct(st.deviationCorrect, st.deviationDecisions)}")
                    Text("Best correct-decision streak: ${st.bestStrategyStreak}")
                    Text("Bet sizing: ${pct(st.betCorrect, st.betDecisions)}")
                    Text("Running count checks: ${pct(st.countCorrect, st.countQuizzes)}")
                    Text("True count checks: ${pct(st.trueCorrect, st.trueQuizzes)}")
                    Text("Hints used: ${st.hintsUsed}   Count peeks: ${st.peeks}")
                    Text("Shot clock timeouts: ${st.slowDecisions}   Watch mode rounds: ${st.watchRounds}")
                }
                Panel {
                    Text("Drills", fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)
                    Text("Count drill: ${pct(st.drillsCorrect, st.drillsRun)}")
                    Text("Best deck countdown (tap mode): ${if (st.bestDeckSeconds > 0) "${st.bestDeckSeconds}s" else "none yet"}")
                    Text("True count drill: ${pct(st.tcDrillsCorrect, st.tcDrills)}")
                    Text("Strategy drill: ${pct(st.strategyDrillsCorrect, st.strategyDrills)}")
                    Text("Discard tray drill: ${pct(st.trayCorrect, st.trayDrills)}")
                    Text("Casino test passes: ${pct(st.casinoPasses, st.casinoTests)}, best ${st.casinoBest.takeIf { it >= 0 }?.let { com.hilotrainer.engine.CasinoTest.LEVELS[it].name } ?: "none yet"}")
                    Text("Speed ladder best: rung ${st.ladderBest + 1} (${com.hilotrainer.engine.SpeedLadder.label(st.ladderBest)} per card)")
                    Text("Pair counting: ${pct(st.pairCorrect, st.pairDrills)}")
                    Text("Missed-play reviews: ${pct(st.reviewsCorrect, st.reviews)}")
                    Text("Cover play situations: ${pct(st.coverCorrect, st.coverDrills)}")
                }
                GoldButton("Reset stats and mistakes", Modifier.fillMaxWidth(), primary = false) { prefs.resetStats(); st = prefs.loadStats() }
            }
        }
    }
}

@Composable
fun MistakesScreen(prefs: Prefs, onBack: () -> Unit) {
    val list = remember { prefs.loadMistakes() }
    ScreenScaffold("Mistake review", onBack) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.isEmpty()) {
                    Panel { Text("No mistakes recorded yet. Play a few rounds and any wrong plays, bets or insurance calls will show up here.") }
                }
                val groups = list.groupingBy { it.situation.substringBefore(" at true count") + " -> " + it.advised }.eachCount()
                    .entries.sortedByDescending { it.value }.take(5)
                if (groups.isNotEmpty()) {
                    Panel {
                        Text("Most repeated", fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)
                        groups.forEach { (k, n) -> Text("${n}x  ${k.replace(" -> ", ": correct play is ")}", fontSize = 14.sp) }
                    }
                }
                val fmt = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                list.forEach { m ->
                    Panel {
                        Text(m.situation, fontWeight = FontWeight.SemiBold)
                        Text("You: ${m.chosen}   Correct: ${m.advised}", color = BadRed, fontSize = 14.sp)
                        Text(m.reason, fontSize = 13.sp, color = Cream.copy(alpha = 0.75f))
                        Text(fmt.format(java.util.Date(m.time)), fontSize = 11.sp, color = Cream.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
internal fun ReminderPanel(s: AppSettings, update: (AppSettings) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        denied = !granted
        update(s.copy(reminderOn = granted))
        com.hilotrainer.Reminders.apply(context)
    }
    Panel {
        Text("Daily reminder", fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)
        Toggle("Remind me to practice", s.reminderOn) { on ->
            if (on && android.os.Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                update(s.copy(reminderOn = on))
                com.hilotrainer.Reminders.apply(context)
            }
        }
        if (s.reminderOn) {
            ChipGroup("Time", listOf("8 AM" to 8, "12 PM" to 12, "5 PM" to 17, "7 PM" to 19, "9 PM" to 21), s.reminderHour) {
                update(s.copy(reminderHour = it))
                com.hilotrainer.Reminders.apply(context)
            }
        }
        Text("Only notifies on days you haven't reached your daily goal. Android may deliver it a little after the time you pick.", fontSize = 12.sp, color = Cream.copy(alpha = 0.65f))
        if (denied) Text("Notifications are off for this app. Turn them on in Android Settings, Apps, Hi-Lo Trainer, Notifications.", fontSize = 12.sp, color = BadRed)
    }
}
