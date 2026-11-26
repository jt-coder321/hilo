package com.hilotrainer.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Rogue Run motion settings. [reduce] turns animation off, [quality] is 0 low, 1 normal, 2 high. */
object RunMotion {
    var reduce = false
    var quality = 1
}

/** True when things may move at all. Tests switch [CasualAnim.live] off so screenshots hold still. */
val animOn: Boolean get() = CasualAnim.live && !RunMotion.reduce

/** 0 = still, 1 = low, 2 = normal, 3 = high. */
val fxLevel: Int get() = if (!animOn) 0 else RunMotion.quality + 1

@Composable
fun rememberLoop(ms: Int): Float {
    if (!animOn) return 0f
    val inf = rememberInfiniteTransition(label = "loop")
    return inf.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart), label = "l").value
}

/** A soft colored glow around the screen edges. [strength] 0..1, pulsing at [beatMs]. */
@Composable
fun EdgeGlow(color: Color, strength: Float, beatMs: Int, modifier: Modifier = Modifier) {
    if (strength <= 0.01f) return
    val s by animateFloatAsState(strength, tween(500), label = "glow")
    val beat = if (animOn) rememberInfiniteTransition(label = "beat").animateFloat(0.55f, 1f, infiniteRepeatable(tween(beatMs.coerceAtLeast(300), easing = androidx.compose.animation.core.FastOutSlowInEasing), RepeatMode.Reverse), label = "b").value else 0.8f
    Canvas(modifier.fillMaxSize()) {
        val a = (s * beat * 0.65f).coerceIn(0f, 0.8f)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, color.copy(alpha = a)), center = center, radius = maxOf(size.width, size.height) * 0.62f))
    }
}

/** An expanding ring from a point. Fires when [key] changes. */
@Composable
fun Shockwave(key: Int, color: Color, modifier: Modifier = Modifier, originX: Float = 0.5f, originY: Float = 0.5f) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { if (key != 0 && animOn) p.animateTo(1f, tween(900, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val t = p.value
        if (key == 0 || t <= 0.001f || t >= 0.999f) return@Canvas
        val c = Offset(size.width * originX, size.height * originY)
        val r = maxOf(size.width, size.height) * 0.9f * t
        drawCircle(color.copy(alpha = (1f - t) * 0.7f), r, c, style = Stroke(width = (14f * (1f - t) + 2f).dp.toPx()))
        drawCircle(Color.White.copy(alpha = (1f - t) * 0.35f), r * 0.82f, c, style = Stroke(width = 3.dp.toPx()))
    }
}

private class Ripple(val x: Float, val y: Float, val born: Long)

/** Rings that spread from every touch. Never takes the touch away from what is underneath. */
@Composable
fun RippleLayer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val ripples = remember { mutableStateListOf<Ripple>() }
    var now by remember { mutableStateOf(0L) }
    LaunchedEffect(ripples.size) {
        while (ripples.isNotEmpty()) {
            delay(32); now += 32
            ripples.removeAll { now - it.born > 600 }
        }
    }
    Box(modifier.pointerInput(Unit) {
        awaitEachGesture {
            val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (animOn) ripples += Ripple(d.position.x, d.position.y, now)
        }
    }) {
        content()
        if (ripples.isNotEmpty()) Canvas(Modifier.fillMaxSize()) {
            ripples.forEach { r ->
                val t = ((now - r.born) / 600f).coerceIn(0f, 1f)
                drawCircle(Color.White.copy(alpha = (1f - t) * 0.5f), 10.dp.toPx() + t * 46.dp.toPx(), Offset(r.x, r.y), style = Stroke(2.dp.toPx()))
            }
        }
    }
}

/** A fan of card backs that spreads out and flips away. Used when the screen changes. */
@Composable
fun CardWipe(key: Int, color: Color, modifier: Modifier = Modifier) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { if (key != 0 && fxLevel >= 2) p.animateTo(1f, tween(650, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val t = p.value
        if (key == 0 || t <= 0.001f || t >= 0.999f) return@Canvas
        val cw = size.width * 0.26f; val ch = cw * 1.4f
        val pivot = Offset(size.width / 2, size.height * 1.05f)
        for (i in 0..8) {
            val a = (-70f + i * 17.5f) * t
            val lift = size.height * 0.62f * t
            rotate(a, pivot) {
                val x = pivot.x - cw / 2; val y = pivot.y - lift - ch
                val al = (1f - t * t).coerceIn(0f, 1f)
                drawRoundRect(color.copy(alpha = al), Offset(x, y), Size(cw, ch), CornerRadius(10.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = al * 0.5f), Offset(x + 6.dp.toPx(), y + 6.dp.toPx()), Size(cw - 12.dp.toPx(), ch - 12.dp.toPx()), CornerRadius(7.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

class Callout(val text: String, val color: Color, val power: Int, val seq: Int)

/** Big stylized words that slam onto the table: DOUBLE DOWN, PERFECT 21, BLACKJACK. */
@Composable
fun CalloutOverlay(c: Callout?, modifier: Modifier = Modifier) {
    if (c == null) return
    val p = remember(c.seq) { Animatable(0f) }
    LaunchedEffect(c.seq) { if (animOn) p.animateTo(1f, tween(1500, easing = LinearEasing)) else p.snapTo(0.2f) }
    val t = p.value
    if (animOn && (t <= 0f || t >= 0.999f)) return
    val slam = (1f - (t / 0.18f).coerceIn(0f, 1f))
    val scale = 1f + slam * 1.6f
    val alpha = if (t < 0.7f) 1f else (1f - (t - 0.7f) / 0.3f)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (fxLevel >= 2) Canvas(Modifier.fillMaxSize()) {
            val n = 18
            for (i in 0 until n) {
                val a = i * (2 * PI.toFloat() / n) + c.seq
                val r0 = size.minDimension * (0.16f + slam * 0.3f); val r1 = size.minDimension * (0.55f + t * 0.25f)
                drawLine(c.color.copy(alpha = alpha * 0.35f), Offset(center.x + cos(a) * r0, center.y + sin(a) * r0), Offset(center.x + cos(a) * r1, center.y + sin(a) * r1), strokeWidth = 5.dp.toPx())
            }
        }
        Text(
            c.text, fontSize = (30 + c.power * 4).sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = -5f + slam * 8f; this.alpha = alpha }
                .chunky(c.color, radius = 14.dp, lift = 5.dp, border = 3.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** RGB-split flicker for when a boss bends the rules. */
@Composable
fun GlitchOverlay(key: Int, modifier: Modifier = Modifier) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { if (key != 0 && animOn) p.animateTo(1f, tween(520, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val t = p.value
        if (key == 0 || t <= 0.001f || t >= 0.999f) return@Canvas
        val r = Random((t * 18).toInt() + key)
        repeat(9) {
            val y = r.nextFloat() * size.height; val h = 3.dp.toPx() + r.nextFloat() * 18.dp.toPx()
            val off = (r.nextFloat() - 0.5f) * 40.dp.toPx()
            val col = when (it % 3) { 0 -> Color(0x66FF2A2A); 1 -> Color(0x6638D6FF); else -> Color(0x55FFFFFF) }
            drawRect(col.copy(alpha = col.alpha * (1f - t)), Offset(off, y), Size(size.width, h))
        }
    }
}

/** Sand running out as hands run out. */
@Composable
fun Hourglass(left: Float, urgent: Boolean, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val f by animateFloatAsState(left.coerceIn(0f, 1f), tween(700), label = "sand")
    val flip = remember { Animatable(0f) }
    LaunchedEffect(left) { if (animOn) { flip.snapTo(0f); flip.animateTo(1f, tween(500)) } }
    Canvas(modifier.size(size, size * 1.25f).graphicsLayer { rotationZ = if (animOn) (1f - flip.value) * 40f * (if (urgent) 1f else 0f) else 0f }) {
        val w = this.size.width; val h = this.size.height
        val sand = if (urgent) Color(0xFFFF6B5E) else Color(0xFFFFD35A)
        val frame = Color(0xFFEFE6CF)
        val top = Path().apply { moveTo(w * 0.12f, h * 0.06f); lineTo(w * 0.88f, h * 0.06f); lineTo(w * 0.52f, h * 0.5f); lineTo(w * 0.48f, h * 0.5f); close() }
        val bot = Path().apply { moveTo(w * 0.48f, h * 0.5f); lineTo(w * 0.52f, h * 0.5f); lineTo(w * 0.88f, h * 0.94f); lineTo(w * 0.12f, h * 0.94f); close() }
        // Sand in the top bulb shrinks, sand in the bottom bulb grows.
        clipPath(top) { drawRect(sand, Offset(0f, h * (0.06f + 0.44f * (1f - f))), Size(w, h * 0.44f * f + 1f)) }
        clipPath(bot) { drawRect(sand, Offset(0f, h * (0.94f - 0.44f * (1f - f))), Size(w, h * 0.44f * (1f - f) + 1f)) }
        drawPath(top, frame, style = Stroke(1.6.dp.toPx())); drawPath(bot, frame, style = Stroke(1.6.dp.toPx()))
        drawLine(frame, Offset(w * 0.08f, h * 0.05f), Offset(w * 0.92f, h * 0.05f), 2.2.dp.toPx())
        drawLine(frame, Offset(w * 0.08f, h * 0.95f), Offset(w * 0.92f, h * 0.95f), 2.2.dp.toPx())
    }
}

/** The running count as a meter: it surges to the right when the shoe is rich and sinks to the left when it is cold. */
@Composable
fun CountMeter(running: Int, tc: Double, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(((running + 12) / 24f).coerceIn(0f, 1f), spring(dampingRatio = 0.5f, stiffness = 180f), label = "count")
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(running) { if (animOn) { pulse.snapTo(1.18f); pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 400f)) } }
    val hot = f > 0.58f; val cold = f < 0.42f
    val col = if (hot) Color(0xFFFF6B5E) else if (cold) Color(0xFF5ED0FF) else Color(0xFFEFE6CF)
    Column(modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }.chunky(Color(0xCC0B0A12), radius = 8.dp, lift = 2.dp, border = 1.5.dp).padding(horizontal = 7.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("COUNT ${if (running > 0) "+" else ""}$running  TC ${"%.1f".format(tc)}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = col, maxLines = 1)
        Box(Modifier.width(86.dp).height(6.dp).background(Color(0x33FFFFFF), RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(f).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF5ED0FF), Color(0xFFEFE6CF), Color(0xFFFF6B5E))), RoundedCornerShape(50)))
        }
    }
}

/** A little stack of chips that grows with the money you hold. */
@Composable
fun MoneyStack(money: Int, modifier: Modifier = Modifier) {
    val n by animateFloatAsState((1 + money / 6).coerceIn(1, 10).toFloat(), spring(dampingRatio = 0.45f, stiffness = 260f), label = "stack")
    Canvas(modifier.size(22.dp, 30.dp)) {
        val w = size.width; val h = 4.dp.toPx()
        val cols = listOf(Color(0xFFE5483F), Color(0xFF2E8BFF), Color(0xFF2FA866), Color(0xFFFFD35A))
        for (i in 0 until kotlin.math.ceil(n).toInt()) {
            val y = size.height - (i + 1) * h * 0.9f
            val col = cols[i % cols.size]
            drawRoundRect(EdgeInk, Offset(1f, y + 1.dp.toPx()), Size(w - 2f, h), CornerRadius(3.dp.toPx()))
            drawRoundRect(col, Offset(1f, y), Size(w - 2f, h), CornerRadius(3.dp.toPx()))
            drawLine(Color.White.copy(alpha = 0.55f), Offset(w * 0.2f, y + h * 0.5f), Offset(w * 0.8f, y + h * 0.5f), 1.dp.toPx())
        }
    }
}

/** A small gem whose shape tells the rarity: round coin, diamond, star. */
@Composable
fun RarityGem(rarity: com.hilotrainer.engine.Rarity, color: Color, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width; val c = Offset(w / 2, w / 2)
        when (rarity) {
            com.hilotrainer.engine.Rarity.COMMON -> { drawCircle(color, w * 0.38f, c); drawCircle(Color.White.copy(alpha = 0.5f), w * 0.18f, Offset(w * 0.42f, w * 0.4f)) }
            com.hilotrainer.engine.Rarity.UNCOMMON -> {
                val d = Path().apply { moveTo(c.x, 0f); lineTo(w, c.y); lineTo(c.x, w); lineTo(0f, c.y); close() }
                drawPath(d, color); drawPath(d, Color.White.copy(alpha = 0.6f), style = Stroke(1.dp.toPx()))
            }
            com.hilotrainer.engine.Rarity.RARE -> {
                val st = Path()
                for (i in 0 until 10) { val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5; val r = if (i % 2 == 0) w * 0.5f else w * 0.22f; val x = c.x + cos(a) * r; val y = c.y + sin(a) * r; if (i == 0) st.moveTo(x, y) else st.lineTo(x, y) }
                st.close(); drawPath(st, color); drawPath(st, Color.White.copy(alpha = 0.7f), style = Stroke(1.dp.toPx()))
            }
        }
    }
}

/** Coins that fly from the table up to your money, each with a fading trail. */
@Composable
fun CoinFlight(key: Int, modifier: Modifier = Modifier, count: Int = 7) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { if (key != 0 && fxLevel >= 2) p.animateTo(1f, tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val t = p.value
        if (key == 0 || t <= 0.001f || t >= 0.999f) return@Canvas
        val from = Offset(size.width * 0.5f, size.height * 0.55f)
        val to = Offset(size.width * 0.92f, -size.height * 0.02f)
        val rnd = Random(key)
        repeat(count) { i ->
            val lag = i * 0.06f
            val tt = ((t - lag) / (1f - lag)).coerceIn(0f, 1f)
            if (tt <= 0f) return@repeat
            val bend = (rnd.nextFloat() - 0.5f) * size.width * 0.5f
            for (k in 0..5) {
                val u = (tt - k * 0.035f).coerceIn(0f, 1f)
                val x = from.x + (to.x - from.x) * u + sin(u * PI.toFloat()) * bend
                val y = from.y + (to.y - from.y) * u - sin(u * PI.toFloat()) * size.height * 0.18f
                drawCircle(Color(0xFFFFD35A).copy(alpha = (0.55f - k * 0.09f).coerceAtLeast(0f) * (1f - tt * 0.5f)), (7f - k).coerceAtLeast(2f).dp.toPx(), Offset(x, y))
            }
        }
    }
}

/** The typed-out look for descriptions. */
@Composable
fun TypeText(text: String, modifier: Modifier = Modifier, fontSize: Int = 15, color: Color = Cream, textAlign: TextAlign = TextAlign.Center) {
    var n by remember(text) { mutableStateOf(if (animOn) 0 else text.length) }
    LaunchedEffect(text) { if (animOn) { while (n < text.length) { delay(16); n++ } } }
    Text(text.take(n), modifier, fontSize = fontSize.sp, color = color, textAlign = textAlign)
}

/** Glowing links between neighbouring perks that fired on the same hand. */
@Composable
fun SynergyLinks(fired: Set<Int>, slots: Int, key: Int, modifier: Modifier = Modifier) {
    if (fired.size < 2) return
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 6.dp.toPx()
            val slotW = (size.width - gap * (slots - 1)) / slots
            val sorted = fired.sorted()
            for (i in 0 until sorted.size - 1) {
                val a = sorted[i]; val b = sorted[i + 1]
                val x1 = a * (slotW + gap) + slotW / 2; val x2 = b * (slotW + gap) + slotW / 2
                val y = -2.dp.toPx()
                val path = Path().apply { moveTo(x1, y); quadraticBezierTo((x1 + x2) / 2, y - 22.dp.toPx(), x2, y) }
                drawPath(path, Color(0x55FFD35A), style = Stroke(9.dp.toPx()))
                drawPath(path, Color(0xFFFFF3B0), style = Stroke(2.5.dp.toPx()))
            }
        }
        Burst(key, listOf(Color(0xFFFFD35A), Color.White, Color(0xFFFF8A3D)), Modifier, count = 16, originX = 0.5f, originY = 0.1f, power = 0.5f)
    }
}

/** Slow drifting smoke, used on the table and in the lounge. */
fun DrawScope.smoke(t: Float, count: Int, tint: Color, seed: Int = 1) {
    val r = Random(seed)
    repeat(count) { i ->
        val ph = (t * (0.6f + r.nextFloat() * 0.5f) + r.nextFloat()) % 1f
        val x = size.width * (0.1f + r.nextFloat() * 0.8f) + sin((ph + i) * 6f) * 24.dp.toPx()
        val y = size.height * (1.05f - ph * 1.1f)
        val rad = (26 + r.nextFloat() * 36).dp.toPx() * (0.6f + ph)
        val a = 0.07f * sin(ph * PI.toFloat())
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = a), Color.Transparent), Offset(x, y), rad), rad, Offset(x, y))
    }
}

/** A bar-top scene for the quiet screens: glasses with rising bubbles, smoke, drifting chips and the pit boss on his rounds. */
@Composable
fun AmbientLounge(venue: Venue, modifier: Modifier = Modifier) {
    val level = fxLevel
    val t = rememberLoop(14000)
    val t2 = rememberLoop(5200)
    BoxWithConstraints(modifier.fillMaxWidth().height(110.dp)) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            // The bar top.
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))), Offset(0f, size.height * 0.55f), Size(size.width, size.height * 0.45f))
            drawRect(Color(0xFF3A2414), Offset(0f, size.height * 0.82f), Size(size.width, 4.dp.toPx()))
            drawRect(Gold.copy(alpha = 0.6f), Offset(0f, size.height * 0.82f), Size(size.width, 1.5.dp.toPx()))
            if (level >= 1) smoke(t, if (level >= 3) 5 else 3, venue.print, seed = 4)
            // Two glasses with bubbles.
            for (g in 0..1) {
                val gx = size.width * (0.22f + g * 0.5f)
                val top = size.height * 0.5f; val bot = size.height * 0.82f
                val glass = Path().apply { moveTo(gx - 11.dp.toPx(), top); lineTo(gx + 11.dp.toPx(), top); lineTo(gx + 8.dp.toPx(), bot); lineTo(gx - 8.dp.toPx(), bot); close() }
                drawPath(glass, venue.print.copy(alpha = 0.18f)); drawPath(glass, Color.White.copy(alpha = 0.4f), style = Stroke(1.2.dp.toPx()))
                drawRect(Color(0xCCE08A2D), Offset(gx - 9.dp.toPx(), top + 8.dp.toPx()), Size(18.dp.toPx(), bot - top - 9.dp.toPx()))
                if (level >= 1) repeat(4) { i ->
                    val ph = (t2 + i * 0.25f + g * 0.4f) % 1f
                    drawCircle(Color.White.copy(alpha = 0.6f * (1f - ph)), 1.6.dp.toPx(), Offset(gx - 4.dp.toPx() + i * 2.6.dp.toPx(), bot - ph * (bot - top - 8.dp.toPx())))
                }
            }
            // Chips drifting along the bar.
            if (level >= 2) repeat(3) { i ->
                val ph = (t * 1f + i / 3f) % 1f
                val x = size.width * ph; val y = size.height * 0.8f - abs(sin(ph * 12f)) * 4.dp.toPx()
                val col = listOf(Color(0xFFE5483F), Color(0xFF2E8BFF), Color(0xFFFFD35A))[i]
                drawCircle(EdgeInk, 6.dp.toPx(), Offset(x, y + 1.dp.toPx())); drawCircle(col, 5.dp.toPx(), Offset(x, y)); drawCircle(Color.White.copy(alpha = 0.6f), 5.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
            }
        }
        // The pit boss on his rounds, slowly crossing behind the bar.
        if (level >= 1) {
            val px = (w - 52.dp) * (if (t < 0.5f) t * 2f else 2f - t * 2f)
            Avatar(com.hilotrainer.engine.Look(0xFFD9B08C, 0xFF9E9E9E, com.hilotrainer.engine.Hair.SHORT, 0xFF222233, 0xFFB71C1C, extras = listOf(com.hilotrainer.engine.Extra.SUNGLASSES, com.hilotrainer.engine.Extra.LANYARD)), 52.dp, walking = true, name = "Pit boss", modifier = Modifier.offset(px, 8.dp))
        }
    }
}

/** Intro for a boss blind: a nameplate slams in with the rule underneath. */
@Composable
fun BossIntro(key: Int, name: String, rule: String, modifier: Modifier = Modifier) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { if (key != 0 && animOn) p.animateTo(1f, tween(2300, easing = LinearEasing)) }
    val t = p.value
    if (key == 0 || t <= 0.001f || t >= 0.999f) return
    val inT = (t / 0.14f).coerceIn(0f, 1f)
    val out = ((t - 0.82f) / 0.18f).coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().graphicsLayer { alpha = 1f - out }.background(Color.Black.copy(alpha = 0.55f * (1f - out))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val a = 0.5f * (1f - t)
            drawRect(Brush.radialGradient(listOf(Color(0xFFD64545).copy(alpha = a), Color.Transparent), center, size.maxDimension * (0.2f + t)))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.graphicsLayer { translationX = (1f - inT) * -size.width * 0.6f }.padding(24.dp)) {
            Text("BOSS BLIND", fontSize = 14.sp, letterSpacing = 6.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color(0xFFFF8A8A))
            Text(name.uppercase(), fontSize = 34.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { val s = 1f + (1f - inT) * 0.6f; scaleX = s; scaleY = s }.chunky(Color(0xFFD64545), radius = 14.dp, lift = 5.dp, border = 3.dp).padding(horizontal = 16.dp, vertical = 8.dp))
            Text(rule, fontSize = 15.sp, color = Cream, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        }
    }
}

/** Slams in at the start of each ante: the room and its name. */
@Composable
fun AnteBanner(key: Int, ante: Int, venue: Venue, modifier: Modifier = Modifier) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { if (key != 0 && animOn) p.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val t = p.value
    if (key == 0 || t <= 0.001f || t >= 0.999f) return
    val wipe = (t / 0.22f).coerceIn(0f, 1f)
    val out = ((t - 0.78f) / 0.22f).coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().graphicsLayer { alpha = 1f - out }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(venue.bg.copy(alpha = 0.94f), Offset(size.width * (1f - wipe), 0f), Size(size.width * wipe, size.height))
            drawRect(venue.print.copy(alpha = 0.85f), Offset(size.width * (1f - wipe), size.height * 0.47f), Size(size.width * wipe, 3.dp.toPx()))
            drawRect(venue.print.copy(alpha = 0.85f), Offset(size.width * (1f - wipe), size.height * 0.60f), Size(size.width * wipe, 3.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.graphicsLayer { this.alpha = wipe; val s = 1.3f - 0.3f * wipe; scaleX = s; scaleY = s }) {
            Text("ANTE $ante", fontSize = 46.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = venue.print)
            Text(venue.name.uppercase(), fontSize = 20.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
            Text(venue.tag, fontSize = 13.sp, color = Cream.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}
