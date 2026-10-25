package io.github.antonovichkorp.chemmod.core.gas

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GasCellReactionPlannerTest {
    private val planner = GasCellReactionPlanner()
    private val methane = molecule("C")
    private val oxygen = molecule("O=O")
    private val nitrogen = molecule("N#N")

    @Test
    fun `environmental combustion uses the ordinary balanced reaction rule`() {
        val rule = ReactionRuleSet.bundled().rule(ReactionRuleId.of("chemmod:complete_combustion"))
        assertTrue(rule.environmental)
        assertNotNull(rule.kinetics)

        val initial = listOf(
            GasMolecularAmount(methane, 1.0),
            GasMolecularAmount(oxygen, 2.0),
            GasMolecularAmount(nitrogen, 7.52),
        )
        val plan = planner.plan(initial, environment(initial, temperatureKelvin = 600.0), 1.0)

        assertNotNull(plan)
        assertEquals("chemmod:complete_combustion", plan.ruleId.value)
        assertTrue(plan.reaction.isConserved())
        assertTrue(plan.extentMoles in 0.0..1.0)

        val result = plan.applyTo(initial)
        val methaneLeft = result.single { it.molecule.canonicalKey() == methane.canonicalKey() }.moles
        val carbonDioxide = result.single { it.molecule.formula() == "CO2" }.moles
        val water = result.single { it.molecule.formula() == "H2O" }.moles
        assertTrue(methaneLeft < 1.0)
        assertEquals(1.0 - methaneLeft, carbonDioxide, 1e-12)
        assertEquals(2.0 * carbonDioxide, water, 1e-12)
        assertElementMolesEqual(initial, result)
    }

    @Test
    fun `hydrogen ignition uses a balanced ordinary environmental rule`() {
        val hydrogen = molecule("[H][H]")
        val oxygen = molecule("O=O")
        val contents = listOf(GasMolecularAmount(hydrogen, 2.0), GasMolecularAmount(oxygen, 1.0))

        val plan = planner.plan(contents, environment(contents, temperatureKelvin = 600.0), 1.0)

        assertNotNull(plan)
        assertEquals("chemmod:hydrogen_combustion", plan.ruleId.value)
        assertEquals("2 H2 + O2 -> 2 H2O", plan.reaction.formatEquation())
        val result = plan.applyTo(contents)
        assertTrue(result.single { it.molecule.canonicalKey() == hydrogen.canonicalKey() }.moles < 2.0)
        assertTrue(result.single { it.molecule.canonicalKey() == oxygen.canonicalKey() }.moles < 1.0)
        assertTrue(result.single { it.molecule.formula() == "H2O" }.moles > 0.0)
        assertElementMolesEqual(contents, result)
    }

    @Test
    fun `environmental rule waits for its ordinary temperature condition`() {
        val contents = listOf(GasMolecularAmount(methane, 1.0), GasMolecularAmount(oxygen, 2.0))
        assertNull(planner.plan(contents, environment(contents, temperatureKelvin = 499.999), 1.0))
        assertNotNull(planner.plan(contents, environment(contents, temperatureKelvin = 500.0), 1.0))
    }

    @Test
    fun `environmental reaction refuses absent limiting co-reactant`() {
        val contents = listOf(GasMolecularAmount(methane, 1.0))
        assertNull(planner.plan(contents, environment(contents, temperatureKelvin = 700.0), 5.0))
    }

    @Test
    fun `non-environmental rules do not leak into the field`() {
        val ethene = molecule("C=C")
        val hydrogen = molecule("[H][H]")
        val contents = listOf(GasMolecularAmount(ethene, 1.0), GasMolecularAmount(hydrogen, 5.0))
        assertNull(planner.plan(contents, environment(contents, temperatureKelvin = 700.0), 10.0))
    }

    @Test
    fun `same-formula structural isomers stay distinct in the gas plan`() {
        val ethanol = molecule("CCO")
        val dimethylEther = molecule("COC")
        assertEquals(ethanol.formula(), dimethylEther.formula())
        assertTrue(ethanol.canonicalKey() != dimethylEther.canonicalKey())

        val contents = listOf(GasMolecularAmount(ethanol, 0.25), GasMolecularAmount(dimethylEther, 0.75))
        val result = planner.plan(contents, environment(contents, temperatureKelvin = 300.0), 1.0)
        assertNull(result)
    }

    private fun environment(
        contents: Iterable<GasMolecularAmount>,
        temperatureKelvin: Double,
    ) = ReactionEnvironment(
        temperatureKelvin = temperatureKelvin,
        pressureKilopascals = 101.325,
        availableCanonicalKeys = contents.mapTo(linkedSetOf()) { it.molecule.canonicalKey() },
    )

    private fun assertElementMolesEqual(
        before: Iterable<GasMolecularAmount>,
        after: Iterable<GasMolecularAmount>,
    ) {
        fun totals(contents: Iterable<GasMolecularAmount>): Map<String, Double> {
            val totals = linkedMapOf<String, Double>()
            contents.forEach { amount ->
                FormulaCalculator.counts(amount.molecule.graph).forEach { (element, count) ->
                    totals[element] = (totals[element] ?: 0.0) + count * amount.moles
                }
            }
            return totals
        }
        val left = totals(before)
        val right = totals(after)
        assertEquals(left.keys, right.keys)
        left.forEach { (element, amount) -> assertEquals(amount, right.getValue(element), 1e-10, element) }
    }

    private fun molecule(structure: String) = Molecule.fromSMILESlike(structure)
}
