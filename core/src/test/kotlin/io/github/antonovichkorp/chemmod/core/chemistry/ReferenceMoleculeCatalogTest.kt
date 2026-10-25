package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.CommonSubstance
import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReferenceMoleculeCatalogTest {
    @Test
    fun `longest carbon chain is derived from the carbon graph`() {
        assertEquals(0, ReferenceMoleculeCatalog.longestCarbonChainLength(graph("O")))
        assertEquals(4, ReferenceMoleculeCatalog.longestCarbonChainLength(graph("CC(C)CC")))
        assertEquals(6, ReferenceMoleculeCatalog.longestCarbonChainLength(graph("C1CCCCC1")))
        assertEquals(6, ReferenceMoleculeCatalog.longestCarbonChainLength(graph("C1=CC=CC=C1")))
    }

    @Test
    fun `descriptors use computed classes and leave unsupported solubility unknown`() {
        val alkane = ReferenceMoleculeCatalog.describe(graph("CCCC"))
        assertTrue(FunctionalGroups.ALKANE in alkane.classes)
        assertEquals(4, alkane.longestCarbonChainLength)
        assertEquals(CatalogSolubility.UNKNOWN, alkane.solubility)

        val ester = ReferenceMoleculeCatalog.describe(graph("CC(=O)OC"))
        assertTrue(FunctionalGroups.ESTER_CLASS in ester.classes)
        assertEquals(2, ester.longestCarbonChainLength)
        assertEquals(CatalogSolubility.UNKNOWN, ester.solubility)
    }

    @Test
    fun `supported salts sort into rule-derived solubility groups`() {
        val input = listOf(
            CommonSubstance("insoluble_salt", "[Ag+].[Cl-]"),
            CommonSubstance("alkane", "CC"),
            CommonSubstance("soluble_salt", "[Na+].[Cl-]"),
        )

        val sorted = ReferenceMoleculeCatalog.sorted(input)

        assertEquals(listOf("soluble_salt", "insoluble_salt", "alkane"), sorted.map { it.canonicalName })
        assertEquals(listOf("soluble-salt"), ReferenceMoleculeCatalog.describe(graph("[Na+].[Cl-]")).classes)
        assertEquals(listOf("insoluble-salt"), ReferenceMoleculeCatalog.describe(graph("[Ag+].[Cl-]")).classes)
    }

    @Test
    fun `finite reference samples sort by graph class then carbon chain length`() {
        val input = listOf(
            CommonSubstance("longer_alkane", "CCCC"),
            CommonSubstance("shorter_alkane", "CC"),
            CommonSubstance("ethanol", "CCO"),
            CommonSubstance("water", "O"),
        )

        val sorted = ReferenceMoleculeCatalog.sorted(input)

        assertEquals(listOf("shorter_alkane", "longer_alkane", "ethanol", "water"), sorted.map { it.canonicalName })
    }

    private fun graph(structure: String) = Molecule.fromSMILESlike(structure).graph
}
