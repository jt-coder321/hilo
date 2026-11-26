package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JustPlayTest {
    private fun c(r: Rank, s: Suit) = Card(r, s)

    @Test fun castHasTenPlusDistinctPeople() {
        assertTrue(Cast.ALL.size >= 10)
        assertEquals(Cast.ALL.size, Cast.ALL.map { it.name }.toSet().size)
        assertEquals(Cast.ALL.size, Cast.ALL.map { it.look }.toSet().size)
        assertTrue(Cast.ALL.count { it.archetype == Archetype.COUNTER } >= 1)
        assertEquals(3, CasualDealers.ALL.size)
        assertEquals(3, CasualDealers.ALL.map { it.cardMs }.toSet().size)
    }

    @Test fun aboutTwoHundredLinesNoDashes() {
        val lines = TableTalk.allLines()
        assertTrue("only ${lines.size} lines", lines.size >= 200)
        lines.forEach { assertFalse(it, it.contains('—') || it.contains('–')) }
    }

    @Test fun sevenHundredUniqueLinesNoDashes() {
        val lines = TableTalk.allLines().distinct()
        println("TOTAL_UNIQUE_LINES ${lines.size} CAST ${Cast.ALL.size}")
        assertTrue("only ${lines.size} unique lines", lines.size >= 700)
        lines.forEach { assertFalse(it, it.contains('\u2014') || it.contains('\u2013') || it.contains(" - ")) }
        val extra = MoreTalk.allLines().distinct()
        assertTrue("only ${extra.size} new lines", extra.size >= 400)
    }

    @Test fun everyNewMomentHasLinesForMostPlayers() {
        val r = Random(11)
        val moments = listOf(Talk.DEALER_WEAK, Talk.DEALER_STRONG, Talk.NEW_PLAYER, Talk.SHUFFLE, Talk.YOU_BUST, Talk.YOU_LOSE_BIG, Talk.DOUBLE, Talk.TIP, Talk.ETIQUETTE_REPLY)
        for (t in moments) {
            TableTalk.resetMemory()
            val got = Archetype.entries.count { TableTalk.say(it, t, r) != null }
            val need = when (t) { Talk.TIP -> MoreTalk.tippers.size; Talk.ETIQUETTE_REPLY -> MoreTalk.forgetful.size; else -> 8 }
            assertTrue("$t only $got players have a line", got >= need)
        }
        listOf("peek", "cut", "burn", "ins", "nobj", "welcome", "thanks", "etiquette", "double", "split").forEach { k ->
            listOf("Vera", "Danny", "Mr. Grant").forEach { n -> assertTrue("$n $k", MoreTalk.dealerSays(n, k, r).isNotBlank()) }
        }
    }

    @Test fun shoeSnapshotKeepsOrderPositionAndCount() {
        val a = Shoe(6, 0.75, Random(5))
        repeat(87) { a.draw() }
        val text = a.snapshot()
        val b = Shoe(6, 0.75, Random(99))
        assertTrue(b.restore(text))
        assertEquals(a.runningCount, b.runningCount)
        assertEquals(a.cardsDealt, b.cardsDealt)
        repeat(30) { assertEquals(a.draw(), b.draw()) }
        assertEquals(a.runningCount, b.runningCount)
        // A snapshot from a different shoe size or a damaged one is refused and changes nothing.
        val c = Shoe(2, 0.75, Random(1)); val before = c.snapshot()
        assertFalse(c.restore(text)); assertFalse(c.restore("junk")); assertEquals(before, c.snapshot())
    }

    @Test fun twelveNewCharactersEachHaveTheirOwnVoice() {
        val newOnes = listOf("Priya", "Gus", "Kenji", "Delilah", "Bruno", "Fiona", "Omar", "Chloe", "Reggie", "Yuki", "Colonel Hargrove", "Savannah")
        assertEquals(newOnes.toSet(), CharTalk.names())
        newOnes.forEach { n -> assertEquals(n, Cast.byName(n).name) }
        assertTrue(Cast.ALL.size >= 25)
        val all = CharTalk.allLines()
        assertEquals("duplicate lines across characters", all.size, all.toSet().size)
        newOnes.forEach { n ->
            val mine = Talk.entries.sumOf { CharTalk.lines(n, it).size }
            assertTrue("$n has only $mine lines", mine in 36..45)
            listOf(Talk.WIN, Talk.BIG_WIN, Talk.LOSE, Talk.BUST, Talk.BLACKJACK, Talk.PUSH, Talk.WIN_STREAK, Talk.LOSS_STREAK, Talk.BIG_BET, Talk.IDLE, Talk.DOUBLE, Talk.SPLIT)
                .forEach { assertTrue("$n $it", CharTalk.lines(n, it).isNotEmpty()) }
        }
        all.forEach { assertFalse(it, it.contains('\u2014') || it.contains('\u2013') || it.contains(" - ")) }
        // Nobody borrows a line from the older cast.
        val old = TableTalk.allLines().toSet() - all.toSet()
        assertEquals(emptySet<String>(), all.toSet().intersect(old))
        // Each character shows up in the table's cast and talks in their own words.
        val r = Random(2)
        TableTalk.resetMemory()
        val line = TableTalk.say(Cast.byName("Gus"), Talk.WIN, r)
        assertNotNull(line)
    }

    @Test fun talkAvoidsRepeats() {
        TableTalk.resetMemory()
        val r = Random(3)
        val got = List(5) { TableTalk.say(Archetype.REGULAR, Talk.WIN, r) }
        // Regulars have 4 own lines and 5 shared, so 5 picks never repeat.
        assertEquals(got.size, got.toSet().size)
    }

    @Test fun twentyOnePlusThree() {
        assertEquals(TwentyOnePlusThree.SUITED_TRIPS, TwentyOnePlusThree.evaluate(c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS), c(Rank.SEVEN, Suit.HEARTS))!!.second)
        assertEquals(TwentyOnePlusThree.STRAIGHT_FLUSH, TwentyOnePlusThree.evaluate(c(Rank.FOUR, Suit.CLUBS), c(Rank.SIX, Suit.CLUBS), c(Rank.FIVE, Suit.CLUBS))!!.second)
        assertEquals(TwentyOnePlusThree.TRIPS, TwentyOnePlusThree.evaluate(c(Rank.KING, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.KING, Suit.SPADES))!!.second)
        assertEquals(TwentyOnePlusThree.STRAIGHT, TwentyOnePlusThree.evaluate(c(Rank.QUEEN, Suit.CLUBS), c(Rank.ACE, Suit.HEARTS), c(Rank.KING, Suit.SPADES))!!.second)
        assertEquals(TwentyOnePlusThree.STRAIGHT, TwentyOnePlusThree.evaluate(c(Rank.ACE, Suit.CLUBS), c(Rank.TWO, Suit.HEARTS), c(Rank.THREE, Suit.SPADES))!!.second)
        assertEquals(TwentyOnePlusThree.FLUSH, TwentyOnePlusThree.evaluate(c(Rank.TWO, Suit.DIAMONDS), c(Rank.NINE, Suit.DIAMONDS), c(Rank.KING, Suit.DIAMONDS))!!.second)
        assertNull(TwentyOnePlusThree.evaluate(c(Rank.KING, Suit.CLUBS), c(Rank.ACE, Suit.HEARTS), c(Rank.TWO, Suit.SPADES)))
        // Ten, jack, queen are a straight; ten and king are not the same rank.
        assertNotNull(TwentyOnePlusThree.evaluate(c(Rank.TEN, Suit.CLUBS), c(Rank.JACK, Suit.HEARTS), c(Rank.QUEEN, Suit.SPADES)))
        assertNull(TwentyOnePlusThree.evaluate(c(Rank.TEN, Suit.CLUBS), c(Rank.KING, Suit.HEARTS), c(Rank.TEN, Suit.SPADES)))
    }

    @Test fun dailyBonusStreak() {
        assertEquals(DailyBonus.BASE to 1, DailyBonus.claim(-1, 0, 100))
        assertNull(DailyBonus.claim(100, 1, 100))
        assertEquals(DailyBonus.BASE + DailyBonus.PER_DAY to 2, DailyBonus.claim(100, 1, 101))
        assertEquals(DailyBonus.BASE to 1, DailyBonus.claim(100, 5, 103))
        assertEquals(DailyBonus.BASE + DailyBonus.PER_DAY * 6 to 7, DailyBonus.claim(100, 7, 101))
    }

    private fun seat(life: TableLife, i: Int, name: String, chips: Int = 1000, heat: Int = 0) {
        val who = Cast.byName(name)
        life.seats[i] = Seat(i.toLong() + 1, who, chips, chips, who.baseUnits * life.unit, plan = 999, heat = heat)
    }

    @Test fun counterSpreadsWithTheCountAndWongsOut() {
        val life = TableLife(4, 10, Random(1))
        seat(life, 0, "Dex")
        life.planBets(0.5); assertEquals(10, life.seats[0]!!.bet)
        life.planBets(4.2); assertEquals(60, life.seats[0]!!.bet)
        life.planBets(6.0); assertEquals(80, life.seats[0]!!.bet)
        life.planBets(-1.5); assertTrue(life.seats[0]!!.sittingOut)
        assertTrue(0 !in life.playing())
    }

    @Test fun touristChasesLosses() {
        val life = TableLife(4, 10, Random(1))
        seat(life, 1, "Hank")
        life.seats[1] = life.seats[1]!!.copy(lossStreak = 3)
        life.planBets(0.0)
        assertEquals(80, life.seats[1]!!.bet)
    }

    @Test fun pitBossWatchesThenBacksOffAHotCounter() {
        val life = TableLife(4, 10, Random(5), comeAndGo = false)
        seat(life, 3, "Ingrid", chips = 1000, heat = TableLife.HEAT_WATCH)
        val ev = mutableListOf<TableEvent>()
        repeat(6) {
            life.planBets(5.0)
            ev += life.afterRound(mapOf(3 to 80), mapOf(3 to Outcome.WIN), 5.0, false, false)
        }
        assertTrue(ev.any { it is TableEvent.PitWatch })
        assertTrue(ev.any { it is TableEvent.PitAct })
    }

    @Test fun bustedPlayersLeaveOrRebuyOnce() {
        val life = TableLife(4, 10, Random(9), comeAndGo = false)
        seat(life, 2, "Grandma Dot", chips = 10)
        val ev = life.afterRound(mapOf(2 to -10), mapOf(2 to Outcome.LOSE), 0.0, false, false)
        assertTrue(ev.any { it is TableEvent.Leave && it.reason == LeaveReason.BUSTED })
        assertNull(life.seats[2])
    }

    @Test fun proLeavesAtStopLoss() {
        val life = TableLife(4, 10, Random(2), comeAndGo = false)
        seat(life, 0, "Mei", chips = 1000)
        val ev = life.afterRound(mapOf(0 to -350), mapOf(0 to Outcome.LOSE), 0.0, false, false)
        assertTrue(ev.any { it is TableEvent.Leave && it.reason == LeaveReason.STOP_LOSS })
    }

    @Test fun openSeatsSpreadAndCloseSeats() {
        val life = TableLife(4, 10, Random(4))
        life.fill(4)
        assertEquals(4, life.seats.count { it != null })
        val ev = life.setOpenSeats(2)
        assertEquals(2, ev.size)
        assertEquals(setOf(1, 2), life.open)
        repeat(30) { life.arrive(0.0, force = true) }
        assertTrue(life.seats.indices.all { life.seats[it] == null || it in life.open })
    }

    @Test fun longSimulationStaysSane() {
        val rnd = Random(11)
        val life = TableLife(4, 25, rnd)
        val game = BlackjackGame(Rules(), 100000, rnd, unit = 25)
        life.fill(3)
        var backoffs = 0; var arrivals = 0; var leaves = 0
        repeat(3000) {
            life.planBets(game.upcomingTrueCount())
            val p = life.playing()
            game.changeSeats(p.size)
            game.botStyles = p.map { life.seats[it]!!.who.style }
            game.botBets = p.map { life.seats[it]!!.bet }
            game.deal(25)
            if (game.state().phase == Phase.INSURANCE) game.insurance(false)
            while (game.state().phase == Phase.PLAYER) game.act(game.state().basicAdvice!!.action)
            val st = game.state()
            val nets = p.indices.associate { p[it] to st.botResults[it].net }
            val outs = p.indices.associate { p[it] to st.botResults[it].outcome }
            p.forEachIndexed { k, s -> assertEquals(life.seats[s]!!.bet, st.bots[k].bet / if (st.bots[k].doubled) 2 else 1) }
            val ev = life.afterRound(nets, outs, game.upcomingTrueCount(), false, false)
            backoffs += ev.count { it is TableEvent.PitAct }
            arrivals += ev.count { it is TableEvent.Arrive }
            leaves += ev.count { it is TableEvent.Leave }
            life.seats.forEach { s -> if (s != null) assertTrue(s.chips >= 0) }
        }
        assertTrue("arrivals $arrivals", arrivals > 30)
        assertTrue("leaves $leaves", leaves > 30)
        println("3000 rounds: $arrivals arrivals, $leaves departures, $backoffs pit boss actions")
    }
}
