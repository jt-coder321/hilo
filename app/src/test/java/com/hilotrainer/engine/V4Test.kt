package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class V4Test {
    private fun c(r: Rank) = Card(r, Suit.SPADES)
    private val all = Options(canDouble = true, canSplit = true, canSurrender = false)

    @Test fun countSystemTags() {
        val ranks = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.ACE)
        assertEquals(listOf(1, 1, 1, 1, 1, 0, 0, 0, -1, -1), ranks.map { CountSystem.HI_LO.tag(it) })
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 0, 0, -1, -1), ranks.map { CountSystem.KO.tag(it) })
        assertEquals(listOf(1, 1, 2, 2, 2, 1, 0, 0, -2, -1), ranks.map { CountSystem.ZEN.tag(it) })
        listOf(Rank.JACK, Rank.QUEEN, Rank.KING).forEach {
            assertEquals(-1, CountSystem.KO.tag(it)); assertEquals(-2, CountSystem.ZEN.tag(it))
        }
        assertEquals(0, CountSystem.HI_LO.deckSum)
        assertEquals(4, CountSystem.KO.deckSum)
        assertEquals(0, CountSystem.ZEN.deckSum)
    }

    @Test fun koStartsAtFourMinusFourPerDeckAndEndsAtFour() {
        assertEquals(-20, CountSystem.KO.initialCount(6))
        assertEquals(-28, CountSystem.KO.initialCount(8))
        assertEquals(0, CountSystem.KO.initialCount(1))
        for (decks in listOf(1, 2, 6, 8)) {
            val shoe = Shoe(decks, 1.0, Random(decks), CountSystem.KO)
            assertEquals(4 - 4 * decks, shoe.runningCount)
            repeat(decks * 52) { shoe.draw() }
            assertEquals(4, shoe.runningCount) // 4 - 4N + 4N
        }
        val zen = Shoe(6, 1.0, Random(2), CountSystem.ZEN)
        repeat(6 * 52) { zen.draw() }
        assertEquals(0, zen.runningCount)
    }

    @Test fun betLevels() {
        // KO: a running count of +4 is bet level 4 at any depth.
        assertEquals(4.0, CountSystem.KO.betLevelExact(4, 5.0), 1e-9)
        assertEquals(4.0, CountSystem.KO.betLevelExact(4, 1.0), 1e-9)
        // Fresh KO shoe sits at bet level 0.
        assertEquals(0.0, CountSystem.KO.betLevelExact(-20, 6.0), 1e-9)
        // Zen bet level is half its true count.
        assertEquals(2.0, CountSystem.ZEN.betLevelExact(12, 3.0), 1e-9)
        assertEquals(2.0, CountSystem.HI_LO.betLevelExact(6, 3.0), 1e-9)
    }

    @Test fun koGameUsesKoCountsAndBasicStrategy() {
        val g = BlackjackGame(Rules(), 100_000, Random(5), botCount = 2, gradeBets = true, useDeviations = true, system = CountSystem.KO)
        assertFalse(g.deviationsActive)
        repeat(200) {
            g.deal(10)
            var s = g.state()
            if (s.phase == Phase.INSURANCE) { g.insurance(false); s = g.state() }
            assertFalse(s.insuranceAdvised)
            while (s.phase == Phase.PLAYER) {
                assertFalse(s.advice!!.isDeviation)
                g.act(s.advice!!.action); s = g.state()
            }
            val expected = CountSystem.KO.initialCount(6) + s.seenSinceShuffle.sumOf { CountSystem.KO.tag(it) }
            assertEquals(expected, s.runningCount)
            assertTrue(s.events.all { e -> e.tag in -1..1 })
        }
    }

    @Test fun sixToFivePaysLess() {
        var checked = 0
        for (seed in 0 until 400) {
            val g = BlackjackGame(Rules(sixToFive = true), 10_000, Random(seed))
            g.deal(50)
            var s = g.state()
            if (s.phase == Phase.INSURANCE) { g.insurance(false); s = g.state() }
            if (s.results.firstOrNull()?.outcome == Outcome.BLACKJACK) {
                assertEquals(60, s.results[0].net)
                checked++
            }
        }
        assertTrue(checked > 0)
        for (seed in 0 until 400) {
            val g = BlackjackGame(Rules(), 10_000, Random(seed))
            g.deal(50)
            var s = g.state()
            if (s.phase == Phase.INSURANCE) { g.insurance(false); s = g.state() }
            if (s.results.firstOrNull()?.outcome == Outcome.BLACKJACK) assertEquals(75, s.results[0].net)
        }
    }

    @Test fun doubleAfterSplitChangesPairPlays() {
        val twos = Hand(listOf(c(Rank.TWO), Card(Rank.TWO, Suit.HEARTS)))
        val das = Rules(doubleAfterSplit = true)
        val noDas = Rules(doubleAfterSplit = false)
        assertEquals(Action.SPLIT, BasicStrategy.basic(twos, c(Rank.TWO), das, all).action)
        assertEquals(Action.HIT, BasicStrategy.basic(twos, c(Rank.TWO), noDas, all).action)
        val fours = Hand(listOf(c(Rank.FOUR), Card(Rank.FOUR, Suit.HEARTS)))
        assertEquals(Action.SPLIT, BasicStrategy.basic(fours, c(Rank.FIVE), das, all).action)
        assertEquals(Action.HIT, BasicStrategy.basic(fours, c(Rank.FIVE), noDas, all).action)
    }

    @Test fun reviewKeysRoundTrip() {
        val cases = listOf(
            Hand(listOf(c(Rank.TEN), c(Rank.SIX))) to c(Rank.TEN),
            Hand(listOf(c(Rank.ACE), c(Rank.SEVEN))) to c(Rank.NINE),
            Hand(listOf(c(Rank.EIGHT), c(Rank.EIGHT))) to c(Rank.ACE),
            Hand(listOf(c(Rank.KING), c(Rank.QUEEN))) to c(Rank.SIX),
            Hand(listOf(c(Rank.FIVE), c(Rank.FOUR))) to c(Rank.THREE),
        )
        val rules = Rules()
        for ((h, up) in cases) {
            val key = Review.keyFor(h, up)!!
            val (h2, up2) = Review.build(key)!!
            assertEquals(key, Review.keyFor(h2, up2))
            assertEquals(BasicStrategy.basic(h, up, rules, all).action, BasicStrategy.basic(h2, up2, rules, all).action)
        }
        assertEquals("H16v10", Review.keyFor(cases[0].first, cases[0].second))
        assertEquals("P8vA", Review.keyFor(cases[2].first, cases[2].second))
        assertNull(Review.keyFor(Hand(listOf(c(Rank.TEN), c(Rank.TWO), c(Rank.FOUR))), c(Rank.TEN)))
        // Every hard total that has a two-card non-pair form rebuilds.
        for (t in 5..19) for (u in 2..11) {
            val k = "H${t}v${if (u == 11) "A" else u}"
            val b = Review.build(k)!!
            assertEquals(t, b.first.total); assertFalse(b.first.isSoft); assertFalse(b.first.isPair)
        }
    }

    @Test fun reviewSchedule() {
        val day = Review.DAY_MS
        var items = Review.missed(emptyList(), "H16v10", 0)
        assertEquals(1, Review.due(items, 0).size)
        items = Review.answered(items, "H16v10", true, 0)
        assertEquals(0, Review.due(items, day - 1).size)
        assertEquals(1, Review.due(items, day).size)
        items = Review.answered(items, "H16v10", true, day)
        assertEquals(day + 3 * day, items[0].dueMs)
        items = Review.answered(items, "H16v10", false, 2 * day)
        assertEquals(0, items[0].box)
        assertEquals(2, items[0].misses)
        assertEquals(items, Review.decode(Review.encode(items)))
        repeat(10) { items = Review.answered(items, "H16v10", true, 0) }
        assertTrue(items[0].mastered)
        assertEquals(0, Review.due(items, Long.MAX_VALUE).size)
    }

    @Test fun riskOfRuinFormula() {
        // e^(-2 x 0.008 x 500 / 7) is about 31.9%.
        assertEquals(0.319, RiskOfRuin.ror(0.008, 500.0, 7.0), 0.001)
        assertEquals(1.0, RiskOfRuin.ror(0.0, 500.0, 7.0), 0.0)
        val units = RiskOfRuin.unitsFor(0.05, 0.01, 6.5)
        assertEquals(0.05, RiskOfRuin.ror(0.01, units, 6.5), 1e-9)
    }

    @Test fun coverPlayHeat() {
        assertEquals(2.0, CoverPlay.betHeat(1, 8, wonLast = false, afterShuffle = false).heat, 0.0)
        assertEquals(0.0, CoverPlay.betHeat(2, 4, wonLast = true, afterShuffle = false).heat, 0.0)
        assertEquals(0.0, CoverPlay.betHeat(1, 2, wonLast = false, afterShuffle = false).heat, 0.0)
        assertTrue(CoverPlay.betHeat(8, 1, wonLast = false, afterShuffle = true).heat > 0)
        assertEquals(0.0, CoverPlay.betHeat(0, 8, wonLast = false, afterShuffle = false).heat, 0.0)
    }

    @Test fun ladderAndCasinoLevels() {
        assertEquals(1, SpeedLadder.next(0, true))
        assertEquals(0, SpeedLadder.next(0, false))
        assertEquals(SpeedLadder.RUNGS_MS.size - 1, SpeedLadder.next(SpeedLadder.RUNGS_MS.size - 1, true))
        assertEquals("1.5s", SpeedLadder.label(0))
        assertEquals("0.3s", SpeedLadder.label(SpeedLadder.RUNGS_MS.size - 1))
        assertNull(CasinoTest.passed(61.0))
        assertEquals(0, CasinoTest.passed(50.0))
        assertEquals(3, CasinoTest.passed(20.0))
    }
}
