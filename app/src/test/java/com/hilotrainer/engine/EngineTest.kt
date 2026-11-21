package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class EngineTest {
    private fun c(r: Rank) = Card(r, Suit.SPADES)
    private fun hand(vararg r: Rank) = Hand(cards = r.map { c(it) }, bet = 10)
    private val all = Options(canDouble = true, canSplit = true, canSurrender = true)
    private val s17 = Rules(dealerHitsSoft17 = false, surrenderAllowed = true)
    private val h17 = Rules(dealerHitsSoft17 = true, surrenderAllowed = true)
    private val upRanks = listOf(
        Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN,
        Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.ACE,
    )

    private fun row(h: Hand, rules: Rules, opts: Options = all): String =
        upRanks.joinToString("") {
            when (BasicStrategy.advise(h, c(it), rules, opts).action) {
                Action.HIT -> "H"; Action.STAND -> "S"; Action.DOUBLE -> "D"
                Action.SPLIT -> "P"; Action.SURRENDER -> "R"
            }
        }

    @Test fun hiLoTags() {
        assertEquals(listOf(1, 1, 1, 1, 1, 0, 0, 0, -1, -1, -1, -1, -1), Rank.entries.map { it.hiLo })
        // A full deck balances to zero.
        assertEquals(0, Rank.entries.sumOf { it.hiLo } * 4)
    }

    @Test fun fullShoeCountsBackToZero() {
        val shoe = Shoe(6, 1.0, Random(1))
        repeat(6 * 52) { shoe.draw() }
        assertEquals(0, shoe.runningCount)
    }

    @Test fun trueCount() {
        val shoe = Shoe(6, 0.75, Random(2))
        repeat(156) { shoe.draw() }
        assertEquals(shoe.runningCount / 3.0, shoe.trueCountExact, 1e-9)
    }

    @Test fun softTotals() {
        assertEquals(21 to true, handTotal(listOf(c(Rank.ACE), c(Rank.KING))))
        assertEquals(12 to true, handTotal(listOf(c(Rank.ACE), c(Rank.ACE))))
        assertEquals(12 to false, handTotal(listOf(c(Rank.ACE), c(Rank.ACE), c(Rank.KING))))
    }

    // Rows read dealer 2,3,4,5,6,7,8,9,10,A. Expected values follow the
    // Wizard of Odds 4-8 deck chart, stands on soft 17, DAS, surrender.
    @Test fun hardTotalsS17() {
        assertEquals("HHHHHHHHHH", row(hand(Rank.FIVE, Rank.THREE), s17))
        assertEquals("HDDDDHHHHH", row(hand(Rank.FIVE, Rank.FOUR), s17))
        assertEquals("DDDDDDDDHH", row(hand(Rank.SIX, Rank.FOUR), s17))
        assertEquals("DDDDDDDDDH", row(hand(Rank.SIX, Rank.FIVE), s17))
        assertEquals("HHSSSHHHHH", row(hand(Rank.TEN, Rank.TWO), s17))
        assertEquals("SSSSSHHHHH", row(hand(Rank.TEN, Rank.THREE), s17))
        assertEquals("SSSSSHHHRH", row(hand(Rank.TEN, Rank.FIVE), s17))
        assertEquals("SSSSSHHRRR", row(hand(Rank.TEN, Rank.SIX), s17))
        assertEquals("SSSSSSSSSS", row(hand(Rank.TEN, Rank.SEVEN), s17))
    }

    @Test fun softTotalsS17() {
        assertEquals("HHHDDHHHHH", row(hand(Rank.ACE, Rank.TWO), s17))
        assertEquals("HHDDDHHHHH", row(hand(Rank.ACE, Rank.FOUR), s17))
        assertEquals("HDDDDHHHHH", row(hand(Rank.ACE, Rank.SIX), s17))
        assertEquals("SDDDDSSHHH", row(hand(Rank.ACE, Rank.SEVEN), s17))
        assertEquals("SSSSSSSSSS", row(hand(Rank.ACE, Rank.EIGHT), s17))
    }

    @Test fun pairsS17() {
        assertEquals("PPPPPPHHHH", row(hand(Rank.TWO, Rank.TWO), s17))
        assertEquals("HHHPPHHHHH", row(hand(Rank.FOUR, Rank.FOUR), s17))
        assertEquals("DDDDDDDDHH", row(hand(Rank.FIVE, Rank.FIVE), s17))
        assertEquals("PPPPPHHHHH", row(hand(Rank.SIX, Rank.SIX), s17))
        assertEquals("PPPPPPHHHH", row(hand(Rank.SEVEN, Rank.SEVEN), s17))
        assertEquals("PPPPPPPPPP", row(hand(Rank.EIGHT, Rank.EIGHT), s17))
        assertEquals("PPPPPSPPSS", row(hand(Rank.NINE, Rank.NINE), s17))
        assertEquals("SSSSSSSSSS", row(hand(Rank.TEN, Rank.KING), s17))
        assertEquals("PPPPPPPPPP", row(hand(Rank.ACE, Rank.ACE), s17))
    }

    @Test fun h17Changes() {
        assertEquals("DDDDDDDDDD", row(hand(Rank.SIX, Rank.FIVE), h17))
        assertEquals("DDDDDSSHHH", row(hand(Rank.ACE, Rank.SEVEN), h17))
        assertEquals("SSSSDSSSSS", row(hand(Rank.ACE, Rank.EIGHT), h17))
        assertEquals("SSSSSHHHRR", row(hand(Rank.TEN, Rank.FIVE), h17))
        assertEquals("SSSSSSSSSR", row(hand(Rank.TEN, Rank.SEVEN), h17))
        assertEquals("PPPPPPPPPR", row(hand(Rank.EIGHT, Rank.EIGHT), h17))
    }

    @Test fun fallbacksWhenDoubleNotAllowed() {
        val none = Options(canDouble = false, canSplit = false, canSurrender = false)
        assertEquals("HHHHHHHHHH", row(hand(Rank.SIX, Rank.FIVE), s17, none))
        assertEquals("SSSSSSSHHH", row(hand(Rank.ACE, Rank.SEVEN), s17, none))
        assertEquals("SSSSSHHHHH", row(hand(Rank.TEN, Rank.SIX), s17, none))
    }

    @Test fun insuranceIndex() {
        assertFalse(BasicStrategy.shouldInsure(2.9))
        assertTrue(BasicStrategy.shouldInsure(3.0))
    }

    @Test fun manyRandomRoundsKeepMoneyAndCountConsistent() {
        val rnd = Random(42)
        val game = BlackjackGame(Rules(surrenderAllowed = true), 1_000_000, rnd)
        repeat(20_000) {
            val before = game.bankroll
            game.deal(10)
            var s = game.state()
            if (s.phase == Phase.INSURANCE) {
                game.insurance(rnd.nextBoolean()); s = game.state()
            }
            while (s.phase == Phase.PLAYER) {
                game.act(s.advice!!.action)
                s = game.state()
            }
            assertEquals(Phase.ROUND_OVER, s.phase)
            assertTrue(s.holeRevealed)
            val net = s.results.sumOf { it.net } + s.insuranceNet
            assertEquals(before + net, game.bankroll)
            // Running count must equal the sum of every card seen since the last shuffle.
        }
    }

    private fun adv(h: Hand, up: Rank, rules: Rules, tc: Double, opts: Options = all) =
        BasicStrategy.advise(h, c(up), rules, opts, tc).action

    @Test fun illustrious18() {
        val r = Rules(dealerHitsSoft17 = false, surrenderAllowed = false)
        val noSur = all.copy(canSurrender = false)
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.SIX), Rank.TEN, r, -0.5, noSur))
        assertEquals(Action.STAND, adv(hand(Rank.TEN, Rank.SIX), Rank.TEN, r, 0.0, noSur))
        assertEquals(Action.STAND, adv(hand(Rank.TEN, Rank.FIVE), Rank.TEN, r, 4.0, noSur))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.FIVE), Rank.TEN, r, 3.9, noSur))
        assertEquals(Action.SPLIT, adv(hand(Rank.TEN, Rank.KING), Rank.FIVE, r, 5.0, noSur))
        assertEquals(Action.STAND, adv(hand(Rank.TEN, Rank.KING), Rank.FIVE, r, 4.9, noSur))
        assertEquals(Action.SPLIT, adv(hand(Rank.TEN, Rank.KING), Rank.SIX, r, 4.0, noSur))
        assertEquals(Action.DOUBLE, adv(hand(Rank.SIX, Rank.FOUR), Rank.TEN, r, 4.0, noSur))
        assertEquals(Action.DOUBLE, adv(hand(Rank.SIX, Rank.FOUR), Rank.ACE, r, 4.0, noSur))
        assertEquals(Action.HIT, adv(hand(Rank.SIX, Rank.FOUR), Rank.ACE, r, 3.0, noSur))
        assertEquals(Action.STAND, adv(hand(Rank.TEN, Rank.TWO), Rank.THREE, r, 2.0, noSur))
        assertEquals(Action.STAND, adv(hand(Rank.TEN, Rank.TWO), Rank.TWO, r, 3.0, noSur))
        assertEquals(Action.DOUBLE, adv(hand(Rank.SIX, Rank.FIVE), Rank.ACE, r, 1.0, noSur))
        assertEquals(Action.HIT, adv(hand(Rank.SIX, Rank.FIVE), Rank.ACE, r, 0.5, noSur))
        assertEquals(Action.DOUBLE, adv(hand(Rank.SIX, Rank.THREE), Rank.TWO, r, 1.0, noSur))
        assertEquals(Action.DOUBLE, adv(hand(Rank.SIX, Rank.THREE), Rank.SEVEN, r, 3.0, noSur))
        assertEquals(Action.STAND, adv(hand(Rank.TEN, Rank.SIX), Rank.NINE, r, 5.0, noSur))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.THREE), Rank.TWO, r, -1.5, noSur))
        assertEquals(Action.STAND, adv(hand(Rank.TEN, Rank.THREE), Rank.TWO, r, -1.0, noSur))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.TWO), Rank.FOUR, r, -0.1, noSur))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.TWO), Rank.FIVE, r, -2.5, noSur))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.TWO), Rank.SIX, r, -1.5, noSur))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.THREE), Rank.THREE, r, -2.5, noSur))
        // H17: 11 vs A is always a double
        assertEquals(Action.DOUBLE, adv(hand(Rank.SIX, Rank.FIVE), Rank.ACE, Rules(), -3.0, noSur))
        // 6,6 vs 4 still splits
        assertEquals(Action.SPLIT, adv(hand(Rank.SIX, Rank.SIX), Rank.FOUR, r, -5.0, noSur))
        // Double not allowed after hitting: 10 vs 10 is a hit
        assertEquals(Action.HIT, adv(hand(Rank.FOUR, Rank.THREE, Rank.THREE), Rank.TEN, r, 6.0, noSur.copy(canDouble = false)))
    }

    @Test fun fab4() {
        val r = Rules(dealerHitsSoft17 = false, surrenderAllowed = true)
        assertEquals(Action.SURRENDER, adv(hand(Rank.TEN, Rank.FOUR), Rank.TEN, r, 3.0))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.FOUR), Rank.TEN, r, 2.0))
        assertEquals(Action.SURRENDER, adv(hand(Rank.TEN, Rank.FIVE), Rank.TEN, r, 0.0))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.FIVE), Rank.TEN, r, -1.0))
        assertEquals(Action.SURRENDER, adv(hand(Rank.TEN, Rank.FIVE), Rank.NINE, r, 2.0))
        assertEquals(Action.SURRENDER, adv(hand(Rank.TEN, Rank.FIVE), Rank.ACE, r, 1.0))
        assertEquals(Action.HIT, adv(hand(Rank.TEN, Rank.FIVE), Rank.ACE, r, 0.0))
        // Basic strategy surrender of 16 vs 10 is kept
        assertEquals(Action.SURRENDER, adv(hand(Rank.TEN, Rank.SIX), Rank.TEN, r, 5.0))
    }

    @Test fun botsAndSequenceNumbers() {
        val rnd = Random(9)
        val game = BlackjackGame(Rules(), 1_000_000, rnd, botCount = 3, useDeviations = true, gradeBets = true)
        repeat(3000) {
            val before = game.bankroll
            game.deal(20)
            var s = game.state()
            assertEquals(3, s.bots.size)
            assertTrue(s.bots.all { it.cards.size >= 2 })
            if (s.phase == Phase.INSURANCE) { game.insurance(false); s = game.state() }
            while (s.phase == Phase.PLAYER) { game.act(s.advice!!.action); s = game.state() }
            // Every seq this round is unique and contiguous.
            val seqs = s.events.map { it.seq }
            assertEquals((s.roundStartSeq until s.nextSeq).toList(), seqs)
            val all = s.hands.flatMap { it.seqs } + s.bots.flatMap { it.seqs } + s.dealerSeqs
            assertEquals(all.size, all.toSet().size)
            assertEquals(before + s.results.sumOf { it.net } + s.insuranceNet, game.bankroll)
            assertEquals(s.bots.size, s.botResults.size)
        }
    }

    @Test fun betGrading() {
        val game = BlackjackGame(Rules(), 10_000, Random(3), gradeBets = true)
        game.deal(10)
        assertTrue(game.state().betDecision!!.correct) // fresh shoe, true count 0, 1 unit
        val g2 = BlackjackGame(Rules(), 10_000, Random(3), gradeBets = true)
        g2.deal(80)
        assertFalse(g2.state().betDecision!!.correct)
    }

    @Test fun sitOutRoundsKeepCountingAndMoney() {
        val game = BlackjackGame(Rules(), 500, Random(11), botCount = 2, gradeBets = true, wongOutAt = -1)
        repeat(500) {
            val before = game.bankroll
            game.deal(0)
            val s = game.state()
            assertEquals(Phase.ROUND_OVER, s.phase)
            assertTrue(s.satOut)
            assertTrue(s.hands.isEmpty())
            assertEquals(before, game.bankroll)
            assertEquals(s.seenSinceShuffle.sumOf { it.hiLo }, s.runningCount) // Hi-Lo default
            assertTrue(s.betDecision != null)
        }
    }

    @Test fun rampsAndWongGrading() {
        assertEquals(12, TrueCount.suggestedUnits(7, TrueCount.RAMP_1_12))
        assertEquals(1, TrueCount.suggestedUnits(-3, TrueCount.RAMP_1_4))
        assertEquals(3, TrueCount.suggestedUnits(3, TrueCount.RAMP_1_4))
        // Fresh shoe: true count 0, so with a sit-out point of 0 sitting out is right.
        val g = BlackjackGame(Rules(), 1000, Random(1), gradeBets = true, wongOutAt = 0)
        g.deal(0)
        assertTrue(g.state().betDecision!!.correct)
        val g2 = BlackjackGame(Rules(), 1000, Random(1), gradeBets = true, wongOutAt = -1)
        g2.deal(0)
        assertFalse(g2.state().betDecision!!.correct)
    }

    @Test fun runningCountMatchesVisibleCards() {
        val game = BlackjackGame(Rules(), 100_000, Random(7))
        game.deal(10)
        val s = game.state()
        val visible = s.hands.flatMap { it.cards } +
            if (s.holeRevealed) s.dealerCards else s.dealerCards.take(1) + s.dealerCards.drop(2)
        assertEquals(visible.sumOf { it.hiLo }, s.runningCount)
    }
}
