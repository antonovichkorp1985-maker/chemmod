package io.github.antonovichkorp.chemmod.core.phase

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.properties.PropertyEngine
import io.github.antonovichkorp.chemmod.core.properties.PropertyRuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pressure is not a special case bolted onto the phase rule: the boiling point becomes a curve.
 * Both inputs to that curve — the normal boiling point and the enthalpy of vaporization — come from
 * the graph, so these tests check the physics against measured values rather than against each
 * other.
 */
class PhaseAtPressureTest {
    private val engine = PropertyEngine(PropertyRuleSet.bundled())

    private fun properties(structure: String) = engine.predict(Molecule.fromSMILESlike(structure).graph)

    private fun assertNear(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, got $actual")

    @Test
    fun `Trouton's rule reproduces measured enthalpies of vaporization within a few percent`() {
        // Measured ΔH_vap in J/mol at the normal boiling point. Donors decide the regime.
        val sourced = listOf(
            Triple(309.21, 26_390.0, 0),  // n-pentane
            Triple(341.87, 28_880.0, 0),  // n-hexane
            Triple(353.22, 30_750.0, 0),  // benzene
            Triple(383.75, 33_230.0, 0),  // toluene
            Triple(423.80, 37_180.0, 0),  // nonane
            Triple(373.15, 40_650.0, 2),  // water
            Triple(351.44, 38_560.0, 1),  // ethanol
        )
        for ((boiling, measured, donors) in sourced) {
            val estimate = EnthalpyOfVaporization.of(boiling, donors)
            val errorPercent = kotlin.math.abs(estimate.joulesPerMole / measured - 1.0) * 100.0
            assertTrue(errorPercent <= 2.5, "at $boiling K expected within 2.5 % of $measured, was off by $errorPercent %")
        }
    }

    @Test
    fun `the regime follows hydrogen-bond donors, not acceptors`() {
        // Acetone accepts hydrogen bonds on its oxygen and donates none; it belongs to the
        // non-associated band, and taking acceptors instead would put it 26 % out.
        assertEquals(0, properties("CC(C)=O").hydrogenBondDonors)
        assertEquals("trouton:non-associated", EnthalpyOfVaporization.of(329.2, 0).basis)
        assertEquals(2, properties("O").hydrogenBondDonors)
        assertEquals("trouton:hydrogen-bonded", EnthalpyOfVaporization.of(373.15, 2).basis)
    }

    @Test
    fun `water boils hotter under pressure and cooler under vacuum`() {
        val water = properties("O")
        // Measured 179.9 °C at 10 atm; the integrated form with constant ΔH_vap lands 0.7 K under.
        assertNear(179.9, PhaseAtPressure.boilingPointCelsiusAt(water, 1_013_250.0)!!, 1.0)
        // Measured 45.8 °C at 10 kPa.
        assertNear(45.8, PhaseAtPressure.boilingPointCelsiusAt(water, 10_132.5)!!, 2.0)
        // And it comes back to the anchor at standard pressure.
        assertNear(100.0, PhaseAtPressure.boilingPointCelsiusAt(water, 101_325.0)!!, 0.01)
    }

    @Test
    fun `the curve runs both ways`() {
        val vaporization = EnthalpyOfVaporization.of(373.15, 2)
        assertNear(
            101_325.0,
            VaporPressureCurve.pressurePascalsAt(373.15, vaporization.joulesPerMole, 373.15),
            0.01,
        )
        assertNear(
            373.15,
            VaporPressureCurve.boilingPointKelvinAt(373.15, vaporization.joulesPerMole, 101_325.0),
            1e-9,
        )
    }

    @Test
    fun `one temperature is a gas at one pressure and a liquid at another`() {
        // The whole point of the layer: 380 K is above water's normal boiling point and far below
        // its boiling point under ten atmospheres.
        assertEquals(Phase.GAS, PhaseAtPressure.of(properties("O"), 380.0, 101_325.0).phase)
        assertEquals(Phase.CONDENSED, PhaseAtPressure.of(properties("O"), 380.0, 1_013_250.0).phase)
    }

    @Test
    fun `at standard pressure this is exactly the standard-pressure rule`() {
        for (structure in listOf("O", "CCl", "CCCCCC", "CCO")) {
            for (temperature in listOf(200.0, 293.15, 380.0)) {
                val standard = PhaseAtStandardPressure.of(properties(structure), temperature)
                val atStandard = PhaseAtPressure.of(
                    properties(structure),
                    temperature,
                    PhaseAtStandardPressure.STANDARD_PRESSURE_PASCALS,
                )
                assertEquals(standard.phase, atStandard.phase, "$structure at $temperature K disagreed")
                assertEquals(standard.basis, atStandard.basis, "$structure at $temperature K disagreed")
            }
        }
    }

    @Test
    fun `a substance with no boiling model stays undecided at any pressure`() {
        val verdict = PhaseAtPressure.of(properties("C(Cl)(Cl)(Cl)Cl"), 293.15, 500_000.0)
        assertNull(verdict.phase)
        assertEquals("undecided:no-model:halide-fit-covers-monohalides-only", verdict.basis)
        assertNull(PhaseAtPressure.boilingPointCelsiusAt(properties("C(Cl)(Cl)(Cl)Cl"), 500_000.0))
    }

    @Test
    fun `chloromethane needs about five atmospheres to stay liquid at room temperature`() {
        // Predicted end to end: the monohalide fit gives −23.7 °C, Trouton gives 21.5 kJ/mol, and
        // the curve gives 476 kPa at 20 °C. Measured is 3796 mm Hg, which is 506 kPa. The 6 %
        // shortfall is the constant-ΔH_vap assumption over a 44 K span, not the Trouton step, which
        // is good to 2 %.
        val pressure = VaporPressureCurve.pressurePascalsAt(
            properties("CCl").boilingPointC!! + PhaseAtStandardPressure.ZERO_CELSIUS_KELVIN,
            EnthalpyOfVaporization.of(
                properties("CCl").boilingPointC!! + PhaseAtStandardPressure.ZERO_CELSIUS_KELVIN,
                properties("CCl").hydrogenBondDonors,
            ).joulesPerMole,
            293.15,
        )
        assertNear(506_090.0, pressure, 40_000.0)
        // Which is why a cylinder of it is a liquid and an open flask of it is not.
        assertEquals(Phase.GAS, PhaseAtPressure.of(properties("CCl"), 293.15, 101_325.0).phase)
        assertEquals(Phase.CONDENSED, PhaseAtPressure.of(properties("CCl"), 293.15, 506_090.0).phase)
    }

    @Test
    fun `pressure and enthalpy must both be positive`() {
        assertFailsWith<IllegalArgumentException> {
            PhaseAtPressure.of(properties("O"), 300.0, 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            VaporPressureCurve.boilingPointKelvinAt(373.15, -1.0, 200_000.0)
        }
        assertFailsWith<IllegalArgumentException> {
            VaporPressureCurve.boilingPointKelvinAt(373.15, 40_000.0, -1.0)
        }
        assertFailsWith<IllegalArgumentException> { EnthalpyOfVaporization.of(0.0, 0) }
        assertFailsWith<IllegalArgumentException> { EnthalpyOfVaporization.of(300.0, -1) }
    }
}
