package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aromaticity is a derived property of the ordinary Kekulé graph: conjugated ring plus Hückel's
 * 4n+2 rule. Nothing here invents a 1.5 bond order.
 */
class AromaticityModelTest {

    private fun graph(structure: String) = Molecule.fromSMILESlike(structure).graph

    @Test
    fun `benzene is one aromatic sextet`() {
        val benzene = graph("C1=CC=CC=C1")
        assertEquals(6, AromaticityModel.huckelPiElectrons(benzene, (0..5).toList()))
        assertEquals(1, AromaticityModel.aromaticRings(benzene).size)
        assertTrue(AromaticityModel.isAromatic(benzene))
    }

    @Test
    fun `a saturated ring is not conjugated at all`() {
        val cyclohexane = graph("C1CCCCC1")
        assertNull(AromaticityModel.huckelPiElectrons(cyclohexane, (0..5).toList()))
        assertEquals(emptyList(), AromaticityModel.aromaticRings(cyclohexane))
    }

    @Test
    fun `anti-aromatic rings are rejected by the electron count`() {
        assertEquals(emptyList(), AromaticityModel.aromaticRings(graph("C1=CC=C1")))
        assertEquals(emptyList(), AromaticityModel.aromaticRings(graph("C1=CC=CC=CC=C1")))
    }

    @Test
    fun `a substituent does not break the aromatic ring`() {
        assertEquals(1, AromaticityModel.aromaticRings(graph("CC1=CC=CC=C1")).size)
    }

    @Test
    fun `pyridine nitrogen contributes one electron, not its lone pair`() {
        val pyridine = graph("C1=CC=CN=C1")
        val rings = AromaticityModel.aromaticRings(pyridine)
        assertEquals(1, rings.size)
        assertEquals(6, AromaticityModel.huckelPiElectrons(pyridine, rings.single()))
    }

    @Test
    fun `pyrrole nitrogen closes the sextet with its lone pair`() {
        val pyrrole = graph("C1=CNC=C1")
        val rings = AromaticityModel.aromaticRings(pyrrole)
        assertEquals(1, rings.size)
        assertEquals(6, AromaticityModel.huckelPiElectrons(pyrrole, rings.single()))
    }

    @Test
    fun `two joined rings are both found`() {
        assertEquals(2, AromaticityModel.aromaticRings(graph("C1=CC=CC=C1C1=CC=CC=C1")).size)
    }

    @Test
    fun `an open chain is never aromatic`() {
        assertEquals(emptyList(), AromaticityModel.aromaticRings(graph("C=C")))
        assertEquals(emptyList(), AromaticityModel.aromaticRings(graph("CCO")))
    }
}
