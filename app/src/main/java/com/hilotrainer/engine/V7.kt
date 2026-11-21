package com.hilotrainer.engine

import kotlin.random.Random

/** Dealers with their own pace and habits. A new one comes in every so often. */
data class DealerProfile(
    val name: String,
    /** Multiplies the dealing delay: below 1 is faster. */
    val pace: Double,
    /** How often (percent of rounds) the dealer makes small talk. */
    val chatty: Int,
    /** How often (percent of rounds) the dealer lets the hole card show. */
    val sloppy: Int,
    val style: String,
    val lines: List<String> = emptyList(),
)

object Dealers {
    val ALL = listOf(
        DealerProfile("Maria", 0.8, 10, 0, "fast and precise", listOf("Good luck, everyone.", "Bets down, please.")),
        DealerProfile("Frank", 1.0, 35, 3, "friendly and chatty", listOf("Where's everybody from tonight?", "Come on, let's get the table a win!", "I've got a good feeling about this shoe.")),
        DealerProfile("Dee", 1.15, 5, 8, "relaxed, a little sloppy", listOf("Oops, take your time.")),
        DealerProfile("Kenji", 0.7, 5, 0, "very fast", listOf("Next hand.")),
        DealerProfile("Rosa", 0.95, 20, 2, "steady and warm", listOf("Nice hand.", "Thank you for playing.")),
    )

    /** Rounds before the dealer is relieved by the next one. */
    const val SHIFT_ROUNDS = 25

    fun next(current: DealerProfile?, rnd: Random = Random.Default): DealerProfile =
        ALL.filter { it != current }.let { it[rnd.nextInt(it.size)] }
}

/** Names for the other players at the table. */
object Seats {
    val NAMES = listOf("Ray", "Linda", "Tom", "Priya", "Sal", "June", "Marcus", "Ellie", "Dmitri", "Bea", "Carlos", "Nora")
    fun pick(taken: Collection<String>, rnd: Random = Random.Default): String =
        NAMES.filter { it !in taken }.let { if (it.isEmpty()) "Player" else it[rnd.nextInt(it.size)] }
}

/** Chips for an amount, biggest first, the way a dealer colors up. */
object ColorUp {
    val DENOMS = listOf(500, 100, 25, 5, 1)
    fun breakdown(amount: Int): List<Pair<Int, Int>> {
        var left = amount
        val out = mutableListOf<Pair<Int, Int>>()
        for (d in DENOMS) { val n = left / d; if (n > 0) { out += d to n; left -= n * d } }
        return out
    }

    fun describe(amount: Int): String = breakdown(amount).joinToString(", ") { (d, n) -> "$n x \$$d" }
}

/** What the dealer says out loud. */
object DealerCalls {
    fun result(dealerCards: List<Card>): String {
        val (t, _) = handTotal(dealerCards)
        return when {
            dealerCards.size == 2 && t == 21 -> "Dealer has blackjack."
            t > 21 -> "Dealer busts."
            else -> "Dealer has $t."
        }
    }
}
