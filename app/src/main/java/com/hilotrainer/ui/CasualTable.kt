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
        chips.forEachIndexed { i, s -> CasinoChip(s, size, Modifier.offset(y = -(size * 0.1f) * i), label = i == chips.size - 1) }
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
    BackHandler(enabled = menu != null) { menu = null }
    LaunchedEffect(vm) { vm.chatterLoop() }
    val s = vm.settings
    CompositionLocalProvider(LocalCardStyle provides CardStyle(back = s.cardBack)) {
        Column(Modifier.fillMaxSize().background(Ink)) {
            TopBar(vm, onBack) { menu = it }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().themedFelt(s.theme)) {
                TableArea(vm, maxWidth, maxHeight)
            }
            Controls(vm)
        }
        if (menu != null) CasualMenu(vm, menu!!, onTab = { menu = it }) { menu = null }
        Toasts(vm)
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

/** Where each of the five other seats sits on the arc, as a fraction of the width, and how far down. */
private val SeatX = listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)
private val SeatDrop = listOf(0.0f, 0.55f, 0.8f, 0.55f, 0.0f)

@Composable
private fun TableArea(vm: CasualViewModel, w: Dp, h: Dp) {
    val gs = vm.gs
    val scale = minOf(w.value / 411f, h.value / 620f).coerceIn(0.78f, 1.9f)
    val dz = h * 0.19f
    val dealerSize = (dz * 0.95f).coerceAtMost(150.dp)
    val dCard = (44 * scale).dp
    val dCardsY = dz + 6.dp
    val seatW = w / 5
    val sCard = minOf((32 * scale).dp, seatW * 0.42f)
    val avatar = minOf((56 * scale).dp, seatW * 0.82f)
    val youCard = (60 * scale).dp
    // Stack from the bottom: your cards, then the seats above them.
    val youTop = h - youCard * 1.4f - 44.dp
    val seatBlock = sCard * 1.4f + 22.dp + avatar + 44.dp
    val seatTop = (youTop - 10.dp - seatBlock - h * 0.06f * 0.8f).coerceAtLeast(dCardsY + dCard * 1.4f + 8.dp)
    val shoe = DpOffset(minOf(w - 44.dp, w / 2 + dealerSize * 0.5f + 110.dp), dz * 0.55f)
    val seatXdp = { i: Int -> w * SeatX[i] }
    val seatYdp = { i: Int -> seatTop + (h * 0.06f) * SeatDrop[i] }
    val pitchMs = when (vm.dealer.pitch) { 0 -> 230; 1 -> 330; else -> 460 }

    // Printing on the felt.
    val theme = vm.settings.theme
    Column(Modifier.fillMaxWidth().offset(y = dz + dCard * 1.4f + 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (vm.settings.sixToFive) "BLACKJACK PAYS 6 TO 5" else "BLACKJACK PAYS 3 TO 2", fontSize = (13 * scale).sp, fontFamily = TitleFont, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = theme.print.copy(alpha = 0.55f))
        Text(
            (if (vm.settings.dealerHitsSoft17) "Dealer hits soft 17" else "Dealer must stand on all 17s") + "  ·  Insurance pays 2 to 1",
            fontSize = (10 * scale).sp, color = theme.print.copy(alpha = 0.45f), letterSpacing = 1.sp,
        )
        Text("${money(vm.settings.tableMin)} to ${money(vm.maxBet)}", fontSize = (10 * scale).sp, color = theme.print.copy(alpha = 0.4f))
    }
    Canvas(Modifier.fillMaxSize()) {
        // The curve of the table edge in front of the dealer.
        drawArc(theme.print.copy(alpha = 0.25f), 20f, 140f, false, Offset(-size.width * 0.15f, size.height * 0.05f), Size(size.width * 1.3f, size.height * 0.62f), style = Stroke(2.dp.toPx()))
    }

    // Dealer's chip rack.
    Row(Modifier.offset(12.dp, dz * 0.62f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(5, 25, 100, 500, 1000).forEach { v -> CasualChipStack(v, 16.dp) }
    }
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
        dealerCards.forEachIndexed { i, (c, seq) ->
            key(seq) {
                val faceUp = i != 1 || (gs.holeRevealed && gs.holeRevealSeq < vm.shown)
                val x = x0 + step * i
                TableCard(c, dCard, faceUp, DpOffset(shoe.x - x, shoe.y - dCardsY), animate = !vm.instant, durationMs = pitchMs, modifier = Modifier.offset(x, dCardsY))
            }
        }
        val visible = dealerCards.mapIndexedNotNull { i, (c, _) -> if (i != 1 || (gs.holeRevealed && gs.holeRevealSeq < vm.shown)) c else null }
        TotalPill(visible, Modifier.offset(x0 + total + 6.dp, dCardsY + dCard * 0.4f))
    }
    if (vm.shuffling) ShuffleShow(vm.dealer.shuffle, Modifier.offset(w / 2 - 70.dp, dz + 8.dp))

    // Other players.
    for (i in 0 until 5) {
        val seat = vm.seatView[i]
        val x = seatXdp(i); val y = seatYdp(i)
        Box(Modifier.offset(x - seatW / 2, y).width(seatW)) {
            // Empty seat marker.
            if (seat == null && vm.departing.none { it.seat == i }) {
                Box(Modifier.align(Alignment.TopCenter).offset(y = sCard * 1.5f).size(avatar * 0.7f).border(1.5.dp, theme.print.copy(alpha = 0.22f), CircleShape))
            }
        }
        if (seat != null) SeatBlock(vm, i, seat, x, y, seatW, sCard, avatar, shoe, pitchMs)
    }
    for (d in vm.departing) key(d.key) { Walker(d.s, seatXdp(d.seat), seatYdp(d.seat) + sCard * 1.4f + 10.dp, avatar, toRight = d.seat >= 2, away = true, phase = d.seat * 0.2f) }

    // You.
    YourSpot(vm, w, youTop, youCard, shoe, pitchMs, scale)

    // Speech bubbles on top of everything.
    vm.bubbles[CasualViewModel.DEALER]?.let { b ->
        val bw = minOf(220.dp, w * 0.48f)
        key(b.key) { SpeechBubble(b.text, Modifier.offset((w / 2 + dealerSize * 0.3f).coerceAtMost(w - bw - 4.dp), 4.dp).width(bw)) }
    }
    vm.bubbles[CasualViewModel.PIT]?.let { b ->
        val px = pitX(vm.pit?.seat ?: 2, w, seatXdp)
        val bw = minOf(190.dp, w * 0.46f)
        key(b.key) { SpeechBubble(b.text, Modifier.offset((px - bw / 2).coerceIn(4.dp, w - bw - 4.dp), dz * 0.78f).width(bw), dark = true) }
    }
    for (i in 0 until 5) vm.bubbles[i]?.let { b ->
        val bw = minOf(170.dp, w * 0.42f)
        val bx = (seatXdp(i) - bw / 2).coerceIn(4.dp, w - bw - 4.dp)
        key(b.key) { SpeechBubble(b.text, Modifier.offset(bx, seatYdp(i) - 30.dp - (if (i % 2 == 1) 34.dp else 0.dp)).width(bw)) }
    }
    // How your round went, over the felt printing.
    vm.banner?.let { b -> key(b.key) { ResultBanner(b, Modifier.width(w).offset(y = dCardsY + dCard * 1.4f + 8.dp)) } }
}

private fun pitX(seat: Int, w: Dp, seatX: (Int) -> Dp): Dp =
    // He stands behind the dealer on the side of the player he's watching, clear of the shoe.
    if (seat <= 2) minOf(w * 0.2f, w / 2 - 110.dp) else maxOf(w * 0.8f, w / 2 + 110.dp).coerceAtMost(w - 130.dp)

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
        Avatar(dealer.look, size, mood = if (shownIdx == vm.dealerIdx) vm.dealerMood else Mood.NEUTRAL, talking = vm.bubbles.containsKey(CasualViewModel.DEALER), phase = 0.3f, walking = walking, dealer = true, name = "Dealer ${dealer.name}")
        Text(dealer.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, modifier = Modifier.align(Alignment.TopStart).offset(x = size * 0.02f, y = size * 0.12f).background(Gold, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp))
    }
    // The dealing hand. It rests by the shoe and reaches toward whoever gets the card.
    val r = vm.reach
    val tx = when {
        r == null -> shoe.x - 18.dp
        r.target == CasualViewModel.DEALER -> w / 2
        r.target >= CasualViewModel.YOU -> w / 2 + ((r.target - CasualViewModel.YOU) * 40).dp
        else -> seatX(r.target)
    }
    val ty = if (r == null) shoe.y + 6.dp else if (r.target == CasualViewModel.DEALER) dz + 10.dp else dz + 26.dp
    val hx by animateFloatAsState(tx.value, if (vm.instant) tween(0) else spring(stiffness = 900f, dampingRatio = 0.8f), label = "hx")
    val hy by animateFloatAsState(ty.value, if (vm.instant) tween(0) else spring(stiffness = 900f, dampingRatio = 0.8f), label = "hy")
    if (!walking) Canvas(Modifier.fillMaxSize()) {
        val sh = Offset((w / 2 + size * 0.3f).toPx(), (size * 0.72f).toPx())
        val hand = Offset(hx.dp.toPx(), hy.dp.toPx())
        val sleeve = Color(dealer.look.shirt.toInt())
        val skin = Color(dealer.look.skin.toInt())
        drawLine(sleeve, sh, Offset((sh.x + hand.x) / 2, (sh.y + hand.y) / 2 + 6.dp.toPx()), (size * 0.1f).toPx(), StrokeCap.Round)
        drawLine(Color.White, Offset((sh.x + hand.x) / 2, (sh.y + hand.y) / 2 + 6.dp.toPx()), hand, (size * 0.08f).toPx(), StrokeCap.Round)
        drawCircle(skin, (size * 0.055f).toPx(), hand)
        if (r != null) {
            drawRoundRect(Color(0xFF9E1B2C), Offset(hand.x - 7.dp.toPx(), hand.y - 13.dp.toPx()), Size(14.dp.toPx(), 19.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
        }
        // Left hand resting on the felt.
        val sl = Offset((w / 2 - size * 0.3f).toPx(), (size * 0.72f).toPx())
        val lh = Offset((w / 2 - size * 0.42f).toPx(), (dz + 2.dp).toPx())
        drawLine(sleeve, sl, lh, (size * 0.1f).toPx(), StrokeCap.Round)
        drawCircle(skin, (size * 0.055f).toPx(), lh)
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
        Avatar(PitLook, sz, mood = if (vm.bubbles.containsKey(CasualViewModel.PIT)) Mood.NEUTRAL else Mood.NEUTRAL, talking = vm.bubbles.containsKey(CasualViewModel.PIT), phase = 0.7f, walking = moving, name = "Pit boss")
        Text("PIT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Gold, modifier = Modifier.align(Alignment.BottomCenter).offset(y = -sz * 0.12f).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp))
    }
}

@Composable
private fun SeatBlock(vm: CasualViewModel, i: Int, s: Seat, x: Dp, y: Dp, seatW: Dp, card: Dp, avatar: Dp, shoe: DpOffset, pitchMs: Int) {
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
            Box(Modifier.height(card * 1.4f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (hand != null) {
                    val cards = hand.cards.zip(hand.seqs).filter { it.second < vm.shown }
                    val step = minOf(card * 0.42f, (seatW - card - 4.dp) / (cards.size - 1).coerceAtLeast(1))
                    val total = card + step * (cards.size - 1).coerceAtLeast(0)
                    Box(Modifier.width(total).height(card * 1.4f)) {
                        cards.forEachIndexed { k, (c, seq) ->
                            key(seq) { TableCard(c, card, true, DpOffset(shoe.x - (x - total / 2 + step * k), shoe.y - y), animate = !vm.instant, durationMs = pitchMs, modifier = Modifier.offset(x = step * k)) }
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
                Avatar(s.who.look, avatar, mood = vm.moods[s.id] ?: Mood.NEUTRAL, talking = vm.bubbles.containsKey(i), phase = (s.id % 7) / 7f, walking = enter.value > 0.02f, name = s.who.name)
            }
            Text(s.who.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun YourSpot(vm: CasualViewModel, w: Dp, top: Dp, card: Dp, shoe: DpOffset, pitchMs: Int, scale: Float) {
    val gs = vm.gs
    val hands = if (vm.dealtOnce) gs.hands else emptyList()
    val many = hands.size > 1
    val cw = if (many) card * (if (hands.size > 2) 0.62f else 0.78f) else card
    val slotW = w / hands.size.coerceAtLeast(1)
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
            SideSpot("Perfect Pairs", vm.ppBet, Modifier.offset(w / 2 - circle / 2 - 92.dp, cy + 8.dp)) { vm.cyclePP() }
            SideSpot("21+3", vm.tpBet, Modifier.offset(w / 2 + circle / 2 + 12.dp, cy + 8.dp)) { vm.cycleTP() }
        }
    }
    if (hands.isNotEmpty() && !(bettingView && !vm.roundOver)) {
        hands.forEachIndexed { hi, h ->
            val cards = h.cards.zip(h.seqs).filter { it.second < vm.shown }
            val step = cw * 0.38f
            val total = cw + step * (cards.size - 1).coerceAtLeast(0)
            val cx = slotW * hi + slotW / 2
            val x0 = cx - total / 2
            val active = gs.phase == Phase.PLAYER && gs.activeHand == hi && many
            Box(Modifier.offset(x0 - 4.dp, top - 4.dp).size(total + 8.dp, cw * 1.4f + 8.dp).then(if (active) Modifier.border(2.dp, Gold, RoundedCornerShape(8.dp)) else Modifier))
            cards.forEachIndexed { k, (c, seq) ->
                key(seq) {
                    val cxk = x0 + step * k
                    TableCard(c, cw, true, DpOffset(shoe.x - cxk, shoe.y - top), animate = !vm.instant, durationMs = pitchMs, modifier = Modifier.offset(cxk, top).graphicsLayer { rotationZ = if (h.doubled && k == 2) 90f else 0f })
                }
            }
            val y2 = top + cw * 1.4f + 6.dp
            Row(Modifier.offset(cx - 60.dp, y2).width(120.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
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
        // Side bet results.
        val notes = listOfNotNull(
            gs.perfectPairsResult?.takeIf { vm.shown > gs.roundStartSeq + 1 }?.let { "PP: $it" },
            vm.tpResult?.takeIf { vm.shown > gs.roundStartSeq + 2 }?.let { "21+3: $it" },
        )
        if (notes.isNotEmpty()) Text(notes.joinToString("   "), fontSize = 12.sp, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.width(w).offset(y = top - 20.dp))
    }
    vm.hint?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink, textAlign = TextAlign.Center, modifier = Modifier.offset(w / 2 - 110.dp, top - 34.dp).width(220.dp).background(Gold, RoundedCornerShape(50)).padding(vertical = 4.dp)) }
    if (vm.gs.phase == Phase.INSURANCE && !vm.busy) {
        Text(if (gs.hands.firstOrNull()?.isBlackjack == true) "Even money?" else "Insurance?", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Gold, textAlign = TextAlign.Center, modifier = Modifier.width(w).offset(y = top - 36.dp))
    }
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
        Column(
            Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.86f))
                .border(1.5.dp, if (b.amount > 0) Gold else Cream.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (b.title.isNotEmpty()) Text(b.title, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = TitleFont, color = if (b.amount > 0) Gold else Cream)
            Text(if (b.amount > 0) "+${money(b.amount)}" else if (b.amount < 0) "-${money(-b.amount)}" else "Even", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (b.amount > 0) GoodGreen else if (b.amount < 0) BadRed else Cream)
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
    Box(Modifier.fillMaxSize().padding(bottom = 150.dp), contentAlignment = Alignment.BottomCenter) {
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
                        GoldButton("Deal", Modifier.weight(1.4f), enabled = vm.bet >= vm.settings.tableMin && vm.bet <= vm.bankroll) { vm.deal() }
                    }
                }
            }
        }
    }
}
