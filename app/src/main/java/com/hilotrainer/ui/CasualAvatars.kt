package com.hilotrainer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.hilotrainer.engine.Extra
import com.hilotrainer.engine.Hair
import com.hilotrainer.engine.Hat
import com.hilotrainer.engine.Look
import kotlin.math.PI
import kotlin.math.sin

/** Tests switch idle animation off so frames settle. */
object CasualAnim { var live = true }

private fun c(v: Long) = Color(v.toInt())

/**
 * A person drawn in code: head, hair, outfit, props and arms. They breathe, blink,
 * talk when they have a speech bubble, and react with their whole body to [mood].
 * [phase] staggers the idle motion so a table never moves in sync.
 */
@Composable
fun Avatar(look: Look, size: Dp, mood: Mood = Mood.NEUTRAL, talking: Boolean = false, phase: Float = 0f, walking: Boolean = false, dealer: Boolean = false, name: String = "", modifier: Modifier = Modifier) {
    val live = CasualAnim.live
    var breath = 0f; var blink = 0f; var mouth = 0f; var t = 0f
    if (live) {
        val inf = rememberInfiniteTransition(label = "idle")
        val b by inf.animateFloat(0f, 1f, infiniteRepeatable(tween((2800 + phase * 900).toInt(), easing = LinearEasing)), label = "breath")
        val bl by inf.animateFloat(0f, 1f, infiniteRepeatable(keyframes { durationMillis = (3600 + phase * 2200).toInt(); 0f at 0; 0f at durationMillis - 160; 1f at durationMillis - 80; 0f at durationMillis }), label = "blink")
        val m by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse), label = "mouth")
        val tt by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "t")
        breath = b; blink = bl; mouth = if (talking) m else 0f; t = tt
    }
    val armsUp by animateFloatAsState(if (mood == Mood.CHEER) 1f else if (mood == Mood.HAPPY) 0.45f else 0f, tween(320, easing = FastOutSlowInEasing), label = "arms")
    val slump by animateFloatAsState(if (mood == Mood.SAD) 1f else 0f, tween(420), label = "slump")
    val anger by animateFloatAsState(if (mood == Mood.ANGRY) 1f else 0f, tween(300), label = "anger")
    val wave by animateFloatAsState(if (mood == Mood.WAVE) 1f else 0f, tween(300), label = "wave")
    val nervous = mood == Mood.NERVOUS
    val desc = remember(name) { if (name.isEmpty()) "Person at the table" else name }
    Canvas(modifier.size(size, size).semantics { contentDescription = desc }) {
        val w = this.size.width; val h = this.size.height
        val tau = (2 * PI).toFloat()
        val bob = sin((breath + phase) * tau) * h * 0.012f
        val jump = if (mood == Mood.CHEER) -kotlin.math.abs(sin(t * tau)) * h * 0.06f else if (mood == Mood.HAPPY) -kotlin.math.abs(sin(t * tau)) * h * 0.025f else 0f
        val walkBob = if (walking) -kotlin.math.abs(sin(t * tau * 2)) * h * 0.04f else 0f
        val shake = if (anger > 0.5f) sin(t * tau * 6) * w * 0.02f else 0f
        translate(left = shake, top = bob + jump + walkBob + slump * h * 0.05f) {
            drawPerson(look, w, h, armsUp, slump, anger, wave, nervous, blink, mouth, mood, t, dealer)
        }
    }
}


private fun shade(c: Color, f: Float) = lerp(c, Color.Black, f)
private fun tint(c: Color, f: Float) = lerp(c, Color.White, f)
private fun lum(c: Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
private fun vgrad(c: Color, y0: Float, y1: Float, hi: Float = 0.14f, lo: Float = 0.3f) =
    Brush.verticalGradient(listOf(tint(c, hi), c, shade(c, lo)), startY = y0, endY = y1)

/** A human face outline: wider at the brow, tapering to a chin. */
private fun facePath(cx: Float, cy: Float, r: Float, wide: Float = 0.9f): Path = Path().apply {
    val rx = r * wide; val top = cy - r * 1.05f; val chin = cy + r * 1.14f
    moveTo(cx, top)
    cubicTo(cx + rx, top, cx + rx * 1.08f, cy + r * 0.1f, cx + rx * 0.78f, cy + r * 0.7f)
    cubicTo(cx + rx * 0.6f, chin - r * 0.1f, cx + rx * 0.28f, chin, cx, chin)
    cubicTo(cx - rx * 0.28f, chin, cx - rx * 0.6f, chin - r * 0.1f, cx - rx * 0.78f, cy + r * 0.7f)
    cubicTo(cx - rx * 1.08f, cy + r * 0.1f, cx - rx, top, cx, top)
    close()
}

private fun almond(x: Float, y: Float, hw: Float, hh: Float, lower: Float = 0.7f) = Path().apply {
    moveTo(x - hw, y); quadraticTo(x, y - hh * 2f, x + hw, y); quadraticTo(x, y + hh * 2f * lower, x - hw, y); close()
}

private fun DrawScope.drawPerson(
    look: Look, w: Float, h: Float, armsUp: Float, slump: Float, anger: Float, wave: Float,
    nervous: Boolean, blink: Float, mouth: Float, mood: Mood, t: Float, dealer: Boolean,
) {
    val skin = c(look.skin).let { if (anger > 0f) lerp(it, Color(0xFFE5735F), anger * 0.45f) else it }
    val skinLo = shade(skin, 0.28f); val skinHi = tint(skin, 0.16f)
    val shirt = c(look.shirt); val trim = c(look.shirtTrim)
    val hairC = c(look.hair)
    val hairLo = shade(hairC, 0.35f); val hairHi = tint(hairC, 0.3f)
    val ex = look.extras
    val feminine = look.hairStyle in listOf(Hair.LONG, Hair.BUN, Hair.PONYTAIL, Hair.BOB) || Extra.PEARLS in ex || look.hat == Hat.TIARA
    val cx = w / 2
    val R = h * 0.19f
    val headY = h * 0.38f + slump * h * 0.04f
    val shoulderY = h * 0.66f
    val top = headY - R * 1.05f; val chin = headY + R * 1.14f
    val rx = R * 0.9f
    val armW = w * 0.12f
    val hairBrush = Brush.verticalGradient(listOf(hairHi, hairC, hairLo), startY = top - R * 0.2f, endY = chin)
    val skinBrush = Brush.radialGradient(listOf(skinHi, skin, skinLo), center = Offset(cx - R * 0.3f, headY - R * 0.35f), radius = R * 1.6f)
    val shirtBrush = vgrad(shirt, shoulderY - h * 0.03f, h, 0.18f, 0.32f)

    // Soft ground shadow so the figure sits in the table instead of floating.
    drawOval(Color.Black.copy(alpha = 0.18f), Offset(cx - w * 0.42f, h * 0.95f), Size(w * 0.84f, h * 0.07f))

    fun arm(side: Int, up: Float, waveA: Float) {
        val sx = cx + side * w * 0.33f
        val sy = shoulderY + h * 0.06f
        val down = Offset(cx + side * w * 0.36f, h * 1.02f)
        val raised = Offset(cx + side * w * 0.44f, h * 0.12f)
        var end = Offset(down.x + (raised.x - down.x) * up, down.y + (raised.y - down.y) * up)
        if (waveA > 0f && side == 1) {
            val ang = sin(t * 2 * PI.toFloat() * 2) * 0.35f
            end = Offset(sx + w * 0.22f + ang * w * 0.12f, h * 0.18f)
        }
        drawLine(shade(shirt, 0.12f), Offset(sx, sy), end, armW, StrokeCap.Round)
        drawLine(tint(shirt, 0.12f), Offset(sx - armW * 0.15f, sy), Offset(end.x - armW * 0.15f, end.y), armW * 0.35f, StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(skinHi, skin, skinLo), center = end - Offset(armW * 0.15f, armW * 0.15f), radius = armW * 0.8f), armW * 0.6f, end)
    }
    val up = armsUp
    if (up > 0.05f || wave > 0.05f) { arm(-1, up, 0f); arm(1, maxOf(up, 0f), wave) }

    // Hair behind the head.
    when (look.hairStyle) {
        Hair.LONG -> drawPath(Path().apply {
            moveTo(cx - rx * 1.05f, headY - R * 0.2f)
            cubicTo(cx - rx * 1.5f, headY + R * 1.2f, cx - rx * 1.45f, headY + R * 2.2f, cx - rx * 1.2f, headY + R * 2.45f)
            lineTo(cx + rx * 1.2f, headY + R * 2.45f)
            cubicTo(cx + rx * 1.45f, headY + R * 2.2f, cx + rx * 1.5f, headY + R * 1.2f, cx + rx * 1.05f, headY - R * 0.2f)
            close()
        }, hairBrush)
        Hair.BOB -> drawPath(Path().apply {
            moveTo(cx - rx * 1.05f, headY - R * 0.3f)
            cubicTo(cx - rx * 1.45f, headY + R * 0.5f, cx - rx * 1.4f, headY + R * 1.2f, cx - rx * 1.1f, headY + R * 1.3f)
            lineTo(cx + rx * 1.1f, headY + R * 1.3f)
            cubicTo(cx + rx * 1.4f, headY + R * 1.2f, cx + rx * 1.45f, headY + R * 0.5f, cx + rx * 1.05f, headY - R * 0.3f)
            close()
        }, hairBrush)
        Hair.CURLY -> for (i in 0 until 11) {
            val a = (PI + i * PI / 10).toFloat()
            val rr = R * (0.42f + 0.06f * ((i * 7) % 3))
            drawCircle(Brush.radialGradient(listOf(hairHi, hairC, hairLo), center = Offset(cx + kotlin.math.cos(a) * rx * 1.12f - rr * 0.3f, headY + sin(a) * R * 0.98f - rr * 0.3f), radius = rr * 1.5f), rr, Offset(cx + kotlin.math.cos(a) * rx * 1.12f, headY + sin(a) * R * 0.98f))
        }
        Hair.BUN -> drawCircle(Brush.radialGradient(listOf(hairHi, hairC, hairLo), center = Offset(cx - R * 0.15f, top - R * 0.35f), radius = R * 0.7f), R * 0.5f, Offset(cx, top - R * 0.15f))
        Hair.PONYTAIL -> drawPath(Path().apply {
            moveTo(cx + rx * 0.7f, top + R * 0.25f)
            cubicTo(cx + rx * 1.9f, top + R * 0.1f, cx + rx * 2.0f, headY + R * 1.2f, cx + rx * 1.4f, headY + R * 1.7f)
            cubicTo(cx + rx * 1.5f, headY + R * 0.8f, cx + rx * 1.1f, headY + R * 0.2f, cx + rx * 0.6f, top + R * 0.7f)
            close()
        }, hairBrush)
        else -> {}
    }

    // Neck with a shadow under the chin.
    val neckW = w * 0.15f
    drawRect(Brush.horizontalGradient(listOf(skinLo, skin, skinLo), startX = cx - neckW / 2, endX = cx + neckW / 2), Offset(cx - neckW / 2, headY + R * 0.6f), Size(neckW, shoulderY - headY - R * 0.4f))
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(cx - neckW * 0.55f, chin - R * 0.18f), Size(neckW * 1.1f, R * 0.42f))

    // Torso with shoulder shading and soft folds.
    val body = Path().apply {
        moveTo(cx - w * 0.44f, h)
        lineTo(cx - w * 0.42f, shoulderY + h * 0.07f)
        quadraticTo(cx - w * 0.38f, shoulderY - h * 0.02f, cx - w * 0.16f, shoulderY - h * 0.03f)
        lineTo(cx + w * 0.16f, shoulderY - h * 0.03f)
        quadraticTo(cx + w * 0.38f, shoulderY - h * 0.02f, cx + w * 0.42f, shoulderY + h * 0.07f)
        lineTo(cx + w * 0.44f, h)
        close()
    }
    drawPath(body, shirtBrush)
    drawPath(body, Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.3f)), startX = cx - w * 0.44f, endX = cx + w * 0.44f))
    for (side in intArrayOf(-1, 1)) {
        val fold = Path().apply { moveTo(cx + side * w * 0.3f, shoulderY + h * 0.05f); quadraticTo(cx + side * w * 0.2f, shoulderY + h * 0.14f, cx + side * w * 0.26f, h * 0.97f) }
        drawPath(fold, Color.Black.copy(alpha = 0.16f), style = Stroke(w * 0.012f, cap = StrokeCap.Round))
    }
    when (look.pattern) {
        1 -> for (i in 0 until 9) {
            val px = cx - w * 0.3f + (i % 4) * w * 0.2f + (i / 4) % 2 * w * 0.08f; val py = shoulderY + h * 0.08f + (i / 4) * h * 0.1f
            for (k in 0 until 5) { val a = k * 2 * PI.toFloat() / 5; drawCircle(trim.copy(alpha = 0.9f), w * 0.02f, Offset(px + kotlin.math.cos(a) * w * 0.03f, py + sin(a) * w * 0.03f)) }
            drawCircle(Color.White.copy(alpha = 0.85f), w * 0.012f, Offset(px, py))
        }
        2 -> {
            for (i in 0 until 6) drawLine(Color.Black.copy(alpha = 0.26f), Offset(cx - w * 0.38f + i * w * 0.16f, shoulderY), Offset(cx - w * 0.38f + i * w * 0.16f, h), w * 0.025f)
            for (i in 0 until 4) drawLine(Color.Black.copy(alpha = 0.2f), Offset(cx - w * 0.42f, shoulderY + h * 0.06f + i * h * 0.09f), Offset(cx + w * 0.42f, shoulderY + h * 0.06f + i * h * 0.09f), w * 0.02f)
        }
    }
    if (dealer) {
        // Crisp white shirt under the jacket, with lapels and a name tag.
        val v = Path().apply { moveTo(cx - w * 0.13f, shoulderY - h * 0.03f); lineTo(cx, h * 0.93f); lineTo(cx + w * 0.13f, shoulderY - h * 0.03f); close() }
        drawPath(v, Brush.verticalGradient(listOf(Color.White, Color(0xFFD9DCE0)), startY = shoulderY, endY = h))
        for (side in intArrayOf(-1, 1)) {
            val lap = Path().apply { moveTo(cx + side * w * 0.13f, shoulderY - h * 0.03f); lineTo(cx + side * w * 0.03f, h * 0.88f); lineTo(cx + side * w * 0.2f, shoulderY + h * 0.1f); close() }
            drawPath(lap, shade(shirt, 0.25f)); drawPath(lap, Color.White.copy(alpha = 0.08f), style = Stroke(w * 0.008f))
        }
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF3D98A), Color(0xFFC9A04A))), Offset(cx + w * 0.2f, shoulderY + h * 0.1f), Size(w * 0.15f, h * 0.045f), CornerRadius(3f))
    } else {
        val collar = Path().apply { moveTo(cx - w * 0.11f, shoulderY - h * 0.03f); lineTo(cx - w * 0.01f, shoulderY + h * 0.1f); lineTo(cx - w * 0.14f, shoulderY + h * 0.07f); close(); moveTo(cx + w * 0.11f, shoulderY - h * 0.03f); lineTo(cx + w * 0.01f, shoulderY + h * 0.1f); lineTo(cx + w * 0.14f, shoulderY + h * 0.07f); close() }
        drawPath(Path().apply { moveTo(cx - w * 0.075f, shoulderY - h * 0.03f); lineTo(cx, shoulderY + h * 0.085f); lineTo(cx + w * 0.075f, shoulderY - h * 0.03f); close() }, Brush.verticalGradient(listOf(skin, skinLo), startY = shoulderY, endY = shoulderY + h * 0.09f))
        drawPath(collar, tint(trim, 0.1f)); drawPath(collar, Color.Black.copy(alpha = 0.2f), style = Stroke(w * 0.006f))
    }
    if (Extra.SASH in ex) { drawLine(Color(0xFFEFEFEF), Offset(cx - w * 0.34f, shoulderY), Offset(cx + w * 0.3f, h), w * 0.085f); drawLine(Color.Black.copy(alpha = 0.15f), Offset(cx - w * 0.32f, shoulderY + h * 0.02f), Offset(cx + w * 0.32f, h + h * 0.02f), w * 0.01f) }
    if (Extra.SCARF in ex) { drawRoundRect(Brush.verticalGradient(listOf(tint(trim, 0.15f), shade(trim, 0.2f)), startY = shoulderY - h * 0.05f, endY = shoulderY + h * 0.03f), Offset(cx - w * 0.2f, shoulderY - h * 0.05f), Size(w * 0.4f, h * 0.075f), CornerRadius(w * 0.04f)) }
    if (Extra.GOLD_CHAIN in ex) drawArc(Color(0xFFFFD54F), 20f, 140f, false, Offset(cx - w * 0.13f, shoulderY - h * 0.1f), Size(w * 0.26f, h * 0.22f), style = Stroke(w * 0.02f))
    if (Extra.PEARLS in ex) for (i in 0..8) {
        val a = (20 + i * 17.5f) * PI.toFloat() / 180f
        val pp = Offset(cx + kotlin.math.cos(a) * w * 0.13f, shoulderY - h * 0.01f + sin(a) * h * 0.08f)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE4DCCD)), center = pp - Offset(w * 0.005f, w * 0.005f), radius = w * 0.02f), w * 0.018f, pp)
    }
    if (Extra.BOW_TIE in ex) {
        val by = shoulderY + h * 0.01f
        val bow = Path().apply { moveTo(cx, by); lineTo(cx - w * 0.075f, by - h * 0.032f); lineTo(cx - w * 0.075f, by + h * 0.032f); close(); moveTo(cx, by); lineTo(cx + w * 0.075f, by - h * 0.032f); lineTo(cx + w * 0.075f, by + h * 0.032f); close() }
        drawPath(bow, if (dealer) Color(0xFF111111) else trim); drawCircle(Color(0xFF000000).copy(alpha = 0.5f), w * 0.015f, Offset(cx, by))
    }
    if (Extra.LANYARD in ex) {
        drawLine(Color(0xFFFF7043), Offset(cx - w * 0.08f, shoulderY - h * 0.02f), Offset(cx, shoulderY + h * 0.16f), w * 0.02f)
        drawLine(Color(0xFFFF7043), Offset(cx + w * 0.08f, shoulderY - h * 0.02f), Offset(cx, shoulderY + h * 0.16f), w * 0.02f)
        drawRoundRect(Color.White, Offset(cx - w * 0.05f, shoulderY + h * 0.15f), Size(w * 0.1f, h * 0.07f), CornerRadius(2f))
    }
    if (look.hairStyle == Hair.LONG) for (side in intArrayOf(-1, 1)) {
        // Long hair falls in front of the shoulders.
        val lock = Path().apply {
            moveTo(cx + side * rx * 0.95f, headY - R * 0.1f)
            cubicTo(cx + side * rx * 1.35f, headY + R * 0.8f, cx + side * rx * 1.3f, headY + R * 1.9f, cx + side * rx * 1.1f, shoulderY + h * 0.14f)
            lineTo(cx + side * rx * 0.5f, shoulderY + h * 0.1f)
            cubicTo(cx + side * rx * 0.75f, headY + R * 1.3f, cx + side * rx * 0.7f, headY + R * 0.7f, cx + side * rx * 0.8f, headY + R * 0.2f)
            close()
        }
        drawPath(lock, hairBrush)
    }

    // Ears, then the face itself.
    for (side in intArrayOf(-1, 1)) {
        val ex0 = cx + side * rx * 1.02f
        drawOval(Brush.horizontalGradient(listOf(skin, skinLo), startX = ex0 - R * 0.15f, endX = ex0 + R * 0.15f), Offset(ex0 - R * 0.14f, headY - R * 0.05f), Size(R * 0.28f, R * 0.5f))
        drawOval(skinLo.copy(alpha = 0.55f), Offset(ex0 - R * 0.06f + side * R * 0.02f, headY + R * 0.04f), Size(R * 0.12f, R * 0.3f))
    }
    val face = facePath(cx, headY, R)
    drawPath(face, skinBrush)
    // Gentle cheek and jaw shading, forehead highlight.
    drawPath(face, Brush.horizontalGradient(listOf(skinLo.copy(alpha = 0.4f), Color.Transparent, Color.Transparent, skinLo.copy(alpha = 0.4f)), startX = cx - rx, endX = cx + rx))
    drawOval(skinHi.copy(alpha = 0.35f), Offset(cx - R * 0.35f, top + R * 0.15f), Size(R * 0.7f, R * 0.4f))
    val blush = if (feminine) 0.22f else 0.12f
    drawCircle(Color(0xFFE57373).copy(alpha = blush), R * 0.2f, Offset(cx - R * 0.55f, headY + R * 0.42f))
    drawCircle(Color(0xFFE57373).copy(alpha = blush), R * 0.2f, Offset(cx + R * 0.55f, headY + R * 0.42f))

    // Beard goes on the lower face before the mouth.
    if (Extra.BEARD in ex) {
        val beard = Path().apply {
            moveTo(cx - rx * 0.98f, headY + R * 0.05f)
            cubicTo(cx - rx * 1.0f, headY + R * 0.9f, cx - rx * 0.6f, chin + R * 0.14f, cx, chin + R * 0.14f)
            cubicTo(cx + rx * 0.6f, chin + R * 0.14f, cx + rx * 1.0f, headY + R * 0.9f, cx + rx * 0.98f, headY + R * 0.05f)
            cubicTo(cx + rx * 0.75f, headY + R * 0.4f, cx + rx * 0.45f, headY + R * 0.42f, cx, headY + R * 0.45f)
            cubicTo(cx - rx * 0.45f, headY + R * 0.42f, cx - rx * 0.75f, headY + R * 0.4f, cx - rx * 0.98f, headY + R * 0.05f)
            close()
        }
        drawPath(beard, hairBrush)
    }

    // Hair on top, with a parting and strand highlights.
    val cap = Path().apply {
        moveTo(cx - rx * 1.04f, headY + R * 0.1f)
        cubicTo(cx - rx * 1.25f, top - R * 0.4f, cx + rx * 1.25f, top - R * 0.4f, cx + rx * 1.04f, headY + R * 0.1f)
        cubicTo(cx + rx * 0.95f, headY - R * 0.35f, cx + rx * 0.55f, headY - R * 0.62f, cx + rx * 0.1f, headY - R * 0.66f)
        cubicTo(cx - rx * 0.4f, headY - R * 0.58f, cx - rx * 0.9f, headY - R * 0.4f, cx - rx * 1.04f, headY + R * 0.1f)
        close()
    }
    fun strands(n: Int) { for (i in 0 until n) { val x = cx - rx * 0.8f + i * (rx * 1.6f / n); drawLine(hairHi.copy(alpha = 0.35f), Offset(x, top + R * 0.12f), Offset(x + rx * 0.15f, headY - R * 0.5f), R * 0.03f, StrokeCap.Round) } }
    when (look.hairStyle) {
        Hair.SHORT, Hair.PONYTAIL, Hair.BUN -> { drawPath(cap, hairBrush); strands(7) }
        Hair.LONG -> { drawPath(cap, hairBrush); drawPath(Path().apply { moveTo(cx - R * 0.1f, top + R * 0.04f); quadraticTo(cx + R * 0.5f, top + R * 0.25f, cx + rx * 1.0f, headY - R * 0.2f); lineTo(cx + rx * 1.0f, headY + R * 0.1f); quadraticTo(cx + rx * 0.6f, headY - R * 0.5f, cx - R * 0.1f, headY - R * 0.62f); close() }, hairHi.copy(alpha = 0.25f)); strands(6) }
        Hair.BOB -> { drawPath(cap, hairBrush); drawPath(Path().apply { moveTo(cx - rx * 1.04f, headY + R * 0.5f); lineTo(cx - rx * 1.04f, headY - R * 0.1f); quadraticTo(cx - rx * 0.9f, headY - R * 0.55f, cx - rx * 0.3f, headY - R * 0.6f); quadraticTo(cx - rx * 0.8f, headY - R * 0.2f, cx - rx * 0.78f, headY + R * 0.5f); close() }, hairBrush); drawPath(Path().apply { moveTo(cx + rx * 1.04f, headY + R * 0.5f); lineTo(cx + rx * 1.04f, headY - R * 0.1f); quadraticTo(cx + rx * 0.9f, headY - R * 0.55f, cx + rx * 0.3f, headY - R * 0.6f); quadraticTo(cx + rx * 0.8f, headY - R * 0.2f, cx + rx * 0.78f, headY + R * 0.5f); close() }, hairBrush); strands(6) }
        Hair.SLICK -> { drawPath(cap, hairBrush); drawArc(Color.White.copy(alpha = 0.22f), 195f, 100f, false, Offset(cx - rx * 0.8f, top + R * 0.1f), Size(rx * 1.6f, R * 1.0f), style = Stroke(R * 0.06f, cap = StrokeCap.Round)) }
        Hair.CURLY -> for (i in 0 until 7) {
            val a = (PI + i * PI / 6).toFloat(); val rr = R * 0.34f
            val p = Offset(cx + kotlin.math.cos(a) * rx * 0.95f, headY - R * 0.3f + sin(a) * R * 0.78f)
            drawCircle(Brush.radialGradient(listOf(hairHi, hairC, hairLo), center = p - Offset(rr * 0.3f, rr * 0.3f), radius = rr * 1.5f), rr, p)
        }
        Hair.BUZZ -> { drawPath(cap, hairC.copy(alpha = 0.7f)); drawPath(cap, Color.Black.copy(alpha = 0.12f)) }
        Hair.BALD -> {
            drawOval(Color.White.copy(alpha = 0.22f), Offset(cx - R * 0.45f, top + R * 0.05f), Size(R * 0.55f, R * 0.3f))
            for (side in intArrayOf(-1, 1)) drawArc(hairC, if (side < 0) 100f else -60f, 80f, false, Offset(cx - rx * 1.04f, headY - R * 0.5f), Size(rx * 2.08f, R * 1.3f), style = Stroke(R * 0.14f))
        }
    }

    // Eyes.
    val eyeY = headY + R * 0.02f
    val eyeDx = R * 0.4f
    val hw = R * 0.19f; val hh = R * 0.085f
    val gaze = if (nervous) sin(t * 2 * PI.toFloat()) * R * 0.1f else 0f
    val irisC = if (lum(hairC) > 0.62f && lum(skin) > 0.55f) Color(0xFF4F83B5) else if (lum(skin) < 0.4f) Color(0xFF2B1B10) else Color(0xFF5A3A22)
    val lid = shade(skin, 0.55f)
    val browC = if (lum(hairC) > 0.7f) Color(0xFF9A9A9A) else shade(hairC, 0.18f)
    if (Extra.SUNGLASSES in ex) {
        for (side in intArrayOf(-1, 1)) {
            val lx = cx + side * eyeDx
            val lens = Path().apply { moveTo(lx - R * 0.3f, eyeY - R * 0.15f); lineTo(lx + R * 0.3f, eyeY - R * 0.15f); quadraticTo(lx + R * 0.3f, eyeY + R * 0.3f, lx, eyeY + R * 0.28f); quadraticTo(lx - R * 0.3f, eyeY + R * 0.3f, lx - R * 0.3f, eyeY - R * 0.15f); close() }
            drawPath(lens, Brush.verticalGradient(listOf(Color(0xFF3A3A3A), Color(0xFF0A0A0A)), startY = eyeY - R * 0.15f, endY = eyeY + R * 0.3f))
            drawLine(Color.White.copy(alpha = 0.4f), Offset(lx - R * 0.2f, eyeY - R * 0.08f), Offset(lx - R * 0.05f, eyeY - R * 0.1f), R * 0.045f, StrokeCap.Round)
        }
        drawLine(Color(0xFF111111), Offset(cx - eyeDx + R * 0.3f, eyeY - R * 0.1f), Offset(cx + eyeDx - R * 0.3f, eyeY - R * 0.1f), R * 0.06f)
        drawLine(Color(0xFF111111), Offset(cx - eyeDx - R * 0.3f, eyeY - R * 0.1f), Offset(cx - rx * 1.0f, eyeY - R * 0.05f), R * 0.05f)
        drawLine(Color(0xFF111111), Offset(cx + eyeDx + R * 0.3f, eyeY - R * 0.1f), Offset(cx + rx * 1.0f, eyeY - R * 0.05f), R * 0.05f)
    } else {
        val closed = blink > 0.5f || mood == Mood.CHEER
        for (side in intArrayOf(-1, 1)) {
            val lx = cx + side * eyeDx
            drawOval(skinLo.copy(alpha = 0.18f), Offset(lx - hw * 1.2f, eyeY - hh * 3.1f), Size(hw * 2.4f, hh * 2.6f))
            if (closed) {
                drawArc(lid, 20f, 140f, false, Offset(lx - hw, eyeY - hh * 1.2f), Size(hw * 2f, hh * 2.2f), style = Stroke(R * 0.05f, cap = StrokeCap.Round))
            } else {
                val a = almond(lx, eyeY, hw, hh)
                drawPath(a, Color(0xFFF6F1EA))
                clipPath(a) {
                    val ic = Offset(lx + gaze, eyeY + slump * R * 0.03f)
                    drawCircle(Brush.radialGradient(listOf(tint(irisC, 0.25f), irisC, shade(irisC, 0.5f)), center = ic, radius = hh * 1.2f), hh * 1.15f, ic)
                    drawCircle(Color.Black, hh * 0.5f, ic)
                    drawCircle(Color.White.copy(alpha = 0.9f), hh * 0.22f, ic + Offset(-hh * 0.35f, -hh * 0.4f))
                    drawRect(Color.Black.copy(alpha = 0.18f), Offset(lx - hw, eyeY - hh * 2f), Size(hw * 2f, hh * 0.9f))
                }
                drawPath(Path().apply { moveTo(lx - hw, eyeY); quadraticTo(lx, eyeY - hh * 2f, lx + hw, eyeY) }, Color(0xFF1A110C), style = Stroke(R * (if (feminine) 0.065f else 0.05f), cap = StrokeCap.Round))
                drawPath(Path().apply { moveTo(lx - hw, eyeY); quadraticTo(lx, eyeY + hh * 1.4f, lx + hw, eyeY) }, skinLo.copy(alpha = 0.6f), style = Stroke(R * 0.025f))
                if (feminine) {
                    val ox = lx + side * hw
                    drawLine(Color(0xFF1A110C), Offset(ox, eyeY), Offset(ox + side * R * 0.07f, eyeY - R * 0.09f), R * 0.035f, StrokeCap.Round)
                    drawLine(Color(0xFF1A110C), Offset(lx + side * hw * 0.5f, eyeY - hh * 1.5f), Offset(lx + side * hw * 0.7f, eyeY - hh * 2.3f), R * 0.025f, StrokeCap.Round)
                }
            }
        }
        if (Extra.GLASSES in ex) {
            val fc = Color(0xFF3B2F2A)
            for (side in intArrayOf(-1, 1)) {
                val lx = cx + side * eyeDx
                drawRoundRect(Color(0xFFBFD9E8).copy(alpha = 0.18f), Offset(lx - R * 0.3f, eyeY - R * 0.24f), Size(R * 0.6f, R * 0.46f), CornerRadius(R * 0.16f))
                drawRoundRect(fc, Offset(lx - R * 0.3f, eyeY - R * 0.24f), Size(R * 0.6f, R * 0.46f), CornerRadius(R * 0.16f), style = Stroke(R * 0.05f))
                drawLine(Color.White.copy(alpha = 0.5f), Offset(lx - R * 0.2f, eyeY - R * 0.15f), Offset(lx - R * 0.1f, eyeY - R * 0.18f), R * 0.03f, StrokeCap.Round)
                drawLine(fc, Offset(lx + side * R * 0.3f, eyeY - R * 0.05f), Offset(cx + side * rx * 1.0f, eyeY - R * 0.02f), R * 0.04f)
            }
            drawLine(fc, Offset(cx - eyeDx + R * 0.3f, eyeY - R * 0.08f), Offset(cx + eyeDx - R * 0.3f, eyeY - R * 0.08f), R * 0.04f)
        }
    }
    // Brows: tapered strokes. Angry slants down, sad slants up.
    val browY = eyeY - R * 0.33f
    val tilt = (anger - slump * 0.8f) * R * 0.12f
    val bw = if (feminine) R * 0.05f else R * 0.085f
    for (side in intArrayOf(-1, 1)) {
        val inner = Offset(cx + side * (eyeDx - R * 0.17f), browY + tilt)
        val outer = Offset(cx + side * (eyeDx + R * 0.2f), browY - tilt * 0.5f + R * 0.03f)
        val mid = Offset((inner.x + outer.x) / 2, browY - R * 0.05f - tilt * 0.3f)
        drawPath(Path().apply { moveTo(inner.x, inner.y); quadraticTo(mid.x, mid.y, outer.x, outer.y) }, browC, style = Stroke(bw, cap = StrokeCap.Round))
    }
    // Nose: bridge shadow, tip, nostrils.
    val noseY = eyeY + R * 0.36f
    drawLine(skinLo.copy(alpha = 0.35f), Offset(cx + R * 0.07f, eyeY + R * 0.08f), Offset(cx + R * 0.1f, noseY), R * 0.06f, StrokeCap.Round)
    drawCircle(Brush.radialGradient(listOf(skinHi, skin), center = Offset(cx - R * 0.03f, noseY - R * 0.03f), radius = R * 0.17f), R * 0.14f, Offset(cx, noseY))
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx - R * 0.15f, noseY + R * 0.06f), Size(R * 0.09f, R * 0.06f))
    drawOval(Color.Black.copy(alpha = 0.35f), Offset(cx + R * 0.06f, noseY + R * 0.06f), Size(R * 0.09f, R * 0.06f))
    drawArc(skinLo.copy(alpha = 0.5f), 20f, 140f, false, Offset(cx - R * 0.14f, noseY - R * 0.02f), Size(R * 0.28f, R * 0.16f), style = Stroke(R * 0.025f, cap = StrokeCap.Round))
    // Mouth: lips shaded, teeth when talking or cheering.
    val my = headY + R * 0.68f
    val mw = R * 0.5f
    val lipC = lerp(skin, if (feminine) Color(0xFFB83A4B) else Color(0xFF9A4A45), if (feminine) 0.6f else 0.45f)
    val lipLo = shade(lipC, 0.25f)
    val smile = mood == Mood.HAPPY || mood == Mood.WAVE
    val frown = mood == Mood.SAD || mood == Mood.ANGRY
    when {
        mouth > 0.5f || mood == Mood.CHEER -> {
            val open = R * (0.2f + mouth * 0.12f)
            val m = Path().apply { moveTo(cx - mw * 0.55f, my); quadraticTo(cx, my - R * 0.06f, cx + mw * 0.55f, my); quadraticTo(cx, my + open * 1.5f, cx - mw * 0.55f, my); close() }
            drawPath(m, Color(0xFF5A1E1E))
            clipPath(m) { drawRect(Color(0xFFF7F3EA), Offset(cx - mw, my - R * 0.05f), Size(mw * 2f, R * 0.09f)) }
            drawPath(m, lipLo, style = Stroke(R * 0.05f))
        }
        smile -> {
            val m = Path().apply { moveTo(cx - mw * 0.6f, my - R * 0.03f); quadraticTo(cx, my + R * 0.24f, cx + mw * 0.6f, my - R * 0.03f); quadraticTo(cx, my + R * 0.04f, cx - mw * 0.6f, my - R * 0.03f); close() }
            drawPath(m, Color(0xFFF7F3EA)); drawPath(m, lipC, style = Stroke(R * 0.06f, cap = StrokeCap.Round))
        }
        frown -> drawPath(Path().apply { moveTo(cx - mw * 0.5f, my + R * 0.06f); quadraticTo(cx, my - R * 0.1f, cx + mw * 0.5f, my + R * 0.06f) }, lipLo, style = Stroke(R * 0.08f, cap = StrokeCap.Round))
        mood == Mood.NERVOUS -> drawLine(lipLo, Offset(cx - mw * 0.4f, my + R * 0.02f), Offset(cx + mw * 0.4f, my - R * 0.02f), R * 0.07f, StrokeCap.Round)
        else -> {
            drawPath(Path().apply { moveTo(cx - mw * 0.5f, my); quadraticTo(cx - mw * 0.2f, my - R * 0.07f, cx, my - R * 0.03f); quadraticTo(cx + mw * 0.2f, my - R * 0.07f, cx + mw * 0.5f, my); quadraticTo(cx, my + R * 0.05f, cx - mw * 0.5f, my); close() }, lipLo)
            drawPath(Path().apply { moveTo(cx - mw * 0.5f, my); quadraticTo(cx, my + R * 0.17f, cx + mw * 0.5f, my); quadraticTo(cx, my + R * 0.04f, cx - mw * 0.5f, my); close() }, lipC)
        }
    }
    if (Extra.MUSTACHE in ex) drawPath(Path().apply { moveTo(cx, my - R * 0.17f); quadraticTo(cx - mw * 0.7f, my - R * 0.3f, cx - mw * 0.95f, my - R * 0.02f); quadraticTo(cx - mw * 0.4f, my - R * 0.12f, cx, my - R * 0.08f); quadraticTo(cx + mw * 0.4f, my - R * 0.12f, cx + mw * 0.95f, my - R * 0.02f); quadraticTo(cx + mw * 0.7f, my - R * 0.3f, cx, my - R * 0.17f); close() }, hairBrush)
    if (nervous) drawCircle(Color(0xFF9AD8FF), R * 0.1f, Offset(cx + R * 0.85f, headY - R * 0.45f + (t * R * 0.4f)))
    if (anger > 0.6f) for (i in 0..1) drawLine(Color(0xFFD32F2F), Offset(cx + R * (0.7f + i * 0.15f), headY - R * 1.1f), Offset(cx + R * (0.8f + i * 0.15f), headY - R * 1.3f), R * 0.08f)

    // Hats, shaded and casting a shadow on the brow.
    val hatC = c(look.hatColor)
    val hb = vgrad(hatC, top - R * 0.5f, headY - R * 0.5f, 0.2f, 0.35f)
    fun brimShadow(y: Float, wd: Float) = drawOval(Color.Black.copy(alpha = 0.22f), Offset(cx - wd / 2, y), Size(wd, R * 0.3f))
    when (look.hat) {
        Hat.BUCKET -> { brimShadow(headY - R * 0.55f, rx * 2f); drawRoundRect(hb, Offset(cx - R * 0.92f, headY - R * 1.45f), Size(R * 1.84f, R * 0.85f), CornerRadius(R * 0.4f)); drawOval(Brush.verticalGradient(listOf(tint(hatC, 0.15f), shade(hatC, 0.2f)), startY = headY - R * 0.9f, endY = headY - R * 0.4f), Offset(cx - R * 1.4f, headY - R * 0.9f), Size(R * 2.8f, R * 0.55f)); drawLine(shade(hatC, 0.4f), Offset(cx - R * 0.9f, headY - R * 0.85f), Offset(cx + R * 0.9f, headY - R * 0.85f), R * 0.06f) }
        Hat.CAP -> { drawArc(hb, 180f, 180f, true, Offset(cx - R * 1.05f, headY - R * 1.25f), Size(R * 2.1f, R * 1.55f)); brimShadow(headY - R * 0.5f, R * 1.7f); drawOval(Brush.verticalGradient(listOf(tint(hatC, 0.1f), shade(hatC, 0.3f)), startY = headY - R * 0.7f, endY = headY - R * 0.3f), Offset(cx - R * 0.25f, headY - R * 0.68f), Size(R * 1.55f, R * 0.38f)); drawCircle(shade(hatC, 0.3f), R * 0.06f, Offset(cx, headY - R * 1.22f)) }
        Hat.VISOR -> { brimShadow(headY - R * 0.55f, R * 1.8f); drawOval(Brush.verticalGradient(listOf(tint(hatC, 0.15f), shade(hatC, 0.3f)), startY = headY - R * 0.8f, endY = headY - R * 0.4f), Offset(cx - R * 1.1f, headY - R * 0.8f), Size(R * 2.2f, R * 0.42f)) }
        Hat.TIARA -> {
            val p = Path().apply { moveTo(cx - R * 0.6f, headY - R * 0.8f); lineTo(cx - R * 0.35f, headY - R * 1.2f); lineTo(cx - R * 0.15f, headY - R * 0.95f); lineTo(cx, headY - R * 1.35f); lineTo(cx + R * 0.15f, headY - R * 0.95f); lineTo(cx + R * 0.35f, headY - R * 1.2f); lineTo(cx + R * 0.6f, headY - R * 0.8f); close() }
            drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Color(0xFFD9A935)), startY = headY - R * 1.35f, endY = headY - R * 0.8f)); drawCircle(Color(0xFFF48FB1), R * 0.08f, Offset(cx, headY - R * 1.12f)); drawCircle(Color.White.copy(alpha = 0.8f), R * 0.03f, Offset(cx - R * 0.02f, headY - R * 1.15f))
        }
        Hat.BEANIE -> { drawArc(hb, 180f, 180f, true, Offset(cx - R * 1.05f, headY - R * 1.38f), Size(R * 2.1f, R * 1.75f)); brimShadow(headY - R * 0.5f, R * 1.9f); drawRoundRect(vgrad(hatC, headY - R * 0.7f, headY - R * 0.4f, 0.25f, 0.25f), Offset(cx - R * 1.05f, headY - R * 0.68f), Size(R * 2.1f, R * 0.26f), CornerRadius(R * 0.06f)); drawCircle(tint(hatC, 0.2f), R * 0.2f, Offset(cx, headY - R * 1.4f)) }
        Hat.FEDORA -> { brimShadow(headY - R * 0.5f, R * 2.3f); drawOval(Brush.verticalGradient(listOf(tint(hatC, 0.1f), shade(hatC, 0.3f)), startY = headY - R * 0.9f, endY = headY - R * 0.45f), Offset(cx - R * 1.45f, headY - R * 0.9f), Size(R * 2.9f, R * 0.45f)); drawRoundRect(hb, Offset(cx - R * 0.85f, headY - R * 1.55f), Size(R * 1.7f, R * 0.9f), CornerRadius(R * 0.28f)); drawRect(shade(hatC, 0.55f), Offset(cx - R * 0.85f, headY - R * 0.88f), Size(R * 1.7f, R * 0.14f)) }
        Hat.NONE -> {}
    }
    if (Extra.HEADPHONES in ex) {
        drawArc(Color(0xFF212121), 180f, 180f, false, Offset(cx - R * 1.1f, headY - R * 1.15f), Size(R * 2.2f, R * 2.0f), style = Stroke(R * 0.12f))
        for (side in intArrayOf(-1, 1)) drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF4DD0E1), Color(0xFF00838F)), startY = headY - R * 0.1f, endY = headY + R * 0.45f), Offset(cx + side * R * 1.07f - R * 0.17f, headY - R * 0.1f), Size(R * 0.35f, R * 0.55f), CornerRadius(R * 0.12f))
    }
    // Arms down in front when resting.
    if (up <= 0.05f && wave <= 0.05f) {
        val handY = h * 0.98f
        for (side in intArrayOf(-1, 1)) {
            drawLine(shade(shirt, 0.1f), Offset(cx + side * w * 0.36f, shoulderY + h * 0.08f), Offset(cx + side * w * 0.2f, handY), armW, StrokeCap.Round)
            drawLine(tint(shirt, 0.1f), Offset(cx + side * w * 0.36f - side * armW * 0.2f, shoulderY + h * 0.08f), Offset(cx + side * w * 0.2f - side * armW * 0.2f, handY), armW * 0.3f, StrokeCap.Round)
        }
    }
}
