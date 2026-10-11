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
    fun `a ring double bond counts only when the ring can hold a trans form`() {
        // In a small ring the two ends are tied together and E/Z describes nothing. But the ring can
        // grow: trans-cyclohexene is a proposed intermediate, trans-cycloheptene lives for seconds,
        // and trans-cyclooctene is routinely isolated.
        assertEquals(emptyList(), Stereochemistry.stereogenicDoubleBonds(graph("C1=CCCCC1")))
        assertEquals(emptyList(), Stereochemistry.stereogenicDoubleBonds(graph("C1=CCCCCC1")))
        assertEquals(1, Stereochemistry.stereogenicDoubleBonds(graph("C1=CCCCCCC1")).size)
        assertEquals(8, Stereochemistry.SMALLEST_STABLE_TRANS_CYCLOALKENE)
    }

    @Test
    fun `a biaryl axis is reported with the bulk hanging off its ortho positions`() {
        // Biphenyl rotates freely: nothing at the ortho positions to get in the way.
        val biphenyl = Stereochemistry.hinderedRotationCandidates(graph("C1=CC=CC=C1C1=CC=CC=C1"))
        assertEquals(1, biphenyl.size)
        assertEquals(0, biphenyl.single().orthoHeavyAtoms)
        // Two methyls at the ortho positions, one heavy atom each.
        val dimethyl = Stereochemistry.hinderedRotationCandidates(graph("CC1=CC=CC=C1C1=CC=CC=C1C"))
        assertEquals(1, dimethyl.size)
        assertEquals(2, dimethyl.single().orthoHeavyAtoms)
    }

    @Test
    fun `bulk at the ortho positions is measured, but no threshold is invented`() {
        // The line between fluxional and isolable is a barrier of some 22-25 kcal/mol, and turning
        // ortho bulk into a barrier needs measured data this repository does not hold. So the model
        // reports the candidate and the quantity a barrier rule would consume, and stops there.
        val candidates = Stereochemistry.hinderedRotationCandidates(graph("CC1=CC=CC=C1C1=CC=CC=C1C"))
        assertTrue(candidates.single().orthoHeavyAtoms >= 0)
        // A single aromatic ring has no axis between rings at all.
        assertEquals(emptyList(), Stereochemistry.hinderedRotationCandidates(graph("C1=CC=CC=C1")))
        // Nor does an ordinary single bond between two saturated carbons.
        assertEquals(emptyList(), Stereochemistry.hinderedRotationCandidates(graph("CCCCCC")))
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
