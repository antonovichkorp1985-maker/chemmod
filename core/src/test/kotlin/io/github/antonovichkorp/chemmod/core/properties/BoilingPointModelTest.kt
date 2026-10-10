package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The boiling point has to come from the graph: dispersion over molar mass, plus ring and
 * aromaticity corrections, plus the hydrogen-bond network for alcohols and polyols. Both fits
 * cover a stated domain, and outside it the model refuses instead of extrapolating.
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
    fun `families outside the fitted domain are refused, not extrapolated`() {
        // Measured this way against experiment the old mass-only branch gave dimethyl ether
        // -41.0 instead of -24.8, acetone -7.3 instead of 56.1, acetonitrile -55.9 instead of
        // 81.6 and tetrachloromethane 208.1 instead of 76.7. Refusing is the smaller lie.
        assertNull(boiling("COC"))
        assertNull(boiling("CN"))
        assertNull(boiling("CC(C)=O"))
        assertNull(boiling("C(Cl)(Cl)(Cl)Cl"))
        assertEquals("no-model:fit-covers-hydrocarbons-only", basis("C(Cl)(Cl)(Cl)Cl"))
        // Hydrocarbons stay inside the fit and keep their numbers.
        assertEquals("dispersion+ring+aromatic", basis("CCCCCC"))
    }

    @Test
    fun `polarizibility comes from element data, so halogens count for what they are`() {
        // Additive atomic values give 10.48 A^3 for CCl4 against 10.002 A^3 measured for the
        // molecule: the element table is good enough to replace molar mass in the dispersion
        // term, which is the point of carrying it.
        assertNear(10.48, engine.predict(Molecule.fromSMILESlike("C(Cl)(Cl)(Cl)Cl").graph)
            .polarizibilityCubicAngstroms!!, 0.01)
        // Methane counts its four implicit hydrogens: 1.76 + 4 * 0.667.
        assertNear(4.43, engine.predict(Molecule.fromSMILESlike("C").graph)
            .polarizibilityCubicAngstroms!!, 0.01)
        // Iron has no measured atomic value in the table, and a gap must stay a gap.
        assertNull(engine.predict(Molecule.fromSMILESlike("[Fe]").graph).polarizibilityCubicAngstroms)
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
