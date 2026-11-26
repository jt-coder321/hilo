package com.hilotrainer.engine

import kotlin.random.Random

/** Just Play only. A rival who keeps score against you, and short story nights with goals. None of it touches training. */
object Rival {
    /** Cast members who can become your rival. */
    val ELIGIBLE = setOf("Reggie", "Savannah", "Bruno", "Marco", "Victor", "Big Lou")

    /** How a hand scores against another: a bust is worst, a blackjack beats any 21. */
    fun score(h: Hand): Int = when { h.isBust -> -1; h.isBlackjack -> 100; else -> h.total }

    /** Your best hand against theirs: 1 you win, -1 they win, 0 push. */
    fun compare(mine: List<Hand>, theirs: Hand): Int {
        val a = mine.maxOfOrNull { score(it) } ?: return 0
        val b = score(theirs)
        return a.compareTo(b).coerceIn(-1, 1)
    }

    fun scoreText(you: Int, them: Int, who: String): String = when {
        you > them -> "You lead $who $you to $them"
        them > you -> "$who leads $them to $you"
        else -> "You and $who are level at $you"
    }

    fun make(seat: Int, seatId: Long, name: String, min: Int, rnd: Random): Dare {
        val stake = min * listOf(3, 5, 8)[rnd.nextInt(3)]
        return Dare(seat, name, stake, challenge[rnd.nextInt(challenge.size)].replace("\$STAKE", "\$$stake"), duel = true, seatId = seatId)
    }

    val challenge = listOf(
        "Head to head, you and me. \$STAKE says my hand beats yours.",
        "Let's make it personal. Best hand wins \$STAKE.",
        "You and me, this hand. \$STAKE. Unless you're scared.",
        "I'm not playing the dealer tonight, I'm playing you. \$STAKE.",
        "Whoever ends closer to twenty one takes \$STAKE. Deal?",
        "Think you can outplay me? \$STAKE on it.",
        "Rematch. \$STAKE, my hand against yours.",
        "Same bet as always. \$STAKE, and I'm winning this one.",
    )
    val accepted = listOf("Now we're talking.", "Good. I like a challenger.", "Let's see what you've got.", "Don't blink.", "This is going to hurt. For you.")
    val declined = listOf("Playing it safe again. Typical.", "Fine. Hide.", "I'll ask again. I always do.", "Next time, then.")
    val iWin = listOf(
        "Pay up, \$STAKE. \$SCORE.", "Too easy. \$STAKE, please. \$SCORE.", "That's \$STAKE. \$SCORE, if you're counting.",
        "Better luck next time. \$SCORE.", "I told you. \$SCORE.", "Thanks for the \$STAKE. \$SCORE.", "Beaten fair and square. \$SCORE.", "Add it to my tab. \$SCORE.",
    )
    val iLose = listOf(
        "Fine. \$STAKE is yours. \$SCORE.", "Lucky. Take your \$STAKE. \$SCORE.", "Don't get used to it. \$SCORE.", "That was a fluke. \$SCORE.",
        "Okay, okay. \$STAKE. \$SCORE.", "I'll win it back. \$SCORE.", "Enjoy it while it lasts. \$SCORE.", "Well played. Barely. \$SCORE.",
    )
    val push = listOf("A tie. That doesn't count.", "Dead even. We'll do it again.", "Nobody pays. Annoying.")
    val taunts = listOf(
        "You're due a loss.", "I've seen better play from the dealer's shoe.", "Keep that bet small. You'll need the chips.", "I'm just warming up.",
        "Nice hand. It won't last.", "The score is about to change.", "I can wait. I have all night.", "Are you always this careful?",
        "You know I'm better than you, right?", "One of these hands, I'll bury you.",
    )
}

/** What a story night asks for. */
enum class Goal { PLAY, WINS, NET, STREAK, DOUBLE }

/** One evening of the story. [target] for NET is in table minimums. [limit] is the most hands you get, 0 for no limit. */
data class Chapter(val title: String, val intro: String, val outro: String, val goal: Goal, val target: Int, val limit: Int, val rewardUnits: Int)

/** A night in progress. Everything is a small number so it saves easily. */
data class NightRun(
    val chapter: Int, val startBank: Int, val hands: Int = 0, val wins: Int = 0, val streak: Int = 0, val best: Int = 0, val doubles: Int = 0,
) {
    fun encode() = listOf(chapter, startBank, hands, wins, streak, best, doubles).joinToString(",")

    companion object {
        fun decode(s: String?): NightRun? {
            val p = s?.split(",")?.map { it.toIntOrNull() ?: return null } ?: return null
            if (p.size != 7) return null
            return NightRun(p[0], p[1], p[2], p[3], p[4], p[5], p[6])
        }
    }
}

enum class NightState { ACTIVE, DONE, FAILED }

object Story {
    const val NAME = "The Long Weekend"

    val chapters = listOf(
        Chapter("Check In", "Welcome to the weekend. Settle in and play a few hands to get a feel for the table.", "Settled in. The weekend begins.", Goal.PLAY, 5, 0, 10),
        Chapter("Warm Up", "The crowd is waking up. Turn a small profit before the night gets going.", "Nicely done. The pit boss noticed.", Goal.NET, 8, 15, 12),
        Chapter("On a Roll", "Friday night, loud and lucky. String three wins together.", "Three in a row. The table is buzzing.", Goal.STREAK, 3, 20, 15),
        Chapter("Double Trouble", "A man at the end of the table says you never double down. Prove him wrong.", "Doubled and won. He bought you a drink.", Goal.DOUBLE, 1, 25, 18),
        Chapter("Big Night", "Saturday. The high rollers are in. Come out well ahead.", "A big night. They will remember your name.", Goal.NET, 20, 30, 25),
        Chapter("Last Call", "Sunday, last hands of the weekend. Win eight of sixteen and go home a legend.", "The weekend is yours. Time to head home.", Goal.WINS, 8, 16, 40),
    )

    /** What this night asks, in words. */
    fun describe(c: Chapter, unit: Int): String {
        val lim = if (c.limit > 0) " in ${c.limit} hands" else ""
        return when (c.goal) {
            Goal.PLAY -> "Play ${c.target} hands"
            Goal.WINS -> "Win ${c.target} hands$lim"
            Goal.NET -> "Finish ${"$"}${c.target * unit} ahead$lim"
            Goal.STREAK -> "Win ${c.target} hands in a row$lim"
            Goal.DOUBLE -> "Win a double down$lim"
        }
    }

    /** Progress toward the goal as (now, target). */
    fun progress(c: Chapter, r: NightRun, bank: Int, unit: Int): Pair<Int, Int> = when (c.goal) {
        Goal.PLAY -> r.hands to c.target
        Goal.WINS -> r.wins to c.target
        Goal.NET -> (bank - r.startBank).coerceAtLeast(0) to c.target * unit
        Goal.STREAK -> r.best to c.target
        Goal.DOUBLE -> r.doubles to c.target
    }

    fun state(c: Chapter, r: NightRun, bank: Int, unit: Int): NightState {
        val (now, target) = progress(c, r, bank, unit)
        return when {
            now >= target -> NightState.DONE
            c.limit > 0 && r.hands >= c.limit -> NightState.FAILED
            else -> NightState.ACTIVE
        }
    }

    /** A short line for the badge on the table. */
    fun badge(c: Chapter, r: NightRun, bank: Int, unit: Int, index: Int): String {
        val (now, target) = progress(c, r, bank, unit)
        val left = if (c.limit > 0) ", ${(c.limit - r.hands).coerceAtLeast(0)} left" else ""
        return if (c.goal == Goal.NET) "Night ${index + 1}: \$$now of \$$target$left" else "Night ${index + 1}: $now of $target$left"
    }

    /** The run after one more hand. [handNet] is your net on the hand, [doubledWin] a won double down. */
    fun after(r: NightRun, handNet: Int, wins: Int, doubledWin: Boolean): NightRun {
        val streak = when { handNet > 0 -> r.streak + 1; handNet < 0 -> 0; else -> r.streak }
        return r.copy(hands = r.hands + 1, wins = r.wins + wins, streak = streak, best = maxOf(r.best, streak), doubles = r.doubles + if (doubledWin) 1 else 0)
    }
}
