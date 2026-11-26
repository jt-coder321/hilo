package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RunMetaTest {
    private fun rec(ante: Int = 1, cleared: Int = 0, best: Int = 0, won: Boolean = false, hard: Boolean = false, perks: Int = 0, skips: Int = 0) =
        RunRecord(ante, cleared, best, won, StartDeck.STANDARD, perks, skips, 0, hard)

    @Test fun rankPointsGrowWithProgressAndDoubleOnHard() {
        val a = RunMeta.xpFor(rec(ante = 3, cleared = 7, best = 2_500))
        assertEquals(7 * 10 + 2 * 25 + 25, a)
        assertEquals(a * 2, RunMeta.xpFor(rec(ante = 3, cleared = 7, best = 2_500, hard = true)))
        assertTrue(RunMeta.xpFor(rec(ante = 6, cleared = 18, won = true)) > 300)
    }

    @Test fun ranksStepUpAtTheirThresholds() {
        assertEquals(0, RunMeta.rankOf(0)); assertEquals(0, RunMeta.rankOf(59)); assertEquals(1, RunMeta.rankOf(60))
        assertEquals(RunMeta.RANK_XP.lastIndex, RunMeta.rankOf(99_999))
        assertEquals(null, RunMeta.progress(99_999))
        assertEquals(RunMeta.TITLES.size, RunMeta.RANK_XP.size)
    }

    @Test fun goalsTriggerOnTheirConditions() {
        fun hit(id: String, r: RunRecord, codex: Int = 0, bonus: Int = 0) = RunMeta.GOALS.first { it.id == id }.test(r, codex, bonus)
        assertTrue(hit("first", rec(cleared = 1))); assertFalse(hit("first", rec()))
        assertTrue(hit("a3", rec(ante = 3))); assertFalse(hit("a4", rec(ante = 3)))
        assertTrue(hit("win", rec(won = true))); assertTrue(hit("hardwin", rec(won = true, hard = true))); assertFalse(hit("hardwin", rec(won = true)))
        assertTrue(hit("big2", rec(best = 12_000))); assertFalse(hit("big3", rec(best = 12_000)))
        assertTrue(hit("full", rec(perks = 5))); assertTrue(hit("skipper", rec(skips = 3)))
        assertTrue(hit("col20", rec(), codex = 20)); assertTrue(hit("goals", rec(), bonus = 10))
        assertEquals(RunMeta.GOALS.size, RunMeta.GOALS.map { it.id }.toSet().size)
    }

    @Test fun everyBossHasAPrizeThatDoesWhatItSaysAndAnte6IsTheWin() {
        assertEquals(Run.MAX_ANTE, Run.MILESTONES.size)
        assertEquals(null, Run.MILESTONES.last())
        fun cleared(ante: Int): Run {
            val r = Run(StartDeck.STANDARD, Random(ante))
            r.setState(ante, 2, 20, 0, false, 0, 0, 0, false)
            r.startBlind(); r.forceScore(10_000_000)
            r.lose("x")
            return r
        }
        val r = Run(StartDeck.STANDARD, Random(3))
        r.setState(2, 2, 20, 0, false, 0, 0, 0, false)
        r.startBlind()
        // Win the boss blind by playing it out with a huge score head start.
        r.forceScore(10_000_000)
        var guard = 0
        while (r.phase == RunPhase.PLAY && guard++ < 100) { if (!r.inRound) r.deal(1) else { val a = r.gs.basicAdvice?.action ?: Action.STAND; if (!r.act(a)) r.act(Action.STAND) } }
        assertEquals(RunPhase.CLEARED, r.phase)
        assertEquals(Milestone.HAND, r.milestone)
        assertEquals(1, r.permHands)
        r.collect(); r.leaveShop()
        r.startBlind()
        assertEquals(StartDeck.STANDARD.hands + 1, r.handsLeft)
        assertNotNull(cleared(3))
    }

    @Test fun slotPrizeAddsASlotAndPackPrizeOpensAPackInTheShop() {
        fun bossClear(ante: Int): Run {
            val r = Run(StartDeck.STANDARD, Random(ante + 40))
            r.setState(ante, 2, 20, 0, false, 0, 0, 0, false)
            r.startBlind(); r.forceScore(10_000_000)
            var guard = 0
            while (r.phase == RunPhase.PLAY && guard++ < 100) { if (!r.inRound) r.deal(1) else { val a = r.gs.basicAdvice?.action ?: Action.STAND; if (!r.act(a)) r.act(Action.STAND) } }
            return r
        }
        val s = bossClear(3)
        assertEquals(RunPhase.CLEARED, s.phase); assertEquals(Milestone.SLOT, s.milestone); assertEquals(Run.SLOTS + 1, s.slots)
        val p = bossClear(1)
        assertEquals(Milestone.PACK, p.milestone)
        p.collect()
        assertEquals(3, p.pack!!.size)
    }

    @Test fun hardModeRaisesTargetsAndNewDecksStartRight() {
        val easy = Run(StartDeck.STANDARD, Random(1)); val hard = Run(StartDeck.STANDARD, Random(1), hard = true)
        assertTrue(hard.target > easy.target)
        val tycoon = Run(StartDeck.TYCOON, Random(1))
        assertEquals(20, tycoon.money); assertEquals("ticket", tycoon.perks.single().id)
        val lucky = Run(StartDeck.LUCKY, Random(1))
        assertEquals(1, lucky.perks.size); assertEquals(Rarity.UNCOMMON, lucky.perks.single().rarity)
        assertTrue(StartDeck.LUCKY.unlockRank > 0 && StartDeck.TYCOON.unlockRank > StartDeck.LUCKY.unlockRank)
    }

    @Test fun savesKeepPrizesAndHardMode() {
        val r = Run(StartDeck.STANDARD, Random(8), hard = true)
        r.setState(3, 0, 12, 0, false, 5, 100, 1, false)
        r.setExtras(1, 2, 4, 3)
        val back = RunSave.restore(r.save(), Random(2))!!
        assertTrue(back.hard); assertEquals(1, back.extraSlots); assertEquals(2, back.permHands); assertEquals(4, back.goalsDone)
        // Old saves without the new fields still load.
        val old = r.save().split("|").take(13).joinToString("|")
        val o = RunSave.restore(old, Random(2))!!
        assertFalse(o.hard); assertEquals(0, o.extraSlots)
    }
}
