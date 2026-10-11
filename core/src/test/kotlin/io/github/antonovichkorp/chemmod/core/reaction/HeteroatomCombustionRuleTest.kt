package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The heteroatom combustion rules are element-level patterns, not per-substance recipes:
 * one rule covers every structure whose atoms can actually balance, and the balance engine
 * alone decides viability. Nothing here names a substance registry.
 */
class HeteroatomCombustionRuleTest {
    private val engine = ReactionEngine.bundled()
    private val oxygen = Molecule.fromSMILESlike("O=O")

    private fun environment(
        temperatureKelvin: Double = 700.0,
        available: Set<String> = setOf(oxygen.canonicalKey()),
    ) = ReactionEnvironment(
        temperatureKelvin = temperatureKelvin,
        pressureKilopascals = 101.325,
        availableCanonicalKeys = available,
    )

    private fun productsOf(structure: String, ruleId: String, environment: ReactionEnvironment): Set<String> {
        val outcomes = engine.apply(Molecule.fromSMILESlike(structure), ReactionRuleId.of(ruleId), environment)
        assertTrue(outcomes.all { it.isConserved() }, "rule $ruleId produced an unbalanced outcome")
        return outcomes.flatMap { outcome -> outcome.products.map { term -> term.molecule.formula() } }.toSet()
    }

    @Test
    fun `nitrogen rule oxidizes an amine to carbon dioxide water and nitrogen`() {
        val formulas = productsOf("CN", "chemmod:complete_combustion_with_nitrogen", environment())
        assertEquals(setOf("CO2", "H2O", "N2"), formulas)
    }

    @Test
    fun `the same nitrogen rule covers a larger amine without new content`() {
        val formulas = productsOf("CCNCC", "chemmod:complete_combustion_with_nitrogen", environment())
        assertEquals(setOf("CO2", "H2O", "N2"), formulas)
    }

    @Test
    fun `sulfur rule oxidizes a thiol to sulfur dioxide`() {
        val formulas = productsOf("CS", "chemmod:complete_combustion_with_sulfur", environment())
        assertEquals(setOf("CO2", "H2O", "O2S"), formulas)
    }

    @Test
    fun `chlorine rule oxidizes a chloroalkane to hydrogen chloride`() {
        val formulas = productsOf("CCl", "chemmod:complete_combustion_with_chlorine", environment())
        assertEquals(setOf("CO2", "H2O", "ClH"), formulas)
    }

    @Test
    fun `a structure without the required element does not match`() {
        val outcomes = engine.apply(
            Molecule.fromSMILESlike("CCO"),
            ReactionRuleId.of("chemmod:complete_combustion_with_nitrogen"),
            environment(),
        )
        assertEquals(emptyList(), outcomes)
    }

    @Test
    fun `a matching structure that cannot balance yields no viable product`() {
        val outcomes = engine.apply(
            Molecule.fromSMILESlike("C(Cl)(Cl)(Cl)Cl"),
            ReactionRuleId.of("chemmod:complete_combustion_with_chlorine"),
            environment(),
        )
        assertEquals(emptyList(), outcomes, "carbon tetrachloride has no hydrogen, so no balanced products exist")
    }

    @Test
    fun `rules stay gated by temperature and by the co-reactant actually being present`() {
        val target = Molecule.fromSMILESlike("CN")
        val ruleId = ReactionRuleId.of("chemmod:complete_combustion_with_nitrogen")
        assertEquals(emptyList(), engine.apply(target, ruleId, environment(temperatureKelvin = 400.0)))
        assertEquals(emptyList(), engine.apply(target, ruleId, environment(available = emptySet())))
    }
}
