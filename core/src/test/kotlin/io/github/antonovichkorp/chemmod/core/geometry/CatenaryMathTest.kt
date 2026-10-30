package io.github.antonovichkorp.chemmod.core.geometry

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatenaryMathTest {

    @Test
    fun `taut cable with minimal sag returns small parameter a`() {
        // 10m span, 10.1m cable (1% extra length) -> very taut, small sag
        val result = CatenaryMath.solveParameter(
            spanMetres = 10.0,
            cableLengthMetres = 10.1,
            weightPerMetreNewtons = 1.0,
        )
        assertNotNull(result)
        val r = result!!
        assertTrue(r.maxSagMetres < 1.0, "Taut cable should have small sag, got ${r.maxSagMetres}")
        assertTrue(r.horizontalTensionNewtons > 10.0, "Taut cable has high tension")
    }

    @Test
    fun `cable with moderate sag`() {
        // 10m span, 11m cable -> noticeable sag
        val result = CatenaryMath.solveParameter(
            spanMetres = 10.0,
            cableLengthMetres = 11.0,
            weightPerMetreNewtons = 1.0,
        )
        assertNotNull(result)
        val r = result!!
        assertTrue(r.maxSagMetres > 1.0 && r.maxSagMetres < 5.0)
        assertTrue(r.horizontalTensionNewtons > 5.0)
    }

    @Test
    fun `heavier cable has same sag but more tension for same length`() {
        val light = CatenaryMath.solveParameter(10.0, 11.0, 1.0)!!
        val heavy = CatenaryMath.solveParameter(10.0, 11.0, 10.0)!!
        assertEquals(light.maxSagMetres, heavy.maxSagMetres, 1e-6, "Same span+length = same sag")
        assertTrue(heavy.horizontalTensionNewtons > light.horizontalTensionNewtons, "Heavier cable has more tension")
    }

    @Test
    fun `cable shorter than span returns null`() {
        val result = CatenaryMath.solveParameter(
            spanMetres = 10.0,
            cableLengthMetres = 9.0,
            weightPerMetreNewtons = 1.0,
        )
        assertNull(result)
    }

    @Test
    fun `zero or negative span rejected`() {
        assertNull(CatenaryMath.solveParameter(0.0, 10.0, 1.0))
        assertNull(CatenaryMath.solveParameter(-1.0, 10.0, 1.0))
        assertNull(CatenaryMath.solveParameter(10.0, 5.0, 1.0))
    }

    @Test
    fun `heightAt returns zero at supports and negative in middle`() {
        val result = CatenaryMath.solveParameter(10.0, 11.0, 1.0)!!
        val left = CatenaryMath.heightAt(0.0, 10.0, result)
        val mid = CatenaryMath.heightAt(5.0, 10.0, result)
        val right = CatenaryMath.heightAt(10.0, 10.0, result)
        assertEquals(0.0, left, 1e-6)
        assertEquals(0.0, right, 1e-6)
        assertTrue(mid < 0.0, "Midpoint should be below supports")
    }

    @Test
    fun `tensionAt is minimum at midpoint, maximum at supports`() {
        val result = CatenaryMath.solveParameter(10.0, 11.0, 1.0)!!
        val tMid = CatenaryMath.tensionAt(5.0, 10.0, result)
        val tEnd = CatenaryMath.tensionAt(0.0, 10.0, result)
        assertTrue(tMid < tEnd, "Midpoint tension < support tension")
    }
}