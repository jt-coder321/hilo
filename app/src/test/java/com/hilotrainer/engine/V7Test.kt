package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class V7Test {
    private fun toPlayer(g: BlackjackGame, bet: Int = 10): Boolean {
        repeat(200) {
            g.deal(bet)
            if (g.state().phase == Phase.INSURANCE) g.insurance(false)
            if (g.state().phase == Phase.PLAYER) return true
        }
        return false
    }

    @Test fun flashedHoleCardCountsOnce() {
        val g = BlackjackGame(Rules(), 100_000, Random(4))
        assertTrue(toPlayer(g))
        val before = g.state().runningCount
        val hole = g.flashHole()
        assertNotNull(hole)
        val tag = CountSystem.HI_LO.tag(hole!!)
        assertEquals(before + tag, g.state().runningCount)
        assertNull("only once per round", g.flashHole())
        // Finish the round: when the hole card flips it must not be counted again.
        while (g.state().phase == Phase.PLAYER) g.act(Action.STAND)
        val rest = g.state().dealerCards.drop(2).sumOf { CountSystem.HI_LO.tag(it) }
        assertEquals(before + tag + rest, g.state().runningCount)
        val flip = g.state().events.first { it.kind == EventKind.FLIP }
        assertFalse(flip.counted)
    }

    @Test fun normalHoleCardStillCounts() {
        val g = BlackjackGame(Rules(), 100_000, Random(4))
        assertTrue(toPlayer(g))
        val before = g.state().runningCount
        while (g.state().phase == Phase.PLAYER) g.act(Action.STAND)
        val d = g.state().dealerCards
        assertEquals(before + d.drop(1).sumOf { CountSystem.HI_LO.tag(it) }, g.state().runningCount)
        assertTrue(g.state().events.first { it.kind == EventKind.FLIP }.counted)
    }

    @Test fun burnCardAfterShuffleIsNotCounted() {
        val g = BlackjackGame(Rules(decks = 1, penetration = 0.65), 100_000, Random(2))
        var burned = false
        repeat(60) {
            g.deal(10)
            if (g.state().phase == Phase.INSURANCE) g.insurance(false)
            while (g.state().phase == Phase.PLAYER) g.act(Action.STAND)
            val s = g.state()
            if (s.shuffledThisRound) {
                assertTrue(g.burnedThisRound)
                // One card more than were dealt has left the shoe, and the count only covers what was seen.
                val seen = s.events.count { it.kind == EventKind.DEAL }
                assertEquals(seen + 1, s.cardsDealt)
                burned = true
            } else assertFalse(g.burnedThisRound)
        }
        assertTrue(burned)
    }

    @Test fun tipsAndSeats() {
        val g = BlackjackGame(Rules(), 1000, Random(1), botCount = 2)
        assertTrue(g.tip(5))
        assertEquals(995, g.state().bankroll)
        assertFalse(g.tip(5000))
        assertFalse(g.tip(0))
        g.changeSeats(3)
        assertEquals(3, g.seats)
        g.changeSeats(1)
        assertEquals(1, g.seats)
        assertTrue(toPlayer(g))
        assertFalse("no tipping mid-hand", g.tip(5))
    }

    @Test fun colorUpAndDealers() {
        assertEquals(listOf(500 to 2, 100 to 2, 25 to 1, 5 to 1, 1 to 2), ColorUp.breakdown(1232))
        assertEquals("2 x \$500, 2 x \$100, 1 x \$25, 1 x \$5, 2 x \$1", ColorUp.describe(1232))
        assertEquals(emptyList<Pair<Int, Int>>(), ColorUp.breakdown(0))
        val r = Random(3)
        var d: DealerProfile? = null
        repeat(20) { val n = Dealers.next(d, r); assertTrue(n != d); d = n }
        assertEquals("Dealer busts.", DealerCalls.result(listOf(Card(Rank.TEN, Suit.SPADES), Card(Rank.SIX, Suit.HEARTS), Card(Rank.NINE, Suit.CLUBS))))
        assertEquals("Dealer has blackjack.", DealerCalls.result(listOf(Card(Rank.ACE, Suit.SPADES), Card(Rank.KING, Suit.HEARTS))))
        val names = mutableListOf<String>()
        repeat(Seats.NAMES.size) { names += Seats.pick(names, r) }
        assertEquals(Seats.NAMES.size, names.toSet().size)
    }
}
