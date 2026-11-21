package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class V5Test {
    private fun c(r: Rank, s: Suit = Suit.SPADES) = Card(r, s)
    private val all = Options(canDouble = true, canSplit = true, canSurrender = false)
    private val s17 = Rules(dealerHitsSoft17 = false)
    private val h17 = Rules(dealerHitsSoft17 = true)

    @Test fun extendedIndexPlays() {
        val a8 = Hand(listOf(c(Rank.ACE), c(Rank.EIGHT)))
        // A,8 vs 6: double at +1 with S17, basic strategy otherwise.
        assertEquals(Action.DOUBLE, BasicStrategy.advise(a8, c(Rank.SIX), s17, all, 1.0, full = true).action)
        assertEquals(Action.STAND, BasicStrategy.advise(a8, c(Rank.SIX), s17, all, 0.0, full = true).action)
        // Not part of the Illustrious 18, so without the full chart it stays basic.
        assertEquals(Action.STAND, BasicStrategy.advise(a8, c(Rank.SIX), s17, all, 3.0, full = false).action)
        // With H17 basic strategy already doubles it.
        assertEquals(Action.DOUBLE, BasicStrategy.advise(a8, c(Rank.SIX), h17, all, -3.0, full = true).action)
        val eight = Hand(listOf(c(Rank.FIVE), c(Rank.THREE)))
        assertEquals(Action.DOUBLE, BasicStrategy.advise(eight, c(Rank.SIX), s17, all, 2.0, full = true).action)
        assertEquals(Action.HIT, BasicStrategy.advise(eight, c(Rank.SIX), s17, all, 1.9, full = true).action)
        val nines = Hand(listOf(c(Rank.NINE), c(Rank.NINE, Suit.HEARTS)))
        assertEquals(Action.SPLIT, BasicStrategy.advise(nines, c(Rank.SEVEN), s17, all, 3.0, full = true).action)
        assertEquals(Action.STAND, BasicStrategy.advise(nines, c(Rank.SEVEN), s17, all, 2.0, full = true).action)
        // Every extended play's "below" action matches basic strategy, so the chart only changes plays at the index.
        for (e in BasicStrategy.EXTENDED) {
            val (h, up) = Review.build(keyFor(e.label))!!
            val rules = if (e.s17Only) s17 else h17
            assertEquals(e.label, e.below, BasicStrategy.basic(h, up, rules, all).action)
        }
        // The Illustrious 18 still works with the full chart on.
        val sixteen = Hand(listOf(c(Rank.TEN), c(Rank.SIX)))
        assertEquals(Action.STAND, BasicStrategy.advise(sixteen, c(Rank.TEN), s17, all, 0.0, full = true).action)
    }

    private fun keyFor(label: String): String {
        val (h, u) = label.split(" vs ")
        return when {
            h.startsWith("A,") -> "S${11 + h.drop(2).toInt()}v$u"
            h.contains(",") -> "P${h.substringBefore(",")}v$u"
            else -> "H${h}v$u"
        }
    }

    @Test fun errorMapCellsAndWeakSpots() {
        assertEquals("H16v10", ErrorMap.cellKey(Hand(listOf(c(Rank.TEN), c(Rank.TWO), c(Rank.FOUR))), c(Rank.KING)))
        assertEquals("S18vA", ErrorMap.cellKey(Hand(listOf(c(Rank.ACE), c(Rank.SEVEN))), c(Rank.ACE)))
        assertEquals("P8v6", ErrorMap.cellKey(Hand(listOf(c(Rank.EIGHT), c(Rank.EIGHT))), c(Rank.SIX)))
        assertEquals(0, ErrorMap.tcBucket(-5.0))
        assertEquals(2, ErrorMap.tcBucket(0.4))
        assertEquals(7, ErrorMap.tcBucket(9.0))
        var m = emptyMap<String, ErrorMap.Cell>()
        repeat(5) { m = ErrorMap.add(m, "H16v10", false) }
        m = ErrorMap.add(m, "H16v10", true)
        m = ErrorMap.add(m, "S18v9", false)
        repeat(4) { m = ErrorMap.add(m, "H12v3", true) }
        assertEquals(m, ErrorMap.decode(ErrorMap.encode(m)))
        val spots = ErrorMap.weakSpots(m)
        assertEquals(listOf("H16v10", "S18v9"), spots.map { it.first })
        assertEquals(5.0 / 6, spots[0].second.rate, 1e-9)
    }

    @Test fun voiceParsing() {
        assertEquals(3, VoiceCount.parse("plus three"))
        assertEquals(-2, VoiceCount.parse("minus two"))
        assertEquals(-5, VoiceCount.parse("negative 5"))
        assertEquals(-4, VoiceCount.parse("-4"))
        assertEquals(7, VoiceCount.parse("+7"))
        assertEquals(0, VoiceCount.parse("zero"))
        assertEquals(12, VoiceCount.parse("12"))
        assertEquals(-23, VoiceCount.parse("minus twenty three"))
        assertEquals(6, VoiceCount.parse("the count is six"))
        assertNull(VoiceCount.parse("hello there"))
    }

    @Test fun perfectPairsAndInsuranceValues() {
        // Fresh six decks: second card matches 23 of the 311 left (5 same suit, 6 same color, 12 other color).
        val six = SideBets.perfectPairsEv(SideBets.unseen(6, emptyList()))
        assertEquals((5 * 25 + 6 * 12 + 12 * 6 - (311 - 23)) / 311.0, six, 1e-12)
        assertEquals(-0.0611, six, 0.0001)
        // Insurance with only the dealer's ace seen: 96 tens among 311 unseen cards.
        val ins = SideBets.insuranceEv(SideBets.unseen(6, listOf(c(Rank.ACE))))
        assertEquals(3 * 96 / 311.0 - 1, ins, 1e-12)
        assertTrue(ins < 0)
        // Strip out low cards and insurance turns positive.
        val lows = Rank.entries.filter { it.value in 2..6 }.flatMap { r -> Suit.entries.flatMap { s -> List(6) { Card(r, s) } } }
        assertTrue(SideBets.insuranceEv(SideBets.unseen(6, lows)) > 0)
    }

    @Test fun perfectPairsSettles() {
        var seen = 0
        for (seed in 0 until 600) {
            val g = BlackjackGame(Rules(), 10_000, Random(seed))
            val before = g.bankroll
            g.deal(10, perfectPairs = 5)
            var st = g.state()
            if (st.phase == Phase.INSURANCE) { g.insurance(false); st = g.state() }
            while (st.phase == Phase.PLAYER) { g.act(Action.STAND); st = g.state() }
            val h = st.hands[0]
            val pair = h.cards[0].rank == h.cards[1].rank
            assertEquals(if (pair) st.perfectPairsNet!! > 0 else st.perfectPairsNet == -5, true)
            if (pair) seen++
            val main = st.results.sumOf { it.net } + st.insuranceNet
            assertEquals(before + main + st.perfectPairsNet!!, g.bankroll)
        }
        assertTrue(seen > 0)
    }

    @Test fun continuousShufflerResetsEveryRound() {
        val g = BlackjackGame(Rules(), 100_000, Random(3), botCount = 2, shuffleStyle = ShuffleStyle.CONTINUOUS)
        repeat(50) { i ->
            g.deal(10)
            var st = g.state()
            if (st.phase == Phase.INSURANCE) { g.insurance(false); st = g.state() }
            while (st.phase == Phase.PLAYER) { g.act(st.advice!!.action); st = g.state() }
            if (i > 0) assertTrue(g.reshuffledByMachine)
            // Only this round's cards are in the count.
            assertEquals(st.seenSinceShuffle.size, st.events.count { it.counted })
            assertEquals(st.cardsDealt, st.events.count { it.kind == EventKind.DEAL })
        }
    }

    @Test fun realisticPlayersMakeMistakes() {
        val g = BlackjackGame(Rules(), 100_000, Random(9), botCount = 3, botStyles = listOf(BotStyle.NEVER_BUST, BotStyle.MIMIC, BotStyle.AGGRESSIVE))
        var mistakes = 0
        repeat(200) {
            g.deal(10)
            var st = g.state()
            if (st.phase == Phase.INSURANCE) { g.insurance(false); st = g.state() }
            while (st.phase == Phase.PLAYER) { g.act(st.advice!!.action); st = g.state() }
            mistakes += st.botMistakes.size
            // Never-bust player never busts.
            assertFalse(st.bots[0].isBust && !st.bots[0].doubled)
            assertEquals(st.seenSinceShuffle.sumOf { it.hiLo }, st.runningCount)
        }
        assertTrue(mistakes > 20)
    }

    @Test fun backCountAndTeamSignals() {
        assertTrue(BackCount.shouldEnter(2.0))
        assertFalse(BackCount.shouldEnter(1.9))
        assertTrue(BackCount.shouldLeave(0.5))
        assertFalse(BackCount.shouldLeave(1.0))
        assertEquals("Touches chin", TeamSignals.forLevel(2.3)!!.gesture)
        assertEquals("Rubs back of neck", TeamSignals.forLevel(7.0)!!.gesture)
        assertEquals("Stretches both arms", TeamSignals.forLevel(-1.0)!!.gesture)
        assertNull(TeamSignals.forLevel(1.2))
    }

    @Test fun courseQuestionsAreAnswerable() {
        val rnd = Random(1)
        for (sys in CountSystem.entries) for (lesson in Course.LESSONS.indices) {
            if (Course.LESSONS[lesson].kind != Course.Kind.QUIZ) continue
            repeat(40) {
                val q = Course.question(lesson, rnd, Rules(), sys, TrueCount.RAMP_1_8)
                assertTrue("lesson $lesson ${q.prompt}", q.answer in q.options.indices)
                assertEquals(q.options.size, q.options.distinct().size)
                assertTrue(q.prompt.isNotBlank())
            }
        }
        assertEquals(10, Course.LESSONS.size)
    }
}
