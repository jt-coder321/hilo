package com.hilotrainer.engine

enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADES("♠", false),
    HEARTS("♥", true),
    DIAMONDS("♦", true),
    CLUBS("♣", false),
}

enum class Rank(val label: String, val value: Int) {
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5), SIX("6", 6),
    SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9), TEN("10", 10),
    JACK("J", 10), QUEEN("Q", 10), KING("K", 10), ACE("A", 11);

    val isAce: Boolean get() = this == ACE

    /** Hi-Lo tag: 2-6 are +1, 7-9 are 0, tens and aces are -1. */
    val hiLo: Int
        get() = when (value) {
            in 2..6 -> 1
            in 7..9 -> 0
            else -> -1
        }
}

data class Card(val rank: Rank, val suit: Suit) {
    val hiLo: Int get() = rank.hiLo
    override fun toString(): String = rank.label + suit.symbol
}

/** Dealer upcard as a strategy column: 2..10, with Ace = 11. */
val Card.upValue: Int get() = rank.value
