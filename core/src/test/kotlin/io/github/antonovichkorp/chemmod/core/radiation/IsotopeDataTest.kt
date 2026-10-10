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
    fun `the uranium series runs from uranium-238 to lead-206`() {
        val uranium238 = network.scheme(Nuclide(92, 238, "U"))
        assertEquals(4.468e9 * JULIAN_YEAR_SECONDS, uranium238.halfLifeSeconds)
        assertEquals("Th-234", uranium238.branches.single().daughter.displayName)
        // Radium-226 is the radon source in ore: Ra-226 -> Rn-222 -> Po-218.
        assertEquals("Rn-222", network.scheme(Nuclide(88, 226, "Ra")).branches.single().daughter.displayName)
        assertEquals("Po-218", network.scheme(Nuclide(86, 222, "Rn")).branches.single().daughter.displayName)
        assertEquals("Pb-206", network.scheme(Nuclide(84, 210, "Po")).branches.single().daughter.displayName)
        assertEquals(1.0, network.branchFractionsFrom(Nuclide(92, 238, "U")).getValue(Nuclide(82, 206, "Pb")), 1.0e-12)
    }

    @Test
    fun `the neptunium series carries americium-241 and plutonium-241 and ends at thallium-205`() {
        val americium = network.scheme(Nuclide(95, 241, "Am"))
        assertEquals(432.6 * JULIAN_YEAR_SECONDS, americium.halfLifeSeconds)
        assertEquals("Np-237", americium.branches.single().daughter.displayName)
        val plutonium = network.scheme(Nuclide(94, 241, "Pu")).branches.associate { it.mode to it.daughter.displayName }
        assertEquals("Am-241", plutonium[DecayMode.BETA_MINUS])
        assertEquals("U-237", plutonium[DecayMode.ALPHA])
        assertEquals(1.0, network.branchFractionsFrom(Nuclide(98, 249, "Cf")).getValue(Nuclide(81, 205, "Tl")), 1.0e-12)
    }

    @Test
    fun `plutonium-240 and uranium-236 feed the thorium series from above`() {
        assertEquals("U-236", network.scheme(Nuclide(94, 240, "Pu")).branches.single().daughter.displayName)
        assertEquals("Th-232", network.scheme(Nuclide(92, 236, "U")).branches.single().daughter.displayName)
    }

    @Test
    fun `every radioactive nuclide spends exactly one decay per parent`() {
        data.all().filter { !it.isStable }.forEach { isotope ->
            assertEquals(1.0, isotope.scheme.branches.sumOf { it.branchingFraction }, 1.0e-12, isotope.nuclide.displayName)
        }
    }

    @Test
    fun `branches whose release energy is not sourced are listed, not silently zero`() {
        // Half-lives for these five come from the decay-chain tables; their Q values did not, and
        // the data carries 0.0 rather than an invented number.
        val unsourced = data.all()
            .filter { isotope -> isotope.scheme.branches.any { it.releasedEnergyMeV == 0.0 } }
            .map { it.nuclide.displayName }
            .toSet()
        assertEquals(
            setOf("Pb-214", "Bi-214", "Po-214", "Pb-210", "Bi-210", "Co-60", "I-131", "Pu-238"),
            unsourced,
        )
    }

    @Test
    fun `the actinium series runs from uranium-235 to lead-207`() {
        val uranium235 = network.scheme(Nuclide(92, 235, "U"))
        assertEquals(7.04e8 * JULIAN_YEAR_SECONDS, uranium235.halfLifeSeconds)
        assertEquals("Th-231", uranium235.branches.single().daughter.displayName)
        // Actinium-227 is the fork: 98.62 % beta minus, 1.38 % alpha.
        val actinium = network.scheme(Nuclide(89, 227, "Ac")).branches.associate { it.mode to it.daughter.displayName }
        assertEquals("Th-227", actinium[DecayMode.BETA_MINUS])
        assertEquals("Fr-223", actinium[DecayMode.ALPHA])
        assertEquals(1.0, network.branchFractionsFrom(Nuclide(92, 235, "U")).getValue(Nuclide(82, 207, "Pb")), 1.0e-12)
    }

    @Test
    fun `plutonium-239 and plutonium-238 are alpha emitters into uranium`() {
        val plutonium239 = network.scheme(Nuclide(94, 239, "Pu")).branches.single()
        assertEquals(2.411e4 * JULIAN_YEAR_SECONDS, network.scheme(Nuclide(94, 239, "Pu")).halfLifeSeconds)
        assertEquals("U-235", plutonium239.daughter.displayName)
        assertEquals(5.244, plutonium239.releasedEnergyMeV)
        assertEquals("U-234", network.scheme(Nuclide(94, 238, "Pu")).branches.single().daughter.displayName)
    }

    @Test
    fun `the isotopes a fuel cycle and a laboratory actually meet are present`() {
        // Tritium for fusion: H-3 -> He-3, endpoint energy 18.6 keV (NRC E-35).
        val tritium = network.scheme(Nuclide(1, 3, "H")).branches.single()
        assertEquals(12.32 * JULIAN_YEAR_SECONDS, network.scheme(Nuclide(1, 3, "H")).halfLifeSeconds)
        assertEquals(DecayMode.BETA_MINUS, tritium.mode)
        assertEquals("He-3", tritium.daughter.displayName)
        // Sr-90 -> Y-90 -> stable Zr-90, the fallout pair.
        assertEquals("Y-90", network.scheme(Nuclide(38, 90, "Sr")).branches.single().daughter.displayName)
        assertEquals("Zr-90", network.scheme(Nuclide(39, 90, "Y")).branches.single().daughter.displayName)
        assertTrue(network.isStable(Nuclide(40, 90, "Zr")))
        // Co-60 -> Ni-60 and I-131 -> Xe-131.
        assertEquals("Ni-60", network.scheme(Nuclide(27, 60, "Co")).branches.single().daughter.displayName)
        assertEquals(8.02 * DAY_SECONDS, network.scheme(Nuclide(53, 131, "I")).halfLifeSeconds)
        assertEquals("Xe-131", network.scheme(Nuclide(53, 131, "I")).branches.single().daughter.displayName)
    }

    @Test
    fun `elements without nuclear data are reported as absent, not as stable`() {
        // Caesium-137 decays through the Ba-137m isomer; Nuclide has no isomer label, so the pair
        // is absent rather than misrepresented as a direct decay to stable barium.
        assertNull(data.find("Cs", 137))
        assertTrue(data.of("Tc").isEmpty())
        assertTrue(data.of("Cs").isEmpty())
    }

    private object ElementTableStub {
        private val table = io.github.antonovichkorp.chemmod.core.model.ElementTable.default()
            .all()
            .associateBy { it.atomicNumber }

        fun symbolOf(atomicNumber: Int): String = requireNotNull(table[atomicNumber]).symbol
    }

    private companion object {
        const val JULIAN_YEAR_SECONDS = 31_557_600.0
        const val DAY_SECONDS = 86_400.0
    }
}
