package com.hilotrainer.engine

import org.junit.Test
import kotlin.random.Random

class RunSimTest {
    /** A plain bot: basic strategy, stake 2 (or 1 when the boss bans repeats), buys the dearest perk it can afford. */
    fun playRun(seed: Int, deck: StartDeck = StartDeck.STANDARD): Run {
        val rnd = Random(seed)
        val r = Run(deck, rnd)
        var guard = 0
        while (r.phase != RunPhase.OVER && r.phase != RunPhase.WON && guard++ < 20000) {
            when (r.phase) {
                RunPhase.SELECT -> r.startBlind()
                RunPhase.PLAY -> {
                    if (!r.inRound) {
                        val s = if (r.activeBoss?.noRepeatStake == true) (if (r.stake == 2) 3 else 2) else 2
                        r.deal(s)
                    } else {
                        val a = r.gs.basicAdvice?.action ?: Action.STAND
                        if (!r.act(a)) { if (!r.act(Action.HIT)) r.act(Action.STAND) }
                    }
                }
                RunPhase.CLEARED -> r.collect()
                RunPhase.SHOP -> {
                    var bought = true
                    while (bought) {
                        bought = false
                        val best = r.offers.indices.filter { r.canBuy(it) && r.offers[it].perk != null }.maxByOrNull { r.offers[it].cost }
                        if (best != null) { r.buy(best); bought = true }
                    }
                    r.leaveShop()
                }
                else -> {}
            }
        }
        return r
    }

    @Test fun balanceReport() {
        val n = 400
        val reach = IntArray(Run.MAX_ANTE + 2)
        var wins = 0
        val clearedByBlind = IntArray(Run.MAX_ANTE * 3 + 1)
        for (seed in 1..n) {
            val r = playRun(seed)
            val idx = (r.ante - 1) * 3 + r.blind
            for (i in 0..idx - (if (r.phase == RunPhase.WON) 0 else 0)) clearedByBlind[i]++
            if (r.phase == RunPhase.WON) wins++
            reach[r.ante]++
        }
        println("RUNSIM wins=$wins/$n reachedAnte=${reach.toList()}")
        println("RUNSIM reachedBlind=${clearedByBlind.toList()}")
    }
}
