package com.hilotrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hilotrainer.engine.Outcome
import com.hilotrainer.engine.Run

/** After every hand, one plain sentence about what just happened: points won, money lost, or nothing. */
@Composable
fun HandResultBanner(r: Run, modifier: Modifier = Modifier) {
    val evs = r.lastScore
    if (evs.isEmpty()) return
    val pts = evs.sumOf { it.total }
    val lostMoney = -evs.filter { it.outcome == Outcome.LOSE || it.outcome == Outcome.BUST }.sumOf { minOf(it.money, 0) }
    val lost = evs.any { it.outcome == Outcome.LOSE || it.outcome == Outcome.BUST }
    val (title, sub, color) = when {
        pts > 0 -> Triple("YOU WON  +%,d points".format(pts), if (evs.size == 1) evs[0].let { "%,d chips x %s".format(it.chips, if (it.mult == it.mult.toInt().toDouble()) "${it.mult.toInt()}" else "%.1f".format(it.mult)) } else "${evs.size} hands won", Color(0xFF1F9A5A))
        lost -> Triple("YOU LOST  -\$$lostMoney", "No points this hand. Your bet money is gone.", Color(0xFFC0392B))
        else -> Triple("PUSH", "A tie. No points, no money lost.", Color(0xFF6A6A7A))
    }
    Column(modifier.fillMaxWidth().chunky(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.75f))), radius = 12.dp, lift = 3.dp).padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Text(sub + if (r.handsLeft > 0 && !r.cleared) "   ·   ${r.handsLeft} ${if (r.handsLeft == 1) "hand" else "hands"} left" else "", fontSize = 12.sp, color = Color.White.copy(alpha = 0.92f), maxLines = 1)
    }
}
