package com.hilotrainer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.Cast
import com.hilotrainer.engine.CastMember
import com.hilotrainer.engine.CasualDealers
import com.hilotrainer.engine.Extra
import com.hilotrainer.engine.Hair
import com.hilotrainer.engine.Hat
import com.hilotrainer.engine.Look
import com.hilotrainer.engine.Outcome
import com.hilotrainer.engine.Phase
import com.hilotrainer.engine.Run
import com.hilotrainer.engine.ScoreEvent
import com.hilotrainer.engine.handTotal
import kotlin.math.abs

/** Each ante is a different room, with its own felt, name and mood. */
class Venue(val name: String, val tag: String, val base: Color, val light: Color, val print: Color, val bg: Color)

object Venues {
    val ALL = listOf(
        Venue("The Back Room", "Sawdust, cheap cigars, fair odds", Color(0xFF0F5A37), Color(0xFF1C8A55), Color(0xFFE9C46A), Color(0xFF0E2A1D)),
        Venue("Riverboat", "Slow water, fast cards", Color(0xFF123C78), Color(0xFF2B63B5), Color(0xFFE9D9A6), Color(0xFF0B1B36)),
        Venue("Strip Lounge", "Neon outside, nerves inside", Color(0xFF6E1420), Color(0xFFA32638), Color(0xFFF3D78A), Color(0xFF2A0A10)),
        Venue("High Roller Suite", "Nobody here checks the clock", Color(0xFF3E1A5E), Color(0xFF64308F), Color(0xFFE6C9FF), Color(0xFF1D0C2C)),
        Venue("The Vault", "Quiet money, loud stakes", Color(0xFF15181C), Color(0xFF2E343B), Color(0xFFC9A553), Color(0xFF0A0C0E)),
        Venue("The Penthouse", "The house always wins. Tonight it might not.", Color(0xFF3A2A08), Color(0xFF7A5A14), Color(0xFFFFE08A), Color(0xFF1A1204)),
    )
    fun of(ante: Int): Venue = ALL[(ante - 1).coerceIn(0, ALL.lastIndex)]
}

class RunDealer(val name: String, val look: Look, val boss: Boolean)

private val BOSS_TRIM = listOf(0xFFB71C1C, 0xFF6A1B9A, 0xFF1565C0, 0xFF2E7D32, 0xFFEF6C00, 0xFF00838F, 0xFFAD1457, 0xFF4E342E)

/** Normal blinds get a house dealer. A boss blind gets the boss, in a dark suit and shades. */
fun runDealer(r: Run): RunDealer {
    if (r.isBoss) {
        val trim = BOSS_TRIM[abs(r.boss.id.hashCode()) % BOSS_TRIM.size]
        return RunDealer(r.boss.name, Look(0xFFD9B08C, 0xFF111111, Hair.SLICK, 0xFF16161A, trim, hat = Hat.FEDORA, hatColor = 0xFF111111, extras = listOf(Extra.SUNGLASSES, Extra.GOLD_CHAIN)), true)
    }
    val d = CasualDealers.ALL[(r.ante - 1 + r.blind) % CasualDealers.ALL.size]
    return RunDealer(d.name, d.look, false)
}

/** Two regulars sit beside you. They change with the room and the blind. */
fun runSeats(r: Run): List<CastMember> {
    val n = Cast.ALL.size
    val a = (r.ante * 7 + r.blind * 5) % n
    var b = (r.ante * 11 + r.blind * 3 + 9) % n
    if (b == a) b = (b + 1) % n
    return listOf(Cast.ALL[a], Cast.ALL[b])
}

private object RunTalk {
    val GREET = listOf("Welcome. Pick your stake.", "Evening. Bets, please.", "Cards are fresh. Good luck.", "Take a seat. Let's play.")
    val NEXT = listOf("Bets, please.", "Place your bet.", "Next hand.", "Whenever you're ready.")
    val YOUR_MOVE = listOf("Your move.", "Hit or stand?", "Take your time.", "Decision time.")
    val WIN = listOf("Nicely played.", "You got me there.", "Pay the player.", "Chips to you.", "Sharp.")
    val LOSE = listOf("House takes it.", "Better luck next hand.", "That one's mine.", "Tough break.")
    val BUST = listOf("Dealer busts. Pay the table.", "Over twenty-one. Ouch.", "Too many. Paying out.")
    val BJ = listOf("Blackjack! Beautiful.", "A natural. Pays big.", "Twenty-one on the deal. Wow.")
    val PUSH = listOf("Push. Nobody moves.", "Tie. Chips stay put.")
    val BOT_WIN = listOf("Yes!", "Mine.", "Take that.", "Come to papa.", "Easy money.")
    val BOT_LOSE = listOf("Ugh.", "Of course.", "Dealer always.", "Next one.", "Figures.")
    val BOT_BUST = listOf("Too many.", "Oof, bust.", "Greedy me.")
    val BOT_BJ = listOf("Blackjack!", "Twenty-one!")
    val BOT_PUSH = listOf("Push.", "Breaks even.")
    val BOT_HI = listOf("Evening.", "Hey there.", "Deal me in.", "Feeling lucky.")
    fun pick(l: List<String>, seed: Int) = l[abs(seed) % l.size]
}

private fun moodFor(o: Outcome?): Mood = when (o) {
    Outcome.BLACKJACK -> Mood.CHEER
    Outcome.WIN -> Mood.HAPPY
    Outcome.LOSE, Outcome.BUST -> Mood.SAD
    else -> Mood.NEUTRAL
}

private fun DrawScope.printSmile(text: String, y0: Float, sag: Float, color: Color) {
    val w = size.width
    val path = Path().apply { moveTo(w * 0.34f, y0); quadraticBezierTo(w * 0.5f, y0 + sag * 2f, w * 0.66f, y0) }
    drawPath(path, color.copy(alpha = 0.28f), style = Stroke(width = 1.2.dp.toPx()))
    runCatching {
        val ap = path.asAndroidPath()
        val len = android.graphics.PathMeasure(ap, false).length
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true; this.color = color.copy(alpha = 0.55f).toArgb(); textSize = 8.sp.toPx(); letterSpacing = 0.08f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        }
        drawContext.canvas.nativeCanvas.drawTextOnPath(text, ap, ((len - paint.measureText(text)) / 2f).coerceAtLeast(0f), -3.dp.toPx(), paint)
    }
}

private fun Modifier.venueFelt(v: Venue, boss: Boolean): Modifier = drawBehind {
    drawRect(v.base)
    drawRect(Brush.radialGradient(listOf(v.light.copy(alpha = 0.9f), v.base.copy(alpha = 0f)), center = Offset(size.width / 2, size.height * 0.5f), radius = maxOf(size.width, size.height) * 0.7f))
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = if (boss) 0.72f else 0.5f)), center = Offset(size.width / 2, size.height / 2), radius = maxOf(size.width, size.height) * 0.85f))
    if (boss) drawRect(Brush.verticalGradient(listOf(Color(0x44B71C1C), Color.Transparent)), size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.4f))
    val rnd = kotlin.random.Random(7)
    repeat(150) { drawCircle(Color.White.copy(alpha = 0.025f), 1.2f, Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)) }
    // Woven cloth: a fine crosshatch over the whole felt.
    val weave = 4.dp.toPx()
    var wx = 0f
    while (wx < size.width + size.height) {
        drawLine(Color.Black.copy(alpha = 0.06f), Offset(wx, 0f), Offset(wx - size.height, size.height), 1f)
        drawLine(Color.White.copy(alpha = 0.025f), Offset(wx - size.height, 0f), Offset(wx, size.height), 1f)
        wx += weave
    }
    // A warm lamp pool above the dealer and a big faint emblem in the middle of the cloth.
    drawRect(Brush.radialGradient(listOf(Color(0x33FFE9B0), Color.Transparent), center = Offset(size.width / 2, size.height * 0.18f), radius = size.width * 0.55f))
    val emblem = Offset(size.width / 2, size.height * 0.72f)
    drawCircle(v.print.copy(alpha = 0.07f), size.width * 0.2f, emblem, style = Stroke(2.dp.toPx()))
    drawCircle(v.print.copy(alpha = 0.05f), size.width * 0.15f, emblem, style = Stroke(1.dp.toPx()))
    // Gold inlay running around the edge of the felt.
    val inset = 5.dp.toPx()
    drawRoundRect(v.print.copy(alpha = 0.35f), Offset(inset, inset), androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2), androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()), style = Stroke(1.2.dp.toPx()))
    drawRoundRect(v.print.copy(alpha = 0.16f), Offset(inset + 4.dp.toPx(), inset + 4.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - inset * 2 - 8.dp.toPx(), size.height - inset * 2 - 8.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx()))
    printSmile("BLACKJACK PAYS 3 TO 2", size.height * 0.47f, size.height * 0.04f, v.print)
    printSmile("DEALER STANDS ON 17", size.height * 0.535f, size.height * 0.04f, v.print)
    // The betting arc in front of the player.
    drawArc(v.print.copy(alpha = 0.18f), 200f, 140f, false, topLeft = Offset(size.width * 0.12f, size.height * 0.56f), size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.9f), style = Stroke(1.2.dp.toPx()))
}

@Composable
private fun Bubble(text: String, modifier: Modifier = Modifier, dark: Boolean = false) {
    Crossfade(text, modifier, label = "bubble") { t ->
        if (t.isNotEmpty()) Text(
            t, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, color = if (dark) Cream else Ink, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (dark) Color(0xCC000000) else Cream.copy(alpha = 0.95f)).padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun Shoe(w: Dp, dealt: Int, total: Int) {
    val left = if (total <= 0) 1f else (1f - dealt.toFloat() / total).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(w, w * 0.72f)) {
            val cw = size.width; val ch = size.height
            drawRoundRect(Color(0xFF1B1209), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()))
            drawRoundRect(Color(0xFF3A2A18), topLeft = Offset(cw * 0.06f, ch * 0.08f), size = androidx.compose.ui.geometry.Size(cw * 0.88f, ch * 0.84f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            val stack = (cw * 0.62f * (0.3f + 0.7f * left))
            drawRect(Color(0xFFF2ECDD), topLeft = Offset(cw * 0.12f, ch * 0.2f), size = androidx.compose.ui.geometry.Size(stack, ch * 0.5f))
            drawRect(Color(0xFFB71C1C), topLeft = Offset(cw * 0.12f, ch * 0.2f), size = androidx.compose.ui.geometry.Size(cw * 0.1f, ch * 0.5f))
            drawLine(Gold.copy(alpha = 0.8f), Offset(cw * 0.06f, ch * 0.92f), Offset(cw * 0.94f, ch * 0.92f), 1.5.dp.toPx())
        }
        Text("${(left * 100).toInt()}%", fontSize = 9.sp, color = Cream.copy(alpha = 0.7f))
    }
}

/** Small cards for a computer player, with the total on top. */
@Composable
private fun BotCards(cards: List<Card>, cw: Dp, maxW: Dp, seat: Int, extraDelay: Int) {
    if (cards.isEmpty()) { Box(Modifier.height(cw * 1.4f)); return }
    val step = if (cards.size <= 1) 0.dp else minOf(cw * 0.6f, (maxW - cw) / (cards.size - 1)).coerceAtLeast(cw * 0.25f)
    Box(Modifier.width(cw + step * (cards.size - 1)).height(cw * 1.4f)) {
        cards.forEachIndexed { i, c ->
            val d = if (i < 2) (i * 4 + seat) * 90 else 300 + (i - 2) * 380 + extraDelay
            androidx.compose.runtime.key(c, i) { Pop(Modifier.offset(step * i, 0.dp), d) { PlayingCard(c, cw) } }
        }
    }
}

@Composable
private fun TotalPill(cards: List<Card>, result: Outcome?) {
    if (cards.isEmpty()) return
    val (t, soft) = handTotal(cards)
    val txt = when { t > 21 -> "Bust"; cards.size == 2 && t == 21 -> "21!"; soft && t < 21 -> "${t - 10}/$t"; else -> "$t" }
    val bg = when (result) { Outcome.WIN, Outcome.BLACKJACK -> GoodGreen; Outcome.LOSE, Outcome.BUST -> BadRed; else -> Cream }
    Text(txt, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp))
}

@Composable
private fun Flash(key: Int, color: Color) {
    val a = remember(key) { Animatable(if (key == 0) 0f else 0.55f) }
    LaunchedEffect(key) { if (key != 0) a.animateTo(0f, tween(900)) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = a.value }.background(Brush.radialGradient(listOf(Color.Transparent, color))))
}

@Composable
private fun ScorePop(key: Int, total: Int, modifier: Modifier) {
    if (key == 0 || total <= 0) return
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { kotlinx.coroutines.delay(500); p.animateTo(1f, tween(1500)) }
    Text(
        "+${"%,d".format(total)}", fontSize = 34.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = Gold,
        modifier = modifier.graphicsLayer { alpha = if (p.value <= 0f) 0f else (1f - p.value).coerceIn(0f, 1f); translationY = -p.value * 60.dp.toPx(); val s = 1f + 0.3f * (1f - abs(p.value * 2f - 1f)); scaleX = s; scaleY = s },
    )
}

/** The whole table: a dealer behind it, two regulars beside you, and your own hands in front. */
@Composable
internal fun RunTable(vm: RunVm, r: Run, modifier: Modifier = Modifier) {
    vm.rev
    val gs = r.gs
    val venue = Venues.of(r.ante)
    val dealer = remember(r.ante, r.blind, r.boss.id) { runDealer(r) }
    val seats = remember(r.ante, r.blind) { runSeats(r) }
    val round = gs.hands.isNotEmpty()
    val over = round && !r.inRound
    val evs: List<ScoreEvent> = if (over) r.lastScore else emptyList()
    val key = evs.firstOrNull()?.key ?: 0
    val showHole = gs.holeRevealed || gs.phase == Phase.ROUND_OVER
    val won = evs.any { it.total > 0 }
    val dBust = gs.dealerCards.isNotEmpty() && handTotal(gs.dealerCards).first > 21
    val extraBot = gs.bots.maxOfOrNull { (it.cards.size - 2).coerceAtLeast(0) } ?: 0

    val dealerQuote = when {
        r.inRound -> RunTalk.pick(RunTalk.YOUR_MOVE, r.handsLeft * 7 + gs.cardsDealt)
        over -> when {
            evs.any { it.outcome == Outcome.BLACKJACK } -> RunTalk.pick(RunTalk.BJ, key)
            dBust && won -> RunTalk.pick(RunTalk.BUST, key)
            won -> RunTalk.pick(RunTalk.WIN, key)
            evs.any { it.outcome == Outcome.PUSH } -> RunTalk.pick(RunTalk.PUSH, key)
            else -> RunTalk.pick(RunTalk.LOSE, key)
        }
        r.handsLeft == r.handsMax -> if (dealer.boss) "I'm ${dealer.name}. ${r.boss.desc}" else RunTalk.pick(RunTalk.GREET, r.ante * 3 + r.blind)
        else -> RunTalk.pick(RunTalk.NEXT, r.handsLeft)
    }
    val dealerMood = when {
        !over -> Mood.NEUTRAL
        evs.any { it.outcome == Outcome.BLACKJACK } -> Mood.SAD
        dBust || won -> Mood.SAD
        else -> Mood.HAPPY
    }

    Box(modifier.clip(RoundedCornerShape(22.dp)).woodRail().padding(5.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).venueFelt(venue, r.isBoss)) {
            val w = maxWidth; val h = maxHeight
            // The dealer, behind the table.
            val dav = minOf(h * 0.21f, w * 0.27f)
            val dTop = h * 0.01f
            val dcw = minOf(h * 0.115f, w * 0.13f)
            Avatar(dealer.look, dav, mood = dealerMood, talking = dealerQuote.isNotEmpty(), dealer = true, name = dealer.name, gazeY = 0.6f, modifier = Modifier.offset(w / 2 - dav / 2, dTop))
            Column(Modifier.offset(8.dp, dTop + 2.dp).width(w / 2 - dav / 2 - 12.dp), horizontalAlignment = Alignment.Start) {
                Shoe(34.dp, gs.cardsDealt, gs.totalCards)
                Text(dealer.name, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (dealer.boss) BossTag else Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (dealer.boss) "Boss" else "Dealer", fontSize = 9.sp, color = Cream.copy(alpha = 0.6f))
            }
            Bubble(dealerQuote, Modifier.offset(w / 2 + dav / 2 + 2.dp, dTop + 26.dp).width(w / 2 - dav / 2 - 8.dp))
            // Dealer cards.
            val dHandTop = dTop + dav - h * 0.012f
            Box(Modifier.offset(0.dp, dHandTop).width(w), contentAlignment = Alignment.TopCenter) {
                val cards = gs.dealerCards
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (cards.isNotEmpty()) TotalPill(if (showHole) cards else listOf(cards.first()), null) else Box(Modifier.height(18.dp))
                    val step = if (cards.size <= 1) 0.dp else minOf(dcw * 0.62f, (w * 0.5f - dcw) / (cards.size - 1)).coerceAtLeast(dcw * 0.25f)
                    Box(Modifier.width(dcw + step * (cards.size - 1).coerceAtLeast(0)).height(dcw * 1.4f)) {
                        cards.forEachIndexed { i, c ->
                            val d = if (i < 2) (if (i == 0) 3 else 7) * 90 else 500 + extraBot * 380 + (i - 2) * 420
                            androidx.compose.runtime.key(c, i) { Pop(Modifier.offset(step * i, 0.dp), d) { PlayingCard(c, dcw, faceDown = i == 1 && !showHole) } }
                        }
                    }
                }
            }
            // Regulars on both sides.
            val ba = minOf(h * 0.17f, w * 0.2f)
            val bTop = h * 0.27f
            val bcw = minOf(h * 0.072f, w * 0.085f)
            val seatW = w * 0.32f
            for (s in 0..1) {
                val m = seats[s]
                val cx = if (s == 0) w * 0.17f else w * 0.83f
                val hand = gs.bots.getOrNull(s)
                val res = if (over) gs.botResults.getOrNull(s)?.outcome else null
                val quote = when {
                    res != null -> RunTalk.pick(when (res) { Outcome.BLACKJACK -> RunTalk.BOT_BJ; Outcome.WIN -> RunTalk.BOT_WIN; Outcome.BUST -> RunTalk.BOT_BUST; Outcome.PUSH -> RunTalk.BOT_PUSH; else -> RunTalk.BOT_LOSE }, key + s * 13 + 1)
                    !round && r.handsLeft == r.handsMax -> RunTalk.pick(RunTalk.BOT_HI, r.ante + r.blind * 5 + s * 3)
                    else -> ""
                }
                Avatar(m.look, ba, mood = moodFor(res), talking = quote.isNotEmpty() && !over, name = m.name, gazeX = if (s == 0) 0.6f else -0.6f, gazeY = 0.3f, modifier = Modifier.offset(cx - ba / 2, bTop))
                Box(Modifier.offset(cx - seatW / 2, bTop + ba - 4.dp).width(seatW), contentAlignment = Alignment.TopCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (hand != null && hand.cards.isNotEmpty()) TotalPill(hand.cards, res) else Box(Modifier.height(18.dp))
                        BotCards(hand?.cards ?: emptyList(), bcw, seatW, s, 0)
                        Text(m.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.height(30.dp), contentAlignment = Alignment.TopCenter) { Bubble(quote, dark = true) }
                    }
                }
            }
            // You.
            val pTop = h * 0.665f
            val many = gs.hands.size > 1
            val cwP = minOf(w * 0.17f, (h - pTop - 36.dp) / 1.4f, 76.dp).coerceAtLeast(34.dp)
            if (!round) {
                Column(Modifier.offset(0.dp, pTop + 4.dp).width(w), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(cwP * 0.95f).border(1.5.dp, venue.print.copy(alpha = 0.5f), CircleShape).background(Color(0x22000000), CircleShape), contentAlignment = Alignment.Center) {
                        CasualChipStack(vm.stakePick * Run.UNIT, 24.dp)
                    }
                    Text("Stake ${vm.stakePick}: wins score x${vm.stakePick} chips", fontSize = 11.sp, color = Cream.copy(alpha = 0.8f), modifier = Modifier.padding(top = 3.dp))
                }
            } else {
                Box(Modifier.offset(0.dp, pTop).width(w), contentAlignment = Alignment.TopCenter) {
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        gs.hands.forEachIndexed { i, h2 ->
                            val ev = if (!r.inRound) r.lastScore.getOrNull(i) else null
                            Column(Modifier.then(Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                                Hand(
                                    if (many) "HAND ${i + 1}" else "YOU", h2.cards, if (many) cwP * 0.78f else cwP, if (many) (w - 40.dp) / gs.hands.size else w - 20.dp,
                                    active = r.inRound && i == gs.activeHand, result = ev, delayOf = { j -> if (j < 2) (j * 4 + 2) * 90 else 0 },
                                )
                            }
                        }
                    }
                }
                if (!many) Column(Modifier.offset(10.dp, pTop + 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CasualChipStack((if (r.inRound) r.stake else vm.stakePick) * Run.UNIT, 20.dp)
                    Text("\$${(if (r.inRound) r.stake else vm.stakePick) * Run.UNIT}", fontSize = 10.sp, color = Cream.copy(alpha = 0.8f))
                }
            }
            if (r.countVisible) CountMeter(gs.runningCount, gs.trueCountExact, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            Flash(key, if (won) Color(0xAAE9C46A) else Color(0x88B71C1C))
            ScorePop(key, evs.sumOf { it.total }, Modifier.align(Alignment.TopCenter).offset(0.dp, pTop))
        }
    }
}

private val BossTag = Color(0xFFE05A5A)

/** The dealer or boss and the room, shown above blind select, the cash out and the shop. */
@Composable
internal fun VenueHeader(r: Run, line: String, title: String = "") {
    val v = Venues.of(r.ante)
    val d = remember(r.ante, r.blind, r.boss.id) { runDealer(r) }
    Row(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.horizontalGradient(listOf(v.base, v.base.copy(alpha = 0.5f)))).border(1.dp, v.print.copy(alpha = 0.4f), RoundedCornerShape(18.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
    ) {
        Avatar(d.look, 72.dp, mood = Mood.NEUTRAL, talking = true, dealer = true, name = d.name, gazeY = 0.3f)
        Column(Modifier.weight(1f), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)) {
            Text(title.ifEmpty { v.name }, fontSize = 17.sp, fontFamily = TitleFont, fontWeight = FontWeight.Black, color = v.print)
            Text(d.name + if (d.boss) " (boss)" else ", dealer", fontSize = 11.sp, color = if (d.boss) BossTag else Cream.copy(alpha = 0.7f))
            Bubble(line)
        }
    }
}
