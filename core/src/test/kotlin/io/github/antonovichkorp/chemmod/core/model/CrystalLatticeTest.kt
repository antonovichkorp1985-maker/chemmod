package io.github.antonovichkorp.chemmod.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Lattice type derived from two ionic radii. None of these salts is listed anywhere in the model:
 * each answer falls out of the radius ratio alone.
 */
class CrystalLatticeTest {
    private val table = ElementTable.default()

    private fun predict(cation: String, anion: String) =
        CrystalLattice.predict(table.require(cation), table.require(anion))

    private fun ratio(cation: String, anion: String) =
        CrystalLattice.radiusRatio(table.require(cation), table.require(anion))!!

    @Test
    fun `table salt comes out rock salt from its ion sizes`() {
        assertEquals(CrystalLattice.Structure.OCTAHEDRAL, predict("Na", "Cl"))
        assertEquals(0.564, ratio("Na", "Cl"), 0.002)
    }

    @Test
    fun `a caesium halide steps up to eight coordination`() {
        assertEquals(CrystalLattice.Structure.CUBIC, predict("Cs", "Cl"))
        assertEquals(0.923, ratio("Cs", "Cl"), 0.002)
    }

    @Test
    fun `a small cation with a large anion drops to tetrahedral holes`() {
        assertEquals(CrystalLattice.Structure.TETRAHEDRAL, predict("Zn", "S"))
        assertEquals(0.402, ratio("Zn", "S"), 0.002)
    }

    @Test
    fun `magnesium oxide is octahedral and calcium fluoride is eight coordinate`() {
        assertEquals(CrystalLattice.Structure.OCTAHEDRAL, predict("Mg", "O"))
        assertEquals(CrystalLattice.Structure.CUBIC, predict("Ca", "F"))
    }

    @Test
    fun `the ratio does not depend on which ion is named first`() {
        assertEquals(ratio("Na", "Cl"), ratio("Cl", "Na"))
        assertEquals(predict("Na", "Cl"), predict("Cl", "Na"))
    }

    @Test
    fun `an element with no ionic radius reports no model rather than a structure`() {
        // Helium forms no ions and holds no radius, so nothing may be asserted about a lattice.
        val helium = table.all().first { it.ionicRadiusPicometres == null }
        assertNull(CrystalLattice.predict(helium, table.require("Cl")))
        assertNull(CrystalLattice.predict(table.require("Na"), helium))
        assertNull(CrystalLattice.radiusRatio(helium, table.require("Cl")))
    }
}
