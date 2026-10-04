package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.reaction.ReactionBalanceException
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEquationParser
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReactionBalancerTest {
    @Test
    fun `balances ethanol combustion`() {
        val reaction = ReactionEquationParser.balance("CCO + O=O -> O=C=O + O")

        assertEquals(listOf(1, 3).big(), reaction.reactantCoefficients)
        assertEquals(listOf(2, 3).big(), reaction.productCoefficients)
        assertEquals("C2H6O + 3 O2 -> 2 CO2 + 3 H2O", ReactionEquationParser.format(reaction))
        assertTrue(reaction.isConserved())
    }

    @Test
    fun `balances methane combustion`() {
        val reaction = ReactionEquationParser.balance("C + O=O -> O=C=O + O")

        assertEquals(listOf(1, 2).big(), reaction.reactantCoefficients)
        assertEquals(listOf(1, 2).big(), reaction.productCoefficients)
    }

    @Test
    fun `balances propane combustion`() {
        val reaction = ReactionEquationParser.balance("CCC + O=O -> O=C=O + O")

        assertEquals(listOf(1, 5).big(), reaction.reactantCoefficients)
        assertEquals(listOf(3, 4).big(), reaction.productCoefficients)
    }

    @Test
    fun `balances acetylene combustion with minimal integer coefficients`() {
        val reaction = ReactionEquationParser.balance("C#C + O=O -> O=C=O + O")

        assertEquals(listOf(2, 5).big(), reaction.reactantCoefficients)
        assertEquals(listOf(4, 2).big(), reaction.productCoefficients)
    }

    @Test
    fun `balances hydrogen combustion`() {
        val reaction = ReactionEquationParser.balance("[H][H] + O=O -> O")

        assertEquals(listOf(2, 1).big(), reaction.reactantCoefficients)
        assertEquals(listOf(2).big(), reaction.productCoefficients)
    }

    @Test
    fun `rejects impossible and ambiguous equations`() {
        assertFailsWith<ReactionBalanceException> {
            ReactionEquationParser.balance("C -> O")
        }
        assertFailsWith<ReactionBalanceException> {
            ReactionEquationParser.balance("C + O=O -> O=C=O + O=O")
        }
    }

    private fun List<Int>.big(): List<BigInteger> = map { BigInteger.valueOf(it.toLong()) }
}
