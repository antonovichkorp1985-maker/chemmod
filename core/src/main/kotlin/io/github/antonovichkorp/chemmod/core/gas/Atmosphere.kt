package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.exp
import kotlin.math.pow

/**
 * A planetary air column: the medium something flies through, as distinct from a cloud somebody
 * leaked.
 *
 * The gas field answers "how much extra gas is in this cell". That is the wrong question for a
 * rocket on a launch pad with nothing wrong: an empty cell there is not a vacuum, it is sea-level
 * air. This class answers the background instead, and the two axes are kept apart deliberately —
 * the field is indexed by block and holds local excess, the column is indexed by geometric
 * altitude and holds the planet's air. Mixing them would make an untouched launch pad read as
 * vacuum.
 *
 * Altitude is **geometric** — metres above the surface of a sphere — because that is the axis
 * flight lives on, and it is not Minecraft Y. The standard atmosphere is defined on geopotential
 * altitude, so the input is converted (`h' = R·h/(R+h)`) before the barometric formula is applied;
 * the two differ by 0.17% at 11 km, which is exactly the difference that shows up as 216.77 K
 * rather than the tabulated 216.65 K.
 *
 * Below the tropopause the temperature falls at [lapseRateKelvinPerMetre] and pressure follows the
 * polytropic barometric formula; above it the layer is isothermal and pressure falls exponentially.
 * Every parameter is a constructor argument, so another planet is data and not a fork.
 */
data class Atmosphere(
    val surfacePressurePascals: Double = 101_325.0,
    val surfaceTemperatureKelvin: Double = 288.15,
    val lapseRateKelvinPerMetre: Double = 0.0065,
    val molarMassKilogramsPerMole: Double = 0.0289647,
    val surfaceGravityMetresPerSecondSquared: Double = 9.80665,
    val planetRadiusMetres: Double = 6_371_000.0,
    val tropopauseGeopotentialMetres: Double = 11_000.0,
) {
    init {
        require(surfacePressurePascals > 0.0) { "Surface pressure must be positive" }
        require(surfaceTemperatureKelvin > 0.0) { "Surface temperature must be positive" }
        require(lapseRateKelvinPerMetre > 0.0) { "A lapse rate must be positive to have a tropopause" }
        require(molarMassKilogramsPerMole > 0.0) { "Molar mass must be positive" }
        require(planetRadiusMetres > 0.0) { "Planet radius must be positive" }
        require(tropopauseGeopotentialMetres > 0.0) { "Tropopause altitude must be positive" }
    }

    /** Geometric altitude above the sphere, converted to the geopotential altitude the ISA uses. */
    fun geopotentialMetres(geometricAltitudeMetres: Double): Double {
        require(geometricAltitudeMetres >= 0.0) { "Altitude must not be below the surface" }
        return planetRadiusMetres * geometricAltitudeMetres /
            (planetRadiusMetres + geometricAltitudeMetres)
    }

    fun temperatureKelvin(geometricAltitudeMetres: Double): Double {
        val geopotential = geopotentialMetres(geometricAltitudeMetres)
        return if (geopotential <= tropopauseGeopotentialMetres) {
            surfaceTemperatureKelvin - lapseRateKelvinPerMetre * geopotential
        } else {
            tropopauseTemperatureKelvin()
        }
    }

    fun pressurePascals(geometricAltitudeMetres: Double): Double {
        val geopotential = geopotentialMetres(geometricAltitudeMetres)
        val exponent = surfaceGravityMetresPerSecondSquared * molarMassKilogramsPerMole /
            (GasPhase.UNIVERSAL_GAS_CONSTANT * lapseRateKelvinPerMetre)
        return if (geopotential <= tropopauseGeopotentialMetres) {
            val ratio = temperatureKelvin(geometricAltitudeMetres) / surfaceTemperatureKelvin
            surfacePressurePascals * ratio.pow(exponent)
        } else {
            val tropopausePressure = surfacePressurePascals *
                (tropopauseTemperatureKelvin() / surfaceTemperatureKelvin).pow(exponent)
            tropopausePressure * exp(
                -surfaceGravityMetresPerSecondSquared * molarMassKilogramsPerMole *
                    (geopotential - tropopauseGeopotentialMetres) /
                    (GasPhase.UNIVERSAL_GAS_CONSTANT * tropopauseTemperatureKelvin()),
            )
        }
    }

    /** Ideal-gas mass density of the background air at a geometric altitude. */
    fun densityKilogramsPerCubicMetre(geometricAltitudeMetres: Double): Double {
        val pressure = pressurePascals(geometricAltitudeMetres)
        val temperature = temperatureKelvin(geometricAltitudeMetres)
        return pressure * molarMassKilogramsPerMole /
            (GasPhase.UNIVERSAL_GAS_CONSTANT * temperature)
    }

    private fun tropopauseTemperatureKelvin(): Double =
        surfaceTemperatureKelvin - lapseRateKelvinPerMetre * tropopauseGeopotentialMetres

    companion object {
        /** Earth's International Standard Atmosphere, sea level 101325 Pa / 288.15 K / 1.225 kg/m3. */
        @JvmField
        val EARTH = Atmosphere()
    }
}
