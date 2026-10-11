package io.github.antonovichkorp.chemmod.core.phase

import io.github.antonovichkorp.chemmod.core.properties.PredictedProperties

/** The two states this layer can tell apart: a vapour, and everything that is not one. */
enum class Phase { GAS, CONDENSED }

/**
 * The answer plus where it came from. [phase] is null when the model refuses, and [basis] then
 * carries the reason, so an undecided substance is never mistaken for a decision.
 */
data class PhaseVerdict(val phase: Phase?, val basis: String) {
    init {
        require(basis.isNotBlank()) { "A verdict must say where it came from" }
    }
}

/**
 * Is this substance a gas at a given temperature, at standard pressure?
 *
 * The rule is one comparison against the boiling point, which is itself predicted from the graph.
 * Nothing here knows any substance: water is condensed at 20 °C because its boiling point is above
 * it, and chloromethane is a gas for the same reason in the other direction.
 *
 * Two honest limits, both reported through [PhaseVerdict.basis] rather than hidden:
 *
 * - **Melting point is not modelled**, so [Phase.CONDENSED] does not distinguish a liquid from a
 *   solid. Adding that split means a melting-point model, not another branch here.
 * - **Pressure is fixed at standard.** This is deliberately the whole name of the object: when a
 *   pressure field exists, the comparison becomes a function of (T, P) and a substance with a
 *   boiling point below room temperature becomes condensable under pressure.
 */
object PhaseAtStandardPressure {
    const val STANDARD_PRESSURE_PASCALS = 101_325.0
    const val ZERO_CELSIUS_KELVIN = 273.15

    /**
     * @param boilingPointC the boiling point in degrees Celsius, or null when no model covers the
     *   substance.
     * @param basis where that boiling point came from. It travels into the verdict unchanged, so a
     *   per-substance calibration anchor is still visible once it has been turned into a phase.
     */
    fun of(boilingPointC: Double?, basis: String, temperatureKelvin: Double): PhaseVerdict {
        require(basis.isNotBlank()) { "A boiling point must carry the basis it came from" }
        require(!temperatureKelvin.isNaN()) { "Temperature must be a number" }
        require(temperatureKelvin > 0.0) { "Temperature must be positive in kelvin, got $temperatureKelvin" }
        if (boilingPointC == null) {
            return PhaseVerdict(null, "undecided:$basis")
        }
        require(!boilingPointC.isNaN()) { "A boiling point must be a number" }
        val boilingKelvin = boilingPointC + ZERO_CELSIUS_KELVIN
        return if (temperatureKelvin > boilingKelvin) {
            PhaseVerdict(Phase.GAS, "above-boiling-point:$basis")
        } else {
            PhaseVerdict(Phase.CONDENSED, "at-or-below-boiling-point:$basis")
        }
    }

    /** The same rule reading a boiling point straight off [PredictedProperties]. */
    fun of(properties: PredictedProperties, temperatureKelvin: Double): PhaseVerdict =
        of(properties.boilingPointC, properties.boilingPointBasis, temperatureKelvin)
}
