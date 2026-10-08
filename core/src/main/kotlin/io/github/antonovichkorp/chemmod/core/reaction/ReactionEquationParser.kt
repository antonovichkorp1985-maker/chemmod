package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule

object ReactionEquationParser {
    fun balance(equation: String): BalancedReaction {
        val arrows = listOf("->", "→").filter(equation::contains)
        if (arrows.size != 1) {
            throw ReactionBalanceException("Equation must contain exactly one '->' arrow")
        }
        val sides = equation.split(arrows.single())
        if (sides.size != 2) throw ReactionBalanceException("Equation must have reactants and products")
        return ReactionBalancer.balance(parseSide(sides[0]), parseSide(sides[1]))
    }

    fun format(reaction: BalancedReaction): String =
        formatSide(reaction.reactants, reaction.reactantCoefficients) + " -> " +
            formatSide(reaction.products, reaction.productCoefficients)

    private fun parseSide(side: String): List<Molecule> {
        val tokens = side.trim().split(Regex("\\s+\\+\\s+")).filter(String::isNotBlank)
        if (tokens.isEmpty()) throw ReactionBalanceException("Reaction side cannot be empty")
        return tokens.map(Molecule::fromInput)
    }

    private fun formatSide(molecules: List<Molecule>, coefficients: List<java.math.BigInteger>): String =
        molecules.indices.joinToString(" + ") { index ->
            val coefficient = coefficients[index]
            val prefix = if (coefficient == java.math.BigInteger.ONE) "" else "$coefficient "
            "$prefix${molecules[index].formula()}"
        }
}
