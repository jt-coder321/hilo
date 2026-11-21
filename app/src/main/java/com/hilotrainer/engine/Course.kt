package com.hilotrainer.engine

import kotlin.math.floor
import kotlin.random.Random

/** The guided course: lessons unlock one at a time as you pass each one. */
object Course {
    enum class Kind { QUIZ, CASINO_TEST, TABLE }

    data class Lesson(val title: String, val teach: String, val kind: Kind = Kind.QUIZ)

    data class Question(
        val prompt: String,
        val cards: List<Card> = emptyList(),
        val dealer: Card? = null,
        val trueCount: Double? = null,
        val options: List<String>,
        val answer: Int,
        val explain: String,
    )

    const val QUESTIONS = 10
    const val PASS = 8

    val LESSONS = listOf(
        Lesson("Card values", "Every card gets a tag. Add the tags as cards appear and you have the running count. Learn the tags until they are instant."),
        Lesson("Hard totals", "Basic strategy is the best play for every hand when you ignore the count. Start with hard totals, hands without an ace counted as 11. Stand on 12 to 16 against a dealer 2 to 6 (except 12 against 2 or 3), hit them against 7 or higher."),
        Lesson("Soft totals", "A soft hand has an ace counted as 11, so one hit can't bust you. Double soft hands against weak dealer cards, and hit soft 18 against a 9, 10 or ace."),
        Lesson("Pairs", "Always split aces and 8s. Never split 5s or 10s. Other pairs split against weaker dealer cards."),
        Lesson("Running count", "Keep one number in your head from the shuffle on. Look for cards that cancel: a 5 and a king together are 0."),
        Lesson("True count", "Divide the running count by the decks left in the shoe. +6 with 3 decks left is +2. Players estimate decks left from the discard tray."),
        Lesson("Betting with the count", "Bet the minimum when the count is low and raise as it climbs. The trainer uses your bet ramp from Settings."),
        Lesson("Insurance and the top count plays", "The count changes a few plays. Insurance at true count +3 is the most valuable. 16 vs 10: stand at 0 or higher. 15 vs 10: stand at +4. 12 vs 3: stand at +2."),
        Lesson("Casino test", "Count down a single deck with a few cards held back, fast and accurately. Pass Bronze (60 seconds) in the Casino test to complete this lesson.", Kind.CASINO_TEST),
        Lesson("Table session", "Put it together at the table with the count hidden. Complete 20 count checks at 80% or better (lifetime) to finish the course.", Kind.TABLE),
    )

    private fun card(r: Rank, rnd: Random) = Card(r, Suit.entries[rnd.nextInt(4)])
    private val tens = listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING)
    private fun rankOf(v: Int, rnd: Random) = if (v == 10) tens[rnd.nextInt(4)] else Rank.entries.first { it.value == v && it != Rank.JACK }
    private fun upCard(rnd: Random): Card {
        val v = rnd.nextInt(2, 12)
        return card(if (v == 11) Rank.ACE else rankOf(v, rnd), rnd)
    }
    private fun upLabel(c: Card) = if (c.rank.isAce) "A" else c.upValue.toString()

    /** Shuffle in the right answer among [wrong] options. */
    private fun choices(right: String, wrong: List<String>, rnd: Random): Pair<List<String>, Int> {
        val opts = (wrong.distinct().filter { it != right }.take(3) + right).shuffled(rnd)
        return opts to opts.indexOf(right)
    }

    fun questions(lesson: Int, rnd: Random, rules: Rules, system: CountSystem, ramp: List<Int>): List<Question> =
        List(QUESTIONS) { question(lesson, rnd, rules, system, ramp) }

    fun question(lesson: Int, rnd: Random, rules: Rules, system: CountSystem, ramp: List<Int>): Question {
        val strategyOpts = Options(canDouble = true, canSplit = true, canSurrender = false)
        fun strategyQ(hand: Hand, up: Card, name: String): Question {
            val a = BasicStrategy.basic(hand, up, rules, strategyOpts)
            val labels = buildList { add("Hit"); add("Stand"); add("Double"); if (hand.isPair) add("Split") }
            return Question("$name vs dealer ${upLabel(up)}. Your play?", hand.cards, up, null, labels, labels.indexOf(a.action.label), a.reason)
        }
        return when (lesson) {
            0 -> {
                val c = card(Rank.entries[rnd.nextInt(Rank.entries.size)], rnd)
                val tag = system.tag(c)
                val range = if (system == CountSystem.ZEN) -2..2 else -1..1
                val labels = range.map { if (it > 0) "+$it" else "$it" }
                Question("What is this card worth in ${system.label}?", listOf(c), null, null, labels, labels.indexOf(if (tag > 0) "+$tag" else "$tag"), system.valuesText)
            }
            1 -> {
                val t = rnd.nextInt(8, 18)
                var a: Int; var b: Int
                do { a = rnd.nextInt(2, 11); b = t - a } while (b !in 2..10 || a == b)
                strategyQ(Hand(listOf(card(rankOf(a, rnd), rnd), card(rankOf(b, rnd), rnd))), upCard(rnd), "Hard $t")
            }
            2 -> {
                val other = rnd.nextInt(2, 10)
                strategyQ(Hand(listOf(card(Rank.ACE, rnd), card(rankOf(other, rnd), rnd))), upCard(rnd), "Soft ${11 + other}")
            }
            3 -> {
                val v = rnd.nextInt(2, 12)
                val r = if (v == 11) Rank.ACE else rankOf(v, rnd)
                strategyQ(Hand(listOf(card(r, rnd), card(r, rnd))), upCard(rnd), "Pair of ${if (v == 11) "aces" else "${v}s"}")
            }
            4 -> {
                val cs = List(6) { card(Rank.entries[rnd.nextInt(Rank.entries.size)], rnd) }
                val rc = cs.sumOf { system.tag(it) }
                val right = if (rc > 0) "+$rc" else "$rc"
                val wrong = listOf(rc - 1, rc + 1, rc + 2, rc - 2).map { if (it > 0) "+$it" else "$it" }.shuffled(rnd)
                val (opts, ans) = choices(right, wrong, rnd)
                Question("Running count of these 6 cards, starting from 0?", cs, null, null, opts, ans, "Tags: " + cs.joinToString(" ") { val t = system.tag(it); if (t > 0) "+$t" else "$t" })
            }
            5 -> {
                val decksLeft = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0)[rnd.nextInt(7)]
                val tc = rnd.nextInt(-3, 6)
                val rc = (tc * decksLeft).toInt()
                val exact = rc / decksLeft
                val right = floor(exact).toInt()
                val fmt = { n: Int -> if (n > 0) "+$n" else "$n" }
                val (opts, ans) = choices(fmt(right), listOf(fmt(right + 1), fmt(right - 1), fmt(rc)).shuffled(rnd), rnd)
                Question("Running count ${fmt(rc)} with ${decksLeft.toString().removeSuffix(".0")} decks left. True count (rounded down)?", emptyList(), null, null, opts, ans,
                    "${fmt(rc)} / ${decksLeft.toString().removeSuffix(".0")} = ${"%.1f".format(exact)}, rounded down to ${fmt(right)}.")
            }
            6 -> {
                val tc = rnd.nextInt(-1, 7)
                val units = TrueCount.suggestedUnits(tc, ramp)
                val all = ramp.distinct().map { "$it unit${if (it == 1) "" else "s"}" }
                val right = "$units unit${if (units == 1) "" else "s"}"
                val (opts, ans) = choices(right, all.shuffled(rnd), rnd)
                Question("True count ${if (tc > 0) "+$tc" else "$tc"}. What do you bet on your ramp?", emptyList(), null, tc.toDouble(), opts, ans, "Your ramp: " + TrueCount.describeRamp(ramp) + ".")
            }
            7 -> {
                // Insurance, or one of the most valuable Illustrious 18 plays.
                val tc = rnd.nextInt(-2, 7).toDouble()
                when (rnd.nextInt(4)) {
                    0 -> {
                        val yes = BasicStrategy.shouldInsure(tc)
                        Question("Dealer shows an ace. True count ${BlackjackGame.fmt(tc)}. Take insurance?", emptyList(), card(Rank.ACE, rnd), tc, listOf("Insure", "No insurance"), if (yes) 0 else 1, "Insure at true count +3 or higher.")
                    }
                    else -> {
                        val spots = listOf(Triple(10, 6, 10), Triple(10, 5, 10), Triple(9, 3, 3))
                        val (a, b, u) = spots[rnd.nextInt(spots.size)]
                        val hand = Hand(listOf(card(rankOf(a, rnd), rnd), card(rankOf(b, rnd), rnd)))
                        val up = card(rankOf(u, rnd), rnd)
                        val adv = BasicStrategy.advise(hand, up, rules, Options(canDouble = false, canSplit = false, canSurrender = false), tc)
                        val labels = listOf("Hit", "Stand")
                        Question("Hard ${a + b} vs ${upLabel(up)} at true count ${BlackjackGame.fmt(tc)}. Your play?", hand.cards, up, tc, labels, labels.indexOf(adv.action.label), adv.reason)
                    }
                }
            }
            else -> Question("", emptyList(), null, null, listOf("OK"), 0, "")
        }
    }
}
