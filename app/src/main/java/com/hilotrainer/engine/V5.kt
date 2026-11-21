package com.hilotrainer.engine

import kotlin.math.floor

/**
 * Error heat map: how often each kind of decision goes wrong, by hand shape and
 * dealer card, and by true count.
 */
object ErrorMap {
    data class Cell(val n: Int = 0, val wrong: Int = 0) {
        val rate: Double get() = if (n == 0) 0.0 else wrong.toDouble() / n
    }

    /** Hand shape and dealer card, like "H16v10", "S18v9" or "P8vA". Pairs only count as pairs on two cards. */
    fun cellKey(hand: Hand, up: Card): String? {
        val u = if (up.rank.isAce) "A" else up.upValue.toString()
        return when {
            hand.cards.size < 2 -> null
            hand.isPair -> "P" + (if (hand.cards[0].rank.isAce) "A" else hand.cards[0].rank.value.toString()) + "v" + u
            hand.isSoft -> "S${hand.total}v$u"
            hand.total in 5..21 -> "H${hand.total}v$u"
            else -> null
        }
    }

    /** True count buckets shown on the heat map: -2 or less, -1, 0, +1, +2, +3, +4, +5 or more. */
    val TC_BUCKETS = listOf("-2 or less", "-1", "0", "+1", "+2", "+3", "+4", "+5 or more")
    fun tcBucket(tc: Double): Int = (floor(tc).toInt() + 2).coerceIn(0, TC_BUCKETS.size - 1)

    fun add(map: Map<String, Cell>, key: String, correct: Boolean): Map<String, Cell> {
        val c = map[key] ?: Cell()
        return map + (key to Cell(c.n + 1, c.wrong + if (correct) 0 else 1))
    }

    fun encode(map: Map<String, Cell>): String = map.entries.joinToString("\n") { "${it.key}\t${it.value.n}\t${it.value.wrong}" }
    fun decode(s: String): Map<String, Cell> = s.lines().mapNotNull { l ->
        val p = l.split('\t')
        if (p.size < 3) null else p[0] to Cell(p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0)
    }.toMap()

    /**
     * Weak spots: cells with at least one mistake, worst first. Ranked by
     * mistakes, then by error rate, so a play missed 5 of 6 times beats one missed 1 of 1.
     */
    fun weakSpots(map: Map<String, Cell>, limit: Int = 12): List<Pair<String, Cell>> =
        map.entries.filter { it.value.wrong > 0 && Review.build(it.key) != null }
            .sortedWith(compareByDescending<Map.Entry<String, Cell>> { it.value.wrong * (0.5 + it.value.rate) }.thenByDescending { it.value.rate })
            .take(limit).map { it.key to it.value }
}

/** Turns what the speech recognizer heard into a count, like "plus three", "minus 2" or "zero". */
object VoiceCount {
    private val units = mapOf(
        "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50)
    private val negative = setOf("minus", "negative", "-")
    private val positive = setOf("plus", "positive", "+")

    fun parse(heard: String): Int? {
        val t = heard.lowercase().replace("\u2212", "-").replace("+", " + ").replace("-", " - ")
            .replace(Regex("[^a-z0-9+\\- ]"), " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        var sign = 1
        var value: Int? = null
        for (w in t) {
            when {
                w in negative -> if (value == null) sign = -1
                w in positive -> if (value == null) sign = 1
                w.toIntOrNull() != null -> if (value == null) value = w.toInt()
                w in tens -> if (value == null) value = tens.getValue(w)
                w in units -> {
                    val v = units.getValue(w)
                    value = when {
                        value == null -> v
                        value!! % 10 == 0 && value!! >= 20 && v < 10 -> value!! + v
                        else -> value
                    }
                }
            }
        }
        return value?.let { it * sign }
    }
}

/**
 * Exact side bet values from the cards you haven't seen yet. The unseen cards are
 * the whole shoe minus every card you've seen since the shuffle.
 */
object SideBets {
    /** Perfect Pairs pays 25 to 1 same suit, 12 to 1 same color, 6 to 1 mixed colors (a common pay table). */
    const val PP_PERFECT = 25
    const val PP_COLORED = 12
    const val PP_MIXED = 6

    /** Counts of each unseen card, indexed [rank ordinal][suit ordinal]. Tens, jacks, queens and kings are separate ranks. */
    fun unseen(decks: Int, seen: List<Card>): Array<IntArray> {
        val n = Array(Rank.entries.size) { IntArray(Suit.entries.size) { decks } }
        seen.forEach { n[it.rank.ordinal][it.suit.ordinal]-- }
        return n
    }

    /** Expected return per unit bet on Perfect Pairs for the first two cards from the unseen cards. */
    fun perfectPairsEv(n: Array<IntArray>): Double {
        val total = n.sumOf { it.sum() }
        if (total < 2) return 0.0
        var ev = 0.0
        for (r in Rank.entries.indices) for (s in Suit.entries.indices) {
            val c = n[r][s]
            if (c <= 0) continue
            val p1 = c.toDouble() / total
            val red = Suit.entries[s].isRed
            val same = c - 1
            var colored = 0
            var mixed = 0
            for (s2 in Suit.entries.indices) {
                if (s2 == s) continue
                if (Suit.entries[s2].isRed == red) colored += n[r][s2] else mixed += n[r][s2]
            }
            val rest = total - 1
            val win = same * PP_PERFECT + colored * PP_COLORED + mixed * PP_MIXED
            val lose = rest - same - colored - mixed
            ev += p1 * (win - lose).toDouble() / rest
        }
        return ev
    }

    /** Expected return per unit on insurance (pays 2 to 1) given the unseen cards. */
    fun insuranceEv(n: Array<IntArray>): Double {
        val total = n.sumOf { it.sum() }
        if (total == 0) return 0.0
        val tens = listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING).sumOf { n[it.ordinal].sum() }
        val p = tens.toDouble() / total
        return 2 * p - (1 - p)
    }
}

/** Shuffle styles. A continuous shuffler puts each round's cards straight back, so the count never builds. */
enum class ShuffleStyle(val label: String) {
    HAND("Hand shuffle with a cut card"),
    MACHINE("Shuffle machine, same cut card"),
    CONTINUOUS("Continuous shuffling machine"),
}

/** How a computer player plays. Realistic tables have a few of these. */
enum class BotStyle(val label: String, val note: String) {
    BASIC("Basic strategy", "plays by the book"),
    NEVER_BUST("Never busts", "stands on 12 or more"),
    MIMIC("Mimics the dealer", "hits until 17, never doubles"),
    HUNCH("Hunch player", "hits stiffs against small cards"),
    AGGRESSIVE("Doubles too much", "doubles 8 through 11 against anything");

    /** What this player does. Returns null to defer to basic strategy. */
    fun override(h: Hand, up: Card, canDouble: Boolean): Action? {
        val t = h.total
        val u = up.upValue
        return when (this) {
            BASIC -> null
            NEVER_BUST -> if (!h.isSoft && t >= 12) Action.STAND else null
            MIMIC -> if (t < 17 || (t == 17 && h.isSoft)) Action.HIT else Action.STAND
            HUNCH -> if (!h.isSoft && t in 12..16 && u in 2..6) Action.HIT else null
            AGGRESSIVE -> if (canDouble && !h.isSoft && t in 8..11) Action.DOUBLE else null
        }
    }
}

/** Back-counting: watch from behind, sit down when the count is good, leave when it falls back. */
object BackCount {
    /** Hi-Lo true count of +2 is a typical place to join a table (PokerNews, back counting). */
    const val ENTER_AT = 2
    /** Leave once the count falls back to neutral. */
    const val LEAVE_AT = 0

    fun shouldEnter(betLevel: Double): Boolean = floor(betLevel).toInt() >= ENTER_AT
    fun shouldLeave(betLevel: Double): Boolean = floor(betLevel).toInt() <= LEAVE_AT
}

/**
 * Team play signals. Real teams make up their own; these are the trainer's practice code.
 * A spotter flags the big player when the count is high, then keeps signalling the count.
 */
object TeamSignals {
    data class Signal(val gesture: String, val meaning: String, val minLevel: Int?)

    val SIGNALS = listOf(
        Signal("Touches chin", "Count is +2: come in", 2),
        Signal("Folds arms", "Count is +3", 3),
        Signal("Hand flat on chips", "Count is +4", 4),
        Signal("Rubs back of neck", "Count is +5 or more", 5),
        Signal("Stretches both arms", "Count is gone: leave", null),
    )

    /** The signal a spotter should give at this bet level, or null when nothing needs saying (+1 to call in range). */
    fun forLevel(level: Double): Signal? {
        val l = floor(level).toInt()
        return when {
            l >= 5 -> SIGNALS[3]
            l == 4 -> SIGNALS[2]
            l == 3 -> SIGNALS[1]
            l == 2 -> SIGNALS[0]
            l <= 0 -> SIGNALS[4]
            else -> null
        }
    }

    /** Big player units: the team bets the top of the ramp as soon as the BP sits down. */
    fun bigPlayerUnits(level: Double, ramp: List<Int>): Int = TrueCount.suggestedUnits(floor(level).toInt(), ramp)
}
