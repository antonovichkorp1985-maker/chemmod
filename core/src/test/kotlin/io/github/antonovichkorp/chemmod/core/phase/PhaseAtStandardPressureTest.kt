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
 * The phase question the gas field asks: is this a vapour at this temperature? The answer has to
 * come from the boiling point the graph already produces, and a substance the boiling-point model
 * does not cover has to stay undecided instead of being quietly called a liquid.
 */
class PhaseAtStandardPressureTest {
    private val engine = PropertyEngine(PropertyRuleSet.bundled())

    private fun properties(structure: String) = engine.predict(Molecule.fromSMILESlike(structure).graph)

    private fun verdict(structure: String, temperatureKelvin: Double) =
        PhaseAtStandardPressure.of(properties(structure), temperatureKelvin)

    private fun assertNear(expected: Double, actual: Double, tolerance: Double) =
        assertTrue(kotlin.math.abs(expected - actual) <= tolerance, "expected $expected ± $tolerance, got $actual")

    @Test
    fun `water is condensed at room temperature and a gas above its boiling point`() {
        assertEquals(Phase.CONDENSED, verdict("O", 293.15).phase)
        // Exactly at the boiling point it is still condensed: the rule is a strict comparison, so a
        // substance does not flicker between phases at its own boiling point.
        assertEquals(Phase.CONDENSED, verdict("O", 373.15).phase)
        assertEquals(Phase.GAS, verdict("O", 380.0).phase)
    }

    @Test
    fun `where the boiling point came from travels into the verdict`() {
        // Water's boiling point is still a calibration anchor, a debt the model reports. Turning it
        // into a phase must not launder that away.
        assertEquals("at-or-below-boiling-point:calibration-anchor:H2O", verdict("O", 293.15).basis)
    }

    @Test
    fun `chloromethane is a gas at room temperature because it boils below it`() {
        // "CCl" is chloromethane; "CCCl" would be chloroethane, a two-carbon chain, because the
        // parser reads "Cl" as one symbol and not as C followed by l.
        val boiling = properties("CCl").boilingPointC!!
        assertNear(-24.2, boiling, 6.0) // measured −24.2 °C; the monohalide fit carries ±4.9 K RMS
        assertEquals(Phase.GAS, verdict("CCl", 293.15).phase)
        assertEquals("above-boiling-point:monohalide+dispersion", verdict("CCl", 293.15).basis)
    }

    @Test
    fun `a substance without a boiling point model is undecided and says why`() {
        val verdict = verdict("C(Cl)(Cl)(Cl)Cl", 293.15)
        assertNull(verdict.phase)
        assertEquals("undecided:no-model:halide-fit-covers-monohalides-only", verdict.basis)
    }

    @Test
    fun `any substance follows its own predicted boiling point`() {
        // The rule is checked against whatever the model produced, so this holds for a class whose
        // fitted value may move: ten kelvin either side of the boiling point is unambiguous.
        val boiling = properties("CCO").boilingPointC!!
        assertEquals(Phase.GAS, verdict("CCO", boiling + 273.15 + 10.0).phase)
        assertEquals(Phase.CONDENSED, verdict("CCO", boiling + 273.15 - 10.0).phase)
    }

    @Test
    fun `temperature must be a positive number of kelvin`() {
        assertFailsWith<IllegalArgumentException> { verdict("O", 0.0) }
        assertFailsWith<IllegalArgumentException> { verdict("O", -1.0) }
        assertFailsWith<IllegalArgumentException> { verdict("O", Double.NaN) }
    }

    @Test
    fun `a boiling point is refused without the basis it came from`() {
        assertFailsWith<IllegalArgumentException> {
            PhaseAtStandardPressure.of(100.0, "", 300.0)
        }
    }

    @Test
    fun `a null boiling point needs no temperature to be undecided`() {
        // The refusal happens before the temperature is even looked at.
        val verdict = PhaseAtStandardPressure.of(null, "no-model", 300.0)
        assertNull(verdict.phase)
        assertEquals("undecided:no-model", verdict.basis)
    }
}
