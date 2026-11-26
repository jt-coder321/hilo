package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RunTest {
    private fun ctx(o: Outcome, vararg c: Card, dealerBust: Boolean = false, stake: Int = 2, tc: Double = 0.0, losses: Int = 0, doubled: Boolean = false, split: Boolean = false): HandCtx {
        val (t, s) = handTotal(c.toList())
        return HandCtx(o, c.toList(), t, s, doubled, split, dealerBust, stake, 1, losses, tc)
    }
    private fun card(r: Rank, s: Suit = Suit.SPADES) = Card(r, s)

    @Test fun everyPerkHasAUniqueIdNameAndNoDashes() {
        assertEquals(Perks.ALL.size, Perks.ALL.map { it.id }.toSet().size)
        assertEquals(Perks.ALL.size, Perks.ALL.map { it.name }.toSet().size)
        assertTrue(Perks.ALL.size >= 20)
        (Perks.ALL.flatMap { listOf(it.name, it.desc) } + Bosses.ALL.flatMap { listOf(it.name, it.desc) } + StartDeck.entries.map { it.desc } + Tonic.entries.map { it.desc }).forEach {
            assertFalse(it, it.contains('—') || it.contains('–') || it.contains(" - "))
        }
        Rarity.entries.forEach { r -> assertTrue(r.name, Perks.ALL.any { it.rarity == r }) }
    }

    @Test fun perksScoreWhenTheirConditionHolds() {
        fun fx(id: String, c: HandCtx): Fx = Fx().also { f -> Perks.get(id)!!.let { p -> p.effect!!.invoke(p, c, f) } }
        val sevens = fx("sevens", ctx(Outcome.WIN, card(Rank.SEVEN), card(Rank.SEVEN, Suit.HEARTS), card(Rank.SEVEN, Suit.CLUBS)))
        assertEquals(150, sevens.chips)
        assertEquals(0, fx("sevens", ctx(Outcome.LOSE, card(Rank.SEVEN), card(Rank.KING))).chips)
        assertEquals(60, fx("court", ctx(Outcome.WIN, card(Rank.KING), card(Rank.QUEEN))).chips)
        assertEquals(3.0, fx("ace", ctx(Outcome.WIN, card(Rank.ACE), card(Rank.NINE))).mult, 0.0)
        assertEquals(100, fx("tell", ctx(Outcome.WIN, card(Rank.TEN), card(Rank.NINE), dealerBust = true)).chips)
        assertEquals(2.0, fx("sharp", ctx(Outcome.WIN, card(Rank.TEN), card(Rank.NINE), tc = 2.5)).xmult, 0.0)
        assertEquals(1.0, fx("sharp", ctx(Outcome.WIN, card(Rank.TEN), card(Rank.NINE), tc = 1.0)).xmult, 0.0)
        assertEquals(6.0, fx("contrarian", ctx(Outcome.WIN, card(Rank.TEN), card(Rank.NINE), tc = -1.5)).mult, 0.0)
        assertEquals(3.0, fx("baron", ctx(Outcome.BLACKJACK, card(Rank.ACE), card(Rank.KING))).xmult, 0.0)
        assertEquals(1.0, fx("baron", ctx(Outcome.WIN, card(Rank.TEN), card(Rank.NINE))).xmult, 0.0)
        assertEquals(6.0, fx("sore", ctx(Outcome.WIN, card(Rank.TEN), card(Rank.NINE), losses = 3)).mult, 0.0)
        assertEquals(4.0, fx("charlie", ctx(Outcome.WIN, card(Rank.TWO), card(Rank.THREE), card(Rank.FOUR), card(Rank.FIVE), card(Rank.SIX))).xmult, 0.0)
        assertEquals(1, fx("penny", ctx(Outcome.WIN, card(Rank.TEN), card(Rank.NINE))).money)
        assertEquals(2.0, fx("trouble", ctx(Outcome.WIN, card(Rank.FIVE), card(Rank.SIX), card(Rank.TEN), doubled = true)).xmult, 0.0)
    }

    @Test fun aRunPlaysThroughBlindsAndMoneyIsHonest() {
        val r = Run(StartDeck.STANDARD, Random(5))
        assertEquals(RunPhase.SELECT, r.phase)
        assertTrue(r.startBlind())
        assertEquals(5, r.handsLeft)
        assertEquals(100, r.target)
        var guard = 0
        while (r.phase == RunPhase.PLAY && guard++ < 200) {
            if (!r.inRound) assertTrue(r.deal(2)) else { val a = r.gs.basicAdvice!!.action; if (!r.act(a)) r.act(Action.STAND) }
            assertTrue(r.money >= 0)
            assertTrue(r.handsLeft in 0..5)
        }
        assertTrue(r.phase == RunPhase.CLEARED || r.phase == RunPhase.OVER)
        if (r.phase == RunPhase.CLEARED) {
            val before = r.money
            val co = r.cashout!!
            assertTrue(co.total >= 3)
            assertTrue(r.collect())
            assertEquals(RunPhase.SHOP, r.phase)
            assertEquals(before, r.money)
            assertEquals(6, r.offers.size)
            assertEquals(1, r.offers.count { it.train != null })
            assertEquals(3, r.offers.count { it.perk != null })
            assertEquals(3, r.offers.mapNotNull { it.perk }.map { it.id }.toSet().size)
        }
    }

    @Test fun shopRulesBuySellRerollSlots() {
        val r = Run(StartDeck.STANDARD, Random(9))
        r.startBlind(); r.forceScore(10_000)
        // Clear the blind the quick way: score is past the target at the next settle, so play one hand.
        var g = 0
        while (r.phase == RunPhase.PLAY && g++ < 50) { if (!r.inRound) r.deal(1) else if (!r.act(Action.STAND)) break }
        assertEquals(RunPhase.CLEARED, r.phase)
        r.collect()
        r.forceMoney(100)
        val idx = r.offers.indexOfFirst { it.perk != null }
        val cost = r.offers[idx].cost
        assertTrue(r.buy(idx))
        assertEquals(100 - cost, r.money)
        assertEquals(1, r.perks.size)
        assertFalse(r.buy(idx))
        val rc = r.rerollCost
        assertTrue(r.reroll()); assertEquals(rc + 1, r.rerollCost)
        assertFalse(r.offers.mapNotNull { it.perk }.any { it.id == r.perks[0].id })
        val m = r.money
        assertTrue(r.sell(0)); assertTrue(r.money > m); assertTrue(r.perks.isEmpty())
        // Slots are limited to five.
        repeat(10) { r.forceMoney(100); val j = r.offers.indices.firstOrNull { r.canBuy(it) && r.offers[it].perk != null }; if (j != null) r.buy(j) else r.reroll() }
        assertTrue(r.perks.size <= Run.SLOTS)
        r.forceMoney(100)
        if (r.perks.size == Run.SLOTS) assertFalse(r.offers.indices.any { r.canBuy(it) && r.offers[it].perk != null })
        assertTrue(r.leaveShop())
        assertEquals(RunPhase.SELECT, r.phase)
        assertEquals(1, r.blind)
    }

    @Test fun skippingPaysAndBossCannotBeSkipped() {
        val r = Run(StartDeck.STANDARD, Random(2))
        val m = r.money
        assertTrue(r.skipBlind()); assertEquals(m + Run.SKIP_TAG, r.money); assertEquals(1, r.blind)
        assertTrue(r.skipBlind()); assertEquals(2, r.blind)
        assertFalse(r.skipBlind())
        assertTrue(r.isBoss)
    }

    @Test fun bossRulesApply() {
        val r = Run(StartDeck.STANDARD, Random(4))
        r.skipBlind(); r.skipBlind()
        r.startBlind()
        val b = r.boss
        assertEquals(if (b.onlyHands > 0) b.onlyHands else 5 + b.handsDelta, r.handsLeft)
        assertTrue(r.target >= 10)
        if (b.noDoubleSplit) { r.deal(2); assertFalse(r.canAct(Action.DOUBLE)); assertFalse(r.canAct(Action.SPLIT)) }
        if (b.noRepeatStake) { assertFalse(r.canDeal(0)); r.deal(2); }
    }

    @Test fun deckUnlockAndStartingPerks() {
        assertEquals(listOf("spotter"), Run(StartDeck.COUNTER).perks.map { it.id })
        assertTrue(Run(StartDeck.COUNTER).countVisible)
        assertEquals(listOf("roller"), Run(StartDeck.ROLLER).perks.map { it.id })
        assertFalse(Run(StartDeck.STANDARD).countVisible)
        assertEquals(10, Run(StartDeck.ROLLER).money)
    }

    @Test fun saveAndRestoreKeepsTheRunBetweenBlinds() {
        val r = Run(StartDeck.COUNTER, Random(8))
        r.skipBlind(); r.skipBlind()
        r.forceMoney(23)
        val text = r.save()
        val back = RunSave.restore(text, Random(1))!!
        assertEquals(r.ante, back.ante); assertEquals(r.blind, back.blind); assertEquals(23, back.money)
        assertEquals(r.perks.map { it.id }, back.perks.map { it.id })
        assertEquals(RunPhase.SELECT, back.phase)
        assertEquals(null, RunSave.restore("garbage"))
        assertEquals(null, RunSave.restore(""))
    }

    @Test fun losingHandsCostMoneyAndNeverGoNegative() {
        val r = Run(StartDeck.STANDARD, Random(31))
        r.startBlind()
        repeat(300) {
            if (r.phase == RunPhase.PLAY) {
                if (!r.inRound) r.deal(3) else { r.act(Action.HIT); if (r.inRound) r.act(Action.STAND) }
            }
            assertTrue(r.money >= 0)
        }
    }

    @Test fun unbeatableDumbBotStillReachesLaterAntesSometimes() {
        val sim = RunSimTest()
        val deep = (1..60).count { sim.playRun(it).ante >= 3 || sim.playRun(it).phase == RunPhase.WON }
        assertTrue("only $deep of 60", deep >= 10)
    }

    // Hand types, editions and the new perks

    @Test fun handTypesPickTheRarestKind() {
        fun t(o: Outcome, cards: List<Card>, total: Int, doubled: Boolean = false, split: Boolean = false, bust: Boolean = false) =
            HandType.of(HandCtx(o, cards, total, false, doubled, split, bust, 2, 1, 0, 0.0))
        val c2 = listOf(card(Rank.TEN), card(Rank.NINE))
        assertEquals(HandType.WIN, t(Outcome.WIN, c2, 19))
        assertEquals(HandType.BUST, t(Outcome.WIN, c2, 19, bust = true))
        assertEquals(HandType.TWENTY_ONE, t(Outcome.WIN, listOf(card(Rank.TEN), card(Rank.FIVE), card(Rank.SIX)), 21, bust = true))
        assertEquals(HandType.SPLIT, t(Outcome.WIN, c2, 19, split = true, bust = true))
        assertEquals(HandType.DOUBLE, t(Outcome.WIN, c2, 19, doubled = true, split = true))
        assertEquals(HandType.CHARLIE, t(Outcome.WIN, List(5) { card(Rank.TWO) }, 10, doubled = true))
        assertEquals(HandType.BLACKJACK, t(Outcome.BLACKJACK, listOf(card(Rank.ACE), card(Rank.KING)), 21))
    }

    @Test fun trainingRaisesBaseChipsAndMult() {
        val r = Run(StartDeck.STANDARD, Random(3))
        assertEquals(50, r.baseChips(HandType.WIN)); assertEquals(1.0, r.baseMult(HandType.WIN), 0.0)
        r.setLevels(listOf(2))
        assertEquals(90, r.baseChips(HandType.WIN)); assertEquals(3.0, r.baseMult(HandType.WIN), 0.0)
        r.perks += Perks.fresh("scholar")!!
        assertEquals(130, r.baseChips(HandType.WIN)); assertEquals(5.0, r.baseMult(HandType.WIN), 0.0)
    }

    @Test fun savesKeepLevelsEditionsAndGrowthAndOldSavesStillLoad() {
        val r = Run(StartDeck.STANDARD, Random(4))
        r.perks += Perks.fresh("snow", Edition.POLY, 3)!!
        r.perks += Perks.fresh("wild", Edition.NEGATIVE)!!
        r.setLevels(listOf(0, 2, 0, 1))
        val back = RunSave.restore(r.save(), Random(1))!!
        assertEquals(listOf("snow", "wild"), back.perks.map { it.id })
        assertEquals(listOf(Edition.POLY, Edition.NEGATIVE), back.perks.map { it.edition })
        assertEquals(3, back.perks[0].grow)
        assertEquals(2, back.level(HandType.BUST)); assertEquals(1, back.level(HandType.SPLIT))
        assertEquals(6, back.slots)
        val old = RunSave.restore("1|STANDARD|2|1|9|ace,wild|0|0|3|120|0|0", Random(2))!!
        assertEquals(listOf("ace", "wild"), old.perks.map { it.id })
        assertEquals(9, old.money)
    }

    @Test fun editionsAddToThePerkAndSellFor() {
        val f = Perks.fresh("penny", Edition.FOIL)!!
        assertEquals(Perks.get("penny")!!.cost + 2, f.cost)
        assertEquals("Foil Penny Saver", f.title)
        assertEquals(Perks.get("penny")!!.cost, f.copyWith(Edition.NONE).cost)
    }

    @Test fun newPerksDoWhatTheySay() {
        val ten = card(Rank.TEN)
        fun ctx(won: Boolean = true, streak: Int = 1, money: Int = 0, perks: Int = 1, dBust: Boolean = false, stake: Int = 2, bj: Boolean = false) =
            HandCtx(if (bj) Outcome.BLACKJACK else if (won) Outcome.WIN else Outcome.LOSE, listOf(ten, ten), 20, false, false, false, dBust, stake, streak, 0, 0.0, money, perks)
        fun run(id: String, c: HandCtx, p: Perk = Perks.fresh(id)!!): Fx = Fx().also { p.effect!!.invoke(p, c, it) }
        assertEquals(4.0, run("bustbuster", ctx(dBust = true)).mult, 0.0)
        assertEquals(0.0, run("bustbuster", ctx()).mult, 0.0)
        assertEquals(36, run("collector", ctx(perks = 3)).chips)
        assertEquals(120, run("natural", ctx(bj = true)).chips)
        assertEquals(3.0, run("hot", ctx(streak = 3)).mult, 0.0)
        assertEquals(8.0, run("heavy", ctx(stake = 1)).mult, 0.0)
        assertEquals(0.0, run("heavy", ctx(stake = 2)).mult, 0.0)
        assertEquals(4.0, run("fat", ctx(money = 47)).mult, 0.0)
        val snow = Perks.fresh("snow", grow = 3)!!
        assertEquals(5.0, run("snow", ctx(), snow).mult, 0.0)
    }

    @Test fun chainReactionCountsOtherPerksThatFired() {
        val ten = card(Rank.TEN)
        val c = HandCtx(Outcome.WIN, listOf(ten, ten), 20, false, false, false, true, 2, 1, 0, 0.0, 0, 3)
        val f = Fx()
        f.hit("A", "x", mult = 1.0); f.hit("B", "x", mult = 1.0); f.hit("A", "again", mult = 1.0)
        val ch = Perks.fresh("chain")!!
        ch.effect!!.invoke(ch, c, f)
        assertEquals(3.0 + 6.0, f.mult, 0.0)
        assertTrue(ch.late)
    }

    @Test fun skippingTrainsARandomHandKind() {
        val r = Run(StartDeck.STANDARD, Random(6))
        r.skipBlind()
        assertEquals(1, HandType.entries.sumOf { r.level(it) })
        assertEquals(r.level(r.lastTrained!!), 1)
    }

    @Test fun shopAlwaysOffersATrainingCardAndEditionsAreRare() {
        var editions = 0; var total = 0
        for (seed in 1..60) {
            val r = Run(StartDeck.STANDARD, Random(seed))
            r.setState(1, 0, 50, 0, false, 0, 0, 0, true)
            assertEquals(1, r.offers.count { it.train != null })
            r.offers.mapNotNull { it.perk }.forEach { total++; if (it.edition != Edition.NONE) editions++ }
        }
        assertTrue("editions $editions of $total", editions in total / 8..total / 2)
    }

    @Test fun packsOfferThreeAndOnePickOrSkipWorks() {
        val r = Run(StartDeck.STANDARD, Random(21))
        r.setState(1, 0, 50, 0, false, 0, 0, 0, true)
        val i = r.offers.indexOfFirst { it.pack }
        assertTrue(i >= 0)
        assertTrue(r.buy(i))
        val pack = r.pack!!
        assertEquals(3, pack.map { it.id }.toSet().size)
        val before = r.perks.size
        assertTrue(r.pickFromPack(0))
        assertEquals(before + 1, r.perks.size)
        assertEquals(null, r.pack)
        val j = r.offers.indexOfFirst { it.pack }
        assertFalse(r.canBuy(j))
    }

    @Test fun skippingAPackKeepsPerksAndMovePerkReorders() {
        val r = Run(StartDeck.STANDARD, Random(22))
        r.setState(1, 0, 80, 0, false, 0, 0, 0, true)
        r.buy(r.offers.indexOfFirst { it.pack })
        r.skipPack()
        assertEquals(null, r.pack)
        r.offers.withIndex().filter { it.value.perk != null }.take(2).forEach { r.buy(it.index) }
        if (r.perks.size >= 2) {
            val a = r.perks[0].id; val b = r.perks[1].id
            assertTrue(r.movePerk(0, 1))
            assertEquals(b, r.perks[0].id); assertEquals(a, r.perks[1].id)
        }
    }

    @Test fun twentyFourNewPerksExistAndSaveRoundTrips() {
        assertTrue(Perks.ALL.size >= 40)
        val r = Run(StartDeck.STANDARD, Random(23))
        r.setState(1, 0, 80, 0, false, 0, 0, 0, true)
        r.offers.withIndex().filter { it.value.perk != null }.take(2).forEach { r.buy(it.index) }
        val back = RunSave.restore(r.save(), Random(1))!!
        assertEquals(r.perks.map { it.id }, back.perks.map { it.id })
        assertEquals(r.money, back.money)
    }
}
