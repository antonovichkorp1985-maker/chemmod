package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The boiling point has to come from the graph: dispersion over molar mass, plus ring and
 * aromaticity corrections, plus the hydrogen-bond network for alcohols and polyols.
 * Anything that is still a substance row must say so through `boilingPointBasis`.
 */
class BoilingPointModelTest {
    private val engine = PropertyEngine(PropertyRuleSet.bundled())

    private fun boiling(structure: String): Double? =
        engine.predict(Molecule.fromSMILESlike(structure).graph).boilingPointC

    private fun basis(structure: String): String =
        engine.predict(Molecule.fromSMILESlike(structure).graph).boilingPointBasis

    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1.0) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, got $actual")

    @Test
    fun `water is still a calibration anchor and says so`() {
        assertNear(100.0, boiling("O")!!, 0.1)
        assertEquals("calibration-anchor:H2O", basis("O"))
    }

    @Test
    fun `the anchor list is not allowed to grow`() {
        val anchors = PropertyRuleSet.bundled().boiling.exactFormulaCelsius
        assertEquals(setOf("H2O"), anchors.keys, "a new row would be a substance record, not a model")
    }

    @Test
    fun `alkanes follow dispersion instead of a carbon-count polynomial`() {
        assertNear(-91.0, boiling("CC")!!)
        assertNear(-29.9, boiling("C1CC1")!!)
        assertNear(63.6, boiling("CCCCCC")!!)
        assertEquals("dispersion+ring+aromatic", basis("CCCCCC"))
    }

    @Test
    fun `rings and aromatic rings carry their own corrections`() {
        assertNear(81.7, boiling("C1CCCCC1")!!)
        assertNear(76.0, boiling("C1=CC=CC=C1")!!)
        assertNear(109.4, boiling("CC1=CC=CC=C1")!!)
    }

    @Test
    fun `alcohols rise with the chain and with their hydrogen-bond network`() {
        assertNear(64.3, boiling("CO")!!)
        assertNear(83.7, boiling("CCO")!!)
        assertNear(101.2, boiling("CCCO")!!)
        assertNear(117.3, boiling("CCCCO")!!)
        assertEquals("hydroxyl-family+hydrogen-bond-network", basis("CCO"))
    }

    @Test
    fun `a diol and a triol boil higher than a heavier alkane because of their sites`() {
        val glycol = boiling("OCCO")!!
        val hexane = boiling("CCCCCC")!!
        assertNear(181.6, glycol)
        assertTrue(glycol > hexane, "ethylene glycol (62 g/mol) must outboil hexane (86 g/mol)")
        // Glycerol was not part of the fit: three donor and three acceptor sites, 63 kJ/mol network.
        assertNear(292.9, boiling("OCC(O)CO")!!, 3.0)
    }

    @Test
    fun `an ether keeps its mass but loses the hydrogen-bond term`() {
        assertNear(-41.0, boiling("COC")!!)
        assertTrue(boiling("CCO")!! - boiling("COC")!! > 100.0, "isomers must differ by the network")
    }

    @Test
    fun `carbon-free substances outside the anchor get no value at all`() {
        assertNull(boiling("N"))
        assertEquals("no-model", basis("N"))
    }

    @Test
    fun `a hydrogen bond can never lower the boiling point`() {
        assertTrue(PropertyRuleSet.bundled().boiling.hydrogenBondCelsiusPerKilojoulePerMole > 0.0)
        assertTrue(PropertyRuleSet.bundled().boiling.hydrocarbonDispersionCelsius > 0.0)
    }
}
