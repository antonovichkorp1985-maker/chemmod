package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Stage B opens with gas physics that is computed, not looked up. Every number below is a
 * measured value the model must land near: air and SF6 densities, argon and SF6 buoyancy,
 * and the Jones lower flammability limits for methane and propane. The upper limit is only
 * asserted to bound the lower one, because Jones' UFL is the weak half of the correlation.
 */
class GasPhaseTest {
    @Test
    fun `air density at room conditions comes out of the ideal gas law`() {
        val air = GasPhase.densityKilogramsPerCubicMeter(28.97, 293.15)
        assertTrue(abs(air - 1.204) < 0.02, "air density $air vs 1.204 kg/m3")
    }

    @Test
    fun `heavy gases are denser than air by their molar mass ratio`() {
        // SF6 M=146.06 is ~5x air; argon ~1.38x. These are why they pool in low spots.
        assertTrue(abs(GasPhase.buoyancyRelativeToAir(146.06) - 5.04) < 0.05)
        assertTrue(abs(GasPhase.buoyancyRelativeToAir(39.95) - 1.38) < 0.02)
        // Methane rises.
        assertTrue(GasPhase.buoyancyRelativeToAir(16.04) < 1.0)
    }

    @Test
    fun `methane lower flammability limit follows Jones from stoichiometry`() {
        val limits = GasPhase.flammabilityLimits(mapOf("C" to 1, "H" to 4))!!
        assertTrue(abs(limits.stoichiometricVolumePercent - 9.5) < 0.2, "C_st ${limits.stoichiometricVolumePercent}")
        assertTrue(abs(limits.lowerVolumePercent - 5.0) < 0.5, "LFL ${limits.lowerVolumePercent} vs 5.0")
        assertTrue(limits.upperVolumePercent > limits.lowerVolumePercent)
    }

    @Test
    fun `propane lower flammability limit follows Jones`() {
        val limits = GasPhase.flammabilityLimits(mapOf("C" to 3, "H" to 8))!!
        assertTrue(abs(limits.lowerVolumePercent - 2.1) < 0.4, "LFL ${limits.lowerVolumePercent} vs 2.1")
    }

    @Test
    fun `non-fuels and halogenated fuels are outside the rule`() {
        assertNull(GasPhase.flammabilityLimits(mapOf("C" to 1, "O" to 2)))          // CO2
        assertNull(GasPhase.flammabilityLimits(mapOf("H" to 2, "O" to 1)))          // water
        assertNull(GasPhase.flammabilityLimits(mapOf("C" to 1, "Cl" to 4)))         // CCl4
    }
}
