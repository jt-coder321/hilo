package com.hilotrainer.engine

data class Hand(
    val cards: List<Card> = emptyList(),
    val bet: Int = 0,
    val isSplitHand: Boolean = false,
    val isSplitAces: Boolean = false,
    val doubled: Boolean = false,
    val stood: Boolean = false,
    val surrendered: Boolean = false,
    /** Deal sequence number of each card, used by the UI to animate the deal in order. */
    val seqs: List<Int> = emptyList(),
) {
    val total: Int get() = handTotal(cards).first
    val isSoft: Boolean get() = handTotal(cards).second
    val isBust: Boolean get() = total > 21
    /** A natural: two-card 21 on an original (non-split) hand. */
    val isBlackjack: Boolean get() = cards.size == 2 && total == 21 && !isSplitHand
    val isPair: Boolean get() = cards.size == 2 && cards[0].rank.value == cards[1].rank.value
    val isDone: Boolean
        get() = stood || surrendered || isBust || doubled || total == 21 ||
            (isSplitAces && cards.size >= 2)

    fun add(card: Card, seq: Int = -1) = copy(cards = cards + card, seqs = seqs + seq)
}

/** Returns best total and whether that total is soft (an ace counted as 11). */
fun handTotal(cards: List<Card>): Pair<Int, Boolean> {
    var total = 0
    var aces = 0
    for (c in cards) {
        total += c.rank.value
        if (c.rank.isAce) aces++
    }
    while (total > 21 && aces > 0) {
        total -= 10
        aces--
    }
    return total to (aces > 0)
}
