package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class V6Test {
    private fun c(r: Rank, s: Suit = Suit.SPADES) = Card(r, s)

    @Test fun edgeAndKelly() {
        assertEquals(-0.005, Edge.atTrueCount(0.0), 1e-12)
        assertEquals(0.0, Edge.atTrueCount(1.0), 1e-12)
        assertEquals(0.01, Edge.atTrueCount(3.0), 1e-12)
        // Full Kelly on $10,000 at a 1% edge: 10000 * 0.01 / 1.3 = 76.9
        assertEquals(76.92, Kelly.bet(10_000, 3.0, 1.0), 0.01)
        assertEquals(38.46, Kelly.bet(10_000, 3.0, 0.5), 0.01)
        assertEquals(0.0, Kelly.bet(10_000, 1.0, 1.0), 1e-12)
        assertEquals(75, Kelly.chips(10_000, 3.0, 1.0))
        assertEquals(10, Kelly.chips(1_000, 2.0, 0.25))
    }

    @Test fun evTrackerSeparatesLuck() {
        val t = EvTracker()
        t.add(100, 3.0, 100)   // expected +1
        t.add(10, 0.0, -10)    // expected -0.05
        t.add(0, 5.0, 0)       // sat out, ignored
        assertEquals(2, t.hands)
        assertEquals(0.95, t.expected, 1e-9)
        assertEquals(90, t.actual)
        assertEquals(89.05, t.luck, 1e-9)
    }

    @Test fun dealerErrorsAreGradable() {
        val dealer = listOf(c(Rank.TEN), c(Rank.SEVEN))
        var wrongSeen = 0; var rightSeen = 0
        for (seed in 0 until 200) {
            val chk = DealerErrors.make(dealer, listOf(HandResult(Outcome.WIN, 20)), listOf(20), Random(seed)) ?: continue
            if (chk.wrong) {
                wrongSeen++
                if (chk.kind == DealerErrors.Kind.PAYOUT) { assertTrue(chk.shortBy > 0); assertFalse(chk.said.contains("$20.")) }
                else assertFalse(chk.said == "Dealer has 17.")
            } else {
                rightSeen++
                if (chk.kind == DealerErrors.Kind.PAYOUT) assertEquals("Paying you $20.", chk.said)
                else assertEquals("Dealer has 17.", chk.said)
            }
        }
        assertTrue(wrongSeen > 50 && rightSeen > 50)
        // A blackjack paid at even money is a mistake worth catching.
        var bj: DealerErrors.Check? = null
        var seed = 0
        while (bj == null || !bj.wrong || bj.kind != DealerErrors.Kind.PAYOUT) {
            bj = DealerErrors.make(dealer, listOf(HandResult(Outcome.BLACKJACK, 15)), listOf(10), Random(seed++))
        }
        assertEquals("Paying you $10.", bj.said)
        assertEquals(5, bj.shortBy)
    }

    @Test fun dailyChallengeIsTheSameEveryTime() {
        val day = 20_400L
        assertEquals(DailyChallenge.cards(day), DailyChallenge.cards(day))
        assertFalse(DailyChallenge.cards(day) == DailyChallenge.cards(day + 1))
        assertEquals(DailyChallenge.COUNT_CARDS, DailyChallenge.cards(day).size)
        val rc = DailyChallenge.cards(day).sumOf { CountSystem.HI_LO.tag(it) }
        assertEquals(rc, DailyChallenge.runningCount(day))
        val q1 = DailyChallenge.questions(day, Rules(), CountSystem.HI_LO, TrueCount.RAMP_1_8)
        val q2 = DailyChallenge.questions(day, Rules(), CountSystem.HI_LO, TrueCount.RAMP_1_8)
        assertEquals(q1.map { it.prompt }, q2.map { it.prompt })
        assertEquals(100, DailyChallenge.score(rc, rc, 5))
        assertEquals(25, DailyChallenge.score(rc + 1, rc, 0))
        assertEquals(30, DailyChallenge.score(rc + 3, rc, 3))
    }

    @Test fun midShoeCardsAreNeverCounted() {
        val g = BlackjackGame(Rules(decks = 6), 1000, botCount = 0)
        g.joinMidShoe(104)
        val s = g.state()
        assertEquals(104, s.cardsDealt)
        assertEquals(0, s.runningCount)
        assertTrue(s.seenSinceShuffle.isEmpty())
        assertEquals(4.0, s.decksRemaining, 1e-9)
    }

    @Test fun deckEstimateAndSession() {
        val q = DeckEstimate.Q(DeckEstimate.View.TRAY, 6, 130, 0.5) // 2.5 decks
        assertTrue(q.isRight(2.5))
        assertFalse(q.isRight(3.5))
        assertEquals(2.5, q.answer, 1e-9)
        assertEquals(11, q.choices().size)
        val r = Random(3)
        repeat(50) { val x = DeckEstimate.make(r, true); assertTrue(x.choices().any { x.isRight(it) }) }
        assertEquals(0, SessionPlan.blockAt(0))
        assertEquals(2, SessionPlan.blockAt(25 * 60_000L))
        assertEquals(5, SessionPlan.blockAt(99 * 60_000L))
        val drop = SessionPlan.fatigueDrop(listOf(SessionPlan.Block(10, 10), SessionPlan.Block(), SessionPlan.Block(10, 8)))
        assertEquals(20.0, drop!!, 1e-9)
        assertEquals(0.4, PitHeat.slowHeat(12_000), 1e-9)
        assertEquals(0.0, PitHeat.slowHeat(2_000), 1e-9)
    }

    @Test fun hapticPatternsDiffer() {
        val low = HapticCode.pattern(1); val high = HapticCode.pattern(-1); val zero = HapticCode.pattern(0)
        assertTrue(high[1] > low[1] * 3)
        assertEquals(4, zero.size)
        assertNotNull(HapticCode.describe(0))
    }
}
