package io.github.antonovichkorp.chemmod.core.gas

/**
 * Reference atmosphere for interpreting trace gases in a cell. The field stores released or
 * produced species sparsely; it does not store an identical background-air inventory in every
 * occupied cell. This view supplies dry standard air as an ideal-gas starting inventory, then
 * combines it with the actual field contents. It is a concentration/readout model, not an air-flow
 * solver and not a model of humidity or local weather.
 */
object GasAtmosphere {
    const val STANDARD_PRESSURE_PASCALS = 101_325.0
    const val UNIVERSAL_GAS_CONSTANT = 8.314462618

    /** Dry-air mole fractions at standard composition; values sum to exactly one. */
    val DRY_AIR_MOLE_FRACTIONS: Map<String, Double> = linkedMapOf(
        "N2" to 0.7808,
        "O2" to 0.2095,
        "Ar" to 0.0093,
        "CO2" to 0.0004,
    )

    init {
        require(kotlin.math.abs(DRY_AIR_MOLE_FRACTIONS.values.sum() - 1.0) < 1e-12)
    }

    /** Ideal-gas moles in a cell initially filled with air at the stated conditions. */
    @JvmStatic
    fun ambientAirMoles(
        volumeCubicMeters: Double,
        temperatureKelvin: Double,
        pressurePascals: Double = STANDARD_PRESSURE_PASCALS,
    ): Double {
        require(volumeCubicMeters.isFinite() && volumeCubicMeters > 0.0) { "Cell volume must be finite and positive" }
        require(temperatureKelvin.isFinite() && temperatureKelvin > 0.0) { "Temperature must be finite and positive" }
        require(pressurePascals.isFinite() && pressurePascals > 0.0) { "Pressure must be finite and positive" }
        return pressurePascals * volumeCubicMeters / (UNIVERSAL_GAS_CONSTANT * temperatureKelvin)
    }

    /**
     * Mole fractions of dry ambient air plus the field's actual species. This assumes a fixed-volume
     * cell initially at ambient pressure; injected species add to that finite air inventory. Callers
     * must not interpret high-load results as a pressure-regulated room or a validated toxic dose.
     */
    @JvmStatic
    fun moleFractions(
        fieldMolesByFormula: Map<String, Double>,
        volumeCubicMeters: Double,
        temperatureKelvin: Double,
        pressurePascals: Double = STANDARD_PRESSURE_PASCALS,
    ): Map<String, Double> {
        require(fieldMolesByFormula.keys.all { it.isNotBlank() }) { "Gas formula cannot be blank" }
        require(fieldMolesByFormula.values.all { it.isFinite() && it >= 0.0 }) {
            "Field gas amounts must be finite and non-negative"
        }
        val airMoles = ambientAirMoles(volumeCubicMeters, temperatureKelvin, pressurePascals)
        val amounts = linkedMapOf<String, Double>()
        DRY_AIR_MOLE_FRACTIONS.forEach { (formula, fraction) -> amounts[formula] = fraction * airMoles }
        fieldMolesByFormula.forEach { (formula, amount) -> amounts.merge(formula, amount, Double::plus) }
        val total = amounts.values.sum()
        check(total.isFinite() && total > 0.0) { "A reference atmosphere must contain positive finite moles" }
        return amounts.mapValues { (_, amount) -> amount / total }
    }

    @JvmStatic
    fun partsPerMillion(
        formula: String,
        fieldMolesByFormula: Map<String, Double>,
        volumeCubicMeters: Double,
        temperatureKelvin: Double,
        pressurePascals: Double = STANDARD_PRESSURE_PASCALS,
    ): Double = moleFractions(fieldMolesByFormula, volumeCubicMeters, temperatureKelvin, pressurePascals)
        .getOrDefault(formula, 0.0) * 1_000_000.0
}
