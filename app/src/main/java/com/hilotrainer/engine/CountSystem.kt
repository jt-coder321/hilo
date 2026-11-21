package com.hilotrainer.engine

import kotlin.math.floor

/**
 * Card counting systems the trainer supports.
 *
 * Tags (2,3,4,5,6,7,8,9,10,A), checked against two published tables:
 * Hi-Lo  +1 +1 +1 +1 +1  0  0  0 -1 -1  (balanced, level 1)
 * KO     +1 +1 +1 +1 +1 +1  0  0 -1 -1  (unbalanced, level 1)
 * Zen    +1 +1 +2 +2 +2 +1  0  0 -2 -1  (balanced, level 2)
 *
 * KO starts at an initial running count of 4 - 4 x decks (Knock-Out Blackjack),
 * so a 6-deck shoe starts at -20 and an 8-deck shoe at -28.
 */
enum class CountSystem(val label: String, val balanced: Boolean) {
    HI_LO("Hi-Lo", true),
    KO("KO", false),
    ZEN("Zen", true);

    fun tag(rank: Rank): Int = when (this) {
        HI_LO -> rank.hiLo
        KO -> when (rank.value) { in 2..7 -> 1; 8, 9 -> 0; else -> -1 }
        ZEN -> when (rank.value) {
            2, 3, 7 -> 1
            4, 5, 6 -> 2
            8, 9 -> 0
            10 -> -2
            else -> -1 // ace
        }
    }

    fun tag(card: Card): Int = tag(card.rank)

    /** Count at the start of a fresh shoe. */
    fun initialCount(decks: Int): Int = if (this == KO) 4 - 4 * decks else 0

    /** Sum of the tags in one full deck: 0 for balanced systems, +4 for KO. */
    val deckSum: Int get() = Rank.entries.sumOf { tag(it) } * 4

    /** True count for balanced systems. KO players use the running count directly. */
    fun trueCount(running: Int, decksRemaining: Double): Double =
        if (decksRemaining <= 0) 0.0 else running / decksRemaining

    /**
     * The count the bet ramp is keyed on, on the Hi-Lo true count scale.
     *
     * Hi-Lo: the true count.
     * Zen: half the Zen true count, since Zen tags run about twice as large as Hi-Lo.
     * KO: (running - 4) / decks left + 4. With the standard starting count this is
     * the count per deck of the cards already seen, so a running count of +4 means
     * about the same at any depth. This is the trainer's own conversion.
     */
    fun betLevelExact(running: Int, decksRemaining: Double): Double {
        if (decksRemaining <= 0) return 0.0
        return when (this) {
            HI_LO -> running / decksRemaining
            ZEN -> running / decksRemaining / 2.0
            KO -> (running - 4) / decksRemaining + 4.0
        }
    }

    fun betLevel(running: Int, decksRemaining: Double): Int = floor(betLevelExact(running, decksRemaining)).toInt()

    /** How the count driving a bet is described to the player. */
    fun describeCount(running: Int, decksRemaining: Double): String = when (this) {
        HI_LO -> "true count ${BlackjackGame.fmt(trueCount(running, decksRemaining))}"
        ZEN -> "Zen true count ${BlackjackGame.fmt(trueCount(running, decksRemaining))}"
        KO -> "KO running count ${if (running > 0) "+$running" else "$running"}"
    }

    val valuesText: String get() = when (this) {
        HI_LO -> "2 to 6 count +1, 7 to 9 count 0, tens and aces count -1."
        KO -> "2 to 7 count +1, 8 and 9 count 0, tens and aces count -1. Start at 4 minus 4 times the decks."
        ZEN -> "2, 3 and 7 count +1. 4, 5 and 6 count +2. 8 and 9 count 0. Tens count -2, aces -1."
    }
}
