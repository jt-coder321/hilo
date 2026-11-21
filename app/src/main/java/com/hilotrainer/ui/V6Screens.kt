package com.hilotrainer.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Card
import com.hilotrainer.engine.Course
import com.hilotrainer.engine.DailyChallenge
import com.hilotrainer.engine.DeckEstimate
import com.hilotrainer.engine.Edge
import com.hilotrainer.engine.ErrorMap
import com.hilotrainer.engine.HapticCode
import com.hilotrainer.engine.Kelly
import com.hilotrainer.engine.Rank
import com.hilotrainer.engine.Suit
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.random.Random

@Composable
private fun V6Col(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Head(text: String) = Text(text, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)

@Composable
private fun Note(text: String) = Text(text, fontSize = 13.sp, color = Cream.copy(alpha = 0.72f))

private val SeriesColors = listOf(Color(0xFFEF9A9A), Gold, Color(0xFF81C784))

// ---------------------------------------------------------------------------
// Stats dashboard and the share card
// ---------------------------------------------------------------------------

@Composable
fun DashboardScreen(prefs: Prefs, onBack: () -> Unit, today: LocalDate = LocalDate.now()) {
    val stats = remember { prefs.loadStats() }
    val byTc = remember { prefs.errorByCount() }
    val byWeek = remember { prefs.errorByWeek() }
    val context = LocalContext.current
    ScreenScaffold("Stats dashboard", onBack) {
        V6Col {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigStat("Strategy", pctOnly(stats.correctDecisions, stats.decisions), Modifier.weight(1f))
                BigStat("Count checks", pctOnly(stats.countCorrect, stats.countQuizzes), Modifier.weight(1f))
                BigStat("Hands", "%,d".format(stats.handsPlayed), Modifier.weight(1f))
            }
            Panel {
                Head("Accuracy by true count")
                Note("Plays at the table, grouped by the true count when you made them. Low accuracy at high counts usually means missed count plays.")
                Spacer(Modifier.height(8.dp))
                TcBars(byTc)
            }
            Panel {
                Head("Over the last 8 weeks")
                Spacer(Modifier.height(6.dp))
                val series = remember { TcTrend.series(byWeek, today) }
                TrendChart(series, Modifier.fillMaxWidth().height(170.dp))
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TcTrend.GROUPS.forEachIndexed { i, (name, _) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(SeriesColors[i]))
                            Text(" $name", fontSize = 12.sp)
                        }
                    }
                }
                Note("Each point is one week's accuracy for plays at that true count. Gaps are weeks with no plays there.")
            }
            Panel {
                Head("Share your progress")
                Note("Makes a picture of your stats to send to a friend or post. It has no personal details.")
                Spacer(Modifier.height(8.dp))
                val card = remember { renderShareCard(prefs) }
                Image(card.asImageBitmap(), "Progress card", Modifier.fillMaxWidth().aspectRatio(card.width / card.height.toFloat()).clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(8.dp))
                GoldButton("Share", Modifier.fillMaxWidth()) {
                    prefs.updateStats { it.copy(shares = it.shares + 1) }
                    shareProgress(context, prefs)
                }
            }
        }
    }
}

private fun pctOnly(ok: Int, n: Int) = if (n == 0) "n/a" else "${ok * 100 / n}%"

@Composable
private fun BigStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Ink.copy(alpha = 0.55f)).border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(14.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Cream)
        Text(label, fontSize = 12.sp, color = Gold)
    }
}

@Composable
private fun TcBars(map: Map<String, ErrorMap.Cell>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        ErrorMap.TC_BUCKETS.forEachIndexed { i, label ->
            val c = map[i.toString()]
            val acc = c?.let { 1 - it.rate }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(acc?.let { "${(it * 100).toInt()}%" } ?: "", fontSize = 10.sp, maxLines = 1)
                        Box(
                            Modifier.fillMaxWidth().height((96.dp * (acc?.toFloat() ?: 0f)).coerceAtLeast(2.dp)).clip(RoundedCornerShape(3.dp))
                                .background(if (acc == null) Cream.copy(alpha = 0.15f) else if (acc >= 0.95) GoodGreen else if (acc >= 0.85) Gold else BadRed),
                        )
                    }
                }
                Text(label.replace(" or less", "").replace(" or more", "+"), fontSize = 10.sp, color = Cream.copy(alpha = 0.7f), maxLines = 1)
                Text(c?.n?.toString() ?: "0", fontSize = 9.sp, color = Cream.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun TrendChart(series: List<List<Double?>>, modifier: Modifier) {
    Canvas(modifier) {
        val left = 34.dp.toPx(); val bottom = size.height - 16.dp.toPx(); val top = 6.dp.toPx()
        val w = size.width - left
        val paint = Paint().apply { color = Cream.copy(alpha = 0.6f).toArgb(); textSize = 10.dp.toPx(); isAntiAlias = true }
        for (p in listOf(50, 75, 100)) {
            val y = bottom - (p - 50) / 50f * (bottom - top)
            drawLine(Cream.copy(alpha = 0.12f), Offset(left, y), Offset(size.width, y), 1f)
            drawContext.canvas.nativeCanvas.drawText("$p%", 0f, y + 4.dp.toPx(), paint)
        }
        val n = series.firstOrNull()?.size ?: 0
        if (n < 2) return@Canvas
        val step = w / (n - 1)
        drawContext.canvas.nativeCanvas.drawText("8 weeks ago", left, size.height, paint)
        paint.textAlign = Paint.Align.RIGHT
        drawContext.canvas.nativeCanvas.drawText("This week", size.width, size.height, paint)
        series.forEachIndexed { si, pts ->
            val col = SeriesColors[si]
            var path: Path? = null
            pts.forEachIndexed { i, v ->
                if (v == null) { path?.let { drawPath(it, col, style = Stroke(2.5.dp.toPx())) }; path = null; return@forEachIndexed }
                val x = left + i * step
                val y = bottom - ((v.coerceIn(0.5, 1.0) - 0.5) / 0.5).toFloat() * (bottom - top)
                if (path == null) path = Path().apply { moveTo(x, y) } else path!!.lineTo(x, y)
                drawCircle(col, 3.dp.toPx(), Offset(x, y))
            }
            path?.let { drawPath(it, col, style = Stroke(2.5.dp.toPx())) }
        }
    }
}

/** A picture of your stats for sharing. Nothing personal goes on it. */
fun renderShareCard(prefs: Prefs): Bitmap {
    val s = prefs.loadStats()
    val ctx = prefs.badgeContext()
    val d = prefs.daily()
    val level = skillLevel(s)
    val w = 1080; val h = 1080
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val bg = Paint().apply { shader = android.graphics.RadialGradient(w / 2f, h * 0.4f, w * 0.8f, 0xFF1E6B45.toInt(), 0xFF0B2F1F.toInt(), android.graphics.Shader.TileMode.CLAMP) }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
    val gold = 0xFFE8C26A.toInt(); val cream = 0xFFF4EEDF.toInt()
    val border = Paint().apply { color = gold; style = Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true }
    c.drawRoundRect(30f, 30f, w - 30f, h - 30f, 40f, 40f, border)
    fun txt(t: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, center: Boolean = false) {
        val p = Paint().apply {
            this.color = color; textSize = size; isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        }
        c.drawText(t, x, y, p)
    }
    txt("Hi-Lo Trainer", w / 2f, 150f, 84f, gold, bold = true, center = true)
    txt("BLACKJACK  ·  CARD COUNTING", w / 2f, 210f, 34f, cream, center = true)
    txt(level.name, w / 2f, 330f, 70f, cream, bold = true, center = true)
    val rows = listOf(
        "Strategy accuracy" to pctOnly(s.correctDecisions, s.decisions),
        "Count check accuracy" to pctOnly(s.countCorrect, s.countQuizzes),
        "Hands played" to "%,d".format(s.handsPlayed),
        "Best run of right plays" to "${s.bestStrategyStreak}",
        "Daily goal streak" to "${d.streak} day${if (d.streak == 1) "" else "s"}",
        "Badges" to "${Badges.earned(ctx).size} of ${Badges.ALL.size}",
    )
    rows.forEachIndexed { i, (k, v) ->
        val y = 450f + i * 92f
        txt(k, 110f, y, 44f, cream)
        val p = Paint().apply { color = gold; textSize = 48f; isAntiAlias = true; textAlign = Paint.Align.RIGHT; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        c.drawText(v, w - 110f, y, p)
        c.drawLine(110f, y + 26f, w - 110f, y + 26f, Paint().apply { color = 0x33F4EEDF })
    }
    txt("Practice made on a Hi-Lo Trainer table", w / 2f, h - 80f, 30f, 0xAAF4EEDF.toInt(), center = true)
    return bmp
}

fun shareProgress(context: Context, prefs: Prefs) {
    val bmp = renderShareCard(prefs)
    val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
    val f = java.io.File(dir, "hilo-progress.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "My Hi-Lo Trainer progress")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share progress").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---------------------------------------------------------------------------
// Badges and personal bests
// ---------------------------------------------------------------------------

@Composable
fun BadgesScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = remember { prefs.badgeContext() }
    val earned = remember { Badges.earned(ctx).map { it.id }.toSet() }
    val bests = remember { personalBests(ctx, prefs.history()) }
    ScreenScaffold("Badges and bests", onBack) {
        V6Col {
            Panel {
                Head("Badges: ${earned.size} of ${Badges.ALL.size}")
                Note("Earned badges are gold. The rest show what to do next.")
            }
            Badges.ALL.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { b -> BadgeTile(b, b.id in earned, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Panel {
                Head("Personal bests")
                bests.forEach { b ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(b.label, Modifier.weight(1f), fontSize = 14.sp)
                        Text(b.value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Gold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(b: Badge, got: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.heightIn(min = 66.dp).clip(shape).background(if (got) Gold.copy(alpha = 0.16f) else Ink.copy(alpha = 0.45f))
            .border(1.dp, if (got) Gold else Cream.copy(alpha = 0.2f), shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (got) Gold else Cream.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            SuitIcon(Suit.entries[(b.id.hashCode() and 3)], 18.dp, color = if (got) Ink else Cream.copy(alpha = 0.4f))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(b.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (got) Gold else Cream.copy(alpha = 0.75f))
            Text(b.how, fontSize = 11.sp, color = Cream.copy(alpha = if (got) 0.8f else 0.55f))
        }
    }
}

// ---------------------------------------------------------------------------
// Weekly goal and calendar
// ---------------------------------------------------------------------------

@Composable
fun WeeklyScreen(prefs: Prefs, onBack: () -> Unit, today: LocalDate = LocalDate.now()) {
    var s by remember { mutableStateOf(prefs.loadSettings()) }
    val hist = remember { prefs.history() }
    val week = Weekly.week(hist, Weekly.weekStart(today), s.weeklyDays)
    val streak = Weekly.weekStreak(hist, today, s.weeklyDays)
    ScreenScaffold("Weekly goal", onBack) {
        V6Col {
            Panel {
                Head("This week")
                Text("${week.practiceDays} of ${s.weeklyDays} practice days", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (week.met) GoodGreen else Cream)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (i in 0 until s.weeklyDays) {
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(if (i < week.practiceDays) Gold else Cream.copy(alpha = 0.15f)))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("${week.points} practice points this week. Week streak: $streak week${if (streak == 1) "" else "s"}.", fontSize = 14.sp)
                Note("A practice day is any day you earn ${Weekly.PRACTICE_POINTS} or more points.")
            }
            Panel {
                ChipGroup("Practice days a week", (3..7).map { "$it" to it }, s.weeklyDays) { n -> s = s.copy(weeklyDays = n); prefs.saveSettings(s) }
            }
            Panel {
                Head("Practice calendar")
                Spacer(Modifier.height(6.dp))
                Row {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = Gold) }
                }
                Weekly.calendar(hist, today).forEach { row ->
                    Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { (day, pts) ->
                            val future = day.isAfter(today)
                            val col = when {
                                future -> Color.Transparent
                                pts >= DAILY_GOAL -> Gold
                                pts >= Weekly.PRACTICE_POINTS -> Gold.copy(alpha = 0.45f)
                                pts > 0 -> Gold.copy(alpha = 0.18f)
                                else -> Cream.copy(alpha = 0.06f)
                            }
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(col)
                                    .border(if (day == today) 2.dp else 0.dp, if (day == today) Cream else Color.Transparent, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!future) Text("${day.dayOfMonth}", fontSize = 12.sp, color = if (pts >= DAILY_GOAL) Ink else Cream.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Note("Full gold: daily goal met (${DAILY_GOAL} points). Light gold: a practice day. Faint: some practice.")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Backup
// ---------------------------------------------------------------------------

@Composable
fun BackupPanel(prefs: Prefs, onRestored: () -> Unit) {
    val context = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            msg = try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(prefs.exportJson().toByteArray()) }
                "Backup saved. Copy the file to your new phone or to cloud storage."
            } catch (e: Exception) { "Couldn't save the backup: ${e.message}" }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = try { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } catch (_: Exception) { null }
            msg = if (text != null && prefs.importJson(text)) { onRestored(); "Restored. Your stats, settings and history are back." }
            else "That file isn't a Hi-Lo Trainer backup."
        }
    }
    Panel {
        Text("Backup and new phone", fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = TitleFont, color = Gold)
        Text(
            "Save everything (stats, streaks, history, settings, career) to one file. Put it in your cloud storage, then restore it on your new phone after installing the app.",
            fontSize = 13.sp, color = Cream.copy(alpha = 0.75f),
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GoldButton("Save backup", Modifier.weight(1f)) { save.launch("hilo-trainer-backup-${LocalDate.now()}.json") }
            GoldButton("Restore", Modifier.weight(1f), primary = false) { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
        }
        Text(
            "Android's Auto Backup is also on for this app, which copies app data to your Google account. Since the app isn't from the Play Store, use a saved file rather than counting on an automatic restore.",
            fontSize = 12.sp, color = Cream.copy(alpha = 0.6f), modifier = Modifier.padding(top = 6.dp),
        )
        msg?.let { Text(it, fontSize = 13.sp, color = if (it.startsWith("Couldn't") || it.startsWith("That")) BadRed else GoodGreen) }
    }
}

// ---------------------------------------------------------------------------
// Shared question view
// ---------------------------------------------------------------------------

@Composable
private fun QuestionView(q: Course.Question, picked: Int?, compact: Boolean = false, onPick: (Int) -> Unit) {
    if (q.cards.isNotEmpty() || q.dealer != null) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x33000000)).padding(12.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                q.dealer?.let { Text("DEALER", fontSize = 11.sp, letterSpacing = 2.sp, color = Gold); PlayingCard(it, if (compact) 56.dp else 70.dp); Spacer(Modifier.height(8.dp)) }
                if (q.cards.isNotEmpty()) CardRow(q.cards, if (q.cards.size > 3) 50.dp else if (compact) 62.dp else 76.dp)
            }
        }
    }
    Text(q.prompt, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        q.options.forEachIndexed { k, opt ->
            val col = when { picked == null -> Gold; k == q.answer -> GoodGreen; k == picked -> BadRed; else -> Gold.copy(alpha = 0.25f) }
            Box(
                Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (picked != null && (k == q.answer || k == picked)) col.copy(alpha = 0.22f) else Ink.copy(alpha = 0.55f))
                    .border(1.dp, col.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .clickable(enabled = picked == null) { onPick(k) }.padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(opt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ---------------------------------------------------------------------------
// 1-minute drill (also what the home screen widget opens)
// ---------------------------------------------------------------------------

@Composable
fun QuickDrillScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}, autoStart: Boolean = false) {
    val settings = remember { prefs.loadSettings() }
    fun newQ() = Course.question(listOf(0, 1, 2, 3, 4, 5).random(), Random.Default, settings.rules(), settings.system, settings.ramp)
    var running by remember { mutableStateOf(autoStart) }
    var done by remember { mutableStateOf(false) }
    var left by remember { mutableLongStateOf(60_000L) }
    var q by remember { mutableStateOf(newQ()) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var right by remember { mutableIntStateOf(0) }
    var asked by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(prefs.loadStats().quickBest) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val end = System.currentTimeMillis() + left
        while (left > 0) { delay(200); left = (end - System.currentTimeMillis()).coerceAtLeast(0) }
        running = false; done = true
        prefs.updateStats { it.copy(quickDrills = it.quickDrills + 1, quickBest = maxOf(it.quickBest, right)) }
        best = maxOf(best, right)
    }
    LaunchedEffect(picked) {
        if (picked != null && running) { delay(650); q = newQ(); picked = null }
    }
    ScreenScaffold("1-minute drill", onBack) {
        V6Col {
            when {
                done -> Panel {
                    Head("Time! $right right out of $asked")
                    Text(if (right >= best && right > 0) "A new best." else "Your best: $best", fontSize = 15.sp, color = if (right >= best && right > 0) GoodGreen else Cream)
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Go again", Modifier.fillMaxWidth()) { right = 0; asked = 0; left = 60_000L; q = newQ(); picked = null; done = false; running = true }
                }
                !running -> Panel {
                    Head("One minute, mixed questions")
                    Note("Card values, basic strategy, running and true counts. Answer as many as you can in 60 seconds. Best so far: $best right.")
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Start", Modifier.fillMaxWidth().height(56.dp)) { running = true }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${(left + 999) / 1000}s", fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = if (left < 10_000) BadRed else Gold)
                        Spacer(Modifier.weight(1f))
                        Text("$right right", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Cream.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth(left / 60_000f).height(6.dp).background(Gold))
                    }
                    QuestionView(q, picked, compact = true) { k ->
                        picked = k; asked++
                        val ok = k == q.answer
                        if (ok) right++
                        fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
                        prefs.addPoints(1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Haptic count drill
// ---------------------------------------------------------------------------

private fun vibrate(context: Context, pattern: LongArray) {
    try {
        val v = if (android.os.Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        v.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
    } catch (_: Exception) { }
}

@Composable
fun HapticDrillScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}) {
    val context = LocalContext.current
    var length by remember { mutableIntStateOf(10) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var index by remember { mutableIntStateOf(-1) }
    var answer by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val hiLo = com.hilotrainer.engine.CountSystem.HI_LO
    LaunchedEffect(cards) {
        if (cards.isEmpty()) return@LaunchedEffect
        delay(1500)
        for (i in cards.indices) {
            index = i
            vibrate(context, HapticCode.pattern(hiLo.tag(cards[i])))
            delay(1100)
        }
        index = cards.size
    }
    ScreenScaffold("Haptic count drill", onBack) {
        V6Col {
            val playing = cards.isNotEmpty() && index < cards.size
            when {
                playing -> Box(
                    Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Eyes off the screen", fontFamily = TitleFont, fontSize = 22.sp, color = Cream.copy(alpha = 0.35f))
                        Text(if (index < 0) "Get ready..." else "Card ${index + 1} of ${cards.size}", fontSize = 14.sp, color = Cream.copy(alpha = 0.25f))
                    }
                }
                cards.isNotEmpty() && result == null -> Panel {
                    Head("What's the Hi-Lo running count?")
                    NumberStepper(answer, { answer = it }, Modifier.fillMaxWidth(), range = -20..20)
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Check", Modifier.fillMaxWidth()) {
                        val rc = cards.sumOf { hiLo.tag(it) }
                        val ok = answer == rc
                        result = ok
                        fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
                        prefs.addPoints(2)
                        prefs.updateStats { it.copy(hapticDrills = it.hapticDrills + 1, hapticCorrect = it.hapticCorrect + if (ok) 1 else 0) }
                    }
                }
                else -> {
                    result?.let { ok ->
                        val rc = cards.sumOf { hiLo.tag(it) }
                        Panel {
                            Text(if (ok) "Right: ${signed(rc)}" else "It was ${signed(rc)}. You said ${signed(answer)}.", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (ok) GoodGreen else BadRed)
                            Spacer(Modifier.height(6.dp))
                            CardRow(cards, 40.dp)
                        }
                    }
                    Panel {
                        Head("Count by feel")
                        Note("Your phone buzzes once for each card. Keep the count without looking, then type it in. Good for building a count that doesn't depend on your eyes.")
                        Spacer(Modifier.height(6.dp))
                        listOf(1, 0, -1).forEach { t ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(HapticCode.describe(t), Modifier.weight(1f), fontSize = 14.sp)
                                GoldButton("Feel it", Modifier.width(100.dp).height(40.dp), primary = false) { vibrate(context, HapticCode.pattern(t)) }
                            }
                        }
                        ChipGroup("Cards", listOf("10" to 10, "15" to 15, "20" to 20), length) { length = it }
                        Spacer(Modifier.height(6.dp))
                        GoldButton(if (result == null) "Start" else "Go again", Modifier.fillMaxWidth().height(56.dp)) {
                            val shoe = com.hilotrainer.engine.Shoe(1)
                            answer = 0; result = null; index = -1
                            cards = List(length) { shoe.draw() }
                        }
                        Note("Needs vibration turned on for your phone. Hi-Lo values are used whatever count system you picked.")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Deck estimation drill
// ---------------------------------------------------------------------------

@Composable
fun DeckEstimateScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}, seed: Int? = null) {
    var expert by remember { mutableStateOf(false) }
    val rnd = remember { seed?.let { Random(it) } ?: Random.Default }
    var q by remember { mutableStateOf(DeckEstimate.make(rnd, false)) }
    var shown by remember { mutableStateOf(true) }
    var guess by remember { mutableStateOf<Double?>(null) }
    var right by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    LaunchedEffect(q) { shown = true; if (seed == null) { delay(3000); if (guess == null) shown = false } }
    ScreenScaffold("Deck estimation", onBack) {
        V6Col {
            Panel {
                Text(
                    if (q.view == DeckEstimate.View.TRAY) "${q.decks}-deck game. How many decks are in the discard tray?"
                    else "${q.decks}-deck shoe, seen from the side. How many decks are left in it?",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                Note("You get 3 seconds to look, like a glance at the table. ${if (expert) "Answer to the nearest quarter deck." else "Answer to the nearest half deck."}")
            }
            Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x33000000)), contentAlignment = Alignment.Center) {
                if (shown || guess != null) {
                    if (q.view == DeckEstimate.View.TRAY) EstimateTray(q.cardsInView, q.decks) else EstimateShoe(q.cardsInView, q.decks)
                } else Text("Covered. What did you see?", color = Cream.copy(alpha = 0.5f))
            }
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                q.choices().forEach { c ->
                    val isAns = guess != null && q.isRight(c) && kotlin.math.abs(c - q.answer) < 1e-9
                    FilterChip(
                        selected = guess == c || isAns,
                        onClick = {
                            if (guess == null) {
                                guess = c; total++
                                val ok = q.isRight(c)
                                if (ok) right++
                                fx(if (ok) Fx.CORRECT else Fx.MISTAKE)
                                prefs.addPoints(1)
                                prefs.updateStats { it.copy(deckEstimates = it.deckEstimates + 1, deckEstimatesOk = it.deckEstimatesOk + if (ok) 1 else 0) }
                            }
                        },
                        label = { Text(fmtDecks(c)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (guess != null && q.isRight(guess!!) || isAns) GoodGreen.copy(alpha = 0.5f) else BadRed.copy(alpha = 0.5f),
                            labelColor = Cream, selectedLabelColor = Cream,
                        ),
                    )
                }
            }
            guess?.let { g ->
                Panel {
                    val ok = q.isRight(g)
                    Text(if (ok) "Right: %.2f decks".format(q.decksInView) else "It was %.2f decks (${q.cardsInView} cards). You said ${fmtDecks(g)}.".format(q.decksInView), fontWeight = FontWeight.Bold, color = if (ok) GoodGreen else BadRed)
                    Text("Score: $right of $total", fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    GoldButton("Next", Modifier.fillMaxWidth()) { q = DeckEstimate.make(rnd, expert); guess = null }
                }
            }
            Panel {
                ChipGroup("Precision", listOf("Half decks" to false, "Quarter decks" to true), expert) { expert = it; q = DeckEstimate.make(rnd, it); guess = null }
                Note("Harder than the discard tray drill: no scale marks, mixed shoe sizes, a shoe seen from the side, and only a glance.")
            }
        }
    }
}

private fun fmtDecks(d: Double): String = (if (d == d.toInt().toDouble()) "${d.toInt()}" else if ((d * 2) == (d * 2).toInt().toDouble()) "%.1f".format(d) else "%.2f".format(d))

@Composable
private fun EstimateTray(cards: Int, decks: Int) {
    // Same physical tray for 6 and 8 decks, so the scale changes with the game.
    val trayH = 200.dp
    Box(Modifier.size(90.dp, trayH).clip(RoundedCornerShape(6.dp)).background(Color(0x66000000)).border(2.dp, Gold.copy(alpha = 0.4f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().height(trayH * (cards / (8 * 52f)).coerceAtMost(1f) * (8f / decks).coerceAtMost(1.33f).let { if (decks == 8) 1f else it }).padding(horizontal = 7.dp)) {
            drawRect(Brush.horizontalGradient(listOf(Color(0xFFBFB6A2), Color(0xFFF4EEDF), Color(0xFFBFB6A2))))
            var y = size.height
            while (y > 0) { drawLine(Color(0x22000000), Offset(0f, y), Offset(size.width, y), 1f); y -= 2.2f }
        }
    }
}

@Composable
private fun EstimateShoe(cards: Int, decks: Int) {
    Canvas(Modifier.size(300.dp, 110.dp)) {
        val pad = 10.dp.toPx()
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2B2B2B), Color(0xFF0E0E0E))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
        val w = size.width - pad * 2
        val stackW = w * cards / (decks * 52f)
        val x0 = pad + w - stackW
        drawRect(Brush.verticalGradient(listOf(Color(0xFFF4EEDF), Color(0xFFBFB6A2))), Offset(x0, pad * 2), Size(stackW, size.height - pad * 3))
        var x = x0
        while (x < pad + w) { drawLine(Color(0x22000000), Offset(x, pad * 2), Offset(x, size.height - pad), 1f); x += 1.6f }
    }
}

// ---------------------------------------------------------------------------
// Kelly bet sizing
// ---------------------------------------------------------------------------

@Composable
fun KellyScreen(prefs: Prefs, onBack: () -> Unit) {
    var s by remember { mutableStateOf(prefs.loadSettings()) }
    var bankroll by remember { mutableIntStateOf(5000) }
    ScreenScaffold("Kelly bet sizing", onBack) {
        V6Col {
            Panel {
                Head("Bet in proportion to your edge")
                Text("Kelly bet = edge / variance x bankroll. With blackjack's variance of about ${Kelly.VARIANCE}, a 1% edge on a \$10,000 bankroll is about \$77.", fontSize = 14.sp)
                Note("Edge here uses the rough Hi-Lo rule of thumb: about -0.5% at a true count of 0, plus about 0.5% for each +1. Betting a fraction of Kelly means smaller swings.")
            }
            Panel {
                ChipGroup("Bankroll", listOf("\$1,000" to 1000, "\$5,000" to 5000, "\$10,000" to 10_000, "\$25,000" to 25_000), bankroll) { bankroll = it }
                ChipGroup("Fraction", Kelly.FRACTIONS.mapIndexed { i, f -> f.second to i }, s.kellyChoice) { s = s.copy(kellyChoice = it); prefs.saveSettings(s) }
                Note("The table's Hint button also shows this fraction's bet for your current bankroll.")
            }
            Panel {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("True count", Modifier.weight(1f), fontSize = 13.sp, color = Gold)
                    Text("Edge", Modifier.weight(1f), fontSize = 13.sp, color = Gold, textAlign = TextAlign.End)
                    Text("Bet", Modifier.weight(1f), fontSize = 13.sp, color = Gold, textAlign = TextAlign.End)
                    Text("Units", Modifier.weight(0.8f), fontSize = 13.sp, color = Gold, textAlign = TextAlign.End)
                }
                for (tc in 0..7) {
                    val e = Edge.atTrueCount(tc.toDouble())
                    val raw = Kelly.bet(bankroll, tc.toDouble(), s.kellyFraction)
                    val chips = if (raw <= 0) 0 else Kelly.chips(bankroll, tc.toDouble(), s.kellyFraction, 10)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(signed(tc), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("%+.1f%%".format(e * 100), Modifier.weight(1f), fontSize = 15.sp, textAlign = TextAlign.End, color = if (e > 0) GoodGreen else BadRed)
                        Text(if (chips == 0) "Minimum" else money(chips), Modifier.weight(1f), fontSize = 15.sp, textAlign = TextAlign.End)
                        Text(if (chips == 0) "1" else "%.1f".format(chips / 10.0).removeSuffix(".0"), Modifier.weight(0.8f), fontSize = 15.sp, textAlign = TextAlign.End)
                    }
                }
                Note("Bets are rounded down to \$5 chips with a \$10 table minimum. Units are \$10, as at the trainer's table.")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Career mode
// ---------------------------------------------------------------------------

@Composable
fun CareerScreen(prefs: Prefs, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    val c = remember { prefs.career }
    ScreenScaffold("Career mode", onBack) {
        V6Col {
            Panel {
                Head("Work your way up")
                Note("Each table is harder than the last. Move up by playing accurately: promotion depends on your plays and count checks, not on winning, because short sessions swing.")
            }
            CareerLevels.ALL.forEachIndexed { i, l ->
                val unlocked = i <= c.level
                val current = i == c.level
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(if (current) Gold.copy(alpha = 0.14f) else Ink.copy(alpha = 0.5f))
                        .border(1.dp, if (current) Gold else Cream.copy(alpha = if (unlocked) 0.35f else 0.12f), shape).padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(30.dp).clip(CircleShape).background(if (i < c.level) GoodGreen else if (current) Gold else Cream.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Text(if (i < c.level) "✓" else "${i + 1}", color = if (unlocked) Ink else Cream.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (unlocked) Cream else Cream.copy(alpha = 0.5f))
                            Text(l.table, fontSize = 12.sp, color = Cream.copy(alpha = if (unlocked) 0.75f else 0.4f))
                        }
                    }
                    if (current && i < CareerLevels.ALL.size - 1) {
                        Spacer(Modifier.height(8.dp))
                        Text("To move up: ${l.needDecisions} plays at ${l.needAccuracy}% or better (${minOf(c.decisions, l.needDecisions)} so far, ${c.accuracy()}%), and ${l.needCounts} count checks at ${l.needCountAccuracy}% (${minOf(c.counts, l.needCounts)} so far, ${c.countAccuracy()}%).", fontSize = 13.sp)
                    }
                    if (unlocked) {
                        Spacer(Modifier.height(8.dp))
                        GoldButton(if (current) "Play this table" else "Replay", Modifier.fillMaxWidth().height(44.dp), primary = current) { onPlay(i) }
                    } else Text("Locked: pass the table before it.", fontSize = 12.sp, color = Cream.copy(alpha = 0.45f), modifier = Modifier.padding(top = 6.dp))
                }
            }
            Note("Career tables use their own speed, players and training settings. Your table rules and count system come from Settings.")
        }
    }
}

// ---------------------------------------------------------------------------
// Daily challenge
// ---------------------------------------------------------------------------

@Composable
fun DailyChallengeScreen(prefs: Prefs, onBack: () -> Unit, fx: (Fx) -> Unit = {}, today: LocalDate = LocalDate.now(), startStage: Int = 0) {
    val settings = remember { prefs.loadSettings() }
    val day = today.toEpochDay()
    val cards = remember(day) { DailyChallenge.cards(day) }
    val actual = remember(day) { DailyChallenge.runningCount(day, settings.system) }
    val questions = remember(day) { DailyChallenge.questions(day, settings.rules(), settings.system, settings.ramp) }
    // 0 intro, 1 counting, 2 count answer, 3 strategy, 4 result
    var stage by remember { mutableIntStateOf(startStage) }
    var shown by remember { mutableIntStateOf(0) }
    var countAns by remember { mutableIntStateOf(settings.system.initialCount(6)) }
    var qi by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<Int?>(null) }
    var right by remember { mutableIntStateOf(0) }
    var scores by remember { mutableStateOf(prefs.dailyScores()) }
    LaunchedEffect(stage) {
        if (stage != 1) return@LaunchedEffect
        shown = 0
        delay(800)
        for (i in cards.indices) { shown = i + 1; fx(Fx.DEAL); delay(DailyChallenge.CARD_MS) }
        delay(400)
        stage = 2
    }
    ScreenScaffold("Daily challenge", onBack) {
        V6Col {
            when (stage) {
                0 -> {
                    Panel {
                        Head("Today's shoe: ${today}")
                        Note("Everyone with the app gets the same ${DailyChallenge.COUNT_CARDS} cards and ${DailyChallenge.STRATEGY_HANDS} hands today. Count the cards as they flash, then play the hands. Replay as often as you like: your best score counts.")
                        Spacer(Modifier.height(6.dp))
                        Text("Best today: ${scores[day]?.let { "$it of ${DailyChallenge.MAX_SCORE}" } ?: "not played yet"}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        GoldButton("Start today's challenge", Modifier.fillMaxWidth().height(56.dp)) { stage = 1; right = 0; qi = 0; picked = null; countAns = settings.system.initialCount(6) }
                    }
                    Panel {
                        Head("Last 7 days")
                        for (k in 6 downTo 0) {
                            val d = today.minusDays(k.toLong())
                            val sc = scores[d.toEpochDay()]
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(d.toString(), Modifier.width(110.dp), fontSize = 13.sp)
                                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Cream.copy(alpha = 0.1f))) {
                                    Box(Modifier.fillMaxWidth((sc ?: 0) / 100f).height(10.dp).background(Gold))
                                }
                                Text(sc?.toString() ?: "-", Modifier.width(40.dp), textAlign = TextAlign.End, fontSize = 13.sp)
                            }
                        }
                    }
                }
                1 -> Box(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x33000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val c = cards.getOrNull(shown - 1)
                        if (c != null) PlayingCard(c, 120.dp) else Text("Get ready", fontFamily = TitleFont, fontSize = 24.sp, color = Gold)
                        Spacer(Modifier.height(10.dp))
                        Text("Card ${shown.coerceAtLeast(0)} of ${cards.size}", fontSize = 13.sp, color = Cream.copy(alpha = 0.6f))
                    }
                }
                2 -> Panel {
                    Head("What's the ${settings.system.label} running count?")
                    NumberStepper(countAns, { countAns = it }, Modifier.fillMaxWidth(), range = -40..40)
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Lock it in", Modifier.fillMaxWidth()) { fx(if (countAns == actual) Fx.CORRECT else Fx.MISTAKE); stage = 3 }
                }
                3 -> {
                    val q = questions[qi]
                    Text("Hand ${qi + 1} of ${questions.size}", color = Cream.copy(alpha = 0.7f))
                    QuestionView(q, picked) { k ->
                        picked = k
                        if (k == q.answer) right++
                        fx(if (k == q.answer) Fx.CORRECT else Fx.MISTAKE)
                    }
                    if (picked != null) GoldButton(if (qi == questions.size - 1) "See my score" else "Next hand", Modifier.fillMaxWidth()) {
                        if (qi == questions.size - 1) {
                            val sc = DailyChallenge.score(countAns, actual, right)
                            prefs.saveDailyScore(day, sc)
                            prefs.addPoints(5)
                            prefs.updateStats { it.copy(dailyChallenges = it.dailyChallenges + 1) }
                            scores = prefs.dailyScores()
                            stage = 4
                        } else { qi++; picked = null }
                    }
                }
                else -> Panel {
                    val sc = DailyChallenge.score(countAns, actual, right)
                    Head("Score: $sc of ${DailyChallenge.MAX_SCORE}")
                    Text("Count: you said ${signed(countAns)}, it was ${signed(actual)}. Hands: $right of ${questions.size} right.", fontSize = 15.sp)
                    Text("Best today: ${scores[day] ?: sc}", fontSize = 14.sp, color = Gold)
                    Spacer(Modifier.height(8.dp))
                    GoldButton("Play again", Modifier.fillMaxWidth()) { stage = 0 }
                }
            }
        }
    }
}
