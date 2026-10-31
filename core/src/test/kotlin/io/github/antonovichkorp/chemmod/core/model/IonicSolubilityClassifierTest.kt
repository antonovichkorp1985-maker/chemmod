package io.github.antonovichkorp.chemmod.core.model

import io.github.antonovichkorp.chemmod.core.CommonSubstances
import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.chemistry.CatalogSolubility
import io.github.antonovichkorp.chemmod.core.chemistry.ReferenceMoleculeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IonicSolubilityClassifierTest {
    @Test
    fun `finite salt references derive soluble and insoluble groups from ion rules`() {
        val expected = linkedMapOf(
            "sodium_chloride" to true,
            "calcium_chloride" to true,
            "silver_chloride" to false,
            "lead_ii_chloride" to false,
            "mercury_ii_chloride" to false,
            "sodium_nitrate" to true,
            "silver_nitrate" to true,
            "barium_nitrate" to true,
            "sodium_sulfate" to true,
            "copper_ii_sulfate" to true,
            "barium_sulfate" to false,
            "strontium_sulfate" to false,
            "lead_ii_sulfate" to false,
            "calcium_carbonate" to false,
            "sodium_carbonate" to true,
            "lithium_carbonate" to true,
            "potassium_phosphate" to true,
            "calcium_phosphate" to false,
            "iron_ii_phosphate" to false,
            "zinc_sulfide" to false,
            "cesium_sulfide" to true,
            "sodium_hydroxide" to true,
            "calcium_hydroxide" to true,
            "barium_hydroxide" to true,
            "magnesium_hydroxide" to false,
            "iron_ii_hydroxide" to false,
            "copper_ii_hydroxide" to false,
            "ammonium_sulfate" to true,
        )

        expected.forEach { (name, soluble) ->
            val reference = assertNotNull(CommonSubstances.find(name), "missing salt reference: $name")
            val molecule = Molecule.fromSMILESlike(reference.structure)
            val result = assertNotNull(IonicSolubilityClassifier.classify(molecule.graph), name)
            assertEquals(soluble, result.soluble, name)
            assertEquals(
                if (soluble) CatalogSolubility.SOLUBLE else CatalogSolubility.INSOLUBLE,
                ReferenceMoleculeCatalog.describe(molecule.graph).solubility,
                name,
            )
        }
    }

    @Test
    fun `bracket parser preserves multivalent ion charges`() {
        assertEquals(2, Molecule.fromSMILESlike("[Ca+2]").graph.atoms.single().formalCharge)
        assertEquals(-2, Molecule.fromSMILESlike("[S-2]").graph.atoms.single().formalCharge)
    }

    @Test
    fun `recognized salt formulae are written cation first and charged atoms add no implicit hydrogen`() {
        assertEquals("NaCl", Molecule.fromSMILESlike("[Cl-].[Na+]").formula())
        assertEquals("CaCl2", Molecule.fromSMILESlike("[Ca+2].[Cl-].[Cl-]").formula())
        assertEquals("Na2SO4", Molecule.fromSMILESlike("[Na+].[Na+].[O-]S(=O)(=O)[O-]").formula())
        assertEquals("K3PO4", Molecule.fromSMILESlike("[K+].[K+].[K+].[O-]P(=O)([O-])[O-]").formula())
        assertEquals("NaOH", Molecule.fromSMILESlike("[Na+].[OH-]").formula())
        val sodiumChloride = Molecule.fromSMILESlike("[Na+].[Cl-]")
        assertTrue(sodiumChloride.validate().isEmpty())
        assertEquals(
            sodiumChloride.canonicalKey(),
            Molecule.fromSMILESlike("[Cl-].[Na+]").canonicalKey(),
        )
        assertEquals(
            sodiumChloride.canonicalKey(),
            Molecule.fromSMILESlike(sodiumChloride.structuralWitness()).canonicalKey(),
        )
    }

    @Test
    fun `ammonium salt graph uses the model's alkali-like cation rule`() {
        val ammoniumSulfate = Molecule.fromSMILESlike(
            "[NH4+].[NH4+].[O-]S(=O)(=O)[O-]",
        )

        assertTrue(ammoniumSulfate.validate().isEmpty())
        assertEquals("N2H8SO4", ammoniumSulfate.formula())
        assertEquals(true, assertNotNull(IonicSolubilityClassifier.classify(ammoniumSulfate.graph)).soluble)
    }

    @Test
    fun `unsupported ion groups remain unknown rather than borrowing a halide rule`() {
        val hypochlorite = Molecule.fromSMILESlike("[Na+].[O-]Cl")

        assertTrue(hypochlorite.validate().isEmpty())
        assertNull(IonicSolubilityClassifier.classify(hypochlorite.graph))
        assertTrue(IonicSolubilityClassifier.isChargeSeparatedCompound(hypochlorite.graph))
        assertEquals(listOf("ionic-compound"), ReferenceMoleculeCatalog.describe(hypochlorite.graph).classes)
        assertEquals(CatalogSolubility.UNKNOWN, ReferenceMoleculeCatalog.describe(hypochlorite.graph).solubility)
    }
}
