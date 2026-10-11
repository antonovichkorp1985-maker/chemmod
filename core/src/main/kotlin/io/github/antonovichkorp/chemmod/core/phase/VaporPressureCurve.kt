package io.github.antonovichkorp.chemmod.core.phase

import kotlin.math.exp
import kotlin.math.ln

/**
 * How the boiling point moves with pressure, by the integrated Clausius–Clapeyron equation:
 *
 * ```text
 * ln(P / P₀) = −(ΔH_vap / R) · (1/T − 1/T₀)
 * ```
 *
 * One anchor — the normal boiling point at standard pressure, which the boiling-point model already
 * predicts from the graph — plus one slope, the enthalpy of vaporization, give the whole curve.
 * Nothing here knows any substance.
 *
 * The integrated form assumes ΔH_vap does not change with temperature. That holds well over a few
 * tens of kelvin and degrades outside it: for water it puts the boiling point at 179.6 °C under
 * 10 atm against a measured 179.9 °C, and at 44.2 °C under 10 kPa against a measured 45.8 °C. Over
 * a wider span — cryogenic storage, high-pressure vessels — a temperature-dependent ΔH_vap or a
 * Wagner-type vapour-pressure correlation is the honest next step, not a wider table.
 */
object VaporPressureCurve {
    const val GAS_CONSTANT_JOULES_PER_MOLE_KELVIN = 8.314462618

    /** The boiling point in kelvin at [pressurePascals], given the normal boiling point and ΔH_vap. */
    fun boilingPointKelvinAt(
        normalBoilingPointKelvin: Double,
        joulesPerMoleVaporization: Double,
        pressurePascals: Double,
    ): Double {
        val (boiling, vaporization, pressure) =
            validated(normalBoilingPointKelvin, joulesPerMoleVaporization, pressurePascals)
        val inverse = 1.0 / boiling -
            GAS_CONSTANT_JOULES_PER_MOLE_KELVIN * ln(pressure / PhaseAtStandardPressure.STANDARD_PRESSURE_PASCALS) /
            vaporization
        require(inverse > 0.0) {
            "No boiling point exists at $pressure Pa for this substance: the curve has run off its end"
        }
        return 1.0 / inverse
    }

    /** The vapour pressure in pascals at [temperatureKelvin], the inverse of the same curve. */
    fun pressurePascalsAt(
        normalBoilingPointKelvin: Double,
        joulesPerMoleVaporization: Double,
        temperatureKelvin: Double,
    ): Double {
        val (boiling, vaporization, temperature) =
            validated(normalBoilingPointKelvin, joulesPerMoleVaporization, temperatureKelvin)
        return PhaseAtStandardPressure.STANDARD_PRESSURE_PASCALS *
            exp(-vaporization / GAS_CONSTANT_JOULES_PER_MOLE_KELVIN * (1.0 / temperature - 1.0 / boiling))
    }

    private fun validated(
        normalBoilingPointKelvin: Double,
        joulesPerMoleVaporization: Double,
        third: Double,
    ): Triple<Double, Double, Double> {
        require(!normalBoilingPointKelvin.isNaN()) { "A boiling point must be a number" }
        require(normalBoilingPointKelvin > 0.0) {
            "A boiling point must be positive in kelvin, got $normalBoilingPointKelvin"
        }
        require(!joulesPerMoleVaporization.isNaN()) { "An enthalpy must be a number" }
        require(joulesPerMoleVaporization > 0.0) {
            "Vaporization needs energy, got $joulesPerMoleVaporization J/mol"
        }
        require(!third.isNaN()) { "Pressure or temperature must be a number" }
        require(third > 0.0) { "Pressure and temperature must both be positive, got $third" }
        return Triple(normalBoilingPointKelvin, joulesPerMoleVaporization, third)
    }
}
