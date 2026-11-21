package com.hilotrainer.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.ColorUp
import com.hilotrainer.engine.ShuffleStyle

// ---------- v7 table realism ----------

/**
 * The dealer's chip rack: columns of chips by color, with true stack heights.
 * Columns refill slowly as the house wins, so it looks alive.
 */
@Composable
internal fun DealerRack(vm: PlayViewModel, modifier: Modifier = Modifier) {
    val colors = listOf(Color(0xFF6A2C91), Color(0xFF1C1C1C), Color(0xFF1C1C1C), Color(0xFF2E7D32), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFFC62828), Color(0xFFC62828), Color(0xFF1E63C6), Color(0xFFE0E0E0))
    val seed = vm.state.roundStartSeq
    Column(modifier.semantics { contentDescription = "Dealer ${vm.dealer.name} with the chip rack" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(132.dp, 16.dp)) {
            val r = 3.dp.toPx()
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3A2A1A), Color(0xFF1A120A))), cornerRadius = CornerRadius(r))
            val n = colors.size
            val slot = (size.width - 6.dp.toPx()) / n
            for (i in 0 until n) {
                val x = 3.dp.toPx() + slot * i
                // Height of each column, varied a little round to round.
                val fill = 0.55f + 0.4f * (((i * 31 + seed * 7) % 10) / 10f)
                val h = (size.height - 4.dp.toPx()) * fill
                val top = size.height - 2.dp.toPx() - h
                drawRect(colors[i], Offset(x + 1f, top), Size(slot - 2f, h))
                var y = top + 2.dp.toPx()
                while (y < size.height - 2.dp.toPx()) {
                    drawLine(Color.Black.copy(alpha = 0.25f), Offset(x + 1f, y), Offset(x + slot - 1f, y), 1f)
                    y += 2.dp.toPx()
                }
                drawRect(Color.White.copy(alpha = 0.18f), Offset(x + 1f, top), Size(slot - 2f, 1.2f))
            }
            drawRoundRect(Gold.copy(alpha = 0.5f), cornerRadius = CornerRadius(r), style = Stroke(1f))
        }
        // Brass nameplate
        Box(
            Modifier.padding(top = 2.dp).clip(RoundedCornerShape(3.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFE6C66B), Color(0xFFA9822F))))
                .padding(horizontal = 8.dp),
        ) {
            Text(vm.dealer.name.uppercase(), fontSize = 9.sp, lineHeight = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A1C05))
        }
    }
}

/** The acrylic sign on the table with the limits and rules. */
@Composable
internal fun TablePlacard(vm: PlayViewModel, modifier: Modifier = Modifier) {
    val r = vm.settings
    val lines = listOf(
        "\$10 MIN",
        "${r.decks} DECK${if (r.decks == 1) "" else "S"}",
        if (r.dealerHitsSoft17) "H17" else "S17",
        if (r.sixToFive) "BJ 6:5" else "BJ 3:2",
    )
    Column(
        modifier.clip(RoundedCornerShape(3.dp)).background(Color(0xE6101010)).border(0.8.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { contentDescription = "Table sign: " + lines.joinToString(", ") },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { Text(it, fontSize = 8.sp, lineHeight = 9.sp, color = if (it.endsWith("6:5")) Color(0xFFFF8A80) else Gold, fontWeight = FontWeight.Bold) }
    }
}

/** The dark glass dome of the eye in the sky. */
@Composable
internal fun CameraDome(modifier: Modifier = Modifier) {
    Canvas(modifier.size(22.dp, 11.dp).semantics { contentDescription = "Security camera" }) {
        drawArc(Brush.radialGradient(listOf(Color(0xFF3A3A44), Color(0xFF0A0A0E)), center = Offset(size.width * 0.4f, size.height * 0.3f), radius = size.width), 180f, 180f, true, Offset(0f, 0f), Size(size.width, size.height * 2))
        drawLine(Color(0xFF555560), Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.5f)
        drawCircle(Color(0xFFE53935), 1.2.dp.toPx(), Offset(size.width * 0.7f, size.height * 0.55f))
    }
}

/** A suited silhouette: the pit boss. */
@Composable
internal fun PitBossFigure(modifier: Modifier = Modifier) {
    Canvas(modifier.size(30.dp, 40.dp)) {
        val w = size.width; val h = size.height
        val body = Path().apply {
            moveTo(w * 0.08f, h); lineTo(w * 0.12f, h * 0.52f)
            quadraticTo(w * 0.5f, h * 0.36f, w * 0.88f, h * 0.52f)
            lineTo(w * 0.92f, h); close()
        }
        drawPath(body, Color(0xE6151515))
        // Shirt and tie
        val shirt = Path().apply { moveTo(w * 0.4f, h * 0.45f); lineTo(w * 0.5f, h * 0.75f); lineTo(w * 0.6f, h * 0.45f); close() }
        drawPath(shirt, Color(0xFFDDDDDD))
        drawLine(Color(0xFF8E2A2A), Offset(w * 0.5f, h * 0.47f), Offset(w * 0.5f, h * 0.72f), w * 0.07f)
        drawCircle(Color(0xE6151515), w * 0.2f, Offset(w * 0.5f, h * 0.27f))
        // Earpiece light
        drawCircle(Color(0xFF90CAF9), 1.2.dp.toPx(), Offset(w * 0.68f, h * 0.27f))
    }
}

/**
 * The pit boss stands behind the dealer when your heat is high, and now and then
 * strolls past the table.
 */
@Composable
internal fun PitBoss(vm: PlayViewModel, width: Dp, modifier: Modifier = Modifier) {
    val walk = remember { Animatable(-1f) }
    LaunchedEffect(vm.pitBossWalkId) {
        if (vm.pitBossWalkId != 0L && !vm.pitBossWatching) {
            walk.snapTo(0f)
            walk.animateTo(if (vm.instant) 0.45f else 1f, tween(if (vm.instant) 1 else 4200, easing = LinearEasing))
            if (!vm.instant) walk.snapTo(-1f)
        }
    }
    val density = LocalDensity.current
    val standX = width * 0.30f
    when {
        vm.pitBossWatching -> Box(modifier.offset(x = standX)) {
            PitBossFigure(Modifier.semantics { contentDescription = "The pit boss is watching your table" })
        }
        walk.value >= 0f -> Box(
            modifier.graphicsLayer {
                translationX = with(density) { (-30.dp + (width + 30.dp) * walk.value).toPx() }
            },
        ) { PitBossFigure(Modifier.semantics { contentDescription = "The pit boss walks past" }) }
    }
}

/**
 * The dealer's hand: it slides from the shoe out to where the last card landed,
 * then drifts back to rest beside the shoe.
 */
@Composable
internal fun DealerHandOverlay(vm: PlayViewModel, width: Dp, height: Dp) {
    val target = remember { Animatable(0f) }
    LaunchedEffect(vm.visibleSeq) {
        if (vm.animating && !vm.instant) {
            target.snapTo(0f)
            target.animateTo(1f, tween((vm.settings.dealDelayMs(vm.ladderRung) * 0.45).toInt().coerceAtLeast(90), easing = FastOutSlowInEasing))
            target.animateTo(0f, tween(260))
        } else target.snapTo(0f)
    }
    // The last card dealt: which seat it went to decides how far the hand travels.
    val seq = vm.visibleSeq - 1
    val s = vm.state
    val toDealer = s.dealerSeqs.contains(seq)
    val toBot = s.bots.indexOfFirst { it.seqs.contains(seq) }
    val tx = when {
        toDealer -> width * 0.5f
        toBot >= 0 -> width * ((toBot + 0.5f) / s.bots.size.coerceAtLeast(1))
        else -> width * 0.45f
    }
    val ty = when {
        toDealer -> height * 0.14f
        toBot >= 0 -> height * 0.42f
        else -> height * 0.62f
    }
    // Resting just in front of the shoe.
    val restX = width - 118.dp
    val restY = 22.dp
    val p = target.value
    val density = LocalDensity.current
    if (!vm.animating && p == 0f) {
        // Resting hand by the shoe
        DealerHandShape(Modifier.graphicsLayer { translationX = with(density) { restX.toPx() }; translationY = with(density) { restY.toPx() } })
        return
    }
    DealerHandShape(
        Modifier.graphicsLayer {
            translationX = with(density) { (restX + (tx - restX) * p).toPx() }
            translationY = with(density) { (restY + (ty - restY) * p).toPx() }
            rotationZ = -12f * p
        },
    )
}

@Composable
private fun DealerHandShape(modifier: Modifier) {
    Canvas(modifier.size(30.dp, 22.dp)) {
        val w = size.width; val h = size.height
        // White shirt cuff and a hand reaching across the felt.
        drawRoundRect(Color(0xFFF2F2F2), Offset(w * 0.62f, h * 0.25f), Size(w * 0.38f, h * 0.5f), CornerRadius(3f))
        drawRoundRect(Color(0xFF1B1B1B), Offset(w * 0.6f, h * 0.25f), Size(w * 0.05f, h * 0.5f))
        drawOval(Color(0xFFD7A783), Offset(w * 0.16f, h * 0.18f), Size(w * 0.5f, h * 0.64f))
        for (i in 0 until 4) {
            drawRoundRect(Color(0xFFCB9A76), Offset(w * 0.02f, h * (0.2f + i * 0.16f)), Size(w * 0.24f, h * 0.13f), CornerRadius(4f))
        }
        drawOval(Color.Black.copy(alpha = 0.15f), Offset(w * 0.18f, h * 0.7f), Size(w * 0.5f, h * 0.25f))
    }
}

/**
 * The shuffle, in stages: the riffle, your cut, and the burn card.
 * Stage 2 waits for you to tap where to put the cut card.
 */
@Composable
internal fun ShuffleStages(vm: PlayViewModel, modifier: Modifier = Modifier) {
    val stage = vm.shuffleStage
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Ink.copy(alpha = 0.8f)).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (stage) {
            2 -> CutPrompt(vm)
            3 -> BurnCard()
            else -> Riffle(machine = vm.settings.shuffleStyle == ShuffleStyle.MACHINE)
        }
    }
}

@Composable
private fun Riffle(machine: Boolean) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Restart)) }
    Box(Modifier.size(170.dp, 80.dp), contentAlignment = Alignment.Center) {
        if (machine) {
            Box(Modifier.size(90.dp, 54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF263238)).border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(8.dp)))
            CardBack(30.dp, Modifier.offset(y = (-20 + 20 * t.value).dp))
        } else {
            // Two halves riffle together, then the stack is stripped and squared.
            val p = t.value
            for (i in 0 until 6) {
                val left = i % 2 == 0
                val start = if (left) -56f else 56f
                val drop = ((p * 6f) - i).coerceIn(0f, 1f)
                CardBack(
                    34.dp,
                    Modifier.offset(x = (start * (1 - drop)).dp, y = (-i * 1.5f).dp)
                        .rotate(if (left) -14f * (1 - drop) else 14f * (1 - drop)),
                )
            }
        }
    }
    Text(if (machine) "Into the shuffle machine" else "Riffle and strip", fontFamily = TitleFont, fontSize = 18.sp, color = Gold)
}

@Composable
private fun CutPrompt(vm: PlayViewModel) {
    // The prompt is above the deck so your finger never covers it.
    Text("Your cut", fontFamily = TitleFont, fontSize = 18.sp, color = Gold)
    Text("Tap the deck where you want the cut card", fontSize = 13.sp, color = Cream, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    BoxWithConstraints(
        Modifier.width(220.dp).height(46.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFF4EEDF), Color(0xFFD9D1BE))))
            .clickable { vm.cutDeck() }
            .semantics { contentDescription = "Cut the deck" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            var x = 0f
            var i = 0
            while (x < size.width) {
                drawLine(Color(0xFF9E1B2C).copy(alpha = if (i % 3 == 0) 0.4f else 0.18f), Offset(x, 0f), Offset(x, size.height), 1f)
                x += 2.5f; i++
            }
        }
    }
    Text("Cutting in a few seconds if you don't", fontSize = 11.sp, color = Cream.copy(alpha = 0.6f))
}

@Composable
private fun BurnCard() {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
    Box(Modifier.size(170.dp, 70.dp)) {
        CardBack(34.dp, Modifier.offset(x = (130 - 130 * t.value).dp, y = 4.dp).rotate(-20f * t.value))
    }
    Text("Burn card", fontFamily = TitleFont, fontSize = 18.sp, color = Gold)
    Text("Goes face down to the tray. Don't count it.", fontSize = 12.sp, color = Cream.copy(alpha = 0.8f))
}

/** The padded leather armrest along the player's edge of the table. */
@Composable
internal fun PaddedRail(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF3B1D12), Color(0xFF1C0B05))))
        drawLine(Color.White.copy(alpha = 0.14f), Offset(0f, 1.5f), Offset(size.width, 1.5f), 1.5f)
        var x = 18.dp.toPx()
        while (x < size.width) {
            drawCircle(Color.Black.copy(alpha = 0.35f), 1.2f, Offset(x, size.height * 0.6f))
            x += 36.dp.toPx()
        }
    }
}

/** The seated view: the table leans back like you're in the chair, and it tips a little as you tilt the phone. */
@Composable
internal fun Modifier.seatedView(on: Boolean): Modifier {
    if (!on) return this
    val ctx = LocalContext.current
    var tilt by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                // y and z gravity give the pitch; a phone held at 45 degrees is "neutral".
                val pitch = Math.toDegrees(kotlin.math.atan2(e.values[1].toDouble(), e.values[2].toDouble())).toFloat()
                tilt = tilt * 0.85f + ((pitch - 45f) / 8f).coerceIn(-4f, 4f) * 0.15f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(l) }
    }
    val shown by animateFloatAsState(tilt, tween(120), label = "tilt")
    return this.graphicsLayer {
        rotationX = 14f + shown
        transformOrigin = TransformOrigin(0.5f, 1f)
        cameraDistance = 10f * density
    }
}

/** Buy in at the table: hand the dealer cash and get chips, or keep playing the chips you have. */
@Composable
internal fun BuyInDock(vm: PlayViewModel) {
    val have = vm.state.bankroll
    val canKeep = have >= 10 && vm.colorUpText == null && !vm.justColoredUp
    Text(if (canKeep) "Sit down: keep your chips or buy in fresh" else "Buy in: how much cash to change?", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(500, 1000, 2000, 5000).forEach { a ->
            GoldButton(money(a), Modifier.weight(1f), primary = !canKeep && a == 1000) { vm.buyIn(a) }
        }
    }
    if (canKeep) GoldButton("Keep my ${money(have)} in chips", Modifier.fillMaxWidth()) { vm.keepChips() }
    else Text("The dealer counts your cash and pushes you the chips.", fontSize = 12.sp, color = Cream.copy(alpha = 0.65f), maxLines = 1)
}

/** Someone at the table asks you something, and you have to answer before you can play on. */
@Composable
internal fun QuestionDock(vm: PlayViewModel, d: Distraction) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        d.options.forEachIndexed { i, o ->
            GoldButton(o, Modifier.weight(1f), primary = false) { vm.answerDistraction(i) }
        }
    }
    Text("Answer to keep playing. The cards keep coming.", fontSize = 12.sp, color = Cream.copy(alpha = 0.65f), maxLines = 1)
}

/**
 * Table distractions drawn over the felt: a speech bubble at a random spot, a phone buzz,
 * or someone walking past who covers part of the table for a moment.
 */
@Composable
internal fun DistractionLayer(vm: PlayViewModel, width: Dp, height: Dp) {
    val a = vm.aside
    if (a != null) {
        val bubbleW = minOf(240.dp, width * 0.62f)
        Box(
            Modifier.offset(x = ((width - bubbleW) * a.x), y = height * a.y).width(bubbleW)
                .semantics { contentDescription = "Table talk: ${a.text}" },
        ) {
            val buzz = a.kind == com.hilotrainer.engine.Distractions.Kind.BUZZ
            Column(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (buzz) Color(0xFF263238) else Cream)
                    .border(1.dp, if (buzz) Color(0xFF90CAF9) else Color(0xFF8D6E63), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                if (a.who.isNotEmpty()) Text(a.who, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (buzz) Color(0xFF90CAF9) else Color(0xFF5D4037))
                Text(a.text, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (buzz) FontWeight.Black else FontWeight.SemiBold, color = if (buzz) Color(0xFF90CAF9) else Ink)
            }
        }
    }
    val w = vm.walkBy ?: return
    val t = remember(w) { Animatable(0f) }
    LaunchedEffect(w) { t.animateTo(1f, tween(if (vm.instant) 1 else w.durationMs.toInt(), easing = LinearEasing)) }
    val bodyW = width * 0.5f
    val bodyH = height * 0.36f
    val p = if (vm.instant) 0.5f else t.value
    val x = if (w.fromLeft) (-bodyW + (width + bodyW) * p) else (width - (width + bodyW) * p)
    Box(
        Modifier.offset(x = x, y = height * w.y - bodyH / 2).size(bodyW, bodyH)
            .semantics { contentDescription = "${w.who} walks past the table" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cw = size.width; val ch = size.height
            // A blurred-looking back and shoulders passing close to the camera.
            drawOval(Color(0xF0101418), Offset(cw * 0.05f, ch * 0.28f), Size(cw * 0.9f, ch * 0.95f))
            drawCircle(Color(0xF0101418), ch * 0.24f, Offset(cw * 0.5f, ch * 0.24f))
            drawOval(Color(0x40FFFFFF), Offset(cw * 0.2f, ch * 0.4f), Size(cw * 0.25f, ch * 0.12f))
        }
        Text(w.who, fontSize = 11.sp, color = Cream.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
    }
}
