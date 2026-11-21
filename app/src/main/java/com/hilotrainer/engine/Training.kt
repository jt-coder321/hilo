package com.hilotrainer.engine

import kotlin.math.exp
import kotlin.math.ln

/**
 * Spaced repetition for missed plays (a Leitner box schedule).
 *
 * A missed two-card decision is stored by its shape, like "H16v10" (hard 16
 * against a 10), "S18v9" or "P8vA". Each correct review moves it up a box and
 * pushes the next review further out; a miss sends it back to box 0.
 */
object Review {
    /** Days until the next review after reaching each box. */
    val INTERVAL_DAYS = listOf(0, 1, 3, 7, 14, 30)
    const val DAY_MS = 24L * 60 * 60 * 1000

    data class Item(val key: String, val box: Int, val dueMs: Long, val misses: Int) {
        val mastered: Boolean get() = box >= INTERVAL_DAYS.size - 1
    }

    /** The key for a two-card decision, or null for hands the review can't rebuild. */
    fun keyFor(hand: Hand, up: Card): String? {
        if (hand.cards.size != 2 || hand.isSplitHand) return null
        val u = if (up.rank.isAce) "A" else up.upValue.toString()
        return when {
            hand.isPair -> "P" + (if (hand.cards[0].rank.isAce) "A" else hand.cards[0].rank.value.toString()) + "v" + u
            hand.isSoft -> "S${hand.total}v$u"
            else -> "H${hand.total}v$u"
        }
    }

    /** Rebuild a hand and dealer card for a key. */
    fun build(key: String, suitPick: (Int) -> Suit = { Suit.entries[it % 4] }): Pair<Hand, Card>? {
        val parts = key.drop(1).split("v")
        if (parts.size != 2) return null
        fun rank(v: String): Rank? = if (v == "A") Rank.ACE else v.toIntOrNull()?.let { n -> Rank.entries.firstOrNull { it.value == n } }
        val up = rank(parts[1]) ?: return null
        val hand = when (key[0]) {
            'P' -> { val r = rank(parts[0]) ?: return null; listOf(r, r) }
            'S' -> { val t = parts[0].toIntOrNull() ?: return null; val other = t - 11; if (other !in 2..9) return null; listOf(Rank.ACE, rank(other.toString())!!) }
            'H' -> {
                val t = parts[0].toIntOrNull() ?: return null
                val a = (t - 2).coerceAtMost(10).let { if (t - it == it) it - 1 else it }
                val b = t - a
                if (a !in 2..10 || b !in 2..10 || a == b) return null
                listOf(rank(a.toString())!!, rank(b.toString())!!)
            }
            else -> return null
        }
        return Hand(hand.mapIndexed { i, r -> Card(r, suitPick(i)) }) to Card(up, suitPick(2))
    }

    fun describe(key: String): String {
        val parts = key.drop(1).split("v")
        val kind = when (key[0]) { 'P' -> "Pair of ${parts[0]}s"; 'S' -> "Soft ${parts[0]}"; else -> "Hard ${parts[0]}" }
        return "$kind vs ${parts.getOrElse(1) { "?" }}"
    }

    fun missed(items: List<Item>, key: String, now: Long): List<Item> {
        val old = items.firstOrNull { it.key == key }
        val next = Item(key, 0, now, (old?.misses ?: 0) + 1)
        return listOf(next) + items.filter { it.key != key }
    }

    fun answered(items: List<Item>, key: String, correct: Boolean, now: Long): List<Item> = items.map {
        if (it.key != key) it
        else if (correct) {
            val box = (it.box + 1).coerceAtMost(INTERVAL_DAYS.size - 1)
            it.copy(box = box, dueMs = now + INTERVAL_DAYS[box] * DAY_MS)
        } else it.copy(box = 0, dueMs = now, misses = it.misses + 1)
    }

    fun due(items: List<Item>, now: Long): List<Item> = items.filter { it.dueMs <= now && !it.mastered }.sortedBy { it.dueMs }

    fun encode(items: List<Item>): String = items.joinToString("\n") { "${it.key}\t${it.box}\t${it.dueMs}\t${it.misses}" }

    fun decode(s: String): List<Item> = s.lines().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 4) null else Item(p[0], p[1].toIntOrNull() ?: 0, p[2].toLongOrNull() ?: 0L, p[3].toIntOrNull() ?: 0)
    }
}

/**
 * Risk of ruin with the standard approximation ROR = e^(-2 x edge x bankroll / variance),
 * with the edge per hand as a fraction of one unit, the bankroll in units and the
 * variance in units squared per hand.
 */
object RiskOfRuin {
    fun ror(edge: Double, bankrollUnits: Double, variance: Double): Double {
        if (edge <= 0.0) return 1.0
        if (variance <= 0.0 || bankrollUnits <= 0.0) return 1.0
        return exp(-2.0 * edge * bankrollUnits / variance).coerceIn(0.0, 1.0)
    }

    /** Units of bankroll needed to keep the risk of ruin at [target]. */
    fun unitsFor(target: Double, edge: Double, variance: Double): Double {
        if (edge <= 0.0) return Double.POSITIVE_INFINITY
        return -ln(target) * variance / (2.0 * edge)
    }

    /** Expected win per hour in units. */
    fun unitsPerHour(edge: Double, handsPerHour: Int): Double = edge * handsPerHour

    /** One standard deviation of results over an hour, in units. */
    fun sdPerHour(variance: Double, handsPerHour: Int): Double = kotlin.math.sqrt(variance * handsPerHour)
}

/**
 * Cover play coaching. Bet changes a watcher would notice raise "heat".
 *
 * Based on common camouflage advice: raise in small steps, let a winning bet
 * ride rather than jumping after a loss, and back down slowly rather than
 * dropping straight to the minimum.
 */
object CoverPlay {
    data class Note(val heat: Double, val message: String?)

    /**
     * Heat added by going from [prevUnits] to [units]. [wonLast] is whether the
     * previous hand won, [afterShuffle] is true for the first bet of a new shoe.
     */
    fun betHeat(prevUnits: Int, units: Int, wonLast: Boolean, afterShuffle: Boolean): Note {
        if (prevUnits <= 0 || units <= 0) return Note(0.0, null)
        val up = units - prevUnits
        val down = prevUnits - units
        return when {
            up >= 4 && !wonLast -> Note(2.0, "Big jump after a loss. Raise in smaller steps, or after a win so it looks like letting your bet ride.")
            up >= 3 && !wonLast -> Note(1.0, "Noticeable raise after a loss. A smaller step or a raise after a win draws less attention.")
            up > 0 && wonLast && units <= prevUnits * 2 -> Note(0.0, null)
            up >= 4 -> Note(1.0, "Large raise. Even after a win, more than doubling stands out.")
            afterShuffle && down >= 4 -> Note(1.5, "Dropping from a big bet to the minimum right at the shuffle is a classic tell. Back down over a couple of hands.")
            down >= 5 -> Note(1.0, "Sharp drop in one hand. Backing down slowly looks more natural.")
            else -> Note(0.0, null)
        }
    }

    /** Heat cools a little every round. */
    const val COOL_PER_ROUND = 0.4
    const val WARN_AT = 3.0
    const val LEAVE_AT = 5.0
}

/** Speed ladder rungs, milliseconds per card. */
object SpeedLadder {
    val RUNGS_MS = listOf(1500, 1200, 1000, 800, 650, 500, 400, 300)
    fun label(rung: Int): String =
        java.math.BigDecimal(RUNGS_MS[rung.coerceIn(0, RUNGS_MS.size - 1)]).divide(java.math.BigDecimal(1000)).stripTrailingZeros().toPlainString() + "s"
    fun next(rung: Int, correct: Boolean): Int = if (correct) (rung + 1).coerceAtMost(RUNGS_MS.size - 1) else (rung - 1).coerceAtLeast(0)
}

/** Casino test levels: a full single deck, a few cards held back, timed. The trainer's own targets. */
object CasinoTest {
    data class Level(val name: String, val seconds: Int)
    val LEVELS = listOf(Level("Bronze", 60), Level("Silver", 45), Level("Gold", 35), Level("Platinum", 25))
    /** Highest level passed for a correct answer in [seconds], or null if none. */
    fun passed(seconds: Double): Int? = LEVELS.indices.lastOrNull { seconds <= LEVELS[it].seconds }
}
