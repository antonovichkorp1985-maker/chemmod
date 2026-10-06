package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.reaction.ReactionEngine
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StructureNotationWriterTest {
    @Test
    fun `structural witness round trips charged branched cyclic and disconnected M0 graphs`() {
        listOf(
            "[NH4+]",
            "CC(=O)O",
            "C1CC1",
            "O.[H][H]",
        ).forEach(::assertRoundTrip)
    }

    @Test
    fun `structural witness serializes a graph created by a reaction transformation`() {
        val target = Molecule.fromSMILESlike("C=CC")
        val reaction = ReactionEngine.bundled().apply(
            target,
            ReactionRuleId.of("chemmod:alkene_hydrogenation"),
            ReactionEnvironment(
                temperatureKelvin = 350.0,
                pressureKilopascals = 101.325,
                catalystTags = setOf("chemmod:palladium"),
                availableCanonicalKeys = setOf(Molecule.fromSMILESlike("[H][H]").canonicalKey()),
            ),
        ).single()

        val product = reaction.products.single().molecule
        val witness = product.structuralWitness()
        assertEquals(product.canonicalKey(), Molecule.fromSMILESlike(witness).canonicalKey())
        assertEquals("C3H8", Molecule.fromSMILESlike(witness).formula())
    }

    private fun assertRoundTrip(input: String) {
        val molecule = Molecule.fromSMILESlike(input)
        val witness = molecule.structuralWitness()
        val reparsed = Molecule.fromSMILESlike(witness)

        assertEquals(molecule.canonicalKey(), reparsed.canonicalKey(), "Witness '$witness' did not preserve '$input'")
        assertEquals(molecule.formula(), reparsed.formula())
        assertTrue(reparsed.validate().isEmpty())
    }
}
