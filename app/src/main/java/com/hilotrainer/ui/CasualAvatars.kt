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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

private fun DrawScope.drawPerson(
    look: Look, w: Float, h: Float, armsUp: Float, slump: Float, anger: Float, wave: Float,
    nervous: Boolean, blink: Float, mouth: Float, mood: Mood, t: Float, dealer: Boolean,
) {
    val skin = c(look.skin).let { if (anger > 0f) androidx.compose.ui.graphics.lerp(it, Color(0xFFE5735F), anger * 0.45f) else it }
    val shirt = c(look.shirt); val trim = c(look.shirtTrim); val hairC = c(look.hair)
    val cx = w / 2
    val headR = h * 0.19f
    val headY = h * 0.38f + slump * h * 0.04f
    val shoulderY = h * 0.66f
    // Arms behind the body when raised.
    val armW = w * 0.11f
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
        drawLine(shirt, Offset(sx, sy), end, armW, StrokeCap.Round)
        drawCircle(skin, armW * 0.62f, end)
    }
    val up = armsUp
    if (up > 0.05f || wave > 0.05f) { arm(-1, up, 0f); arm(1, maxOf(up, 0f), wave) }

    // Hair behind the head.
    when (look.hairStyle) {
        Hair.LONG -> drawRoundRect(hairC, Offset(cx - headR * 1.15f, headY - headR * 0.9f), Size(headR * 2.3f, headR * 2.7f), CornerRadius(headR))
        Hair.BOB -> drawRoundRect(hairC, Offset(cx - headR * 1.15f, headY - headR * 1.05f), Size(headR * 2.3f, headR * 2.0f), CornerRadius(headR * 0.9f))
        Hair.CURLY -> for (i in 0 until 9) {
            val a = (PI + i * PI / 8).toFloat()
            drawCircle(hairC, headR * 0.42f, Offset(cx + kotlin.math.cos(a) * headR * 0.95f, headY + sin(a) * headR * 0.85f))
        }
        Hair.BUN -> drawCircle(hairC, headR * 0.5f, Offset(cx, headY - headR * 1.15f))
        Hair.PONYTAIL -> drawOval(hairC, Offset(cx + headR * 0.6f, headY - headR * 0.2f), Size(headR * 0.7f, headR * 1.6f))
        else -> {}
    }
    // Neck and torso.
    drawRect(skin.copy(alpha = 1f), Offset(cx - w * 0.06f, headY + headR * 0.7f), Size(w * 0.12f, shoulderY - headY))
    val body = Path().apply {
        moveTo(cx - w * 0.42f, h)
        lineTo(cx - w * 0.40f, shoulderY + h * 0.06f)
        quadraticTo(cx - w * 0.36f, shoulderY - h * 0.01f, cx - w * 0.18f, shoulderY - h * 0.02f)
        lineTo(cx + w * 0.18f, shoulderY - h * 0.02f)
        quadraticTo(cx + w * 0.36f, shoulderY - h * 0.01f, cx + w * 0.40f, shoulderY + h * 0.06f)
        lineTo(cx + w * 0.42f, h)
        close()
    }
    drawPath(body, shirt)
    // Prints: Hawaiian flowers or plaid.
    when (look.pattern) {
        1 -> for (i in 0 until 7) {
            val px = cx - w * 0.3f + (i % 4) * w * 0.2f; val py = shoulderY + h * 0.08f + (i / 4) * h * 0.13f
            drawCircle(trim, w * 0.035f, Offset(px, py)); drawCircle(Color.White.copy(alpha = 0.8f), w * 0.014f, Offset(px, py))
        }
        2 -> {
            for (i in 0 until 5) drawLine(Color.Black.copy(alpha = 0.28f), Offset(cx - w * 0.38f + i * w * 0.19f, shoulderY), Offset(cx - w * 0.38f + i * w * 0.19f, h), w * 0.03f)
            for (i in 0 until 3) drawLine(Color.Black.copy(alpha = 0.22f), Offset(cx - w * 0.42f, shoulderY + h * 0.06f + i * h * 0.1f), Offset(cx + w * 0.42f, shoulderY + h * 0.06f + i * h * 0.1f), w * 0.025f)
        }
    }
    if (dealer) {
        // White shirt, vest and name tag.
        val v = Path().apply { moveTo(cx - w * 0.12f, shoulderY - h * 0.02f); lineTo(cx, h * 0.92f); lineTo(cx + w * 0.12f, shoulderY - h * 0.02f); close() }
        drawPath(v, Color.White)
        drawRoundRect(Color(0xFFE9C46A), Offset(cx + w * 0.16f, shoulderY + h * 0.1f), Size(w * 0.16f, h * 0.05f), CornerRadius(3f))
    } else {
        // Collar.
        val v = Path().apply { moveTo(cx - w * 0.1f, shoulderY - h * 0.02f); lineTo(cx, shoulderY + h * 0.09f); lineTo(cx + w * 0.1f, shoulderY - h * 0.02f); close() }
        drawPath(v, trim)
        drawPath(Path().apply { moveTo(cx - w * 0.06f, shoulderY - h * 0.02f); lineTo(cx, shoulderY + h * 0.05f); lineTo(cx + w * 0.06f, shoulderY - h * 0.02f); close() }, skin)
    }
    val ex = look.extras
    if (Extra.SASH in ex) drawLine(Color.White, Offset(cx - w * 0.34f, shoulderY), Offset(cx + w * 0.3f, h), w * 0.08f)
    if (Extra.SCARF in ex) drawRoundRect(trim, Offset(cx - w * 0.2f, shoulderY - h * 0.05f), Size(w * 0.4f, h * 0.07f), CornerRadius(w * 0.04f))
    if (Extra.GOLD_CHAIN in ex) drawArc(Color(0xFFFFD54F), 20f, 140f, false, Offset(cx - w * 0.13f, shoulderY - h * 0.1f), Size(w * 0.26f, h * 0.22f), style = Stroke(w * 0.018f))
    if (Extra.PEARLS in ex) for (i in 0..8) {
        val a = (20 + i * 17.5f) * PI.toFloat() / 180f
        drawCircle(Color(0xFFFFFDF5), w * 0.017f, Offset(cx + kotlin.math.cos(a) * w * 0.13f, shoulderY - h * 0.01f + sin(a) * h * 0.08f))
    }
    if (Extra.BOW_TIE in ex) {
        val by = shoulderY + h * 0.01f
        val bow = Path().apply { moveTo(cx, by); lineTo(cx - w * 0.07f, by - h * 0.03f); lineTo(cx - w * 0.07f, by + h * 0.03f); close(); moveTo(cx, by); lineTo(cx + w * 0.07f, by - h * 0.03f); lineTo(cx + w * 0.07f, by + h * 0.03f); close() }
        drawPath(bow, if (dealer) Color(0xFF111111) else trim)
    }
    if (Extra.LANYARD in ex) {
        drawLine(Color(0xFFFF7043), Offset(cx - w * 0.08f, shoulderY - h * 0.02f), Offset(cx, shoulderY + h * 0.16f), w * 0.02f)
        drawLine(Color(0xFFFF7043), Offset(cx + w * 0.08f, shoulderY - h * 0.02f), Offset(cx, shoulderY + h * 0.16f), w * 0.02f)
        drawRect(Color.White, Offset(cx - w * 0.05f, shoulderY + h * 0.15f), Size(w * 0.1f, h * 0.07f))
    }

    val hc = Offset(cx, headY)
    // Head.
    drawCircle(skin, headR, hc)
    drawCircle(skin, headR * 0.22f, Offset(cx - headR * 0.98f, headY + headR * 0.08f))
    drawCircle(skin, headR * 0.22f, Offset(cx + headR * 0.98f, headY + headR * 0.08f))
    // Hair on top.
    when (look.hairStyle) {
        Hair.SHORT, Hair.LONG, Hair.BOB, Hair.PONYTAIL, Hair.BUN -> drawArc(hairC, 180f, 180f, true, Offset(cx - headR * 1.02f, headY - headR * 1.08f), Size(headR * 2.04f, headR * 1.5f))
        Hair.SLICK -> { drawArc(hairC, 175f, 190f, true, Offset(cx - headR * 1.03f, headY - headR * 1.1f), Size(headR * 2.06f, headR * 1.4f)); drawLine(Color.White.copy(alpha = 0.25f), Offset(cx - headR * 0.5f, headY - headR * 0.8f), Offset(cx + headR * 0.4f, headY - headR * 0.9f), headR * 0.08f) }
        Hair.CURLY -> for (i in 0 until 5) drawCircle(hairC, headR * 0.38f, Offset(cx - headR * 0.7f + i * headR * 0.35f, headY - headR * 0.82f))
        Hair.BUZZ -> drawArc(hairC.copy(alpha = 0.85f), 180f, 180f, true, Offset(cx - headR, headY - headR * 1.0f), Size(headR * 2f, headR * 1.2f))
        Hair.BALD -> drawCircle(Color.White.copy(alpha = 0.18f), headR * 0.3f, Offset(cx - headR * 0.3f, headY - headR * 0.55f))
    }
    // Face.
    val eyeY = headY + headR * 0.05f
    val eyeDx = headR * 0.38f
    val look2 = if (nervous) sin(t * 2 * PI.toFloat()) * headR * 0.12f else 0f
    val eyeColor = Color(0xFF231A14)
    if (Extra.SUNGLASSES in ex) {
        drawRoundRect(Color(0xFF111111), Offset(cx - eyeDx - headR * 0.3f, eyeY - headR * 0.18f), Size(headR * 0.6f, headR * 0.36f), CornerRadius(headR * 0.12f))
        drawRoundRect(Color(0xFF111111), Offset(cx + eyeDx - headR * 0.3f, eyeY - headR * 0.18f), Size(headR * 0.6f, headR * 0.36f), CornerRadius(headR * 0.12f))
        drawLine(Color(0xFF111111), Offset(cx - eyeDx + headR * 0.3f, eyeY - headR * 0.08f), Offset(cx + eyeDx - headR * 0.3f, eyeY - headR * 0.08f), headR * 0.07f)
        drawLine(Color.White.copy(alpha = 0.35f), Offset(cx - eyeDx - headR * 0.18f, eyeY - headR * 0.08f), Offset(cx - eyeDx, eyeY - headR * 0.1f), headR * 0.05f)
    } else {
        val closed = blink > 0.5f || mood == Mood.CHEER
        if (closed) {
            drawArc(eyeColor, 200f, 140f, false, Offset(cx - eyeDx - headR * 0.13f, eyeY - headR * 0.08f), Size(headR * 0.26f, headR * 0.18f), style = Stroke(headR * 0.07f))
            drawArc(eyeColor, 200f, 140f, false, Offset(cx + eyeDx - headR * 0.13f, eyeY - headR * 0.08f), Size(headR * 0.26f, headR * 0.18f), style = Stroke(headR * 0.07f))
        } else {
            drawCircle(Color.White, headR * 0.14f, Offset(cx - eyeDx, eyeY)); drawCircle(Color.White, headR * 0.14f, Offset(cx + eyeDx, eyeY))
            drawCircle(eyeColor, headR * 0.085f, Offset(cx - eyeDx + look2, eyeY + slump * headR * 0.05f)); drawCircle(eyeColor, headR * 0.085f, Offset(cx + eyeDx + look2, eyeY + slump * headR * 0.05f))
        }
        if (Extra.GLASSES in ex) {
            drawCircle(Color(0xFF3B2F2A), headR * 0.25f, Offset(cx - eyeDx, eyeY), style = Stroke(headR * 0.06f))
            drawCircle(Color(0xFF3B2F2A), headR * 0.25f, Offset(cx + eyeDx, eyeY), style = Stroke(headR * 0.06f))
            drawLine(Color(0xFF3B2F2A), Offset(cx - eyeDx + headR * 0.25f, eyeY), Offset(cx + eyeDx - headR * 0.25f, eyeY), headR * 0.05f)
        }
    }
    // Brows: angry slants down, sad slants up.
    val browY = eyeY - headR * 0.3f
    val tilt = (anger - slump * 0.8f) * headR * 0.12f
    drawLine(hairC.copy(alpha = 0.9f), Offset(cx - eyeDx - headR * 0.15f, browY - tilt), Offset(cx - eyeDx + headR * 0.15f, browY + tilt), headR * 0.07f, StrokeCap.Round)
    drawLine(hairC.copy(alpha = 0.9f), Offset(cx + eyeDx - headR * 0.15f, browY + tilt), Offset(cx + eyeDx + headR * 0.15f, browY - tilt), headR * 0.07f, StrokeCap.Round)
    // Nose.
    drawLine(Color.Black.copy(alpha = 0.18f), Offset(cx, eyeY + headR * 0.12f), Offset(cx + headR * 0.06f, eyeY + headR * 0.32f), headR * 0.06f, StrokeCap.Round)
    // Mouth.
    val my = headY + headR * 0.55f
    val mw = headR * 0.5f
    val mouthColor = Color(0xFF7A2E2A)
    when {
        mouth > 0.5f || mood == Mood.CHEER -> drawOval(mouthColor, Offset(cx - mw * 0.35f, my - headR * 0.1f), Size(mw * 0.7f, headR * (0.22f + mouth * 0.1f)))
        mood == Mood.HAPPY || mood == Mood.WAVE -> drawArc(mouthColor, 10f, 160f, false, Offset(cx - mw / 2, my - headR * 0.2f), Size(mw, headR * 0.3f), style = Stroke(headR * 0.08f, cap = StrokeCap.Round))
        mood == Mood.SAD || mood == Mood.ANGRY -> drawArc(mouthColor, 200f, 140f, false, Offset(cx - mw / 2, my), Size(mw, headR * 0.25f), style = Stroke(headR * 0.08f, cap = StrokeCap.Round))
        mood == Mood.NERVOUS -> drawLine(mouthColor, Offset(cx - mw * 0.35f, my + headR * 0.02f), Offset(cx + mw * 0.35f, my - headR * 0.02f), headR * 0.07f, StrokeCap.Round)
        else -> drawArc(mouthColor, 20f, 140f, false, Offset(cx - mw * 0.4f, my - headR * 0.1f), Size(mw * 0.8f, headR * 0.18f), style = Stroke(headR * 0.07f, cap = StrokeCap.Round))
    }
    if (Extra.MUSTACHE in ex) drawArc(hairC, 180f, 180f, true, Offset(cx - mw * 0.55f, my - headR * 0.22f), Size(mw * 1.1f, headR * 0.3f))
    if (Extra.BEARD in ex) drawArc(hairC.copy(alpha = 0.92f), 0f, 180f, true, Offset(cx - headR * 0.85f, headY - headR * 0.05f), Size(headR * 1.7f, headR * 1.2f), style = Stroke(headR * 0.22f))
    if (nervous) drawCircle(Color(0xFF9AD8FF), headR * 0.1f, Offset(cx + headR * 0.85f, headY - headR * 0.45f + (t * headR * 0.4f)))
    if (anger > 0.6f) for (i in 0..1) drawLine(Color(0xFFD32F2F), Offset(cx + headR * (0.7f + i * 0.15f), headY - headR * 1.1f), Offset(cx + headR * (0.8f + i * 0.15f), headY - headR * 1.3f), headR * 0.08f)
    // Hats.
    val hatC = c(look.hatColor)
    when (look.hat) {
        Hat.BUCKET -> { drawOval(hatC, Offset(cx - headR * 1.35f, headY - headR * 0.85f), Size(headR * 2.7f, headR * 0.5f)); drawRoundRect(hatC, Offset(cx - headR * 0.9f, headY - headR * 1.45f), Size(headR * 1.8f, headR * 0.8f), CornerRadius(headR * 0.4f)) }
        Hat.CAP -> { drawArc(hatC, 180f, 180f, true, Offset(cx - headR * 1.05f, headY - headR * 1.2f), Size(headR * 2.1f, headR * 1.5f)); drawOval(hatC, Offset(cx - headR * 0.2f, headY - headR * 0.6f), Size(headR * 1.5f, headR * 0.35f)) }
        Hat.VISOR -> drawOval(hatC, Offset(cx - headR * 1.1f, headY - headR * 0.75f), Size(headR * 2.2f, headR * 0.4f))
        Hat.TIARA -> {
            val p = Path().apply { moveTo(cx - headR * 0.6f, headY - headR * 0.8f); lineTo(cx - headR * 0.35f, headY - headR * 1.2f); lineTo(cx - headR * 0.15f, headY - headR * 0.95f); lineTo(cx, headY - headR * 1.35f); lineTo(cx + headR * 0.15f, headY - headR * 0.95f); lineTo(cx + headR * 0.35f, headY - headR * 1.2f); lineTo(cx + headR * 0.6f, headY - headR * 0.8f); close() }
            drawPath(p, hatC); drawCircle(Color(0xFFF48FB1), headR * 0.08f, Offset(cx, headY - headR * 1.15f))
        }
        Hat.BEANIE -> { drawArc(hatC, 180f, 180f, true, Offset(cx - headR * 1.05f, headY - headR * 1.35f), Size(headR * 2.1f, headR * 1.7f)); drawRect(hatC.copy(alpha = 0.75f), Offset(cx - headR * 1.05f, headY - headR * 0.62f), Size(headR * 2.1f, headR * 0.22f)); drawCircle(hatC, headR * 0.2f, Offset(cx, headY - headR * 1.38f)) }
        Hat.FEDORA -> { drawOval(hatC, Offset(cx - headR * 1.4f, headY - headR * 0.85f), Size(headR * 2.8f, headR * 0.4f)); drawRoundRect(hatC, Offset(cx - headR * 0.85f, headY - headR * 1.5f), Size(headR * 1.7f, headR * 0.85f), CornerRadius(headR * 0.25f)) }
        Hat.NONE -> {}
    }
    if (Extra.HEADPHONES in ex) {
        drawArc(Color(0xFF212121), 180f, 180f, false, Offset(cx - headR * 1.1f, headY - headR * 1.15f), Size(headR * 2.2f, headR * 2.0f), style = Stroke(headR * 0.12f))
        drawRoundRect(Color(0xFF00BCD4), Offset(cx - headR * 1.25f, headY - headR * 0.1f), Size(headR * 0.35f, headR * 0.55f), CornerRadius(headR * 0.12f))
        drawRoundRect(Color(0xFF00BCD4), Offset(cx + headR * 0.9f, headY - headR * 0.1f), Size(headR * 0.35f, headR * 0.55f), CornerRadius(headR * 0.12f))
    }
    // Arms down in front when resting: hands on the rail.
    if (up <= 0.05f && wave <= 0.05f) {
        val handY = h * 0.98f
        drawLine(shirt, Offset(cx - w * 0.36f, shoulderY + h * 0.08f), Offset(cx - w * 0.2f, handY), armW, StrokeCap.Round)
        drawLine(shirt, Offset(cx + w * 0.36f, shoulderY + h * 0.08f), Offset(cx + w * 0.2f, handY), armW, StrokeCap.Round)
        drawLine(Color.Black.copy(alpha = 0.18f), Offset(cx - w * 0.36f, shoulderY + h * 0.08f), Offset(cx - w * 0.2f, handY), armW * 0.15f)
    }
}
