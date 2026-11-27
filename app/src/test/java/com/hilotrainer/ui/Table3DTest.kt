package com.hilotrainer.ui

import com.hilotrainer.engine.Cast
import com.hilotrainer.engine.Extra
import com.hilotrainer.engine.Hair
import com.hilotrainer.engine.Hat
import com.hilotrainer.engine.Look
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Table3DTest {
    @Test fun personMapsLookToStage() {
        val look = Look(0xFFD9B08C, 0xFF111111, Hair.SLICK, 0xFF16161A, 0xFFB0202A, hat = Hat.FEDORA, extras = listOf(Extra.SUNGLASSES, Extra.BEARD))
        val j = Table3D.person(look, "Boss")
        assertEquals("slick", j.getString("style"))
        assertEquals("#D9B08C", j.getString("skin"))
        assertTrue(j.getBoolean("glasses")); assertTrue(j.getBoolean("shades")); assertTrue(j.getBoolean("beard"))
        assertEquals("fedora", j.getString("hat"))
        assertEquals("none", Table3D.person(look.copy(hairStyle = Hair.BALD), "x").getString("style"))
        assertEquals("long", Table3D.person(look.copy(hairStyle = Hair.PONYTAIL), "x").getString("style"))
    }

    @Test fun stageJsonHasEveryField() {
        val v = Venues.ALL[0]
        val d = RunDealer("Vera", Cast.ALL[0].look, false)
        val j = org.json.JSONObject(Table3D.stage(v, d, listOf(Cast.ALL[0], Cast.ALL[1]), false, true, 1))
        for (k in listOf("bg", "base", "light", "accent", "boss", "motion", "q", "dealer", "bots")) assertTrue(k, j.has(k))
        assertEquals(2, j.getJSONArray("bots").length())
    }

    @Test fun assetsShipWithTheApp() {
        val dir = File(System.getProperty("user.dir"), "src/main/assets/table3d")
        assertTrue(File(dir, "stage.html").length() > 10_000)
        assertTrue(File(dir, "three.min.js").length() > 400_000)
        assertTrue(File(dir, "stage.html").readText().contains("window.setStage"))
    }
}
