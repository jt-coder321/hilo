package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HeatTest {
    private val unit = 10

    @Test fun streakNeedsEvidenceAndReadsRealResults() {
        val s = TableStreak()
        s.record(3, 0); s.record(3, 0)
        assertEquals(0, s.level)
        s.record(2, 1); s.record(4, 0)
        assertEquals(2, s.level)
        assertEquals("HOT", TableStreak.label(s.level))
        repeat(6) { s.record(0, 3) }
        assertEquals(-2, s.level)
        assertEquals("COLD", TableStreak.label(s.level))
        s.clear()
        assertEquals(0, s.level)
    }

    @Test fun streakLinesHaveNoDashes() {
        (TableStreak.hotTalk + TableStreak.coldTalk + TableStreak.warmTalk + TableStreak.coolTalk + (-2..2).map { TableStreak.note(it) }).forEach {
            assertFalse(it, it.contains('—') || it.contains('–') || it.contains(" - "))
        }
    }

    @Test fun bigBetsAndBigWinsRaiseHeatSmallBetsCoolIt() {
        val h = PlayerHeat()
        assertTrue(h.delta(500, 500, unit) >= 3)
        assertEquals(-1, h.delta(0, 0, unit))
        assertEquals(-1, h.delta(20, 20, unit) + 0)
        assertTrue(h.delta(20, -20, unit) < 0)
        h.force(6); h.tip(); assertEquals(4, h.level)
        h.tip(); h.tip(); h.tip(); assertEquals(0, h.level)
    }

    @Test fun pitBossWatchesThenActsThenSendsYouToTheRailThenReleases() {
        val h = PlayerHeat()
        val rnd = Random(3)
        var move = PitMove.NONE
        var rounds = 0
        val seen = mutableListOf<PitMove>()
        while (rounds++ < 20 && PitMove.ASKED_TO_LEAVE !in seen) { move = h.afterRound(600, 600, unit, rnd); if (move != PitMove.NONE) seen += move }
        assertEquals(PitMove.WATCH, seen.first())
        assertTrue(seen.toString(), PitMove.ASKED_TO_LEAVE in seen)
        assertEquals(PlayerHeat.COOLOFF, h.cooloff)
        var releases = 0
        repeat(PlayerHeat.COOLOFF) { if (h.afterRound(0, 0, unit, rnd) == PitMove.RELEASE) releases++ }
        assertEquals(1, releases)
        assertEquals(0, h.cooloff)
    }

    @Test fun flatBetsLastAFewHandsThenRelease() {
        var found = false
        for (seed in 0..40) {
            val h = PlayerHeat(); val rnd = Random(seed)
            h.force(5)
            assertEquals(PitMove.WATCH, h.afterRound(300, 0, unit, rnd))
            var move = PitMove.NONE
            var n = 0
            while (n++ < 6 && move != PitMove.FLAT_BET && move != PitMove.SHUFFLE_UP) { h.force(7); move = h.afterRound(300, 0, unit, rnd) }
            if (move == PitMove.FLAT_BET) {
                assertEquals(PlayerHeat.FLAT_ROUNDS, h.flatLeft)
                var out = PitMove.NONE
                repeat(PlayerHeat.FLAT_ROUNDS) { out = h.afterRound(20, 0, unit, rnd) }
                assertEquals(PitMove.RELEASE, out); assertEquals(0, h.flatLeft); found = true; break
            }
        }
        assertTrue(found)
    }

    @Test fun friendlyPlayIsLeftAlone() {
        val h = PlayerHeat(); val rnd = Random(1)
        repeat(60) { assertEquals(PitMove.NONE, h.afterRound(10, if (it % 2 == 0) 10 else -10, unit, rnd)) }
        assertEquals(0, h.level)
    }
}
