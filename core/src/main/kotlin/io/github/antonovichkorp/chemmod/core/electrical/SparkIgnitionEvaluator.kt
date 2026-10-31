package io.github.antonovichkorp.chemmod.core.electrical

import io.github.antonovichkorp.chemmod.core.gas.GasMolecularAmount
import io.github.antonovichkorp.chemmod.core.gas.GasPhase

/**
 * Evaluates whether an electrical spark can ignite a combustible gas mixture.
 *
 * No redstone: ignition is purely physical — spark energy vs minimum ignition energy,
 * fuel and oxidizer within real flammability limits.
 */
object SparkIgnitionEvaluator {

    /** Result of a spark ignition evaluation. */
    data class IgnitionResult(
        /** Whether the spark ignites the gas mixture. */
        val ignites: Boolean,
        /** Spark energy in joules. */
        val sparkEnergyJoules: Double,
        /** Minimum ignition energy of the mixture in joules, or null if unknown. */
        val minIgnitionEnergyJoules: Double?,
        /** Reason for the result. */
        val reason: String,
    )

    /** Minimum ignition energy for common fuels at stoichiometric in air, J (indicative). */
    private val MIN_IGNITION_ENERGY = mapOf(
        "H2" to 0.017,   // hydrogen, very easy to ignite
        "CH4" to 0.28,   // methane
        "CO" to 0.35,    // carbon monoxide
        "C2H6" to 0.26,  // ethane
        "C3H8" to 0.25,  // propane
    )

    /** Look up minimum ignition energy for a fuel species. Returns null if unmodelled (NO_MODEL). */
    fun minIgnitionEnergyJoules(species: String): Double? = MIN_IGNITION_ENERGY[species]

    /**
     * Evaluate whether a spark of given energy at a gas cell ignites the mixture.
     *
     * @param sparkEnergyJoules energy of the spark (J)
     * @param cellContents gas contents at the cell (moles by species)
     * @return IgnitionResult with ignites flag and reason
     */
    fun evaluate(
        sparkEnergyJoules: Double,
        cellContents: List<GasMolecularAmount>,
    ): IgnitionResult {
        require(sparkEnergyJoules >= 0.0) { "Spark energy must be non-negative" }

        val totalMoles = cellContents.sumOf { it.moles }
        if (totalMoles <= 0.0) {
            return IgnitionResult(false, sparkEnergyJoules, null, "no gas present")
        }

        // Check for fuel + oxidizer (O2) present
        val fuelAmounts = cellContents.filter { it.moles > 0.0 && MIN_IGNITION_ENERGY.containsKey(it.molecule.formula()) }
        val oxygenMoles = cellContents
            .filter { it.molecule.formula() == "O2" }
            .sumOf { it.moles }

        if (oxygenMoles <= 0.0) {
            return IgnitionResult(false, sparkEnergyJoules, null, "no oxidizer (O2) present")
        }
        if (fuelAmounts.isEmpty()) {
            return IgnitionResult(false, sparkEnergyJoules, null, "no combustible fuel present")
        }

        // For each fuel, check if it's within flammability limits and spark >= MIE
        for (fuel in fuelAmounts) {
            val formula = fuel.molecule.formula()
            val mie = MIN_IGNITION_ENERGY[formula] ?: continue
            val fuelFraction = fuel.moles / totalMoles
            val oxygenFraction = oxygenMoles / totalMoles

            val limits = GasPhase.flammabilityLimits(parseFormula(formula))
            if (limits == null) continue

            val fuelPct = fuelFraction * 100.0
            if (fuelPct >= limits.lowerVolumePercent && fuelPct <= limits.upperVolumePercent) {
                if (sparkEnergyJoules >= mie) {
                    return IgnitionResult(true, sparkEnergyJoules, mie,
                        "spark $sparkEnergyJoules J >= MIE $mie J ignites $formula at ${fuelPct}% in air")
                } else {
                    return IgnitionResult(false, sparkEnergyJoules, mie,
                        "spark $sparkEnergyJoules J < MIE $mie J for $formula")
                }
            }
        }

        return IgnitionResult(false, sparkEnergyJoules, null,
            "no fuel within flammability limits")
    }

    /** Spark energy from a short-circuit discharge: E = 0.5 * C * V^2, or I^2 * R * t. */
    fun sparkEnergyFromShortCircuit(voltageVolts: Double, currentAmps: Double, durationSeconds: Double): Double {
        require(voltageVolts >= 0.0 && currentAmps >= 0.0 && durationSeconds >= 0.0) { "values must be non-negative" }
        return voltageVolts * currentAmps * durationSeconds
    }

    /** Spark energy from capacitor discharge. */
    fun sparkEnergyFromCapacitor(capacitanceFarads: Double, voltageVolts: Double): Double {
        require(capacitanceFarads > 0.0 && voltageVolts >= 0.0) { "values must be positive/non-negative" }
        return 0.5 * capacitanceFarads * voltageVolts * voltageVolts
    }

    private fun parseFormula(formula: String): Map<String, Int> {
        // Simple formula parser: e.g., "H2", "CH4", "CO", "O2"
        val counts = mutableMapOf<String, Int>()
        var i = 0
        while (i < formula.length) {
            val element = formula.substring(i, i + 1)
            i++
            var numStr = ""
            while (i < formula.length && Character.isDigit(formula[i])) {
                numStr += formula[i]
                i++
            }
            val count = if (numStr.isEmpty()) 1 else numStr.toInt()
            counts[element] = (counts[element] ?: 0) + count
        }
        return counts
    }
}