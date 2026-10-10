package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A coordinate bond is predicted from a lone pair meeting a real vacancy. Boron in BF3 is
 * electron-deficient by bookkeeping, not because it is named as an acid anywhere.
 */
class CoordinateBondModelTest {
    private val model = CoordinateBondModel.bundled()

    private fun graph(structure: String) = Molecule.fromSMILESlike(structure).graph

    @Test
    fun `boron trifluoride has one vacancy and three donors of its own`() {
        val boronTrifluoride = graph("FB(F)F")
        assertEquals(1, model.acceptorAtoms(boronTrifluoride).size)
        assertEquals(3, model.donorAtoms(boronTrifluoride).size)
    }

    @Test
    fun `ammonia donates its lone pair into the vacancy`() {
        val pairs = model.pairs(graph("N"), graph("FB(F)F"))
        assertEquals(1, pairs.size)
        assertEquals(100.0, model.estimatedKilojoulesPerMole(pairs))
    }

    @Test
    fun `water is also a donor`() {
        assertEquals(1, model.pairs(graph("O"), graph("FB(F)F")).size)
    }

    @Test
    fun `a saturated carbon is not a Lewis acid`() {
        assertEquals(emptyList(), model.acceptorAtoms(graph("C")))
        assertEquals(emptyList(), model.pairs(graph("N"), graph("C")))
    }

    @Test
    fun `a hydrocarbon has no lone pair to donate`() {
        assertEquals(emptyList(), model.donorAtoms(graph("CC")))
        assertEquals(emptyList(), model.pairs(graph("CC"), graph("FB(F)F")))
    }

    @Test
    fun `aluminium and beryllium halides are electron deficient the same way`() {
        assertEquals(1, model.acceptorAtoms(graph("Cl[Al](Cl)Cl")).size)
        assertEquals(1, model.acceptorAtoms(graph("Cl[Be]Cl")).size)
        assertEquals(1, model.pairs(graph("O"), graph("Cl[Al](Cl)Cl")).size)
    }

    @Test
    fun `hypervalent sulfur has no vacancy left`() {
        assertTrue(model.acceptorAtoms(graph("FS(F)(F)(F)(F)F")).isEmpty())
    }
}
