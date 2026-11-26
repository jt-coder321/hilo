package com.hilotrainer.engine

/** One finished run, as the rank and goals see it. */
data class RunRecord(
    val ante: Int, val cleared: Int, val bestHand: Int, val won: Boolean, val deck: StartDeck,
    val maxPerks: Int, val skips: Int, val goalsDone: Int, val hard: Boolean,
)

/** What the player carries from run to run: a rank that unlocks decks, goals that pay rank points, and a perk collection. */
object RunMeta {
    val RANK_XP = intArrayOf(0, 60, 150, 280, 450, 680, 980, 1350, 1800, 2400)
    val TITLES = listOf("Rookie", "Regular", "Hustler", "Card Shark", "High Roller", "Pit Legend", "Table Boss", "House Rival", "Casino King", "Legend")

    fun xpFor(r: RunRecord): Int {
        val base = r.cleared * 10 + (r.ante - 1) * 25 + (if (r.won) 250 else 0) + (r.bestHand / 100).coerceAtMost(100)
        return if (r.hard) base * 2 else base
    }

    fun rankOf(xp: Int): Int = RANK_XP.indexOfLast { xp >= it }.coerceAtLeast(0)
    fun title(rank: Int): String = TITLES[rank.coerceIn(0, TITLES.lastIndex)]
    /** Points into the current rank and points needed for the next, or null at the top rank. */
    fun progress(xp: Int): Pair<Int, Int>? {
        val r = rankOf(xp)
        if (r >= RANK_XP.lastIndex) return null
        return (xp - RANK_XP[r]) to (RANK_XP[r + 1] - RANK_XP[r])
    }

    class Goal(val id: String, val label: String, val desc: String, val xp: Int, val test: (RunRecord, Int, Int) -> Boolean)

    /** Permanent goals. The tests get the run, the number of perks found in the collection, and total bonus goals finished. */
    val GOALS: List<Goal> = listOf(
        Goal("first", "First Blood", "Clear a blind.", 10) { r, _, _ -> r.cleared >= 1 },
        Goal("a2", "Second Room", "Reach Ante 2.", 15) { r, _, _ -> r.ante >= 2 },
        Goal("a3", "Getting Serious", "Reach Ante 3.", 25) { r, _, _ -> r.ante >= 3 },
        Goal("a4", "Deep Pockets", "Reach Ante 4.", 40) { r, _, _ -> r.ante >= 4 },
        Goal("a5", "Almost There", "Reach Ante 5.", 60) { r, _, _ -> r.ante >= 5 },
        Goal("win", "Beat the House", "Win a run.", 150) { r, _, _ -> r.won },
        Goal("big1", "Four Digits", "Score one hand worth 1,000.", 25) { r, _, _ -> r.bestHand >= 1_000 },
        Goal("big2", "Five Digits", "Score one hand worth 10,000.", 50) { r, _, _ -> r.bestHand >= 10_000 },
        Goal("big3", "Six Digits", "Score one hand worth 100,000.", 120) { r, _, _ -> r.bestHand >= 100_000 },
        Goal("full", "Full House", "Hold 5 perks at once.", 20) { r, _, _ -> r.maxPerks >= 5 },
        Goal("skipper", "Shortcut", "Skip 3 blinds in one run.", 25) { r, _, _ -> r.skips >= 3 },
        Goal("goals", "Overachiever", "Finish 10 bonus goals, over any number of runs.", 40) { _, _, g -> g >= 10 },
        Goal("col20", "Collector", "Find 20 perks.", 40) { _, c, _ -> c >= 20 },
        Goal("col40", "Curator", "Find 40 perks.", 80) { _, c, _ -> c >= 40 },
        Goal("hardwin", "Gold Stake", "Win a run on Hard.", 250) { r, _, _ -> r.won && r.hard },
    )
}
