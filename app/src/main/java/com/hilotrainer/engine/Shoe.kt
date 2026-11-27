package com.hilotrainer.engine

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * A multi-deck shoe with a cut card. Tracks the Hi-Lo running count of every
 * card that has been revealed (the caller decides when a card is "seen").
 */
class Shoe(
    val decks: Int,
    /** Fraction of the shoe dealt before the cut card comes out, e.g. 0.75. */
    val penetration: Double = 0.75,
    private val random: Random = Random.Default,
    val system: CountSystem = CountSystem.HI_LO,
) {
    private val cards = ArrayList<Card>(decks * 52)
    /** Roguelike deck building: extra (or fewer) cards of each rank per deck, indexed like Rank.entries. Applies on the next shuffle. */
    val adjust = IntArray(13)
    private var next = 0

    var runningCount: Int = 0
        private set

    private val seen = ArrayList<Card>()
    /** Every card counted since the last shuffle, in the order it was seen. */
    val seenSinceShuffle: List<Card> get() = seen.toList()

    init {
        require(decks in 1..8)
        shuffle()
    }

    val totalCards: Int get() = cards.size
    val cardsDealt: Int get() = next
    val cardsRemaining: Int get() = totalCards - next
    val decksRemaining: Double get() = cardsRemaining / 52.0
    val needsShuffle: Boolean get() = next >= (totalCards * penetration).toInt()

    fun shuffle() {
        cards.clear()
        repeat(decks) {
            for (r in Rank.entries) for (k in 0 until (4 + adjust[r.ordinal]).coerceIn(0, 8)) cards.add(Card(r, Suit.entries[k % 4]))
        }
        cards.shuffle(random)
        next = 0
        runningCount = system.initialCount(decks)
        seen.clear()
    }

    /** Draws a card. If [seen] it is added to the running count right away. */
    fun draw(seen: Boolean = true): Card {
        if (next >= cards.size) shuffle()
        val c = cards[next++]
        if (seen) see(c)
        return c
    }

    /**
     * Cards that went out before you sat down. They leave the shoe without being
     * counted, so your count only covers what you've actually seen.
     */
    fun burnUnseen(n: Int) {
        next = (next + n).coerceIn(0, cards.size - 1)
    }

    /** The shoe as text (order, position, running count) so a table can be picked up again later. */
    fun snapshot(): String =
        "$decks;$next;$runningCount;" + cards.joinToString(",") { (it.suit.ordinal * 13 + it.rank.ordinal).toString() }

    /** Restores a [snapshot]. Returns false, leaving the shoe alone, if it does not fit this shoe. */
    fun restore(text: String): Boolean {
        val parts = text.split(";")
        if (parts.size != 4) return false
        val d = parts[0].toIntOrNull() ?: return false
        val n = parts[1].toIntOrNull() ?: return false
        val rc = parts[2].toIntOrNull() ?: return false
        val idx = parts[3].split(",").map { it.toIntOrNull() ?: return false }
        if (d != decks || idx.size != totalCards || n !in 0..totalCards || idx.any { it !in 0..51 }) return false
        val ranks = Rank.entries; val suits = Suit.entries
        if (idx.groupBy { it }.any { it.value.size != decks }) return false
        cards.clear()
        idx.forEach { cards.add(Card(ranks[it % 13], suits[it / 13])) }
        next = n; runningCount = rc; seen.clear()
        return true
    }

    /** Count a card that was drawn face down and has now been revealed. */
    fun see(card: Card) {
        runningCount += system.tag(card)
        seen += card
    }

    /** Exact true count: running count divided by decks remaining. */
    val trueCountExact: Double
        get() = if (cardsRemaining == 0) 0.0 else runningCount / decksRemaining

    /** The count the bet ramp uses, on the Hi-Lo true count scale. See [CountSystem.betLevelExact]. */
    val betLevelExact: Double get() = system.betLevelExact(runningCount, decksRemaining)

    /** True count floored to an integer, the usual convention for betting and indices. */
    val trueCountFloor: Int get() = floor(trueCountExact).toInt()

    /** Decks remaining rounded to the nearest half deck, the way players estimate it from the discard tray. */
    val decksRemainingEstimate: Double
        get() = ((decksRemaining * 2).roundToInt() / 2.0).coerceAtLeast(0.5)
}

object TrueCount {
    /**
     * A true count answer is accepted when it is within 1 of the exact value.
     * Players estimate decks remaining by eye, so small differences are normal.
     */
    fun isAcceptable(answer: Int, exact: Double): Boolean =
        kotlin.math.abs(answer - exact) <= 1.0

    /** Bet ramps: units for floored true counts of +1 or less, +2, +3, +4, +5 or more. */
    val RAMP_1_4 = listOf(1, 2, 3, 4, 4)
    val RAMP_1_8 = listOf(1, 2, 4, 6, 8)
    val RAMP_1_12 = listOf(1, 2, 4, 8, 12)

    fun rampIndex(trueCount: Int): Int = (trueCount - 1).coerceIn(0, 4)

    /** Units to bet at this floored true count on the given ramp (default 1 to 8). */
    fun suggestedUnits(trueCount: Int, ramp: List<Int> = RAMP_1_8): Int = ramp[rampIndex(trueCount)]

    fun describeRamp(ramp: List<Int>): String =
        "+1 or less bet ${ramp[0]}, +2 bet ${ramp[1]}, +3 bet ${ramp[2]}, +4 bet ${ramp[3]}, +5 or more bet ${ramp[4]}"
}
