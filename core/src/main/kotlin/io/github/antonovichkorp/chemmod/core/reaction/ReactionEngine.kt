package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.model.Bond
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
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

/** One matched rule application, including automatically balanced stoichiometry. */
data class AppliedReaction(
    val ruleId: ReactionRuleId,
    val reactants: List<MoleculeAmount>,
    val products: List<MoleculeAmount>,
) {
    init {
        require(reactants.isNotEmpty()) { "A reaction needs at least one reactant" }
        require(products.isNotEmpty()) { "A reaction needs at least one product" }
    }

    fun formatEquation(): String = formatSide(reactants) + " -> " + formatSide(products)

    fun isConserved(): Boolean = BalancedReaction(
        reactants = reactants.map { term -> term.molecule },
        products = products.map { term -> term.molecule },
        reactantCoefficients = reactants.map { term -> term.coefficient },
        productCoefficients = products.map { term -> term.coefficient },
    ).isConserved()

    private fun formatSide(terms: List<MoleculeAmount>): String = terms.joinToString(" + ") { term ->
        val prefix = if (term.coefficient == BigInteger.ONE) "" else "${term.coefficient} "
        prefix + term.molecule.formula()
    }
}

/**
 * Rule interpreter matching a molecule against content data, checking its
 * environment, then delegating all coefficients to the exact balance engine.
 * A target may contain several matching bonds, so the result is a list of all
 * distinct structural products rather than a registry-selected single product.
 */
class ReactionEngine(private val ruleSet: ReactionRuleSet) {
    fun apply(target: Molecule, ruleId: ReactionRuleId, environment: ReactionEnvironment): List<AppliedReaction> {
        val rule = ruleSet.rule(ruleId)
        if (!conditionsMet(rule.conditions, environment)) return emptyList()

        val coReactants = rule.coReactantStructures.map { structure -> Molecule.fromSMILESlike(structure) }
        if (coReactants.any { it.canonicalKey() !in environment.availableCanonicalKeys }) return emptyList()
        val staticProducts = rule.productStructures.map { structure -> Molecule.fromSMILESlike(structure) }

        return targetProducts(target, rule)
            .mapNotNull { transformedTarget ->
                val products = listOfNotNull(transformedTarget) + staticProducts
                try {
                    val balanced = ReactionBalancer.balance(listOf(target) + coReactants, products)
                    AppliedReaction(
                        ruleId = rule.id,
                        reactants = (listOf(target) + coReactants).mapIndexed { index, molecule ->
                            MoleculeAmount(molecule, balanced.reactantCoefficients[index])
                        },
                        products = products.mapIndexed { index, molecule ->
                            MoleculeAmount(molecule, balanced.productCoefficients[index])
                        },
                    )
                } catch (_: ReactionBalanceException) {
                    // A data rule can match a local bond but still lead to an invalid
                    // whole graph; that candidate is not a viable product.
                    null
                }
            }
            .distinctBy { outcome ->
                outcome.products.joinToString("|") { term ->
                    "${term.molecule.canonicalKey()}:${term.coefficient}"
                }
            }
    }

    private fun targetProducts(target: Molecule, rule: ReactionRule): List<Molecule?> = when (val matcher = rule.matcher) {
        is FormulaPattern -> if (matchesFormula(target, matcher)) listOf(null) else emptyList()
        is BondOrderPattern -> matchingBondProducts(target, matcher, requireNotNull(rule.targetProductBondOrder))
    }

    private fun matchingBondProducts(
        target: Molecule,
        pattern: BondOrderPattern,
        productBondOrder: BondOrder,
    ): List<Molecule> {
        val graph = target.graph
        if (pattern.requireNeutral && graph.atoms.any { it.formalCharge != 0 }) return emptyList()
        return graph.bonds.asSequence()
            .filter { it.order == pattern.bondOrder }
            .filter { bondMatches(graph, it, pattern) }
            .map { bond -> transformedTarget(graph, bond, productBondOrder) }
            .filter { it.validate().isEmpty() }
            .distinctBy(Molecule::canonicalKey)
            .toList()
    }

    private fun bondMatches(graph: MoleculeGraph, bond: Bond, pattern: BondOrderPattern): Boolean {
        val first = graph.atom(bond.first)
        val second = graph.atom(bond.second)
        val direct = endpointMatches(graph, first.id, pattern.firstElement, pattern.firstMinimumHydrogens) &&
            endpointMatches(graph, second.id, pattern.secondElement, pattern.secondMinimumHydrogens)
        val reversed = endpointMatches(graph, first.id, pattern.secondElement, pattern.secondMinimumHydrogens) &&
            endpointMatches(graph, second.id, pattern.firstElement, pattern.firstMinimumHydrogens)
        return direct || reversed
    }

    private fun endpointMatches(
        graph: MoleculeGraph,
        atomId: Int,
        element: String,
        minimumHydrogens: Int,
    ): Boolean {
        val atom = graph.atom(atomId)
        return atom.element.symbol == element && atom.explicitHydrogens + graph.implicitHydrogens(atomId) >= minimumHydrogens
    }

    private fun transformedTarget(
        graph: MoleculeGraph,
        matchedBond: Bond,
        productBondOrder: BondOrder,
    ): Molecule = Molecule.fromGraph(
        MoleculeGraph(
            atoms = graph.atoms,
            bonds = graph.bonds.map { bond ->
                if (bond == matchedBond) bond.copy(order = productBondOrder) else bond
            },
        ),
    )

    private fun matchesFormula(target: Molecule, pattern: FormulaPattern): Boolean {
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
