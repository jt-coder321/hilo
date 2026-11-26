package com.hilotrainer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Balatro-style colors: chips are blue, mult is red, everything sits on a dark outline. */
val ChipBlue = Color(0xFF2E8BFF)
val MultRed = Color(0xFFFF4B4B)
val XMultGold = Color(0xFFFFC53D)
val EdgeInk = Color(0xFF0B0A12)

/** A chunky sticker: dark outline, flat fill and a hard drop shadow underneath. */
fun Modifier.chunky(fill: Brush, edge: Color = EdgeInk, radius: Dp = 12.dp, lift: Dp = 3.dp, border: Dp = 2.5.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .drawBehind {
            drawRoundRect(edge, topLeft = Offset(0f, lift.toPx()), size = size, cornerRadius = CornerRadius(radius.toPx()))
        }
        .background(fill, shape)
        .border(border, edge, shape)
}

fun Modifier.chunky(fill: Color, edge: Color = EdgeInk, radius: Dp = 12.dp, lift: Dp = 3.dp, border: Dp = 2.5.dp): Modifier =
    chunky(Brush.verticalGradient(listOf(fill, fill)), edge, radius, lift, border)

/**
 * The room behind everything: slow colored swirls in the venue's colors, CRT scan lines and dark corners.
 * Standing still when animation is off, so screenshots are steady.
 */
@Composable
fun RunBackdrop(venue: Venue, boss: Boolean, modifier: Modifier = Modifier) {
    val live = animOn
    val t = if (live) {
        val inf = rememberInfiniteTransition(label = "swirl")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "t").value
    } else 0.18f
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(venue.bg)
        val tau = (2 * PI).toFloat()
        for (i in 0..4) {
            val cx = w * (0.5f + 0.42f * sin(tau * (t * (1 + i % 2) + i * 0.21f)))
            val cy = h * (0.5f + 0.42f * cos(tau * (t * (1 + (i + 1) % 2) + i * 0.37f)))
            val col = lerp(venue.base, venue.light, i / 4f)
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.42f), Color.Transparent), center = Offset(cx, cy), radius = maxOf(w, h) * 0.55f), radius = maxOf(w, h) * 0.55f, center = Offset(cx, cy))
        }
        if (boss) drawRect(Brush.verticalGradient(listOf(Color(0x55B71C1C), Color.Transparent), endY = h * 0.5f))
        // Scan lines.
        val step = 3.dp.toPx()
        var y = 0f
        while (y < h) { drawRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(0f, y), size = Size(w, 1f)); y += step }
        // Dark corners.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center = Offset(w / 2, h / 2), radius = maxOf(w, h) * 0.78f))
    }
}

/** Shakes whatever it is applied to. Call [hit] with a power from 0 to 1. */
class Shaker {
    val x = Animatable(0f)
    val y = Animatable(0f)
    private val rnd = Random(5)
    suspend fun hit(power: Float) {
        if (!animOn) return
        val p = power.coerceIn(0f, 1f)
        for (i in 0 until 10) {
            val k = 1f - i / 10f
            x.snapTo((rnd.nextFloat() - 0.5f) * 2f * p * 16f * k)
            y.snapTo((rnd.nextFloat() - 0.5f) * 2f * p * 12f * k)
            delay(16)
        }
        x.snapTo(0f); y.snapTo(0f)
    }
}

fun Modifier.shaken(s: Shaker): Modifier = graphicsLayer { translationX = s.x.value * density; translationY = s.y.value * density; rotationZ = s.x.value * 0.12f }

private class Bit(val a: Float, val v: Float, val size: Float, val color: Color, val spin: Float, val square: Boolean)

/** Confetti and coins thrown out from a point when [key] changes. */
@Composable
fun Burst(key: Int, colors: List<Color>, modifier: Modifier = Modifier, count: Int = 42, originX: Float = 0.5f, originY: Float = 0.5f, power: Float = 1f) {
    val bits = remember(key) {
        val r = Random(key * 31 + 7)
        List(count) { Bit(r.nextFloat() * 6.2832f, (0.35f + r.nextFloat() * 0.75f) * power, 4f + r.nextFloat() * 6f, colors[r.nextInt(colors.size)], r.nextFloat() * 6f, r.nextBoolean()) }
    }
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { if (key != 0 && animOn) p.animateTo(1f, tween(1200, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val t = p.value
        if (key == 0 || t <= 0.001f || t >= 0.999f) return@Canvas
        val ox = size.width * originX; val oy = size.height * originY
        val reach = size.minDimension * 0.75f
        bits.forEach { b ->
            val e = 1f - (1f - t) * (1f - t)
            val x = ox + cos(b.a) * b.v * reach * e
            val y = oy + sin(b.a) * b.v * reach * e + t * t * size.height * 0.35f
            val c = b.color.copy(alpha = (1f - t).coerceIn(0f, 1f))
            val s = b.size.dp.toPx()
            if (b.square) {
                rotate(b.spin * t * 6f, Offset(x, y)) { drawRect(c, topLeft = Offset(x - s / 2, y - s / 2), size = Size(s, s * 0.6f)) }
            } else drawCircle(c, s / 2, Offset(x, y))
        }
    }
}
