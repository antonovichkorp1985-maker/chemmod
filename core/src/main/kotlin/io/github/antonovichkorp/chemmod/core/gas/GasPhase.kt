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
}

data class FlammabilityLimits(
    val stoichiometricVolumePercent: Double,
    val lowerVolumePercent: Double,
    val upperVolumePercent: Double,
)
