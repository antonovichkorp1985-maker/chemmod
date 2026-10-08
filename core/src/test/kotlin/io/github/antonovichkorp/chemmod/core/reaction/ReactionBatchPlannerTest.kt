package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactionBatchPlannerTest {
    private val planner = ReactionBatchPlanner.bundled()

    @Test
    fun `hydrogenation plans exact physical consumption and graph product`() {
        val plans = planner.plan(
            target = MolecularPortion(Molecule.fromSMILESlike("C=CC"), 1_000_000),
            coReactants = listOf(MolecularPortion(Molecule.fromSMILESlike("[H][H]"), 2_000_000)),
            ruleId = ReactionRuleId.of("chemmod:alkene_hydrogenation"),
            environment = ReactionEnvironment(
                temperatureKelvin = 350.0,
                pressureKilopascals = 101.325,
                catalystTags = setOf("chemmod:palladium"),
                // This caller-provided claim must not replace the real vial input.
                availableCanonicalKeys = setOf("not:a-real-identity"),
            ),
        )

        val plan = plans.single()
        assertEquals(1_000_000L, plan.extentMicromoles)
        assertEquals(1_000_000L, plan.targetConsumedMicromoles)
        assertEquals(listOf(1_000_000L), plan.coReactantConsumedMicromoles)
        assertEquals(listOf("C3H8"), plan.products.map { it.molecule.formula() })
        assertEquals(listOf(1_000_000L), plan.products.map { it.micromoles })
        assertTrue(plan.reaction.isConserved())
    }

    @Test
    fun `limiting reagent leaves nondivisible micromoles unconsumed rather than rounding`() {
        val plans = planner.plan(
            target = MolecularPortion(Molecule.fromSMILESlike("CCO"), 1_000_000),
            coReactants = listOf(MolecularPortion(Molecule.fromSMILESlike("O=O"), 2_000_000)),
            ruleId = ReactionRuleId.of("chemmod:complete_combustion"),
            environment = ReactionEnvironment(temperatureKelvin = 600.0, pressureKilopascals = 101.325),
        )

        val plan = plans.single()
        assertEquals(666_666L, plan.extentMicromoles)
        assertEquals(666_666L, plan.targetConsumedMicromoles)
        assertEquals(listOf(1_999_998L), plan.coReactantConsumedMicromoles)
        assertEquals(listOf("CO2", "H2O"), plan.products.map { it.molecule.formula() })
        assertEquals(listOf(1_333_332L, 1_999_998L), plan.products.map { it.micromoles })
    }

    @Test
    fun `mismatched physical co-reactant cannot satisfy an identity gate`() {
        val plans = planner.plan(
            target = MolecularPortion(Molecule.fromSMILESlike("C=CC"), 1_000_000),
            coReactants = listOf(MolecularPortion(Molecule.fromSMILESlike("O=O"), 1_000_000)),
            ruleId = ReactionRuleId.of("chemmod:alkene_hydrogenation"),
            environment = ReactionEnvironment(
                temperatureKelvin = 350.0,
                pressureKilopascals = 101.325,
                catalystTags = setOf("chemmod:palladium"),
            ),
        )

        assertTrue(plans.isEmpty())
    }
}
