package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.Molecule
import java.math.BigInteger

data class BalancedReaction(
    val reactants: List<Molecule>,
    val products: List<Molecule>,
    val reactantCoefficients: List<BigInteger>,
    val productCoefficients: List<BigInteger>,
) {
    init {
        require(reactants.size == reactantCoefficients.size)
        require(products.size == productCoefficients.size)
        require((reactantCoefficients + productCoefficients).all { it > BigInteger.ZERO })
    }

    fun isConserved(): Boolean {
        val symbols = (reactants + products)
            .flatMap { FormulaCalculator.counts(it.graph).keys }
            .toSortedSet()
        val atomsConserved = symbols.all { symbol ->
            weightedCount(reactants, reactantCoefficients, symbol) ==
                weightedCount(products, productCoefficients, symbol)
        }
        val chargeConserved = weightedCharge(reactants, reactantCoefficients) ==
            weightedCharge(products, productCoefficients)
        return atomsConserved && chargeConserved
    }

    private fun weightedCount(
        molecules: List<Molecule>,
        coefficients: List<BigInteger>,
        symbol: String,
    ): BigInteger = molecules.indices.fold(BigInteger.ZERO) { total, index ->
        total + coefficients[index] * BigInteger.valueOf(
            FormulaCalculator.counts(molecules[index].graph).getOrDefault(symbol, 0).toLong(),
        )
    }

    private fun weightedCharge(molecules: List<Molecule>, coefficients: List<BigInteger>): BigInteger =
        molecules.indices.fold(BigInteger.ZERO) { total, index ->
            val charge = molecules[index].graph.atoms.sumOf { it.formalCharge }
            total + coefficients[index] * BigInteger.valueOf(charge.toLong())
        }
}

class ReactionBalanceException(message: String) : IllegalArgumentException(message)

object ReactionBalancer {
    fun balance(reactants: List<Molecule>, products: List<Molecule>): BalancedReaction {
        require(reactants.isNotEmpty()) { "At least one reactant is required" }
        require(products.isNotEmpty()) { "At least one product is required" }
        val molecules = reactants + products
        molecules.forEach { molecule ->
            val issues = molecule.validate()
            if (issues.isNotEmpty()) {
                throw ReactionBalanceException("Cannot balance an invalid molecule: ${issues.first().message}")
            }
        }

        val symbols = molecules.flatMap { FormulaCalculator.counts(it.graph).keys }.toSortedSet()
        val rows = symbols.map { symbol ->
            molecules.mapIndexed { index, molecule ->
                val side = if (index < reactants.size) 1L else -1L
                val count = FormulaCalculator.counts(molecule.graph).getOrDefault(symbol, 0)
                Rational.of(BigInteger.valueOf(side * count))
            }.toMutableList()
        }.toMutableList()

        // Charge is conserved alongside atoms. Keeping the row even when all charges
        // are zero makes the matrix construction explicit and harmless.
        rows += molecules.mapIndexed { index, molecule ->
            val side = if (index < reactants.size) 1L else -1L
            val charge = molecule.graph.atoms.sumOf { it.formalCharge }
            Rational.of(BigInteger.valueOf(side * charge))
        }.toMutableList()

        val rationals = oneDimensionalNullSpace(rows, molecules.size)
        val commonDenominator = rationals.fold(BigInteger.ONE) { result, value ->
            lcm(result, value.denominator)
        }
        var integers = rationals.map { it.numerator * (commonDenominator / it.denominator) }
        if (integers.all { it < BigInteger.ZERO }) integers = integers.map { it.negate() }
        if (integers.any { it <= BigInteger.ZERO }) {
            throw ReactionBalanceException("Equation has no solution with every species on the declared side")
        }
        val gcd = integers.map(BigInteger::abs).reduce(BigInteger::gcd)
        integers = integers.map { it / gcd }

        return BalancedReaction(
            reactants,
            products,
            integers.take(reactants.size),
            integers.drop(reactants.size),
        ).also {
            check(it.isConserved()) { "Internal error: computed coefficients do not conserve atoms and charge" }
        }
    }

    private fun oneDimensionalNullSpace(
        input: MutableList<MutableList<Rational>>,
        columns: Int,
    ): List<Rational> {
        val matrix = input.filterNot { row -> row.all { it.isZero } }.toMutableList()
        val pivotColumns = mutableListOf<Int>()
        var pivotRow = 0

        for (column in 0 until columns) {
            val selected = (pivotRow until matrix.size).firstOrNull { !matrix[it][column].isZero } ?: continue
            val swap = matrix[pivotRow]
            matrix[pivotRow] = matrix[selected]
            matrix[selected] = swap

            val pivot = matrix[pivotRow][column]
            matrix[pivotRow] = matrix[pivotRow].map { it / pivot }.toMutableList()
            for (row in matrix.indices) {
                if (row == pivotRow) continue
                val factor = matrix[row][column]
                if (!factor.isZero) {
                    matrix[row] = matrix[row].indices
                        .map { index -> matrix[row][index] - factor * matrix[pivotRow][index] }
                        .toMutableList()
                }
            }
            pivotColumns += column
            pivotRow++
            if (pivotRow == matrix.size) break
        }

        val freeColumns = (0 until columns).filterNot(pivotColumns::contains)
        if (freeColumns.size != 1) {
            throw ReactionBalanceException(
                if (freeColumns.isEmpty()) "Equation has no non-zero balance"
                else "Equation is underdetermined (${freeColumns.size} independent coefficients)",
            )
        }

        val free = freeColumns.single()
        val solution = MutableList(columns) { Rational.ZERO }
        solution[free] = Rational.ONE
        pivotColumns.forEachIndexed { row, pivotColumn ->
            solution[pivotColumn] = -matrix[row][free]
        }
        return solution
    }

    private fun lcm(first: BigInteger, second: BigInteger): BigInteger =
        if (first == BigInteger.ZERO || second == BigInteger.ZERO) BigInteger.ZERO
        else (first / first.gcd(second) * second).abs()
}
