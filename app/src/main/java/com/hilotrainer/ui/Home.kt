package com.hilotrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.Course
import com.hilotrainer.engine.Rank
import com.hilotrainer.engine.Suit

/** One thing you can open from a hub. */
data class HubItem(val screen: Screen, val title: String, val sub: String, val suit: Suit)

/** A hub groups related screens so the home screen stays short. */
data class Hub(val screen: Screen, val title: String, val blurb: String, val suit: Suit, val groups: List<Pair<String, List<HubItem>>>) {
    val count: Int get() = groups.sumOf { it.second.size }
}

object Hubs {
    val TRAIN = Hub(
        Screen.HUB_TRAIN, "Train", "Counting and strategy drills", Suit.SPADES,
        listOf(
            "Keep the count" to listOf(
                HubItem(Screen.COUNT_DRILL, "Count drill", "Cards flash by, you keep the count", Suit.SPADES),
                HubItem(Screen.CASINO_TEST, "Casino test", "Timed deck countdown with pass levels", Suit.DIAMONDS),
                HubItem(Screen.SPEED_LADDER, "Speed ladder", "Speeds up as you get it right", Suit.HEARTS),
                HubItem(Screen.PAIR_DRILL, "Pair counting", "Read two cards as one value", Suit.CLUBS),
                HubItem(Screen.HAPTIC_DRILL, "Haptic count drill", "Count vibrations without looking", Suit.SPADES),
            ),
            "True count and decks" to listOf(
                HubItem(Screen.TRUE_DRILL, "True count drill", "Running count divided by decks left", Suit.DIAMONDS),
                HubItem(Screen.TRAY_DRILL, "Discard tray drill", "Estimate decks left by eye", Suit.HEARTS),
                HubItem(Screen.DECK_ESTIMATE, "Deck estimation", "Judge decks by sight, fast", Suit.CLUBS),
            ),
            "Strategy" to listOf(
                HubItem(Screen.STRATEGY_DRILL, "Strategy drill", "Rapid-fire decisions, count plays too", Suit.CLUBS),
                HubItem(Screen.REVIEW, "Review missed plays", "Spaced repetition of your misses", Suit.SPADES),
                HubItem(Screen.WEAK_SPOTS, "Weak-spot practice", "Drills the plays you miss most", Suit.DIAMONDS),
                HubItem(Screen.COVER_DRILL, "Cover play", "Camouflage bets, when to leave", Suit.HEARTS),
            ),
        ),
    )
    val LEARN = Hub(
        Screen.HUB_LEARN, "Learn", "Course, charts and money", Suit.HEARTS,
        listOf(
            "Start here" to listOf(
                HubItem(Screen.COURSE, "Guided course", "Step-by-step lessons with tests", Suit.SPADES),
                HubItem(Screen.LEARN, "How counting works", "Hi-Lo, KO, Zen, bets, count plays", Suit.HEARTS),
            ),
            "Charts" to listOf(
                HubItem(Screen.CHART, "Strategy chart", "Basic strategy for your table rules", Suit.CLUBS),
                HubItem(Screen.INDEX_CHART, "Hi-Lo index chart", "Illustrious 18, Fab 4 and more", Suit.DIAMONDS),
            ),
            "Money" to listOf(
                HubItem(Screen.BANKROLL, "Bankroll and risk", "Risk of ruin calculator", Suit.DIAMONDS),
                HubItem(Screen.KELLY, "Kelly bet sizing", "Bet sizes for your bankroll", Suit.CLUBS),
                HubItem(Screen.SIDE_BETS, "Side bets", "Perfect Pairs and insurance, worth it?", Suit.HEARTS),
            ),
        ),
    )
    val MODES = Hub(
        Screen.HUB_MODES, "Play modes", "Career, daily, sessions", Suit.DIAMONDS,
        listOf(
            "At the table" to listOf(
                HubItem(Screen.JUST_PLAY, "Just Play", "Blackjack for fun, no counting", Suit.HEARTS),
                HubItem(Screen.CAREER, "Career mode", "Start small, prove yourself, move up", Suit.SPADES),
                HubItem(Screen.SESSION, "One-hour session", "Breaks, fatigue and bankroll swings", Suit.CLUBS),
                HubItem(Screen.MID_SHOE, "Join mid-shoe", "Sit down with the count unknown", Suit.DIAMONDS),
            ),
            "Challenges" to listOf(
                HubItem(Screen.DAILY, "Daily challenge", "Today's shoe, the same for everyone", Suit.HEARTS),
            ),
            "Advanced" to listOf(
                HubItem(Screen.BACK_COUNT, "Back-counting", "Watch a table, sit down at the right count", Suit.HEARTS),
                HubItem(Screen.TEAM_PLAY, "Team play", "Spot, signal, then play the big player", Suit.CLUBS),
            ),
        ),
    )
    val PROGRESS = Hub(
        Screen.HUB_PROGRESS, "Progress", "Stats, badges, goals", Suit.CLUBS,
        listOf(
            "Overview" to listOf(
                HubItem(Screen.DASHBOARD, "Stats dashboard", "Accuracy by true count over time", Suit.SPADES),
                HubItem(Screen.WEEKLY, "Weekly goal", "Practice calendar and week streak", Suit.CLUBS),
                HubItem(Screen.BADGES, "Badges and bests", "Personal records and what to unlock", Suit.DIAMONDS),
            ),
            "Details" to listOf(
                HubItem(Screen.STATS, "Lifetime stats", "Accuracy and records", Suit.CLUBS),
                HubItem(Screen.PROGRESS, "Daily chart and export", "Your trend, save a CSV", Suit.HEARTS),
                HubItem(Screen.HEAT_MAP, "Error heat map", "Mistakes by hand, dealer card and count", Suit.HEARTS),
                HubItem(Screen.MISTAKES, "Mistake review", "Your recent mistakes and fixes", Suit.DIAMONDS),
            ),
        ),
    )
    val ALL = listOf(TRAIN, LEARN, MODES, PROGRESS)
    fun of(s: Screen): Hub? = ALL.firstOrNull { it.screen == s }
    val isHub: (Screen) -> Boolean = { s -> ALL.any { it.screen == s } }
}

/**
 * The home screen: who you are and what's next at the top, one big Play button,
 * two quick starts, then four hubs. Fits a Fold cover screen without scrolling.
 */
@Composable
fun HomeScreen(prefs: Prefs? = null, go: (Screen) -> Unit) {
    var tour by remember { mutableStateOf(prefs != null && !prefs.tourSeen) }
    if (tour) QuickTour { tour = false; prefs?.tourSeen = true }
    val settings = remember { prefs?.loadSettings() ?: AppSettings() }
    val passed = remember { prefs?.coursePassed ?: 0 }
    val due = remember { prefs?.let { com.hilotrainer.engine.Review.due(it.loadReview(), System.currentTimeMillis()).size } ?: 0 }
    Box(Modifier.fillMaxSize().feltBackground(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 680.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: name of the app and the settings gear, nothing else.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hi-Lo Trainer", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Gold, fontFamily = TitleFont)
                    Text("Blackjack and card counting", fontSize = 14.sp, color = Cream.copy(alpha = 0.75f))
                }
                IconButton(onClick = { go(Screen.SETTINGS) }, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Gold, modifier = Modifier.size(30.dp))
                }
            }
            if (prefs != null) StatusStrip(prefs)
            JustPlayCard(prefs) { go(Screen.JUST_PLAY) }
            PlayCard(settings) { go(Screen.PLAY) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val lessons = Course.LESSONS.size
                QuickTile(
                    if (passed >= lessons) "Course done" else if (passed == 0) "Start course" else "Your course",
                    if (passed >= lessons) "All $lessons passed" else "Lesson ${passed + 1} of $lessons",
                    Suit.SPADES, Modifier.weight(1f),
                ) { go(Screen.COURSE) }
                QuickTile("Quick drill", "60 seconds", Suit.DIAMONDS, Modifier.weight(1f)) { go(Screen.QUICK_DRILL) }
            }
            Text("MORE", fontSize = 12.sp, letterSpacing = 2.sp, color = Gold, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
            Hubs.ALL.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { h ->
                        val badge = if (h == Hubs.TRAIN && due > 0) "$due to review" else null
                        HubTile(h, badge, Modifier.weight(1f)) { go(h.screen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusStrip(prefs: Prefs) {
    val d = remember { prefs.daily() }
    val level = remember { skillLevel(prefs.loadStats()) }
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Ink.copy(alpha = 0.55f)).border(1.dp, Gold.copy(alpha = 0.35f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stat("Level", level.name, Modifier.weight(1.2f))
            Stat("Streak", if (d.streak == 0) "Start today" else "${d.streak} day${if (d.streak == 1) "" else "s"}", Modifier.weight(1f))
            Stat("Today", if (d.metToday) "Goal done" else "${d.points} of ${d.goal}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))
                .semantics { contentDescription = "Daily goal ${d.points} of ${d.goal} points" },
        ) {
            Box(Modifier.fillMaxWidth((d.points / d.goal.toFloat()).coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Gold))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Gold)
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Just Play: casual blackjack, no counting. Shown first so it's easy to find. */
@Composable
private fun JustPlayCard(prefs: Prefs?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val bank = remember { prefs?.let { CasualStore(it.context).bankroll } }
    Box(
        Modifier.fillMaxWidth().height(96.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFA32638), Color(0xFF5C0C18))))
            .border(1.5.dp, Gold.copy(alpha = 0.7f), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Just Play, blackjack for fun" },
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 120.dp)) {
            Text("Just Play", fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = TitleFont, color = Cream)
            Text(if (bank != null) "For fun, no counting  ·  ${money(bank)}" else "Blackjack for fun, no counting", fontSize = 14.sp, color = Cream.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
            listOf(5, 25, 100).forEach { v -> CasualChipStack(v, 42.dp) }
        }
    }
}

/** The main action, big and first. */
@Composable
private fun PlayCard(settings: AppSettings, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.fillMaxWidth().height(116.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFFE8C26A), Color(0xFFB8892F))))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play blackjack" },
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp, end = 130.dp)) {
            Text("Play blackjack", fontSize = 26.sp, fontWeight = FontWeight.Black, fontFamily = TitleFont, color = Ink)
            val rules = "${settings.decks} decks, ${if (settings.dealerHitsSoft17) "H17" else "S17"}, " +
                if (settings.bots == 0) "just you" else "${settings.bots} other player${if (settings.bots == 1) "" else "s"}"
            Text(rules, fontSize = 14.sp, color = Ink.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // A small fan of cards for the casino feel.
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(110.dp, 96.dp), contentAlignment = Alignment.Center) {
            val hero = listOf(Card(Rank.ACE, Suit.SPADES) to -12f, Card(Rank.KING, Suit.HEARTS) to 10f)
            hero.forEachIndexed { i, (c, deg) ->
                PlayingCard(c, 54.dp, modifier = Modifier.offset(x = ((i - 0.5f) * 30).dp).rotate(deg))
            }
        }
    }
}

@Composable
private fun QuickTile(title: String, sub: String, suit: Suit, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.height(72.dp).clip(shape).background(Ink.copy(alpha = 0.6f)).border(1.dp, Gold.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Cream), contentAlignment = Alignment.Center) { SuitIcon(suit, 18.dp) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 13.sp, color = Cream.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HubTile(h: Hub, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 112.dp).clip(shape).background(Ink.copy(alpha = 0.55f)).border(1.dp, Gold.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Cream), contentAlignment = Alignment.Center) { SuitIcon(h.suit, 17.dp) }
            Spacer(Modifier.weight(1f))
            if (badge != null) {
                Text(badge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.clip(RoundedCornerShape(50)).background(Gold).padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(h.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Cream, fontFamily = TitleFont)
        Text(h.blurb, fontSize = 13.sp, color = Cream.copy(alpha = 0.75f), maxLines = 2)
    }
}

/** A hub's own screen: a short grouped list with big, readable rows. */
@Composable
fun HubScreen(hub: Hub, prefs: Prefs? = null, onBack: () -> Unit, go: (Screen) -> Unit) {
    val due = remember { prefs?.let { com.hilotrainer.engine.Review.due(it.loadReview(), System.currentTimeMillis()).size } ?: 0 }
    ScreenScaffold(hub.title, onBack) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val twoCol = maxWidth >= 600.dp
            Column(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hub.groups.forEach { (title, items) ->
                    Text(title.uppercase(), fontSize = 12.sp, letterSpacing = 2.sp, color = Gold, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                    items.chunked(if (twoCol) 2 else 1).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { item ->
                                val sub = if (item.screen == Screen.REVIEW && due > 0) "$due due now" else item.sub
                                HubRow(item.title, sub, item.suit, Modifier.weight(1f)) { go(item.screen) }
                            }
                            if (twoCol && row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HubRow(title: String, sub: String, suit: Suit, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.height(62.dp).clip(shape).background(Ink.copy(alpha = 0.55f)).border(1.dp, Gold.copy(alpha = 0.25f), shape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Cream), contentAlignment = Alignment.Center) { SuitIcon(suit, 17.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Cream, maxLines = 1)
            Text(sub, fontSize = 13.sp, color = Cream.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 26.sp, color = Gold.copy(alpha = 0.7f))
    }
}
