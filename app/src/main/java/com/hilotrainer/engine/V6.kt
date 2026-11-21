package com.hilotrainer.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

/**
 * Rough Hi-Lo player edge by true count. A common rule of thumb (gamblingcalc.com,
 * card counting guide): about -0.5% off the top of a six-deck game, and each +1 of
 * true count adds roughly +0.5%. It is an estimate, not an exact figure for every rule set.
 */
object Edge {
    const val OFF_TOP = -0.005
    const val PER_TRUE_COUNT = 0.005

    fun atTrueCount(tc: Double): Double = OFF_TOP + PER_TRUE_COUNT * tc

    /** Expected win in dollars for a bet at this true count. */
    fun handEv(bet: Int, tc: Double): Double = bet * atTrueCount(tc)
}

/**
 * Kelly bet sizing: optimal bet = edge / variance x bankroll (rarepike.com, bankroll
 * management). Blackjack's variance per hand is about 1.3, the same value the risk
 * of ruin calculator uses.
 */
object Kelly {
    const val VARIANCE = 1.3
    val FRACTIONS = listOf(1.0 to "Full Kelly", 0.5 to "Half Kelly", 0.25 to "Quarter Kelly")

    /** Dollars to bet, before rounding to chips. 0 when the edge isn't positive. */
    fun bet(bankroll: Int, tc: Double, fraction: Double = 0.5): Double {
        val e = Edge.atTrueCount(tc)
        if (e <= 0 || bankroll <= 0) return 0.0
        return bankroll * e / VARIANCE * fraction
    }

    /** Rounded down to $5 chips, never below the table minimum. */
    fun chips(bankroll: Int, tc: Double, fraction: Double = 0.5, tableMin: Int = 10): Int {
        val b = bet(bankroll, tc, fraction)
        return maxOf(tableMin, (b / 5).toInt() * 5)
    }
}

/** Expected value versus luck across a session. */
class EvTracker {
    var expected = 0.0; private set
    var actual = 0; private set
    var hands = 0; private set
    var wagered = 0; private set

    fun add(bet: Int, tc: Double, net: Int) {
        if (bet <= 0) return
        expected += Edge.handEv(bet, tc)
        actual += net
        wagered += bet
        hands++
    }

    /** Actual result minus what skill and the count earned on average. */
    val luck: Double get() = actual - expected
    fun reset() { expected = 0.0; actual = 0; hands = 0; wagered = 0 }
}

/** Dealer mistakes to spot: wrong payouts and wrongly announced totals. */
object DealerErrors {
    enum class Kind { PAYOUT, TOTAL }
    data class Check(val kind: Kind, val said: String, val wrong: Boolean, val explain: String, val shortBy: Int = 0)

    /**
     * Builds what the dealer says after a round. Returns null when there is nothing
     * to check. Half of the checks contain a mistake.
     */
    fun make(dealerCards: List<Card>, results: List<HandResult>, bets: List<Int>, rnd: Random = Random.Default): Check? {
        val wrong = rnd.nextBoolean()
        val (total, soft) = handTotal(dealerCards)
        val winIndex = results.indexOfFirst { it.net > 0 }
        val usePayout = winIndex >= 0 && rnd.nextInt(3) != 0
        if (usePayout) {
            val r = results[winIndex]
            val bet = bets.getOrElse(winIndex) { 10 }
            val right = r.net
            if (!wrong) return Check(Kind.PAYOUT, "Paying you ${dollars(right)}.", false, "A ${dollars(bet)} ${if (r.outcome == Outcome.BLACKJACK) "blackjack" else "win"} pays ${dollars(right)}. The dealer got it right.")
            val paid = when {
                r.outcome == Outcome.BLACKJACK -> bet // paid even money instead of the blackjack bonus
                bet >= 20 && rnd.nextBoolean() -> right - 5
                else -> right - maxOf(5, bet / 2)
            }.coerceAtLeast(5)
            val why = if (r.outcome == Outcome.BLACKJACK) "A blackjack pays more than even money. ${dollars(bet)} should win ${dollars(right)}."
            else "A ${dollars(bet)} bet that wins pays ${dollars(right)}, not ${dollars(paid)}."
            return Check(Kind.PAYOUT, "Paying you ${dollars(paid)}.", true, why, right - paid)
        }
        if (dealerCards.size < 2 || total > 21) {
            if (total > 21) {
                if (!wrong) return Check(Kind.TOTAL, "Dealer busts with $total.", false, "The dealer's cards add up to $total. Correct.")
                val said = rnd.nextInt(17, 22)
                return Check(Kind.TOTAL, "Dealer has $said.", true, "The dealer's cards add up to $total, a bust, not $said.")
            }
            return null
        }
        val label = if (soft && total < 21) "soft $total" else "$total"
        if (!wrong) return Check(Kind.TOTAL, "Dealer has $label.", false, "The dealer's cards add up to $label. Correct.")
        val off = listOf(-2, -1, 1, 2).filter { total + it in 17..21 && total + it != total }.ifEmpty { listOf(1) }
        val said = total + off[rnd.nextInt(off.size)]
        return Check(Kind.TOTAL, "Dealer has $said.", true, "The dealer's cards add up to $label, not $said. A misread total can cost you a push or a win.")
    }

    private fun dollars(n: Int) = "$" + "%,d".format(n)
}

/** Daily challenge: the same shoe for everyone on a given date, replayable as often as you like. */
object DailyChallenge {
    const val COUNT_CARDS = 40
    const val STRATEGY_HANDS = 5
    const val CARD_MS = 900L
    const val MAX_SCORE = 100

    fun seed(epochDay: Long): Long = epochDay * 7_919L + 104_729L

    /** The cards to count down, from a freshly shuffled six-deck shoe seeded by the date. */
    fun cards(epochDay: Long): List<Card> {
        val shoe = Shoe(6, 1.0, Random(seed(epochDay)))
        return List(COUNT_CARDS) { shoe.draw() }
    }

    fun runningCount(epochDay: Long, system: CountSystem = CountSystem.HI_LO): Int =
        system.initialCount(6) + cards(epochDay).sumOf { system.tag(it) }

    /** Strategy questions for the day: hard, soft and pair hands. */
    fun questions(epochDay: Long, rules: Rules, system: CountSystem, ramp: List<Int>): List<Course.Question> {
        val rnd = Random(seed(epochDay) + 1)
        return List(STRATEGY_HANDS) { i -> Course.question(listOf(1, 2, 3, 1, 3)[i], rnd, rules, system, ramp) }
    }

    /** 50 points for the exact count (25 if one off), 10 for each right play. */
    fun score(countAnswer: Int, actual: Int, strategyRight: Int): Int {
        val c = when (abs(countAnswer - actual)) { 0 -> 50; 1 -> 25; else -> 0 }
        return c + strategyRight * 10
    }
}

/** Haptic count drill: feel each card's tag as a vibration pattern. */
object HapticCode {
    /** Off/on timings in milliseconds for a card's Hi-Lo tag. */
    fun pattern(tag: Int): LongArray = when {
        tag > 0 -> longArrayOf(0, 70)             // low card: one short buzz
        tag < 0 -> longArrayOf(0, 320)            // high card: one long buzz
        else -> longArrayOf(0, 35, 90, 35)        // neutral: two quick taps
    }

    fun describe(tag: Int): String = when {
        tag > 0 -> "One short buzz: low card, +1"
        tag < 0 -> "One long buzz: high card, -1"
        else -> "Two quick taps: 7, 8 or 9, 0"
    }
}

/** Deck estimation drill questions. */
object DeckEstimate {
    enum class View { TRAY, SHOE }
    data class Q(val view: View, val decks: Int, val cardsInView: Int, val step: Double) {
        val decksInView: Double get() = cardsInView / 52.0
        /** Nearest step (half or quarter deck) to the true amount. */
        val answer: Double get() = Math.round(decksInView / step) * step
        fun choices(): List<Double> {
            val all = generateSequence(step) { it + step }.takeWhile { it < decks - 0.01 }.toList()
            return all
        }
        fun isRight(guess: Double): Boolean = abs(guess - decksInView) <= step / 2 + 1e-9
    }

    fun make(rnd: Random, expert: Boolean): Q {
        val decks = listOf(6, 8)[rnd.nextInt(2)]
        val view = View.entries[rnd.nextInt(2)]
        val step = if (expert) 0.25 else 0.5
        val cards = rnd.nextInt(20, decks * 52 - 20)
        return Q(view, decks, cards, step)
    }
}

/** A full simulated session: blocks of play with suggested breaks and accuracy tracked by block. */
object SessionPlan {
    const val MINUTES = 60
    const val BLOCK_MINUTES = 10
    const val BREAK_EVERY_MINUTES = 20
    const val BREAK_MINUTES = 5
    const val BLOCKS = MINUTES / BLOCK_MINUTES

    data class Block(val decisions: Int = 0, val correct: Int = 0, val counts: Int = 0, val countsOk: Int = 0) {
        val accuracy: Double? get() {
            val n = decisions + counts
            return if (n == 0) null else (correct + countsOk).toDouble() / n
        }
    }

    fun blockAt(elapsedMs: Long): Int = (elapsedMs / (BLOCK_MINUTES * 60_000L)).toInt().coerceIn(0, BLOCKS - 1)

    /** Accuracy change from the first block with data to the last, in percentage points. */
    fun fatigueDrop(blocks: List<Block>): Double? {
        val acc = blocks.mapNotNull { it.accuracy }
        if (acc.size < 2) return null
        return (acc.first() - acc.last()) * 100
    }
}

/** Heat that comes from sloppy play: slow, unsure decisions look like someone working something out. */
object PitHeat {
    /** A decision slower than this looks like you're thinking about the count. */
    const val SLOW_MS = 10_000L
    const val SLOW_HEAT = 0.4
    fun slowHeat(decisionMs: Long): Double = if (decisionMs > SLOW_MS) SLOW_HEAT else 0.0
}

/** Floor of a true count, shared helper. */
fun tcFloor(tc: Double): Int = floor(tc).toInt()
