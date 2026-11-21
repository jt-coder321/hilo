package com.hilotrainer.engine

enum class Action(val label: String) {
    HIT("Hit"), STAND("Stand"), DOUBLE("Double"), SPLIT("Split"), SURRENDER("Surrender")
}

data class Rules(
    val decks: Int = 6,
    val dealerHitsSoft17: Boolean = true,
    val doubleAfterSplit: Boolean = true,
    val surrenderAllowed: Boolean = false,
    val maxHands: Int = 4,
    val penetration: Double = 0.75,
    /** Blackjack pays 6 to 5 instead of 3 to 2. */
    val sixToFive: Boolean = false,
)

/** What the player may legally do with the current hand. */
data class Options(
    val canDouble: Boolean,
    val canSplit: Boolean,
    val canSurrender: Boolean,
)

data class Advice(
    val action: Action,
    val reason: String,
    /** True when the count changed the play away from basic strategy. */
    val isDeviation: Boolean = false,
)

/**
 * Multi-deck (4 to 8 decks) basic strategy, following the Wizard of Odds
 * 4 to 8 deck chart, including its dealer-hits-soft-17 changes.
 *
 * When a true count is passed, the Hi-Lo Illustrious 18 and Fab 4 index plays
 * (six deck values as published by blackjackinfo.com) override basic strategy.
 */
object BasicStrategy {

    /** An index play: at or above [index] do [atOrAbove], below it do [below]. */
    data class Index(
        val label: String,
        val index: Int,
        val atOrAbove: Action,
        val below: Action,
    )

    fun advise(
        hand: Hand,
        dealerUp: Card,
        rules: Rules,
        options: Options,
        trueCount: Double? = null,
        /** Also use the extended index plays in [EXTENDED], beyond the Illustrious 18 and Fab 4. */
        full: Boolean = false,
    ): Advice {
        val basic = basic(hand, dealerUp, rules, options)
        if (trueCount == null) return basic
        val idx = indexFor(hand, dealerUp, rules, options, full) ?: return basic
        val action = if (trueCount >= idx.index) idx.atOrAbove else idx.below
        val tc = if (idx.index > 0) "+${idx.index}" else idx.index.toString()
        val side = if (trueCount >= idx.index) "at or above" else "below"
        val dev = action != basic.action
        return Advice(
            action,
            "${idx.label}: ${action.label.lowercase()} $side true count $tc",
            isDeviation = dev,
        )
    }

    /** The Illustrious 18 or Fab 4 entry that applies to this decision, if any. */
    fun indexFor(hand: Hand, dealerUp: Card, rules: Rules, options: Options, full: Boolean = false): Index? {
        val up = dealerUp.upValue
        val t = hand.total
        val hard = !hand.isSoft
        val pairOf10 = hand.isPair && hand.cards[0].rank.value == 10
        if (full) extendedFor(hand, dealerUp, rules, options)?.let { return it }

        // Fab 4 surrenders (only when surrender is possible on this hand).
        if (options.canSurrender && rules.surrenderAllowed && hard && !hand.isPair) {
            when {
                t == 14 && up == 10 -> return Index("14 vs 10", 3, Action.SURRENDER, Action.HIT)
                t == 15 && up == 10 -> return Index("15 vs 10", 0, Action.SURRENDER, Action.HIT)
                t == 15 && up == 9 -> return Index("15 vs 9", 2, Action.SURRENDER, Action.HIT)
                // Under H17 basic strategy already surrenders 15 vs A.
                t == 15 && up == 11 && !rules.dealerHitsSoft17 ->
                    return Index("15 vs A", 1, Action.SURRENDER, Action.HIT)
            }
        }
        // If basic strategy surrenders, keep it.
        if (basic(hand, dealerUp, rules, options).action == Action.SURRENDER) return null

        if (pairOf10 && options.canSplit) {
            if (up == 5) return Index("10,10 vs 5", 5, Action.SPLIT, Action.STAND)
            if (up == 6) return Index("10,10 vs 6", 4, Action.SPLIT, Action.STAND)
        }
        // A pair that basic strategy splits is not covered by the hard total indices.
        if (hand.isPair && options.canSplit && basic(hand, dealerUp, rules, options).action == Action.SPLIT) return null
        if (!hard) return null

        if (options.canDouble) {
            when {
                t == 10 && up == 10 -> return Index("10 vs 10", 4, Action.DOUBLE, Action.HIT)
                t == 10 && up == 11 -> return Index("10 vs A", 4, Action.DOUBLE, Action.HIT)
                t == 11 && up == 11 && !rules.dealerHitsSoft17 ->
                    return Index("11 vs A", 1, Action.DOUBLE, Action.HIT)
                t == 9 && up == 2 -> return Index("9 vs 2", 1, Action.DOUBLE, Action.HIT)
                t == 9 && up == 7 -> return Index("9 vs 7", 3, Action.DOUBLE, Action.HIT)
            }
        }
        return when {
            t == 16 && up == 10 -> Index("16 vs 10", 0, Action.STAND, Action.HIT)
            t == 15 && up == 10 -> Index("15 vs 10", 4, Action.STAND, Action.HIT)
            t == 16 && up == 9 -> Index("16 vs 9", 5, Action.STAND, Action.HIT)
            t == 12 && up == 2 -> Index("12 vs 2", 3, Action.STAND, Action.HIT)
            t == 12 && up == 3 -> Index("12 vs 3", 2, Action.STAND, Action.HIT)
            t == 12 && up == 4 -> Index("12 vs 4", 0, Action.STAND, Action.HIT)
            t == 12 && up == 5 -> Index("12 vs 5", -2, Action.STAND, Action.HIT)
            t == 12 && up == 6 -> Index("12 vs 6", -1, Action.STAND, Action.HIT)
            t == 13 && up == 2 -> Index("13 vs 2", -1, Action.STAND, Action.HIT)
            t == 13 && up == 3 -> Index("13 vs 3", -2, Action.STAND, Action.HIT)
            else -> null
        }
    }

    /**
     * Extra six-deck Hi-Lo index plays beyond the Illustrious 18 and Fab 4. Each one is
     * listed with the same number in both the blackjackstrategy.io and blackjacktrainer.fyi
     * S17 deviation charts; plays the two charts disagree on are left out.
     */
    data class ExtendedEntry(val label: String, val index: Int, val atOrAbove: Action, val below: Action, val s17Only: Boolean)

    val EXTENDED = listOf(
        ExtendedEntry("8 vs 6", 2, Action.DOUBLE, Action.HIT, false),
        ExtendedEntry("A,6 vs 2", 1, Action.DOUBLE, Action.HIT, false),
        ExtendedEntry("A,8 vs 4", 3, Action.DOUBLE, Action.STAND, false),
        ExtendedEntry("A,8 vs 5", 1, Action.DOUBLE, Action.STAND, false),
        // With the dealer hitting soft 17, basic strategy already doubles A,8 vs 6.
        ExtendedEntry("A,8 vs 6", 1, Action.DOUBLE, Action.STAND, true),
        ExtendedEntry("A,9 vs 5", 5, Action.DOUBLE, Action.STAND, false),
        ExtendedEntry("9,9 vs 7", 3, Action.SPLIT, Action.STAND, false),
    )

    private fun extendedFor(hand: Hand, dealerUp: Card, rules: Rules, options: Options): Index? {
        if (hand.cards.size != 2) return null
        val up = dealerUp.upValue
        val upLabel = if (up == 11) "A" else up.toString()
        val label = when {
            hand.isPair && hand.cards[0].rank.value == 9 -> "9,9"
            hand.isPair -> return null
            hand.isSoft -> "A,${hand.total - 11}"
            else -> hand.total.toString()
        }
        val e = EXTENDED.firstOrNull { it.label == "$label vs $upLabel" } ?: return null
        if (e.s17Only && rules.dealerHitsSoft17) return null
        if (e.atOrAbove == Action.DOUBLE && !options.canDouble) return null
        if (e.atOrAbove == Action.SPLIT && !options.canSplit) return null
        return Index(e.label, e.index, e.atOrAbove, e.below)
    }

    fun basic(hand: Hand, dealerUp: Card, rules: Rules, options: Options): Advice {
        val up = dealerUp.upValue
        val upLabel = if (up == 11) "A" else up.toString()
        val total = hand.total
        val soft = hand.isSoft
        val h17 = rules.dealerHitsSoft17

        // 1. Surrender
        if (options.canSurrender && rules.surrenderAllowed) {
            val isEights = hand.isPair && hand.cards[0].rank.value == 8
            val hard = !soft
            val surrender = when {
                isEights -> h17 && up == 11
                hard && total == 16 && up in listOf(9, 10, 11) -> true
                hard && total == 15 && up == 10 -> true
                hard && total == 15 && up == 11 && h17 -> true
                hard && total == 17 && up == 11 && h17 -> true
                else -> false
            }
            if (surrender) return Advice(Action.SURRENDER, "Surrender ${describe(hand)} vs $upLabel")
        }

        // 2. Split
        if (options.canSplit && hand.isPair) {
            val p = hand.cards[0].rank.value
            val das = rules.doubleAfterSplit
            val split = when (p) {
                11 -> true
                8 -> true
                10, 5 -> false
                2, 3 -> up in 4..7 || (das && up in 2..3)
                4 -> das && up in 5..6
                6 -> up in 3..6 || (das && up == 2)
                7 -> up in 2..7
                9 -> up in 2..6 || up in 8..9
                else -> false
            }
            if (split) {
                val name = if (p == 11) "aces" else "${p}s"
                return Advice(Action.SPLIT, "Split $name vs $upLabel")
            }
        }

        // 3. Double, falling back when doubling is not allowed
        if (soft) {
            val doubleSoft = when (total) {
                13, 14 -> up in 5..6
                15, 16 -> up in 4..6
                17 -> up in 3..6
                18 -> up in 3..6 || (h17 && up == 2)
                19 -> h17 && up == 6
                else -> false
            }
            if (doubleSoft && options.canDouble) {
                return Advice(Action.DOUBLE, "Double soft $total vs $upLabel")
            }
            return when {
                total >= 19 -> Advice(Action.STAND, "Stand on soft $total")
                total == 18 -> if (up in 9..11) Advice(Action.HIT, "Hit soft 18 vs $upLabel")
                    else Advice(Action.STAND, "Stand on soft 18 vs $upLabel")
                else -> Advice(Action.HIT, "Hit soft $total")
            }
        }

        val doubleHard = when (total) {
            9 -> up in 3..6
            10 -> up in 2..9
            11 -> up in 2..10 || (h17 && up == 11)
            else -> false
        }
        if (doubleHard && options.canDouble) {
            return Advice(Action.DOUBLE, "Double hard $total vs $upLabel")
        }
        return when {
            total <= 11 -> Advice(Action.HIT, "Hit hard $total")
            total == 12 -> if (up in 4..6) Advice(Action.STAND, "Stand on 12 vs $upLabel")
                else Advice(Action.HIT, "Hit 12 vs $upLabel")
            total in 13..16 -> if (up in 2..6) Advice(Action.STAND, "Stand on $total vs $upLabel")
                else Advice(Action.HIT, "Hit $total vs $upLabel")
            else -> Advice(Action.STAND, "Stand on hard $total")
        }
    }

    /** Hi-Lo insurance index: insure when the true count is +3 or higher. */
    fun shouldInsure(trueCount: Double): Boolean = trueCount >= 3.0

    private fun describe(hand: Hand): String =
        if (hand.isPair && hand.cards[0].rank.value == 8) "8,8" else "hard ${hand.total}"
}
