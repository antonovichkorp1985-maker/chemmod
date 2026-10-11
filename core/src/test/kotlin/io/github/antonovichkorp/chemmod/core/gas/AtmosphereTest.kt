package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The background air column. The values checked here are the International Standard Atmosphere,
 * which is what a launch pad with nothing wrong must read as - an empty cell of the gas field is
 * not a vacuum.
 */
class AtmosphereTest {
    private val earth = Atmosphere.EARTH

    @Test
    fun `sea level reads as the standard atmosphere`() {
        assertEquals(101_325.0, earth.pressurePascals(0.0), 1e-6)
        assertEquals(288.15, earth.temperatureKelvin(0.0), 1e-9)
        assertEquals(1.225, earth.densityKilogramsPerCubicMetre(0.0), 5e-4)
    }

    @Test
    fun `pressure density and temperature all fall with altitude`() {
        val altitudes = listOf(0.0, 1_000.0, 5_000.0, 11_000.0, 20_000.0, 50_000.0, 80_000.0)
        for ((lower, higher) in altitudes.zipWithNext()) {
            assertTrue(
                earth.pressurePascals(higher) < earth.pressurePascals(lower),
                "pressure did not fall between $lower m and $higher m",
            )
            assertTrue(
                earth.densityKilogramsPerCubicMetre(higher) < earth.densityKilogramsPerCubicMetre(lower),
                "density did not fall between $lower m and $higher m",
            )
            assertTrue(
                earth.temperatureKelvin(higher) <= earth.temperatureKelvin(lower),
                "temperature rose between $lower m and $higher m",
            )
        }
    }

    @Test
    fun `the tropopause matches the tabulated standard atmosphere`() {
        assertEquals(22_632.0, earth.pressurePascals(11_000.0), 200.0)
        assertEquals(0.3639, earth.densityKilogramsPerCubicMetre(11_000.0), 2e-3)
        assertEquals(5_475.0, earth.pressurePascals(20_000.0), 120.0)
    }

    @Test
    fun `geometric altitude is not geopotential altitude`() {
        // The distinction is the point: the ISA is defined on geopotential altitude, flight on
        // geometric. At 11 km the two differ enough to move the temperature by a tenth of a kelvin.
        assertTrue(
            earth.geopotentialMetres(11_000.0) < 11_000.0,
            "geopotential altitude was not below geometric altitude",
        )
        assertEquals(10_981.1, earth.geopotentialMetres(11_000.0), 0.5)
        assertEquals(216.77, earth.temperatureKelvin(11_000.0), 0.02)
    }

    @Test
    fun `high altitude approaches vacuum without ever reaching it`() {
        val pressure = earth.pressurePascals(80_000.0)
        assertTrue(pressure > 0.0, "the model returned a non-physical pressure")
        assertTrue(pressure < 1.0, "80 km was not nearly vacuum: $pressure Pa")
    }

    @Test
    fun `density is consistent with pressure and temperature through the ideal gas law`() {
        for (altitude in listOf(0.0, 3_000.0, 15_000.0, 40_000.0)) {
            val expected = earth.pressurePascals(altitude) * earth.molarMassKilogramsPerMole /
                (GasPhase.UNIVERSAL_GAS_CONSTANT * earth.temperatureKelvin(altitude))
            assertTrue(
                abs(expected - earth.densityKilogramsPerCubicMetre(altitude)) < 1e-12,
                "density disagreed with the ideal gas law at $altitude m",
            )
        }
    }

    @Test
    fun `another planet is data rather than a fork`() {
        val thin = earth.copy(surfacePressurePascals = 610.0, molarMassKilogramsPerMole = 0.04334)
        assertEquals(610.0, thin.pressurePascals(0.0), 1e-9)
        assertTrue(
            thin.densityKilogramsPerCubicMetre(0.0) < earth.densityKilogramsPerCubicMetre(0.0) / 50.0,
            "a 610 Pa carbon-dioxide surface was not far thinner than Earth's",
        )
    }
}
