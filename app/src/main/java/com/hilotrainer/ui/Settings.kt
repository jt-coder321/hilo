package com.hilotrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.hilotrainer.engine.CountSystem
import com.hilotrainer.engine.Suit
import com.hilotrainer.engine.TrueCount

/** The five settings groups, in the order people usually need them. */
enum class SettingsPage(val title: String, val suit: Suit) {
    RULES("Table rules", Suit.SPADES),
    TABLE("At the table", Suit.HEARTS),
    COACHING("Coaching and counting", Suit.DIAMONDS),
    CONTROLS("Sound, screen and controls", Suit.CLUBS),
    DATA("Updates and backup", Suit.SPADES),
}

/** One-line summary of a group's current choices, shown on the settings menu. */
fun settingsSummary(page: SettingsPage, s: AppSettings): String = when (page) {
    SettingsPage.RULES -> {
        CasinoProfiles.matching(s)?.let { CasinoProfiles.ALL[it].name }
            ?: "Custom: ${s.decks} decks, ${if (s.dealerHitsSoft17) "H17" else "S17"}, ${if (s.sixToFive) "6:5" else "3:2"}"
    }
    SettingsPage.TABLE -> "${if (s.bots == 0) "Just you" else "${s.bots} other player${if (s.bots == 1) "" else "s"}"}, " +
        listOf("slow", "normal", "fast", "ladder").getOrElse(s.dealSpeed) { "normal" } + " dealing"
    SettingsPage.COACHING -> "${s.system.label}, count ${if (s.showRunningCount) "shown" else "hidden"}, " +
        if (s.quizEvery == 0) "no count checks" else "checks every ${if (s.quizEvery == 1) "round" else "${s.quizEvery} rounds"}"
    SettingsPage.CONTROLS -> "Sound ${if (s.sound) "on" else "off"}, vibration ${if (s.haptics) "on" else "off"}" +
        if (s.leftHanded) ", left-handed" else ""
    SettingsPage.DATA -> if (s.reminderOn) "Reminder at ${hourLabel(s.reminderHour)}" else "Reminder off"
}

private fun hourLabel(h: Int) = when {
    h == 0 -> "12 AM"; h < 12 -> "$h AM"; h == 12 -> "12 PM"; else -> "${h - 12} PM"
}

/**
 * Settings, organized as a short menu of five groups. Each group opens its own page
 * with plain labels and a one-line explanation under anything that isn't obvious.
 */
@Composable
fun SettingsScreen(prefs: Prefs, initialPage: SettingsPage? = null, onBack: () -> Unit) {
    var s by remember { mutableStateOf(prefs.loadSettings()) }
    fun update(n: AppSettings) { s = n; prefs.saveSettings(n) }
    var page by rememberSaveable { mutableStateOf(initialPage) }
    BackHandler(enabled = page != null) { page = null }
    val current = page
    if (current == null) {
        SettingsMenu(s, prefs, onBack, onOpen = { page = it }, onReset = { update(AppSettings()) })
    } else {
        ScreenScaffold(current.title, { page = null }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (current) {
                        SettingsPage.RULES -> RulesPage(s, ::update)
                        SettingsPage.TABLE -> TablePage(s, ::update)
                        SettingsPage.COACHING -> CoachingPage(s, ::update)
                        SettingsPage.CONTROLS -> ControlsPage(s, ::update)
                        SettingsPage.DATA -> {
                            UpdatePanel()
                            ReminderPanel(s) { update(it) }
                            BackupPanel(prefs) { s = prefs.loadSettings() }
                            Group("Help") {
                                GoldButton("Show the quick tour again", Modifier.fillMaxWidth(), primary = false) { prefs.tourSeen = false }
                            }
                        }
                    }
                    if (current == SettingsPage.RULES || current == SettingsPage.TABLE || current == SettingsPage.COACHING)
                        Text("Changes save right away and start a fresh shoe.", fontSize = 13.sp, color = Cream.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun SettingsMenu(s: AppSettings, prefs: Prefs, onBack: () -> Unit, onOpen: (SettingsPage) -> Unit, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ScreenScaffold("Settings", onBack) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingsPage.entries.forEach { p ->
                    MenuRow(p.title, settingsSummary(p, s), p.suit) { onOpen(p) }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { confirm = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Restore recommended settings", color = Gold, fontSize = 15.sp)
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = FeltDark,
            title = { Text("Restore recommended settings?", fontFamily = TitleFont, color = Gold) },
            text = { Text("Every setting goes back to how the app starts. Your stats, streak, course and badges are kept.", color = Cream) },
            confirmButton = { GoldButton("Restore", Modifier.widthIn(min = 120.dp)) { confirm = false; onReset() } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel", color = Cream) } },
        )
    }
}

@Composable
private fun MenuRow(title: String, sub: String, suit: Suit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape).background(Ink.copy(alpha = 0.55f))
            .border(1.dp, Gold.copy(alpha = 0.25f), shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Cream), contentAlignment = Alignment.Center) { SuitIcon(suit, 18.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Cream)
            Text(sub, fontSize = 14.sp, color = Cream.copy(alpha = 0.75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text("›", fontSize = 28.sp, color = Gold.copy(alpha = 0.7f))
    }
}

/** A titled card inside a settings page. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Panel {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)
        content()
    }
}

/** A switch with a plain label and, when it helps, one line explaining it. The whole row is tappable. */
@Composable
private fun Setting(label: String, help: String? = null, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Switch) { onChange(!value) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, fontSize = 16.sp, color = Cream)
            if (help != null) Text(help, fontSize = 13.sp, color = Cream.copy(alpha = 0.65f))
        }
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Help(text: String, warn: Boolean = false) {
    Text(text, fontSize = 13.sp, color = if (warn) BadRed else Cream.copy(alpha = 0.65f))
}

@Composable
private fun RulesPage(s: AppSettings, update: (AppSettings) -> Unit) {
    Group("Quick pick") {
        Help("Pick a common casino's rules in one tap, or set each rule below.")
        val cur = CasinoProfiles.matching(s)
        CasinoProfiles.ALL.forEachIndexed { i, p ->
            val sel = cur == i
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Gold.copy(alpha = 0.18f) else Ink.copy(alpha = 0.4f))
                    .border(1.dp, Gold.copy(alpha = if (sel) 0.9f else 0.3f), RoundedCornerShape(12.dp))
                    .clickable { update(p.apply(s)) }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text((if (sel) "✓ " else "") + p.name, fontWeight = FontWeight.SemiBold, color = if (sel) Gold else Cream)
                Text(p.rules, fontSize = 13.sp, color = Cream.copy(alpha = 0.75f))
                if (p.note.startsWith("Avoid")) Text(p.note, fontSize = 13.sp, color = BadRed)
            }
        }
    }
    Group("The shoe") {
        ChipGroup("Decks", listOf("4" to 4, "6" to 6, "8" to 8), s.decks) { update(s.copy(decks = it)) }
        ChipGroup("Cut card depth", listOf("65%" to 65, "75%" to 75, "85%" to 85), s.penetrationPct) { update(s.copy(penetrationPct = it)) }
        Help("How much of the shoe is dealt before the shuffle. Deeper is better for counting.")
        ChipGroup("Shuffle", listOf("By hand" to 0, "Machine" to 1, "Continuous" to 2), s.shuffleChoice) { update(s.copy(shuffleChoice = it)) }
        if (s.shuffleChoice == 2) Help("A continuous shuffler puts cards back every round, so counting can't help. Try it to see why counters avoid it.", warn = true)
    }
    Group("Dealer and payouts") {
        Setting("Dealer hits soft 17", "Called H17 on the table sign.", s.dealerHitsSoft17) { update(s.copy(dealerHitsSoft17 = it)) }
        Setting("Blackjack pays 6 to 5", "Instead of 3 to 2.", s.sixToFive) { update(s.copy(sixToFive = it)) }
        if (s.sixToFive) Help("6 to 5 adds about 1.36% to the house edge. Practice with it, but avoid these tables for real.", warn = true)
    }
    Group("Your options") {
        Setting("Double after splitting", value = s.doubleAfterSplit) { update(s.copy(doubleAfterSplit = it)) }
        Setting("Surrender", "Give up half your bet before playing the hand.", s.surrender) { update(s.copy(surrender = it)) }
        ChipGroup("Perfect Pairs side bet", listOf("Off" to 0, "$5" to 5, "$10" to 10), s.perfectPairs) { update(s.copy(perfectPairs = it)) }
        Help("Always on: split up to 4 hands, split aces get one card each, and the dealer checks for blackjack.")
    }
}

@Composable
private fun TablePage(s: AppSettings, update: (AppSettings) -> Unit) {
    Group("Players and pace") {
        ChipGroup("Other players", listOf("None" to 0, "1" to 1, "2" to 2, "3" to 3), s.bots) { update(s.copy(bots = it)) }
        Setting("Players make common mistakes", "Like real tables. Their cards still count.", s.realisticPlayers) { update(s.copy(realisticPlayers = it)) }
        ChipGroup("Dealing speed", listOf("Slow" to 0, "Normal" to 1, "Fast" to 2, "Ladder" to 3), s.dealSpeed) { update(s.copy(dealSpeed = it)) }
        if (s.dealSpeed == 3) Help("Ladder starts at your speed ladder level and speeds up after 3 right count checks in a row.")
        ChipGroup("Time to decide", listOf("No limit" to 0, "8 sec" to 8, "5 sec" to 5, "3 sec" to 3), s.shotClock) { update(s.copy(shotClock = it)) }
    }
    Group("Casino realism") {
        Setting("Table distractions", "People talk, ask you things and walk past while you count. Different every round.", s.distractions) { update(s.copy(distractions = it)) }
        if (s.distractions) {
            ChipGroup("How busy", listOf("Light" to false, "Real casino" to true), s.distractionsReal) { update(s.copy(distractionsReal = it)) }
            Help(if (s.distractionsReal) "Questions you must answer before you can play, number talk out loud, people blocking your view, your phone buzzing." else "Now and then some chatter or chips, nothing you have to answer.")
        }
        Setting("Dealer mistakes to catch", "Now and then the dealer pays or calls a hand wrong.", s.dealerErrors) { update(s.copy(dealerErrors = it)) }
        Setting("You cut the deck", "Tap where the cut card goes after each shuffle.", s.cutCards) { update(s.copy(cutCards = it)) }
        Setting("Seated view", "The table leans back like you're in the chair.", s.seatedView) { update(s.copy(seatedView = it)) }
        ChipGroup("Card backs", CardBacks.ALL.mapIndexed { i, b -> b.name to i }, s.cardBack) { update(s.copy(cardBack = it)) }
    }
    Group("Hand signals") {
        ChipGroup("Hit and stand by", listOf("Buttons" to 0, "Buttons or signals" to 1, "Signals only" to 2), s.signals) { update(s.copy(signals = it)) }
        Help("Signals: tap the felt by your cards to hit, wave sideways to stand.")
    }
}

@Composable
private fun CoachingPage(s: AppSettings, update: (AppSettings) -> Unit) {
    Group("How much help") {
        Setting("Show the count all the time", "Turn off to practice keeping it yourself.", s.showRunningCount) { update(s.copy(showRunningCount = it)) }
        Setting("Show the right play", "A hint for every decision.", s.showHints) { update(s.copy(showHints = it)) }
        Setting("Stop me before a mistake", "Asks before you make a wrong play.", s.confirmMistakes) { update(s.copy(confirmMistakes = it)) }
    }
    Group("Count checks") {
        ChipGroup("Ask me the count", listOf("Never" to 0, "Every round" to 1, "Every 2" to 2, "Every 3" to 3, "Every 5" to 5), s.quizEvery) { update(s.copy(quizEvery = it)) }
        if (s.quizEvery > 0) Setting("Ask the true count too", value = s.askTrueCount) { update(s.copy(askTrueCount = it)) }
        Help("Only asked while the count is hidden.")
    }
    Group("Counting system") {
        ChipGroup("Count with", listOf("Hi-Lo" to 0, "KO" to 1, "Zen" to 2), s.systemChoice) { update(s.copy(systemChoice = it)) }
        Help(s.system.valuesText)
        if (s.system != CountSystem.HI_LO) Help("Count plays use Hi-Lo numbers, so with ${s.system.label} you're coached on basic strategy. Bets still follow your count.")
        Setting("Count plays", "Change your play at certain counts (Illustrious 18 and Fab 4).", s.deviations) { update(s.copy(deviations = it)) }
        if (s.deviations) Setting("Extra count plays", "The full index chart.", s.fullIndices) { update(s.copy(fullIndices = it)) }
    }
    Group("Betting") {
        Setting("Coach my bet size", value = s.gradeBets) { update(s.copy(gradeBets = it)) }
        ChipGroup("Bet range (\$10 units)", listOf("1 to 4" to 0, "1 to 8" to 1, "1 to 12" to 2), s.rampChoice) { update(s.copy(rampChoice = it)) }
        Help("Your ramp: " + TrueCount.describeRamp(s.ramp) + " units.")
        Setting("Tell me when to sit out", "At a true count of -1 or lower.", s.wongOut) { update(s.copy(wongOut = it)) }
    }
    Group("Staying under the radar") {
        Setting("Cover play coaching", "A heat meter shows how much attention your bets draw.", s.coverPlay) { update(s.copy(coverPlay = it)) }
        if (s.coverPlay) Setting("The pit can step in", "Limit you to flat bets, or ask you to leave.", s.pitBackoff) { update(s.copy(pitBackoff = it)) }
    }
}

@Composable
private fun ControlsPage(s: AppSettings, update: (AppSettings) -> Unit) {
    Group("Sound") {
        Setting("Sound effects", "Cards, chips and results.", s.sound) { update(s.copy(sound = it)) }
        Setting("Dealer talks", "The dealer calls hands out loud. Needs sound on.", s.dealerVoice) { update(s.copy(dealerVoice = it)) }
        Setting("Vibration", value = s.haptics) { update(s.copy(haptics = it)) }
    }
    Group("Controls") {
        Setting("Left-handed", "Moves the main buttons and side panels to the left.", s.leftHanded) { update(s.copy(leftHanded = it)) }
        Setting("Answer by voice", "Say the count out loud instead of tapping.", s.voice) { update(s.copy(voice = it)) }
    }
    Group("Fold") {
        Setting("Flex mode", "Half open the Fold: table on top, buttons on the bottom.", s.flexMode) { update(s.copy(flexMode = it)) }
    }
}

/** Check GitHub for a newer version, download it and open Android's installer. */
@Composable
fun UpdatePanel() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val (code, name) = remember { try { com.hilotrainer.Updater.installed(ctx) } catch (e: Exception) { 0L to "?" } }
    var status by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<com.hilotrainer.Updater.Release?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var apk by remember { mutableStateOf<java.io.File?>(null) }
    Group("App updates") {
        Text("Installed: version $name", fontSize = 16.sp, color = Cream)
        Text("Updates come from GitHub. Android asks you to confirm each install, and your stats and settings are kept.", fontSize = 13.sp, color = Cream.copy(alpha = 0.65f))
        Spacer(Modifier.height(8.dp))
        val r = found
        when {
            progress != null -> {
                Text("Downloading version ${r?.versionName}... ${((progress ?: 0f) * 100).toInt()}%", fontSize = 15.sp, color = Gold)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color(0x33FFFFFF))) {
                    Box(Modifier.fillMaxWidth(progress ?: 0f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Gold))
                }
            }
            r != null && r.versionCode > code -> {
                Text("Version ${r.versionName} is available.", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Gold)
                if (r.notes.isNotBlank()) Text(r.notes, fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
                Spacer(Modifier.height(8.dp))
                GoldButton(if (apk != null) "Install version ${r.versionName}" else "Download and install", Modifier.fillMaxWidth(), enabled = !busy) {
                    val ready = apk
                    if (!com.hilotrainer.Updater.canInstall(ctx)) {
                        status = "Allow Hi-Lo Trainer to install updates on the next screen, then come back and tap the button again."
                        com.hilotrainer.Updater.askPermission(ctx)
                    } else if (ready != null) {
                        com.hilotrainer.Updater.install(ctx, ready)
                    } else {
                        busy = true; status = null; progress = 0f
                        scope.launch {
                            try {
                                val f = com.hilotrainer.Updater.download(ctx, r) { p -> progress = p }
                                apk = f; progress = null
                                com.hilotrainer.Updater.install(ctx, f)
                            } catch (e: Exception) {
                                progress = null; status = "Couldn't download the update. ${e.message ?: ""}".trim()
                            }
                            busy = false
                        }
                    }
                }
            }
            else -> GoldButton(if (busy) "Checking..." else "Check for updates", Modifier.fillMaxWidth(), enabled = !busy, primary = false) {
                busy = true; status = null
                scope.launch {
                    try {
                        val latest = com.hilotrainer.Updater.latest()
                        found = latest
                        if (latest.versionCode <= code) status = "You have the newest version."
                    } catch (e: Exception) {
                        status = if (e.message?.contains("404") == true) "GitHub has no update file yet. The repository may be private, or no update has been published."
                        else "Couldn't reach GitHub. Check your connection and try again."
                    }
                    busy = false
                }
            }
        }
        status?.let { Spacer(Modifier.height(6.dp)); Text(it, fontSize = 14.sp, color = Cream) }
    }
}
