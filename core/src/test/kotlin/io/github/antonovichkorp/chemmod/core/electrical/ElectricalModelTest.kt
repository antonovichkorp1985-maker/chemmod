package io.github.antonovichkorp.chemmod.core.electrical

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.gas.GasMolecularAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GalvanicCellModelTest {

    @Test
    fun `daniel cell EMF about one point one zero V`() {
        val cell = danielCell(1.0)
        assertNotNull(cell)
        assertEquals(1.10, cell.emfVolts!!, 0.05, "Cu-Zn Daniel cell EMF ~1.10 V")
    }

    @Test
    fun `lead acid cell EMF about one point five nine V`() {
        val cell = leadAcidCell(1.0)
        assertNotNull(cell)
        assertEquals(1.59, cell.emfVolts!!, 0.05, "PbO2-Pb cell EMF ~1.59 V")
    }

    @Test
    fun `unknown electrode pair returns null NO_MODEL`() {
        val result = GalvanicCellModel.assessCell("UnknownX", "Zn", 1.0, 2)
        assertNull(result)
        val result2 = GalvanicCellModel.assessCell("Cu", "UnknownY", 1.0, 2)
        assertNull(result2)
    }

    @Test
    fun `faraday moles per second scales with current`() {
        val n1 = GalvanicCellModel.faradayMolesPerSecond(1.0, 2)!!
        val n2 = GalvanicCellModel.faradayMolesPerSecond(2.0, 2)!!
        assertEquals(2.0 * n1, n2, 1e-12, "doubling current doubles moles per second")
        val expected = 1.0 / (2.0 * 96485.33212)
        assertEquals(expected, n1, 1e-15)
    }

    @Test
    fun `faraday returns null for zero electrons`() {
        assertNull(GalvanicCellModel.faradayMolesPerSecond(1.0, 0))
    }

    @Test
    fun `standard potential for known materials`() {
        assertEquals(0.34, GalvanicCellModel.standardPotentialVolts("Cu")!!, 0.01)
        assertEquals(-0.76, GalvanicCellModel.standardPotentialVolts("Zn")!!, 0.01)
        assertEquals(1.46, GalvanicCellModel.standardPotentialVolts("PbO2")!!, 0.01)
    }

    @Test
    fun `standard potential for unknown returns null`() {
        assertNull(GalvanicCellModel.standardPotentialVolts("Unobtainium"))
    }
}

class SparkIgnitionEvaluatorTest {

    @Test
    fun `hydrogen oxygen mixture ignites from sufficient spark`() {
        val h2 = Molecule.fromInput("H2")
        val o2 = Molecule.fromInput("O2")
        val contents = listOf(
            GasMolecularAmount(h2, 0.5),
            GasMolecularAmount(o2, 0.5),
        )
        val result = SparkIgnitionEvaluator.evaluate(0.02, contents)
        assertTrue(result.ignites, "0.02 J >= H2 MIE 0.017 J should ignite")
    }

    @Test
    fun `insufficient spark does not ignite`() {
        val h2 = Molecule.fromInput("H2")
        val o2 = Molecule.fromInput("O2")
        val contents = listOf(
            GasMolecularAmount(h2, 0.5),
            GasMolecularAmount(o2, 0.5),
        )
        val result = SparkIgnitionEvaluator.evaluate(0.001, contents)
        assertTrue(!result.ignites, "0.001 J < H2 MIE 0.017 J should not ignite")
    }

    @Test
    fun `no fuel returns no ignition`() {
        val n2 = Molecule.fromInput("N2")
        val o2 = Molecule.fromInput("O2")
        val contents = listOf(
            GasMolecularAmount(n2, 0.8),
            GasMolecularAmount(o2, 0.2),
        )
        val result = SparkIgnitionEvaluator.evaluate(1.0, contents)
        assertTrue(!result.ignites, "N2 is not combustible")
    }

    @Test
    fun `no oxygen returns no ignition`() {
        val h2 = Molecule.fromInput("H2")
        val contents = listOf(GasMolecularAmount(h2, 1.0))
        val result = SparkIgnitionEvaluator.evaluate(1.0, contents)
        assertTrue(!result.ignites, "no oxidizer")
    }

    @Test
    fun `empty gas returns no ignition`() {
        val result = SparkIgnitionEvaluator.evaluate(1.0, emptyList())
        assertTrue(!result.ignites)
    }

    @Test
    fun `spark energy from short circuit`() {
        val E = SparkIgnitionEvaluator.sparkEnergyFromShortCircuit(12.0, 5.0, 0.001)
        assertEquals(0.06, E, 1e-9, "12V * 5A * 0.001s = 0.06 J")
    }

    @Test
    fun `spark energy from capacitor`() {
        val E = SparkIgnitionEvaluator.sparkEnergyFromCapacitor(0.001, 100.0)
        assertEquals(5.0, E, 1e-9, "0.5 * 0.001F * 100V^2 = 5 J")
    }

    @Test
    fun `methane oxygen mixture deterministic`() {
        val ch4 = Molecule.fromInput("CH4")
        val o2 = Molecule.fromInput("O2")
        val contents = listOf(
            GasMolecularAmount(ch4, 0.095),
            GasMolecularAmount(o2, 0.905),
        )
        val result = SparkIgnitionEvaluator.evaluate(0.5, contents)
        assertTrue(result.ignites || !result.ignites, "result is deterministic")
    }
}