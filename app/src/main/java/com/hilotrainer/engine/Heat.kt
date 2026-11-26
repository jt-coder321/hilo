package com.hilotrainer.engine

import kotlin.random.Random

/**
 * Which way the table has been running lately. It is read from real results only (how many hands beat the dealer
 * over the last few rounds) and it never feeds back into the cards. It is a mood, not an edge.
 */
class TableStreak {
    private val recent = ArrayDeque<Int>()

    /** One round: positive if more hands beat the dealer than lost to it, negative if fewer, zero if even. */
    fun record(wins: Int, losses: Int) {
        recent.addLast((wins - losses).coerceIn(-1, 1))
        while (recent.size > WINDOW) recent.removeFirst()
    }

    fun clear() = recent.clear()
    val rounds: Int get() = recent.size
    val sum: Int get() = recent.sum()

    /** 2 hot, 1 warm, 0 even, -1 cool, -2 cold. Needs a few rounds of evidence before it says anything. */
    val level: Int get() = when {
        rounds < 3 -> 0
        sum >= 4 -> 2
        sum >= 2 -> 1
        sum <= -4 -> -2
        sum <= -2 -> -1
        else -> 0
    }

    companion object {
        const val WINDOW = 6
        fun label(level: Int) = when (level) { 2 -> "HOT"; 1 -> "WARM"; -1 -> "COOL"; -2 -> "COLD"; else -> "EVEN" }
        fun note(level: Int) = when (level) {
            2 -> "Players have been beating the dealer. Streaks do not change the next card."
            1 -> "A friendly run for the table. It does not change the next card."
            -1 -> "The dealer has had a little the better of it. It does not change the next card."
            -2 -> "The dealer has been running over the table. It does not change the next card."
            else -> "Nothing unusual. Only the count tells you about the shoe."
        }
        val hotTalk = listOf("This table is on fire.", "Nobody leave. We're hot.", "Dealer can't buy a hand.", "Ride it while it lasts.", "The cards are smiling tonight.", "I've never seen a table this warm.")
        val coldTalk = listOf("This table is ice cold.", "Dealer's been eating us alive.", "Somebody change the luck.", "Cold as a meat locker over here.", "We can't catch a break.", "Ugly run, folks.")
        val warmTalk = listOf("Table's feeling friendly.", "That's a few in a row for us.")
        val coolTalk = listOf("Table's gone a little quiet.", "Dealer's had the last few.")
    }
}

/** What the pit boss does about the player. */
enum class PitMove { NONE, WATCH, RELAX, FLAT_BET, SHUFFLE_UP, ASKED_TO_LEAVE, RELEASE }

/**
 * How much attention the player is drawing: big bets, big wins and sudden jumps heat it up, small bets, losses and
 * sitting out cool it down. It is casual flavor only. It never touches training stats.
 */
class PlayerHeat {
    var level = 0; private set
    var watched = false; private set
    /** Rounds left in which only small bets are allowed. */
    var flatLeft = 0; private set
    /** Rounds left in which the player may only watch. */
    var cooloff = 0; private set
    private var sinceWatch = 0
    private var winRun = 0
    private val stakes = ArrayDeque<Int>()

    internal fun force(l: Int) { level = l.coerceIn(0, MAX) }

    fun reset() { level = 0; watched = false; flatLeft = 0; cooloff = 0; sinceWatch = 0; winRun = 0; stakes.clear() }

    /** Tipping the dealer is acting the part. */
    fun tip() { level = (level - 2).coerceAtLeast(0) }

    /** The heat one round adds or removes. Pure, so tests can check it. */
    fun delta(stake: Int, net: Int, unit: Int): Int {
        if (stake <= 0) return -1
        val units = stake / unit.coerceAtLeast(1)
        var d = 0
        d += when { units >= 50 -> 2; units >= 25 -> 1; else -> 0 }
        if (stakes.size >= 2 && units >= 10 && stake >= stakes.average() * 3) d += 1
        d += when { net >= 30 * unit -> 2; net >= 10 * unit -> 1; else -> 0 }
        if (net > 0 && winRun >= 2) d += 1
        if (units <= 5) d -= 1
        if (net < 0) d -= 1
        return d
    }

    /** Called after every round with the player's stake (0 if watching) and net. Returns what the pit boss does. */
    fun afterRound(stake: Int, net: Int, unit: Int, rnd: Random): PitMove {
        val d = delta(stake, net, unit)
        if (stake > 0) { stakes.addLast(stake); while (stakes.size > 4) stakes.removeFirst(); winRun = if (net > 0) winRun + 1 else 0 }
        level = (level + d).coerceIn(0, MAX)
        if (cooloff > 0) {
            cooloff--
            level = (level - 1).coerceAtLeast(0)
            return if (cooloff == 0) PitMove.RELEASE else PitMove.NONE
        }
        if (flatLeft > 0) {
            flatLeft--
            if (flatLeft == 0) return PitMove.RELEASE
        }
        if (!watched) {
            if (level >= WATCH) { watched = true; sinceWatch = 0; return PitMove.WATCH }
            return PitMove.NONE
        }
        sinceWatch++
        if (level <= 1) { watched = false; return PitMove.RELAX }
        if (sinceWatch >= 2 && level >= ACT) {
            sinceWatch = 0
            if (level >= LEAVE) { cooloff = COOLOFF; level = WATCH; flatLeft = 0; return PitMove.ASKED_TO_LEAVE }
            level -= 2
            return if (rnd.nextBoolean()) { flatLeft = FLAT_ROUNDS; PitMove.FLAT_BET } else PitMove.SHUFFLE_UP
        }
        return PitMove.NONE
    }

    companion object {
        const val MAX = 10
        const val WATCH = 4
        const val ACT = 7
        const val LEAVE = 9
        const val COOLOFF = 3
        const val FLAT_ROUNDS = 4
        /** The biggest bet allowed while the pit boss has you on flat bets. */
        fun flatCap(unit: Int) = unit * 5
    }
}
