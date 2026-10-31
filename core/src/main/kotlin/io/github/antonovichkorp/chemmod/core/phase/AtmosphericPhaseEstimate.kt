package io.github.antonovichkorp.chemmod.core.phase

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.gas.Atmosphere

/** A graph-derived gas/condensed estimate in a named atmospheric model at geometric altitude. */
data class AtmosphericPhaseEstimate(
    val verdict: PhaseVerdict,
    val temperatureKelvin: Double,
    val pressurePascals: Double,
    val atmosphereDescription: String,
)

/**
 * Combines a molecule's predicted boiling point with the local T/P from an atmospheric model.
 *
 * This deliberately reports only gas versus condensed: ChemMod has no melting-point model, so it
 * cannot honestly distinguish liquid from solid. Mixture phase equilibrium is also outside this
 * single-molecule estimate and must be handled by a mixture model, not by classifying each part as
 * if it were pure.
 */
object AtmosphericPhaseEstimateModel {
    fun at(
        molecule: Molecule,
        atmosphere: Atmosphere,
        geometricAltitudeMetres: Double,
        atmosphereDescription: String,
    ): AtmosphericPhaseEstimate {
        require(atmosphereDescription.isNotBlank()) { "Atmosphere description cannot be blank" }
        val temperature = atmosphere.temperatureKelvin(geometricAltitudeMetres)
        val pressure = atmosphere.pressurePascals(geometricAltitudeMetres)
        return AtmosphericPhaseEstimate(
            PhaseAtPressure.of(molecule.properties(), temperature, pressure),
            temperature,
            pressure,
            atmosphereDescription,
        )
    }
}
