package com.hilotrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hilotrainer.engine.Rarity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The little picture on a perk. Drawn in code, one shape for each family of perk, tinted by what it does. */
enum class PerkIcon { COIN, STAR, BOLT, SHIELD, HEART, DIAMOND, SPADE, CROWN, ARROW, CARDS, STACK, GHOST, CLOVER }

fun perkIconOf(id: String): PerkIcon = when (id) {
    "sevens", "natural", "veteran", "sharp", "wild", "scholar" -> PerkIcon.STAR
    "court", "baron", "bossbane" -> PerkIcon.CROWN
    "ace", "acehigh", "spotter", "sharpshooter" -> PerkIcon.SPADE
    "tenstack", "opener", "collector", "allin", "lowball", "tell" -> PerkIcon.STACK
    "soft", "sore", "consol", "heavy" -> PerkIcon.HEART
    "twenty", "minimal", "bustbuster", "overkill", "hot", "echo", "chain", "combo", "lastcall" -> PerkIcon.BOLT
    "pairup", "trouble", "splitter", "charlie", "bettor", "copy", "cut", "pockets" -> PerkIcon.CARDS
    "rainbow", "flush", "roller", "gambler", "contrarian" -> PerkIcon.DIAMOND
    "snow", "comeback", "grinder", "trainer" -> PerkIcon.ARROW
    "penny", "tipjar", "ticket", "payday", "parlay", "banker", "fat" -> PerkIcon.COIN
    "net", "insure" -> PerkIcon.SHIELD
    "ghost" -> PerkIcon.GHOST
    else -> PerkIcon.CLOVER
}

private fun DrawScope.star(c: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 10) { val a = -PI.toFloat() / 2 + i * PI.toFloat() / 5; val rr = if (i % 2 == 0) r else r * 0.45f; val x = c.x + cos(a) * rr; val y = c.y + sin(a) * rr; if (i == 0) moveTo(x, y) else lineTo(x, y) }
    close()
}

private fun DrawScope.heart(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y + r * 0.9f)
    cubicTo(c.x - r * 1.4f, c.y + r * 0.05f, c.x - r * 0.95f, c.y - r * 0.95f, c.x, c.y - r * 0.3f)
    cubicTo(c.x + r * 0.95f, c.y - r * 0.95f, c.x + r * 1.4f, c.y + r * 0.05f, c.x, c.y + r * 0.9f)
    close()
}

/** The icon shapes, each drawn inside a box of [s] pixels at [c]. [fill] is the main color. */
private fun DrawScope.drawIcon(icon: PerkIcon, c: Offset, s: Float, fill: Color) {
    val ink = EdgeInk
    val hi = Color.White.copy(alpha = 0.55f)
    fun solid(p: Path) { drawPath(p, ink, style = Stroke(s * 0.16f)); drawPath(p, fill) }
    when (icon) {
        PerkIcon.COIN -> {
            drawCircle(ink, s * 0.5f, c); drawCircle(fill, s * 0.43f, c)
            drawCircle(Color.Black.copy(alpha = 0.25f), s * 0.3f, c, style = Stroke(s * 0.07f))
            drawCircle(hi, s * 0.1f, Offset(c.x - s * 0.17f, c.y - s * 0.19f))
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(c.x, c.y - s * 0.2f), Offset(c.x, c.y + s * 0.2f), s * 0.09f)
        }
        PerkIcon.STAR -> { val p = star(c, s * 0.52f); solid(p); drawCircle(hi, s * 0.07f, Offset(c.x - s * 0.1f, c.y - s * 0.12f)) }
        PerkIcon.BOLT -> {
            val p = Path().apply { moveTo(c.x + s * 0.12f, c.y - s * 0.52f); lineTo(c.x - s * 0.3f, c.y + s * 0.06f); lineTo(c.x - s * 0.02f, c.y + s * 0.06f); lineTo(c.x - s * 0.14f, c.y + s * 0.52f); lineTo(c.x + s * 0.32f, c.y - s * 0.12f); lineTo(c.x + s * 0.04f, c.y - s * 0.12f); close() }
            solid(p)
        }
        PerkIcon.SHIELD -> {
            val p = Path().apply { moveTo(c.x - s * 0.4f, c.y - s * 0.4f); lineTo(c.x + s * 0.4f, c.y - s * 0.4f); lineTo(c.x + s * 0.4f, c.y + s * 0.05f); quadraticTo(c.x + s * 0.38f, c.y + s * 0.4f, c.x, c.y + s * 0.55f); quadraticTo(c.x - s * 0.38f, c.y + s * 0.4f, c.x - s * 0.4f, c.y + s * 0.05f); close() }
            solid(p); drawLine(hi, Offset(c.x - s * 0.22f, c.y - s * 0.28f), Offset(c.x - s * 0.22f, c.y + s * 0.06f), s * 0.08f)
        }
        PerkIcon.HEART -> { val p = heart(c, s * 0.5f); solid(p); drawCircle(hi, s * 0.07f, Offset(c.x - s * 0.22f, c.y - s * 0.12f)) }
        PerkIcon.DIAMOND -> {
            val p = Path().apply { moveTo(c.x, c.y - s * 0.55f); lineTo(c.x + s * 0.42f, c.y); lineTo(c.x, c.y + s * 0.55f); lineTo(c.x - s * 0.42f, c.y); close() }
            solid(p); drawLine(hi, Offset(c.x - s * 0.12f, c.y - s * 0.1f), Offset(c.x, c.y - s * 0.3f), s * 0.07f)
        }
        PerkIcon.SPADE -> {
            val p = Path().apply {
                moveTo(c.x, c.y - s * 0.55f)
                cubicTo(c.x + s * 0.2f, c.y - s * 0.25f, c.x + s * 0.55f, c.y - s * 0.05f, c.x + s * 0.4f, c.y + s * 0.2f)
                cubicTo(c.x + s * 0.3f, c.y + s * 0.38f, c.x + s * 0.08f, c.y + s * 0.3f, c.x + s * 0.06f, c.y + s * 0.2f)
                lineTo(c.x + s * 0.14f, c.y + s * 0.5f); lineTo(c.x - s * 0.14f, c.y + s * 0.5f); lineTo(c.x - s * 0.06f, c.y + s * 0.2f)
                cubicTo(c.x - s * 0.08f, c.y + s * 0.3f, c.x - s * 0.3f, c.y + s * 0.38f, c.x - s * 0.4f, c.y + s * 0.2f)
                cubicTo(c.x - s * 0.55f, c.y - s * 0.05f, c.x - s * 0.2f, c.y - s * 0.25f, c.x, c.y - s * 0.55f); close()
            }
            solid(p)
        }
        PerkIcon.CROWN -> {
            val p = Path().apply {
                moveTo(c.x - s * 0.48f, c.y + s * 0.38f); lineTo(c.x - s * 0.5f, c.y - s * 0.3f); lineTo(c.x - s * 0.2f, c.y + s * 0.02f); lineTo(c.x, c.y - s * 0.46f)
                lineTo(c.x + s * 0.2f, c.y + s * 0.02f); lineTo(c.x + s * 0.5f, c.y - s * 0.3f); lineTo(c.x + s * 0.48f, c.y + s * 0.38f); close()
            }
            solid(p); drawLine(ink, Offset(c.x - s * 0.42f, c.y + s * 0.24f), Offset(c.x + s * 0.42f, c.y + s * 0.24f), s * 0.07f)
        }
        PerkIcon.ARROW -> {
            val p = Path().apply { moveTo(c.x, c.y - s * 0.55f); lineTo(c.x + s * 0.5f, c.y + s * 0.05f); lineTo(c.x + s * 0.18f, c.y + s * 0.05f); lineTo(c.x + s * 0.18f, c.y + s * 0.5f); lineTo(c.x - s * 0.18f, c.y + s * 0.5f); lineTo(c.x - s * 0.18f, c.y + s * 0.05f); lineTo(c.x - s * 0.5f, c.y + s * 0.05f); close() }
            solid(p)
        }
        PerkIcon.CARDS -> {
            for (k in 0..1) {
                val dx = (k - 0.5f) * s * 0.34f
                val rot = (k - 0.5f) * 22f
                rotate(rot, Offset(c.x + dx, c.y + s * 0.2f)) {
                    drawRoundRect(ink, Offset(c.x + dx - s * 0.27f, c.y - s * 0.46f), androidx.compose.ui.geometry.Size(s * 0.54f, s * 0.84f), androidx.compose.ui.geometry.CornerRadius(s * 0.1f))
                    drawRoundRect(if (k == 0) Color(0xFFEFE6CF) else fill, Offset(c.x + dx - s * 0.22f, c.y - s * 0.41f), androidx.compose.ui.geometry.Size(s * 0.44f, s * 0.74f), androidx.compose.ui.geometry.CornerRadius(s * 0.07f))
                }
            }
        }
        PerkIcon.STACK -> {
            for (k in 0..2) {
                val y = c.y + s * 0.3f - k * s * 0.24f
                drawRoundRect(ink, Offset(c.x - s * 0.46f, y - s * 0.12f), androidx.compose.ui.geometry.Size(s * 0.92f, s * 0.3f), androidx.compose.ui.geometry.CornerRadius(s * 0.14f))
                drawRoundRect(if (k % 2 == 0) fill else Color.White.copy(alpha = 0.92f), Offset(c.x - s * 0.4f, y - s * 0.16f), androidx.compose.ui.geometry.Size(s * 0.8f, s * 0.26f), androidx.compose.ui.geometry.CornerRadius(s * 0.12f))
            }
        }
        PerkIcon.GHOST -> {
            val p = Path().apply {
                moveTo(c.x - s * 0.4f, c.y + s * 0.5f); lineTo(c.x - s * 0.4f, c.y - s * 0.1f)
                cubicTo(c.x - s * 0.4f, c.y - s * 0.75f, c.x + s * 0.4f, c.y - s * 0.75f, c.x + s * 0.4f, c.y - s * 0.1f)
                lineTo(c.x + s * 0.4f, c.y + s * 0.5f); lineTo(c.x + s * 0.2f, c.y + s * 0.34f); lineTo(c.x, c.y + s * 0.5f); lineTo(c.x - s * 0.2f, c.y + s * 0.34f); close()
            }
            solid(p); drawCircle(ink, s * 0.07f, Offset(c.x - s * 0.15f, c.y - s * 0.12f)); drawCircle(ink, s * 0.07f, Offset(c.x + s * 0.15f, c.y - s * 0.12f))
        }
        PerkIcon.CLOVER -> {
            for (a in 0 until 4) { val ang = a * PI.toFloat() / 2 + PI.toFloat() / 4; val o = Offset(c.x + cos(ang) * s * 0.22f, c.y + sin(ang) * s * 0.22f); drawCircle(ink, s * 0.27f, o); drawCircle(fill, s * 0.22f, o) }
            drawLine(ink, c, Offset(c.x + s * 0.2f, c.y + s * 0.55f), s * 0.1f)
        }
    }
}

/** A perk's picture on a glowing medallion. Rarity sets the glow, what the perk does sets the color. */
@Composable
fun PerkArt(id: String, rarity: Rarity, tint: Color, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val glow = rarityColor(rarity)
    val t = rememberLoop(3200)
    Canvas(modifier.size(size)) {
        val w = this.size.width; val c = Offset(w / 2, w / 2)
        val pulse = if (animOn) 0.85f + 0.15f * sin(t * 2f * PI.toFloat()) else 1f
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.55f * pulse), Color.Transparent), c, w * 0.62f), w * 0.62f, c)
        drawCircle(EdgeInk, w * 0.43f, c)
        drawCircle(Brush.verticalGradient(listOf(Color(0xFF3A3166), Color(0xFF1B1636)), startY = 0f, endY = w), w * 0.4f, c)
        drawCircle(glow.copy(alpha = 0.8f), w * 0.4f, c, style = Stroke(w * 0.035f))
        drawIcon(perkIconOf(id), c, w * 0.52f, tint)
        if (rarity != Rarity.COMMON) {
            val a = if (animOn) t * 2f * PI.toFloat() else 0.5f
            val sp = Offset(c.x + cos(a) * w * 0.4f, c.y + sin(a) * w * 0.4f)
            drawCircle(Color.White.copy(alpha = 0.9f), w * 0.04f, sp)
        }
    }
}

/** What a perk does decides its color: chips blue, mult red, big multipliers gold, money green. */
fun perkTint(badgeColor: Color): Color = badgeColor
