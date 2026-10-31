package io.github.antonovichkorp.chemmod.core.mixture

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.model.IonicSolubilityClassifier
import io.github.antonovichkorp.chemmod.core.model.MetathesisDrivingForce
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
import io.github.antonovichkorp.chemmod.core.model.Phase
import io.github.antonovichkorp.chemmod.core.model.Solubility
import io.github.antonovichkorp.chemmod.core.model.ElementTable
import io.github.antonovichkorp.chemmod.core.reaction.MolecularPortion
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBatchPlan
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBatchPlanner
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEngine
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId

/** One exact reaction plan selected from an explicit mixture component. */
data class MixtureReactionPlan(
    val batchPlan: ReactionBatchPlan,
    val residual: MolecularMixture,
    val products: List<MolecularPortion>,
)

/**
 * Assess whether ionic exchange can proceed given the physical phase and ion pair.
 * CM-1.2: Dynamic planning - uses the existing MetathesisDrivingForce and Phase.gate
 * rather than hardcoded reaction tables. Returns the driving force outcome, or
 * NO_FREE_IONS if the phase does not permit ion exchange (SOLID/GAS).
 */
fun assessIonicExchange(
    cation1: Solubility.Cation,
    anion1: Solubility.AnionGroup,
    cation2: Solubility.Cation,
    anion2: Solubility.AnionGroup,
    phase: Phase,
): MetathesisDrivingForce.Outcome = when {
    phase != Phase.AQUEOUS && phase != Phase.MOLTEN -> MetathesisDrivingForce.Outcome.NO_FREE_IONS
    else -> MetathesisDrivingForce.assess(cation1, anion1, cation2, anion2, phase)
}

/**
 * Selects a target identity from a mixture and uses only identities physically
 * present in that mixture as co-reactants. Products remain distinct portions so
 * a later vessel can decide whether to separate them or combine them.
 */
class MixtureReactionPlanner(private val engine: ReactionEngine) {
    private val batchPlanner = ReactionBatchPlanner(engine)

    fun plan(
        mixture: MolecularMixture,
        targetCanonicalKey: String,
        ruleId: ReactionRuleId,
        environment: ReactionEnvironment,
    ): List<MixtureReactionPlan> {
        val target = mixture.portion(targetCanonicalKey) ?: return emptyList()
        val physicalEnvironment = environment.copy(
            availableCanonicalKeys = mixture.portions().map { it.molecule.canonicalKey() }.toSet(),
        )
        val candidates = engine.apply(target.molecule, ruleId, physicalEnvironment)
        if (candidates.isEmpty()) return emptyList()

        // Every candidate outcome may need a different set of co-reactants; try each one so a
        // rule that can trade with several partners is not limited to the first candidate's list.
        return candidates.flatMap { candidate ->
            val coReactants = candidate.reactants.drop(1).map { required ->
                mixture.portion(required.molecule.canonicalKey()) ?: return@flatMap emptyList()
            }
            batchPlanner.plan(target, coReactants, ruleId, physicalEnvironment).mapNotNull { batchPlan ->
                val withdrawals = withdrawals(batchPlan)
                if (withdrawals.any { (key, amount) -> amount > mixture.micromolesOf(key) }) return@mapNotNull null
                MixtureReactionPlan(
                    batchPlan = batchPlan,
                    residual = mixture.withdraw(withdrawals),
                    products = batchPlan.products,
                )
            }
        }.distinctBy { it.batchPlan.reaction.formatEquation() }
    }

    private fun withdrawals(batchPlan: ReactionBatchPlan): Map<String, Long> {
        val identities = batchPlan.reaction.reactants.map { it.molecule.canonicalKey() }
        val quantities = listOf(batchPlan.targetConsumedMicromoles) + batchPlan.coReactantConsumedMicromoles
        return identities.zip(quantities).groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        ).mapValues { (_, amounts) -> amounts.fold(0L) { total, amount -> Math.addExact(total, amount) } }
    }

    companion object {
        @JvmStatic
        fun bundled(): MixtureReactionPlanner = MixtureReactionPlanner(ReactionEngine.bundled())
    }
}
