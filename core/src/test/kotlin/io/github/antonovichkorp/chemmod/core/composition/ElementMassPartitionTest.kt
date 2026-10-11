package io.github.antonovichkorp.chemmod.core.composition

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A substance has to become element masses before anything that carries mass can use it: the gas
 * field asks how much of each element sits in a cell, the radiation model turns an element mass
 * into an activity. The split comes from the graph and the element table, so these tests never name
 * a substance's composition — they derive what water or methane must weigh and check that the
 * partition is exact.
 */
class ElementMassPartitionTest {
    private fun graph(structure: String) = Molecule.fromSMILESlike(structure).graph

    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-9) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, got $actual")

    @Test
    fun `mass fractions follow atomic mass, not atom count`() {
        // Water is two hydrogens to one oxygen by count and still eleven percent hydrogen by mass.
        val fractions = ElementMassPartition.massFractions(graph("O"))
        assertEquals(setOf("H", "O"), fractions.keys)
        assertNear(15.999 / 18.015, fractions.getValue("O"), 1e-6)
        assertNear(2 * 1.008 / 18.015, fractions.getValue("H"), 1e-6)
    }

    @Test
    fun `methane is three quarters carbon by mass`() {
        val fractions = ElementMassPartition.massFractions(graph("C"))
        assertNear(12.011 / 16.043, fractions.getValue("C"), 1e-6)
    }

    @Test
    fun `fractions add up to one for whatever the molecule is`() {
        for (structure in listOf("O", "C", "CCO", "CCCl", "CN", "O=C=O")) {
            val total = ElementMassPartition.massFractions(graph(structure)).values.sum()
            assertNear(1.0, total, 1e-9)
        }
    }

    @Test
    fun `a partition returns every microgram it was given`() {
        for (structure in listOf("O", "C", "CCO", "CCCl")) {
            for (total in listOf(1L, 3L, 7L, 999_999L, 1_000_000L, 12_345_678L)) {
                val parts = ElementMassPartition.partition(graph(structure), total)
                assertEquals(total, parts.values.sum(), "$structure at $total µg did not conserve mass")
            }
        }
    }

    @Test
    fun `the leftover microgram goes to the element that lost the most`() {
        // One microgram of water cannot be split, so it lands on oxygen, which holds 88.8 % of it.
        val parts = ElementMassPartition.partition(graph("O"), 1L)
        assertEquals(1L, parts.getValue("O"))
        assertEquals(0L, parts.getValue("H"))
    }

    @Test
    fun `every element of the formula keeps a key, even at zero micrograms`() {
        val parts = ElementMassPartition.partition(graph("CCCl"), 1L)
        assertEquals(setOf("C", "H", "Cl"), parts.keys)
    }

    @Test
    fun `zero mass partitions into nothing`() {
        assertEquals(emptyMap(), ElementMassPartition.partition(graph("O"), 0L))
    }

    @Test
    fun `a negative mass is refused rather than partitioned`() {
        assertFailsWith<IllegalArgumentException> {
            ElementMassPartition.partition(graph("O"), -1L)
        }
    }

    @Test
    fun `element masses are the molar mass taken apart, not a second calculation`() {
        val graph = graph("CCO")
        val perElement = FormulaCalculator.elementMolarMasses(graph)
        assertNear(FormulaCalculator.molarMass(graph), perElement.values.sum(), 1e-12)
        assertNear(2 * 12.011, perElement.getValue("C"), 1e-9)
        assertNear(15.999, perElement.getValue("O"), 1e-9)
        assertNear(6 * 1.008, perElement.getValue("H"), 1e-9)
    }

    @Test
    fun `one element can be read out on its own`() {
        val graph = graph("O")
        val oxygen = ElementMassPartition.massOfElementMicrograms(graph, "O", 1_000_000L)
        assertEquals(888_093L, oxygen)
        // An element the substance does not contain weighs nothing, which is not the same as
        // "unknown": the formula is known to have no chlorine in it.
        assertEquals(0L, ElementMassPartition.massOfElementMicrograms(graph, "Cl", 1_000_000L))
    }
}
