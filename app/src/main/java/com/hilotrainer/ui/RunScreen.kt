package com.hilotrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.Edition
import com.hilotrainer.engine.HandCtx
import com.hilotrainer.engine.HandType
import com.hilotrainer.engine.Outcome
import com.hilotrainer.engine.Perk
import com.hilotrainer.engine.Rarity
import com.hilotrainer.engine.Run
import com.hilotrainer.engine.RunPhase
import com.hilotrainer.engine.RunMeta
import com.hilotrainer.engine.RunRecord
import com.hilotrainer.engine.RunSave
import com.hilotrainer.engine.ScoreEvent
import com.hilotrainer.engine.StartDeck
import com.hilotrainer.engine.handTotal
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** A perk that just fired, shown rising off its card. */
class Popup(val perk: String, val text: String, val kind: Int, val seq: Int)

/** What the chips x mult boxes show while a hand scores. */
class ScoreAnim {
    var chips by mutableIntStateOf(0)
    var mult by mutableDoubleStateOf(0.0)
    var label by mutableStateOf("")
    var note by mutableStateOf("")
    var shown by mutableIntStateOf(-1)
    var popup by mutableStateOf<Popup?>(null)
    var slam by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
    fun clear() { label = ""; note = ""; popup = null; total = 0; chips = 0; mult = 0.0; shown = -1 }
}

val LocalRunSfx = staticCompositionLocalOf<(Fx, Float) -> Unit> { { _, _ -> } }

/** Holds one run and the screen's small state. Every change goes through [mut] so the screen redraws and the run is saved between blinds. */
class RunVm(val store: CasualStore, private val rnd: Random = Random.Default) {
    var rev by mutableIntStateOf(0)
    var run by mutableStateOf<Run?>(null)
    var perkOpen by mutableStateOf<Int?>(null)
    var levelsOpen by mutableStateOf(false)
    var stakePick by mutableIntStateOf(2)
    var fxHook: (Fx, Float) -> Unit = { _, _ -> }
    val sa = ScoreAnim()
    val shaker = Shaker()
    /** True while a finished hand is still being scored on screen. */
    var animating by mutableStateOf(false)
    var animKey by mutableIntStateOf(0)
    var fast = false
    var soundOn by mutableStateOf(store.runSound)
    var callout by mutableStateOf<Callout?>(null)
    var fired by mutableStateOf<Set<Int>>(emptySet())
    var shockKey by mutableIntStateOf(0)
    var glitchKey by mutableIntStateOf(0)
    var bossKey by mutableIntStateOf(0)
    var anteKey by mutableIntStateOf(0)
    var wipeKey by mutableIntStateOf(0)
    var coinKey by mutableIntStateOf(0)
    var packOpen by mutableStateOf(false)
    var historyOpen by mutableStateOf(false)
    var guideOpen by mutableStateOf(false)
    private var lastAnte = 0
    private var lastBlindSeen = -1
    private var calloutSeq = 0
    private var goalSeen = 0
    fun say(text: String, color: Color, power: Int) { callout = Callout(text, color, power, ++calloutSeq) }
    private var seenKey = 0
    private var recorded = false

    fun sfx(e: Fx, pitch: Float = 1f) { fxHook(e, pitch) }
    fun toggleSound() { soundOn = !soundOn; store.runSound = soundOn; rev++ }

    val hasSave: Boolean get() = !store.runSave.isNullOrBlank()
    var lastReward by mutableStateOf<RunReward?>(null)
    var codexOpen by mutableStateOf(false)
    var goalsOpen by mutableStateOf(false)
    val rank: Int get() = RunMeta.rankOf(store.runXp)
    fun unlocked(d: StartDeck) = (d.unlockAnte == 0 && d.unlockRank == 0) || (d.unlockAnte > 0 && store.runBest > d.unlockAnte) || (d.unlockRank > 0 && rank >= d.unlockRank)
    val hardOn: Boolean get() = store.runHard && store.runWins > 0
    fun toggleHard() { store.runHard = !store.runHard; rev++ }

    /** Every perk the player has seen in a shop or pack is added to the collection. */
    private fun noteSeen(r: Run) {
        val have = store.runSeen.split(',').filter { it.isNotEmpty() }.toMutableSet()
        val before = have.size
        r.perks.forEach { have += it.id }
        r.offers.forEach { o -> o.perk?.let { have += it.id } }
        r.pack?.forEach { have += it.id }
        if (have.size != before) store.runSeen = have.joinToString(",")
    }

    private fun payOut(r: Run) {
        val rec = RunRecord(if (r.phase == RunPhase.WON) Run.MAX_ANTE else r.ante, r.blindsCleared, r.bestHand, r.phase == RunPhase.WON, r.deck, r.maxPerks, r.skips, r.goalsDone, r.hard)
        store.runBonusTotal = store.runBonusTotal + r.goalsDone
        val seenCount = store.runSeen.split(',').count { it.isNotEmpty() }
        val done = store.runGoalsDone.split(',').filter { it.isNotEmpty() }.toMutableSet()
        val fresh = RunMeta.GOALS.filter { it.id !in done && it.test(rec, seenCount, store.runBonusTotal) }
        val gained = RunMeta.xpFor(rec) + fresh.sumOf { it.xp }
        val before = store.runXp
        val after = before + gained
        store.runXp = after
        store.runGoalsDone = (done + fresh.map { it.id }).joinToString(",")
        val unlocks = StartDeck.entries.filter { it.unlockRank > RunMeta.rankOf(before) && it.unlockRank <= RunMeta.rankOf(after) }.map { it.label }
        lastReward = RunReward(gained, before, after, fresh, unlocks)
    }

    fun start(d: StartDeck) {
        if (!unlocked(d)) return
        if (store.runCount == 0) guideOpen = true
        lastReward = null
        run = Run(d, rnd, hardOn).also { it.openStarterPack() }; recorded = false; seenKey = 0; lastAnte = 0; lastBlindSeen = -1; packOpen = false; animating = false; sa.clear(); store.runCount = store.runCount + 1; store.runSave = run!!.save(); rev++
    }

    fun resume() {
        val r = RunSave.restore(store.runSave, rnd) ?: return
        run = r; recorded = false; seenKey = 0; lastAnte = r.ante; lastBlindSeen = r.blind; packOpen = false; animating = false; sa.clear(); rev++
    }

    fun <T> mut(f: Run.() -> T): T? {
        val r = run ?: return null
        val out = r.f()
        after(r); rev++
        return out
    }

    private fun after(r: Run) {
        val k = r.lastScore.firstOrNull()?.key ?: 0
        if (k != 0 && k != seenKey) { seenKey = k; animKey = k; animating = animOn; fast = false }
        if (r.saveable) store.runSave = r.save()
        noteSeen(r)
        if (r.phase == RunPhase.PLAY && !r.inRound && (r.ante != lastAnte || r.blind != lastBlindSeen)) {
            if (r.ante != lastAnte) anteKey++
            if (r.isBoss) { bossKey++; glitchKey++ }
            lastAnte = r.ante; lastBlindSeen = r.blind
        }
        if (r.phase == RunPhase.SELECT || r.phase == RunPhase.SHOP) wipeKey++
        if ((r.phase == RunPhase.OVER || r.phase == RunPhase.WON) && !recorded) {
            recorded = true
            store.runSave = null
            store.runBest = maxOf(store.runBest, if (r.phase == RunPhase.WON) Run.MAX_ANTE + 1 else r.ante)
            store.runBestHand = maxOf(store.runBestHand, r.bestHand)
            if (r.phase == RunPhase.WON) store.runWins = store.runWins + 1
            val row = "${if (r.phase == RunPhase.WON) Run.MAX_ANTE else r.ante},${r.blindsCleared},${r.bestHand},${if (r.phase == RunPhase.WON) 1 else 0},${r.deck.ordinal}"
            store.runHistory = (store.runHistory.split(';').filter { it.isNotBlank() } + row).takeLast(20).joinToString(";")
            payOut(r)
        }
    }

    fun deal() { sa.clear(); sfx(Fx.CHIP, 1f); mut { deal(stakePick) } }
    fun act(a: Action) { mut { act(a) } }
    fun leaveRun() { run = null; animating = false; rev++ }

    private fun step(ms: Long): Long = if (fast) 0 else ms

    /** The Balatro moment: base chips and mult, each perk firing in turn, then the total slams in. */
    suspend fun playScore(r: Run) {
        val evs = r.lastScore
        if (evs.isEmpty()) { animating = false; return }
        var running = r.score - evs.sumOf { it.total }
        sa.shown = running
        sa.chips = 0; sa.mult = 0.0; sa.label = ""; sa.note = ""
        delay(step(950))
        var pitchN = 0
        for ((idx, ev) in evs.withIndex()) {
            val tag = if (evs.size > 1) "Hand ${idx + 1}: " else ""
            if (ev.total > 0 && ev.type != null) {
                sa.label = "$tag${ev.type.label}  Lv.${ev.level + 1}"
                if (ev.outcome == Outcome.BLACKJACK) { say("BLACKJACK!", Color(0xFFD64545), 2); sfx(Fx.RG_CHIME, 1.3f) }
                else if (r.winStreak >= 3) say("${r.winStreak} IN A ROW", Color(0xFFE0782B), 1)
                else if (ev.type.ordinal >= HandType.entries.size - 3) say(ev.type.label.uppercase(), Color(0xFF6A4DE0), 1)
                sa.note = ""
                sa.chips = ev.baseChips; sa.mult = ev.baseMult
                sfx(Fx.RG_TICK, 1f); delay(step(480))
                val ordered = ev.triggers.sortedBy { if (it.chips != 0) 0 else if (it.mult != 0.0) 1 else if (it.x != 1.0) 2 else 3 }
                for (t in ordered) {
                    val kind = when { t.chips != 0 -> 0; t.mult != 0.0 -> 1; t.x != 1.0 -> 2; else -> 3 }
                    sa.popup = Popup(t.perk, t.text, kind, sa.slam * 100 + pitchN)
                    r.perks.indices.firstOrNull { r.perks[it].title == t.perk || t.perk.startsWith(r.perks[it].name + ":") }?.let { fired = fired + it }
                    if (t.chips != 0) sa.chips += t.chips
                    if (t.mult != 0.0) sa.mult += t.mult
                    if (t.x != 1.0) sa.mult *= t.x
                    val pitch = (1f + 0.06f * pitchN).coerceAtMost(2.2f)
                    sfx(when (kind) { 2 -> Fx.RG_CHIME; 3 -> Fx.RG_COIN; else -> Fx.RG_TICK }, pitch)
                    pitchN++
                    delay(step(430))
                }
                sa.popup = null
                sa.chips = ev.chips; sa.mult = ev.mult
                val to = running + ev.total
                val from = running
                if (!fast) {
                    val a = Animatable(from.toFloat())
                    a.animateTo(to.toFloat(), tween(520, easing = LinearEasing)) { sa.shown = value.toInt() }
                }
                sa.shown = to; running = to
                sa.total = ev.total; sa.slam++
                sfx(Fx.RG_SLAM, 1f)
                if (ev.total * 4 >= r.target) { shockKey++; say("HUGE HAND", Color(0xFFD99A1E), 3); sfx(Fx.RG_CHIME, 0.8f) }
                shaker.hit((ev.total.toFloat() / r.target.coerceAtLeast(1) * 3f).coerceIn(0.25f, 1f))
                delay(step(520))
            } else {
                val lost = ev.outcome == Outcome.LOSE || ev.outcome == Outcome.BUST
                sa.label = ""
                sa.note = if (ev.note.isNotEmpty()) ev.note else if (lost) "${ev.outcome.label}. No score${if (ev.money < 0) ", \$${-ev.money} lost" else ""}." else "Push. No score."
                if (lost) { sfx(Fx.RG_BUST, 1f); shaker.hit(0.25f) }
                delay(step(750))
            }
        }
        if (r.goalKey != 0 && r.goalKey != goalSeen) { goalSeen = r.goalKey; say("GOAL DONE +\$${Run.GOAL_PAY}", Color(0xFF2FA866), 2); sfx(Fx.RG_COIN, 1.4f) }
        sa.shown = -1
        fired = emptySet()
        animating = false
    }
}

private val Uncommon = Color(0xFF6FB7FF)
private val RareCol = Color(0xFFD59CFF)
internal val BossRed = Color(0xFFE05A5A)
private val Mono = FontFamily.Monospace
internal fun rarityColor(r: Rarity) = when (r) { Rarity.COMMON -> Color(0xFFEFE6CF); Rarity.UNCOMMON -> Uncommon; Rarity.RARE -> RareCol }
private fun fmt(n: Int) = "%,d".format(n)
private fun fmt1(d: Double): String { val r = Math.round(d * 10) / 10.0; return if (r == r.toInt().toDouble()) "${r.toInt()}" else "%.1f".format(r) }

@Composable
fun RunScreen(store: CasualStore, onExit: () -> Unit, vm: RunVm = remember { RunVm(store) }, fx: (Fx, Float) -> Unit = { _, _ -> }) {
    vm.rev
    vm.fxHook = fx
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val sysOff = remember { try { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false } }
    RunMotion.reduce = !vm.store.runMotion || sysOff
    RunMotion.quality = vm.store.runQuality
    val r = vm.run
    BackHandler { if (vm.codexOpen) vm.codexOpen = false else if (vm.goalsOpen) vm.goalsOpen = false else if (vm.guideOpen) vm.guideOpen = false else if (vm.historyOpen) vm.historyOpen = false else if (vm.levelsOpen) vm.levelsOpen = false else if (vm.perkOpen != null) vm.perkOpen = null else if (r != null) vm.leaveRun() else onExit() }
    val venue = Venues.of(r?.ante ?: 1)
    CompositionLocalProvider(LocalRunSfx provides vm::sfx) {
        RippleLayer(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize()) {
            RunBackdrop(venue, r?.isBoss == true && r.phase != RunPhase.SELECT)
            if (r != null && r.phase == RunPhase.PLAY && r.handsLeft <= 1) EdgeGlow(Color(0xFFFF3B3B), if (r.handsLeft == 0) 0.5f else 0.9f, 700)
            else if (r != null && r.winStreak >= 3 && r.phase == RunPhase.PLAY) EdgeGlow(Color(0xFFFF9A2B), (0.25f + 0.12f * r.winStreak).coerceAtMost(0.8f), 1200)
            Column(Modifier.fillMaxSize()) {
                RunTop(vm, r) { if (r != null) vm.leaveRun() else onExit() }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val playing = r != null && (r.phase == RunPhase.PLAY || (vm.animating && (r.phase == RunPhase.CLEARED || r.phase == RunPhase.OVER)))
                    when {
                        r == null -> StartPane(vm)
                        playing -> PlayPane(vm, r)
                        r.phase == RunPhase.SELECT -> SelectPane(vm, r)
                        r.phase == RunPhase.CLEARED -> CashoutPane(vm, r)
                        r.phase == RunPhase.SHOP -> ShopPane(vm, r)
                        else -> EndPane(vm, r, onExit)
                    }
                }
            }
            val open = vm.perkOpen
            if (r != null && open != null) r.perks.getOrNull(open)?.let { PerkSheet(vm, r, open, it) }
            if (r != null && vm.levelsOpen) LevelsSheet(vm, r)
            if (r != null && r.pack != null) PackSheet(vm, r)
            if (r == null && vm.historyOpen) HistorySheet(vm)
            if (r == null && vm.codexOpen) CodexSheet(vm.store) { vm.codexOpen = false }
            if (r == null && vm.goalsOpen) GoalsSheet(vm.store) { vm.goalsOpen = false }
            if (vm.guideOpen) RunGuideSheet { vm.guideOpen = false }
            CardWipe(vm.wipeKey, venue.base)
            Shockwave(vm.shockKey, Color(0xFFFFD35A))
            CalloutOverlay(vm.callout, Modifier.padding(top = 80.dp))
            GlitchOverlay(vm.glitchKey)
            AnteBanner(vm.anteKey, r?.ante ?: 1, venue)
            if (r != null && r.isBoss) BossIntro(vm.bossKey, r.boss.name, r.boss.desc)
          }
        }
    }
}

@Composable
private fun RunTop(vm: RunVm, r: Run?, onBack: () -> Unit) {
    vm.rev
    Row(Modifier.fillMaxWidth().background(Color(0xCC120A06)).padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold) }
        Column(Modifier.weight(1f)) {
            Text("ROGUE RUN", fontSize = 11.sp, letterSpacing = 2.sp, color = Gold, fontFamily = Mono, fontWeight = FontWeight.Black)
            if (r != null && r.phase != RunPhase.OVER && r.phase != RunPhase.WON) Text("Ante ${r.ante}/${Run.MAX_ANTE}  ·  ${if (r.phase == RunPhase.SHOP) "Shop" else r.blindName}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (r.isBoss) BossRed else Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text("Roguelike mode, casual only", fontSize = 13.sp, color = Cream.copy(alpha = 0.7f))
        }
        Text(
            "?", fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Ink,
            modifier = Modifier.padding(end = 8.dp).chunky(Color(0xFF5ED0FF), radius = 8.dp, lift = 2.dp, border = 2.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.guideOpen = true }.padding(horizontal = 10.dp, vertical = 4.dp)
                .semantics { contentDescription = "How Rogue Run works" },
        )
        Text(
            "SFX", fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (vm.soundOn) Ink else Cream.copy(alpha = 0.55f),
            textDecoration = if (vm.soundOn) null else androidx.compose.ui.text.style.TextDecoration.LineThrough,
            modifier = Modifier.padding(end = 8.dp).chunky(if (vm.soundOn) Gold else Color(0xFF3A3A4A), radius = 8.dp, lift = 2.dp, border = 2.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.toggleSound() }.padding(horizontal = 8.dp, vertical = 5.dp)
                .semantics { contentDescription = if (vm.soundOn) "Mute sound effects" else "Turn sound effects on" },
        )
        if (r != null) Text("\$${r.money}", fontSize = 18.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.padding(end = 10.dp).chunky(Color(0xFFFFD35A), radius = 10.dp, lift = 2.dp, border = 2.dp).padding(horizontal = 10.dp, vertical = 3.dp).semantics { contentDescription = "Money ${r.money} dollars" })
    }
}

@Composable
internal fun ChunkyButton(text: String, color: Color, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false, onClick: () -> Unit) {
    val fill = if (enabled) Brush.verticalGradient(listOf(lighter(color), color)) else Brush.verticalGradient(listOf(Color(0xFF3A3A44), Color(0xFF2C2C34)))
    Box(
        modifier.height(if (small) 40.dp else 48.dp).chunky(fill, radius = 12.dp, lift = 3.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = if (small) 14.sp else 17.sp, fontWeight = FontWeight.Black, maxLines = 1, color = if (enabled) Color.White else Color(0xFF8A8A96))
    }
}

private fun lighter(c: Color) = androidx.compose.ui.graphics.lerp(c, Color.White, 0.28f)

// Start

@Composable
private fun StartPane(vm: RunVm) {
    vm.rev
    val live = animOn
    val bob = if (live) rememberInfiniteTransition(label = "logo").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "b").value else 0.5f
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "ROGUE\nRUN", fontSize = 52.sp, lineHeight = 52.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Gold, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { rotationZ = (bob - 0.5f) * 5f; translationY = (bob - 0.5f) * 8f }.padding(top = 4.dp),
        )
        Text(
            "Beat each blind's chip target before you run out of hands. Wins score chips x mult. Train your hand types, stack perks and editions, and clear ${Run.MAX_ANTE} antes to win.",
            fontSize = 14.sp, color = Cream.copy(alpha = 0.88f), textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 520.dp),
        )
        RankCard(vm.store, Modifier.widthIn(max = 520.dp))
        if (vm.hasSave) ChunkyButton("Continue your Rogue Run", Color(0xFF2FA866), Modifier.widthIn(max = 420.dp).fillMaxWidth()) { vm.resume() }
        if (vm.store.runWins > 0) Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().chunky(if (vm.hardOn) Color(0xFF7A1E1E) else Color(0xFF241F3F), radius = 12.dp, lift = 2.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.toggleHard() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("HARD MODE  ${if (vm.hardOn) "ON" else "OFF"}", fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (vm.hardOn) Color(0xFFFF8A8A) else Gold)
                Text("Targets are 30% higher. Rank points are doubled.", fontSize = 11.sp, color = Cream.copy(alpha = 0.8f))
            }
        }
        Text(if (vm.hasSave) "Or start a new one (this replaces the saved run)" else "Pick a starting deck", fontSize = 13.sp, color = Gold, fontFamily = Mono)
        StartDeck.entries.forEach { d ->
            val ok = vm.unlocked(d)
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxWidth().chunky(Brush.verticalGradient(listOf(Color(if (ok) 0xFF2A2350 else 0xFF1B1B24), Color(if (ok) 0xFF191433 else 0xFF14141B))), radius = 16.dp)
                    .clip(RoundedCornerShape(16.dp)).clickable(enabled = ok) { vm.sfx(Fx.DEAL, 1f); vm.start(d) }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(d.label, fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (ok) Gold else Cream.copy(alpha = 0.5f))
                Text(if (ok) d.desc else if (d.unlockRank > 0) "Locked. Reach rank ${RunMeta.title(d.unlockRank)} to unlock it." else "Locked. Reach Ante ${d.unlockAnte + 1} in any run to unlock it.", fontSize = 13.sp, color = Cream.copy(alpha = if (ok) 0.85f else 0.5f))
            }
        }
        val found = vm.store.runSeen.split(',').count { it.isNotEmpty() }
        val goalsN = vm.store.runGoalsDone.split(',').count { it.isNotEmpty() }
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChunkyButton("Perks $found/${com.hilotrainer.engine.Perks.ALL.size}", Color(0xFF9B5CE6), Modifier.weight(1f), small = true) { vm.codexOpen = true }
            ChunkyButton("Goals $goalsN/${RunMeta.GOALS.size}", Color(0xFFC9922A), Modifier.weight(1f), small = true) { vm.goalsOpen = true }
        }
        ChunkyButton("How it works in plain words", Color(0xFF2E8BFF), Modifier.widthIn(max = 520.dp).fillMaxWidth(), small = true) { vm.guideOpen = true }
        ChunkyButton("Run history", Color(0xFF6A4DE0), Modifier.widthIn(max = 520.dp).fillMaxWidth(), small = true) { vm.historyOpen = true }
        Text("Best ante ${vm.store.runBest.coerceAtMost(Run.MAX_ANTE).coerceAtLeast(0)}  ·  Runs ${vm.store.runCount}  ·  Wins ${vm.store.runWins}  ·  Best hand ${fmt(vm.store.runBestHand)}", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f), textAlign = TextAlign.Center)
        Text("Casual only. Nothing here touches your training stats.", fontSize = 12.sp, color = Cream.copy(alpha = 0.55f))
    }
}

// Blind select

@Composable
private fun SelectPane(vm: RunVm, r: Run) {
    vm.rev
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VenueHeader(r, if (r.isBoss) "I'm ${r.boss.name}. ${r.boss.desc}" else "Welcome to ${Venues.of(r.ante).name}. ${Venues.of(r.ante).tag}.")
        JourneyMap(r, Modifier.widthIn(max = 560.dp))
        for (i in 0..2) BlindCard(vm, r, i)
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR PERKS", fontSize = 12.sp, letterSpacing = 1.sp, color = Gold, fontFamily = Mono, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            ChunkyButton("Hand levels", Color(0xFF6A4DE0), Modifier.width(130.dp), small = true) { vm.levelsOpen = true }
        }
        PerkRow(vm, r, Modifier.widthIn(max = 560.dp))
        if (r.countNotes) Text("Count Notes are active: the running count shows in the next blind.", fontSize = 12.sp, color = Uncommon)
        AmbientLounge(Venues.of(r.ante), Modifier.widthIn(max = 560.dp))
    }
}

@Composable
private fun BlindCard(vm: RunVm, r: Run, i: Int) {
    vm.rev
    val active = i == r.blind
    val done = i < r.blind
    val boss = if (i == 2) r.boss else null
    val name = boss?.name ?: Run.BLIND_NAMES[i]
    val target = r.targetFor(r.ante, i, r.boss)
    val col = if (i == 2) Color(0xFFD64545) else if (i == 1) Color(0xFF3A8DFF) else Color(0xFF2FA866)
    val live = animOn
    val bounce = if (active && live) rememberInfiniteTransition(label = "pick").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "p").value else 0f
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().graphicsLayer { alpha = if (active) 1f else 0.6f; translationY = -bounce * 3.dp.toPx(); scaleX = if (active) 1f else 0.97f; scaleY = scaleX }
            .chunky(Brush.verticalGradient(listOf(Color(0xFF241F3F), Color(0xFF14112A))), edge = if (active) col else EdgeInk, radius = 16.dp, lift = 4.dp, border = if (active) 3.dp else 2.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        Row(Modifier.fillMaxWidth().background(col).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name.uppercase(), fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = Mono, color = Color.White, modifier = Modifier.weight(1f))
            if (done) Text("CLEARED", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
            else Text("REWARD \$${Run.REWARD[i]}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFE08A))
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SCORE AT LEAST", fontSize = 10.sp, letterSpacing = 1.sp, color = Cream.copy(alpha = 0.7f), fontFamily = Mono)
                Text(fmt(target), fontSize = 28.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color(0xFFFF6B5E))
            }
            if (boss != null) Text(boss.desc, fontSize = 13.sp, color = BossRed.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
            if (active) {
                val hands = when { boss != null && boss.onlyHands > 0 -> boss.onlyHands; else -> r.deck.hands + r.perks.sumOf { it.handsBonus } + (if (i == 2) boss!!.handsDelta else 0) + (if (r.ante == 1) 1 else 0) } + r.bonusHands
                Text("$hands ${if (hands == 1) "hand" else "hands"} to get there", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    ChunkyButton("Play blind", Color(0xFF2FA866), Modifier.weight(1f)) { vm.sfx(Fx.DEAL, 1f); vm.mut { startBlind() } }
                    if (i < 2) ChunkyButton("Skip: ${r.skipTag.label}", Color(0xFFB8892B), Modifier.weight(1f)) { vm.sfx(Fx.RG_COIN, 1f); vm.mut { skipBlind() } }
                }
                if (i < 2) Text(r.skipTag.desc, fontSize = 11.sp, color = Cream.copy(alpha = 0.6f))
            }
        }
    }
}

// Playing a blind

@Composable
private fun PlayPane(vm: RunVm, r: Run) {
    vm.rev
    LaunchedEffect(vm.animKey) { if (vm.animating) vm.playScore(r) }
    val dark = Color(0xFF14102A)
    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ScoreHud(vm, r, dark)
        Box(Modifier.weight(1f).fillMaxWidth().shaken(vm.shaker).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { vm.fast = true }) {
            RunTable(vm, r, Modifier.fillMaxSize())
            Burst(vm.sa.slam, listOf(Color(0xFFFFD35A), Color(0xFFFF6B5E), Color(0xFF5ED0FF), Color(0xFFFFFFFF), Color(0xFF7BE495)), originX = 0.5f, originY = 0.72f, count = 46)
        }
        PerkRow(vm, r, Modifier.fillMaxWidth(), popup = vm.sa.popup)
        PlayControls(vm, r)
    }
}

private fun previewType(r: Run): HandType? {
    val g = r.gs
    val h = g.hands.getOrNull(g.activeHand) ?: return null
    val (t, soft) = handTotal(h.cards)
    if (t > 21 || h.cards.isEmpty()) return null
    return HandType.of(HandCtx(if (t == 21 && h.cards.size == 2 && !h.isSplitHand) Outcome.BLACKJACK else Outcome.WIN, h.cards, t, soft, h.doubled, h.isSplitHand, false, r.stake, 0, 0, 0.0))
}

@Composable
private fun NumBox(text: String, color: Color, pulse: Any, modifier: Modifier = Modifier, size: Int = 26) {
    val s = remember { Animatable(1f) }
    LaunchedEffect(pulse) { if (animOn) { s.snapTo(1.25f); s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f)) } }
    Box(modifier.graphicsLayer { scaleX = s.value; scaleY = s.value }.chunky(color, radius = 10.dp, lift = 3.dp).padding(horizontal = 10.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = size.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun ScoreHud(vm: RunVm, r: Run, dark: Color) {
    vm.rev
    val sa = vm.sa
    val shown = if (sa.shown >= 0) sa.shown else r.score
    val frac by androidx.compose.animation.core.animateFloatAsState((shown.toFloat() / r.target).coerceIn(0f, 1f), tween(300), label = "bar")
    val bossCol = if (r.isBoss) Color(0xFFD64545) else if (r.blind == 1) Color(0xFF3A8DFF) else Color(0xFF2FA866)
    Column(
        Modifier.fillMaxWidth().chunky(Brush.verticalGradient(listOf(Color(0xFF241F3F), dark)), radius = 14.dp, lift = 3.dp).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(r.blindName.uppercase(), fontSize = 10.sp, letterSpacing = 1.sp, color = bossCol, fontFamily = Mono, fontWeight = FontWeight.Black, maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fmt(r.target), fontSize = 26.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color(0xFFFF6B5E))
                    Text("to clear", fontSize = 11.sp, color = Cream.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("HANDS", fontSize = 9.sp, letterSpacing = 1.sp, color = Cream.copy(alpha = 0.7f), fontFamily = Mono)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Hourglass(if (r.handsMax > 0) r.handsLeft / r.handsMax.toFloat() else 0f, r.handsLeft <= 1, size = 16.dp)
                Text("${r.handsLeft}", fontSize = 24.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (r.handsLeft <= 1) Color(0xFFFF6B5E) else Color(0xFF5ED0FF))
                }
            }
            ChunkyButton("Lv", Color(0xFF6A4DE0), Modifier.width(48.dp), small = true) { vm.levelsOpen = true }
        }
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFF8A3D), Color(0xFFFFD35A)))))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ROUND SCORE", fontSize = 10.sp, letterSpacing = 1.sp, color = Cream.copy(alpha = 0.7f), fontFamily = Mono, modifier = Modifier.weight(1f))
            val combo = r.comboNow
            if (combo > 0) Text("STREAK ${r.winStreak}  +${fmt1(combo)} mult", fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color(0xFFFF8A3D), modifier = Modifier.padding(end = 8.dp))
            Text(fmt(shown), fontSize = 20.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Gold)
        }
        r.goal?.let { g ->
            Text(
                if (r.goalDone) "BONUS GOAL DONE  +\$${Run.GOAL_PAY}" else "BONUS GOAL: ${g.label}  (+\$${Run.GOAL_PAY})",
                fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (r.goalDone) GoodGreen else Color(0xFFFFD35A), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val prev = if (r.inRound) previewType(r) else null
        val label = if (sa.label.isNotEmpty()) sa.label else if (prev != null) "${prev.label}  Lv.${r.level(prev) + 1}" else ""
        val chips = if (sa.label.isNotEmpty()) sa.chips else if (prev != null) r.baseChips(prev) * r.stake else 0
        val mult = if (sa.label.isNotEmpty()) sa.mult else if (prev != null) r.baseMult(prev) else 0.0
        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (label.isEmpty()) {
                Text(
                    sa.note.ifEmpty { if (r.inRound) "Hit, stand, double or split. Wins score chips x mult." else "Deal a hand. Wins score chips x mult." },
                    fontSize = 13.sp, color = if (sa.note.isNotEmpty()) Color(0xFFFF9A9A) else Cream.copy(alpha = 0.7f), fontWeight = if (sa.note.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f), maxLines = 2,
                )
            } else {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (sa.label.isNotEmpty()) Color.White else Cream.copy(alpha = 0.8f), maxLines = 2, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                NumBox(fmt(chips), ChipBlue, chips)
                Text("x", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Cream)
                NumBox(fmt1(mult), MultRed, mult)
            }
        }
    }
}

@Composable
internal fun Hand(label: String, cards: List<Card>, cw: Dp, maxW: Dp, hideIndex: Int = -1, total: List<Card>? = null, active: Boolean = false, result: ScoreEvent? = null, delayOf: (Int) -> Int = { 0 }) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (label.isNotEmpty()) Text(label, fontSize = 11.sp, letterSpacing = 1.sp, color = if (active) Gold else Cream.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
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
            cards.forEachIndexed { i, c -> key2(c, i) { Pop(Modifier.offset(step * i, 0.dp), delayOf(i)) { PlayingCard(c, cw, faceDown = i == hideIndex) } } }
        }
    }
}

@Composable
private fun key2(c: Card, i: Int, content: @Composable () -> Unit) = androidx.compose.runtime.key(c, i) { content() }

/** A card that is dealt in: it slides down from the shoe side and settles with a small bounce. */
@Composable
internal fun Pop(modifier: Modifier, delayMs: Int = 0, content: @Composable () -> Unit) {
    val p = remember { Animatable(0.5f) }
    val sfx = LocalRunSfx.current
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        if (animOn) sfx(Fx.DEAL, 0.9f + (delayMs % 7) * 0.03f)
        p.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 420f))
    }
    Box(modifier.graphicsLayer {
        val t = ((p.value - 0.5f) / 0.5f)
        scaleX = 0.6f + 0.4f * p.value; scaleY = scaleX
        alpha = t.coerceIn(0f, 1f)
        translationY = -(1f - t.coerceIn(0f, 1f)) * 36.dp.toPx()
        rotationZ = (1f - t.coerceIn(0f, 1f)) * -8f
    }) { content() }
}

// Perk cards

/** The short badge on a perk card, taken from the first number in its description. */
internal fun badgeOf(pk: Perk): Pair<String, Color> {
    val d = pk.desc
    val found = listOf(
        Regex("x[0-9.]+ mult") to XMultGold,
        Regex("\\+[0-9]+ mult") to MultRed,
        Regex("\\+[0-9]+ chips") to ChipBlue,
        Regex("\\+\\\$[0-9]+|\\\$[0-9]+") to Color(0xFF3DDC84),
        Regex("\\+1 hand") to Color(0xFF3DDC84),
    ).mapNotNull { (re, c) -> re.find(d)?.let { Triple(it.range.first, it.value, c) } }.minByOrNull { it.first }
    return if (found != null) found.second to found.third else pk.rarity.label to rarityColor(pk.rarity)
}

private fun popMatches(pk: Perk, p: Popup?) = p != null && (p.perk == pk.title || p.perk.startsWith(pk.name + ":"))

@Composable
internal fun PerkCard(pk: Perk, idx: Int, popup: Popup?, height: Dp, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val live = animOn
    val t = if (live) rememberInfiniteTransition(label = "sway").animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "t").value else 0f
    val ang = sin((t + idx * 0.19f) * 2f * PI.toFloat())
    val bounce = remember { Animatable(1f) }
    val firing = popMatches(pk, popup)
    LaunchedEffect(popup?.seq, firing) { if (firing && live) { bounce.snapTo(1.22f); bounce.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f)) } }
    val col = rarityColor(pk.rarity)
    val (badge, badgeCol) = badgeOf(pk)
    val edge = when (pk.edition) {
        Edition.FOIL -> Color(0xFF7AD7FF)
        Edition.HOLO -> Color(0xFFFF7AE0)
        Edition.POLY -> Color.hsv(((t * 360f) + idx * 40f) % 360f, 0.6f, 1f)
        Edition.NEGATIVE -> Color.White
        else -> col
    }
    val bg = when (pk.edition) {
        Edition.NEGATIVE -> Brush.verticalGradient(listOf(Color(0xFF111118), Color(0xFF05050A)))
        Edition.FOIL -> Brush.verticalGradient(listOf(Color(0xFF1B3550), Color(0xFF101C2C)))
        Edition.HOLO -> Brush.verticalGradient(listOf(Color(0xFF4A1B48), Color(0xFF241028)))
        Edition.POLY -> Brush.verticalGradient(listOf(Color(0xFF2A2656), Color(0xFF1C1238)))
        else -> Brush.verticalGradient(listOf(androidx.compose.ui.graphics.lerp(Color(0xFF16122B), col, 0.22f), Color(0xFF16122B)))
    }
    Box(modifier.height(height).graphicsLayer { rotationZ = ang * 1.7f; translationY = ang * 1.2.dp.toPx(); scaleX = bounce.value; scaleY = bounce.value }) {
        Column(
            Modifier.fillMaxSize().chunky(bg, edge = edge.copy(alpha = 0.95f), radius = 10.dp, lift = 3.dp, border = 2.dp).clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTap).semantics { contentDescription = "${pk.title}, ${pk.rarity.label} perk. ${pk.desc}" }.padding(horizontal = 3.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PerkArt(pk.id, pk.rarity, badgeCol, size = 30.dp)
            Text(pk.name, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
            Text(badge, fontSize = 9.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, modifier = Modifier.background(badgeCol.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp))
        }
        if (pk.edition != Edition.NONE && live) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), start = androidx.compose.ui.geometry.Offset(-200f + t * 700f, 0f), end = androidx.compose.ui.geometry.Offset(-100f + t * 700f, 160f))))
        }
        if (firing && popup != null) PopupText(popup, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun PopupText(p: Popup, modifier: Modifier) {
    val a = remember(p.seq) { Animatable(0f) }
    LaunchedEffect(p.seq) { if (animOn) a.animateTo(1f, tween(520)) else a.snapTo(0.4f) }
    val col = when (p.kind) { 0 -> ChipBlue; 1 -> MultRed; 2 -> XMultGold; else -> Color(0xFF3DDC84) }
    Text(
        p.text.substringBefore(" ("), fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, softWrap = false,
        modifier = modifier.graphicsLayer { translationY = -(8.dp.toPx() + a.value * 22.dp.toPx()); alpha = (1f - a.value * 0.25f) }.chunky(col, radius = 8.dp, lift = 2.dp, border = 1.5.dp).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun PerkRow(vm: RunVm, r: Run, modifier: Modifier, popup: Popup? = null) {
    vm.rev
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until r.slots) {
            val pk = r.perks.getOrNull(i)
            Box(Modifier.weight(1f)) {
                if (pk == null) Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, Cream.copy(alpha = 0.18f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Text("Empty", fontSize = 10.sp, color = Cream.copy(alpha = 0.35f))
                } else PerkCard(pk, i, popup, 86.dp, Modifier.then(if (i in vm.fired) Modifier.graphicsLayer { scaleX = 1.05f; scaleY = 1.05f } else Modifier)) { vm.perkOpen = i }
            }
        }
    }
}

@Composable
private fun PlayControls(vm: RunVm, r: Run) {
    vm.rev
    val busy = vm.animating
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!r.inRound) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                (1..3).forEach { s ->
                    val on = vm.stakePick == s
                    val ok = r.canDeal(s)
                    val fill = if (on) Brush.verticalGradient(listOf(Color(0xFFFFE28A), Color(0xFFE0A93A))) else Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF1A1736)))
                    Column(
                        Modifier.weight(1f).height(44.dp).chunky(fill, radius = 12.dp, lift = 3.dp).clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = ok) { vm.stakePick = s; vm.sfx(Fx.CHIP, 0.8f + s * 0.15f); vm.rev++ }.semantics { contentDescription = "Stake $s. Wins score $s times the chips, a loss costs $s dollars." },
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("STAKE $s", fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (on) Ink else if (ok) Cream else Cream.copy(alpha = 0.4f))
                        Text("x$s chips, -\$$s loss", fontSize = 10.sp, color = if (on) Ink.copy(alpha = 0.8f) else Cream.copy(alpha = if (ok) 0.7f else 0.35f))
                    }
                }
            }
            ChunkyButton("Deal", Color(0xFF2FA866), Modifier.fillMaxWidth(), enabled = r.canDeal(vm.stakePick) && !busy) { vm.deal() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ChunkyButton("Hit", Color(0xFF2E8BFF), Modifier.weight(1f), enabled = r.canAct(Action.HIT)) { vm.act(Action.HIT) }
                ChunkyButton("Stand", Color(0xFFE5483F), Modifier.weight(1f), enabled = r.canAct(Action.STAND)) { vm.act(Action.STAND) }
                ChunkyButton("Double", Color(0xFFC9922A), Modifier.weight(1f), enabled = r.canAct(Action.DOUBLE)) { vm.act(Action.DOUBLE) }
                ChunkyButton("Split", Color(0xFF8A5CE6), Modifier.weight(1f), enabled = r.canAct(Action.SPLIT)) { vm.act(Action.SPLIT) }
            }
            Text(if (r.activeBoss?.noDoubleSplit == true) "The Hook: no doubling or splitting." else "Stake ${r.stake}: wins score x${r.stake} chips.", fontSize = 11.sp, color = Cream.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// Cash out

@Composable
private fun CashoutPane(vm: RunVm, r: Run) {
    vm.rev
    val co = r.cashout ?: return
    val pop = remember { Animatable(0.4f) }
    var rows by remember { mutableIntStateOf(if (animOn) 0 else co.lines.size + 1) }
    LaunchedEffect(Unit) {
        vm.sfx(Fx.RG_FANFARE, 1f)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        if (animOn) { for (i in 0..co.lines.size) { delay(380); rows = i + 1; vm.sfx(Fx.RG_COIN, 1f + 0.07f * i) } }
    }
    Box(Modifier.fillMaxSize()) {
        CoinFlight(vm.sa.slam + 555)
        Burst(vm.sa.slam + 777, listOf(Color(0xFFFFD35A), Color(0xFF3DDC84), Color(0xFF5ED0FF), Color.White), originX = 0.5f, originY = 0.15f, count = 60)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            VenueHeader(r, "Well played. The house pays up.", title = "")
            Text("BLIND CLEARED", fontSize = 30.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
            Text("${fmt(r.score)} of ${fmt(r.target)}", fontSize = 16.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, color = Cream)
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().chunky(Brush.verticalGradient(listOf(Color(0xFF241F3F), Color(0xFF14102A))), radius = 16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                co.lines.forEachIndexed { i, (l, n) ->
                    if (i < rows) Row { Text(l, fontSize = 15.sp, color = Cream, modifier = Modifier.weight(1f)); Text("+\$$n", fontSize = 16.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = GoodGreen) }
                }
                if (rows > co.lines.size) Row { Text("Total", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.weight(1f)); Text("+\$${co.total}", fontSize = 20.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = GoodGreen) }
            }
            Text("You now have \$${r.money}", fontSize = 14.sp, color = Cream.copy(alpha = 0.85f))
            r.milestone?.let { m ->
                Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().chunky(Brush.verticalGradient(listOf(Color(0xFF3A2D10), Color(0xFF1E1606))), edge = Color(0xFFFFD35A), radius = 16.dp, lift = 4.dp, border = 3.dp).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ANTE ${r.ante} COMPLETE", fontSize = 13.sp, letterSpacing = 2.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color(0xFFFFE08A))
                    Text("BOSS PRIZE: ${m.label.uppercase()}", fontSize = 18.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
                    Text(m.desc, fontSize = 13.sp, color = Cream.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                }
            }
            ChunkyButton(if (r.isBoss && r.ante >= Run.MAX_ANTE) "Finish the Rogue Run" else "To the shop", Color(0xFF2FA866), Modifier.widthIn(max = 460.dp).fillMaxWidth()) { vm.sfx(Fx.RG_COIN, 1f); vm.mut { collect() } }
        }
    }
}

// Shop

@Composable
private fun ShopPane(vm: RunVm, r: Run) {
    vm.rev
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        VenueHeader(r, "Spend it wisely. Train a hand, buy a perk, or roll for a shiny one.", title = "THE SHOP")
        val perkOffers = r.offers.withIndex().filter { it.value.perk != null }
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            perkOffers.forEach { (i, o) -> ShopCard(vm, r, i, o, Modifier.weight(1f)) }
            repeat(3 - perkOffers.size) { Spacer(Modifier.weight(1f)) }
        }
        r.offers.withIndex().filter { it.value.perk == null }.forEach { (i, o) -> ShopStrip(vm, r, i, o) }
        if (r.perks.size >= r.slots) Text("Your perk slots are full. Sell one to buy another.", fontSize = 12.sp, color = BadRed)
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChunkyButton("Reroll \$${r.rerollCost}", Color(0xFF2E8BFF), Modifier.weight(1f), enabled = r.money >= r.rerollCost) { vm.sfx(Fx.RG_TICK, 1.2f); vm.mut { reroll() } }
            ChunkyButton("Next blind", Color(0xFFE5483F), Modifier.weight(1f)) { vm.sfx(Fx.DEAL, 1f); vm.mut { leaveShop() } }
        }
        Row(Modifier.widthIn(max = 560.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR PERKS (tap to sell)", fontSize = 12.sp, letterSpacing = 1.sp, color = Gold, fontFamily = Mono, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            ChunkyButton("Hand levels", Color(0xFF6A4DE0), Modifier.width(130.dp), small = true) { vm.levelsOpen = true }
        }
        PerkRow(vm, r, Modifier.widthIn(max = 560.dp))
        AmbientLounge(Venues.of(r.ante), Modifier.widthIn(max = 560.dp))
    }
}

@Composable
private fun ShopCard(vm: RunVm, r: Run, i: Int, o: com.hilotrainer.engine.Offer, modifier: Modifier) {
    val pk = o.perk!!
    val col = rarityColor(pk.rarity)
    val edge = when (pk.edition) { Edition.FOIL -> Color(0xFF7AD7FF); Edition.HOLO -> Color(0xFFFF7AE0); Edition.POLY -> Color(0xFFFFE08A); Edition.NEGATIVE -> Color.White; else -> col }
    val (badge, badgeCol) = badgeOf(pk)
    val ok = r.canBuy(i)
    Column(
        modifier.graphicsLayer { alpha = if (o.sold) 0.4f else 1f }.chunky(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = edge, radius = 12.dp, lift = 4.dp, border = 2.5.dp).clip(RoundedCornerShape(12.dp)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (pk.edition == Edition.NONE) pk.rarity.label.uppercase() else "${pk.edition.label.uppercase()} EDITION", fontSize = 8.sp, letterSpacing = 1.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = edge)
        PerkArt(pk.id, pk.rarity, badgeCol, size = 40.dp)
        Text(pk.name, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.heightIn(min = 30.dp))
        Text(badge, fontSize = 10.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.background(badgeCol.copy(alpha = 0.85f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp))
        Text(if (pk.edition == Edition.NONE) pk.desc else "${pk.desc}  ${pk.edition.desc}.", fontSize = 10.sp, lineHeight = 12.sp, color = Cream.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 7, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).heightIn(min = 70.dp))
        ChunkyButton(if (o.sold) "SOLD" else "\$${o.cost}", Color(0xFF2FA866), Modifier.fillMaxWidth(), enabled = ok, small = true) { vm.sfx(Fx.RG_BUY, 1f); vm.mut { buy(i) } }
    }
}

@Composable
private fun ShopStrip(vm: RunVm, r: Run, i: Int, o: com.hilotrainer.engine.Offer) {
    val ok = r.canBuy(i)
    val train = o.train
    val edge = if (train != null) Color(0xFF9B7BFF) else Color(0xFF5ED0FF)
    Row(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().graphicsLayer { alpha = if (o.sold) 0.4f else 1f }.chunky(Brush.horizontalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = edge, radius = 12.dp, lift = 3.dp).clip(RoundedCornerShape(12.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (train != null) "HAND TRAINING" else "TONIC", fontSize = 8.sp, letterSpacing = 1.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = edge)
            Text(o.name, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
            Text(o.desc, fontSize = 11.sp, color = Cream.copy(alpha = 0.85f), lineHeight = 13.sp)
        }
        ChunkyButton(if (o.sold) "SOLD" else "\$${o.cost}", Color(0xFF2FA866), Modifier.width(76.dp), enabled = ok, small = true) { vm.sfx(if (o.pack) Fx.RG_CHIME else Fx.RG_BUY, 1f); vm.mut { buy(i) } }
    }
}

@Composable
private fun PerkSheet(vm: RunVm, r: Run, i: Int, pk: Perk) {
    vm.rev
    val col = rarityColor(pk.rarity)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable { vm.perkOpen = null }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 380.dp).padding(20.dp).chunky(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = col, radius = 20.dp, lift = 5.dp, border = 3.dp).clickable(enabled = false) {}.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(pk.title, fontSize = 22.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = col, textAlign = TextAlign.Center)
            Text(pk.rarity.label + if (pk.edition != Edition.NONE) "  ·  ${pk.edition.desc}" else "", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            TypeText(pk.desc, fontSize = 15)
            if (pk.id == "snow") Text("Now worth +${2 + pk.grow} mult.", fontSize = 13.sp, color = MultRed, fontWeight = FontWeight.Bold)
            val canSell = (r.phase == RunPhase.SHOP || r.phase == RunPhase.SELECT) && !(pk.edition == Edition.NEGATIVE && r.perks.size > r.slots - 1)
            if (!r.inRound && r.perks.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ChunkyButton("Move left", Color(0xFF3A5AA0), Modifier.weight(1f), enabled = i > 0, small = true) { vm.sfx(Fx.CHIP, 1.2f); vm.mut { movePerk(i, i - 1) }; vm.perkOpen = i - 1 }
                ChunkyButton("Move right", Color(0xFF3A5AA0), Modifier.weight(1f), enabled = i < r.perks.size - 1, small = true) { vm.sfx(Fx.CHIP, 1.2f); vm.mut { movePerk(i, i + 1) }; vm.perkOpen = i + 1 }
            }
            if (canSell) ChunkyButton("Sell for \$${pk.sell}", Color(0xFFC9922A), Modifier.fillMaxWidth()) { vm.sfx(Fx.RG_COIN, 1f); vm.mut { sell(i) }; vm.perkOpen = null }
            ChunkyButton("Close", Color(0xFF3A3A5A), Modifier.fillMaxWidth()) { vm.perkOpen = null }
        }
    }
}

/** Every kind of win, how far it is trained and what it scores at that level. */
@Composable
private fun LevelsSheet(vm: RunVm, r: Run) {
    vm.rev
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable { vm.levelsOpen = false }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).padding(16.dp).chunky(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = Color(0xFF9B7BFF), radius = 20.dp, lift = 5.dp, border = 3.dp).clickable(enabled = false) {}.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("HAND LEVELS", fontSize = 18.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Gold)
            Text("A win counts as the rarest kind it qualifies for. Train a kind in the shop to raise its chips and mult.", fontSize = 12.sp, color = Cream.copy(alpha = 0.8f))
            HandType.entries.reversed().forEach { t ->
                val lv = r.level(t)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text(t.how, fontSize = 10.sp, color = Cream.copy(alpha = 0.65f))
                    }
                    Text("Lv.${lv + 1}", fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (lv > 0) Gold else Cream.copy(alpha = 0.6f))
                    Text("${r.baseChips(t)}", fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.chunky(ChipBlue, radius = 6.dp, lift = 2.dp, border = 1.5.dp).padding(horizontal = 6.dp, vertical = 1.dp))
                    Text("x", fontSize = 12.sp, color = Cream)
                    Text(fmt1(r.baseMult(t)), fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.chunky(MultRed, radius = 6.dp, lift = 2.dp, border = 1.5.dp).padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
            ChunkyButton("Close", Color(0xFF3A3A5A), Modifier.fillMaxWidth().padding(top = 4.dp)) { vm.levelsOpen = false }
        }
    }
}

// End

@Composable
private fun EndPane(vm: RunVm, r: Run, onExit: () -> Unit) {
    vm.rev
    val won = r.phase == RunPhase.WON
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { vm.sfx(if (won) Fx.RG_FANFARE else Fx.RG_BUST, 1f); pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    Box(Modifier.fillMaxSize()) {
        if (won) Burst(4242, listOf(Color(0xFFFFD35A), Color(0xFFFF6B5E), Color(0xFF5ED0FF), Color.White), originX = 0.5f, originY = 0.2f, count = 70)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (won) "YOU BEAT THE HOUSE" else "RUN OVER", fontSize = 30.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (won) Gold else BadRed, textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
            if (!won) Text(r.cause, fontSize = 14.sp, color = Cream.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().chunky(Brush.verticalGradient(listOf(Color(0xFF241F3F), Color(0xFF14102A))), radius = 16.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                fun line(a: String, b: String) = @Composable { Row { Text(a, fontSize = 14.sp, color = Cream.copy(alpha = 0.8f), modifier = Modifier.weight(1f)); Text(b, fontSize = 14.sp, fontFamily = Mono, fontWeight = FontWeight.Bold, color = Cream) } }
                line("Deck", r.deck.label)()
                line("Ante reached", "${if (won) Run.MAX_ANTE else r.ante} of ${Run.MAX_ANTE}")()
                line("Blinds cleared", "${r.blindsCleared}")()
                line("Best hand", fmt(r.bestHand))()
                line("Blinds skipped", "${r.skips}")()
                val best = HandType.entries.maxByOrNull { r.level(it) }
                if (best != null && r.level(best) > 0) line("Best trained hand", "${best.label} Lv.${r.level(best) + 1}")()
            }
            r.bestType?.let { bt ->
                Column(Modifier.widthIn(max = 460.dp).fillMaxWidth().chunky(Brush.verticalGradient(listOf(Color(0xFF2A2350), Color(0xFF14102A))), radius = 16.dp).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("BEST HAND OF THE RUN", fontSize = 11.sp, letterSpacing = 1.sp, color = Gold, fontFamily = Mono, fontWeight = FontWeight.Black)
                    Hand("", r.bestCards, 38.dp, 240.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(bt.label, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White)
                        NumBox(fmt(r.bestChips), ChipBlue, r.bestChips, size = 16)
                        Text("x", fontSize = 14.sp, color = Cream)
                        NumBox(fmt1(r.bestMult), MultRed, r.bestMult, size = 16)
                    }
                    Text("= ${fmt(r.bestHand)}", fontSize = 18.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Gold)
                }
            }
            vm.lastReward?.let { RewardPanel(it, Modifier.widthIn(max = 460.dp)) }
            Text("YOUR PERKS", fontSize = 12.sp, letterSpacing = 1.sp, color = Gold, fontFamily = Mono, fontWeight = FontWeight.Black)
            Text(if (r.perks.isEmpty()) "None" else r.perks.joinToString(", ") { it.title }, fontSize = 13.sp, color = Cream.copy(alpha = 0.85f), textAlign = TextAlign.Center)
            ChunkyButton("New Rogue Run", Color(0xFF2FA866), Modifier.widthIn(max = 460.dp).fillMaxWidth()) { vm.leaveRun() }
            ChunkyButton("Back to the table", Color(0xFF3A3A5A), Modifier.widthIn(max = 460.dp).fillMaxWidth()) { onExit() }
        }
    }
}


/** Booster pack: three perks face up, pick one or skip. */
@Composable
private fun PackSheet(vm: RunVm, r: Run) {
    vm.rev
    val pack = r.pack ?: return
    val flip = remember { Animatable(if (animOn) 0f else 1f) }
    LaunchedEffect(Unit) { if (animOn) { vm.sfx(Fx.RG_CHIME, 0.9f); flip.animateTo(1f, tween(700)) } }
    Box(Modifier.fillMaxSize().background(Color(0xF2080612)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (r.phase == RunPhase.SHOP) "PERK PACK" else "PICK A PERK", fontSize = 26.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Gold)
            Text("Pick 1 of 3", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pack.forEachIndexed { j, pk ->
                    val col = rarityColor(pk.rarity)
                    val edge = when (pk.edition) { Edition.FOIL -> Color(0xFF7AD7FF); Edition.HOLO -> Color(0xFFFF7AE0); Edition.POLY -> Color(0xFFFFE08A); Edition.NEGATIVE -> Color.White; else -> col }
                    val ok = r.canPick(j)
                    val f = ((flip.value * 3f) - j * 0.7f).coerceIn(0f, 1f)
                    Column(
                        Modifier.weight(1f).graphicsLayer { rotationY = (1f - f) * 90f; cameraDistance = 12f * density; alpha = f }
                            .chunky(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = edge, radius = 12.dp, lift = 4.dp, border = 2.5.dp).clip(RoundedCornerShape(12.dp)).padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(if (pk.edition == Edition.NONE) pk.rarity.label.uppercase() else "${pk.edition.label.uppercase()} EDITION", fontSize = 8.sp, letterSpacing = 1.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = edge)
                        PerkArt(pk.id, pk.rarity, badgeOf(pk).second, size = 46.dp)
                        Text(pk.name, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, lineHeight = 14.sp, maxLines = 2, modifier = Modifier.heightIn(min = 30.dp))
                        Text(if (pk.edition == Edition.NONE) pk.desc else "${pk.desc}  ${pk.edition.desc}.", fontSize = 10.sp, lineHeight = 12.sp, color = Cream.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 90.dp))
                        ChunkyButton("Take", Color(0xFF2FA866), Modifier.fillMaxWidth(), enabled = ok, small = true) { vm.sfx(Fx.RG_BUY, 1f); vm.mut { pickFromPack(j) } }
                    }
                }
            }
            if (pack.none { r.perks.size < r.slots || it.edition == Edition.NEGATIVE }) Text("Your perk slots are full. Skip, or sell a perk first.", fontSize = 12.sp, color = BadRed)
            ChunkyButton("Skip pack", Color(0xFF3A3A5A), Modifier.fillMaxWidth()) { vm.mut { skipPack() } }
        }
    }
}

/** The last 20 finished runs with animated bars for how far each got. */
@Composable
private fun HistorySheet(vm: RunVm) {
    val rows = vm.store.runHistory.split(';').filter { it.isNotBlank() }.mapNotNull { l -> l.split(',').let { p -> if (p.size >= 5) listOf(p[0].toIntOrNull() ?: 0, p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0) else null } }.reversed()
    val grow = remember { Animatable(if (animOn) 0f else 1f) }
    LaunchedEffect(Unit) { if (animOn) grow.animateTo(1f, tween(900)) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)).clickable { vm.historyOpen = false }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).padding(16.dp).chunky(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = Color(0xFF9B7BFF), radius = 20.dp, lift = 5.dp, border = 3.dp).clickable(enabled = false) {}.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("RUN HISTORY", fontSize = 18.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Gold)
            if (rows.isEmpty()) Text("No finished runs yet. Your last 20 will show here.", fontSize = 13.sp, color = Cream.copy(alpha = 0.8f))
            val top = rows.maxOfOrNull { it[2] }?.coerceAtLeast(1) ?: 1
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rows.forEach { p ->
                    val won = p[3] == 1
                    Column {
                        Row {
                            Text(if (won) "WIN" else "Ante ${p[0]}", fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (won) Gold else Cream, modifier = Modifier.weight(1f))
                            Text("${p[1]} blinds  ·  best ${fmt(p[2])}", fontSize = 11.sp, color = Cream.copy(alpha = 0.75f))
                        }
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                            Box(Modifier.fillMaxWidth((p[2] / top.toFloat() * grow.value).coerceIn(0.02f, 1f)).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFF8A3D), Color(0xFFFFD35A)))))
                        }
                    }
                }
            }
            ChunkyButton("Close", Color(0xFF3A3A5A), Modifier.fillMaxWidth().padding(top = 4.dp)) { vm.historyOpen = false }
        }
    }
}
