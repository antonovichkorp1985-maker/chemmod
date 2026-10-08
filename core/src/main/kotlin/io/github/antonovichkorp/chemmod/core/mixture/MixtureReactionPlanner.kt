package io.github.antonovichkorp.chemmod.core.mixture

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

        // All outcomes for one data rule have the same reactant identities;
        // their product graphs may differ when several target bonds match.
        val coReactants = candidates.first().reactants.drop(1).map { required ->
            mixture.portion(required.molecule.canonicalKey()) ?: return emptyList()
        }
        return batchPlanner.plan(target, coReactants, ruleId, physicalEnvironment).mapNotNull { batchPlan ->
            val withdrawals = withdrawals(batchPlan)
            if (withdrawals.any { (key, amount) -> amount > mixture.micromolesOf(key) }) return@mapNotNull null
            MixtureReactionPlan(
                batchPlan = batchPlan,
                residual = mixture.withdraw(withdrawals),
                products = batchPlan.products,
            )
        }
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
