package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.parse.MoleculeParseException
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MoleculeTest {
    @Test
    fun `ethanol acceptance fixture`() {
        val ethanol = Molecule.fromSMILESlike("CCO")

        assertTrue(ethanol.validate().isEmpty())
        assertEquals("C2H6O", ethanol.formula())
        assertNear(46.069, ethanol.molarMass(), 0.01)
        assertNear(64.0, ethanol.properties().boilingPointC!!, 0.1)
    }

    @Test
    fun `atom traversal does not change canonical identity`() {
        val leftToRight = Molecule.fromSMILESlike("CCO")
        val rightToLeft = Molecule.fromSMILESlike("OCC")

        assertEquals(leftToRight.canonicalKey(), rightToLeft.canonicalKey())
        assertEquals(leftToRight.canonicalId(), rightToLeft.canonicalId())
    }

    @Test
    fun `structural isomers have distinct identity and properties`() {
        val ethanol = Molecule.fromSMILESlike("CCO")
        val dimethylEther = Molecule.fromSMILESlike("COC")

        assertEquals("C2H6O", dimethylEther.formula())
        assertNotEquals(ethanol.canonicalKey(), dimethylEther.canonicalKey())
        assertNear(-2.0, dimethylEther.properties().boilingPointC!!, 0.1)
        assertNotEquals(ethanol.properties().boilingPointC, dimethylEther.properties().boilingPointC)
    }

    @Test
    fun `branches and rings produce the expected formula`() {
        assertEquals("C4H10", Molecule.fromSMILESlike("CC(C)C").formula())
        assertEquals("C3H6", Molecule.fromSMILESlike("C1CC1").formula())
        assertNotEquals(
            Molecule.fromSMILESlike("CCCC").canonicalKey(),
            Molecule.fromSMILESlike("CC(C)C").canonicalKey(),
        )
    }

    @Test
    fun `valence violations are reported`() {
        val impossibleCarbon = Molecule.fromSMILESlike("C(C)(C)(C)(C)C")

        val issue = impossibleCarbon.validate().single()
        assertEquals("VALENCE_EXCEEDED", issue.code)
        assertTrue(issue.message.contains("valence 5"))
    }

    @Test
    fun `syntax errors include their position`() {
        val error = assertFailsWith<MoleculeParseException> {
            Molecule.fromSMILESlike("CC(")
        }
        assertTrue(error.message!!.contains("position"))
    }

    @Test
    fun `water and methanol fixtures`() {
        val water = Molecule.fromSMILESlike("O")
        val methanol = Molecule.fromSMILESlike("CO")

        assertEquals("H2O", water.formula())
        assertNear(100.0, water.properties().boilingPointC!!, 0.1)
        assertNear(65.0, methanol.properties().boilingPointC!!, 0.1)
    }

    private fun assertNear(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(abs(expected - actual) <= tolerance, "Expected $expected ± $tolerance, got $actual")
    }
}
