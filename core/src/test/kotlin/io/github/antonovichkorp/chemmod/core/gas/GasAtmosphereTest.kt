package io.github.antonovichkorp.chemmod.core.gas

import io.github.antonovichkorp.chemmod.core.gas.GasToxicity.Mechanism
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GasAtmosphereTest {
    @Test
    fun `empty field resolves to dry standard air`() {
        val fractions = GasAtmosphere.moleFractions(
            emptyMap(),
            volumeCubicMeters = 64.0,
            temperatureKelvin = 293.15,
        )

        assertEquals(1.0, fractions.values.sum(), 1e-12)
        assertEquals(0.2095, fractions.getValue("O2"), 1e-12)
        assertEquals(0.7808, fractions.getValue("N2"), 1e-12)
    }

    @Test
    fun `field amount is converted to ideal-gas ppm against cell air inventory`() {
        val chlorinePpm = GasAtmosphere.partsPerMillion(
            formula = "Cl2",
            fieldMolesByFormula = mapOf("Cl2" to 4.0),
            volumeCubicMeters = 64.0,
            temperatureKelvin = 293.15,
        )

        assertTrue(chlorinePpm in 1_490.0..1_510.0, "4 mol Cl2 in a 4 m cell gives $chlorinePpm ppm")
    }

    @Test
    fun `large inert addition lowers oxygen fraction through displacement`() {
        val composition = GasAtmosphere.moleFractions(
            fieldMolesByFormula = mapOf("CO2" to 600.0),
            volumeCubicMeters = 64.0,
            temperatureKelvin = 293.15,
        )

        assertTrue(composition.getValue("O2") < GasToxicity.SAFE_OXYGEN_FRACTION)
        assertTrue(
            GasToxicity.assess(composition).any {
                it is GasToxicity.ToxicityAssessment.ByMechanism && it.mechanism == Mechanism.OXYGEN_DISPLACEMENT
            },
        )
    }

    @Test
    fun `mole inventory rejects invalid physical conditions and negative species`() {
        assertFailsWith<IllegalArgumentException> {
            GasAtmosphere.ambientAirMoles(volumeCubicMeters = 0.0, temperatureKelvin = 293.15)
        }
        assertFailsWith<IllegalArgumentException> {
            GasAtmosphere.moleFractions(
                fieldMolesByFormula = mapOf("Cl2" to -1.0),
                volumeCubicMeters = 64.0,
                temperatureKelvin = 293.15,
            )
        }
    }
}
