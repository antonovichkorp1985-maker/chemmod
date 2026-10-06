package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.properties.ReferencePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MolecularReactionRulesTest {
    @Test
    fun `an arbitrary valid molecular structure exists without a catalog row`() {
        val butanol = MolecularSubstance.fromStructure("CCCCO")

        assertEquals("C4H10O", butanol.properties.formula)
        assertEquals(ReferencePhase.CONDENSED, butanol.properties.referencePhaseAt20C)
        assertTrue(butanol.properties.combustionEnthalpyEstimateKilojoulesPerMole!! < 0.0)
        assertNull(ChemicalCatalog.bundled().findSubstance("CCCCO"))
    }

    @Test
    fun `complete combustion is generated for an unregistered C H O structure`() {
        val butanol = MolecularSubstance.fromStructure("CCCCO")
        val reaction = assertNotNull(MolecularReactionRules.completeCombustion(butanol))

        assertEquals("2 C4H10O + 12 O2 -> 8 CO2 + 10 H2O", reaction.formatEquation())
        assertTrue(reaction.isConserved())
    }

    @Test
    fun `equivalent structures retain one substance identity despite different traversal`() {
        val leftToRight = MolecularSubstance.fromStructure("CCO")
        val rightToLeft = MolecularSubstance.fromStructure("OCC")

        assertEquals(leftToRight, rightToLeft)
        assertEquals(leftToRight.canonicalId, rightToLeft.canonicalId)
    }

    @Test
    fun `combustion does not fabricate an equation for fully oxidized or unsupported structures`() {
        assertNull(MolecularReactionRules.completeCombustion(MolecularSubstance.fromStructure("O=C=O")))
        assertNull(MolecularReactionRules.completeCombustion(MolecularSubstance.fromStructure("Cl-Cl")))

        val malformed = MolecularReaction(
            reactants = listOf(
                MolecularReactionTerm(MolecularSubstance.fromStructure("C"), java.math.BigInteger.ONE),
                MolecularReactionTerm(MolecularSubstance.fromStructure("O=O"), java.math.BigInteger.ONE),
            ),
            products = listOf(MolecularReactionTerm(MolecularSubstance.fromStructure("O"), java.math.BigInteger.ONE)),
        )
        assertFalse(malformed.isConserved())
    }
}
