package io.github.antonovichkorp.chemmod.core.gas

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GasFieldReactionTest {
    private val field = GasField(cellSizeMetres = 4.0)
    private val cell = GasCell(0, 0, 0)

    @Test
    fun `same formula isomers occupy distinct graph identities`() {
        val ethanol = molecule("CCO")
        val dimethylEther = molecule("COC")
        assertEquals(ethanol.formula(), dimethylEther.formula())
        assertNotEquals(ethanol.canonicalKey(), dimethylEther.canonicalKey())

        field.addMoles(cell, ethanol, 0.25)
        field.addMoles(cell, dimethylEther, 0.75)

        val contents = field.molecularContents(cell)
        assertEquals(2, contents.size)
        assertEquals(setOf(0.25, 0.75), contents.map { it.moles }.toSet())
        assertEquals(setOf(ethanol.canonicalKey(), dimethylEther.canonicalKey()),
            contents.map { it.molecule.canonicalKey() }.toSet())
    }

    @Test
    fun `ordinary balanced combustion rule commits graph products to a cell`() {
        val methane = molecule("C")
        val oxygen = molecule("O=O")
        val nitrogen = molecule("N#N")
        field.addMoles(cell, methane, 1.0)
        field.addMoles(cell, oxygen, 2.0)
        field.addMoles(cell, nitrogen, 7.52)
        val before = field.molecularContents(cell)

        val plans = field.applyEnvironmentalReactions(
            seconds = 1.0,
            environmentProvider = GasCellReactionEnvironmentProvider { _, _ ->
                ReactionEnvironment(temperatureKelvin = 600.0, pressureKilopascals = 101.325)
            },
        )

        assertEquals(1, plans.size)
        assertEquals("chemmod:complete_combustion", plans.getValue(cell).ruleId.value)
        val after = field.molecularContents(cell)
        assertTrue(after.any { it.molecule.formula() == "CO2" })
        assertTrue(after.any { it.molecule.formula() == "H2O" })
        assertEquals(7.52, after.single { it.molecule.canonicalKey() == nitrogen.canonicalKey() }.moles, 1e-12)
        assertAtomMolesEqual(before, after)
    }

    @Test
    fun `hydrogen and oxygen use the shared balanced rule in a cell`() {
        val hydrogen = molecule("[H][H]")
        val oxygen = molecule("O=O")
        field.addMoles(cell, hydrogen, 2.0)
        field.addMoles(cell, oxygen, 1.0)

        val plans = field.applyEnvironmentalReactions(
            seconds = 1.0,
            environmentProvider = GasCellReactionEnvironmentProvider { _, _ ->
                ReactionEnvironment(temperatureKelvin = 600.0, pressureKilopascals = 101.325)
            },
        )

        assertEquals("chemmod:hydrogen_combustion", plans.getValue(cell).ruleId.value)
        assertTrue(field.molecularContents(cell).any { it.molecule.formula() == "H2O" && it.moles > 0.0 })
    }

    @Test
    fun `legacy formula labels are not guessed into molecular graphs`() {
        field.addMoles(cell, "CH4", 1.0)
        field.addMoles(cell, "O2", 2.0)
        val plans = field.applyEnvironmentalReactions(
            seconds = 1.0,
            environmentProvider = GasCellReactionEnvironmentProvider { _, _ ->
                ReactionEnvironment(temperatureKelvin = 600.0, pressureKilopascals = 101.325)
            },
        )

        assertTrue(plans.isEmpty())
        assertEquals(1.0, field.molesOf(cell, "CH4"), 0.0)
        assertEquals(2.0, field.molesOf(cell, "O2"), 0.0)
        assertTrue(field.molecularContents(cell).isEmpty())
    }

    private fun assertAtomMolesEqual(before: List<GasMolecularAmount>, after: List<GasMolecularAmount>) {
        fun totals(contents: List<GasMolecularAmount>): Map<String, Double> = buildMap {
            contents.forEach { amount ->
                FormulaCalculator.counts(amount.molecule.graph).forEach { (element, count) ->
                    put(element, (get(element) ?: 0.0) + count * amount.moles)
                }
            }
        }
        val beforeTotals = totals(before)
        val afterTotals = totals(after)
        assertEquals(beforeTotals.keys, afterTotals.keys)
        beforeTotals.forEach { (element, amount) ->
            assertEquals(amount, afterTotals.getValue(element), 1e-10, "element $element was not conserved")
        }
    }

    private fun molecule(structure: String) = Molecule.fromSMILESlike(structure)
}
