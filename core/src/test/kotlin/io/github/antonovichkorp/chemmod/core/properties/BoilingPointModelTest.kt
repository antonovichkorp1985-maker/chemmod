package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The boiling point has to come from the graph: dispersion over molar mass, plus ring and
 * aromaticity corrections, plus the hydrogen-bond network for alcohols and polyols, plus a branch
 * per compound class for amines, carbonyls and ethers. Every fit covers a stated domain, and
 * outside it the model refuses instead of extrapolating.
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
        // Ethers, ketones and amines have their own branches now; what is still refused is a
        // nitrile, a halide, an ester and a carboxylic acid - measured, they were off by 137 K,
        // 131 K and more, so no number is better than a wrong one.
        assertNull(boiling("CC#N"))
        assertNull(boiling("C(Cl)(Cl)(Cl)Cl"))
        assertNull(boiling("CCOC(C)=O"))
        assertNull(boiling("CC(=O)O"))
        assertEquals("no-model:fit-covers-hydrocarbons-only", basis("C(Cl)(Cl)(Cl)Cl"))
        // Hydrocarbons stay inside the fit and keep their numbers.
        assertEquals("dispersion+ring+aromatic", basis("CCCCCC"))
    }

    @Test
    fun `polarizibility comes from element data, so halogens count for what they are`() {
        // Additive free-atom sums give 10.33 A^3 for CCl4 against 10.002 A^3 measured for the
        // molecule (+3 %): for a halide the sum is a usable descriptor. For a hydride it is
        // not - CH4 sums to 4.34 A^3 against 2.66 A^3 measured, because hydrogen bound to
        // carbon is far less polarizable than a free hydrogen atom. So the sum is a fitted
        // descriptor whose branch carries its own intercept, never a measured molecular value.
        assertNear(10.33, engine.predict(Molecule.fromSMILESlike("C(Cl)(Cl)(Cl)Cl").graph)
            .polarizibilityCubicAngstroms!!, 0.01)
        // Methane counts its four implicit hydrogens: 1.674 + 4 * 0.667.
        assertNear(4.34, engine.predict(Molecule.fromSMILESlike("C").graph)
            .polarizibilityCubicAngstroms!!, 0.01)
        // Livermorium is the single element the source table leaves without a value, and a
        // gap must stay a gap instead of quietly turning into zero.
        assertNull(engine.predict(Molecule.fromSMILESlike("[Lv]").graph).polarizibilityCubicAngstroms)
    }

    @Test
    fun `amines boil by dispersion plus the hydrogens their nitrogen can donate`() {
        // Fit on ten amines, RMS 4.4 K, worst trimethylamine at -7.3 K.
        assertNear(-6.3, boiling("CN")!!, 8.0)
        assertNear(16.6, boiling("CCN")!!, 8.0)
        assertNear(78.0, boiling("CCCCN")!!, 8.0)
        assertNear(3.5, boiling("CN(C)C")!!, 8.0)
        assertNear(89.5, boiling("CCN(CC)CC")!!, 8.0)
        assertEquals("amine+hydrogen-bond-donors", basis("CN"))
    }

    @Test
    fun `three isomeric butylamines separate by donor count, not by mass`() {
        // Same formula C4H11N, same molar mass: a primary amine donates two hydrogens, a
        // secondary one, a tertiary none - and that is exactly the order they boil in.
        val primary = boiling("CCCCN")!!
        val secondary = boiling("CCNCC")!!
        val tertiary = boiling("CCN(C)C")!!
        assertTrue(primary > secondary, "primary must outboil secondary: $primary vs $secondary")
        assertTrue(secondary > tertiary, "secondary must outboil tertiary: $secondary vs $tertiary")
    }

    @Test
    fun `ketones and ethers get their own branches`() {
        assertNear(56.1, boiling("CC(C)=O")!!, 4.0)
        assertNear(79.6, boiling("CCC(C)=O")!!, 4.0)
        assertEquals("carbonyl+dispersion", basis("CC(C)=O"))
        assertNear(-24.8, boiling("COC")!!, 2.0)
        assertNear(34.6, boiling("CCOCC")!!, 2.0)
        assertEquals("ether+dispersion", basis("COC"))
        // Isomers again: the alcohol keeps its hydrogen-bond network, the ether does not.
        assertTrue(boiling("CCO")!! - boiling("COC")!! > 100.0)
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
