package com.hilotrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private class Step(val title: String, val body: String, val color: Color)

private val Steps = listOf(
    Step("What am I playing with?", "Two things. MONEY ($) is what you spend in the shop and what you lose when a hand loses. POINTS are what you score by winning hands. You need enough points to beat each blind before your hands run out.", Color(0xFFFF8A8A)),
    Step("The goal", "Every blind has a score to reach. Reach it before you run out of hands. Clear a Small Blind, a Big Blind and a Boss Blind to finish a round. Finish 6 rounds to win the run.", Color(0xFFFFD35A)),
    Step("One hand is one hand of blackjack", "Play it like normal: Hit, Stand, Double or Split. Win the hand and it scores points. Lose it and you score nothing and pay your bet in money. A tie costs nothing.", Color(0xFF3DDC84)),
    Step("Score = Chips x Mult", "Chips (blue) are the base points. Mult (red) multiplies them. A win worth 100 chips with 3 mult scores 300. Perks make either number bigger.", Color(0xFF5ED0FF)),
    Step("Bet size", "You pick Stake 1, 2 or 3 before each hand. Stake 2 means a win scores double the points, and a loss costs $2. Winning does not pay you money. It scores points. Only losses take money.", Color(0xFFFFB84D)),
    Step("Perks", "Perks are little powers that make your wins score more or earn you money. You can hold 5. Tap one to read it, sell it or move it. Order matters for Copycat.", Color(0xFFD59CFF)),
    Step("Special wins score more", "A dealer bust, a 21, a double down, a split, five cards or a blackjack each start with more chips and mult than a plain win. Train a kind in the shop to make it stronger.", Color(0xFFFF8A8A)),
    Step("Money and the shop", "You earn money for clearing blinds, finishing bonus goals and from some perks. Spend it in the shop on perks, hand training and Perk Packs. Keep some cash: you earn $1 interest for every $5 you hold.", Color(0xFFFFD35A)),
    Step("Bonus goal", "Each blind has a side goal, like winning a hand you doubled. Finish it for $4 on the spot.", Color(0xFF3DDC84)),
    Step("Skipping", "You can skip a Small or Big Blind for the reward shown on the button. Skipping means you do not play for that blind's money, so use it when the reward is better.", Color(0xFFFFB84D)),
    Step("The count", "The Spotter perk shows the Hi-Lo count. A high count means lots of 10s and Aces are left, which is good for you. Some perks pay extra when the count is high or low.", Color(0xFF5ED0FF)),
)

/** A plain-words guide to how Rogue Run scores, so nothing in it needs a manual. */
@Composable
fun RunGuideSheet(onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xF2080612)).clickable { onClose() }, contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 460.dp).padding(14.dp).chunky(Brush.verticalGradient(listOf(Color(0xFF2A2650), Color(0xFF16122B))), edge = Color(0xFFFFD35A), radius = 20.dp, lift = 5.dp, border = 3.dp)
                .clickable(enabled = false) {}.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("HOW ROGUE RUN WORKS", fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Gold)
            Text("Scroll for all ${Steps.size} steps", fontSize = 11.sp, color = Cream.copy(alpha = 0.6f))
            Column(Modifier.heightIn(max = 520.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Steps.forEachIndexed { i, st ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}", fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.chunky(st.color, radius = 8.dp, lift = 2.dp, border = 1.5.dp).padding(horizontal = 8.dp, vertical = 2.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(st.title, fontSize = 15.sp, fontWeight = FontWeight.Black, color = st.color)
                            Text(st.body, fontSize = 13.sp, lineHeight = 17.sp, color = Cream.copy(alpha = 0.92f))
                        }
                    }
                }
            }
            ChunkyButton("Got it", Color(0xFF2FA866), Modifier.fillMaxWidth()) { onClose() }
        }
    }
}
