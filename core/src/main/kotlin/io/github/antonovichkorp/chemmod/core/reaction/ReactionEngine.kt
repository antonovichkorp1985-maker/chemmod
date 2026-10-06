package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.Molecule
import java.math.BigInteger

/** Runtime conditions supplied by a container, reactor or later gas field. */
data class ReactionEnvironment(
    val temperatureKelvin: Double,
    val pressureKilopascals: Double,
    val catalystTags: Set<String> = emptySet(),
    /** Canonical identities of co-reactants that are actually present in the environment. */
    val availableCanonicalKeys: Set<String> = emptySet(),
) {
    init {
        require(temperatureKelvin > 0.0 && temperatureKelvin.isFinite()) { "Temperature must be finite and positive" }
        require(pressureKilopascals > 0.0 && pressureKilopascals.isFinite()) { "Pressure must be finite and positive" }
    }
}

data class MoleculeAmount(
    val molecule: Molecule,
    val coefficient: BigInteger,
) {
    init {
        require(coefficient > BigInteger.ZERO) { "Reaction coefficient must be positive" }
    }
}

/** One matched rule application, including the automatically balanced stoichiometry. */
data class AppliedReaction(
    val ruleId: ReactionRuleId,
    val reactants: List<MoleculeAmount>,
    val products: List<MoleculeAmount>,
) {
    fun formatEquation(): String = formatSide(reactants) + " -> " + formatSide(products)

    private fun formatSide(terms: List<MoleculeAmount>): String = terms.joinToString(" + ") { term ->
        val prefix = if (term.coefficient == BigInteger.ONE) "" else "${term.coefficient} "
        prefix + term.molecule.formula()
    }
}

/**
 * Rule interpreter matching a molecule against content data, checking its
 * environment, then delegating all coefficients to the exact balance engine.
 */
class ReactionEngine(private val ruleSet: ReactionRuleSet) {
    fun apply(target: Molecule, ruleId: ReactionRuleId, environment: ReactionEnvironment): AppliedReaction? {
        val rule = ruleSet.rule(ruleId)
        if (!matches(target, rule.matcher) || !conditionsMet(rule.conditions, environment)) return null

        val coReactants = rule.coReactantStructures.map { structure -> Molecule.fromSMILESlike(structure) }
        if (coReactants.any { it.canonicalKey() !in environment.availableCanonicalKeys }) return null
        val products = rule.productStructures.map { structure -> Molecule.fromSMILESlike(structure) }
        val balanced = ReactionBalancer.balance(listOf(target) + coReactants, products)
        return AppliedReaction(
            ruleId = rule.id,
            reactants = (listOf(target) + coReactants).mapIndexed { index, molecule ->
                MoleculeAmount(molecule, balanced.reactantCoefficients[index])
            },
            products = products.mapIndexed { index, molecule ->
                MoleculeAmount(molecule, balanced.productCoefficients[index])
            },
        )
    }

    private fun matches(target: Molecule, pattern: FormulaPattern): Boolean {
        val graph = target.graph
        if (pattern.requireNeutral && graph.atoms.any { it.formalCharge != 0 }) return false
        val formula = FormulaCalculator.counts(graph)
        if (!formula.keys.all { it in pattern.allowedElements }) return false
        if (!pattern.requiredElements.all { formula.getOrDefault(it, 0) > 0 }) return false
        if (!pattern.requirePositiveOxygenDemand) return true
        val carbon = formula["C"] ?: return false
        val hydrogen = formula["H"] ?: 0
        val oxygen = formula["O"] ?: 0
        return 4 * carbon + hydrogen - 2 * oxygen > 0
    }

    private fun conditionsMet(conditions: ReactionConditions, environment: ReactionEnvironment): Boolean =
        (conditions.minimumTemperatureKelvin == null || environment.temperatureKelvin >= conditions.minimumTemperatureKelvin) &&
            (conditions.maximumTemperatureKelvin == null || environment.temperatureKelvin <= conditions.maximumTemperatureKelvin) &&
            (conditions.minimumPressureKilopascals == null || environment.pressureKilopascals >= conditions.minimumPressureKilopascals) &&
            environment.catalystTags.containsAll(conditions.catalystTags)

    companion object {
        @JvmStatic
        fun bundled(): ReactionEngine = ReactionEngine(ReactionRuleSet.bundled())
    }
}
