package com.hilotrainer.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DistractionsTest {
    private val table = Distractions.Table(decks = 6, minBet = 10, players = listOf("Ray", "Linda"), yourBet = 30)

    @Test fun realPlansAreBusyAndVaried() {
        val plans = (0 until 300).map { Distractions.plan(true, table, Random(it)) }
        val busy = plans.count { it.isNotEmpty() }
        assertTrue("most rounds have something: $busy", busy in 220..290)
        assertTrue(plans.all { p -> p.count { it.kind == Distractions.Kind.QUESTION } <= 1 })
        assertTrue(plans.any { it.size == 3 })
        // Every kind shows up, and the content and timing are rarely the same twice.
        Distractions.Kind.entries.forEach { k -> assertTrue("$k", plans.flatten().any { it.kind == k }) }
        val signatures = plans.filter { it.isNotEmpty() }.map { p -> p.joinToString { "${it.kind}${it.atMs}${it.text}" } }
        assertEquals(signatures.size, signatures.toSet().size)
        val texts = plans.flatten().filter { it.kind == Distractions.Kind.NUMBERS }.map { it.text }
        assertTrue(texts.toSet().size > texts.size * 0.8)
        assertTrue(texts.all { t -> t.any { it.isDigit() } })
    }

    @Test fun lightNeverAsksAnything() {
        val plans = (0 until 300).map { Distractions.plan(false, table, Random(it)) }
        assertTrue(plans.all { it.size <= 1 })
        assertTrue(plans.flatten().none { it.kind == Distractions.Kind.QUESTION || it.kind == Distractions.Kind.WALK_BY })
    }

    @Test fun factualQuestionsHaveTheRightAnswer() {
        var factual = 0
        for (seed in 0 until 2000) {
            val q = Distractions.plan(true, table, Random(seed)).firstOrNull { it.kind == Distractions.Kind.QUESTION } ?: continue
            assertTrue(q.options.size in 2..3)
            assertEquals(q.options.size, q.options.toSet().size)
            val c = q.correct ?: continue
            factual++
            val right = q.options[c]
            when {
                q.text.contains("minimum") -> assertEquals("\$10", right)
                q.text.contains("decks") -> assertEquals("6", right)
                q.text.contains("betting this hand") -> assertEquals("\$30", right)
                q.text.contains("soft 17") -> assertEquals("Yes", right)
                q.text.contains("blackjack pay") -> assertEquals("3 to 2", right)
                q.text.contains("times") -> {
                    val (a, b) = Regex("(\\d+) times (\\d+)").find(q.text)!!.destructured
                    assertEquals("${a.toInt() * b.toInt()}", right)
                }
            }
        }
        assertTrue(factual > 50)
    }
}
