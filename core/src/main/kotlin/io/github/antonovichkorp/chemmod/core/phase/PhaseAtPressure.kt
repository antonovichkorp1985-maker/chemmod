package io.github.antonovichkorp.chemmod.core.phase

import io.github.antonovichkorp.chemmod.core.properties.PredictedProperties

/**
 * The same question [PhaseAtStandardPressure] answers, at any pressure: is this a gas here?
 *
 * The boiling point is no longer a constant of the substance but a function of pressure, taken from
 * the Clausius–Clapeyron curve anchored at the normal boiling point and sloped by the enthalpy of
 * vaporization. Both of those come from the graph — the boiling point from the fitted family, the
 * enthalpy from Trouton's rule — so pressure costs no new substance data.
 *
 * At standard pressure this delegates to [PhaseAtStandardPressure] instead of running the curve, so
 * the two cannot disagree by a rounding error at the one pressure that already worked.
 *
 * Melting point is still not modelled, so [Phase.CONDENSED] still does not say liquid or solid.
 * That limit is inherited, not introduced here.
 */
object PhaseAtPressure {
    /**
     * @param hydrogenBondDonors how many hydrogens the molecule carries on N, O or F. It decides
     *   which Trouton regime applies, and it is already counted by the property model.
     */
    fun of(
        boilingPointC: Double?,
        basis: String,
        hydrogenBondDonors: Int,
        temperatureKelvin: Double,
        pressurePascals: Double,
    ): PhaseVerdict {
        require(basis.isNotBlank()) { "A boiling point must carry the basis it came from" }
        require(!pressurePascals.isNaN()) { "Pressure must be a number" }
        require(pressurePascals > 0.0) { "Pressure must be positive, got $pressurePascals Pa" }
        if (boilingPointC == null) {
            require(!temperatureKelvin.isNaN()) { "Temperature must be a number" }
            require(temperatureKelvin > 0.0) { "Temperature must be positive in kelvin" }
            return PhaseVerdict(null, "undecided:$basis")
        }
        if (pressurePascals == PhaseAtStandardPressure.STANDARD_PRESSURE_PASCALS) {
            return PhaseAtStandardPressure.of(boilingPointC, basis, temperatureKelvin)
        }
        val boilingHere = boilingPointKelvinAt(boilingPointC, basis, hydrogenBondDonors, pressurePascals)
        require(!temperatureKelvin.isNaN()) { "Temperature must be a number" }
        require(temperatureKelvin > 0.0) { "Temperature must be positive in kelvin, got $temperatureKelvin" }
        val vaporization = EnthalpyOfVaporization.of(
            boilingPointC + PhaseAtStandardPressure.ZERO_CELSIUS_KELVIN,
            hydrogenBondDonors,
        )
        val suffix = "at-${pressurePascals}Pa:$basis+${vaporization.basis}"
        return if (temperatureKelvin > boilingHere) {
            PhaseVerdict(Phase.GAS, "above-boiling-point-$suffix")
        } else {
            PhaseVerdict(Phase.CONDENSED, "at-or-below-boiling-point-$suffix")
        }
    }

    /** The same rule reading a boiling point and a donor count straight off [PredictedProperties]. */
    fun of(properties: PredictedProperties, temperatureKelvin: Double, pressurePascals: Double): PhaseVerdict =
        of(
            properties.boilingPointC,
            properties.boilingPointBasis,
            properties.hydrogenBondDonors,
            temperatureKelvin,
            pressurePascals,
        )

    /**
     * Where the substance boils at [pressurePascals], in degrees Celsius, or null when the
     * boiling-point model does not cover it. This is what a pressure gauge or a sensor block needs
     * in order to say "this flask is above the boiling point at its own pressure".
     */
    fun boilingPointCelsiusAt(
        properties: PredictedProperties,
        pressurePascals: Double,
    ): Double? {
        val boiling = properties.boilingPointC ?: return null
        return boilingPointKelvinAt(
            boiling,
            properties.boilingPointBasis,
            properties.hydrogenBondDonors,
            pressurePascals,
        ) - PhaseAtStandardPressure.ZERO_CELSIUS_KELVIN
    }

    private fun boilingPointKelvinAt(
        boilingPointC: Double,
        basis: String,
        hydrogenBondDonors: Int,
        pressurePascals: Double,
    ): Double {
        require(basis.isNotBlank()) { "A boiling point must carry the basis it came from" }
        val normalBoilingKelvin = boilingPointC + PhaseAtStandardPressure.ZERO_CELSIUS_KELVIN
        val vaporization = EnthalpyOfVaporization.of(normalBoilingKelvin, hydrogenBondDonors)
        return VaporPressureCurve.boilingPointKelvinAt(
            normalBoilingKelvin,
            vaporization.joulesPerMole,
            pressurePascals,
        )
    }
}
