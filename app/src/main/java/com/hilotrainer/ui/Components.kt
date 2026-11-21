package com.hilotrainer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hilotrainer.engine.Rank
import com.hilotrainer.engine.Suit
import com.hilotrainer.engine.Card as CardModel

// Palette
val Felt = Color(0xFF0F5A37)
val FeltLight = Color(0xFF1C8A55)
val FeltDark = Color(0xFF0A2E1E)
val Ink = Color(0xFF07140E)
val Gold = Color(0xFFE9C46A)
val GoldDeep = Color(0xFFB8892B)
val Cream = Color(0xFFF7EEDB)
val GoodGreen = Color(0xFF7BE495)
val BadRed = Color(0xFFFF8A80)
val WoodTop = Color(0xFF4A2A14)
val WoodBottom = Color(0xFF2A160A)
val CardRed = Color(0xFFC62828)
val CardBlack = Color(0xFF15171A)

private val Scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF2B2100),
    secondary = Color(0xFF9AD0B5),
    background = Felt,
    onBackground = Cream,
    surface = FeltDark,
    onSurface = Cream,
    surfaceVariant = Color(0xFF14603D),
    onSurfaceVariant = Color(0xFFD5E8DC),
    secondaryContainer = Color(0xFF7A6420),
    onSecondaryContainer = Color.White,
    surfaceContainerLow = FeltDark,
    surfaceContainer = FeltDark,
)

val TitleFont = FontFamily.Serif

@Composable
fun HiLoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        CompositionLocalProvider(LocalContentColor provides Cream, content = content)
    }
}

fun signed(n: Int) = if (n > 0) "+$n" else n.toString()
fun signed1(d: Double): String {
    val s = "%.1f".format(d)
    return if (d > 0.04) "+$s" else s
}
fun money(n: Int): String = "%,d".format(n).let { "$$it" }
fun pct(ok: Int, total: Int) = if (total == 0) "n/a" else "${ok * 100 / total}% ($ok of $total)"

/** Felt with a soft spotlight and darker edges. */
fun Modifier.feltBackground(): Modifier = drawBehind {
    drawRect(Felt)
    drawRect(
        Brush.radialGradient(
            listOf(FeltLight.copy(alpha = 0.85f), Felt.copy(alpha = 0f)),
            center = Offset(size.width / 2, size.height * 0.42f),
            radius = maxOf(size.width, size.height) * 0.75f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
            center = Offset(size.width / 2, size.height / 2),
            radius = maxOf(size.width, size.height) * 0.8f,
        ),
    )
    // Overhead spotlight: a warm pool of light on the middle of the table.
    drawRect(
        Brush.radialGradient(
            listOf(Color(0x2EFFF3C4), Color.Transparent),
            center = Offset(size.width / 2, size.height * 0.38f),
            radius = minOf(size.width, size.height) * 0.62f,
        ),
    )
    // Felt weave in both directions
    val step = 6.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(Color.White.copy(alpha = 0.018f), Offset(0f, y), Offset(size.width, y), 1f)
        y += step
    }
    var x = step / 2
    while (x < size.width) {
        drawLine(Color.Black.copy(alpha = 0.03f), Offset(x, 0f), Offset(x, size.height), 1f)
        x += step
    }
}

fun Modifier.woodRail(): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(listOf(WoodTop, WoodBottom)))
    val grain = 5.dp.toPx()
    var y = grain
    var i = 0
    while (y < size.height) {
        drawLine(Color.Black.copy(alpha = 0.12f + 0.06f * (i % 3)), Offset(0f, y), Offset(size.width, y + (i % 2) * 2f), 1.2f)
        y += grain; i++
    }
    drawLine(Gold.copy(alpha = 0.7f), Offset(0f, size.height - 1.5f), Offset(size.width, size.height - 1.5f), 2f)
}

@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().feltBackground()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).woodRail().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Gold)
            }
            Text(title, fontFamily = TitleFont, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Cream, modifier = Modifier.weight(1f))
            actions()
        }
        Box(Modifier.fillMaxSize()) { content() }
    }
}

// Suit shapes, drawn in a unit box and scaled.
fun suitPath(suit: Suit, w: Float, h: Float): Path = Path().apply {
    fun mx(x: Float) = x * w
    fun my(y: Float) = y * h
    when (suit) {
        Suit.DIAMONDS -> {
            moveTo(mx(0.5f), my(0f)); lineTo(mx(0.92f), my(0.5f)); lineTo(mx(0.5f), my(1f)); lineTo(mx(0.08f), my(0.5f)); close()
        }
        Suit.HEARTS -> {
            moveTo(mx(0.5f), my(0.97f))
            cubicTo(mx(-0.12f), my(0.52f), mx(0.02f), my(-0.08f), mx(0.5f), my(0.24f))
            cubicTo(mx(0.98f), my(-0.08f), mx(1.12f), my(0.52f), mx(0.5f), my(0.97f))
            close()
        }
        Suit.SPADES -> {
            moveTo(mx(0.5f), my(0f))
            cubicTo(mx(1.12f), my(0.42f), mx(0.98f), my(0.92f), mx(0.53f), my(0.68f))
            lineTo(mx(0.66f), my(1f)); lineTo(mx(0.34f), my(1f)); lineTo(mx(0.47f), my(0.68f))
            cubicTo(mx(0.02f), my(0.92f), mx(-0.12f), my(0.42f), mx(0.5f), my(0f))
            close()
        }
        Suit.CLUBS -> {
            addOval(androidx.compose.ui.geometry.Rect(mx(0.29f), my(0.0f), mx(0.71f), my(0.42f)))
            addOval(androidx.compose.ui.geometry.Rect(mx(0.04f), my(0.34f), mx(0.46f), my(0.76f)))
            addOval(androidx.compose.ui.geometry.Rect(mx(0.54f), my(0.34f), mx(0.96f), my(0.76f)))
            moveTo(mx(0.5f), my(0.45f)); lineTo(mx(0.66f), my(1f)); lineTo(mx(0.34f), my(1f)); close()
        }
    }
}

fun DrawScope.drawSuit(suit: Suit, center: Offset, sizePx: Float, color: Color, flipped: Boolean = false) {
    val p = suitPath(suit, sizePx, sizePx)
    translate(center.x - sizePx / 2, center.y - sizePx / 2) {
        if (flipped) rotate(180f, pivot = Offset(sizePx / 2, sizePx / 2)) { drawPath(p, color) } else drawPath(p, color)
    }
}

@Composable
fun SuitIcon(suit: Suit, size: Dp, color: Color = if (suit.isRed) CardRed else CardBlack, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawPath(suitPath(suit, this.size.width, this.size.height), color) }
}

/** Pip positions (x in 0..1 across, y in 0..1 down) for number cards. */
private fun pips(rank: Rank): List<Pair<Float, Float>> {
    val l = 0.355f; val c = 0.5f; val r = 0.645f
    return when (rank) {
        Rank.TWO -> listOf(c to 0f, c to 1f)
        Rank.THREE -> listOf(c to 0f, c to 0.5f, c to 1f)
        Rank.FOUR -> listOf(l to 0f, r to 0f, l to 1f, r to 1f)
        Rank.FIVE -> listOf(l to 0f, r to 0f, c to 0.5f, l to 1f, r to 1f)
        Rank.SIX -> listOf(l to 0f, r to 0f, l to 0.5f, r to 0.5f, l to 1f, r to 1f)
        Rank.SEVEN -> listOf(l to 0f, r to 0f, c to 0.25f, l to 0.5f, r to 0.5f, l to 1f, r to 1f)
        Rank.EIGHT -> listOf(l to 0f, r to 0f, c to 0.25f, l to 0.5f, r to 0.5f, c to 0.75f, l to 1f, r to 1f)
        Rank.NINE -> listOf(l to 0f, r to 0f, l to 0.333f, r to 0.333f, c to 0.5f, l to 0.667f, r to 0.667f, l to 1f, r to 1f)
        Rank.TEN -> listOf(l to 0f, r to 0f, c to 0.167f, l to 0.333f, r to 0.333f, l to 0.667f, r to 0.667f, c to 0.833f, l to 1f, r to 1f)
        else -> emptyList()
    }
}

/** Card back design and how worn the cards look. */
data class CardStyle(val back: Int = 0, val wear: Float = 0f)
val LocalCardStyle = androidx.compose.runtime.staticCompositionLocalOf { CardStyle() }

/** Made-up casino decks. */
object CardBacks {
    data class Back(val name: String, val a: Color, val b: Color, val trim: Color)
    val ALL = listOf(
        Back("Crimson Crown", Color(0xFF9E1B2C), Color(0xFF5C0C18), Gold),
        Back("Royal Blue", Color(0xFF1E4FA8), Color(0xFF0D2557), Color(0xFFD7E3FF)),
        Back("Emerald Palms", Color(0xFF1C7A4E), Color(0xFF0B3D27), Color(0xFFE8C26A)),
        Back("Midnight Gold", Color(0xFF202020), Color(0xFF060606), Color(0xFFE8C26A)),
    )
}

@Composable
fun CardFace(card: CardModel, width: Dp, modifier: Modifier = Modifier) {
    val wear = LocalCardStyle.current.wear
    val height = width * 1.4f
    val color = if (card.suit.isRed) CardRed else CardBlack
    val shape = RoundedCornerShape(width * 0.09f)
    val corner = width.value
    Box(
        modifier.size(width, height).semantics { contentDescription = cardName(card) }.shadow(width * 0.06f, shape).clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF3EFE6))))
            .border(0.6.dp, Color(0xFFCFC8B8), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val rank = card.rank
            when {
                rank == Rank.ACE -> {
                    val s = w * if (card.suit == Suit.SPADES) 0.56f else 0.44f
                    drawSuit(card.suit, Offset(w / 2, h / 2), s, color)
                }
                rank == Rank.JACK || rank == Rank.QUEEN || rank == Rank.KING -> {
                    val inset = Size(w * 0.56f, h * 0.62f)
                    val tl = Offset((w - inset.width) / 2, (h - inset.height) / 2)
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFFFFF4D6), if (card.suit.isRed) Color(0xFFF6D3C7) else Color(0xFFD9DEE8)),
                            startY = tl.y, endY = tl.y + inset.height,
                        ),
                        tl, inset,
                    )
                    drawRect(GoldDeep, tl, inset, style = Stroke(w * 0.025f))
                    drawRect(color.copy(alpha = 0.5f), Offset(tl.x + w * 0.035f, tl.y + w * 0.035f), Size(inset.width - w * 0.07f, inset.height - w * 0.07f), style = Stroke(w * 0.01f))
                    drawSuit(card.suit, Offset(w / 2, tl.y + inset.height * 0.78f), w * 0.16f, color)
                    drawSuit(card.suit, Offset(w / 2, tl.y + inset.height * 0.22f), w * 0.16f, color, flipped = true)
                }
                else -> {
                    val pipSize = w * if (rank.value >= 9) 0.15f else 0.17f
                    val top = h * 0.19f; val bottom = h * 0.81f
                    for ((px, py) in pips(rank)) {
                        val cx = w * px
                        val cy = top + (bottom - top) * py
                        drawSuit(card.suit, Offset(cx, cy), pipSize, color, flipped = py > 0.5f)
                    }
                }
            }
        }
        if (card.rank == Rank.JACK || card.rank == Rank.QUEEN || card.rank == Rank.KING) {
            Text(
                card.rank.label, color = color, fontFamily = TitleFont, fontWeight = FontWeight.Bold,
                fontSize = (corner * 0.34f).sp, modifier = Modifier.align(Alignment.Center),
            )
        }
        CornerIndex(card, corner, color, Modifier.align(Alignment.TopStart))
        CornerIndex(card, corner, color, Modifier.align(Alignment.BottomEnd).graphicsLayer { rotationZ = 180f })
        if (wear > 0.01f) Canvas(Modifier.fillMaxSize()) {
            // Handled cards pick up a faint tint and scuffs along the edges.
            drawRect(Color(0xFFB8A27A).copy(alpha = 0.10f * wear))
            val r = kotlin.random.Random(card.hashCode())
            repeat((6 * wear).toInt() + 1) {
                val y = r.nextFloat() * size.height
                val x = if (r.nextBoolean()) 0f else size.width
                drawLine(Color(0xFF7A6A4F).copy(alpha = 0.18f * wear), Offset(x, y), Offset(x + (if (x == 0f) 1 else -1) * size.width * 0.12f * r.nextFloat(), y + size.height * 0.02f), 1f)
            }
        }
    }
}

@Composable
private fun CornerIndex(card: CardModel, w: Float, color: Color, modifier: Modifier) {
    Column(modifier.padding(start = (w * 0.05f).dp, top = (w * 0.03f).dp, end = (w * 0.05f).dp, bottom = (w * 0.03f).dp).width((w * 0.2f).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            card.rank.label, color = color, fontWeight = FontWeight.Bold, fontSize = (w * if (card.rank == Rank.TEN) 0.17f else 0.2f).sp,
            lineHeight = (w * 0.2f).sp, maxLines = 1, softWrap = false,
            letterSpacing = if (card.rank == Rank.TEN) (-0.08).sp * w / 10 else 0.sp,
        )
        SuitIcon(card.suit, (w * 0.13f).dp)
    }
}

fun cardName(c: CardModel): String {
    val r = when (c.rank) {
        Rank.ACE -> "Ace"; Rank.KING -> "King"; Rank.QUEEN -> "Queen"; Rank.JACK -> "Jack"
        else -> c.rank.label
    }
    return "$r of ${c.suit.name.lowercase()}"
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.09f)
    val design = CardBacks.ALL.getOrElse(LocalCardStyle.current.back) { CardBacks.ALL[0] }
    Box(
        modifier.size(width, width * 1.4f).semantics { contentDescription = "Face down card" }.shadow(width * 0.06f, shape).clip(shape).background(Color.White).padding(width * 0.05f)
            .clip(RoundedCornerShape(width * 0.06f))
            .drawBehind {
                drawRect(Brush.linearGradient(listOf(design.a, design.b)))
                val step = size.width / 6f
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(Color.White.copy(alpha = 0.16f), Offset(x, 0f), Offset(x + size.height, size.height), 1.2f)
                    drawLine(Color.White.copy(alpha = 0.16f), Offset(x + size.height, 0f), Offset(x, size.height), 1.2f)
                    x += step
                }
                drawRect(design.trim.copy(alpha = 0.55f), Offset(size.width * 0.08f, size.width * 0.08f), Size(size.width * 0.84f, size.height - size.width * 0.16f), style = Stroke(1.5f))
                drawSuit(Suit.SPADES, Offset(size.width / 2, size.height / 2), size.width * 0.32f, design.trim.copy(alpha = 0.7f))
            },
    )
}

/** Static card, used by the drills. */
@Composable
fun PlayingCard(card: CardModel?, width: Dp, faceDown: Boolean = false, modifier: Modifier = Modifier) {
    if (faceDown || card == null) CardBack(width, modifier) else CardFace(card, width, modifier)
}

/**
 * A card on the table. On first appearance it slides in from the shoe ([from]),
 * and it flips over when [faceUp] changes.
 */
@Composable
fun TableCard(card: CardModel, width: Dp, faceUp: Boolean, from: DpOffset, animate: Boolean, durationMs: Int = 320, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) progress.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
    }
    val flip by animateFloatAsState(if (faceUp) 0f else 180f, tween(360), label = "flip")
    val density = LocalDensity.current
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            translationX = with(density) { from.x.toPx() } * (1 - p)
            translationY = with(density) { from.y.toPx() } * (1 - p)
            rotationZ = -18f * (1 - p)
            rotationY = flip
            cameraDistance = 14f * density.density
            alpha = (p * 3f).coerceAtMost(1f)
        },
    ) {
        if (flip < 90f) CardFace(card, width) else CardBack(width)
    }
}

/** Cards in a hand, overlapping. The spacing tightens to fit [maxWidth]. */
@Composable
fun CardRow(cards: List<CardModel>, width: Dp, hideIndex: Int = -1, maxWidth: Dp = Dp.Unspecified) {
    val n = cards.size
    var step = width * 0.36f
    if (maxWidth != Dp.Unspecified && n > 1) step = minOf(step, (maxWidth - width) / (n - 1))
    Box(Modifier.size(width + step * (n - 1).coerceAtLeast(0), width * 1.4f)) {
        cards.forEachIndexed { i, c ->
            PlayingCard(c, width, faceDown = i == hideIndex, modifier = Modifier.offset(x = step * i))
        }
    }
}

// Chips
data class ChipStyle(val value: Int, val base: Color, val stripe: Color, val text: Color)

val ChipStyles = listOf(
    ChipStyle(10, Color(0xFF1E63C6), Color.White, Color.White),
    ChipStyle(50, Color(0xFFE07A1F), Color.White, Color.White),
    ChipStyle(100, Color(0xFF1C1C1C), Gold, Gold),
    ChipStyle(500, Color(0xFF6A2C91), Color(0xFFF3D36B), Color.White),
)

fun chipStyleFor(value: Int) = ChipStyles.lastOrNull { it.value <= value } ?: ChipStyles[0]

@Composable
fun CasinoChip(style: ChipStyle, size: Dp, modifier: Modifier = Modifier, label: Boolean = true) {
    Box(modifier.size(size).shadow(size * 0.08f, CircleShape).clip(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            val c = Offset(r, r)
            drawCircle(style.base, r, c)
            // Edge spots
            for (i in 0 until 8) {
                rotate(i * 45f, c) {
                    drawRect(style.stripe, Offset(c.x - r * 0.12f, 0f), Size(r * 0.24f, r * 0.26f))
                }
            }
            drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.68f, c)
            drawCircle(style.base, r * 0.64f, c)
            drawCircle(style.stripe.copy(alpha = 0.8f), r * 0.64f, c, style = Stroke(r * 0.05f))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent), center = Offset(r * 0.7f, r * 0.6f), radius = r), r, c)
        }
        if (label) Text("${style.value}", color = style.text, fontWeight = FontWeight.Black, fontSize = (size.value * 0.26f).sp)
    }
}

/** A short stack of chips representing [amount]. */
@Composable
fun ChipStack(amount: Int, size: Dp, modifier: Modifier = Modifier) {
    val chips = remember(amount) {
        val out = mutableListOf<ChipStyle>()
        var left = amount
        for (s in ChipStyles.reversed()) while (left >= s.value && out.size < 8) { out += s; left -= s.value }
        out.reversed()
    }
    Box(modifier.size(size, size + size * 0.09f * (chips.size - 1).coerceAtLeast(0)), contentAlignment = Alignment.BottomCenter) {
        chips.forEachIndexed { i, s ->
            CasinoChip(s, size, Modifier.offset(y = -(size * 0.09f) * i), label = i == chips.size - 1)
        }
    }
}

/** One-tap number picker: a scrolling row of values with fine-adjust buttons at each end. */
@Composable
fun NumberStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, range: IntRange = -30..30) {
    val values = range.toList()
    val state = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (values.indexOf(value) - 3).coerceAtLeast(0),
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun set(v: Int) {
        val nv = v.coerceIn(range)
        onChange(nv)
        scope.launch {
            val info = state.layoutInfo
            val visible = info.visibleItemsInfo
            val idx = values.indexOf(nv)
            if (visible.isEmpty() || idx <= visible.first().index || idx >= visible.last().index) {
                state.animateScrollToItem((idx - visible.size / 2).coerceAtLeast(0))
            }
        }
    }
    Row(modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        StepButton("-") { set(value - 1) }
        androidx.compose.foundation.lazy.LazyRow(
            state = state,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(values.size) { i ->
                val v = values[i]
                val sel = v == value
                Box(
                    Modifier.size(width = 50.dp, height = 46.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (sel) Gold else Ink.copy(alpha = 0.6f))
                        .border(1.dp, Gold.copy(alpha = if (sel) 1f else 0.35f), RoundedCornerShape(10.dp))
                        .clickable { set(v) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(signed(v), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (sel) Ink else Cream)
                }
            }
        }
        StepButton("+") { set(value + 1) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        border = BorderStroke(1.dp, Gold),
        modifier = Modifier.width(44.dp),
    ) { Text(label, fontSize = 20.sp, color = Gold) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Ink.copy(alpha = 0.55f)),
        border = BorderStroke(1.dp, Gold.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
fun ShoeBar(cardsDealt: Int, totalCards: Int, modifier: Modifier = Modifier) {
    val frac = if (totalCards == 0) 0f else cardsDealt / totalCards.toFloat()
    Column(modifier) {
        Text(
            "Discard tray: %.1f of %d decks dealt".format(cardsDealt / 52.0, totalCards / 52),
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(GoldDeep, Gold))))
        }
    }
}

/** A pill-shaped gold button used across the table UI. */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val bg = when {
        !enabled -> Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x22FFFFFF)))
        primary -> Brush.verticalGradient(listOf(Color(0xFFF6DA8C), Gold, GoldDeep))
        else -> Brush.verticalGradient(listOf(Color(0xFF214A36), Color(0xFF12301F)))
    }
    Box(
        modifier.height(48.dp).shadow(if (enabled) 3.dp else 0.dp, shape).clip(shape).background(bg)
            .border(1.dp, if (primary && enabled) Color(0xFFFFF1C1) else Gold.copy(alpha = if (enabled) 0.6f else 0.2f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1,
            color = when { !enabled -> Cream.copy(alpha = 0.35f); primary -> Color(0xFF2B1D00); else -> Gold },
        )
    }
}
