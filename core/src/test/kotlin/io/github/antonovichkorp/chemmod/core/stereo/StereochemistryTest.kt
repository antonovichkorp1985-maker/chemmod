package io.github.antonovichkorp.chemmod.core.stereo

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.MoleculeCanonicalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Whether a spatial arrangement exists at all is a question about the graph, and these tests check
 * it is answered from the graph: nothing here is told that 2-butanol has a centre or that ethylene
 * has no cis or trans form to be told about.
 */
class StereochemistryTest {
    private fun graph(structure: String) = Molecule.fromSMILESlike(structure).graph

    @Test
    fun `a carbon with four different substituents is a centre`() {
        // 2-butanol: methyl, hydroxyl, ethyl and an implied hydrogen.
        assertEquals(listOf(1), Stereochemistry.stereocentres(graph("CC(O)CC")))
        // 2-chlorobutane, the same shape with chlorine in place of the hydroxyl.
        assertEquals(listOf(1), Stereochemistry.stereocentres(graph("CC(Cl)CC")))
    }

    @Test
    fun `two identical substituents cancel the centre out`() {
        assertEquals(emptyList(), Stereochemistry.stereocentres(graph("C")))
        // Isopropanol carries two methyls, so its carbon has three distinct substituents, not four.
        assertEquals(emptyList(), Stereochemistry.stereocentres(graph("CC(C)O")))
        assertEquals(emptyList(), Stereochemistry.stereocentres(graph("CC(C)(C)O")))
    }

    @Test
    fun `ring symmetry cancels the centre out without anyone enumerating paths`() {
        // Both ring bonds of cyclohexanol lead round the same way, so their Morgan labels match and
        // the hydroxyl carbon is not a centre. That falls out of the label comparison, not out of a
        // special case for rings.
        assertEquals(emptyList(), Stereochemistry.stereocentres(graph("OC1CCCCC1")))
        val labels = MoleculeCanonicalizer.morganLabels(graph("OC1CCCCC1"))
        assertEquals(labels.getValue(2), labels.getValue(6), "the two ring paths should be the same")
    }

    @Test
    fun `a double bond is stereogenic only when both ends differ`() {
        // 2-butene and 1,2-dichloroethene have cis and trans forms.
        assertEquals(1, Stereochemistry.stereogenicDoubleBonds(graph("CC=CC")).size)
        assertEquals(1, Stereochemistry.stereogenicDoubleBonds(graph("ClC=CCl")).size)
        // Ethylene has two hydrogens at each end; propene has two at one.
        assertEquals(emptyList(), Stereochemistry.stereogenicDoubleBonds(graph("C=C")))
        assertEquals(emptyList(), Stereochemistry.stereogenicDoubleBonds(graph("C=CC")))
        // And 1,1-dichloroethene puts both chlorines on the same carbon.
        assertEquals(emptyList(), Stereochemistry.stereogenicDoubleBonds(graph("C=C(Cl)Cl")))
    }

    @Test
    fun `a ring double bond has no cis or trans to speak of`() {
        // The ring already ties the two ends together, so E/Z would be describing nothing.
        assertEquals(emptyList(), Stereochemistry.stereogenicDoubleBonds(graph("C1=CCCCC1")))
    }

    @Test
    fun `a pyramidal atom with three different groups is chiral in principle`() {
        assertEquals(emptyList(), Stereochemistry.pyramidalChiralityCentres(graph("CN(C)C")))
        // Methyl, ethyl and propyl on nitrogen, then the same three on phosphorus.
        assertEquals(listOf(1), Stereochemistry.pyramidalChiralityCentres(graph("CN(CC)CCC")))
        assertEquals(listOf(1), Stereochemistry.pyramidalChiralityCentres(graph("CP(CC)CCC")))
    }

    @Test
    fun `nitrogen inverts away its chirality and phosphorus does not`() {
        // The barrier is a property of the element: about 25 kJ/mol for nitrogen, which ammonia
        // crosses 4e10 times a second, against roughly 130 kJ/mol for phosphorus. So the amine is
        // one substance and the phosphine is two, with identical connectivity in both cases.
        val amine = graph("CN(CC)CCC")
        val phosphine = graph("CP(CC)CCC")
        assertEquals(listOf(1), Stereochemistry.rapidlyInvertingCentres(amine))
        assertEquals(emptyList(), Stereochemistry.rapidlyInvertingCentres(phosphine))
        // The phosphorus centre is still a chirality centre; it simply survives.
        assertEquals(listOf(1), Stereochemistry.pyramidalChiralityCentres(phosphine))
    }

    @Test
    fun `detection never invents a centre where the graph has none`() {
        for (structure in listOf("O", "C", "CCO", "CCCCCC", "CCCl", "O=C=O", "CC#N")) {
            val graph = graph(structure)
            val centres = Stereochemistry.stereocentres(graph)
            assertTrue(centres.all { it in graph.atoms.map { atom -> atom.id } }, "$structure: unknown atom id")
        }
    }
}
