package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The addition rule type is the second graph transform in the engine: the matched bond order
 * drops and one co-reactant atom attaches, with implicit hydrogens redistributing the rest.
 * One rule therefore covers a whole family of additions without naming a single product.
 */
class BondAdditionRuleTest {
    private val engine = ReactionEngine.bundled()

    private fun environment(vararg coReactants: String, temperatureKelvin: Double = 500.0) = ReactionEnvironment(
        temperatureKelvin = temperatureKelvin,
        pressureKilopascals = 101.325,
        availableCanonicalKeys = coReactants.map { Molecule.fromSMILESlike(it).canonicalKey() }.toSet(),
    )

    private fun apply(structure: String, ruleId: String, environment: ReactionEnvironment) =
        engine.apply(Molecule.fromSMILESlike(structure), ReactionRuleId.of(ruleId), environment)

    @Test
    fun `hydrating a symmetric alkene gives one balanced alcohol`() {
        val outcome = apply("C=C", "chemmod:alkene_hydration", environment("O")).single()
        assertTrue(outcome.isConserved())
        assertEquals(listOf("C2H4", "H2O"), outcome.reactants.map { it.molecule.formula() })
        assertEquals(listOf("C2H6O"), outcome.products.map { it.molecule.formula() })
    }

    @Test
    fun `hydrating an unsymmetric alkene enumerates both regioisomers`() {
        val outcomes = apply("CC=C", "chemmod:alkene_hydration", environment("O"))
        assertEquals(2, outcomes.size, "both attachment sites are structural outcomes")
        assertTrue(outcomes.all { it.isConserved() })
        assertTrue(outcomes.all { it.products.single().molecule.formula() == "C3H8O" })
        assertEquals(
            2,
            outcomes.map { it.products.single().molecule.canonicalKey() }.toSet().size,
            "regioisomers must not collapse into one identity",
        )
    }

    @Test
    fun `hydrogen chloride and ammonia add whole across the double bond`() {
        val chloride = apply("C=C", "chemmod:alkene_hydrochlorination", environment("Cl")).single()
        assertTrue(chloride.isConserved())
        assertEquals("C2H5Cl", chloride.products.single().molecule.formula())

        val amine = apply("C=C", "chemmod:alkene_hydroamination", environment("N")).single()
        assertTrue(amine.isConserved())
        assertEquals("C2H7N", amine.products.single().molecule.formula())
    }

    @Test
    fun `carbonyl hydration attacks the carbon and yields the gem-diol only`() {
        val outcomes = apply("C=O", "chemmod:carbonyl_hydration", environment("O"))
        val outcome = outcomes.single()
        assertTrue(outcome.isConserved())
        assertEquals("CH4O2", outcome.products.single().molecule.formula())
    }

    @Test
    fun `alkyne hydration yields the enol`() {
        val outcome = apply("C#C", "chemmod:alkyne_hydration", environment("O")).single()
        assertTrue(outcome.isConserved())
        assertEquals("C2H4O", outcome.products.single().molecule.formula())
    }

    @Test
    fun `a saturated target has nothing to add across`() {
        assertEquals(emptyList(), apply("CC", "chemmod:alkene_hydration", environment("O")))
    }

    @Test
    fun `addition stays gated by temperature and by the co-reactant being present`() {
        assertEquals(emptyList(), apply("C=C", "chemmod:alkene_hydration", environment("O", temperatureKelvin = 300.0)))
        assertEquals(emptyList(), apply("C=C", "chemmod:alkene_hydration", environment()))
    }
}
