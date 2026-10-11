package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.model.Element
import io.github.antonovichkorp.chemmod.core.model.ElementTable
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Bond character derived from one atomic property. The point of these tests is that the same
 * number comes out for substances nobody enumerated: sodium chloride is ionic because of Δχ, not
 * because a row says so.
 */
class BondCharacterTest {
    private fun element(symbol: String, chi: Double?) =
        Element(symbol, symbol, 1, 1.0, listOf(1), 1, null, chi)

    @Test
    fun `an alkali halide bond comes out ionic from the electronegativity difference alone`() {
        val sodium = element("Na", 0.93)
        val chlorine = element("Cl", 3.16)
        val fraction = BondCharacter.ionicFraction(sodium, chlorine)!!
        assertEquals(0.711, fraction, 0.002, "NaCl did not read as mostly ionic")
        assertEquals(true, BondCharacter.isIonic(sodium, chlorine))
    }

    @Test
    fun `a hydrogen halide bond is polar covalent, not ionic`() {
        val hydrogen = element("H", 2.20)
        val chlorine = element("Cl", 3.16)
        val fraction = BondCharacter.ionicFraction(hydrogen, chlorine)!!
        assertEquals(0.206, fraction, 0.002, "HCl was misclassified")
        assertEquals(false, BondCharacter.isIonic(hydrogen, chlorine))
    }

    @Test
    fun `a bond between identical atoms carries no ionic character at all`() {
        val chlorine = element("Cl", 3.16)
        assertEquals(0.0, BondCharacter.ionicFraction(chlorine, chlorine)!!, 1e-12)
        assertTrue(BondCharacter.isPurelyCovalent(chlorine, chlorine))
    }

    @Test
    fun `the result does not depend on which atom is named first`() {
        val sodium = element("Na", 0.93)
        val chlorine = element("Cl", 3.16)
        assertEquals(
            BondCharacter.ionicFraction(sodium, chlorine),
            BondCharacter.ionicFraction(chlorine, sodium),
        )
    }

    @Test
    fun `a missing electronegativity reports no model rather than a non-polar bond`() {
        val known = element("Cl", 3.16)
        val unknown = element("Xx", null)
        assertNull(BondCharacter.ionicFraction(known, unknown))
        assertNull(BondCharacter.ionicFraction(unknown, known))
        assertNull(BondCharacter.isIonic(known, unknown))
        assertFalse(
            BondCharacter.isPurelyCovalent(known, unknown),
            "an unknown atom was reported as a purely covalent partner",
        )
    }

    @Test
    fun `the bundled table really carries the property this derivation runs on`() {
        val table = ElementTable.default()
        val withChi = table.all().count { it.electronegativity != null }
        assertTrue(withChi >= 60, "only $withChi elements carry an electronegativity")
        val sodium = table.require("Na")
        val chlorine = table.require("Cl")
        assertEquals(0.93, sodium.electronegativity!!, 1e-9)
        assertTrue(
            abs(BondCharacter.ionicFraction(sodium, chlorine)!! - 0.711) < 0.002,
            "the bundled data did not reproduce the ionic fraction for NaCl",
        )
    }
}
