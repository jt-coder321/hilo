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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Achievements
import com.hilotrainer.engine.Cast
import com.hilotrainer.engine.CasualDealers

val CasualTabs = listOf("Games", "Table", "Settings", "Me")

/** Just Play's menu: rules, looks, options, stats, achievements and who's who. */
@Composable
fun CasualMenu(vm: CasualViewModel, tab: Int, onTab: (Int) -> Unit, part: Int = 0, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxSize().padding(horizontal = 10.dp, vertical = 16.dp)
                .clip(RoundedCornerShape(18.dp)).background(FeltDark).border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .clickable(enabled = false) {},
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Just Play", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = TitleFont, color = Gold, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Close, contentDescription = "Close menu", tint = Gold) }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CasualTabs.forEachIndexed { i, t ->
                    val on = i == tab
                    Text(
                        t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (on) Ink else Cream,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Gold else Color(0x22FFFFFF)).clickable { onTab(i) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    0 -> GamesTab(vm, onClose, part)
                    1 -> TableTab(vm)
                    2 -> OptionsTab(vm)
                    else -> MeTab(vm, part)
                }
            }
        }
    }
}

@Composable
private fun Seg(label: String, options: List<String>, selected: Int, note: String? = null, onPick: (Int) -> Unit) {
    Column {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
        if (note != null) Text(note, fontSize = 12.sp, color = Cream.copy(alpha = 0.65f))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF))) {
            options.forEachIndexed { i, o ->
                Box(
                    Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp)).background(if (i == selected) Gold else Color.Transparent).clickable { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(o, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (i == selected) Ink else Cream, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
            if (note != null) Text(note, fontSize = 12.sp, color = Cream.copy(alpha = 0.65f))
        }
        Switch(on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = Ink, checkedTrackColor = Gold))
    }
}

@Composable
private fun TableTab(vm: CasualViewModel) {
    val s = vm.settings
    val mid = !vm.canBet
    if (mid) Text("Rule changes apply after this hand.", fontSize = 13.sp, color = Gold)
    val decks = listOf(1, 2, 6, 8)
    Seg("Decks", decks.map { "$it" }, decks.indexOf(s.decks).coerceAtLeast(0)) { if (!mid) vm.update(s.copy(decks = decks[it])) }
    Seg("Dealer on soft 17", listOf("Stands", "Hits"), if (s.dealerHitsSoft17) 1 else 0) { if (!mid) vm.update(s.copy(dealerHitsSoft17 = it == 1)) }
    Seg("Blackjack pays", listOf("3 to 2", "6 to 5"), if (s.sixToFive) 1 else 0, "3 to 2 is the better deal for you.") { if (!mid) vm.update(s.copy(sixToFive = it == 1)) }
    TierList(vm, enabled = !mid)
    Seg("Other players", (0..4).map { "$it" }, s.seats, "How many seats around you can fill up.") { if (!mid) vm.update(s.copy(seats = it)) }
    Toggle("Players come and go", s.comeAndGo, "People sit down and leave between hands, like a real casino.") { vm.update(s.copy(comeAndGo = it)) }
    Toggle("Surrender", s.surrender, "Give up a bad hand and get half your bet back.") { if (!mid) vm.update(s.copy(surrender = it)) }
    Toggle("Double after split", s.doubleAfterSplit) { if (!mid) vm.update(s.copy(doubleAfterSplit = it)) }
    Toggle("Side bets", s.sideBets, "Perfect Pairs and 21+3 spots next to your bet.") { vm.update(s.copy(sideBets = it)) }
    Text(
        "Side bets pay: Perfect Pairs 25 to 1 same suit, 12 to 1 same color, 6 to 1 mixed. 21+3 pays 100 to 1 suited three of a kind, 40 to 1 straight flush, 30 to 1 three of a kind, 10 to 1 straight, 5 to 1 flush.",
        fontSize = 12.sp, color = Cream.copy(alpha = 0.65f),
    )
    Heading("LOOK")
    LookTab(vm)
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 12.sp, letterSpacing = 2.sp, color = Gold, modifier = Modifier.padding(top = 8.dp))
}

/** A short row of pill choices for switching between the parts of a tab. */
@Composable
private fun SubTabs(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF))) {
        options.forEachIndexed { i, o ->
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(if (i == selected) Color(0xFF3A5F4C) else Color.Transparent).clickable { onPick(i) },
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (i == selected) Gold else Cream, maxLines = 1) }
        }
    }
}

/** Story nights and how the side games work. */
@Composable
private fun GamesTab(vm: CasualViewModel, onClose: () -> Unit, start: Int = 0) {
    var part by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(start) }
    SubTabs(listOf("Story Nights", "Side games"), part) { part = it }
    when (part) {
        0 -> NightsTab(vm)
        else -> SideGamesInfo()
    }
}

@Composable
private fun SideGamesInfo() {
    Text("Side games", fontSize = 20.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = Gold)
    Text("Tap the Side bets circle at the table to play Perfect Pairs, 21+3, Lucky Ladies and Next Card. Turn the spots on in Table, rules.", fontSize = 14.sp, color = Cream.copy(alpha = 0.85f))
    Heading("PAYOUTS")
    Text("Perfect Pairs: 25 to 1 same suit, 12 to 1 same color, 6 to 1 mixed.", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
    Text("21+3: 100 to 1 suited three of a kind, 40 to 1 straight flush, 30 to 1 three of a kind, 10 to 1 straight, 5 to 1 flush.", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
    Text("Lucky Ladies: 4, 9, 19 and 125 to 1 for 20 totals, matched 20s and the big hands.", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
    Text("Next Card: Low 3 to 2, Seven 10 to 1, High 1 to 1.", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
    Heading("POWER-UPS")
    Text("Peek shows the dealer's hole card, Refund returns a lost bet, and Cover pays for a bust. They show under the felt when you have them, and they come from the wheel, comps and wins.", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
    Text("Casual only. None of it touches your training stats.", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
}

/** Your record: numbers, awards, and the people at the tables. */
@Composable
private fun MeTab(vm: CasualViewModel, start: Int = 0) {
    var part by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(start) }
    SubTabs(listOf("Stats", "Awards", "Regulars"), part) { part = it }
    when (part) {
        0 -> StatsTab(vm)
        1 -> AwardsTab(vm)
        else -> RegularsTab()
    }
}

@Composable
private fun LookTab(vm: CasualViewModel) {
    val s = vm.settings
    Text("Felt", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FeltTheme.entries.forEachIndexed { i, t ->
            Column(Modifier.weight(1f).clickable { vm.update(s.copy(felt = i)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(52.dp).clip(CircleShape).themedFelt(t).border(if (i == s.felt) 3.dp else 1.dp, if (i == s.felt) Gold else Cream.copy(alpha = 0.3f), CircleShape))
                Text(t.label, fontSize = 11.sp, color = Cream, maxLines = 2)
            }
        }
    }
    Text("Card backs", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CardBacks.ALL.forEachIndexed { i, b ->
            Column(Modifier.weight(1f).clickable { vm.update(s.copy(cardBack = i)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.runtime.CompositionLocalProvider(LocalCardStyle provides CardStyle(back = i)) {
                    Box(Modifier.border(if (i == s.cardBack) 3.dp else 0.dp, if (i == s.cardBack) Gold else Color.Transparent, RoundedCornerShape(6.dp)).padding(3.dp)) { CardBack(48.dp) }
                }
                Text(b.name, fontSize = 11.sp, color = Cream, maxLines = 2)
            }
        }
    }
}

@Composable
private fun OptionsTab(vm: CasualViewModel) {
    val s = vm.settings
    Heading("COUNT AND HELP")
    Toggle("Card count", s.showCount, "Shows the Hi-Lo running and true count in the top bar. The count keeps going for each room, even after you leave. Watch sits a round out so you can count without a bet.") { vm.update(s.copy(showCount = it)) }
    Toggle("Hint button", s.hints, "Shows a Hint button that suggests the basic strategy play. Off by default.") { vm.update(s.copy(hints = it)) }
    Toggle("Offer insurance", s.offerInsurance, "When the dealer shows an ace.") { vm.update(s.copy(offerInsurance = it)) }
    Heading("TABLE TALK")
    Toggle("Table talk", s.tableTalk, "Players chat, react and tease each other.") { vm.update(s.copy(tableTalk = it)) }
    Seg("Chat style", listOf("Lane", "Bubbles", "Caption", "Log", "Float"), s.chatStyle, "How table talk appears. Try each one.") { vm.update(s.copy(chatStyle = it)) }
    Heading("SOUND AND PACE")
    Toggle("Sound", s.sound) { vm.update(s.copy(sound = it)) }
    Toggle("Vibration", s.haptics) { vm.update(s.copy(haptics = it)) }
    Seg("Deal speed", listOf("Relaxed", "Normal", "Fast"), s.speed, "Each dealer also has their own pace.") { vm.update(s.copy(speed = it)) }
    Text("Power-ups and mini games are casual only. They never touch your training stats. Just Play has no quizzes and no coaching. The count is off unless you turn it on. Your training settings are separate and unchanged.", fontSize = 12.sp, color = Cream.copy(alpha = 0.65f))
}

@Composable
private fun StatsTab(vm: CasualViewModel) {
    val t = vm.stats
    val rows = listOf(
        "Bankroll" to money(vm.bankroll), "Best bankroll" to money(t.biggestBankroll),
        "Hands played" to "%,d".format(t.hands), "Win rate" to "${t.winRate}%",
        "Won, lost, pushed" to "${t.wins}, ${t.losses}, ${t.pushes}", "Blackjacks" to "${t.blackjacks}",
        "Biggest win" to money(t.biggestWin), "Net all time" to (if (t.net >= 0) "+" else "-") + money(kotlin.math.abs(t.net).toInt()),
        "Win streak now" to "${t.winStreak}", "Best win streak" to "${t.bestWinStreak}",
        "Doubles won" to "${t.doublesWon}", "Splits" to "${t.splits}",
        "Side bets hit" to "${t.sideWins}", "Dealer busts seen" to "${t.dealerBusts}",
        "Total wagered" to money(t.wagered.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()), "Rebuys" to "${t.rebuys}",
    )
    rows.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { (k, v) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Color(0x22FFFFFF)).padding(12.dp)) {
                    Text(k.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = Gold)
                    Text(v, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Cream, maxLines = 1)
                }
            }
        }
    }
    Text("Win rate counts every hand, including pushes.", fontSize = 12.sp, color = Cream.copy(alpha = 0.65f))
}

@Composable
private fun AwardsTab(vm: CasualViewModel) {
    Text("${vm.earned.size} of ${Achievements.ALL.size} earned", fontSize = 14.sp, color = Gold)
    Achievements.ALL.forEach { a ->
        val got = a.id in vm.earned
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (got) Gold.copy(alpha = 0.18f) else Color(0x14FFFFFF)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (got) Gold else Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                Text(if (got) "★" else "?", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (got) Ink else Cream.copy(alpha = 0.6f))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(a.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (got) Cream else Cream.copy(alpha = 0.7f))
                Text(a.desc, fontSize = 12.sp, color = Cream.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun RegularsTab() {
    Text("DEALERS", fontSize = 12.sp, letterSpacing = 2.sp, color = Gold)
    CasualDealers.ALL.forEach { d -> Person(d.look, d.name, "Dealer", d.personality, dealer = true) }
    Text("THE REGULARS", fontSize = 12.sp, letterSpacing = 2.sp, color = Gold)
    Cast.ALL.forEach { m -> Person(m.look, m.name, m.archetype.label, m.bio) }
    Person(PitLook, "The pit boss", "Floor supervisor", "Watches the big bettors. Comps the high rollers and backs off card counters who win too much.")
}

@Composable
private fun Person(look: com.hilotrainer.engine.Look, name: String, kind: String, bio: String, dealer: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).themedFelt(FeltTheme.GREEN)) { Avatar(look, 60.dp, dealer = dealer, name = name) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("$name  ·  $kind", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
            Text(bio, fontSize = 12.sp, color = Cream.copy(alpha = 0.72f))
        }
    }
}
