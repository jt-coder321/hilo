package com.hilotrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.Boss
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.Outcome
import com.hilotrainer.engine.Perk
import com.hilotrainer.engine.Perks
import com.hilotrainer.engine.Rarity
import com.hilotrainer.engine.Run
import com.hilotrainer.engine.RunPhase
import com.hilotrainer.engine.RunSave
import com.hilotrainer.engine.ScoreEvent
import com.hilotrainer.engine.StartDeck
import com.hilotrainer.engine.handTotal
import kotlin.random.Random

/** Holds one run and the screen's small state. Every change goes through [mut] so the screen redraws and the run is saved between blinds. */
class RunVm(val store: CasualStore, private val rnd: Random = Random.Default) {
    var rev by mutableIntStateOf(0)
    var run by mutableStateOf<Run?>(null)
    var perkOpen by mutableStateOf<Int?>(null)
    var stakePick by mutableIntStateOf(2)
    private var recorded = false

    val hasSave: Boolean get() = !store.runSave.isNullOrBlank()
    fun unlocked(d: StartDeck) = d.unlockAnte == 0 || store.runBest > d.unlockAnte

    fun start(d: StartDeck) {
        if (!unlocked(d)) return
        run = Run(d, rnd); recorded = false; store.runCount = store.runCount + 1; store.runSave = run!!.save(); rev++
    }

    fun resume() {
        val r = RunSave.restore(store.runSave, rnd) ?: return
        run = r; recorded = false; rev++
    }

    fun <T> mut(f: Run.() -> T): T? {
        val r = run ?: return null
        val out = r.f()
        after(r); rev++
        return out
    }

    private fun after(r: Run) {
        if (r.saveable) store.runSave = r.save()
        if ((r.phase == RunPhase.OVER || r.phase == RunPhase.WON) && !recorded) {
            recorded = true
            store.runSave = null
            store.runBest = maxOf(store.runBest, if (r.phase == RunPhase.WON) Run.MAX_ANTE + 1 else r.ante)
            store.runBestHand = maxOf(store.runBestHand, r.bestHand)
            if (r.phase == RunPhase.WON) store.runWins = store.runWins + 1
        }
    }

    fun deal() { mut { deal(stakePick) } }
    fun act(a: Action) { mut { act(a) } }
    fun leaveRun() { run = null; rev++ }
}

private val Uncommon = Color(0xFF6FB7FF)
private val RareCol = Color(0xFFD59CFF)
private val BossRed = Color(0xFFE05A5A)
private fun rarityColor(r: Rarity) = when (r) { Rarity.COMMON -> Cream; Rarity.UNCOMMON -> Uncommon; Rarity.RARE -> RareCol }
private fun fmt(n: Int) = "%,d".format(n)
private fun fmt1(d: Double) = if (d == d.toInt().toDouble()) "${d.toInt()}" else "%.1f".format(d)

@Composable
fun RunScreen(store: CasualStore, onExit: () -> Unit, vm: RunVm = remember { RunVm(store) }) {
    vm.rev
    val r = vm.run
    BackHandler { if (vm.perkOpen != null) vm.perkOpen = null else if (r != null) vm.leaveRun() else onExit() }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E2A1D), Color(0xFF07140E))))) {
        Column(Modifier.fillMaxSize()) {
            RunTop(vm, r) { if (r != null) vm.leaveRun() else onExit() }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    r == null -> StartPane(vm)
                    r.phase == RunPhase.SELECT -> SelectPane(vm, r)
                    r.phase == RunPhase.PLAY -> PlayPane(vm, r)
                    r.phase == RunPhase.CLEARED -> CashoutPane(vm, r)
                    r.phase == RunPhase.SHOP -> ShopPane(vm, r)
                    else -> EndPane(vm, r, onExit)
                }
            }
        }
        val open = vm.perkOpen
        if (r != null && open != null) r.perks.getOrNull(open)?.let { PerkSheet(vm, r, open, it) }
    }
}

@Composable
private fun RunTop(vm: RunVm, r: Run?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF2A1A10)).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold) }
        Column(Modifier.weight(1f)) {
            Text("THE RUN", fontSize = 11.sp, letterSpacing = 2.sp, color = Gold)
            if (r != null && r.phase != RunPhase.OVER && r.phase != RunPhase.WON) Text("Ante ${r.ante} of ${Run.MAX_ANTE}  ·  ${if (r.phase == RunPhase.SHOP) "Shop" else r.blindName}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (r.isBoss) BossRed else Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text("Roguelike mode, casual only", fontSize = 13.sp, color = Cream.copy(alpha = 0.7f))
        }
        if (r != null) Text("\$${r.money}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = GoodGreen, modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = "Money ${r.money} dollars" })
    }
}

// Start

@Composable
private fun StartPane(vm: RunVm) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Beat the blinds", fontSize = 26.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = Gold)
        Text(
            "Every blind sets a chip target. Win hands to score chips times mult, before you run out of hands. Cash from each blind buys perks that bend the numbers. Clear ${Run.MAX_ANTE} antes of three blinds to win the run.",
            fontSize = 14.sp, color = Cream.copy(alpha = 0.85f), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 560.dp),
        )
        if (vm.hasSave) GoldButton("Continue your run", Modifier.widthIn(max = 420.dp).fillMaxWidth()) { vm.resume() }
        Text(if (vm.hasSave) "Or start a new one (this replaces the saved run)" else "Pick a starting deck", fontSize = 13.sp, color = Gold)
        StartDeck.entries.forEach { d ->
            val ok = vm.unlocked(d)
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(if (ok) 0x22FFFFFF else 0x11FFFFFF))
                    .border(1.dp, Gold.copy(alpha = if (ok) 0.6f else 0.2f), RoundedCornerShape(16.dp)).clickable(enabled = ok) { vm.start(d) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(d.label, fontSize = 17.sp, fontWeight = FontWeight.Black, color = if (ok) Cream else Cream.copy(alpha = 0.5f))
                Text(if (ok) d.desc else "Locked. Reach Ante ${d.unlockAnte + 1} in any run to unlock it.", fontSize = 13.sp, color = Cream.copy(alpha = if (ok) 0.8f else 0.5f))
            }
        }
        Text("Best ante ${vm.store.runBest.coerceAtMost(Run.MAX_ANTE).coerceAtLeast(0)}  ·  Runs ${vm.store.runCount}  ·  Wins ${vm.store.runWins}  ·  Best hand ${fmt(vm.store.runBestHand)}", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Text("Casual only. Nothing here touches your training stats.", fontSize = 12.sp, color = Cream.copy(alpha = 0.55f))
    }
}

// Blind select

@Composable
private fun SelectPane(vm: RunVm, r: Run) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        for (i in 0..2) BlindCard(vm, r, i)
        Text("Your perks", fontSize = 12.sp, letterSpacing = 1.sp, color = Gold)
        PerkRow(vm, r, Modifier.widthIn(max = 560.dp))
        if (r.countNotes) Text("Count Notes are active: the running count shows in the next blind.", fontSize = 12.sp, color = Uncommon)
    }
}

@Composable
private fun BlindCard(vm: RunVm, r: Run, i: Int) {
    val active = i == r.blind
    val done = i < r.blind
    val boss = if (i == 2) r.boss else null
    val name = boss?.name ?: Run.BLIND_NAMES[i]
    val target = r.targetFor(r.ante, i, r.boss)
    val col = if (i == 2) BossRed else Gold
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().graphicsLayer { alpha = if (active) 1f else 0.55f }.clip(RoundedCornerShape(18.dp))
            .background(if (active) Color(0x33FFFFFF) else Color(0x14FFFFFF)).border(if (active) 2.dp else 1.dp, col.copy(alpha = if (active) 1f else 0.4f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontSize = 19.sp, fontWeight = FontWeight.Black, fontFamily = TitleFont, color = col, modifier = Modifier.weight(1f))
            if (done) Text("Done", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GoodGreen)
            else Text("Reward \$${Run.REWARD[i]}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GoodGreen)
        }
        Text("Score at least ${fmt(target)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Cream)
        if (boss != null) Text(boss.desc, fontSize = 13.sp, color = BossRed.copy(alpha = 0.95f))
        if (active) {
            val hands = when { boss != null && boss.onlyHands > 0 -> boss.onlyHands; else -> r.deck.hands + r.perks.sumOf { it.handsBonus } + (if (i == 2) boss!!.handsDelta else 0) } + r.bonusHands
            Text("$hands ${if (hands == 1) "hand" else "hands"}", fontSize = 13.sp, color = Cream.copy(alpha = 0.75f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                GoldButton("Play blind", Modifier.weight(1f)) { vm.mut { startBlind() } }
                if (i < 2) GoldButton("Skip  +\$${Run.SKIP_TAG}", Modifier.weight(0.8f), primary = false) { vm.mut { skipBlind() } }
            }
        }
    }
}

// Playing a blind

@Composable
private fun PlayPane(vm: RunVm, r: Run) {
    val gs = r.gs
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScorePanel(r)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(Color(0xFF14573A), Color(0xFF0C3D29))))) {
            val mw = maxWidth
            val cw = minOf(maxWidth * 0.17f, (maxHeight - 64.dp) / 2.9f, 84.dp).coerceAtLeast(34.dp)
            Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                val showHole = gs.holeRevealed || gs.phase == com.hilotrainer.engine.Phase.ROUND_OVER
                Hand("DEALER", gs.dealerCards, cw, mw - 20.dp, hideIndex = if (showHole) -1 else 1, total = if (showHole) null else gs.dealerCards.firstOrNull()?.let { listOf(it) })
                if (gs.hands.isEmpty()) Text("Pick a stake and deal.", fontSize = 15.sp, color = Cream.copy(alpha = 0.8f))
                else {
                    val many = gs.hands.size > 1
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        gs.hands.forEachIndexed { i, h ->
                            val ev = if (!r.inRound) r.lastScore.getOrNull(i) else null
                            Column(Modifier.weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
                                Hand(if (many) "HAND ${i + 1}" else "YOU", h.cards, if (many) cw * 0.78f else cw, if (many) (mw - 40.dp) / gs.hands.size else mw - 20.dp, active = r.inRound && i == gs.activeHand, result = ev)
                            }
                        }
                    }
                }
            }
            if (r.countVisible) Text(
                "RUN ${gs.runningCount}  TRUE ${"%.1f".format(gs.trueCountExact)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Tally(r)
        PerkRow(vm, r, Modifier.fillMaxWidth(), fire = r.lastScore)
        PlayControls(vm, r)
    }
}

@Composable
private fun ScorePanel(r: Run) {
    val frac by androidx.compose.animation.core.animateFloatAsState((r.score.toFloat() / r.target).coerceIn(0f, 1f), tween(500), label = "bar")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x33000000)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SCORE AT LEAST", fontSize = 10.sp, letterSpacing = 1.sp, color = Gold)
                Text(fmt(r.target), fontSize = 26.sp, fontWeight = FontWeight.Black, color = Cream)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("HANDS LEFT", fontSize = 10.sp, letterSpacing = 1.sp, color = Gold)
                Text("${r.handsLeft}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = if (r.handsLeft <= 1) BadRed else Cream)
            }
        }
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFB8892B), Gold))))
        }
        Row {
            Text("Scored ${fmt(r.score)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Gold, modifier = Modifier.weight(1f))
            val combo = r.comboNow
            if (combo > 0) Text("Streak ${r.winStreak}: +${fmt1(combo)} mult", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF8A3D))
        }
        r.activeBoss?.let { Text("${it.name}: ${it.desc}", fontSize = 12.sp, color = BossRed, maxLines = 2) }
    }
}

@Composable
private fun Hand(label: String, cards: List<Card>, cw: Dp, maxW: Dp, hideIndex: Int = -1, total: List<Card>? = null, active: Boolean = false, result: ScoreEvent? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontSize = 11.sp, letterSpacing = 1.sp, color = if (active) Gold else Cream.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            if (cards.isNotEmpty()) {
                val shown = total ?: cards
                val (t, soft) = handTotal(shown)
                val txt = when { t > 21 -> "Bust"; shown.size == 2 && t == 21 -> "21!"; soft && t < 21 -> "${t - 10}/$t"; else -> "$t" }
                Text(txt, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.background(Cream, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp))
            }
            result?.let { ev ->
                val good = ev.total > 0
                val bad = ev.outcome == Outcome.LOSE || ev.outcome == Outcome.BUST
                Text(
                    when { good -> "+${fmt(ev.total)}"; bad -> ev.outcome.label; else -> if (ev.outcome == Outcome.PUSH) "Push" else "No score" },
                    fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (good) GoodGreen else if (bad) BadRed else Cream,
                )
            }
        }
        val step = if (cards.size <= 1) 0.dp else minOf(cw * 0.62f, (maxW - cw) / (cards.size - 1)).coerceAtLeast(cw * 0.2f)
        Box(Modifier.width(cw + step * (cards.size - 1).coerceAtLeast(0)).height(cw * 1.4f).then(if (active) Modifier.border(2.dp, Gold, RoundedCornerShape(8.dp)).padding(0.dp) else Modifier)) {
            cards.forEachIndexed { i, c -> key2(c, i) { Pop(Modifier.offset(step * i, 0.dp)) { PlayingCard(c, cw, faceDown = i == hideIndex) } } }
        }
    }
}

@Composable
private fun key2(c: Card, i: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(c, i) { content() }

/** A card that pops in when it first appears. */
@Composable
private fun Pop(modifier: Modifier, content: @Composable () -> Unit) {
    val p = remember { Animatable(0.5f) }
    LaunchedEffect(Unit) { p.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 500f)) }
    Box(modifier.graphicsLayer { scaleX = p.value; scaleY = p.value; alpha = ((p.value - 0.5f) / 0.5f).coerceIn(0f, 1f) }) { content() }
}

/** The tally of the last round: chips times mult for each scoring hand, counting up, with what each perk added. */
@Composable
private fun Tally(r: Run) {
    val evs = if (r.inRound) emptyList() else r.lastScore
    Column(Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x26000000)).padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
        if (evs.isEmpty()) { Text(if (r.inRound) "Hit, stand, double or split. Win to score chips x mult." else "Win a hand to score chips x mult.", fontSize = 13.sp, color = Cream.copy(alpha = 0.65f)); return@Column }
        val ev = evs.firstOrNull { it.total > 0 } ?: evs.first()
        if (evs.all { it.total == 0 }) {
            val lost = ev.outcome == Outcome.LOSE || ev.outcome == Outcome.BUST
            Text(
                if (ev.note.isNotEmpty()) ev.note else if (lost) "${ev.outcome.label}. No score${if (ev.money < 0) ", \$${-ev.money} lost" else ""}." else "Push. No score.",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (lost) BadRed else Cream,
            )
            ev.triggers.forEach { Text("${it.perk}: ${it.text}", fontSize = 12.sp, color = Uncommon) }
            return@Column
        }
        key(evs.first().key) {
            val p = remember { Animatable(0f) }
            LaunchedEffect(Unit) { p.animateTo(1f, tween(900)) }
            val chipsP = (p.value / 0.4f).coerceIn(0f, 1f)
            val multP = ((p.value - 0.4f) / 0.4f).coerceIn(0f, 1f)
            val totP = ((p.value - 0.8f) / 0.2f).coerceIn(0f, 1f)
            val totalAll = evs.sumOf { it.total }
            val chips = (ev.chips * chipsP).toInt()
            val mult = 1.0 + (ev.mult - 1.0) * multP
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fmt(chips), fontSize = 20.sp, fontWeight = FontWeight.Black, color = Uncommon, modifier = Modifier.background(Color(0x33000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                Text("x", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Cream)
                Text(fmt1(Math.round(mult * 10) / 10.0), fontSize = 20.sp, fontWeight = FontWeight.Black, color = BadRed, modifier = Modifier.background(Color(0x33000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                Text("=", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Cream)
                Text(
                    fmt((totalAll * totP).toInt()), fontSize = 24.sp, fontWeight = FontWeight.Black, color = Gold,
                    modifier = Modifier.graphicsLayer { val s = 1f + 0.25f * (1f - Math.abs(totP * 2f - 1f)) * (if (totP > 0f && totP < 1f) 1f else 0f); scaleX = s; scaleY = s },
                )
                if (evs.count { it.total > 0 } > 1) Text("(${evs.count { it.total > 0 }} hands)", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f))
            }
            val trig = evs.flatMap { it.triggers }
            if (trig.isNotEmpty()) Text(trig.joinToString("  ·  ") { "${it.perk} ${it.text}" }, fontSize = 12.sp, color = Cream.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun key(k: Any, content: @Composable () -> Unit) = androidx.compose.runtime.key(k) { content() }

@Composable
private fun PerkRow(vm: RunVm, r: Run, modifier: Modifier, fire: List<ScoreEvent> = emptyList()) {
    val firedNames = fire.flatMap { it.triggers }.map { it.perk }.toSet()
    val fireKey = fire.firstOrNull()?.key ?: 0
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until Run.SLOTS) {
            val pk = r.perks.getOrNull(i)
            Box(Modifier.weight(1f)) {
                if (pk == null) Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Cream.copy(alpha = 0.2f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text("Empty", fontSize = 10.sp, color = Cream.copy(alpha = 0.35f))
                } else PerkTile(pk, if (pk.name in firedNames) fireKey else 0) { vm.perkOpen = i }
            }
        }
    }
}

@Composable
private fun PerkTile(pk: Perk, fire: Int, onTap: () -> Unit) {
    val s = remember { Animatable(1f) }
    LaunchedEffect(fire) { if (fire != 0) { s.animateTo(1.16f, tween(140)); s.animateTo(1f, spring(dampingRatio = 0.4f)) } }
    val col = rarityColor(pk.rarity)
    Column(
        Modifier.fillMaxWidth().height(58.dp).graphicsLayer { scaleX = s.value; scaleY = s.value }.clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(col.copy(alpha = 0.28f), Color(0x33000000)))).border(1.5.dp, col.copy(alpha = if (fire != 0) 1f else 0.7f), RoundedCornerShape(10.dp))
            .clickable(onClick = onTap).semantics { contentDescription = "${pk.name}, ${pk.rarity.label} perk. ${pk.desc}" }.padding(4.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(pk.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = col, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 13.sp)
        Text(pk.rarity.label, fontSize = 9.sp, color = Cream.copy(alpha = 0.55f))
    }
}

@Composable
private fun PlayControls(vm: RunVm, r: Run) {
    val gs = r.gs
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!r.inRound) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (1..3).forEach { s ->
                    val on = vm.stakePick == s
                    val ok = r.canDeal(s)
                    Column(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Gold else Color(0x22FFFFFF)).border(1.dp, Gold.copy(alpha = if (ok) 0.8f else 0.2f), RoundedCornerShape(12.dp))
                            .clickable(enabled = ok) { vm.stakePick = s; vm.rev++ }.semantics { contentDescription = "Stake $s. Wins score $s times the chips, a loss costs $s dollars." },
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Stake $s", fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (on) Ink else if (ok) Cream else Cream.copy(alpha = 0.4f))
                        Text("x$s chips, -\$$s loss", fontSize = 10.sp, color = if (on) Ink.copy(alpha = 0.8f) else Cream.copy(alpha = if (ok) 0.7f else 0.35f))
                    }
                }
            }
            GoldButton("Deal", Modifier.fillMaxWidth(), enabled = r.canDeal(vm.stakePick)) { vm.deal() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                GoldButton("Hit", Modifier.weight(1f), enabled = r.canAct(Action.HIT)) { vm.act(Action.HIT) }
                GoldButton("Stand", Modifier.weight(1f), enabled = r.canAct(Action.STAND)) { vm.act(Action.STAND) }
                GoldButton("Double", Modifier.weight(1f), enabled = r.canAct(Action.DOUBLE), primary = false) { vm.act(Action.DOUBLE) }
                GoldButton("Split", Modifier.weight(1f), enabled = r.canAct(Action.SPLIT), primary = false) { vm.act(Action.SPLIT) }
            }
            Text(if (r.activeBoss?.noDoubleSplit == true) "The Hook: no doubling or splitting." else "Stake ${r.stake}: wins score x${r.stake} chips.", fontSize = 11.sp, color = Cream.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// Cash out

@Composable
private fun CashoutPane(vm: RunVm, r: Run) {
    val co = r.cashout ?: return
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("BLIND CLEARED", fontSize = 28.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        Text("${fmt(r.score)} of ${fmt(r.target)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Cream)
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x33000000)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            co.lines.forEach { (l, n) -> Row { Text(l, fontSize = 15.sp, color = Cream, modifier = Modifier.weight(1f)); Text("+\$$n", fontSize = 16.sp, fontWeight = FontWeight.Black, color = GoodGreen) } }
            Row { Text("Total", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.weight(1f)); Text("+\$${co.total}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = GoodGreen) }
        }
        Text("You now have \$${r.money}", fontSize = 14.sp, color = Cream.copy(alpha = 0.8f))
        GoldButton(if (r.isBoss && r.ante >= Run.MAX_ANTE) "Finish the run" else "To the shop", Modifier.widthIn(max = 460.dp).fillMaxWidth()) { vm.mut { collect() } }
    }
}

// Shop

@Composable
private fun ShopPane(vm: RunVm, r: Run) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("THE SHOP", fontSize = 22.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = Gold)
        r.offers.forEachIndexed { i, o ->
            val col = rarityColor(o.rarity)
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().graphicsLayer { alpha = if (o.sold) 0.4f else 1f }.clip(RoundedCornerShape(14.dp)).background(Brush.horizontalGradient(listOf(col.copy(alpha = 0.22f), Color(0x22000000))))
                    .border(1.5.dp, col.copy(alpha = 0.8f), RoundedCornerShape(14.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(o.name, fontSize = 15.sp, fontWeight = FontWeight.Black, color = col)
                        Text(if (o.perk != null) o.rarity.label else "Tonic", fontSize = 10.sp, color = Cream.copy(alpha = 0.6f))
                    }
                    Text(o.desc, fontSize = 12.sp, color = Cream.copy(alpha = 0.85f))
                }
                GoldButton(if (o.sold) "Sold" else "\$${o.cost}", Modifier.width(78.dp), enabled = r.canBuy(i)) { vm.mut { buy(i) } }
            }
        }
        if (r.perks.size >= Run.SLOTS) Text("Your perk slots are full. Sell one to buy another.", fontSize = 12.sp, color = BadRed)
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton("Reroll \$${r.rerollCost}", Modifier.weight(1f), enabled = r.money >= r.rerollCost, primary = false) { vm.mut { reroll() } }
            GoldButton("Next blind", Modifier.weight(1f)) { vm.mut { leaveShop() } }
        }
        Text("Your perks (tap one to sell it)", fontSize = 12.sp, letterSpacing = 1.sp, color = Gold)
        PerkRow(vm, r, Modifier.widthIn(max = 560.dp))
    }
}

@Composable
private fun PerkSheet(vm: RunVm, r: Run, i: Int, pk: Perk) {
    val col = rarityColor(pk.rarity)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable { vm.perkOpen = null }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).padding(20.dp).clip(RoundedCornerShape(20.dp)).background(FeltDark).border(2.dp, col, RoundedCornerShape(20.dp)).clickable(enabled = false) {}.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(pk.name, fontSize = 22.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = col)
            Text(pk.rarity.label, fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
            Text(pk.desc, fontSize = 15.sp, color = Cream, textAlign = TextAlign.Center)
            val canSell = r.phase == RunPhase.SHOP || r.phase == RunPhase.SELECT
            if (canSell) GoldButton("Sell for \$${pk.sell}", Modifier.fillMaxWidth(), primary = false) { vm.mut { sell(i) }; vm.perkOpen = null }
            GoldButton("Close", Modifier.fillMaxWidth()) { vm.perkOpen = null }
        }
    }
}

// End

@Composable
private fun EndPane(vm: RunVm, r: Run, onExit: () -> Unit) {
    val won = r.phase == RunPhase.WON
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (won) "YOU BEAT THE HOUSE" else "RUN OVER", fontSize = 28.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = if (won) Gold else BadRed, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        if (!won) Text(r.cause, fontSize = 14.sp, color = Cream.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0x33000000)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            fun line(a: String, b: String) = @Composable { Row { Text(a, fontSize = 14.sp, color = Cream.copy(alpha = 0.8f), modifier = Modifier.weight(1f)); Text(b, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Cream) } }
            line("Deck", r.deck.label)()
            line("Ante reached", "${if (won) Run.MAX_ANTE else r.ante} of ${Run.MAX_ANTE}")()
            line("Blinds cleared", "${r.blindsCleared}")()
            line("Best hand", fmt(r.bestHand))()
            line("Blinds skipped", "${r.skips}")()
        }
        Text("Your perks", fontSize = 12.sp, letterSpacing = 1.sp, color = Gold)
        Text(if (r.perks.isEmpty()) "None" else r.perks.joinToString(", ") { it.name }, fontSize = 13.sp, color = Cream.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        GoldButton("New run", Modifier.widthIn(max = 460.dp).fillMaxWidth()) { vm.leaveRun() }
        GoldButton("Back to the table", Modifier.widthIn(max = 460.dp).fillMaxWidth(), primary = false) { onExit() }
    }
}
