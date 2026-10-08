package io.github.antonovichkorp.chemmod.core.radiation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Thorium232SeriesTest {
    @Test
    fun `thorium series is complete conserves nuclei and ends at stable lead`() {
        val chain = Thorium232Series.network

        assertEquals(12, chain.nuclides.size)
        assertTrue(chain.isStable(Thorium232Series.LEAD_208))
        assertEquals(0.0, chain.decayConstantPerSecond(Thorium232Series.LEAD_208))

        val fractions = chain.branchFractionsFrom(Thorium232Series.THORIUM_232)
        assertEquals(1.0, fractions.getValue(Thorium232Series.LEAD_208), absoluteTolerance = 1.0e-12)
        assertEquals(
            Thorium232Series.BISMUTH_TO_POLONIUM_FRACTION,
            fractions.getValue(Thorium232Series.POLONIUM_212),
            absoluteTolerance = 1.0e-12,
        )
        assertEquals(
            Thorium232Series.BISMUTH_TO_THALLIUM_FRACTION,
            fractions.getValue(Thorium232Series.THALLIUM_208),
            absoluteTolerance = 1.0e-12,
        )
    }

    @Test
    fun `one gram thorium 232 has the expected order of specific activity`() {
        val activity = Thorium232Series.network.activityBecquerel(Thorium232Series.THORIUM_232, 0.001)

        // Published values are about 4.1 kBq/g. The core uses mass number in
        // place of exact isotopic molar mass, so this is intentionally a range.
        assertTrue(activity in 4_000.0..4_200.0, "Expected about 4.1 kBq/g, got $activity")

        val equilibrium = Thorium232Series.network.secularEquilibriumActivityBecquerel(
            Thorium232Series.THORIUM_232,
            activity,
        )
        assertEquals(activity, equilibrium.getValue(Thorium232Series.THORIUM_232), absoluteTolerance = 1.0e-9)
        assertEquals(
            activity * Thorium232Series.BISMUTH_TO_THALLIUM_FRACTION,
            equilibrium.getValue(Thorium232Series.THALLIUM_208),
            absoluteTolerance = 1.0e-9,
        )
    }

    @Test
    fun `network rejects an A Z violating daughter`() {
        val parent = Nuclide(90, 232, "Th")
        val impossibleDaughter = Nuclide(89, 229, "Ac")
        val error = assertFailsWith<IllegalArgumentException> {
            DecayNetwork.of(
                DecayScheme(
                    parent,
                    1.0,
                    listOf(DecayBranch(DecayMode.ALPHA, impossibleDaughter, 1.0, 1.0)),
                ),
                DecayScheme.stable(impossibleDaughter),
            )
        }

        assertTrue(error.message!!.contains("does not conserve A/Z"))
    }

    @Test
    fun `network reports an unregistered daughter instead of crashing during validation`() {
        val parent = Nuclide(90, 232, "Th")
        val daughter = Nuclide(88, 228, "Ra")
        val error = assertFailsWith<IllegalArgumentException> {
            DecayNetwork.of(
                DecayScheme(
                    parent,
                    1.0,
                    listOf(DecayBranch(DecayMode.ALPHA, daughter, 1.0, 1.0)),
                ),
            )
        }

        assertTrue(error.message!!.contains("unregistered daughter Ra-228"))
    }

    @Test
    fun `network rejects incomplete branch probabilities`() {
        val parent = Nuclide(90, 232, "Th")
        val daughter = Nuclide(88, 228, "Ra")
        val error = assertFailsWith<IllegalArgumentException> {
            DecayNetwork.of(
                DecayScheme(
                    parent,
                    1.0,
                    listOf(DecayBranch(DecayMode.ALPHA, daughter, 0.90, 1.0)),
                ),
                DecayScheme.stable(daughter),
            )
        }

        assertTrue(error.message!!.contains("branch total"))
    }
}
