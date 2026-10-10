package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.model.ElementTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The non-covalent layer has to reach the property view: hydrogen-bond sites, aromatic rings and
 * Lewis-acid vacancies become flags and numbers instead of staying hidden in a helper class.
 */
class InteractionPropertiesTest {
    private val engine = PropertyEngine(PropertyRuleSet.bundled())

    private fun properties(structure: String) = engine.predict(Molecule.fromSMILESlike(structure).graph)

    @Test
    fun `water reports both hydrogen-bond roles`() {
        val water = properties("O")
        assertEquals(2, water.hydrogenBondDonors)
        assertEquals(2, water.hydrogenBondAcceptors)
        assertEquals(42.0, water.hydrogenBondNetworkKilojoulesPerMole)
        assertTrue("HYDROGEN_BOND_DONOR" in water.flags)
        assertTrue("HYDROGEN_BOND_ACCEPTOR" in water.flags)
    }

    @Test
    fun `a carbonyl is flagged as an acceptor only`() {
        val acetone = properties("CC(C)=O")
        assertEquals(0, acetone.hydrogenBondDonors)
        assertEquals(2, acetone.hydrogenBondAcceptors)
        assertFalse("HYDROGEN_BOND_DONOR" in acetone.flags)
        assertTrue("HYDROGEN_BOND_ACCEPTOR" in acetone.flags)
    }

    @Test
    fun `benzene carries an aromatic ring and its resonance energy`() {
        val benzene = properties("C1=CC=CC=C1")
        assertEquals(1, benzene.aromaticRings)
        assertEquals(150.0, benzene.aromaticResonanceKilojoulesPerMole)
        assertTrue("AROMATIC_RING" in benzene.flags)
    }

    @Test
    fun `cyclohexane stays a plain ring`() {
        val cyclohexane = properties("C1CCCCC1")
        assertEquals(0, cyclohexane.aromaticRings)
        assertEquals(0.0, cyclohexane.aromaticResonanceKilojoulesPerMole)
        assertFalse("AROMATIC_RING" in cyclohexane.flags)
        assertTrue("CYCLIC_STRUCTURE" in cyclohexane.flags)
    }

    @Test
    fun `boron trifluoride is flagged as a coordinate-bond acceptor`() {
        val boronTrifluoride = properties("FB(F)F")
        assertEquals(1, boronTrifluoride.coordinateBondAcceptors)
        assertTrue("COORDINATE_BOND_ACCEPTOR" in boronTrifluoride.flags)
    }

    @Test
    fun `a hydrocarbon earns none of the new flags`() {
        val ethane = properties("CC")
        assertEquals(0, ethane.hydrogenBondDonors)
        assertEquals(0, ethane.hydrogenBondAcceptors)
        assertEquals(0, ethane.aromaticRings)
        assertEquals(0, ethane.coordinateBondAcceptors)
        assertFalse(ethane.flags.any { it.startsWith("HYDROGEN_BOND") })
    }

    @Test
    fun `valence electrons are part of the element content, not a code table`() {
        val table = ElementTable.default()
        val expected = mapOf(
            "H" to 1, "He" to 2, "Li" to 1, "B" to 3, "C" to 4, "N" to 5, "O" to 6, "F" to 7,
            "Ne" to 8, "Na" to 1, "S" to 6, "Cl" to 7, "Fe" to 8, "Zn" to 2, "Kr" to 8,
        )
        expected.forEach { (symbol, electrons) ->
            assertEquals(electrons, table.require(symbol).valenceElectrons, "$symbol valence electrons")
        }
        assertTrue(table.all().all { it.valenceElectrons > 0 })
    }
}
