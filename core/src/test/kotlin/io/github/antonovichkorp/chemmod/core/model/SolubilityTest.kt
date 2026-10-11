package io.github.antonovichkorp.chemmod.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Solubility by ion class. None of these salts is listed anywhere in the model: each answer comes
 * from the two ions, so a compound nobody enumerated is classified the same way.
 */
class SolubilityTest {
    private val table = ElementTable.default()

    private fun soluble(cation: String, anion: Solubility.AnionGroup) =
        Solubility.isSoluble(table.require(cation), anion)

    @Test
    fun `nitrates and alkali salts dissolve whatever the partner is`() {
        assertEquals(true, soluble("Na", Solubility.AnionGroup.CARBONATE))
        assertEquals(true, soluble("K", Solubility.AnionGroup.PHOSPHATE))
        assertEquals(true, soluble("Ag", Solubility.AnionGroup.NITRATE))
        assertEquals(true, soluble("Ba", Solubility.AnionGroup.NITRATE))
    }

    @Test
    fun `halides dissolve except with the cations that break the rule`() {
        assertEquals(true, soluble("Na", Solubility.AnionGroup.HALIDE))
        assertEquals(true, soluble("Ca", Solubility.AnionGroup.HALIDE))
        assertEquals(false, soluble("Ag", Solubility.AnionGroup.HALIDE))
        assertEquals(false, soluble("Pb", Solubility.AnionGroup.HALIDE))
        assertEquals(false, soluble("Hg", Solubility.AnionGroup.HALIDE))
    }

    @Test
    fun `sulfates dissolve except with barium, strontium and lead`() {
        assertEquals(true, soluble("Na", Solubility.AnionGroup.SULFATE))
        assertEquals(true, soluble("Cu", Solubility.AnionGroup.SULFATE))
        assertEquals(false, soluble("Ba", Solubility.AnionGroup.SULFATE))
        assertEquals(false, soluble("Sr", Solubility.AnionGroup.SULFATE))
        assertEquals(false, soluble("Pb", Solubility.AnionGroup.SULFATE))
    }

    @Test
    fun `weak acid anions leave solution unless the cation is an alkali metal`() {
        assertEquals(false, soluble("Ca", Solubility.AnionGroup.CARBONATE))
        assertEquals(false, soluble("Fe", Solubility.AnionGroup.PHOSPHATE))
        assertEquals(false, soluble("Zn", Solubility.AnionGroup.SULFIDE))
        assertEquals(true, soluble("Li", Solubility.AnionGroup.CARBONATE))
        assertEquals(true, soluble("Cs", Solubility.AnionGroup.SULFIDE))
    }

    @Test
    fun `hydroxides follow the same rule with the heavy alkaline earths allowed through`() {
        assertEquals(true, soluble("Na", Solubility.AnionGroup.HYDROXIDE))
        assertEquals(true, soluble("Ca", Solubility.AnionGroup.HYDROXIDE))
        assertEquals(true, soluble("Ba", Solubility.AnionGroup.HYDROXIDE))
        assertEquals(false, soluble("Mg", Solubility.AnionGroup.HYDROXIDE))
        assertEquals(false, soluble("Fe", Solubility.AnionGroup.HYDROXIDE))
        assertEquals(false, soluble("Cu", Solubility.AnionGroup.HYDROXIDE))
    }

    @Test
    fun `precipitation is the negation of solubility and is what drives a metathesis`() {
        assertEquals(true, Solubility.precipitates(table.require("Ag"), Solubility.AnionGroup.HALIDE))
        assertEquals(true, Solubility.precipitates(table.require("Ba"), Solubility.AnionGroup.SULFATE))
        assertFalse(Solubility.precipitates(table.require("Na"), Solubility.AnionGroup.HALIDE)!!)
        assertTrue(Solubility.precipitates(table.require("Ca"), Solubility.AnionGroup.CARBONATE)!!)
    }
}
