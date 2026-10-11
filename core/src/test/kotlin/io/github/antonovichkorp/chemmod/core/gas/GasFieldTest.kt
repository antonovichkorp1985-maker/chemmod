package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage B's transport and enclosed-volume physics, checked against measured values where they
 * exist (the N2-O2 binary coefficient) and against conservation/stratification laws otherwise.
 */
class GasFieldTest {
    @Test
    fun `nitrogen-oxygen binary diffusion lands near the measured value`() {
        val d = GasTransport.binaryDiffusionSquareMetersPerSecond("N2", "O2", 28.013, 31.999, 298.15)!!
        assertTrue(d in 1.6e-5..2.4e-5, "D(N2-O2) $d vs ~1.9e-5 m2/s")
    }

    @Test
    fun `diffusion grows with temperature and reports gaps honestly`() {
        val cold = GasTransport.binaryDiffusionSquareMetersPerSecond("CH4", "N2", 16.04, 28.013, 298.15)!!
        val hot = GasTransport.binaryDiffusionSquareMetersPerSecond("CH4", "N2", 16.04, 28.013, 600.0)!!
        assertTrue(hot > cold)
        assertNull(GasTransport.binaryDiffusionSquareMetersPerSecond("Xx", "N2", 1.0, 28.013, 298.15))
    }

    @Test
    fun `le chatelier makes a blend more flammable than its leanest member`() {
        val mix = GasPhase.mixtureLowerVolumePercent(listOf(0.5, 0.5), listOf(5.0, 2.1))
        assertTrue(abs(mix - 2.96) < 0.1, "mixture LFL $mix vs 2.96")
    }

    @Test
    fun `limiting oxygen concentration is indicative but sane`() {
        val loc = GasPhase.limitingOxygenConcentration(mapOf("C" to 1, "H" to 4))!!
        assertTrue(loc in 8.0..14.0, "methane LOC $loc vs ~12")
        assertNull(GasPhase.limitingOxygenConcentration(mapOf("C" to 1, "Cl" to 4)))
    }

    @Test
    fun `a closed column conserves its trace gas under diffusion`() {
        val column = GasColumn(1.0, 8).apply { fill(0.1) }
        val before = column.total()
        repeat(10) { column.advance(diffusion = 0.3, settling = 0.0, ventilation = 0.0) }
        assertTrue(abs(column.total() - before) < 1e-9)
    }

    @Test
    fun `heavy gas pools at the bottom of a closed column`() {
        val column = GasColumn(densityRatio = 5.0, layers = 8).apply { fill(0.1) }
        repeat(20) { column.advance(diffusion = 0.0, settling = 0.3, ventilation = 0.0) }
        assertTrue(column.fraction.first() > column.fraction.last(),
            "bottom ${column.fraction.first()} vs top ${column.fraction.last()}")
    }

    @Test
    fun `ventilation clears the volume monotonically`() {
        val column = GasColumn(1.0, 4).apply { fill(0.1) }
        val first = column.total()
        column.advance(0.0, 0.0, ventilation = 0.5)
        assertTrue(abs(column.total() - first * 0.5) < 1e-9)
    }
}
