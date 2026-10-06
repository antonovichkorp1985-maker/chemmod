package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import java.math.BigInteger

/**
 * A quantized physical portion of one molecular graph. This is deliberately a
 * quantity wrapper, not a substance catalogue entry: identity and predicted
 * properties remain on [Molecule], while containers own their physical amount.
 */
data class MolecularPortion(
    val molecule: Molecule,
    val micromoles: Long,
) {
    init {
        require(molecule.validate().isEmpty()) { "A molecular portion needs a valid graph" }
        require(micromoles > 0) { "A molecular portion must contain a positive number of micromoles" }
    }
}

/**
 * Exact integer-micromole execution plan for one already-balanced rule outcome.
 * Inputs follow the supplied target/co-reactant order; outputs follow the
 * balanced equation order. The planner does not mutate inventories or attach
 * properties to the molecular graph.
 */
data class ReactionBatchPlan(
    val reaction: AppliedReaction,
    /** Extent of the balanced reaction in micromoles of stoichiometric units. */
    val extentMicromoles: Long,
    val targetConsumedMicromoles: Long,
    val coReactantConsumedMicromoles: List<Long>,
    val products: List<MolecularPortion>,
) {
    init {
        require(reaction.isConserved()) { "Only a conserved reaction may receive a batch plan" }
        require(extentMicromoles > 0) { "Reaction extent must be positive" }
        require(targetConsumedMicromoles > 0) { "Target consumption must be positive" }
        require(coReactantConsumedMicromoles.all { it > 0 }) { "Co-reactant consumption must be positive" }
        require(products.isNotEmpty()) { "A batch plan needs products" }
    }
}

/**
 * Bridges structural reaction rules to discrete physical quantities without
 * choosing names, material registrations, or game inventory semantics.
 */
class ReactionBatchPlanner(private val engine: ReactionEngine) {
    /**
     * Plans every viable structural outcome at the maximum exact extent allowed
     * by [target] and the ordered [coReactants]. A non-divisible limiting amount
     * is left in the input instead of being rounded or destroyed.
     */
    fun plan(
        target: MolecularPortion,
        coReactants: List<MolecularPortion>,
        ruleId: ReactionRuleId,
        environment: ReactionEnvironment,
    ): List<ReactionBatchPlan> {
        // Co-reactant presence is physical input, not a caller-supplied claim.
        val physicalEnvironment = environment.copy(
            availableCanonicalKeys = coReactants.map { it.molecule.canonicalKey() }.toSet(),
        )
        return engine.apply(target.molecule, ruleId, physicalEnvironment).mapNotNull { reaction ->
            planOutcome(target, coReactants, reaction)
        }
    }

    private fun planOutcome(
        target: MolecularPortion,
        coReactants: List<MolecularPortion>,
        reaction: AppliedReaction,
    ): ReactionBatchPlan? {
        val available = listOf(target) + coReactants
        if (reaction.reactants.size != available.size) return null
        if (reaction.reactants.zip(available).any { (required, present) ->
                required.molecule.canonicalKey() != present.molecule.canonicalKey()
            }
        ) return null

        val extent = reaction.reactants.zip(available)
            .minOf { (required, present) ->
                BigInteger.valueOf(present.micromoles).divide(required.coefficient)
            }
        if (extent <= BigInteger.ZERO) return null
        val extentMicromoles = extent.longValueExact()
        val consumed = reaction.reactants.map { required -> quantity(required.coefficient, extentMicromoles) }
        val products = reaction.products.map { product ->
            MolecularPortion(product.molecule, quantity(product.coefficient, extentMicromoles))
        }
        return ReactionBatchPlan(
            reaction = reaction,
            extentMicromoles = extentMicromoles,
            targetConsumedMicromoles = consumed.first(),
            coReactantConsumedMicromoles = consumed.drop(1),
            products = products,
        )
    }

    private fun quantity(coefficient: BigInteger, extentMicromoles: Long): Long = coefficient
        .multiply(BigInteger.valueOf(extentMicromoles))
        .longValueExact()

    companion object {
        @JvmStatic
        fun bundled(): ReactionBatchPlanner = ReactionBatchPlanner(ReactionEngine.bundled())
    }
}
