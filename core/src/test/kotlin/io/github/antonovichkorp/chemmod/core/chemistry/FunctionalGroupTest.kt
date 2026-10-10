package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Classes come out of the graph, so nothing is ever told that butane is an alkane. What is
 * asserted here is that the derivation separates the families a synthesis actually distinguishes:
 * chain against ring, saturated against unsaturated, and one functional group against another.
 */
class FunctionalGroupTest {
    private fun classes(structure: String): Set<String> =
        FunctionalGroups.classes(Molecule.fromSMILESlike(structure).graph)

    private fun groups(structure: String): Set<String> =
        FunctionalGroups.groups(Molecule.fromSMILESlike(structure).graph).map { it.kind }.toSet()

    @Test
    fun `alkanes are hydrocarbons with nothing else in them`() {
        assertEquals(setOf("hydrocarbon", "alkane"), classes("CCCC"))
        // Branched or not makes no difference to the class, only to the identity.
        assertEquals(classes("CCCC"), classes("CC(C)C"))
        assertEquals(setOf("hydrocarbon", "alkane"), classes("C"))
    }

    @Test
    fun `one double bond is an alkene and two is a diene`() {
        assertEquals(setOf("hydrocarbon", "alkene"), classes("CCC=C"))
        assertEquals(setOf("hydrocarbon", "alkene", "diene"), classes("C=CC=C"))
        assertEquals(setOf("hydrocarbon", "alkyne"), classes("CCC#C"))
    }

    @Test
    fun `a ring without multiple bonds is a cycloalkane and an aromatic ring is an arene`() {
        assertEquals(setOf("hydrocarbon", "cycloalkane"), classes("C1CCCCC1"))
        // Kekule double bonds inside the sextet do not make benzene an alkene or a diene.
        assertEquals(setOf("hydrocarbon", "arene"), classes("C1=CC=CC=C1"))
        assertEquals(setOf("hydrocarbon", "arene"), classes("CC1=CC=CC=C1"))
        // A double bond outside the ring still counts: styrene is an arene and an alkene.
        assertEquals(setOf("hydrocarbon", "alkene", "arene"), classes("C=CC1=CC=CC=C1"))
    }

    @Test
    fun `alcohols and polyols are counted by hydroxyls`() {
        // An alcohol is not a hydrocarbon: the oxygen takes it out of that class.
        assertEquals(setOf("alcohol"), classes("CCCCO"))
        val glycol = classes("OCCO")
        assertTrue("alcohol" in glycol && "polyol" in glycol)
        assertEquals(setOf("hydroxyl"), groups("CCCCO"))
    }

    @Test
    fun `an ether oxygen is not a hydroxyl`() {
        assertEquals(setOf("ether"), classes("COC"))
        assertEquals(setOf("ether"), groups("COC"))
        assertFalse("alcohol" in classes("COC"))
    }

    @Test
    fun `carbonyls split into aldehyde, ketone, acid and ester`() {
        assertTrue("aldehyde" in classes("CC=O"))
        assertTrue("ketone" in classes("CC(C)=O"))
        val acid = classes("CC(=O)O")
        assertTrue("carboxylic-acid" in acid && "alcohol" in acid, "the OH of a carboxyl is a hydroxyl too")
        assertTrue("ester" in classes("CCOC(C)=O"))
        assertEquals(setOf("carboxyl"), groups("CC(=O)O") - "hydroxyl")
    }

    @Test
    fun `amines are graded by how many hydrogens the nitrogen keeps`() {
        assertTrue("primary-amine" in classes("CN"))
        assertTrue("secondary-amine" in classes("CNC"))
        assertTrue("tertiary-amine" in classes("CN(C)C"))
        assertEquals(setOf("amine"), groups("CNC"))
    }

    @Test
    fun `nitriles, halides and sulfur groups are recognised`() {
        assertTrue("nitrile" in classes("CC#N"))
        assertTrue("halide" in classes("CCl"))
        assertTrue("thiol" in classes("CCS"))
        assertTrue("thioether" in classes("CCSCC"))
    }

    @Test
    fun `water and ammonia are not organic classes at all`() {
        assertTrue(classes("O").isEmpty(), "water is an oxide, not an alcohol")
        assertTrue(classes("N").isEmpty(), "ammonia is not an amine in the organic sense")
    }

    @Test
    fun `the cycle rank counts rings the way the boiling model needs`() {
        val graph = { structure: String -> Molecule.fromSMILESlike(structure).graph }
        assertEquals(0, FunctionalGroups.cycleRank(graph("CCCCCC")))
        assertEquals(1, FunctionalGroups.cycleRank(graph("C1CCCCC1")))
        assertEquals(2, FunctionalGroups.cycleRank(graph("C1CCC2CCCCC2CC1")))
    }
}
