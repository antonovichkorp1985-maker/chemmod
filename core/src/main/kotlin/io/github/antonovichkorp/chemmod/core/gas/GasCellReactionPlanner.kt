package io.github.antonovichkorp.chemmod.core.gas

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.reaction.AppliedReaction
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEngine
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleSet
import java.math.BigInteger
import kotlin.math.exp

/** A molecule and the amount of it present in a gas cell, measured in moles. */
data class GasMolecularAmount(
    val molecule: Molecule,
    val moles: Double,
) {
    init {
        require(moles.isFinite() && moles >= 0.0) { "Gas amount must be finite and non-negative" }
        require(molecule.validate().isEmpty()) { "Gas species must be a valid molecular graph" }
    }
}

/** One signed molecular change in a proposed environmental reaction. */
data class GasMoleDelta(
    val molecule: Molecule,
    val moles: Double,
)

/**
 * An immutable, balanced reaction extent for one gas-cell step. Applying a plan is all-or-nothing:
 * missing or insufficient reactants are rejected before a new composition is returned.
 */
data class GasCellReactionPlan(
    val reaction: AppliedReaction,
    val extentMoles: Double,
    val moleDeltas: Map<String, GasMoleDelta>,
) {
    val ruleId: ReactionRuleId get() = reaction.ruleId

    init {
        require(reaction.isConserved()) { "Environmental reaction must conserve every atom" }
        require(extentMoles.isFinite() && extentMoles > 0.0) { "Reaction extent must be finite and positive" }
        require(moleDeltas.isNotEmpty()) { "A reaction plan must change the gas mixture" }
        require(moleDeltas.values.all { it.moles.isFinite() }) { "Mole deltas must be finite" }
    }

    /** Return the new mixture, refusing an overdraft rather than clipping a reaction result. */
    fun applyTo(contents: Iterable<GasMolecularAmount>): List<GasMolecularAmount> {
        val amounts = linkedMapOf<String, Pair<Molecule, Double>>()
        for (entry in contents) {
            val key = entry.molecule.canonicalKey()
            val previous = amounts[key]
            amounts[key] = entry.molecule to ((previous?.second ?: 0.0) + entry.moles)
        }

        for ((key, delta) in moleDeltas) {
            val current = amounts[key]?.second ?: 0.0
            val updated = current + delta.moles
            require(updated >= -MOLE_TOLERANCE) {
                "Environmental reaction $ruleId overdraws ${delta.molecule.formula()}"
            }
            if (updated <= MOLE_TOLERANCE) amounts.remove(key)
            else amounts[key] = delta.molecule to updated
        }

        return amounts.entries
            .sortedBy { it.key }
            .map { (_, value) -> GasMolecularAmount(value.first, value.second) }
    }

    private companion object {
        const val MOLE_TOLERANCE = 1e-12
    }
}

/**
 * Plans one ordinary data-defined reaction in a gas mixture. The planner owns no reaction
 * equations: structural matching, conditions and exact balancing all remain in [ReactionEngine].
 * A rule runs here only when its data explicitly marks it `environmental` and supplies Arrhenius
 * kinetics; a missing model is a refusal, not an invented rate.
 *
 * Species are supplied as molecular graphs, never formula strings. Consequently two isomers remain
 * distinct and no formula-to-structure lookup table is needed.
 */
class GasCellReactionPlanner @JvmOverloads constructor(
    private val ruleSet: ReactionRuleSet = ReactionRuleSet.bundled(),
    private val engine: ReactionEngine = ReactionEngine(ruleSet),
) {
    /**
     * Select the first deterministic, applicable environmental rule for this cell and time step.
     * The extent is the limiting-reagent capacity multiplied by the rule's first-order Arrhenius
     * conversion over [seconds]. No input state is mutated.
     */
    fun plan(
        contents: Iterable<GasMolecularAmount>,
        environment: ReactionEnvironment,
        seconds: Double,
    ): GasCellReactionPlan? {
        require(seconds.isFinite() && seconds > 0.0) { "Reaction step must be finite and positive" }

        val current = linkedMapOf<String, GasMolecularAmount>()
        for (amount in contents) {
            val key = amount.molecule.canonicalKey()
            val previous = current[key]
            current[key] = if (previous == null) amount else previous.copy(moles = previous.moles + amount.moles)
        }
        if (current.isEmpty()) return null

        val availableKeys = current.keys
        val reactionEnvironment = environment.copy(availableCanonicalKeys = availableKeys)
        val environmentalRules = ruleSet.rules.values
            .asSequence()
            .filter { it.environmental && it.kinetics != null }
            .sortedBy { it.id.value }

        for (rule in environmentalRules) {
            for ((_, target) in current.toSortedMap()) {
                val candidate = engine.apply(target.molecule, rule.id, reactionEnvironment).singleOrNull()
                    ?: continue
                if (!candidate.isConserved()) continue
                val limitingExtent = limitingExtent(candidate, current) ?: continue
                val rate = requireNotNull(rule.kinetics).ratePerSecond(
                    environment.temperatureKelvin,
                    reactionEnvironment.catalystSurfaceSiteMoles,
                )
                val conversion = 1.0 - exp(-rate * seconds)
                val extent = limitingExtent * conversion
                if (!extent.isFinite() || extent <= 0.0) continue

                return GasCellReactionPlan(
                    reaction = candidate,
                    extentMoles = extent,
                    moleDeltas = deltas(candidate, extent),
                )
            }
        }
        return null
    }

    private fun limitingExtent(
        reaction: AppliedReaction,
        current: Map<String, GasMolecularAmount>,
    ): Double? {
        val required = linkedMapOf<String, Pair<Molecule, BigInteger>>()
        for (term in reaction.reactants) {
            val key = term.molecule.canonicalKey()
            val previous = required[key]
            required[key] = term.molecule to ((previous?.second ?: BigInteger.ZERO) + term.coefficient)
        }
        if (required.keys.any { it !in current }) return null
        return required.minOf { (key, requirement) ->
            current.getValue(key).moles / requirement.second.toDouble()
        }.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun deltas(reaction: AppliedReaction, extent: Double): Map<String, GasMoleDelta> {
        val amounts = linkedMapOf<String, Pair<Molecule, Double>>()
        fun add(molecule: Molecule, amount: Double) {
            val key = molecule.canonicalKey()
            val previous = amounts[key]
            amounts[key] = molecule to ((previous?.second ?: 0.0) + amount)
        }
        reaction.reactants.forEach { add(it.molecule, -it.coefficient.toDouble() * extent) }
        reaction.products.forEach { add(it.molecule, it.coefficient.toDouble() * extent) }
        return amounts.mapNotNull { (key, value) ->
            if (kotlin.math.abs(value.second) <= 1e-15) null
            else key to GasMoleDelta(value.first, value.second)
        }.toMap()
    }
}
