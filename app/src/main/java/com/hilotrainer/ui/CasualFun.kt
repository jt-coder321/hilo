package com.hilotrainer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Tiers
import com.hilotrainer.engine.PowerUp
import com.hilotrainer.engine.Rival
import com.hilotrainer.engine.Story
import com.hilotrainer.engine.NightState
import com.hilotrainer.engine.NextCard
import com.hilotrainer.engine.Wheel
import kotlinx.coroutines.launch

/** Small badges at the top of the table: the rush, the wheel, an accepted dare. */
@Composable
fun FunBadges(vm: CasualViewModel, onWheel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (vm.rushLeft > 0) {
            val pulse = if (CasualAnim.live) rememberInfiniteTransition(label = "rush").animateFloat(0.75f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p").value else 1f
            Text(
                "RUSH x${vm.rushLeft}  wins +50%", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Ink,
                modifier = Modifier.graphicsLayer { alpha = pulse }.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFFF8A65), Color(0xFFFFD54F)))).padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
        if (vm.wheelReady) {
            Text(
                "Spin the wheel!", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ink,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFFF6DA8C), Gold))).clickable(onClick = onWheel).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        vm.nightBadge?.let { b ->
            Text(b, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp).clip(RoundedCornerShape(50)).background(Cream).padding(horizontal = 9.dp, vertical = 3.dp))
        }
        val d = vm.dare
        if (vm.dareOn && d != null) {
            Text(if (d.duel) "Duel ${money(d.stake)} vs ${d.name}" else "Dare ${money(d.stake)} vs ${d.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Cream, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC6A1B1B)).padding(horizontal = 9.dp, vertical = 3.dp))
        }
    }
}

/** A dare from another player, as a compact row at the end of the chat. Take it for a side bet on this hand, or pass. */
@Composable
fun DareRow(vm: CasualViewModel, modifier: Modifier = Modifier) {
    val d = vm.dare ?: return
    if (vm.dareOn || !vm.canBet) return
    val can = vm.bankroll >= vm.bet + d.stake
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xF0101A14)).border(1.5.dp, Gold, RoundedCornerShape(14.dp)).padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            // One line, so it always fits in the chat zone on the cover screen.
            Text(if (d.duel) "${d.name}: head to head, ${money(d.stake)}" else "${d.name} dares you: ${money(d.stake)} on this hand", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (d.duel) Text(Rival.scoreText(vm.rivalWins, vm.rivalLosses, d.name), fontSize = 10.sp, color = Cream.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Pass", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Cream, modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Cream.copy(alpha = 0.5f), RoundedCornerShape(50)).clickable { vm.declineDare() }.padding(horizontal = 12.dp, vertical = 6.dp))
        Text("Take it", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.graphicsLayer { alpha = if (can) 1f else 0.4f }.clip(RoundedCornerShape(50)).background(Gold).clickable(enabled = can) { vm.acceptDare() }.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** A cocktail on your rail. */
@Composable
fun DrinkGlass(modifier: Modifier = Modifier) {
    val t = if (CasualAnim.live) rememberInfiniteTransition(label = "drink").animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "b").value else 0.3f
    Canvas(modifier.size(24.dp, 34.dp).semantics { contentDescription = "A drink" }) {
        val w = size.width; val h = size.height
        val glass = Path().apply { moveTo(w * 0.08f, h * 0.18f); lineTo(w * 0.92f, h * 0.18f); lineTo(w * 0.78f, h * 0.95f); lineTo(w * 0.22f, h * 0.95f); close() }
        drawPath(glass, Color.White.copy(alpha = 0.18f))
        val liquid = Path().apply { moveTo(w * 0.13f, h * 0.36f); lineTo(w * 0.87f, h * 0.36f); lineTo(w * 0.77f, h * 0.92f); lineTo(w * 0.23f, h * 0.92f); close() }
        drawPath(liquid, Brush.verticalGradient(listOf(Color(0xFFFFB74D), Color(0xFFE65100))))
        drawRoundRect(Color(0xFFB3E5FC).copy(alpha = 0.8f), Offset(w * 0.3f, h * 0.45f), Size(w * 0.2f, w * 0.2f), androidx.compose.ui.geometry.CornerRadius(3f))
        for (i in 0..2) drawCircle(Color.White.copy(alpha = 0.7f * (1f - ((t + i * 0.33f) % 1f))), 1.6f, Offset(w * (0.4f + 0.12f * i), h * (0.85f - 0.4f * ((t + i * 0.33f) % 1f))))
        drawLine(Color(0xFFD32F2F), Offset(w * 0.62f, h * 0.05f), Offset(w * 0.55f, h * 0.7f), 2.5f)
        drawPath(glass, Color.White.copy(alpha = 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
    }
}

/** Perks and surprises appear for a few seconds, stacked under the achievement toast. */
@Composable
fun PerkToasts(vm: CasualViewModel) {
    val p = vm.perks.firstOrNull() ?: return
    LaunchedEffect(p.id) { if (!vm.instant) { kotlinx.coroutines.delay(3600); vm.perks.remove(p) } }
    Box(Modifier.fillMaxSize().padding(top = 112.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xF0101A14)).border(1.5.dp, Gold, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Gold, textAlign = TextAlign.Center)
            Text(p.desc, fontSize = 12.sp, color = Cream, textAlign = TextAlign.Center)
        }
    }
}

private val WheelColors = listOf(Color(0xFFC62828), Color(0xFF1B1B1F), Color(0xFF2E7D32), Color(0xFF1B1B1F), Color(0xFF6A2C91), Color(0xFF1B1B1F), Color(0xFF1E63C6), Color(0xFFF9A825))

@Composable
private fun Scrim(onTap: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(enabled = onTap != null) { onTap?.invoke() }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.clip(RoundedCornerShape(22.dp)).background(FeltDark).border(1.5.dp, Gold, RoundedCornerShape(22.dp)).clickable(enabled = false) {}.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

/** The bonus wheel: spin, watch it slow down, collect. */
@Composable
fun WheelDialog(vm: CasualViewModel, onClose: () -> Unit) {
    val rot = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val min = vm.settings.tableMin
    val idx = vm.wheelIdx
    Scrim {
        Text("BONUS WHEEL", fontSize = 18.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = Gold)
        Text("Three wins in a row. One free spin.", fontSize = 12.sp, color = Cream)
        Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(250.dp).graphicsLayer { rotationZ = rot.value }) {
                Canvas(Modifier.fillMaxSize()) {
                    val sweep = 360f / Wheel.UNITS.size
                    Wheel.UNITS.indices.forEach { i ->
                        drawArc(WheelColors[i], -90f + i * sweep - sweep / 2, sweep, true)
                        drawArc(Color.White.copy(alpha = 0.35f), -90f + i * sweep - sweep / 2, sweep, true, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                    }
                    drawCircle(Color.Black.copy(alpha = 0.25f), size.minDimension * 0.5f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(6f))
                }
                Wheel.UNITS.indices.forEach { i ->
                    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = i * 360f / Wheel.UNITS.size }) {
                        Text(money(Wheel.UNITS[i] * min), fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (i == 7) Ink else Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp))
                    }
                }
            }
            Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFFF6DA8C), Gold))).border(2.dp, Ink, CircleShape))
            Canvas(Modifier.size(270.dp)) {
                val p = Path().apply { moveTo(center.x - 12.dp.toPx(), 0f); lineTo(center.x + 12.dp.toPx(), 0f); lineTo(center.x, 22.dp.toPx()); close() }
                drawPath(p, Color.White); drawPath(p, Ink, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            }
        }
        if (idx != null && !spinning) {
            Text("+${money(Wheel.UNITS[idx] * min)}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = GoodGreen)
            GoldButton("Collect", Modifier.width(180.dp)) { vm.collectWheel(); onClose() }
        } else {
            GoldButton(if (spinning) "Spinning..." else "SPIN", Modifier.width(180.dp), enabled = !spinning) {
                val i = vm.spinWheel()
                if (i >= 0) {
                    spinning = true
                    scope.launch {
                        val target = 360f * 5 + (360f - i * 360f / Wheel.UNITS.size) + ((i * 37) % 21 - 10)
                        if (vm.instant) rot.snapTo(target) else rot.animateTo(target, tween(4200, easing = FastOutSlowInEasing))
                        spinning = false
                    }
                }
            }
        }
        if (idx == null && !spinning) Text("Not now", fontSize = 13.sp, color = Cream.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(6.dp))
    }
}

/** Three face down cards after claiming the daily bonus: pick one. */
@Composable
fun LuckyDialog(vm: CasualViewModel) {
    val prizes = vm.luckyPrizes ?: return
    val pick = vm.luckyPick
    Scrim {
        Text("LUCKY CARD", fontSize = 18.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = Gold)
        Text(if (pick == null) "Pick a card for a bonus." else "Your bonus is in your stack.", fontSize = 13.sp, color = Cream)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            prizes.forEachIndexed { i, p ->
                val flip by animateFloatAsState(if (pick != null) 180f else 0f, tween(450), label = "lucky$i")
                Box(
                    Modifier.size(84.dp, 118.dp).graphicsLayer { rotationY = flip; cameraDistance = 14f * density }.clickable(enabled = pick == null) { vm.pickLucky(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (flip < 90f) CardBack(84.dp) else Box(
                        Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }.clip(RoundedCornerShape(8.dp)).background(if (i == pick) Gold else Cream.copy(alpha = 0.85f)).border(2.dp, if (i == pick) Color.White else Color.Transparent, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("+${money(p)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink) }
                }
            }
        }
        if (pick != null) GoldButton("Nice", Modifier.width(160.dp)) { vm.closeLucky() }
    }
}

/** The rooms of the casino: pick where you play. Locked rooms say how to get in. */
@Composable
fun TierList(vm: CasualViewModel, enabled: Boolean) {
    val s = vm.settings
    val peak = maxOf(vm.stats.biggestBankroll, vm.bankroll)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Casino floor", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
        Text("Your best bankroll opens the bigger rooms. The pit boss may also invite you early.", fontSize = 12.sp, color = Cream.copy(alpha = 0.65f))
        Tiers.ALL.forEach { t ->
            val open = vm.tierUnlocked(t)
            val on = s.tableMin == t.min
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) Gold.copy(alpha = 0.22f) else Color(0x14FFFFFF))
                    .border(if (on) 2.dp else 0.dp, if (on) Gold else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable(enabled = open && enabled && !on) { vm.update(s.copy(tableMin = t.min)) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(if (open) Gold else Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                    Text(if (open) "$" else "·", fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (open) Ink else Cream.copy(alpha = 0.7f))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${t.name}  ${money(t.min)} to ${money(t.min * 100)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (open) Cream else Cream.copy(alpha = 0.6f))
                    Text(t.blurb, fontSize = 12.sp, color = Cream.copy(alpha = 0.7f))
                    if (!open) Text("Locked. Reach a ${money(t.unlockAt)} bankroll (best so far ${money(peak)}).", fontSize = 12.sp, color = Gold)
                    else if (on) Text("You are here", fontSize = 12.sp, color = Gold)
                }
            }
        }
    }
}

/**
 * A small tap-to-toggle Hi-Lo counter that lives in the top bar, above the felt, so it can never cover the dealer, the pit boss,
 * a player, the cards or anyone's words. Off, it is a faint "Count" tag. On, it shows the running and true count on one line.
 */
@Composable
fun CountChip(vm: CasualViewModel, modifier: Modifier = Modifier) {
    val on = vm.settings.showCount
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.clip(shape).background(Color(if (on) 0xE6101512 else 0x66101512)).border(1.dp, Gold.copy(alpha = if (on) 0.7f else 0.35f), shape)
            .clickable { vm.update(vm.settings.copy(showCount = !on)) }
            .semantics { contentDescription = if (on) "Card count on, running ${vm.countRun}, true ${"%.1f".format(vm.countTrue)}, ${vm.decksLeft} decks left. Tap to hide" else "Card count off. Tap to show" }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!on) {
            Text("Count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold.copy(alpha = 0.85f), maxLines = 1)
        } else {
            val rc = vm.countRun
            val tc = vm.countTrue
            val col = { v: Double -> when { v > 0.49 -> Color(0xFF8EE6A0); v < -0.49 -> Color(0xFFFF9A8A); else -> Cream } }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("RUN ", fontSize = 9.sp, letterSpacing = 1.sp, color = Gold)
                    Text(signed(rc.toDouble(), 0), fontSize = 15.sp, fontWeight = FontWeight.Black, color = col(rc.toDouble() / 4), maxLines = 1)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("TRUE ", fontSize = 9.sp, letterSpacing = 1.sp, color = Gold)
                    Text(signed(tc, 1), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = col(tc), maxLines = 1)
                }
            }
        }
    }
}

private fun signed(v: Double, digits: Int): String {
    val t = if (digits == 0) v.toInt().toString() else "%.${digits}f".format(v)
    return if (v > 0 && !t.startsWith("+")) "+$t" else t
}

/** Power-ups you hold, in a thin strip above the buttons. Casual only: they never touch training. */
@Composable
fun PowerRow(vm: CasualViewModel) {
    val any = vm.power.sum() > 0 || vm.armedRefund || vm.armedCover
    if (!any) return
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF2A1A10)).padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("POWER-UPS", fontSize = 10.sp, letterSpacing = 1.sp, color = Gold, modifier = Modifier.semantics { contentDescription = "Power-ups, casual only" })
        PowerUp.entries.forEach { p ->
            val n = vm.power[p.ordinal]
            val armed = (p == PowerUp.REFUND && vm.armedRefund) || (p == PowerUp.COVER && vm.armedCover) || (p == PowerUp.PEEK && vm.peeked != null)
            if (n > 0 || armed) {
                Text(
                    if (armed) "${p.short} on" else "${p.short} x$n", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (armed) Ink else Cream,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (armed) Gold else Color(0x33FFFFFF)).border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .clickable(enabled = !armed && n > 0) { vm.usePower(p) }.semantics { contentDescription = "${p.label}. ${p.desc}" }.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** The two casual mini game side bets. */
@Composable
fun MiniGamesDialog(vm: CasualViewModel) {
    if (!vm.miniOpen) return
    Scrim(onTap = { vm.miniOpen = false }) {
        Text("MINI GAMES", fontSize = 18.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = Gold)
        Text("Casual side bets. They never count toward training.", fontSize = 12.sp, color = Cream.copy(alpha = 0.75f), textAlign = TextAlign.Center)
        MiniRow("Lucky Ladies", "Your first two cards total 20. Any 20 pays 4 to 1, suited 9 to 1, matched 19 to 1, two queens of hearts 125 to 1.", vm.llBet) { vm.cycleLL() }
        MiniRow("Next Card", "Call your first card.", vm.ncBet) { vm.cycleNC() }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NextCard.CALLS.forEachIndexed { i, c ->
                val on = vm.ncCall == i
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) Gold else Color(0x22FFFFFF)).clickable { vm.callNC(i) }.padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(c, fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (on) Ink else Cream)
                    Text(NextCard.BLURB[i], fontSize = 10.sp, color = if (on) Ink else Cream.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
            }
        }
        GoldButton("Done", Modifier.width(160.dp)) { vm.miniOpen = false }
    }
}

@Composable
private fun MiniRow(title: String, desc: String, amount: Int, onTap: () -> Unit) {
    Row(Modifier.fillMaxWidth().widthIn(max = 380.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Cream)
            Text(desc, fontSize = 11.sp, color = Cream.copy(alpha = 0.7f))
        }
        Box(
            Modifier.size(52.dp).clickable(onClick = onTap).border(1.5.dp, Gold.copy(alpha = if (amount > 0) 0.9f else 0.45f), CircleShape).semantics { contentDescription = "$title bet ${if (amount == 0) "off" else money(amount)}. Tap to change" },
            contentAlignment = Alignment.Center,
        ) { if (amount > 0) CasualChipStack(amount, 32.dp) else Text("+", color = Gold.copy(alpha = 0.7f), fontSize = 22.sp) }
    }
}

/** The story nights and your rival. Casual only. */
@Composable
fun NightsTab(vm: CasualViewModel) {
    val unit = vm.settings.tableMin
    Text(Story.NAME.uppercase(), fontSize = 12.sp, letterSpacing = 2.sp, color = Gold)
    Text("Short evenings with a goal. Finish one to unlock the next and earn chips and a power-up. Casual only: nothing here touches training.", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f))
    val run = vm.night
    Story.chapters.forEachIndexed { i, c ->
        val unlocked = i <= vm.storyDone
        val done = i < vm.storyDone
        val active = run?.chapter == i
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (active) Gold.copy(alpha = 0.22f) else Color(0x14FFFFFF))
                .border(if (active) 2.dp else 0.dp, if (active) Gold else Color.Transparent, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Night ${i + 1}: ${c.title}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (unlocked) Cream else Cream.copy(alpha = 0.45f), modifier = Modifier.weight(1f))
                if (done) Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.clip(RoundedCornerShape(50)).background(Gold).padding(horizontal = 10.dp, vertical = 2.dp))
            }
            if (unlocked) {
                Text(c.intro, fontSize = 12.sp, color = Cream.copy(alpha = 0.8f))
                Text("Goal: ${Story.describe(c, unit)}. Reward ${money(c.rewardUnits * unit)} and a power-up.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                if (active && run != null) {
                    val (now, target) = Story.progress(c, run, vm.bankroll, unit)
                    Text(Story.badge(c, run, vm.bankroll, unit, i), fontSize = 12.sp, color = Cream)
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF))) {
                        Box(Modifier.fillMaxWidth((now.toFloat() / target).coerceIn(0f, 1f)).height(6.dp).background(Gold))
                    }
                    Text("Give up this night", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f), modifier = Modifier.clickable { vm.quitNight() }.padding(vertical = 4.dp))
                } else if (run == null) {
                    GoldButton(if (done) "Play again" else "Start", Modifier.width(140.dp), primary = !done) { vm.startNight(i) }
                }
            } else {
                Text("Finish Night $i to unlock.", fontSize = 12.sp, color = Cream.copy(alpha = 0.5f))
            }
        }
    }
    val rival = vm.rivalName
    Spacer(Modifier.height(6.dp))
    Text("YOUR RIVAL", fontSize = 12.sp, letterSpacing = 2.sp, color = Gold)
    if (rival == null) {
        Text("Nobody yet. Play a few hands and someone at the table will pick a fight.", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f))
    } else {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rival, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Cream)
                Text(Rival.scoreText(vm.rivalWins, vm.rivalLosses, rival), fontSize = 12.sp, color = Cream.copy(alpha = 0.75f))
            }
            Text("${vm.rivalWins} to ${vm.rivalLosses}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Gold)
        }
        Text("Head to head: whoever ends with the better hand wins the side stake. A bust loses, a blackjack beats any 21.", fontSize = 12.sp, color = Cream.copy(alpha = 0.65f))
    }
}
