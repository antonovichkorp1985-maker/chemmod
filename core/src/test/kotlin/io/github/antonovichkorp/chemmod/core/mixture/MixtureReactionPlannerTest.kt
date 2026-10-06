package io.github.antonovichkorp.chemmod.core.mixture

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.reaction.MolecularPortion
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MixtureReactionPlannerTest {
    private val ethanol = Molecule.fromSMILESlike("CCO")
    private val oxygen = Molecule.fromSMILESlike("O=O")

    @Test
    fun `reaction consumes only the stoichiometric mixture portions and keeps exact residuals`() {
        val mixture = MolecularMixture(
            listOf(
                MolecularPortion(ethanol, 1_000_000),
                MolecularPortion(oxygen, 2_000_000),
                MolecularPortion(Molecule.fromSMILESlike("N#N"), 500_000),
            ),
        )

        val plan = MixtureReactionPlanner.bundled().plan(
            mixture,
            ethanol.canonicalKey(),
            ReactionRuleId.of("chemmod:complete_combustion"),
            ReactionEnvironment(temperatureKelvin = 600.0, pressureKilopascals = 101.325),
        ).single()

        assertEquals(666_666L, plan.batchPlan.extentMicromoles)
        assertEquals(333_334L, plan.residual.micromolesOf(ethanol.canonicalKey()))
        assertEquals(2L, plan.residual.micromolesOf(oxygen.canonicalKey()))
        assertEquals(500_000L, plan.residual.micromolesOf(Molecule.fromSMILESlike("N#N").canonicalKey()))
        assertEquals(listOf("CO2", "H2O"), plan.products.map { it.molecule.formula() })
        assertEquals(listOf(1_333_332L, 1_999_998L), plan.products.map { it.micromoles })
    }

    @Test
    fun `same canonical components normalize into one exact mixture portion`() {
        val mixture = MolecularMixture(
            listOf(
                MolecularPortion(Molecule.fromSMILESlike("CCO"), 400_000),
                MolecularPortion(Molecule.fromSMILESlike("OCC"), 600_000),
            ),
        )

        assertEquals(1, mixture.portions().size)
        assertEquals(1_000_000L, mixture.micromolesOf(ethanol.canonicalKey()))
        assertEquals(1_000_000L, mixture.totalMicromoles())
    }

    @Test
    fun `missing target identity does not turn a different component into a reactant`() {
        val mixture = MolecularMixture(listOf(MolecularPortion(oxygen, 1_000_000)))

        val plans = MixtureReactionPlanner.bundled().plan(
            mixture,
            ethanol.canonicalKey(),
            ReactionRuleId.of("chemmod:complete_combustion"),
            ReactionEnvironment(temperatureKelvin = 600.0, pressureKilopascals = 101.325),
        )

        assertTrue(plans.isEmpty())
    }
}
