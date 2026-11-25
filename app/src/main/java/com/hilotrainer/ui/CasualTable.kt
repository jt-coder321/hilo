package com.hilotrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.CasualDealers
import com.hilotrainer.engine.Extra
import com.hilotrainer.engine.Hair
import com.hilotrainer.engine.Hand
import com.hilotrainer.engine.Look
import com.hilotrainer.engine.Phase
import com.hilotrainer.engine.Seat
import com.hilotrainer.engine.handTotal
import kotlin.math.abs

/** The pit boss: dark suit, gray temples, earpiece. */
val PitLook = Look(0xFFDDB08C, 0xFF616161, Hair.SHORT, 0xFF1B1B1F, 0xFF8E1B1B, extras = listOf(Extra.MUSTACHE, Extra.BOW_TIE))

// Chips for Just Play.
private val CasualChips = listOf(
    ChipStyle(5, Color(0xFFC62828), Color.White, Color.White),
    ChipStyle(10, Color(0xFF1E63C6), Color.White, Color.White),
    ChipStyle(25, Color(0xFF2E7D32), Color.White, Color.White),
    ChipStyle(100, Color(0xFF1C1C1C), Gold, Gold),
    ChipStyle(500, Color(0xFF6A2C91), Color(0xFFF3D36B), Color.White),
    ChipStyle(1000, Color(0xFFF9A825), Color(0xFF5D4037), Color(0xFF3E2723)),
    ChipStyle(5000, Color(0xFFE65100), Color.White, Color.White),
)

fun chipButtons(min: Int): List<Int> = when {
    min >= 500 -> listOf(500, 1000, 5000)
    min >= 100 -> listOf(100, 500, 1000, 5000)
    min >= 25 -> listOf(25, 100, 500, 1000)
    min >= 10 -> listOf(10, 25, 100, 500)
    else -> listOf(5, 25, 100, 500)
}

private fun styleOf(v: Int) = CasualChips.firstOrNull { it.value == v } ?: CasualChips.lastOrNull { it.value <= v } ?: CasualChips[0]

/** A stack of Just Play chips for [amount], biggest at the bottom. */
@Composable
fun CasualChipStack(amount: Int, size: Dp, modifier: Modifier = Modifier) {
    val chips = remember(amount) {
        val out = mutableListOf<ChipStyle>()
        var left = amount
        for (s in CasualChips.reversed()) while (left >= s.value && out.size < 9) { out += s; left -= s.value }
        if (out.isEmpty() && amount > 0) out += CasualChips[0]
        out
    }
    Box(modifier.size(size, size + size * 0.1f * (chips.size - 1).coerceAtLeast(0)), contentAlignment = Alignment.BottomCenter) {
        chips.forEachIndexed { i, s ->
            // Real stacks are never perfectly square: each chip sits a hair off the one below.
            val jx = ((i * 37 + amount / 5) % 5 - 2) * 0.012f
            CasinoChip(s, size, Modifier.offset(x = size * jx, y = -(size * 0.1f) * i), label = i == chips.size - 1)
        }
    }
}

/** Felt for the chosen theme, with a spotlight and dark edges. */
fun Modifier.themedFelt(t: FeltTheme): Modifier = drawBehind {
    drawRect(t.base)
    drawRect(Brush.radialGradient(listOf(t.light.copy(alpha = 0.9f), t.base.copy(alpha = 0f)), center = Offset(size.width / 2, size.height * 0.45f), radius = maxOf(size.width, size.height) * 0.7f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) * 0.85f))
    // Felt fibers.
    val r = kotlin.random.Random(7)
    repeat(160) { drawCircle(Color.White.copy(alpha = 0.025f), 1.2f, Offset(r.nextFloat() * size.width, r.nextFloat() * size.height)) }
}

private fun money0(n: Int) = if (n < 0) "-" + money(-n) else money(n)

@Composable
fun JustPlayScreen(vm: CasualViewModel, onBack: () -> Unit) {
    var menu by remember { mutableStateOf<Int?>(null) }
    var wheelOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = menu != null) { menu = null }
    LaunchedEffect(vm) { vm.chatterLoop() }
    val s = vm.settings
    CompositionLocalProvider(LocalCardStyle provides CardStyle(back = s.cardBack)) {
        Column(Modifier.fillMaxSize().background(Ink)) {
            TopBar(vm, onBack) { menu = it }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().themedFelt(s.theme)) {
                TableArea(vm, maxWidth, maxHeight) { wheelOpen = true }
            }
            Controls(vm)
        }
        if (menu != null) CasualMenu(vm, menu!!, onTab = { menu = it }) { menu = null }
        if (wheelOpen && (vm.wheelReady || vm.wheelIdx != null)) WheelDialog(vm) { wheelOpen = false }
        LuckyDialog(vm)
        Toasts(vm)
        PerkToasts(vm)
    }
}

@Composable
private fun TopBar(vm: CasualViewModel, onBack: () -> Unit, openMenu: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(WoodTop, WoodBottom))).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold) }
        Column(Modifier.weight(1f)) {
            Text("JUST PLAY", fontSize = 11.sp, letterSpacing = 2.sp, color = Gold)
            val shown by animateFloatAsState(vm.bankroll.toFloat(), tween(700), label = "bank")
            Text(money(shown.toInt()), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Cream, modifier = Modifier.semantics { contentDescription = "Bankroll ${money(vm.bankroll)}" })
        }
        val bonus = vm.bonusAvailable()
        if (bonus != null && vm.canBet) {
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFF6DA8C), Gold))).clickable { vm.claimBonus() }.padding(horizontal = 12.dp, vertical = 8.dp),
            ) { Text("Daily bonus +${money(bonus.first)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink) }
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = { openMenu(0) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Menu, contentDescription = "Table menu", tint = Gold) }
    }
}

/**
 * The four other seats sit on a curved row that bends down toward you. Nobody sits between you and the
 * dealer: you take the lowest point of the curve at the bottom center, with the others beside you.
 */
private val SeatX = listOf(0.1f, 0.265f, 0.735f, 0.9f)
private const val SEATS = 4

@Composable
private fun TableArea(vm: CasualViewModel, w: Dp, h: Dp, onWheel: () -> Unit) {
    val gs = vm.gs
    val scale = minOf(w.value / 411f, h.value / 620f).coerceIn(0.78f, 1.9f)
    val dz = h * 0.19f
    val dealerSize = (dz * 0.95f).coerceAtMost(150.dp)
    val dCard = (44 * scale).dp
    val dCardsY = dz + 6.dp
    val seatW = w / 4.6f
    val sCard = minOf((34 * scale).dp, seatW * 0.5f)
    val avatar = minOf((58 * scale).dp, seatW * 0.85f)
    val youCard = (60 * scale).dp
    val youTop = h - youCard * 1.4f - 44.dp
    // The curve: its lowest point is just under your cards, and the seats follow it up and out.
    val arcLow = youTop + youCard * 1.4f + 30.dp
    val arcEnd = arcLow - h * 0.17f
    val seatBlock = sCard * 1.4f + 22.dp + avatar + 40.dp
    val seatXdp = { i: Int -> w * SeatX[i] }
    val arcY = { xf: Float -> arcEnd + (arcLow - arcEnd) * (1f - (2f * xf - 1f) * (2f * xf - 1f)) }
    val seatYdp = { i: Int -> arcY(SeatX[i]) - seatBlock - 12.dp }
    // The shoe sits right beside the dealer, so the dealing arm stays short.
    val shoe = DpOffset(w / 2 + dealerSize * 0.5f + 32.dp, dz * 0.68f)
    val tray = DpOffset(w / 2 - dealerSize * 0.5f - 46.dp, dz * 0.68f)
    val pitchMs = when (vm.dealer.pitch) { 0 -> 230; 1 -> 330; else -> 460 }
    val sweep by animateFloatAsState(if (vm.sweeping) 1f else 0f, tween(if (vm.sweeping) 420 else 0, easing = FastOutSlowInEasing), label = "sweep")

    // Printing on the felt.
    val theme = vm.settings.theme
    Column(Modifier.fillMaxWidth().offset(y = dz + dCard * 1.4f + 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (vm.settings.sixToFive) "BLACKJACK PAYS 6 TO 5" else "BLACKJACK PAYS 3 TO 2", fontSize = (13 * scale).sp, fontFamily = TitleFont, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = theme.print.copy(alpha = 0.55f))
        Text(
            (if (vm.settings.dealerHitsSoft17) "Dealer hits soft 17" else "Dealer must stand on all 17s") + "  ·  Insurance pays 2 to 1",
            fontSize = (10 * scale).sp, color = theme.print.copy(alpha = 0.45f), letterSpacing = 1.sp,
        )
        Text("${com.hilotrainer.engine.Tiers.forMin(vm.settings.tableMin).name.uppercase()}  ·  ${money(vm.settings.tableMin)} to ${money(vm.maxBet)}", fontSize = (10 * scale).sp, color = theme.print.copy(alpha = 0.4f))
    }
    Canvas(Modifier.fillMaxSize()) {
        // The curve the seats follow, drawn through the foot of every seat and under your cards.
        val e = arcEnd.toPx() + 6.dp.toPx(); val m = arcLow.toPx() + 2.dp.toPx()
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, e); quadraticTo(size.width / 2, 2 * m - e, size.width, e)
        }
        drawPath(path, theme.print.copy(alpha = 0.28f), style = Stroke(2.dp.toPx()))
    }

    // Dealer's chip rack.
    Row(Modifier.offset(8.dp, dz * 0.9f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(5, 25, 100, 500, 1000).forEach { v -> CasualChipStack(v, 16.dp) }
    }
    DiscardTray(tray, gs.cardsDealt, gs.totalCards)
    // Pit boss walks in behind the dealer and stands near the player he's watching.
    PitBoss(vm, w, dz, seatXdp)
    // Dealer and the hand that deals.
    DealerZone(vm, w, dz, dealerSize, seatXdp, shoe)

    // Dealer's cards.
    val dealerCards = gs.dealerCards.zip(gs.dealerSeqs).filter { it.second < vm.shown }
    if (dealerCards.isNotEmpty()) {
        val step = dCard * 0.62f
        val total = dCard + step * (dealerCards.size - 1)
        val x0 = (w - total) / 2
        val dens = LocalDensity.current.density
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = (tray.x.value - (x0 + total / 2).value) * dens * sweep; translationY = (tray.y.value - dCardsY.value) * dens * sweep
            alpha = 1f - sweep; scaleX = 1f - 0.5f * sweep; scaleY = 1f - 0.5f * sweep
        }) {
        dealerCards.forEachIndexed { i, (c, seq) ->
            key(seq) {
                val faceUp = i != 1 || (gs.holeRevealed && gs.holeRevealSeq < vm.shown)
                val x = x0 + step * i
                TableCard(c, dCard, faceUp, DpOffset(shoe.x - x, shoe.y - dCardsY), animate = !vm.instant, durationMs = pitchMs, modifier = Modifier.offset(x, dCardsY), restTilt = ((seq * 29) % 5 - 2) * 0.8f, lift = true)
            }
        }
        val visible = dealerCards.mapIndexedNotNull { i, (c, _) -> if (i != 1 || (gs.holeRevealed && gs.holeRevealSeq < vm.shown)) c else null }
        TotalPill(visible, Modifier.offset(x0 + total + 6.dp, dCardsY + dCard * 0.4f))
        }
    }
    if (vm.shuffling) ShuffleShow(vm.dealer.shuffle, Modifier.offset(w / 2 - 70.dp, dz + 8.dp))
    if (vm.proc != 0) ShoeProc(vm.proc, shoe, tray, w / 2, youTop)

    // Other players.
    for (i in 0 until SEATS) {
        val seat = vm.seatView[i]
        val x = seatXdp(i); val y = seatYdp(i)
        Box(Modifier.offset(x - seatW / 2, y).width(seatW)) {
            // Empty seat marker.
            if (seat == null && vm.departing.none { it.seat == i }) {
                Box(Modifier.align(Alignment.TopCenter).offset(y = sCard * 1.5f).size(avatar * 0.7f).border(1.5.dp, theme.print.copy(alpha = 0.22f), CircleShape))
            }
        }
        if (seat != null) SeatBlock(vm, i, seat, x, y, seatW, sCard, avatar, shoe, pitchMs, sweep, tray)
    }
    for (d in vm.departing) key(d.key) { Walker(d.s, seatXdp(d.seat), seatYdp(d.seat) + sCard * 1.4f + 10.dp, avatar, toRight = d.seat >= 2, away = true, phase = d.seat * 0.2f) }

    // You.
    YourSpot(vm, w, youTop, youCard, shoe, pitchMs, scale, sweep, tray)
    PayoutFlights(vm, DpOffset(52.dp, dz * 0.9f + 8.dp)) { seat ->
        if (seat >= CasualViewModel.YOU) DpOffset(w / 2 + 54.dp, youTop + youCard * 1.4f + 16.dp)
        else DpOffset(seatXdp(seat) - 10.dp, seatYdp(seat) + sCard * 1.4f + 11.dp)
    }

    ChatLayer(vm, w, dz, dCard, dealerSize, seatXdp, seatYdp, youTop)
    FunBadges(vm, onWheel, Modifier.offset(6.dp, 4.dp))
    CountChip(vm, Modifier.offset(w - 100.dp, 4.dp).width(94.dp))
    // How your round went, over the felt printing.
    vm.banner?.let { b -> key(b.key) { ResultBanner(b, Modifier.width(w).offset(y = dCardsY + dCard * 1.4f + 6.dp)) } }
}


/** Table talk, drawn in the style the player picked. Every style keeps every line readable and avoids overlap. */
@Composable
private fun ChatLayer(vm: CasualViewModel, w: Dp, dz: Dp, dCard: Dp, dealerSize: Dp, seatX: (Int) -> Dp, seatY: (Int) -> Dp, youTop: Dp) {
    val feedTop = dz + dCard * 1.4f + 14.dp + 50.dp
    val fh = (seatY(0) - 6.dp - feedTop).coerceIn(60.dp, 132.dp)
    when (vm.settings.chatStyle) {
        1 -> { BubbleLayer(vm, w, dz, dCard, seatX, seatY, floating = false); BubbleDare(vm, w, feedTop + fh - 46.dp) }
        4 -> { BubbleLayer(vm, w, dz, dCard, seatX, seatY, floating = true); BubbleDare(vm, w, feedTop + fh - 46.dp) }
        2 -> Captions(vm, Modifier.offset(w * 0.04f, feedTop).width(w * 0.92f).height(fh))
        3 -> TableLog(vm, Modifier.offset(w * 0.04f, feedTop).width(w * 0.92f).height(fh))
        else -> ChatFeed(vm, Modifier.offset(w * 0.04f, feedTop).width(w * 0.92f).height(fh))
    }
}

/** The dare offer, for chat styles that have no lane of their own. */
@Composable
private fun BubbleDare(vm: CasualViewModel, w: Dp, top: Dp) {
    if (vm.dare != null && !vm.dareOn && vm.canBet) DareRow(vm, Modifier.offset(w * 0.04f, top).width(w * 0.92f))
}

private data class Placed(val l: ChatLine, val x: Float, val y: Float, val w: Float, val h: Float, val tailX: Float?, val minor: Boolean)

/** One bubble per speaker, anchored to them. Bubbles that would touch are moved up (or down) until they don't. */
private fun layoutBubbles(lines: List<ChatLine>, w: Float, dz: Float, dealerBottom: Float, seatX: (Int) -> Float, seatY: (Int) -> Float, seatW: Float, occupied: List<Int>, floating: Boolean): List<Placed> {
    val latest = lines.groupBy { it.who }.map { it.value.last() }
    class Anchor(val cx: Float, val y: Float, val down: Boolean)
    fun anchor(l: ChatLine): Anchor = when {
        l.who == CasualViewModel.DEALER -> Anchor(w / 2 + 30f, dealerBottom, true)
        l.who == CasualViewModel.PIT -> Anchor(w - 90f, dz * 0.68f + 28f, true)
        else -> Anchor(seatX(l.who), seatY(l.who) - 2f, false)
    }
    val out = ArrayList<Placed>()
    for (l in latest.sortedByDescending { Math.abs(anchor(it).cx - w / 2) }) {
        val a = anchor(l)
        val minor = floating && l.minor
        val cw = if (minor) 6.0f else 6.4f
        val bw = (l.text.length * cw + 26f).coerceIn(74f, if (minor) 150f else 168f).coerceAtMost(w - 8f)
        val lines2 = Math.ceil((l.text.length * cw / (bw - 20f)).toDouble()).toInt().coerceIn(1, 3)
        val bh = 15f * lines2 + (if (minor) 6f else 12f) + (if (a.down) 0f else 6f)
        val x0 = (a.cx - bw / 2).coerceIn(4f, w - bw - 4f)
        var x = x0; var y = 0f
        search@ for (level in 0..6) {
            for (dx in listOf(0f, -28f, 28f, -56f, 56f, -84f, 84f, -112f, 112f, -140f, 140f)) {
                val cx0 = (x0 + dx).coerceIn(4f, w - bw - 4f)
                val cy0 = if (a.down) a.y + level * (bh + 4f) else a.y - bh - level * (bh + 4f)
                val clash = out.any { o -> cx0 < o.x + o.w + 4f && o.x < cx0 + bw + 4f && cy0 < o.y + o.h + 3f && o.y < cy0 + bh + 3f } ||
                    occupied.any { i -> i != l.who && cx0 < seatX(i) + seatW * 0.47f && seatX(i) - seatW * 0.47f < cx0 + bw && cy0 < seatY(i) + 52f && seatY(i) < cy0 + bh }
                if (!clash || level == 6) { x = cx0; y = cy0; break@search }
            }
        }
        out += Placed(l, x, y, bw, bh, if (a.down) null else (a.cx - x).coerceIn(14f, bw - 14f), minor)
    }
    return out
}

@Composable
private fun BubbleLayer(vm: CasualViewModel, w: Dp, dz: Dp, dCard: Dp, seatX: (Int) -> Dp, seatY: (Int) -> Dp, floating: Boolean) {
    val occupied = (0 until SEATS).filter { vm.seatView[it] != null }
    val placed = layoutBubbles(vm.chat.toList(), w.value, dz.value, (dz + dCard * 1.4f + 8.dp).value, { seatX(it).value }, { seatY(it).value }, (w / 4.6f).value, occupied, floating)
    placed.forEach { p -> key(p.l.key) { AnchoredBubble(p, Modifier.offset(p.x.dp, p.y.dp).width(p.w.dp)) } }
}

@Composable
private fun AnchoredBubble(p: Placed, modifier: Modifier) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f)) }
    val col = speakerColor(p.l.who)
    if (p.minor) {
        // Small talk: plain words floating up from them, with a little icon.
        Row(modifier.graphicsLayer { alpha = a.value.coerceIn(0f, 1f) * 0.92f; translationY = (1f - a.value) * 10.dp.toPx() }, verticalAlignment = Alignment.CenterVertically) {
            ReactionIcon(p.l.key.toInt(), col, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(p.l.text, fontSize = 12.sp, lineHeight = 15.sp, color = Cream, fontWeight = FontWeight.Medium, style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 2f), 6f)))
        }
        return
    }
    val dark = p.l.who == CasualViewModel.PIT
    Column(modifier.graphicsLayer { scaleX = 0.7f + 0.3f * a.value; scaleY = scaleX; alpha = a.value.coerceIn(0f, 1f); transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }) {
        Text(
            p.l.text, fontSize = 12.sp, lineHeight = 15.sp, color = if (dark) Cream else Ink, fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (dark) Color(0xF01B1B1F) else Color(0xF7FFFDF6))
                .border(1.dp, if (dark) col.copy(alpha = 0.7f) else Color(0x33000000), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
        )
        if (p.tailX != null) Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val tx = p.tailX.dp.toPx()
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(tx - 5.dp.toPx(), 0f); lineTo(tx + 5.dp.toPx(), 0f); lineTo(tx, size.height); close() }
            drawPath(path, if (dark) Color(0xF01B1B1F) else Color(0xF7FFFDF6))
        }
    }
}

/** A tiny hand drawn icon for small talk: a smile, a star or a heart. */
@Composable
private fun ReactionIcon(seed: Int, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val c = center; val r = size.minDimension / 2
        when (((seed % 3) + 3) % 3) {
            0 -> {
                drawCircle(color, r); drawCircle(Ink, r * 0.12f, Offset(c.x - r * 0.35f, c.y - r * 0.2f)); drawCircle(Ink, r * 0.12f, Offset(c.x + r * 0.35f, c.y - r * 0.2f))
                drawArc(Ink, 20f, 140f, false, Offset(c.x - r * 0.5f, c.y - r * 0.35f), androidx.compose.ui.geometry.Size(r, r * 0.9f), style = Stroke(r * 0.18f, cap = StrokeCap.Round))
            }
            1 -> {
                val p = androidx.compose.ui.graphics.Path()
                for (i in 0 until 10) { val rr = if (i % 2 == 0) r else r * 0.45f; val a = (-90 + i * 36) * Math.PI / 180; val x = c.x + (rr * Math.cos(a)).toFloat(); val y = c.y + (rr * Math.sin(a)).toFloat(); if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
                p.close(); drawPath(p, color)
            }
            else -> {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(c.x, c.y + r * 0.9f); cubicTo(c.x - r * 1.5f, c.y - r * 0.1f, c.x - r * 0.6f, c.y - r * 1.1f, c.x, c.y - r * 0.3f)
                    cubicTo(c.x + r * 0.6f, c.y - r * 1.1f, c.x + r * 1.5f, c.y - r * 0.1f, c.x, c.y + r * 0.9f); close()
                }
                drawPath(p, color)
            }
        }
    }
}

/** Film style captions: the last two lines, centered, no boxes. */
@Composable
private fun Captions(vm: CasualViewModel, modifier: Modifier) {
    val dareShown = vm.dare != null && !vm.dareOn && vm.canBet
    val lines = vm.chat.toList().takeLast(if (dareShown) 2 else 3)
    Column(
        modifier.clip(androidx.compose.ui.graphics.RectangleShape)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (lines.isEmpty()) 0f else 0.32f)))),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom), horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEachIndexed { i, l ->
            key(l.key) {
                val a = remember { Animatable(0f) }
                val fade = remember { Animatable(1f) }
                LaunchedEffect(Unit) { a.animateTo(1f, tween(260)) }
                LaunchedEffect(Unit) { if (!vm.instant) { delay(4300L + l.text.length * 60L); fade.animateTo(0f, tween(800)) } }
                val dim = if (i == lines.size - 1) 1f else if (i == lines.size - 2) 0.75f else 0.5f
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        pushStyle(androidx.compose.ui.text.SpanStyle(color = speakerColor(l.who), fontWeight = FontWeight.Black, letterSpacing = 1.sp, fontSize = 11.sp)); append(l.name.uppercase().ifEmpty { "SOMEONE" }); pop()
                        append("   "); append(l.text)
                    },
                    fontSize = 15.sp, lineHeight = 19.sp, color = Color.White, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, maxLines = 2,
                    style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 2f), 8f)),
                    modifier = Modifier.graphicsLayer { alpha = a.value * dim * fade.value },
                )
            }
        }
        if (dareShown) DareRow(vm, Modifier.fillMaxWidth())
    }
}

/** A quiet log: one line per remark with a colored bar, no boxes. */
@Composable
private fun TableLog(vm: CasualViewModel, modifier: Modifier) {
    Column(modifier.clip(androidx.compose.ui.graphics.RectangleShape), verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom)) {
        val dareShown = vm.dare != null && !vm.dareOn && vm.canBet
        val lines = vm.chat.toList().takeLast(if (dareShown) 3 else 4)
        lines.forEachIndexed { i, l ->
            key(l.key) {
                val a = remember { Animatable(0f) }
                LaunchedEffect(Unit) { a.animateTo(1f, tween(260)) }
                val dim = 0.5f + 0.5f * (i + 1f) / lines.size
                val col = speakerColor(l.who)
                Row(Modifier.graphicsLayer { alpha = a.value * dim; translationX = (1f - a.value) * -16.dp.toPx() }.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(col))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = col, fontWeight = FontWeight.Black, fontSize = 11.sp)); append(l.name.ifEmpty { "Someone" }); pop()
                            append("  "); append(l.text)
                        },
                        fontSize = 13.sp, lineHeight = 16.sp, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (dareShown) DareRow(vm, Modifier.fillMaxWidth())
    }
}

private val SeatColors = listOf(Color(0xFFFFB74D), Color(0xFF81D4FA), Color(0xFFA5D6A7), Color(0xFFF48FB1))
private fun speakerColor(who: Int): Color = when {
    who == CasualViewModel.DEALER -> Gold
    who == CasualViewModel.PIT -> Color(0xFFFF8A80)
    else -> SeatColors[who.coerceIn(0, 3)]
}

/** The table chat: the newest line at the bottom, older ones fading above it. */
@Composable
private fun ChatFeed(vm: CasualViewModel, modifier: Modifier) {
    Column(modifier.clip(androidx.compose.ui.graphics.RectangleShape), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom), horizontalAlignment = Alignment.CenterHorizontally) {
        val dareShown = vm.dare != null && !vm.dareOn && vm.canBet
        val lines = vm.chat.toList().takeLast(if (dareShown) 2 else 4)
        lines.forEachIndexed { i, l -> key(l.key) { ChatRow(l, lines.size - 1 - i) } }
        if (dareShown) DareRow(vm, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChatRow(l: ChatLine, age: Int) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    val dim = when (age) { 0 -> 1f; 1 -> 0.88f; else -> 0.7f }
    val dark = l.who == CasualViewModel.PIT
    val text = androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(androidx.compose.ui.text.SpanStyle(color = speakerColor(l.who), fontWeight = FontWeight.Black)); append(l.name.ifEmpty { "Someone" }); pop()
        append("  "); append(l.text)
    }
    Text(
        text, fontSize = 12.sp, lineHeight = 15.sp, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.graphicsLayer { alpha = a.value * dim; translationY = (1f - a.value) * 12.dp.toPx() }
            .clip(RoundedCornerShape(14.dp)).background(if (dark) Color(0xF01B1B1F) else Color(0xE60B2417))
            .border(1.dp, speakerColor(l.who).copy(alpha = 0.55f), RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

// Where people look. Positions are fractions of the table: the dealer at the top, you at the bottom.
private fun dirTo(fx: Float, fy: Float, tx: Float, ty: Float) = Offset(((tx - fx) * 2.4f).coerceIn(-1f, 1f), ((ty - fy) * 2.4f).coerceIn(-1f, 1f))

/** A slowly changing number that decides which thing someone glances at next. */
@Composable
private fun rememberGlance(seed: Long): Int {
    var g by remember(seed) { mutableStateOf((seed % 6).toInt().let { if (it < 0) -it else it }) }
    if (CasualAnim.live) LaunchedEffect(seed) {
        val r = kotlin.random.Random(seed)
        while (true) { kotlinx.coroutines.delay(1300L + r.nextLong(0, 2900)); g = r.nextInt(6) }
    }
    return g
}

private fun seatGaze(vm: CasualViewModel, i: Int, g: Int, hasHand: Boolean, talking: Boolean, mood: Mood?): Offset {
    val mx = SeatX[i]; val my = 0.72f
    val dealer = dirTo(mx, my, 0.5f, 0.05f); val you = dirTo(mx, my, 0.5f, 0.95f); val cards = Offset(0f, 0.9f)
    fun seat(j: Int) = dirTo(mx, my, SeatX[j.coerceIn(0, 3)], 0.72f)
    val near = if (i == 0) 1 else if (i == 3) 2 else if (g % 2 == 0) i - 1 else i + 1
    val far = if (i < 2) 3 else 0
    val r = vm.reach
    return when {
        mood == Mood.SAD || mood == Mood.ANGRY -> cards
        mood == Mood.CHEER || mood == Mood.HAPPY -> if (g % 2 == 0) dealer else you
        talking -> if (g % 3 == 0) dealer else you
        r != null && g < 4 -> when { r.target == CasualViewModel.DEALER -> dealer; r.target >= CasualViewModel.YOU -> you; r.target == i -> cards; else -> seat(r.target) }
        hasHand && g < 2 -> cards
        else -> when (g) { 0 -> cards; 1 -> dealer; 2 -> you; 3 -> seat(near); 4 -> seat(far); else -> dealer }
    }
}

private fun dealerGaze(vm: CasualViewModel, g: Int): Offset {
    val r = vm.reach
    val from = 0.1f
    return when {
        vm.peeking -> Offset(0f, 1f)
        r != null -> when { r.target == CasualViewModel.DEALER -> Offset(0f, 0.9f); r.target >= CasualViewModel.YOU -> Offset(0f, 0.8f); else -> dirTo(0.5f, from, SeatX[r.target.coerceIn(0, 3)], 0.72f) }
        g < 3 -> Offset(0f, 0.8f)
        g == 3 -> dirTo(0.5f, from, SeatX[1], 0.72f)
        g == 4 -> dirTo(0.5f, from, SeatX[2], 0.72f)
        else -> dirTo(0.5f, from, SeatX[if (g % 2 == 0) 0 else 3], 0.72f)
    }
}

private fun pitX(seat: Int, w: Dp, seatX: (Int) -> Dp): Dp =
    // He stands behind the dealer on the side of the player he's watching, clear of the shoe.
    if (seat <= 1) w * 0.14f else w * 0.86f

@Composable
private fun DealerZone(vm: CasualViewModel, w: Dp, dz: Dp, size: Dp, seatX: (Int) -> Dp, shoe: DpOffset) {
    val d = vm.dealer
    // Shift change: the old dealer slides out, the new one slides in.
    val slide = remember { Animatable(0f) }
    var shownIdx by remember { mutableStateOf(vm.dealerIdx) }
    LaunchedEffect(vm.dealerIdx) {
        if (shownIdx != vm.dealerIdx) {
            if (!vm.instant) slide.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            shownIdx = vm.dealerIdx
            slide.snapTo(-1f)
            if (!vm.instant) slide.animateTo(0f, tween(700, easing = FastOutSlowInEasing)) else slide.snapTo(0f)
        }
    }
    val dealer = CasualDealers.ALL[shownIdx]
    val density = LocalDensity.current
    // The shoe.
    Box(
        Modifier.offset(shoe.x - 22.dp, shoe.y - 26.dp).size(48.dp, 40.dp).clip(RoundedCornerShape(6.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF3B3B3B), Color(0xFF151515)))).border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
    ) { CardBack(22.dp, Modifier.align(Alignment.CenterStart).offset(x = 4.dp).graphicsLayer { rotationZ = -8f }) }
    val walking = slide.value != 0f
    Box(Modifier.offset(w / 2 - size / 2, 0.dp).graphicsLayer { translationX = slide.value * w.value * density.density * 0.7f }) {
        val dg = dealerGaze(vm, rememberGlance(901L + shownIdx))
        Avatar(dealer.look, size, mood = if (shownIdx == vm.dealerIdx) vm.dealerMood else Mood.NEUTRAL, talking = vm.bubbles.containsKey(CasualViewModel.DEALER), phase = 0.3f, walking = walking, dealer = true, name = "Dealer ${dealer.name}", gazeX = dg.x, gazeY = dg.y)
        if (vm.peeking) Text("Peeking", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.align(Alignment.TopEnd).offset(x = -size * 0.05f, y = size * 0.12f).background(Cream, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
        Text(dealer.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, modifier = Modifier.align(Alignment.TopStart).offset(x = size * 0.02f, y = size * 0.12f).background(Gold, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp))
    }
    // The dealing hand stays next to the shoe. To pitch a card it flicks a short way toward the seat, and the
    // card itself glides across the felt (see TableCard). It never stretches across the table.
    val r = vm.reach
    val restX = shoe.x.value - 22f; val restY = shoe.y.value + 4f
    val reachMax = 30f
    var tx = restX; var ty = restY
    if (r != null) {
        val gx: Float; val gy: Float
        when {
            r.target == CasualViewModel.DEALER -> { gx = w.value / 2; gy = dz.value + 10f }
            r.target >= CasualViewModel.YOU -> { gx = w.value / 2 + (r.target - CasualViewModel.YOU) * 40f; gy = dz.value + 80f }
            else -> { gx = seatX(r.target).value; gy = dz.value + 80f }
        }
        val dx = gx - restX; val dy = gy - restY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val k = minOf(1f, reachMax / dist)
        tx = restX + dx * k; ty = restY + dy * k
    }
    val hx by animateFloatAsState(tx, if (vm.instant) tween(0) else spring(stiffness = 1100f, dampingRatio = 0.75f), label = "hx")
    val hy by animateFloatAsState(ty, if (vm.instant) tween(0) else spring(stiffness = 1100f, dampingRatio = 0.75f), label = "hy")
    if (!walking) Canvas(Modifier.fillMaxSize()) {
        val sh = Offset((w / 2 + size * 0.3f).toPx(), (size * 0.72f).toPx())
        val hand = Offset(hx.dp.toPx(), hy.dp.toPx())
        val sleeve = Color(dealer.look.shirt.toInt())
        val skin = Color(dealer.look.skin.toInt())
        val elbow = Offset(sh.x + (hand.x - sh.x) * 0.35f + 6.dp.toPx(), maxOf(sh.y, hand.y) + 12.dp.toPx())
        drawLine(sleeve, sh, elbow, (size * 0.1f).toPx(), StrokeCap.Round)
        drawLine(Color.White, elbow, hand, (size * 0.08f).toPx(), StrokeCap.Round)
        drawCircle(skin, (size * 0.055f).toPx(), hand)
        // Left hand rests on the felt.
        val sl = Offset((w / 2 - size * 0.3f).toPx(), (size * 0.72f).toPx())
        val lh = Offset((w / 2 - size * 0.4f).toPx(), (size * 0.88f).toPx())
        drawLine(sleeve, sl, lh, (size * 0.1f).toPx(), StrokeCap.Round)
        drawCircle(skin, (size * 0.055f).toPx(), lh)
    }
}

/** Shoe procedure: the player's yellow cut card goes into the shoe, then the dealer burns the top card into the tray. */
@Composable
private fun ShoeProc(stage: Int, shoe: DpOffset, tray: DpOffset, midX: Dp, youTop: Dp) {
    val p = remember(stage) { Animatable(0f) }
    LaunchedEffect(stage) { p.animateTo(1f, tween(if (stage == 1) 800 else 650, easing = FastOutSlowInEasing)) }
    val dens = LocalDensity.current.density
    if (stage == 1) {
        val fromX = midX.value + 20f; val fromY = youTop.value
        Box(
            Modifier.offset(shoe.x - 14.dp, shoe.y - 10.dp).size(28.dp, 18.dp).graphicsLayer {
                translationX = (fromX - shoe.x.value) * dens * (1f - p.value); translationY = (fromY - shoe.y.value) * dens * (1f - p.value)
                rotationZ = -12f * (1f - p.value)
            }.clip(RoundedCornerShape(2.dp)).background(Color(0xFFF2D14B)).border(1.dp, Color(0xFF8A6D00), RoundedCornerShape(2.dp)).semantics { contentDescription = "Cut card" },
        )
    } else {
        CardBack(22.dp, Modifier.offset(shoe.x - 11.dp, shoe.y - 14.dp).graphicsLayer {
            translationX = (tray.x.value - shoe.x.value) * dens * p.value; translationY = (tray.y.value - shoe.y.value) * dens * p.value
            rotationZ = 24f * p.value; alpha = 1f - 0.4f * p.value
        })
    }
}

/** The discard tray: swept cards land here and the stack grows as the shoe is dealt. */
@Composable
private fun DiscardTray(at: DpOffset, dealt: Int, total: Int) {
    val frac = if (total == 0) 0f else (dealt.toFloat() / total).coerceIn(0f, 1f)
    Box(Modifier.offset(at.x - 22.dp, at.y - 20.dp).size(44.dp, 34.dp).clip(RoundedCornerShape(5.dp)).background(Brush.verticalGradient(listOf(Color(0xFF3B3B3B), Color(0xFF151515)))).border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(5.dp)).semantics { contentDescription = "Discard tray" }) {
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(34.dp, (4 + 22 * frac).dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFEFE8D8)))
    }
}

@Composable
private fun PitBoss(vm: CasualViewModel, w: Dp, dz: Dp, seatX: (Int) -> Dp) {
    val p = vm.pit
    val target = if (p == null) w + 90.dp else pitX(p.seat, w, seatX)
    val x by animateFloatAsState(target.value, if (vm.instant) tween(0) else tween(1100, easing = FastOutSlowInEasing), label = "pit")
    if (x > w.value + 60) return
    val moving = abs(x - target.value) > 2
    val sz = dz * 0.8f
    Box(Modifier.offset(x.dp - sz / 2, -sz * 0.08f)) {
        val ps = p?.seat ?: 2
        val pg = dirTo(if (ps <= 1) 0.14f else 0.86f, 0.1f, SeatX[ps.coerceIn(0, 3)], 0.72f)
        Avatar(PitLook, sz, talking = vm.bubbles.containsKey(CasualViewModel.PIT), phase = 0.7f, walking = moving, name = "Pit boss", gazeX = pg.x, gazeY = pg.y)
        Text("PIT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.align(Alignment.BottomCenter).offset(y = -sz * 0.12f).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp))
    }
}

@Composable
private fun SeatBlock(vm: CasualViewModel, i: Int, s: Seat, x: Dp, y: Dp, seatW: Dp, card: Dp, avatar: Dp, shoe: DpOffset, pitchMs: Int, sweep: Float, tray: DpOffset) {
    val gs = vm.gs
    val bot = vm.roundSeats.indexOfFirst { it.first == i && it.second == s.id }
    val hand: Hand? = if (bot >= 0) gs.bots.getOrNull(bot) else null
    val enter = remember(s.id) { Animatable(if (vm.arriving.containsKey(s.id) && !vm.instant) 1f else 0f) }
    LaunchedEffect(s.id) { if (enter.value > 0f) { enter.animateTo(0f, tween(900, easing = FastOutSlowInEasing)); vm.arriving.remove(s.id) } }
    val density = LocalDensity.current
    val dir = if (i >= 2) 1f else -1f
    Box(Modifier.offset(x - seatW / 2, y).width(seatW)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Cards on the felt in front of them.
            val dens = LocalDensity.current.density
            Box(Modifier.height(card * 1.4f).fillMaxWidth().graphicsLayer {
                translationX = (tray.x.value - x.value) * dens * sweep; translationY = (tray.y.value - y.value) * dens * sweep
                alpha = 1f - sweep; scaleX = 1f - 0.5f * sweep; scaleY = 1f - 0.5f * sweep
            }, contentAlignment = Alignment.Center) {
                if (hand != null) {
                    val cards = hand.cards.zip(hand.seqs).filter { it.second < vm.shown }
                    val step = minOf(card * 0.42f, (seatW - card - 4.dp) / (cards.size - 1).coerceAtLeast(1))
                    val total = card + step * (cards.size - 1).coerceAtLeast(0)
                    Box(Modifier.width(total).height(card * 1.4f)) {
                        cards.forEachIndexed { k, (c, seq) ->
                            key(seq) { TableCard(c, card, true, DpOffset(shoe.x - (x - total / 2 + step * k), shoe.y - y), animate = !vm.instant, durationMs = pitchMs, modifier = Modifier.offset(x = step * k), restTilt = ((seq * 37) % 7 - 3) * 0.9f, lift = true) }
                        }
                    }
                } else if (s.sittingOut && vm.dealtOnce) {
                    Text("sitting out", fontSize = 10.sp, color = Cream.copy(alpha = 0.6f))
                }
            }
            // Their bet and how the hand ended.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(22.dp)) {
                if (hand != null && vm.shown >= gs.nextSeq) {
                    val res = if (vm.roundOver) gs.botResults.getOrNull(bot) else null
                    val total = handTotal(hand.cards).first
                    val label = res?.outcome?.label ?: if (total > 21) "Bust" else "$total"
                    val color = when { res == null -> Cream; res.net > 0 -> GoodGreen; res.net < 0 -> BadRed; else -> Cream }
                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)).padding(horizontal = 6.dp))
                }
                if (hand != null) { Spacer(Modifier.width(3.dp)); CasualChipStack(hand.bet, 15.dp) }
            }
            Box(Modifier.graphicsLayer { translationX = dir * enter.value * 220 * density.density; alpha = 1f - enter.value }, contentAlignment = Alignment.BottomCenter) {
                val glance = rememberGlance(s.id * 31 + i)
                val mood = vm.moods[s.id]
                val gz = if (enter.value > 0.02f) Offset(0f, -0.2f) else seatGaze(vm, i, glance, hand != null && !vm.roundOver, vm.bubbles.containsKey(i), mood)
                Avatar(s.who.look, avatar, mood = mood ?: Mood.NEUTRAL, talking = vm.bubbles.containsKey(i), phase = (s.id % 7) / 7f, walking = enter.value > 0.02f, name = s.who.name, gazeX = gz.x, gazeY = gz.y)
                vm.actions[i]?.let { a ->
                    Text(a, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.align(Alignment.TopEnd).offset(x = avatar * 0.2f, y = avatar * 0.1f).background(Gold, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp))
                }
            }
            Text(s.who.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (vm.bubbles.containsKey(i)) SeatColors[i] else Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(money(s.chips), fontSize = 11.sp, color = Gold, maxLines = 1)
        }
    }
}

/** Someone walking away from (or to) a seat. */
@Composable
private fun Walker(s: Seat, x: Dp, y: Dp, size: Dp, toRight: Boolean, away: Boolean, phase: Float) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(1100, easing = LinearEasing)) }
    val density = LocalDensity.current
    Box(Modifier.offset(x - size / 2, y).graphicsLayer {
        translationX = (if (toRight) 1 else -1) * p.value * 240 * density.density
        alpha = 1f - p.value
    }) { Avatar(s.who.look, size, mood = Mood.WAVE, walking = true, phase = phase, name = s.who.name) }
}

@Composable
private fun TotalPill(cards: List<Card>, modifier: Modifier) {
    if (cards.isEmpty()) return
    val (t, soft) = handTotal(cards)
    val txt = when { t > 21 -> "Bust"; cards.size == 2 && t == 21 -> "BJ"; soft && t < 21 -> "${t - 10}/$t"; else -> "$t" }
    Text(txt, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Ink, modifier = modifier.background(Cream, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun YourSpot(vm: CasualViewModel, w: Dp, top: Dp, card: Dp, shoe: DpOffset, pitchMs: Int, scale: Float, sweep: Float, tray: DpOffset) {
    val gs = vm.gs
    val hands = if (vm.dealtOnce) gs.hands else emptyList()
    val many = hands.size > 1
    val cw = if (many) card * (if (hands.size > 2) 0.62f else 0.78f) else card
    // Your hands stay in a narrow lane under the middle of the table so they never run over the seats beside you.
    val slotW = minOf(w * 0.27f, w * 0.8f / hands.size.coerceAtLeast(1))
    // Side bet spots and the main betting circle.
    val circle = (64 * scale).dp
    val bettingView = vm.canBet && !(vm.roundOver && hands.isNotEmpty())
    if (bettingView || hands.isEmpty()) {
        val cy = top + 30.dp
        Box(Modifier.offset(w / 2 - circle / 2, cy).size(circle).border(2.dp, Cream.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
            if (vm.bet > 0) BetChips(vm.bet, (36 * scale).dp) else Text("BET", fontSize = 12.sp, color = Cream.copy(alpha = 0.5f), letterSpacing = 2.sp)
        }
        Text(if (vm.bet > 0) money(vm.bet) else "Tap chips to bet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream, textAlign = TextAlign.Center, modifier = Modifier.width(w).offset(y = cy + circle + 4.dp))
        if (vm.settings.sideBets) {
            SideSpot("Perfect Pairs", vm.ppBet, Modifier.offset(w / 2 - 78.dp, cy - 72.dp)) { vm.cyclePP() }
            SideSpot("21+3", vm.tpBet, Modifier.offset(w / 2 + 0.dp, cy - 72.dp)) { vm.cycleTP() }
        }
    }
    if (hands.isNotEmpty() && !(bettingView && !vm.roundOver)) {
        val dens = LocalDensity.current.density
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = (tray.x.value - w.value / 2) * dens * sweep; translationY = (tray.y.value - top.value) * dens * sweep
            alpha = 1f - sweep; scaleX = 1f - 0.5f * sweep; scaleY = 1f - 0.5f * sweep
        }) {
        vm.actions[CasualViewModel.YOU]?.let { sig ->
            Box(Modifier.offset(0.dp, top - 26.dp).width(w), contentAlignment = Alignment.Center) {
                Text(sig, fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.background(Gold, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 1.dp))
            }
        }
        hands.forEachIndexed { hi, h ->
            val cards = h.cards.zip(h.seqs).filter { it.second < vm.shown }
            val step = minOf(cw * 0.38f, ((slotW - cw) / (cards.size - 1).coerceAtLeast(1)).coerceAtLeast(9.dp))
            val total = cw + step * (cards.size - 1).coerceAtLeast(0)
            val cx = w / 2 + slotW * (hi - (hands.size - 1) / 2f)
            val x0 = cx - total / 2
            val active = gs.phase == Phase.PLAYER && gs.activeHand == hi && many
            Box(Modifier.offset(x0 - 4.dp, top - 4.dp).size(total + 8.dp, cw * 1.4f + 8.dp).then(if (active) Modifier.border(2.dp, Gold, RoundedCornerShape(8.dp)) else Modifier))
            cards.forEachIndexed { k, (c, seq) ->
                key(seq) {
                    val cxk = x0 + step * k
                    TableCard(c, cw, true, DpOffset(shoe.x - cxk, shoe.y - top), animate = !vm.instant, durationMs = pitchMs, modifier = Modifier.offset(cxk, top).graphicsLayer { rotationZ = if (h.doubled && k == 2) 90f else 0f }, restTilt = ((seq * 41) % 5 - 2) * 0.7f, lift = true)
                }
            }
            val y2 = top + cw * 1.4f + 6.dp
            Row(Modifier.offset(cx - 80.dp, y2).width(160.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (hi == 0) { Text("YOU", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.background(Gold, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp)); Spacer(Modifier.width(5.dp)) }
                TotalPill(cards.map { it.first }, Modifier)
                Spacer(Modifier.width(6.dp))
                CasualChipStack(h.bet, 20.dp)
                val res = if (vm.roundOver) gs.results.getOrNull(hi) else null
                if (res != null && many) {
                    Spacer(Modifier.width(4.dp))
                    Text(res.outcome.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (res.net > 0) GoodGreen else if (res.net < 0) BadRed else Cream)
                }
            }
        }
        }
        // Side bet results.
        val notes = listOfNotNull(
            gs.perfectPairsResult?.takeIf { vm.shown > gs.roundStartSeq + 1 }?.let { "PP: $it" },
            vm.tpResult?.takeIf { vm.shown > gs.roundStartSeq + 2 }?.let { "21+3: $it" },
        )
        if (notes.isNotEmpty()) Text(notes.joinToString("   "), fontSize = 12.sp, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.width(w).offset(y = top - 20.dp))
    }
    if (vm.drinkHands > 0) DrinkGlass(Modifier.offset(w / 2 + 92.dp, top + cw * 1.4f - 6.dp))
    vm.hint?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.offset(w / 2 - 110.dp, top - 34.dp).width(220.dp).background(Gold, RoundedCornerShape(50)).padding(vertical = 4.dp)) }
    if (vm.gs.phase == Phase.INSURANCE && !vm.busy) {
        Text(if (gs.hands.firstOrNull()?.isBlackjack == true) "Even money?" else "Insurance?", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.width(w).offset(y = top - 36.dp))
    }
}

@Composable
private fun PayoutFlights(vm: CasualViewModel, rack: DpOffset, spot: (Int) -> DpOffset) {
    vm.payouts.forEach { p -> key(p.key) { ChipFlight(p, rack, spot(p.seat), vm.instant) } }
}

/** Chips slide from the dealer's rack to a winner, or from a loser's bet back into the rack. */
@Composable
private fun ChipFlight(p: Payout, rack: DpOffset, spot: DpOffset, instant: Boolean) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!instant) kotlinx.coroutines.delay(500)
        t.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val from = if (p.toDealer) spot else rack
    val to = if (p.toDealer) rack else spot
    val x = from.x + (to.x - from.x) * t.value
    val y = from.y + (to.y - from.y) * t.value - (kotlin.math.sin(t.value * 3.14159f) * 14f).dp
    Box(Modifier.offset(x - 9.dp, y - 12.dp).graphicsLayer { alpha = if (t.value >= 1f) 0f else 1f }) { CasualChipStack(p.amount, 18.dp) }
}

@Composable
private fun BetChips(amount: Int, size: Dp) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(amount) { pop.snapTo(1.18f); pop.animateTo(1f, spring(dampingRatio = 0.45f)) }
    CasualChipStack(amount, size, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
}

@Composable
private fun SideSpot(label: String, amount: Int, modifier: Modifier, onTap: () -> Unit) {
    Column(modifier.width(80.dp).clickable(onClick = onTap).semantics { contentDescription = "$label side bet ${if (amount == 0) "off" else money(amount)}" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).border(1.5.dp, Gold.copy(alpha = if (amount > 0) 0.9f else 0.4f), CircleShape), contentAlignment = Alignment.Center) {
            if (amount > 0) CasualChipStack(amount, 26.dp) else Text("+", color = Gold.copy(alpha = 0.6f), fontSize = 18.sp)
        }
        Text(label, fontSize = 10.sp, color = Gold.copy(alpha = 0.85f), maxLines = 1)
    }
}

@Composable
private fun ResultBanner(b: CasualBanner, modifier: Modifier) {
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 500f)) }
    val sparkle = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (b.big) sparkle.animateTo(1f, tween(1400)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (b.big) Canvas(Modifier.size(260.dp, 120.dp)) {
            val r = kotlin.random.Random(b.key)
            repeat(28) {
                val a = r.nextFloat() * 6.283f; val d = (40 + r.nextFloat() * 110).dp.toPx() * sparkle.value
                val col = listOf(Gold, Color.White, Color(0xFFFF8A80), Color(0xFF80D8FF))[it % 4]
                drawCircle(col.copy(alpha = 1f - sparkle.value), 3.dp.toPx(), Offset(center.x + kotlin.math.cos(a) * d, center.y + kotlin.math.sin(a) * d * 0.6f))
            }
        }
        Row(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.86f))
                .border(1.5.dp, if (b.amount > 0) Gold else Cream.copy(alpha = 0.3f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (b.title.isNotEmpty()) Text(b.title, fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = TitleFont, color = if (b.amount > 0) Gold else Cream)
            Text(if (b.amount > 0) "+${money(b.amount)}" else if (b.amount < 0) "-${money(-b.amount)}" else "Even", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (b.amount > 0) GoodGreen else if (b.amount < 0) BadRed else Cream)
        }
    }
}

@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 700f)) }
    Text(
        text, fontSize = 12.sp, lineHeight = 15.sp, color = if (dark) Cream else Ink, fontWeight = FontWeight.Medium,
        modifier = modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = ((pop.value - 0.6f) / 0.4f).coerceIn(0f, 1f) }
            .clip(RoundedCornerShape(12.dp)).background(if (dark) Color(0xEE1B1B1F) else Color(0xF5FFFDF6))
            .border(1.dp, if (dark) Gold.copy(alpha = 0.6f) else Color(0x33000000), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

/** The dealer's shuffle: a hand riffle, a strip shuffle or the machine. */
@Composable
private fun ShuffleShow(style: Int, modifier: Modifier) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(if (style == 2) 900 else 1500, easing = LinearEasing)) }
    Box(modifier.size(140.dp, 70.dp), contentAlignment = Alignment.Center) {
        if (style == 2) {
            Box(Modifier.size(90.dp, 50.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF263238)).border(1.dp, Gold, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("SHUFFLING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Gold.copy(alpha = 0.5f + 0.5f * kotlin.math.abs(kotlin.math.sin(p.value * 12))))
            }
        } else {
            val phase = (p.value * 3) % 1f
            for (side in listOf(-1, 1)) {
                val dx = if (style == 0) side * (30 * (1 - phase)) else side * 6f
                val dy = if (style == 1) (phase * 20 - 10) * side else -kotlin.math.abs(kotlin.math.sin(phase * 3.14f)) * 8
                CardBack(30.dp, Modifier.offset(dx.dp, dy.dp).graphicsLayer { rotationZ = side * (if (style == 0) 12f * (1 - phase) else 0f) })
            }
        }
    }
}

@Composable
private fun Toasts(vm: CasualViewModel) {
    val a = vm.toasts.firstOrNull() ?: return
    LaunchedEffect(a.id) { if (!vm.instant) { kotlinx.coroutines.delay(2600); vm.toasts.remove(a) } }
    Box(Modifier.fillMaxSize().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFF6DA8C), Gold))).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("★", fontSize = 18.sp, color = Ink)
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Achievement: ${a.title}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Ink)
                Text(a.desc, fontSize = 12.sp, color = Ink.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun Controls(vm: CasualViewModel) {
    val gs = vm.gs
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(WoodTop, WoodBottom))).padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().height(116.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            when {
                vm.busy -> Text("Dealing...", fontSize = 16.sp, color = Cream.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
                gs.phase == Phase.INSURANCE -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoldButton("No thanks", Modifier.weight(1f), primary = false) { vm.insurance(false) }
                    GoldButton(if (gs.hands.firstOrNull()?.isBlackjack == true) "Even money" else "Insure", Modifier.weight(1f)) { vm.insurance(true) }
                }
                gs.phase == Phase.PLAYER -> {
                    val o = gs.options
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton("Hit", Modifier.weight(1f)) { vm.act(Action.HIT) }
                        GoldButton("Stand", Modifier.weight(1f)) { vm.act(Action.STAND) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoldButton("Double", Modifier.weight(1f), enabled = o.canDouble, primary = false) { vm.act(Action.DOUBLE) }
                        GoldButton("Split", Modifier.weight(1f), enabled = o.canSplit, primary = false) { vm.act(Action.SPLIT) }
                        if (vm.settings.surrender) GoldButton("Give up", Modifier.weight(1f), enabled = o.canSurrender, primary = false) { vm.act(Action.SURRENDER) }
                        if (vm.settings.hints) GoldButton("Hint", Modifier.weight(0.8f), primary = false) { vm.showHint() }
                    }
                }
                vm.broke -> {
                    Text("Out of chips. Grab more and keep playing.", fontSize = 14.sp, color = Cream, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldButton("Rebuy ${money(CasualStore.REBUY)}", Modifier.weight(1f)) { vm.rebuy() }
                        vm.bonusAvailable()?.let { b -> GoldButton("Daily bonus ${money(b.first)}", Modifier.weight(1f), primary = false) { vm.claimBonus() } }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        chipButtons(vm.settings.tableMin).forEach { v ->
                            CasinoChip(styleOf(v), 50.dp, Modifier.clickable(enabled = vm.bankroll >= vm.settings.tableMin) { vm.addChip(v) }.semantics { contentDescription = "Add ${money(v)}" })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (vm.bet == 0 && vm.store.lastBet > 0) GoldButton("Rebet", Modifier.weight(0.8f), primary = false) { vm.repeatBet() }
                        else GoldButton("Clear", Modifier.weight(0.8f), enabled = vm.bet > 0, primary = false) { vm.clearBet() }
                        GoldButton("x2", Modifier.weight(0.6f), enabled = vm.bet > 0 && vm.bet * 2 <= vm.bankroll, primary = false) { vm.doubleBet() }
                        if (vm.settings.showCount) GoldButton("Watch", Modifier.weight(0.8f), primary = false) { vm.watch() }
                        GoldButton("Deal", Modifier.weight(1.4f), enabled = vm.bet >= vm.settings.tableMin && vm.bet <= vm.bankroll) { vm.deal() }
                    }
                }
            }
        }
    }
}
