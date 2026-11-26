package com.hilotrainer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Milestone
import com.hilotrainer.engine.Perks
import com.hilotrainer.engine.Run
import com.hilotrainer.engine.RunMeta
import com.hilotrainer.engine.RunPhase

private val Mono = FontFamily.Monospace
private val GoldCol = Color(0xFFFFD35A)

/** What a finished run earned: rank points, a possible rank up, goals ticked off and decks unlocked. */
class RunReward(val xp: Int, val before: Int, val after: Int, val goals: List<RunMeta.Goal>, val unlocks: List<String>) {
    val rankBefore: Int get() = RunMeta.rankOf(before)
    val rankAfter: Int get() = RunMeta.rankOf(after)
    val rankedUp: Boolean get() = rankAfter > rankBefore
}

fun milestoneText(m: Milestone?, ante: Int): String = when {
    ante >= Run.MAX_ANTE -> "Beat this boss to win the run"
    m == null -> ""
    else -> "Boss prize: ${m.label}"
}

private fun DrawScope.trophy(c: Offset, s: Float, tint: Color) {
    val ink = EdgeInk
    val cup = Path().apply {
        moveTo(c.x - s * 0.34f, c.y - s * 0.4f); lineTo(c.x + s * 0.34f, c.y - s * 0.4f)
        cubicTo(c.x + s * 0.34f, c.y + s * 0.1f, c.x + s * 0.18f, c.y + s * 0.2f, c.x, c.y + s * 0.22f)
        cubicTo(c.x - s * 0.18f, c.y + s * 0.2f, c.x - s * 0.34f, c.y + s * 0.1f, c.x - s * 0.34f, c.y - s * 0.4f); close()
    }
    for (side in listOf(-1f, 1f)) drawArc(ink, if (side < 0) 90f else -90f, 180f, false, Offset(c.x + side * s * 0.34f - s * 0.17f, c.y - s * 0.34f), androidx.compose.ui.geometry.Size(s * 0.34f, s * 0.34f), style = Stroke(s * 0.09f))
    drawPath(cup, ink, style = Stroke(s * 0.16f)); drawPath(cup, tint)
    drawRect(ink, Offset(c.x - s * 0.06f, c.y + s * 0.2f), androidx.compose.ui.geometry.Size(s * 0.12f, s * 0.2f))
    drawRoundRect(ink, Offset(c.x - s * 0.26f, c.y + s * 0.38f), androidx.compose.ui.geometry.Size(s * 0.52f, s * 0.14f), androidx.compose.ui.geometry.CornerRadius(s * 0.04f))
    drawRoundRect(tint, Offset(c.x - s * 0.22f, c.y + s * 0.4f), androidx.compose.ui.geometry.Size(s * 0.44f, s * 0.09f), androidx.compose.ui.geometry.CornerRadius(s * 0.03f))
    drawLine(Color.White.copy(alpha = 0.6f), Offset(c.x - s * 0.2f, c.y - s * 0.3f), Offset(c.x - s * 0.2f, c.y - s * 0.05f), s * 0.06f)
}

/** The road for the whole run: six rooms, what each boss gives you, and the vault at the end. */
@Composable
fun JourneyMap(r: Run, modifier: Modifier = Modifier) {
    val t = rememberLoop(1600)
    Column(modifier.fillMaxWidth().chunky(Brush.verticalGradient(listOf(Color(0xFF241F3F), Color(0xFF14102A))), radius = 16.dp, lift = 3.dp).padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            for (a in 1..Run.MAX_ANTE) {
                val v = Venues.of(a)
                val done = a < r.ante
                val now = a == r.ante
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Canvas(Modifier.size(36.dp)) {
                        val c = Offset(size.width / 2, size.height / 2); val rad = size.width * 0.4f
                        if (now) drawCircle(v.print.copy(alpha = 0.35f + 0.25f * (if (animOn) kotlin.math.sin(t * 6.283f) else 0.5f)), size.width * 0.5f, c)
                        drawCircle(EdgeInk, rad + 2.dp.toPx(), c)
                        drawCircle(if (done) v.base else if (now) v.light else v.base.copy(alpha = 0.55f), rad, c)
                        drawCircle(if (done) GoldCol else if (now) v.print else Color.White.copy(alpha = 0.2f), rad, c, style = Stroke(2.dp.toPx()))
                        if (done) {
                            val p = Path().apply { moveTo(c.x - rad * 0.45f, c.y); lineTo(c.x - rad * 0.1f, c.y + rad * 0.38f); lineTo(c.x + rad * 0.5f, c.y - rad * 0.35f) }
                            drawPath(p, GoldCol, style = Stroke(3.dp.toPx()))
                        }
                    }
                    Text("$a", fontSize = 11.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (done) GoldCol else if (now) Color.White else Color.White.copy(alpha = 0.4f))
                    val m = Run.MILESTONES.getOrNull(a - 1)
                    Text(
                        when (m) { Milestone.PACK -> "PACK"; Milestone.HAND -> "+HAND"; Milestone.SLOT -> "+SLOT"; null -> "VAULT" },
                        fontSize = 8.sp, fontFamily = Mono, fontWeight = FontWeight.Black, maxLines = 1,
                        color = if (done) GoldCol.copy(alpha = 0.6f) else if (now) GoldCol else Color.White.copy(alpha = 0.4f),
                    )
                }
            }
            Canvas(Modifier.size(34.dp, 40.dp)) { trophy(Offset(size.width / 2, size.height * 0.45f), size.height * 0.9f, if (r.ante >= Run.MAX_ANTE) GoldCol else GoldCol.copy(alpha = 0.45f)) }
        }
        val v = Venues.of(r.ante)
        Text("Ante ${r.ante} of ${Run.MAX_ANTE}: ${v.name}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = v.print)
        val m = Run.MILESTONES.getOrNull(r.ante - 1)
        Text(
            if (m != null) "Beat the boss to earn: ${m.label}. ${m.desc}" else "Beat this boss to win the run and reach the Vault.",
            fontSize = 12.sp, color = Cream.copy(alpha = 0.85f), lineHeight = 15.sp,
        )
        if (r.extraSlots > 0 || r.permHands > 0) Text("Earned so far: ${if (r.permHands > 0) "+${r.permHands} hand${if (r.permHands > 1) "s" else ""}  " else ""}${if (r.extraSlots > 0) "+${r.extraSlots} perk slot" else ""}", fontSize = 11.sp, color = Color(0xFF3DDC84), fontWeight = FontWeight.Bold)
    }
}

/** A shield with the rank number, drawn in code. */
@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Canvas(modifier.size(size)) {
        val w = size.toPx(); val c = Offset(w / 2, w / 2)
        val sh = Path().apply { moveTo(w * 0.15f, w * 0.12f); lineTo(w * 0.85f, w * 0.12f); lineTo(w * 0.85f, w * 0.52f); quadraticTo(w * 0.8f, w * 0.82f, c.x, w * 0.95f); quadraticTo(w * 0.2f, w * 0.82f, w * 0.15f, w * 0.52f); close() }
        drawPath(sh, EdgeInk, style = Stroke(w * 0.1f))
        drawPath(sh, Brush.verticalGradient(listOf(Color(0xFFFFE08A), Color(0xFFC9922A)), startY = 0f, endY = w))
        val stars = (rank / 2 + 1).coerceAtMost(5)
        for (i in 0 until stars) {
            val x = w * (0.5f + (i - (stars - 1) / 2f) * 0.13f)
            drawCircle(EdgeInk, w * 0.045f, Offset(x, w * 0.64f))
        }
    }
}

@Composable
fun RankCard(store: CasualStore, modifier: Modifier = Modifier) {
    val xp = store.runXp
    val rank = RunMeta.rankOf(xp)
    val prog = RunMeta.progress(xp)
    Row(modifier.fillMaxWidth().chunky(Brush.horizontalGradient(listOf(Color(0xFF2E2658), Color(0xFF181334))), radius = 16.dp, lift = 3.dp).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        RankBadge(rank)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("RANK ${rank + 1}: ${RunMeta.title(rank).uppercase()}", fontSize = 13.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = GoldCol)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(if (prog == null) 1f else (prog.first / prog.second.toFloat()).coerceIn(0.02f, 1f)).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFF8A3D), GoldCol))))
            }
            val next = com.hilotrainer.engine.StartDeck.entries.firstOrNull { it.unlockRank > rank }
            Text(
                if (prog == null) "Top rank reached" else "${prog.second - prog.first} points to ${RunMeta.title(rank + 1)}" + (next?.let { ".  Next unlock: ${it.label} at ${RunMeta.title(it.unlockRank)}" } ?: ""),
                fontSize = 11.sp, color = Cream.copy(alpha = 0.8f), lineHeight = 13.sp,
            )
        }
    }
}

/** Every perk in the game. Perks you have not met yet stay hidden, so there is always something left to find. */
@Composable
fun CodexSheet(store: CasualStore, onClose: () -> Unit) {
    val seen = store.runSeen.split(',').filter { it.isNotEmpty() }.toSet()
    Box(Modifier.fillMaxSize().background(Color(0xF2080612)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).padding(12.dp).chunky(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = Color(0xFF9B7BFF), radius = 20.dp, lift = 5.dp, border = 3.dp).clickable(enabled = false) {}.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PERK COLLECTION  ${Perks.ALL.count { it.id in seen }} of ${Perks.ALL.size}", fontSize = 16.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = GoldCol)
            Text("A perk is added when you see it in a shop or a pack. Find them all.", fontSize = 12.sp, color = Cream.copy(alpha = 0.8f))
            Column(Modifier.heightIn(max = 520.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Perks.ALL.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { pk ->
                            val known = pk.id in seen
                            Column(Modifier.weight(1f).height(138.dp).chunky(if (known) Color(0xFF1E1A3C) else Color(0xFF12101F), edge = if (known) rarityColor(pk.rarity) else Color(0xFF2A2640), radius = 10.dp, lift = 2.dp, border = 1.5.dp).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (known) {
                                    PerkArt(pk.id, pk.rarity, badgeOf(pk).second, size = 32.dp)
                                    Text(pk.name, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 11.sp)
                                    Text(pk.desc, fontSize = 8.sp, lineHeight = 9.sp, color = Cream.copy(alpha = 0.8f), textAlign = TextAlign.Center, maxLines = 8)
                                } else {
                                    Text("?", fontSize = 30.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = Color(0xFF3A3560))
                                    Text(pk.rarity.label, fontSize = 9.sp, color = Color(0xFF5A5480))
                                    Text("Not found yet", fontSize = 9.sp, color = Color(0xFF5A5480))
                                }
                            }
                        }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            ChunkyButton("Close", Color(0xFF3A3A5A), Modifier.fillMaxWidth()) { onClose() }
        }
    }
}

/** The permanent goals list. */
@Composable
fun GoalsSheet(store: CasualStore, onClose: () -> Unit) {
    val done = store.runGoalsDone.split(',').filter { it.isNotEmpty() }.toSet()
    Box(Modifier.fillMaxSize().background(Color(0xF2080612)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp).padding(14.dp).chunky(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = GoldCol, radius = 20.dp, lift = 5.dp, border = 3.dp).clickable(enabled = false) {}.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("GOALS  ${RunMeta.GOALS.count { it.id in done }} of ${RunMeta.GOALS.size}", fontSize = 16.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = GoldCol)
            Text("Each goal pays rank points once. Rank unlocks new starting decks.", fontSize = 12.sp, color = Cream.copy(alpha = 0.8f))
            Column(Modifier.heightIn(max = 480.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                RunMeta.GOALS.forEach { g ->
                    val ok = g.id in done
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (ok) "✓" else "·", fontSize = 16.sp, fontWeight = FontWeight.Black, color = if (ok) Color(0xFF3DDC84) else Cream.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.label, fontSize = 14.sp, fontWeight = FontWeight.Black, color = if (ok) Color.White else Cream)
                            Text(g.desc, fontSize = 11.sp, color = Cream.copy(alpha = 0.7f))
                        }
                        Text("+${g.xp}", fontSize = 12.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = if (ok) Color(0xFF3DDC84) else GoldCol)
                    }
                }
            }
            ChunkyButton("Close", Color(0xFF3A3A5A), Modifier.fillMaxWidth()) { onClose() }
        }
    }
}

/** Shown on the end screen: what this run was worth. */
@Composable
fun RewardPanel(rw: RunReward, modifier: Modifier = Modifier) {
    val bar = remember { Animatable(0f) }
    LaunchedEffect(Unit) { if (animOn) bar.animateTo(1f, tween(1100)) else bar.snapTo(1f) }
    val shown = rw.before + ((rw.after - rw.before) * bar.value).toInt()
    val prog = RunMeta.progress(shown)
    Column(modifier.fillMaxWidth().chunky(Brush.verticalGradient(listOf(Color(0xFF2E2658), Color(0xFF181334))), edge = GoldCol, radius = 16.dp, lift = 4.dp, border = 2.5.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RankBadge(RunMeta.rankOf(shown))
            Column(Modifier.weight(1f)) {
                Text("+${rw.xp} RANK POINTS", fontSize = 16.sp, fontFamily = Mono, fontWeight = FontWeight.Black, color = GoldCol)
                Text("Rank ${RunMeta.rankOf(shown) + 1}: ${RunMeta.title(RunMeta.rankOf(shown))}", fontSize = 12.sp, color = Cream)
            }
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(if (prog == null) 1f else (prog.first / prog.second.toFloat()).coerceIn(0.02f, 1f)).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFF8A3D), GoldCol))))
        }
        if (rw.rankedUp) Text("RANK UP! You are now ${RunMeta.title(rw.rankAfter)}.", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF3DDC84))
        rw.unlocks.forEach { Text("UNLOCKED: $it", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color(0xFFD59CFF)) }
        rw.goals.forEach { Text("Goal done: ${it.label} (+${it.xp})", fontSize = 12.sp, color = Color(0xFF5ED0FF), fontWeight = FontWeight.Bold) }
    }
}
