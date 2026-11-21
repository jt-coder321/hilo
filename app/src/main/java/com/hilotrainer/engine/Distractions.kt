package com.hilotrainer.engine

import kotlin.random.Random

/**
 * Table distractions, built fresh at random every round: which ones happen, when,
 * what is said and by whom, where on the screen, and how many at once.
 */
object Distractions {
    enum class Kind { QUESTION, NUMBERS, WALK_BY, BUZZ, CHIPS, CROWD }

    data class Event(
        val atMs: Long,
        val kind: Kind,
        /** Who is talking, for example "Server" or a player's name. */
        val who: String = "",
        val text: String = "",
        /** Answers to pick from, for a question. Empty for everything else. */
        val options: List<String> = emptyList(),
        /** Index of the right answer when there is one; null when any answer is fine. */
        val correct: Int? = null,
        /** Where it shows across the table, 0 left to 1 right. */
        val x: Float = 0.5f,
        /** Where it shows down the table, 0 top to 1 bottom. */
        val y: Float = 0.4f,
        val durationMs: Long = 2500,
        /** How many repeats (chip clicks, buzzes). */
        val count: Int = 1,
        /** Voice settings for the spoken line. */
        val pitch: Float = 1f,
        val rate: Float = 1f,
        val fromLeft: Boolean = true,
    )

    /** What the generator needs to know about the table. */
    data class Table(
        val decks: Int = 6,
        val minBet: Int = 10,
        val dealerHitsSoft17: Boolean = true,
        val sixToFive: Boolean = false,
        val players: List<String> = emptyList(),
        val yourBet: Int = 10,
    )

    /**
     * One round's distractions. Light is one small thing on about half the rounds and never
     * stops you. Real is busy: one to three things on most rounds, and a question you must answer.
     */
    fun plan(real: Boolean, t: Table, rnd: Random = Random.Default): List<Event> {
        val chance = if (real) 85 else 50
        if (rnd.nextInt(100) >= chance) return emptyList()
        val n = if (real) 1 + rnd.nextInt(3) else 1
        val kinds = mutableListOf<Kind>()
        repeat(n) {
            val pool = if (real) listOf(Kind.QUESTION, Kind.QUESTION, Kind.NUMBERS, Kind.NUMBERS, Kind.NUMBERS, Kind.WALK_BY, Kind.WALK_BY, Kind.BUZZ, Kind.CHIPS, Kind.CROWD)
            else listOf(Kind.NUMBERS, Kind.CHIPS, Kind.CROWD)
            var k = pool[rnd.nextInt(pool.size)]
            // Only one question at a time.
            if (k == Kind.QUESTION && Kind.QUESTION in kinds) k = Kind.NUMBERS
            kinds += k
        }
        var at = rnd.nextLong(150, 1600)
        return kinds.map { k ->
            val e = make(k, at, real, t, rnd)
            at += rnd.nextLong(400, 2600)
            e
        }
    }

    private fun voice(rnd: Random) = (0.75f + rnd.nextFloat() * 0.6f) to (0.85f + rnd.nextFloat() * 0.45f)

    private fun make(k: Kind, at: Long, real: Boolean, t: Table, rnd: Random): Event {
        val who = speaker(t, rnd)
        val (pitch, rate) = voice(rnd)
        val x = 0.15f + rnd.nextFloat() * 0.7f
        val y = 0.12f + rnd.nextFloat() * 0.45f
        return when (k) {
            Kind.QUESTION -> question(at, real, t, rnd).copy(x = x, y = y, pitch = pitch, rate = rate)
            Kind.NUMBERS -> Event(at, k, who, numberLine(rnd), x = x, y = y, durationMs = rnd.nextLong(1800, 3400), pitch = pitch, rate = rate)
            Kind.WALK_BY -> Event(
                at, k, pick(rnd, "A server with a tray", "Someone squeezing past", "A guest looking for a seat", "A security guard", "A cocktail server"),
                y = 0.3f + rnd.nextFloat() * 0.3f, durationMs = rnd.nextLong(900, 1800), fromLeft = rnd.nextBoolean(),
            )
            Kind.BUZZ -> Event(at, k, "", pick(rnd, "Bzzt", "Bzz bzz", "Bzzzt"), x = x, y = 0.2f + rnd.nextFloat() * 0.6f, durationMs = 1200, count = 1 + rnd.nextInt(3))
            Kind.CHIPS -> Event(at, k, "", "", count = 2 + rnd.nextInt(5), durationMs = rnd.nextLong(60, 180))
            Kind.CROWD -> Event(at, k, "", crowdLine(rnd), x = x, y = y, durationMs = rnd.nextLong(1500, 2800), pitch = pitch, rate = rate)
        }
    }

    private fun speaker(t: Table, rnd: Random): String {
        val people = t.players + listOf("Player beside you", "Someone behind you", "A player at the next table")
        return people[rnd.nextInt(people.size)]
    }

    private fun <T> pick(rnd: Random, vararg xs: T): T = xs[rnd.nextInt(xs.size)]

    /** Lines full of numbers, which compete with the count in your head. */
    fun numberLine(rnd: Random): String {
        val a = 2 + rnd.nextInt(18)
        val b = 1 + rnd.nextInt(9)
        val c = 100 + rnd.nextInt(900)
        val room = (2 + rnd.nextInt(28)) * 100 + rnd.nextInt(40)
        return when (rnd.nextInt(12)) {
            0 -> "I'm in room $room, I keep forgetting."
            1 -> "That's $a hands in a row I've lost."
            2 -> "I'm down \$$c tonight. No, wait, \$${c + 10 * b}."
            3 -> "Last time I had $b blackjacks in an hour."
            4 -> "My flight's at $b:${10 + rnd.nextInt(50)} tomorrow."
            5 -> "Is it $a or ${a + b} minutes to the show?"
            6 -> "I've played here $a nights this year."
            7 -> "Table $b had a run of $a bankers."
            8 -> "My lucky number's ${a + 10}. Always bet it."
            9 -> "Who had the $b? Was that ${a}?"
            10 -> "Hey, what's ${a} plus ${b + 3}? I'm tipping."
            else -> "Seat $b, you owe me \$${b * 5}!"
        }
    }

    private fun crowdLine(rnd: Random): String = pick(
        rnd,
        "The craps table erupts in cheers.", "Someone hits a jackpot nearby.", "A bachelor party walks past, shouting.",
        "Bells ring from the slot machines.", "A player slaps the table and groans.", "Somebody drops a stack of chips.",
    )

    private fun question(at: Long, real: Boolean, t: Table, rnd: Random): Event {
        val opts = mutableListOf<String>()
        fun factual(who: String, q: String, right: String, wrong: List<String>): Event {
            val all = (wrong.shuffled(rnd).take(2) + right).shuffled(rnd)
            return Event(at, Kind.QUESTION, who, q, all, all.indexOf(right), durationMs = 0)
        }
        fun social(who: String, q: String, answers: List<String>): Event =
            Event(at, Kind.QUESTION, who, q, answers.shuffled(rnd).take(3), null, durationMs = 0)
        val name = t.players.randomOrNull(rnd) ?: "A player"
        return when (rnd.nextInt(if (real) 9 else 3)) {
            0 -> social("Server", pick(rnd, "Can I get you something to drink?", "Cocktails? Water, soda, coffee?", "Anything for you, hon?"),
                listOf("Water, please", "Coffee", "Soda", "No thanks", "Iced tea"))
            1 -> social("Pit boss", pick(rnd, "How's your night going?", "Are you staying with us?", "Have you got a players card?", "First time at this table?"),
                listOf("Pretty good", "Just visiting", "Not yet", "Yes, thanks", "Can't complain"))
            2 -> social(name, pick(rnd, "Where are you from?", "You here for the game tomorrow?", "Is this your first trip?"),
                listOf("Out west", "Just in town for work", "Second time here", "Visiting family"))
            3 -> factual(name, "What's the minimum here?", "\$${t.minBet}", listOf("\$5", "\$25", "\$15", "\$50").filter { it != "\$${t.minBet}" })
            4 -> factual(name, "How many decks are they using?", "${t.decks}", listOf("1", "2", "4", "6", "8").filter { it != "${t.decks}" })
            5 -> factual(name, "Does the dealer hit soft 17 here?", if (t.dealerHitsSoft17) "Yes" else "No", listOf(if (t.dealerHitsSoft17) "No" else "Yes", "Only on 6 decks"))
            6 -> factual(name, "What does blackjack pay here?", if (t.sixToFive) "6 to 5" else "3 to 2", listOf(if (t.sixToFive) "3 to 2" else "6 to 5", "2 to 1", "Even money"))
            7 -> factual("Dealer", "How much are you betting this hand?", "\$${t.yourBet}", listOf("\$${t.yourBet + 10}", "\$${(t.yourBet - 5).coerceAtLeast(5)}", "\$${t.yourBet * 2}").distinct().filter { it != "\$${t.yourBet}" })
            else -> {
                val a = 2 + rnd.nextInt(8); val b = 2 + rnd.nextInt(8)
                factual(name, "Quick, what's $a times $b? I'm splitting the tip.", "${a * b}", listOf("${a * b + 1}", "${a * b - 2}", "${a * b + a}", "${a * (b + 1)}").distinct().filter { it != "${a * b}" })
            }
        }.also { opts.clear() }
    }
}
