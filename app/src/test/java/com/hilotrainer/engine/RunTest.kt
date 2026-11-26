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
            assertEquals(4, r.offers.size)
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
}
