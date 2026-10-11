package io.github.antonovichkorp.chemmod.core.gas

/**
 * Bulk physics of a dilute gas phase, derived from the molecular formula alone.
 *
 * Nothing here is tabulated per substance. Density and buoyancy come from the ideal-gas
 * law over the molar mass the formula already carries, and the flammability limits come
 * from the stoichiometry of complete combustion through Jones' 1938 correlations, so a
 * fuel that was never measured still gets a limit and a measured one acts as a check.
 */
object GasPhase {
    const val UNIVERSAL_GAS_CONSTANT = 8.314462618
    /** Dry air near sea level, g/mol. */
    const val AIR_MOLAR_MASS_GRAMS_PER_MOLE = 28.97
    /** Moles of air per mole of O2 (1/0.2095). */
    private const val AIR_PER_OXYGEN = 4.773

    private val HALOGENS = setOf("F", "Cl", "Br", "I")

    /** Ideal-gas mass density in kg/m^3 at the given temperature and pressure. */
    fun densityKilogramsPerCubicMeter(
        molarMassGramsPerMole: Double,
        temperatureKelvin: Double,
        pressurePascals: Double = 101_325.0,
    ): Double {
        require(temperatureKelvin > 0.0) { "Gas temperature must be positive" }
        require(pressurePascals > 0.0) { "Gas pressure must be positive" }
        return pressurePascals * (molarMassGramsPerMole / 1000.0) /
            (UNIVERSAL_GAS_CONSTANT * temperatureKelvin)
    }

    /**
     * Density relative to air at the same T and P: >1 sinks, <1 rises. The temperature and
     * pressure cancel, so this is a property of the molecule alone.
     */
    fun buoyancyRelativeToAir(molarMassGramsPerMole: Double): Double =
        molarMassGramsPerMole / AIR_MOLAR_MASS_GRAMS_PER_MOLE

    /**
     * Jones (1938) flammability limits in volume percent, computed from the stoichiometric
     * concentration of the fuel in air.
     *
     * The stoichiometric oxygen demand is read off complete combustion
     * (C→CO2, H→H2O, S→SO2, existing O subtracts), `C_st = 100 / (1 + 4.773·n_O2)`, and
     * LFL ≈ 0.55·C_st, UFL ≈ 3.5·C_st. The upper limit is the weaker half of the correlation
     * (benzene predicts 9.5 against a measured 7.8); callers that need a safety envelope
     * should treat UFL as indicative.
     *
     * Returns null when the rule does not apply: no positive oxygen demand (CO2, water, CCl4
     * as a formula would still demand O2, so halogens are excluded explicitly) or any halogen,
     * whose C-X bonds quench the chain chemistry the correlation assumes.
     */
    fun flammabilityLimits(formula: Map<String, Int>): FlammabilityLimits? {
        if (formula.keys.any { it in HALOGENS }) return null
        val oxygenDemand = (formula["C"] ?: 0) +
            (formula["H"] ?: 0) / 4.0 +
            (formula["S"] ?: 0) / 2.0 -
            (formula["O"] ?: 0) / 2.0
        if (oxygenDemand <= 0.0) return null
        val stoichiometric = 100.0 / (1.0 + AIR_PER_OXYGEN * oxygenDemand)
        return FlammabilityLimits(
            stoichiometricVolumePercent = stoichiometric,
            lowerVolumePercent = 0.55 * stoichiometric,
            upperVolumePercent = 3.5 * stoichiometric,
        )
    }
    /**
     * Le Chatelier mixing rule for the lower limit of a fuel blend, volume percent.
     * `fractions` are the fuel components' volume fractions (summing to 1) and `lowerLimits`
     * their individual LFLs; `1/LFL_mix = Σ x_i / LFL_i`. The blend is more flammable than its
     * least flammable member, which is exactly why a mixture rule must not be a minimum.
     */
    fun mixtureLowerVolumePercent(fractions: List<Double>, lowerLimits: List<Double>): Double {
        require(fractions.size == lowerLimits.size) { "fractions and limits must align" }
        require(lowerLimits.all { it > 0.0 }) { "A component LFL must be positive" }
        val total = fractions.sum()
        require(total > 0.0) { "A mixture needs at least one fuel" }
        return 1.0 / fractions.zip(lowerLimits).sumOf { (x, lfl) -> x / lfl / total }
    }

    /**
     * Limiting oxygen concentration, volume percent: below this O2 fraction no mixture of the
     * fuel with air burns. Jones' estimate LFL × n_O2, where n_O2 is the stoichiometric oxygen
     * demand per mole fuel. Indicative — it inherits the LFL correlation's error.
     */
    fun limitingOxygenConcentration(formula: Map<String, Int>): Double? {
        val limits = flammabilityLimits(formula) ?: return null
        val demand = (formula["C"] ?: 0) + (formula["H"] ?: 0) / 4.0 +
            (formula["S"] ?: 0) / 2.0 - (formula["O"] ?: 0) / 2.0
        return limits.lowerVolumePercent * demand
    }
}

data class FlammabilityLimits(
    val stoichiometricVolumePercent: Double,
    val lowerVolumePercent: Double,
    val upperVolumePercent: Double,
)
