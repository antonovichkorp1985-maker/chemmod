package io.github.antonovichkorp.chemmod.core.radiation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The nuclear layer used to hold exactly one decay family, written in Kotlin. These tests pin the
 * data-driven version: nuclides come from `chemmod/isotopes.json`, every one of them belongs to
 * the element table, and all four decay modes are exercised by real nuclides rather than only
 * being declared in the enum.
 */
class IsotopeDataTest {
    private val data = IsotopeData.default()
    private val network = data.network()

    @Test
    fun `every nuclide in the data belongs to the element table`() {
        assertTrue(data.all().isNotEmpty())
        data.all().forEach { isotope ->
            assertEquals(
                isotope.nuclide.symbol,
                ElementTableStub.symbolOf(isotope.nuclide.atomicNumber),
                "${isotope.nuclide.displayName} does not match the element table",
            )
        }
    }

    @Test
    fun `all four decay modes are used by data, not only declared`() {
        val used = data.all().flatMap { it.scheme.branches.map { branch -> branch.mode } }.toSet()
        assertEquals(
            setOf(DecayMode.ALPHA, DecayMode.BETA_MINUS, DecayMode.BETA_PLUS, DecayMode.ELECTRON_CAPTURE),
            used,
        )
    }

    @Test
    fun `potassium-40 decays by beta minus and by electron capture`() {
        val potassium40 = network.scheme(Nuclide(19, 40, "K"))
        assertEquals(1.248e9 * JULIAN_YEAR_SECONDS, potassium40.halfLifeSeconds)
        val daughters = potassium40.branches.associate { it.mode to it.daughter.displayName }
        assertEquals("Ca-40", daughters[DecayMode.BETA_MINUS])
        assertEquals("Ar-40", daughters[DecayMode.ELECTRON_CAPTURE])
        assertEquals(1.0, potassium40.branches.sumOf { it.branchingFraction }, 1.0e-12)
    }

    @Test
    fun `positron emission lowers the nuclear charge and keeps the mass number`() {
        val branch = network.scheme(Nuclide(19, 38, "K")).branches.single()
        assertEquals(DecayMode.BETA_PLUS, branch.mode)
        assertEquals(18, branch.daughter.atomicNumber)
        assertEquals(38, branch.daughter.massNumber)
        assertEquals("Ar", branch.daughter.symbol)
    }

    @Test
    fun `carbon-14 becomes nitrogen-14`() {
        val carbon14 = network.scheme(Nuclide(6, 14, "C"))
        assertEquals(5700.0 * JULIAN_YEAR_SECONDS, carbon14.halfLifeSeconds)
        val branch = carbon14.branches.single()
        assertEquals(DecayMode.BETA_MINUS, branch.mode)
        assertEquals("N-14", branch.daughter.displayName)
        assertEquals(0.1565, branch.releasedEnergyMeV)
    }

    @Test
    fun `stable isotopes carry no activity`() {
        assertTrue(network.isStable(Nuclide(6, 12, "C")))
        assertEquals(0.0, network.decayConstantPerSecond(Nuclide(6, 12, "C")))
        assertEquals(0.0, network.activityBecquerel(Nuclide(19, 39, "K"), 1.0))
        assertNull(network.scheme(Nuclide(2, 4, "He")).halfLifeSeconds)
    }

    @Test
    fun `the data reproduces the thorium series that used to be hardcoded`() {
        Thorium232Series.network.nuclides.forEach { nuclide ->
            val hardcoded = Thorium232Series.network.scheme(nuclide)
            val fromData = network.scheme(nuclide)
            assertEquals(hardcoded.halfLifeSeconds, fromData.halfLifeSeconds, nuclide.displayName)
            assertEquals(
                hardcoded.branches.map { Triple(it.mode, it.daughter, it.branchingFraction) }.toSet(),
                fromData.branches.map { Triple(it.mode, it.daughter, it.branchingFraction) }.toSet(),
                nuclide.displayName,
            )
        }
    }

    @Test
    fun `every branch of the thorium series ends at lead-208`() {
        val fractions = network.branchFractionsFrom(Nuclide(90, 232, "Th"))
        assertEquals(1.0, fractions.getValue(Nuclide(82, 208, "Pb")), 1.0e-12)
    }

    @Test
    fun `elements without nuclear data are reported as absent, not as stable`() {
        assertNull(data.find("U", 238))
        assertTrue(data.of("U").isEmpty())
    }

    private object ElementTableStub {
        private val table = io.github.antonovichkorp.chemmod.core.model.ElementTable.default()
            .all()
            .associateBy { it.atomicNumber }

        fun symbolOf(atomicNumber: Int): String = requireNotNull(table[atomicNumber]).symbol
    }

    private companion object {
        const val JULIAN_YEAR_SECONDS = 31_557_600.0
    }
}
