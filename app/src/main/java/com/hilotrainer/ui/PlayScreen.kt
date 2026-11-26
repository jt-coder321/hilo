package com.hilotrainer.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Action
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.Decision
import com.hilotrainer.engine.DecisionKind
import com.hilotrainer.engine.Hand
import com.hilotrainer.engine.HandResult
import com.hilotrainer.engine.Outcome
import com.hilotrainer.engine.Phase
import com.hilotrainer.engine.handTotal
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(vm: PlayViewModel, onBack: () -> Unit) {
    var showLog by remember { mutableStateOf(false) }
    var showAudit by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false; vm.stopWatching() }
    }
    val openStrip = { if (vm.banner?.action == BannerAction.AUDIT) showAudit = true else showLog = true }
    val fold = LocalFold.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var bodyTopPx by remember { mutableStateOf(0f) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        TopRail(vm, onBack, onLog = { showLog = true }, onAudit = { showAudit = true }, onReport = { showReport = true })
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f)
                .onGloballyPositioned { bodyTopPx = it.positionInWindow().y },
        ) {
            val wide = maxWidth >= 600.dp
            val sideW = minOf(320.dp, maxWidth * 0.36f)
            // Flex mode: Fold half open like a laptop. Table above the hinge, controls below it.
            val tableAboveHinge = with(density) { (fold.hingeTopPx - bodyTopPx).toDp() }
            if (fold.tabletop && vm.settings.flexMode && tableAboveHinge in 160.dp..(maxHeight - 160.dp)) {
                val hinge = with(density) { fold.hingeHeightPx.toDp() }
                Column(Modifier.fillMaxSize()) {
                    Table(vm, Modifier.height(tableAboveHinge))
                    Spacer(Modifier.height(hinge).fillMaxWidth().background(Ink))
                    Row(Modifier.fillMaxWidth().weight(1f)) {
                        Dock(vm, openStrip, Modifier.weight(1f).fillMaxHeight(), flex = true)
                        if (wide) Box(Modifier.width(sideW).fillMaxHeight().background(Ink)) {
                            CoachColumn(vm, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp))
                        }
                    }
                }
            } else if (wide && maxWidth > maxHeight * 1.25f) {
                // Landscape on the inner screen: the table takes the full height and the
                // controls sit beside it, on the left for left-handed players.
                val panelW = minOf(380.dp, maxWidth * 0.4f)
                val controls = @Composable {
                    Column(Modifier.width(panelW).fillMaxHeight().background(Ink)) {
                        Dock(vm, openStrip, Modifier.fillMaxWidth().height(232.dp))
                        CoachColumn(vm, Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    if (vm.settings.leftHanded) controls()
                    Table(vm, Modifier.weight(1f).fillMaxHeight())
                    if (!vm.settings.leftHanded) controls()
                }
            } else if (wide) {
                val side = @Composable {
                    Box(Modifier.width(sideW).fillMaxHeight().background(Ink)) {
                        CoachColumn(vm, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp))
                    }
                }
                Row(Modifier.fillMaxSize()) {
                    if (vm.settings.leftHanded) side()
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Table(vm, Modifier.weight(1f))
                        Dock(vm, openStrip)
                    }
                    if (!vm.settings.leftHanded) side()
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Table(vm, Modifier.weight(1f))
                    Dock(vm, openStrip)
                }
            }
        }
    }

    if (showLog) {
        ModalBottomSheet(onDismissRequest = { showLog = false }, containerColor = FeltDark) {
            CoachColumn(vm, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }

    if (showAudit) {
        ModalBottomSheet(onDismissRequest = { showAudit = false }, containerColor = FeltDark) {
            CountAudit(vm, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
    if (showReport) {
        ModalBottomSheet(onDismissRequest = { showReport = false }, containerColor = FeltDark) {
            SessionReport(vm, Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }

    vm.colorUpText?.let { t ->
        AlertDialog(
            onDismissRequest = { vm.dismissColorUp() },
            containerColor = FeltDark,
            title = { Text("Colored up", fontFamily = TitleFont, color = Gold) },
            text = { Text(t, color = Cream) },
            confirmButton = { GoldButton("Leave the table", Modifier.widthIn(min = 140.dp)) { vm.dismissColorUp(); onBack() } },
            dismissButton = { TextButton(onClick = { vm.dismissColorUp() }) { Text("Buy in again", color = Cream) } },
        )
    }

    vm.pendingMistake?.let { (chosen, advice) ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = FeltDark,
            title = { Text("Basic strategy says ${advice.action.label}", fontFamily = TitleFont, color = Gold) },
            text = { Text("${advice.reason}. You picked ${chosen.label}.", color = Cream) },
            confirmButton = { GoldButton(advice.action.label, Modifier.widthIn(min = 120.dp)) { vm.takeAdvice() } },
            dismissButton = { TextButton(onClick = { vm.confirmMistake() }) { Text("${chosen.label} anyway", color = Cream) } },
        )
    }
}

@Composable
private fun TopRail(vm: PlayViewModel, onBack: () -> Unit, onLog: () -> Unit, onAudit: () -> Unit, onReport: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val s = vm.state
    val shownBankroll by animateIntAsState(if (vm.animating) s.bankroll - pendingNet(vm) else s.bankroll, tween(600), label = "bank")
    Row(
        Modifier.fillMaxWidth().height(52.dp).woodRail().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Gold) }
        Column(Modifier.weight(1f)) {
            Text("BANKROLL", fontSize = 9.sp, letterSpacing = 1.5.sp, color = Gold.copy(alpha = 0.8f))
            Text(money(shownBankroll), fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Cream, maxLines = 1)
        }
        if (vm.sessionActive) SessionClock(vm)
        if (vm.settings.coverPlay) HeatGauge(vm.heat)
        CountPill(vm)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Gold) }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = FeltDark) {
                MenuHeading("MODES")
                MenuItem(if (vm.watching) "Stop watch mode" else "Watch mode (count only)", vm.watching || vm.bettingOpen) {
                    menu = false
                    if (vm.watching) vm.stopWatching() else vm.startWatching()
                }
                if (vm.mode == TableMode.NORMAL) {
                    MenuItem("Back-counting practice", vm.bettingOpen && !vm.watching) { menu = false; vm.startBackCount() }
                    MenuItem("Team play practice", vm.bettingOpen && !vm.watching) { menu = false; vm.startTeam() }
                } else {
                    MenuItem("Back to normal play", true) { menu = false; vm.stopMode() }
                }
                if (!vm.sessionActive) MenuItem("Start a one-hour session", vm.bettingOpen && !vm.watching) { menu = false; vm.startSession() }
                else {
                    MenuItem("Take a 5-minute break", vm.bettingOpen && !vm.onBreak) { menu = false; vm.takeBreak() }
                    MenuItem("End the session early", true) { menu = false; vm.stopSession(); onReport() }
                }
                MenuHeading("TABLE")
                MenuItem("Tip the dealer \$5", vm.bettingOpen && !vm.watching && !vm.needBuyIn && vm.state.bankroll >= 15) { menu = false; vm.tipDealer(5) }
                MenuItem("Join a table mid-shoe", vm.bettingOpen && !vm.watching && vm.system.balanced) { menu = false; vm.joinMidShoe() }
                MenuItem("Leave table (new shoe)", vm.bettingOpen && !vm.watching) { menu = false; vm.leaveTable() }
                MenuItem("Color up and leave", vm.bettingOpen && !vm.watching && !vm.needBuyIn) { menu = false; vm.colorUp() }
                MenuHeading("REVIEW")
                MenuItem("Count audit", true) { menu = false; onAudit() }
                MenuItem("Coach log", true) { menu = false; onLog() }
                MenuItem("Session report", true) { menu = false; onReport() }
            }
        }
    }
}

/** Minutes into the one-hour session. */
@Composable
private fun SessionClock(vm: PlayViewModel) {
    val m = vm.sessionElapsedMs / 60_000L
    val sec = vm.sessionElapsedMs / 1000L % 60
    Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SESSION", fontSize = 9.sp, letterSpacing = 1.5.sp, color = Gold.copy(alpha = 0.8f))
        Text("%d:%02d".format(m, sec), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Cream)
    }
}

/** How much attention your betting is drawing, shown only with cover play on. */
@Composable
private fun HeatGauge(heat: Double) {
    val frac = (heat / com.hilotrainer.engine.CoverPlay.LEAVE_AT).coerceIn(0.0, 1.0).toFloat()
    val col = when {
        heat >= com.hilotrainer.engine.CoverPlay.LEAVE_AT -> BadRed
        heat >= com.hilotrainer.engine.CoverPlay.WARN_AT -> Color(0xFFFFB74D)
        else -> GoodGreen
    }
    Column(
        Modifier.padding(horizontal = 4.dp).width(46.dp).semantics { contentDescription = "Heat ${(frac * 100).toInt()} percent" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("HEAT", fontSize = 9.sp, letterSpacing = 1.5.sp, color = Gold.copy(alpha = 0.8f))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.7f))) {
            Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.04f)).fillMaxHeight().background(col))
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(text, fontSize = 11.sp, letterSpacing = 2.sp, color = Gold, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
}

@Composable
private fun MenuItem(text: String, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(text, color = if (enabled) Cream else Cream.copy(alpha = 0.4f)) },
        onClick = onClick, enabled = enabled,
    )
}

/** While a round is still being animated, hold back winnings so the bankroll ticks up at the end. */
private fun pendingNet(vm: PlayViewModel): Int {
    val s = vm.state
    if (s.phase != Phase.ROUND_OVER) return 0
    return s.results.zip(s.hands).sumOf { (r, h) -> r.net + h.bet } + maxOf(0, s.insuranceNet + s.insuranceBet)
}

@Composable
private fun CountPill(vm: PlayViewModel) {
    val show = vm.settings.showRunningCount || vm.peeking
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.padding(horizontal = 4.dp).height(38.dp).clip(shape)
            .background(if (vm.peeking) Gold.copy(alpha = 0.25f) else Ink.copy(alpha = 0.6f))
            .border(1.dp, Gold.copy(alpha = 0.6f), shape)
            .clickable(enabled = !vm.settings.showRunningCount) { vm.togglePeek() }
            .semantics { contentDescription = if (vm.peeking) "Hide count" else "Peek at the count" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.settings.showRunningCount) {
            Text(if (vm.system == com.hilotrainer.engine.CountSystem.HI_LO) "RC " else "${vm.system.label} ", fontSize = 11.sp, color = Gold)
            Text(signed(vm.visibleRunningCount), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Cream)
            if (vm.system.balanced) {
                Text("  TC ", fontSize = 11.sp, color = Gold)
                Text(signed1(vm.visibleTrueCount), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Cream)
            }
        } else {
            Text(if (vm.peeking) "Hide count" else "Peek count", fontSize = 13.sp, color = Gold, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Table ----------

private class Layout(
    val w: Dp, val dealerH: Dp, val arcH: Dp, val botsH: Dp, val playerH: Dp,
)

@Composable
private fun Table(vm: PlayViewModel, modifier: Modifier) {
    val style = CardStyle(back = vm.settings.cardBack, wear = (vm.shoesAtTable * 0.15f).coerceAtMost(1f))
    androidx.compose.runtime.CompositionLocalProvider(LocalCardStyle provides style) {
        Box(modifier.fillMaxWidth().background(Ink)) {
            TableInner(vm, Modifier.fillMaxSize().seatedView(vm.settings.seatedView))
        }
    }
}

@Composable
private fun TableInner(vm: PlayViewModel, modifier: Modifier) {
    val s = vm.state
    BoxWithConstraints(
        modifier.fillMaxWidth().feltBackground()
            .pointerInput(vm.animating) { if (vm.animating) detectTapGestures { vm.skipAnimation() } },
    ) {
        val h = maxHeight - 10.dp
        val hasBots = s.bots.isNotEmpty()
        // A short table (the top half in flex mode) gives the player's hand a bigger share.
        val lay = if (hasBots && h < 340.dp) Layout(maxWidth, h * 0.27f, h * 0.08f, h * 0.17f, h * 0.48f)
        else if (hasBots) Layout(maxWidth, h * 0.28f, h * 0.10f, h * 0.20f, h * 0.42f)
        else Layout(maxWidth, h * 0.33f, h * 0.12f, 0.dp, h * 0.55f)

        TableFelt(vm, lay)
        val roomy = lay.dealerH >= 120.dp
        if (roomy) PitBoss(vm, lay.w, Modifier.align(Alignment.TopStart).padding(top = 4.dp))
        Shoe(vm, Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp))
        Column(Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DiscardTray(vm, Modifier)
            if (roomy) { Spacer(Modifier.height(4.dp)); TablePlacard(vm) }
        }
        if (roomy) {
            DealerRack(vm, Modifier.align(Alignment.TopCenter).padding(top = 3.dp))
            CameraDome(Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 92.dp))
        }

        Column(Modifier.fillMaxSize()) {
            DealerArea(vm, lay, roomy)
            Spacer(Modifier.height(lay.arcH))
            if (hasBots) BotsArea(vm, lay)
            PlayerArea(vm, lay)
            PaddedRail()
        }
        DealerHandOverlay(vm, lay.w, h)

        DistractionLayer(vm, lay.w, h)
        if (vm.shuffling) ShuffleStages(vm, Modifier.align(Alignment.Center))
        // Round result sits on the table printing so it never hides cards.
        Box(Modifier.fillMaxWidth().offset(y = lay.dealerH).height(lay.arcH), contentAlignment = Alignment.Center) {
            RoundResult(vm, Modifier)
        }
        if (vm.watching) {
            Text(
                "WATCH MODE", fontSize = 11.sp, letterSpacing = 2.sp, color = Gold,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp).clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        AnimatedVisibility(vm.peeking && !vm.settings.showRunningCount, Modifier.align(Alignment.Center), enter = scaleIn(initialScale = 0.7f) + fadeIn(), exit = fadeOut()) {
            PeekCard(vm)
        }
    }
}

/** Table printing: arc text and the betting line. */
@Composable
private fun TableFelt(vm: PlayViewModel, lay: Layout) {
    val r = vm.settings
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val arcTop = lay.dealerH.toPx()
        val arcH = lay.arcH.toPx()
        val radius = w * 1.1f
        val cx = w / 2
        val cy = arcTop + arcH * 0.55f - radius
        val oval = android.graphics.RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val path = android.graphics.Path().apply { addArc(oval, 128f, -76f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Gold.copy(alpha = 0.85f).toArgb()
            textSize = (arcH * 0.38f).coerceIn(10.dp.toPx(), 20.dp.toPx())
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        drawContext.canvas.nativeCanvas.drawTextOnPath(if (r.sixToFive) "BLACKJACK PAYS 6 TO 5" else "BLACKJACK PAYS 3 TO 2", path, 0f, 0f, paint)
        val small = Paint(paint).apply {
            textSize = paint.textSize * 0.55f
            color = Cream.copy(alpha = 0.6f).toArgb()
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            letterSpacing = 0.08f
        }
        val path2 = android.graphics.Path().apply {
            val r2 = radius + paint.textSize * 1.05f
            addArc(android.graphics.RectF(cx - r2, cy - r2, cx + r2, cy + r2), 125f, -70f)
        }
        val rule = "DEALER ${if (r.dealerHitsSoft17) "HITS" else "STANDS ON"} SOFT 17  ·  INSURANCE PAYS 2 TO 1"
        drawContext.canvas.nativeCanvas.drawTextOnPath(rule, path2, 0f, 0f, small)
        // A thin gold outline arc for the insurance line
        drawArc(
            Gold.copy(alpha = 0.35f), startAngle = 128f, sweepAngle = -76f, useCenter = false,
            topLeft = Offset(cx - radius - 6, cy - radius - 6), size = Size((radius + 6) * 2, (radius + 6) * 2),
            style = Stroke(1.2f),
        )
    }
}

/**
 * The dealing shoe seen from the side: one band per deck, with the yellow cut card
 * sitting in the stack. As cards come out the front, the cut card moves closer.
 */
@Composable
private fun Shoe(vm: PlayViewModel, modifier: Modifier) {
    val s = vm.state
    val total = s.totalCards
    val dealt = vm.visibleCardsDealt.coerceIn(0, total)
    val cutAt = (total * vm.settings.penetrationPct / 100).coerceAtMost(total)
    val untilCut = cutAt - dealt
    val decks = total / 52
    Column(modifier.semantics { contentDescription = "Shoe: %.1f decks left, %s".format((total - dealt) / 52.0, if (untilCut > 0) "$untilCut cards to the cut card" else "cut card is out") }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(74.dp, 40.dp)) {
            val r = 6.dp.toPx()
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2B2B2B), Color(0xFF0E0E0E))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
            drawRoundRect(Gold.copy(alpha = 0.5f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r), style = Stroke(1.dp.toPx()))
            val pad = 5.dp.toPx()
            val w = size.width - pad * 2
            val top = 8.dp.toPx(); val h = size.height - top - pad
            // The stack: cards left in the shoe, from the front (left) to the back.
            val leftFrac = (total - dealt) / total.toFloat()
            val stackW = w * leftFrac
            val x0 = pad + (w - stackW)
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF9E1B2C), Color(0xFF5C0C18))), Offset(x0, top), Size(stackW, h))
            // Deck bands so you can read how many decks are left.
            for (d in 1 until decks) {
                val x = pad + w - w * d / decks
                if (x > x0) drawLine(Color(0x66000000), Offset(x, top), Offset(x, top + h), 1f)
            }
            if (untilCut > 0) {
                val cx = pad + w - w * (total - cutAt) / total.toFloat()
                drawRect(Color(0xFFFFD54F), Offset(cx - 1.5.dp.toPx(), top - 4.dp.toPx()), Size(3.dp.toPx(), h + 4.dp.toPx()))
            }
        }
        Text(
            if (untilCut > 0) "%.1f decks".format((total - dealt) / 52.0) else "Cut card out",
            fontSize = 10.sp, color = if (untilCut > 0) Cream.copy(alpha = 0.7f) else Color(0xFFFFD54F),
        )
    }
}

@Composable
private fun DiscardTray(vm: PlayViewModel, modifier: Modifier) {
    val s = vm.state
    val decks = vm.visibleCardsDealt / 52.0
    val maxDecks = s.totalCards / 52.0
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(30.dp, 44.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x44000000))
                .border(1.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Cards in the tray seen edge-on: thin lines, one every few cards.
            Canvas(Modifier.fillMaxWidth().fillMaxHeight((decks / maxDecks).toFloat().coerceIn(0f, 1f))) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFFEDE6D6), Color(0xFFBDB5A3))))
                var y = size.height - 1.5f
                var i = 0
                while (y > 0f) {
                    drawLine(Color(0xFF6D6250).copy(alpha = if (i % 4 == 0) 0.55f else 0.22f), Offset(1f, y), Offset(size.width - 1f, y + (i % 3 - 1) * 0.6f), 0.8f)
                    y -= 1.6.dp.toPx(); i++
                }
            }
        }
        Text("Tray %.1f".format(decks), fontSize = 10.sp, color = Cream.copy(alpha = 0.7f))
    }
}

private fun visibleOf(cards: List<Card>, seqs: List<Int>, visibleSeq: Int): List<Pair<Card, Int>> =
    cards.zip(seqs).filter { it.second < visibleSeq }

@Composable
private fun TotalBadge(text: String, highlight: Boolean = false, color: Color = Ink) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (highlight) Gold else color.copy(alpha = 0.8f))
            .border(1.dp, Gold.copy(alpha = 0.7f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (highlight) Ink else Cream) }
}

@Composable
private fun HandCards(
    vm: PlayViewModel, cards: List<Pair<Card, Int>>, w: Dp, from: DpOffset, maxW: Dp,
    hiddenSeq: Int = -1, faceUpHidden: Boolean = true, liftId: Long = 0L,
) {
    val n = cards.size
    val lift = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(liftId) {
        if (liftId != 0L && !vm.instant) {
            delay((vm.settings.dealDelayMs(vm.ladderRung) * 2).toLong())
            lift.animateTo(1f, tween(220)); delay(260); lift.animateTo(0f, tween(220))
        }
    }
    var step = w * 0.36f
    if (n > 1) step = minOf(step, (maxW - w) / (n - 1)).coerceAtLeast(w * 0.16f)
    Box(Modifier.size(w + step * (n - 1).coerceAtLeast(0), w * 1.4f)) {
        cards.forEachIndexed { i, (c, seq) ->
            key(seq) {
                TableCard(
                    card = c, width = w,
                    faceUp = if (seq == hiddenSeq) faceUpHidden else true,
                    from = DpOffset(from.x - step * i, from.y),
                    animate = seq >= vm.animateFromSeq && !vm.instant,
                    durationMs = (vm.settings.dealDelayMs(vm.ladderRung) * 0.9).toInt().coerceAtLeast(160),
                    // Real cards never land perfectly square: a small, steady angle and offset per card.
                    modifier = Modifier.offset(x = step * i, y = ((((seq * 13) % 5) - 2) * 0.7f).dp)
                        .rotate((((seq * 37) % 7) - 3) * 0.9f)
                        .then(if (seq == hiddenSeq) Modifier.androidxLift(lift.value) else Modifier),
                )
            }
        }
    }
}

@Composable
private fun DealerArea(vm: PlayViewModel, lay: Layout, roomy: Boolean) {
    val s = vm.state
    val rackH = if (roomy) 30.dp else 0.dp
    Column(
        Modifier.fillMaxWidth().height(lay.dealerH).padding(top = 6.dp + rackH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val cardW = ((lay.dealerH - 34.dp - rackH) / 1.4f).coerceIn(30.dp, 96.dp)
        val cards = visibleOf(s.dealerCards, s.dealerSeqs, vm.visibleSeq)
        val holeShown = s.holeRevealed && vm.visibleSeq > s.holeRevealSeq
        Box(Modifier.height(cardW * 1.4f), contentAlignment = Alignment.Center) {
            if (cards.isNotEmpty()) {
                HandCards(
                    vm, cards, cardW, DpOffset(lay.w * 0.38f, -cardW * 0.6f), lay.w * 0.6f,
                    hiddenSeq = s.dealerSeqs.getOrElse(1) { -1 }, faceUpHidden = holeShown || vm.holeFlashShowing,
                    liftId = vm.dealerPeekId,
                )
            } else {
                Text("DEALER", fontFamily = TitleFont, fontSize = 18.sp, letterSpacing = 3.sp, color = Cream.copy(alpha = 0.35f))
            }
        }
        Spacer(Modifier.height(4.dp))
        if (cards.isNotEmpty()) {
            val shownCards = cards.filterIndexed { i, _ -> i != 1 || holeShown }.map { it.first }
            val (t, soft) = handTotal(shownCards)
            val label = when {
                holeShown && t > 21 -> "Bust $t"
                holeShown && cards.size == 2 && t == 21 -> "Blackjack"
                soft && t < 21 && holeShown -> "Soft $t"
                else -> "$t"
            }
            TotalBadge(label)
        }
    }
}

@Composable
private fun BotsArea(vm: PlayViewModel, lay: Layout) {
    val s = vm.state
    val n = s.bots.size
    Row(
        Modifier.fillMaxWidth().height(lay.botsH).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val seatW = (lay.w - 16.dp) / n
        val named = lay.botsH >= 90.dp
        val cardW = minOf((lay.botsH - if (named) 40.dp else 26.dp) / 1.4f, seatW / 2.2f, 64.dp).coerceAtLeast(22.dp)
        s.bots.forEachIndexed { i, b ->
            val cards = visibleOf(b.cards, b.seqs, vm.visibleSeq)
            val result = if (!vm.animating) s.botResults.getOrNull(i) else null
            Column(Modifier.width(seatW), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.height(cardW * 1.4f), contentAlignment = Alignment.Center) {
                    if (cards.isEmpty()) {
                        Box(Modifier.size(cardW * 0.9f).clip(CircleShape).border(1.dp, Cream.copy(alpha = 0.2f), CircleShape))
                    } else {
                        HandCards(vm, cards, cardW, DpOffset(lay.w * 0.3f - seatW * i, -(lay.dealerH + lay.arcH)), seatW - 8.dp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                val total = handTotal(cards.map { it.first }).first
                val label = when {
                    result != null -> "${result.outcome.label}"
                    cards.isEmpty() -> if (named) (vm.seatNames.getOrNull(i) ?: "Player ${i + 1}") else "Player ${i + 1}"
                    total > 21 -> "Bust"
                    cards.size == 2 && total == 21 -> "Blackjack"
                    else -> "$total"
                }
                Text(label, fontSize = 12.sp, lineHeight = 14.sp, color = resultColor(result) ?: Cream.copy(alpha = 0.85f), maxLines = 1)
                if (named) {
                    val name = vm.seatNames.getOrNull(i) ?: "Player ${i + 1}"
                    val stack = vm.seatStacks.getOrNull(i) ?: 0
                    // Reactions go on the name line, never over the cards.
                    val react = if (result != null) vm.seatReactions.getOrNull(i) else null
                    Text(if (react != null) "$name: $react" else if (cards.isEmpty()) money(stack) else "$name ${money(stack)}", fontSize = 10.sp, lineHeight = 12.sp, color = Gold.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun resultColor(r: HandResult?): Color? = when (r?.outcome) {
    null -> null
    Outcome.WIN, Outcome.BLACKJACK -> GoodGreen
    Outcome.PUSH -> Cream
    else -> BadRed
}

@Composable
private fun PlayerArea(vm: PlayViewModel, lay: Layout) {
    val s = vm.state
    val betRowH = 58.dp
    val signalsOn = vm.settings.signals > 0 && s.phase == Phase.PLAYER && !vm.animating
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sig = if (!signalsOn) Modifier else Modifier
        .pointerInput("tap", signalsOn) { detectTapGestures { vm.signal(true) } }
        .pointerInput("wave", signalsOn) {
            var dx = 0f
            detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragEnd = { if (kotlin.math.abs(dx) > with(density) { 48.dp.toPx() }) vm.signal(false) },
            ) { change, amount -> change.consume(); dx += amount }
        }
    Column(Modifier.fillMaxWidth().height(lay.playerH).then(sig), horizontalAlignment = Alignment.CenterHorizontally) {
        val handsH = lay.playerH - betRowH
        val hands = s.hands
        val n = hands.size.coerceAtLeast(1)
        val perHand = (lay.w - 16.dp - 10.dp * (n - 1)) / n
        val maxCards = hands.maxOfOrNull { it.cards.size }?.coerceAtLeast(2) ?: 2
        // Leave room under the cards for the total badge, even on the short flex mode table.
        val byHeight = (handsH - 40.dp) / 1.4f
        val byWidth = perHand / (1f + 0.36f * (maxCards - 1))
        val cardW = minOf(byHeight, byWidth, 100.dp).coerceAtLeast(26.dp)
        Row(
            Modifier.fillMaxWidth().height(handsH).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hands.isEmpty()) {
                Text("Tap chips to bet, then Deal", fontFamily = TitleFont, fontSize = 16.sp, color = Cream.copy(alpha = 0.45f))
            }
            hands.forEachIndexed { i, h ->
                val active = s.phase == Phase.PLAYER && i == s.activeHand && !vm.animating
                HandView(vm, h, i, cardW, perHand, active, lay)
            }
        }
        BetSpot(vm, betRowH)
    }
}

@Composable
private fun HandView(vm: PlayViewModel, h: Hand, index: Int, cardW: Dp, maxW: Dp, active: Boolean, lay: Layout) {
    val s = vm.state
    val cards = visibleOf(h.cards, h.seqs, vm.visibleSeq)
    val result = if (!vm.animating && s.phase == Phase.ROUND_OVER) s.results.getOrNull(index) else null
    val glow = if (active) Modifier.shadow(14.dp, RoundedCornerShape(12.dp), ambientColor = Gold, spotColor = Gold) else Modifier
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.then(glow).clip(RoundedCornerShape(12.dp))
                .border(BorderStroke(2.dp, if (active) Gold else Color.Transparent), RoundedCornerShape(12.dp))
                .padding(4.dp),
        ) {
            HandCards(vm, cards, cardW, DpOffset(lay.w * 0.36f, -(lay.dealerH + lay.arcH + lay.botsH + cardW)), maxW - 8.dp)
        }
        Spacer(Modifier.height(3.dp))
        val (t, soft) = handTotal(cards.map { it.first })
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = when {
                result != null -> "${result.outcome.label} " + when {
                    result.net > 0 -> "+${money(result.net)}"
                    result.net < 0 -> "-${money(-result.net)}"
                    else -> ""
                }
                cards.size == 2 && t == 21 && !h.isSplitHand -> "Blackjack"
                t > 21 -> "Bust"
                soft && t < 21 -> "Soft $t"
                else -> "$t"
            }
            val color = when (result?.outcome) {
                Outcome.WIN, Outcome.BLACKJACK -> Color(0xFF1E7B43)
                Outcome.LOSE, Outcome.BUST, Outcome.SURRENDER -> Color(0xFF8E2A2A)
                else -> Ink
            }
            TotalBadge(label.trim(), highlight = active, color = color)
            if (s.hands.size > 1 || h.doubled) {
                Spacer(Modifier.width(4.dp))
                Text(money(h.bet), fontSize = 11.sp, color = Gold)
            }
        }
    }
}

@Composable
private fun BetSpot(vm: PlayViewModel, height: Dp) {
    val s = vm.state
    val inRound = s.phase == Phase.PLAYER || s.phase == Phase.INSURANCE
    val amount = if (inRound) s.hands.sumOf { it.bet } else vm.bet
    Row(Modifier.height(height), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(height - 6.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape).border(2.dp, Gold.copy(alpha = 0.7f), CircleShape)
                    .background(Color(0x22000000)),
                contentAlignment = Alignment.Center,
            ) {
                if (amount > 0) BetChips(vm, amount, height * 0.56f) else Text("BET", fontSize = 11.sp, color = Gold.copy(alpha = 0.6f))
            }
            RoundChips(vm, height * 0.56f)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(if (inRound) "IN PLAY" else "YOUR BET", fontSize = 9.sp, letterSpacing = 1.5.sp, color = Gold.copy(alpha = 0.8f))
            Text(money(amount), fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (s.insuranceBet > 0) Text("Insurance ${money(s.insuranceBet)}", fontSize = 10.sp, color = Cream.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun PeekCard(vm: PlayViewModel) {
    Column(
        Modifier.shadow(16.dp, RoundedCornerShape(18.dp)).clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF0152A20), Color(0xF00A1610))))
            .border(1.5.dp, Gold, RoundedCornerShape(18.dp))
            .clickable { vm.togglePeek() }.padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${vm.system.label.uppercase()} RUNNING COUNT", fontSize = 11.sp, letterSpacing = 2.sp, color = Gold)
        Text(signed(vm.visibleRunningCount), fontFamily = TitleFont, fontWeight = FontWeight.Black, fontSize = 46.sp, color = Cream)
        if (vm.system.balanced) Text("True count ${signed1(vm.visibleTrueCount)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Cream)
        else Text("No true count needed with KO", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Cream)
        Text("%.1f decks left".format((vm.state.totalCards - vm.visibleCardsDealt) / 52.0), fontSize = 12.sp, color = Cream.copy(alpha = 0.7f))
        Text("Hides in a few seconds. Peeks are tracked.", fontSize = 11.sp, color = Cream.copy(alpha = 0.55f))
    }
}

/** One fixed line above the buttons for coaching, so messages never cover the cards. */
@Composable
private fun CoachStrip(vm: PlayViewModel, onOpen: () -> Unit) {
    val b = vm.banner
    LaunchedEffect(b?.id) {
        if (b != null) {
            delay(if (b.tone == BannerTone.BAD || b.tone == BannerTone.COUNT) 9000 else 5000)
            if (vm.banner?.id == b.id) vm.dismissBanner()
        }
    }
    val (bg, edge) = when (b?.tone) {
        BannerTone.GOOD -> Color(0xFF173F2A) to GoodGreen
        BannerTone.BAD -> Color(0xFF431A1D) to BadRed
        BannerTone.COUNT -> Color(0xFF3A3010) to Gold
        BannerTone.INFO -> Color(0xFF14261D) to Cream
        null -> Color(0xFF0F1D16) to Cream.copy(alpha = 0.55f)
    }
    val hintEnabled = !vm.animating && !vm.watching && vm.state.phase != Phase.ROUND_OVER || vm.bettingOpen && !vm.watching
    vm.distraction?.let { d ->
        Row(
            Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2140))
                .border(1.dp, Color(0xFFB39DDB).copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                d.text, fontSize = 13.sp, lineHeight = 16.sp, color = Cream, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 4.dp).semantics { contentDescription = "Table noise: ${d.text}" },
            )
            if (d.needsAnswer && d.options.isEmpty()) GoldButton("No thanks", Modifier.padding(end = 4.dp).width(100.dp).height(40.dp), primary = false) { vm.answerDistraction() }
        }
        return
    }
    val hintButton = @Composable {
        GoldButton("Hint", Modifier.padding(horizontal = 4.dp).width(72.dp).height(40.dp), hintEnabled, primary = false) { vm.hint() }
    }
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .border(1.dp, edge.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (vm.settings.leftHanded) hintButton()
        Column(Modifier.weight(1f).clickable(onClick = onOpen).padding(horizontal = 10.dp, vertical = 4.dp)) {
            if (b == null) {
                Text(defaultStripText(vm), fontSize = 13.sp, color = edge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text(b.title, fontWeight = FontWeight.Bold, color = edge, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (b.detail.isNotBlank()) Text(b.detail, fontSize = 12.sp, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (vm.clockLeftMs > 0 && vm.state.phase == Phase.PLAYER) {
            val total = vm.settings.shotClock * 1000f
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Cream.copy(alpha = 0.15f), 0f, 360f, false, style = Stroke(3.dp.toPx()))
                    drawArc(if (vm.clockLeftMs < 2000) BadRed else Gold, -90f, 360f * vm.clockLeftMs / total, false, style = Stroke(3.dp.toPx()))
                }
                Text("${(vm.clockLeftMs + 999) / 1000}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (!vm.settings.leftHanded) hintButton()
    }
}

private fun defaultStripText(vm: PlayViewModel): String = when {
    vm.watching -> "Watch mode: just keep the count. A count check comes every few rounds."
    vm.animating -> "Dealing. Tap the table to skip ahead."
    vm.state.phase == Phase.PLAYER && vm.settings.signals > 0 -> "Your move. Tap the felt to hit, wave sideways to stand."
    vm.state.phase == Phase.PLAYER -> "Your move. Tap Hint if you're unsure."
    vm.state.phase == Phase.INSURANCE -> "Insurance decision."
    else -> "Tap chips to bet. Tap here for the coach log."
}

@Composable
private fun ShuffleOverlay(modifier: Modifier) {
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, androidx.compose.animation.core.infiniteRepeatable(tween(500), androidx.compose.animation.core.RepeatMode.Reverse))
    }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Ink.copy(alpha = 0.75f)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(120.dp, 76.dp), contentAlignment = Alignment.Center) {
            for (i in 0 until 5) {
                val spread = (i - 2) * 14f * t.value
                CardBack(44.dp, Modifier.offset(x = spread.dp, y = (kotlin.math.abs(i - 2) * 2).dp).rotate((i - 2) * 9f * t.value))
            }
        }
        Text("Shuffling", fontFamily = TitleFont, fontSize = 18.sp, color = Gold)
    }
}


@Composable
private fun RoundResult(vm: PlayViewModel, modifier: Modifier) {
    val net = vm.roundNet
    val show = net != null && !vm.animating && vm.state.phase == Phase.ROUND_OVER
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(show, net, vm.state.roundStartSeq) {
        visible = show
        if (show) { delay(1600); visible = false }
    }
    AnimatedVisibility(visible, modifier, enter = scaleIn(initialScale = 0.4f) + fadeIn(), exit = fadeOut()) {
        val bj = vm.state.results.any { it.outcome == Outcome.BLACKJACK }
        val n = net ?: 0
        val text = when {
            bj -> "BLACKJACK!"
            n > 0 -> "+${money(n)}"
            n < 0 -> "-${money(-n)}"
            else -> "PUSH"
        }
        Text(
            text, fontFamily = TitleFont, fontWeight = FontWeight.Black, fontSize = 42.sp,
            color = when { n > 0 || bj -> Gold; n < 0 -> BadRed; else -> Cream },
            modifier = Modifier.shadow(0.dp).clip(RoundedCornerShape(12.dp)).background(Ink.copy(alpha = 0.55f)).padding(horizontal = 18.dp, vertical = 6.dp),
        )
    }
}

// ---------- Dock ----------

@Composable
private fun Dock(vm: PlayViewModel, onOpenStrip: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth().height(214.dp), flex: Boolean = false) {
    val s = vm.state
    Column(
        modifier.background(Brush.verticalGradient(listOf(Color(0xFF10261B), Ink)))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (flex) 12.dp else 8.dp, Alignment.CenterVertically),
    ) {
        CoachStrip(vm, onOpenStrip)
        val q = vm.quiz
        val dc = vm.dealerCheck
        when {
            vm.onBreak -> BreakDock(vm)
            vm.distraction?.let { it.blocking && it.options.isNotEmpty() } == true -> QuestionDock(vm, vm.distraction!!)
            vm.needBuyIn && vm.bettingOpen && !vm.watching -> BuyInDock(vm)
            dc != null && !vm.animating -> DealerCheckDock(vm, dc)
            q != null && q.stage != QuizStage.DONE && !vm.animating -> QuizDock(vm, q)
            vm.watching -> WatchDock(vm)
            vm.mode == TableMode.TEAM && vm.teamRole == TeamRole.SIGNAL -> SignalDock(vm)
            vm.mode != TableMode.NORMAL && !vm.seated && !vm.animating -> ModeDock(vm)
            s.phase == Phase.PLAYER -> PlayDock(vm)
            s.phase == Phase.INSURANCE -> InsuranceDock(vm)
            vm.animating && s.phase == Phase.ROUND_OVER -> PlayDock(vm)
            else -> BetDock(vm)
        }
    }
}

@Composable
private fun PlayDock(vm: PlayViewModel) {
    val s = vm.state
    val o = s.options
    val ready = !vm.animating && s.phase == Phase.PLAYER
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val onlySignals = vm.settings.signals == 2
        GoldButton(if (onlySignals) "Tap felt: Hit" else "Hit", Modifier.weight(1f), ready && !onlySignals) { vm.act(Action.HIT) }
        GoldButton(if (onlySignals) "Wave: Stand" else "Stand", Modifier.weight(1f), ready && !onlySignals) { vm.act(Action.STAND) }
        GoldButton("Double", Modifier.weight(1f), ready && o.canDouble) { vm.act(Action.DOUBLE) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GoldButton("Split", Modifier.weight(1f), ready && o.canSplit) { vm.act(Action.SPLIT) }
        if (vm.settings.surrender) GoldButton("Surrender", Modifier.weight(1f), ready && o.canSurrender) { vm.act(Action.SURRENDER) }
    }
}

@Composable
private fun InsuranceDock(vm: PlayViewModel) {
    val s = vm.state
    val ready = !vm.animating
    val evenMoney = s.hands.firstOrNull()?.isBlackjack == true
    Text(
        if (evenMoney) "You have blackjack and the dealer shows an Ace. Take even money (a sure 1 to 1 payout)?"
        else "Dealer shows an Ace. Insurance costs ${money(s.hands.firstOrNull()?.bet?.div(2) ?: 0)}.",
        fontSize = 13.sp, maxLines = 2,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GoldButton(if (evenMoney) "Even money" else "Insure", Modifier.weight(1f), ready) { vm.insurance(true) }
        GoldButton(if (evenMoney) "No thanks" else "No insurance", Modifier.weight(1f), ready) { vm.insurance(false) }
    }
}

@Composable
private fun QuizDock(vm: PlayViewModel, q: QuizState) {
    if (q.stage == QuizStage.RUNNING) {
        Text("Count check: what's the running count?", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
        NumberStepper(q.runningAnswer, vm::setRunningAnswer, Modifier.fillMaxWidth(), range = -60..60)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.settings.voice) VoiceButton(Modifier.width(120.dp)) { heard -> vm.voiceAnswer(heard) }
            GoldButton("Check", Modifier.weight(1f)) { vm.submitRunning() }
        }
    } else {
        val rc = if (q.runningCorrect == true) "Correct, RC ${signed(q.actualRunning)}." else "RC was ${signed(q.actualRunning)}."
        Text(
            "$rc Now the ${vm.system.label} true count (%.1f decks left).".format(q.decksRemaining),
            fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            color = if (q.runningCorrect == true) GoodGreen else BadRed, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        NumberStepper(q.trueAnswer, vm::setTrueAnswer, Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (vm.settings.voice) VoiceButton(Modifier.width(120.dp)) { heard -> vm.voiceAnswer(heard) }
            GoldButton("Check", Modifier.weight(1f)) { vm.submitTrue() }
        }
    }
}

@Composable
private fun BetDock(vm: PlayViewModel) {
    val s = vm.state
    if (s.bankroll < 10 && vm.bettingOpen) {
        Text("You're out of chips.", fontSize = 15.sp)
        GoldButton("Reset bankroll to $1,000", Modifier.fillMaxWidth()) { vm.resetBankroll() }
        return
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chipSize = minOf(56.dp, (maxWidth - 90.dp) / 4.6f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ChipStyles.forEach { st ->
                val can = vm.bettingOpen && vm.bet + st.value <= s.bankroll
                CasinoChip(
                    st, chipSize,
                    Modifier.alpha(if (can) 1f else 0.35f).clickable(enabled = can) { vm.addChip(st.value) },
                )
            }
            TextButton(onClick = { vm.clearBet() }, enabled = vm.bettingOpen) { Text("Clear", color = Gold) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val dealB = @Composable { GoldButton(if (vm.bet == 0) "Add chips" else "Deal ${money(vm.bet)}", Modifier.weight(1.4f), vm.canDeal) { vm.deal() } }
        if (vm.settings.leftHanded) dealB()
        if (vm.mode != TableMode.NORMAL) GoldButton("Stand up", Modifier.weight(0.9f), vm.bettingOpen, primary = false) { vm.standUp() }
        else GoldButton("Sit out", Modifier.weight(0.9f), vm.bettingOpen, primary = false) { vm.sitOut() }
        GoldButton("Undo", Modifier.weight(0.8f), vm.bettingOpen && vm.bet > 0, primary = false) { vm.undoChip() }
        if (!vm.settings.leftHanded) dealB()
    }
}

/** Did the dealer get it right? */
@Composable
private fun DealerCheckDock(vm: PlayViewModel, c: com.hilotrainer.engine.DealerErrors.Check) {
    Text("The dealer says: \u201C${c.said}\u201D", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text("Check the cards on the table. Is that right?", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f), maxLines = 1)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val ok = @Composable { GoldButton("Looks right", Modifier.weight(1f), primary = false) { vm.callDealer(false) } }
        val bad = @Composable { GoldButton("That's wrong", Modifier.weight(1f)) { vm.callDealer(true) } }
        if (vm.settings.leftHanded) { bad(); ok() } else { ok(); bad() }
    }
}

@Composable
private fun BreakDock(vm: PlayViewModel) {
    val m = vm.breakLeftMs / 60_000L
    val sec = vm.breakLeftMs / 1000L % 60
    Text("On a break: %d:%02d left".format(m, sec), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Text("Look away from the screen and rest your eyes. The session clock is paused.", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f), maxLines = 2)
    GoldButton("Back to the table", Modifier.fillMaxWidth()) { vm.endBreak() }
}


@Composable
private fun WatchDock(vm: PlayViewModel) {
    Text("Watch mode is dealing rounds for you. Count every card, including the other players' cards.", fontSize = 13.sp, maxLines = 2)
    GoldButton("Stop watching", Modifier.fillMaxWidth()) { vm.stopWatching() }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CountAudit(vm: PlayViewModel, modifier: Modifier) {
    val seen = if (vm.animating) emptyList() else vm.state.seenSinceShuffle
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Count audit", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gold)
        Text(
            "Every card you've seen since the shuffle, with the running count after it. The dealer's hole card counts when it flips.",
            fontSize = 13.sp, color = Cream.copy(alpha = 0.75f),
        )
        if (vm.animating) Text("Finish the current deal to see the audit.")
        val sys = vm.system
        val start = sys.initialCount(vm.settings.decks)
        Text("${seen.size} cards · ${sys.label} running count ${signed(start + seen.sumOf { sys.tag(it) })}", fontWeight = FontWeight.SemiBold)
        if (start != 0) Text("KO starts this shoe at ${signed(start)}.", fontSize = 13.sp, color = Cream.copy(alpha = 0.75f))
        var rc = start
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            seen.forEach { c ->
                val tag = sys.tag(c)
                rc += tag
                val tagColor = when { tag > 0 -> GoodGreen; tag < 0 -> BadRed; else -> Cream.copy(alpha = 0.7f) }
                Column(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Ink.copy(alpha = 0.6f)).border(1.dp, tagColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.rank.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Cream)
                        SuitIcon(c.suit, 11.dp, color = if (c.suit.isRed) Color(0xFFFF6B6B) else Cream)
                    }
                    Text(signed(tag), fontSize = 11.sp, color = tagColor)
                    Text(signed(rc), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                }
            }
        }
        Text("Top: card. Middle: its ${sys.label} value. Bottom: running count after it.", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
    }
}

@Composable
fun SessionReport(vm: PlayViewModel, modifier: Modifier) {
    val hist = vm.bankrollHistory.toList()
    val net = (hist.lastOrNull() ?: 1000) - (hist.firstOrNull() ?: 1000)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Session report", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gold)
        Panel {
            Text("Bankroll", fontWeight = FontWeight.SemiBold)
            BankrollChart(hist, Modifier.fillMaxWidth().height(150.dp).padding(top = 6.dp))
            Text(
                "Net ${if (net >= 0) "+" else "-"}${money(kotlin.math.abs(net))} (${if (net >= 0) "+" else "-"}${"%.1f".format(kotlin.math.abs(net) / 10.0).removeSuffix(".0")} units of $10) over ${vm.roundsThisSession} rounds",
                fontSize = 14.sp, color = if (net >= 0) GoodGreen else BadRed,
            )
            Text("Short sessions swing a lot. Judge yourself on accuracy, not money.", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
        }
        Panel {
            Text("Accuracy", fontWeight = FontWeight.SemiBold)
            Text("Strategy: ${pct(vm.sessionCorrect, vm.sessionDecisions)}", fontSize = 14.sp)
            Text("Bet sizing: ${pct(vm.sessionBetsOk, vm.sessionBets)}", fontSize = 14.sp)
            Text("Count checks: ${pct(vm.sessionQuizCorrect, vm.sessionQuizzes)}", fontSize = 14.sp)
            Text("Hints used: ${vm.hintsThisSession}", fontSize = 14.sp)
            if (vm.system.balanced) Text("True count range seen: ${signed1(vm.minTrueCount)} to ${signed1(vm.maxTrueCount)}", fontSize = 14.sp)
            if (vm.settings.coverPlay) Text("Peak heat: %.1f (warning at %.0f, leave at %.0f)".format(vm.peakHeat, com.hilotrainer.engine.CoverPlay.WARN_AT, com.hilotrainer.engine.CoverPlay.LEAVE_AT), fontSize = 14.sp)
        }
        EvPanel(vm)
        if (vm.sessionActive || vm.sessionDone || vm.sessionBlocks.isNotEmpty()) SessionBlocksPanel(vm)
    }
}

/** Expected value from the count versus what luck did. */
@Composable
fun EvPanel(vm: PlayViewModel) {
    @Suppress("UNUSED_VARIABLE") val v = vm.evVersion
    val ev = vm.ev
    Panel {
        Text("Edge versus luck", fontWeight = FontWeight.SemiBold)
        if (ev.hands == 0) {
            Text("Play a few hands to see how much of your result came from your edge and how much from luck.", fontSize = 13.sp, color = Cream.copy(alpha = 0.7f))
            return@Panel
        }
        val exp = ev.expected
        Text("Expected from your bets and the count: ${if (exp >= 0) "+" else "-"}${money(kotlin.math.abs(exp).toInt())}", fontSize = 14.sp)
        Text("Actual result: ${if (ev.actual >= 0) "+" else "-"}${money(kotlin.math.abs(ev.actual))}", fontSize = 14.sp)
        Text(
            "Luck: ${if (ev.luck >= 0) "+" else "-"}${money(kotlin.math.abs(ev.luck).toInt())} over ${ev.hands} hands",
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (ev.luck >= 0) GoodGreen else BadRed,
        )
        Text(
            "Your edge per hand: %+.2f%% of the money bet. Uses the rough Hi-Lo rule of thumb: about -0.5%% at a true count of 0, plus about 0.5%% for each +1, with perfect play.".format(if (ev.wagered == 0) 0.0 else exp / ev.wagered * 100),
            fontSize = 12.sp, color = Cream.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SessionBlocksPanel(vm: PlayViewModel) {
    Panel {
        Text("One-hour session", fontWeight = FontWeight.SemiBold)
        Text("%d of %d minutes played, %d break%s taken".format(vm.sessionElapsedMs / 60_000L, com.hilotrainer.engine.SessionPlan.MINUTES, vm.breaksTaken, if (vm.breaksTaken == 1) "" else "s"), fontSize = 14.sp)
        val blocks = vm.sessionBlocks.toList()
        Row(Modifier.fillMaxWidth().height(90.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            for (i in 0 until com.hilotrainer.engine.SessionPlan.BLOCKS) {
                val a = blocks.getOrNull(i)?.accuracy
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(a?.let { "${(it * 100).toInt()}" } ?: "", fontSize = 10.sp, color = Cream.copy(alpha = 0.75f))
                    Box(
                        Modifier.fillMaxWidth().height((56.dp * (a?.toFloat() ?: 0f)).coerceAtLeast(2.dp)).clip(RoundedCornerShape(3.dp))
                            .background(if (a == null) Cream.copy(alpha = 0.15f) else if (a >= 0.9) GoodGreen else if (a >= 0.75) Gold else BadRed),
                    )
                    Text("${i * 10}", fontSize = 10.sp, color = Cream.copy(alpha = 0.6f))
                }
            }
        }
        Text("Accuracy by 10-minute block, plays and count checks together.", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
        com.hilotrainer.engine.SessionPlan.fatigueDrop(blocks)?.let { d ->
            Text(
                if (d > 3) "Your accuracy fell %.0f points from the start to the end. That's fatigue: take the breaks.".format(d)
                else "Your accuracy held up across the session.",
                fontSize = 13.sp, color = if (d > 3) BadRed else GoodGreen,
            )
        }
    }
}

@Composable
private fun BankrollChart(points: List<Int>, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) {
            drawLine(Gold.copy(alpha = 0.4f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
            return@Canvas
        }
        val min = points.min().toFloat(); val max = points.max().toFloat()
        val span = (max - min).coerceAtLeast(20f)
        val start = points.first().toFloat()
        fun y(v: Float) = size.height - (v - min) / span * size.height * 0.9f - size.height * 0.05f
        val stepX = size.width / (points.size - 1)
        drawLine(Cream.copy(alpha = 0.25f), Offset(0f, y(start)), Offset(size.width, y(start)), 1.5f)
        val path = androidx.compose.ui.graphics.Path()
        points.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v.toFloat())) else path.lineTo(i * stepX, y(v.toFloat())) }
        drawPath(path, Gold, style = Stroke(2.5.dp.toPx()))
        drawCircle(Gold, 4.dp.toPx(), Offset((points.size - 1) * stepX, y(points.last().toFloat())))
    }
}

// ---------- Coach column (side panel on the inner screen, sheet on the cover screen) ----------

@Composable
fun CoachColumn(vm: PlayViewModel, modifier: Modifier) {
    val s = vm.state
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        vm.careerLevel?.let { lvl -> CareerPanel(vm, lvl) }
        if (vm.flatBetOnly) Panel {
            Text("Flat betting only", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = BadRed)
            Text("The pit locked your bet. Raise it and you'll be backed off. Use Leave table in the menu to find a new game.", fontSize = 13.sp)
        }
        if (vm.mode == TableMode.TEAM) Panel {
            Text("Signal code", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold)
            com.hilotrainer.engine.TeamSignals.SIGNALS.forEach { Text("${it.gesture}: ${it.meaning}", fontSize = 13.sp) }
        }
        if (vm.settings.perfectPairs > 0 && (vm.settings.showRunningCount || vm.peeking)) Panel {
            Text("Side bet value now", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold)
            Text("Perfect Pairs: ${"%+.1f".format(vm.perfectPairsEvNow() * 100)}% per bet", fontSize = 14.sp)
            Text("Worked out from the cards you haven't seen yet.", fontSize = 12.sp, color = Cream.copy(alpha = 0.6f))
        }
        Panel {
            Text("Count", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold)
            if (vm.settings.showRunningCount || vm.peeking) {
                Text(
                    "${vm.system.label} running ${signed(vm.visibleRunningCount)}" + if (vm.system.balanced) "   True ${signed1(vm.visibleTrueCount)}" else "",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text("Hidden. Tap Peek count at the top to see it for a few seconds (peeks are tracked).", fontSize = 13.sp, color = Cream.copy(alpha = 0.75f))
            }
            Spacer(Modifier.height(6.dp))
            ShoeBar(vm.visibleCardsDealt, s.totalCards)
        }
        Panel {
            Text("This session", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold)
            Text("Strategy: ${pct(vm.sessionCorrect, vm.sessionDecisions)}", fontSize = 14.sp)
            Text("Streak: ${vm.streak}", fontSize = 14.sp)
            Text("Count checks: ${pct(vm.sessionQuizCorrect, vm.sessionQuizzes)}", fontSize = 14.sp)
            Text("Hints used: ${vm.hintsThisSession}", fontSize = 14.sp)
        }
        Panel {
            Text("Coach log", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold)
            if (vm.log.isEmpty()) Text("Your decisions will show up here.", fontSize = 13.sp, color = Cream.copy(alpha = 0.7f))
            vm.log.take(40).forEach { LogRow(it) }
        }
        val r = vm.settings
        Text(
            "${r.decks} decks · dealer ${if (r.dealerHitsSoft17) "hits" else "stands on"} soft 17 · ${if (r.doubleAfterSplit) "double after split" else "no double after split"} · ${if (r.sixToFive) "blackjack pays 6:5" else "blackjack pays 3:2"} · " +
                "${if (r.surrender) "late surrender" else "no surrender"} · ${r.bots} other player${if (r.bots == 1) "" else "s"}" +
                if (r.deviations) " · count plays on" else "",
            fontSize = 11.sp, color = Cream.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CareerPanel(vm: PlayViewModel, lvl: Int) {
    val l = CareerLevels.ALL[lvl]
    val c = remember(vm.sessionDecisions, vm.sessionQuizzes) { vm.careerProgress() }
    Panel {
        Text("Career: ${l.name}", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Gold)
        Text(l.table, fontSize = 13.sp, color = Cream.copy(alpha = 0.75f))
        if (lvl >= CareerLevels.ALL.size - 1) Text("Top level. Keep your accuracy up.", fontSize = 13.sp)
        else {
            Text("Plays: ${minOf(c.decisions, l.needDecisions)} of ${l.needDecisions}, ${c.accuracy()}% (need ${l.needAccuracy}%)", fontSize = 13.sp)
            Text("Count checks: ${minOf(c.counts, l.needCounts)} of ${l.needCounts}, ${c.countAccuracy()}% (need ${l.needCountAccuracy}%)", fontSize = 13.sp)
        }
    }
}

@Composable
private fun LogRow(d: Decision) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 3.dp).size(10.dp).clip(CircleShape)
                .background(if (d.correct) GoodGreen else BadRed),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            val kind = when (d.kind) { DecisionKind.BET -> "Bet"; DecisionKind.INSURANCE -> "Insurance"; DecisionKind.PLAY -> "Play" }
            Text(
                "$kind: ${d.chosen}" + if (!d.correct) "  (right: ${d.advised})" else "" + if (d.isDeviation) "  count play" else "",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(d.situation, fontSize = 12.sp, color = Cream.copy(alpha = 0.7f), textAlign = TextAlign.Start)
        }
    }
}

/** Fold posture, provided by the activity. [hingeTopPx] is in window coordinates. */
data class FoldInfo(val tabletop: Boolean = false, val hingeTopPx: Float = 0f, val hingeHeightPx: Float = 0f)

val LocalFold = androidx.compose.runtime.staticCompositionLocalOf { FoldInfo() }

@Composable
private fun ModeDock(vm: PlayViewModel) {
    val team = vm.mode == TableMode.TEAM
    Text(
        if (team) "You're the spotter. Count, then call in the big player when the count is good."
        else "You're watching from behind the table. Sit down when the count is good.",
        fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GoldButton(if (team) "Keep spotting" else "Keep watching", Modifier.weight(1f), vm.bettingOpen, primary = false) { vm.keepWatching() }
        GoldButton(if (team) "Call big player" else "Sit down", Modifier.weight(1f), vm.bettingOpen) { if (team) vm.callBigPlayer() else vm.sitDown() }
    }
    Text("Calls: ${pct(vm.modeCorrect, vm.modeCalls)}", fontSize = 12.sp, color = Cream.copy(alpha = 0.7f))
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SignalDock(vm: PlayViewModel) {
    Text("Signal the count to your big player:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    // All five signals in two rows of large targets: the four count levels, then leave.
    val sigs = com.hilotrainer.engine.TeamSignals.SIGNALS
    listOf(listOf(0, 1, 2), listOf(3, 4)).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { i ->
                val shape = RoundedCornerShape(14.dp)
                Box(
                    Modifier.weight(1f).height(44.dp).clip(shape)
                        .background(if (i == 4) Color(0xFF4A1E1E) else Color(0xFF1A3D2B))
                        .border(1.dp, Gold.copy(alpha = 0.6f), shape)
                        .clickable { vm.giveSignal(i) }.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(sigs[i].gesture, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = Gold, maxLines = 1, softWrap = false)
                        Text(sigs[i].meaning.substringBefore(":"), fontSize = 10.sp, lineHeight = 12.sp, color = Cream.copy(alpha = 0.75f), maxLines = 1)
                    }
                }
            }
        }
    }
}


/** The dealer lifts the corner of the hole card to check for blackjack. */
private fun Modifier.androidxLift(p: Float): Modifier = this.graphicsLayer {
    rotationX = -32f * p
    translationY = -6f * density * p
    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
}

/** Your bet: each chip you add slides in from your side of the table. */
@Composable
private fun BetChips(vm: PlayViewModel, amount: Int, size: Dp) {
    val y = remember { androidx.compose.animation.core.Animatable(0f) }
    var last by remember { mutableStateOf(amount) }
    LaunchedEffect(amount) {
        if (amount > last && !vm.instant) { y.snapTo(28f); y.animateTo(0f, tween(220)) }
        last = amount
    }
    ChipStack(amount, size, Modifier.graphicsLayer { translationY = y.value * density })
}

/** After a round: winnings are paid in a stack beside your bet, losses are swept to the rack. */
@Composable
private fun RoundChips(vm: PlayViewModel, size: Dp) {
    val s = vm.state
    val net = vm.roundNet
    val done = s.phase == Phase.ROUND_OVER && !vm.animating && net != null && net != 0
    if (!done) return
    val p = remember(s.roundStartSeq) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(s.roundStartSeq) { p.animateTo(1f, tween(if (vm.instant) 1 else 700)) }
    val n = net ?: 0
    if (n > 0) {
        // The payout slides down from the dealer and stops next to your bet.
        ChipStack(n, size * 0.9f, Modifier.graphicsLayer {
            translationX = -34f * density
            translationY = -160f * density * (1 - p.value)
            alpha = p.value.coerceAtLeast(0.2f)
        })
    } else {
        // The lost bet is swept up toward the rack and fades.
        ChipStack(-n, size, Modifier.graphicsLayer {
            translationY = -170f * density * p.value
            alpha = 1f - p.value
        })
    }
}
