package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBalancer
import java.math.BigInteger

/** A reaction generated from structure rather than looked up by an ID. */
data class MolecularReactionTerm(
    val substance: MolecularSubstance,
    val coefficient: BigInteger,
) {
    init {
        require(coefficient > BigInteger.ZERO) { "Molecular reaction coefficients must be positive" }
    }
}

class MolecularReaction(
    val reactants: List<MolecularReactionTerm>,
    val products: List<MolecularReactionTerm>,
) {
    init {
        require(reactants.isNotEmpty()) { "A molecular reaction needs a reactant" }
        require(products.isNotEmpty()) { "A molecular reaction needs a product" }
        require(reactants.map { it.substance }.distinct().size == reactants.size) {
            "A molecular reaction cannot repeat a reactant"
        }
        require(products.map { it.substance }.distinct().size == products.size) {
            "A molecular reaction cannot repeat a product"
        }
    }

    /** Verifies both atom/charge conservation and minimal whole-number coefficients. */
    fun isConserved(): Boolean = try {
        val balanced = ReactionBalancer.balance(
            reactants.map { it.substance.molecule },
            products.map { it.substance.molecule },
        )
        balanced.reactantCoefficients == reactants.map { it.coefficient } &&
            balanced.productCoefficients == products.map { it.coefficient }
    } catch (_: IllegalArgumentException) {
        false
    }

    fun formatEquation(): String = formatSide(reactants) + " -> " + formatSide(products)

    private fun formatSide(terms: List<MolecularReactionTerm>): String = terms.joinToString(" + ") { term ->
        val coefficient = if (term.coefficient == BigInteger.ONE) "" else "${term.coefficient} "
        coefficient + term.substance.properties.formula
    }
}

/**
 * Structure-level reaction families. They are an extension point for chemistry,
 * not a replacement for data-declared gameplay reactions in [ChemicalCatalog].
 */
object MolecularReactionRules {
    private val OXYGEN = MolecularSubstance.fromStructure("O=O")
    private val CARBON_DIOXIDE = MolecularSubstance.fromStructure("O=C=O")
    private val WATER = MolecularSubstance.fromStructure("O")

    /**
     * Generates complete combustion for any neutral C/H/O molecular structure
     * whose equation has positive oxygen demand. The exact integer equation is
     * solved by [ReactionBalancer], so butanol, acetylene and future player
     * structures do not need one hand-authored row each.
     */
    fun completeCombustion(fuel: MolecularSubstance): MolecularReaction? {
        val graph = fuel.molecule.graph
        val atoms = FormulaCalculator.counts(graph)
        if (graph.atoms.any { it.formalCharge != 0 }) return null
        if (atoms.keys.any { it !in setOf("C", "H", "O") }) return null
        val carbon = atoms["C"] ?: return null
        val hydrogen = atoms["H"] ?: 0
        val oxygen = atoms["O"] ?: 0
        if (carbon <= 0 || 4 * carbon + hydrogen - 2 * oxygen <= 0) return null

        val balanced = ReactionBalancer.balance(
            listOf(fuel.molecule, OXYGEN.molecule),
            listOf(CARBON_DIOXIDE.molecule, WATER.molecule),
        )
        return MolecularReaction(
            reactants = listOf(
                MolecularReactionTerm(fuel, balanced.reactantCoefficients[0]),
                MolecularReactionTerm(OXYGEN, balanced.reactantCoefficients[1]),
            ),
            products = listOf(
                MolecularReactionTerm(CARBON_DIOXIDE, balanced.productCoefficients[0]),
                MolecularReactionTerm(WATER, balanced.productCoefficients[1]),
            ),
        ).also { check(it.isConserved()) { "Internal combustion rule produced an unconserved reaction" } }
    }
}
