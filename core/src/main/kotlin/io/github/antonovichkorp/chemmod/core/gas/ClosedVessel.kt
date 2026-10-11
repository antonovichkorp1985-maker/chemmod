package io.github.antonovichkorp.chemmod.core.gas

/**
 * A rigid, sealed gas volume — a tank, a vial headspace, a closed room. The state is the mole
 * count of each species plus a single uniform temperature; everything else is thermodynamics.
 *
 * Pressure is the ideal-gas law over the total mole count (Dalton: partial pressures add), so
 * filling the volume, heating it, or running a reaction that changes the mole count all move the
 * pressure, and a vessel whose pressure exceeds its wall strength bursts. Combustion is the
 * Hindenburg case: a flammable species with oxygen inside its limits, given an ignition source,
 * converts stoichiometrically to products, releases its heat of combustion into the gas, and the
 * resulting temperature and mole-count change produce the pressure spike that ruptures weak tanks.
 */
class ClosedVessel(val volumeCubicMeters: Double) {
    private val moles = linkedMapOf<String, Double>()
    var temperatureKelvin: Double = 293.15
        private set

    init { require(volumeCubicMeters > 0.0) { "A vessel must have positive volume" } }

    fun addMoles(species: String, amount: Double) {
        require(amount >= 0.0) { "Cannot add a negative amount" }
        moles[species] = (moles[species] ?: 0.0) + amount
    }

    fun molesOf(species: String): Double = moles[species] ?: 0.0
    fun totalMoles(): Double = moles.values.sum()
    fun species(): Set<String> = moles.filterValues { it > 1e-12 }.keys

    /** Total pressure in pascals, P = (Σn)·R·T / V. */
    fun pressurePascals(): Double =
        totalMoles() * GasPhase.UNIVERSAL_GAS_CONSTANT * temperatureKelvin / volumeCubicMeters

    /** Dalton partial pressure of one species. */
    fun partialPressurePascals(species: String): Double =
        molesOf(species) * GasPhase.UNIVERSAL_GAS_CONSTANT * temperatureKelvin / volumeCubicMeters

    /** A wall fails when the internal pressure exceeds what its material and shape carry. */
    fun bursts(wallStrengthPascals: Double): Boolean = pressurePascals() > wallStrengthPascals

    /**
     * Ignite every flammable species that has oxygen within its flammability envelope. Returns the
     * heat released in joules (0 if nothing burns). Combustion is limited by the scarcer of fuel
     * and stoichiometric oxygen, consumes both, adds products, and deposits the heat of combustion
     * into the gas, raising its temperature.
     */
    fun ignite(): Double {
        var released = 0.0
        for ((fuel, data) in SPECIES) {
            val combustion = data.combustion ?: continue
            val fuelMoles = molesOf(fuel)
            val oxygen = molesOf("O2")
            if (fuelMoles <= 0.0 || oxygen <= 0.0) continue
            if (!withinFlammabilityEnvelope(fuel)) continue

            val byOxygen = oxygen / combustion.oxygenDemand
            val burned = minOf(fuelMoles, byOxygen)
            if (burned <= 0.0) continue

            moles[fuel] = fuelMoles - burned
            moles["O2"] = oxygen - burned * combustion.oxygenDemand
            for ((product, perFuel) in combustion.products) {
                moles[product] = molesOf(product) + burned * perFuel
            }
            released += burned * combustion.heatOfCombustionJoulesPerMole
        }
        if (released > 0.0) heat(released)
        return released
    }

    /** Volume-percent of a species in the gas, for the flammability envelope check. */
    private fun volumePercent(species: String): Double {
        val total = totalMoles()
        return if (total <= 0.0) 0.0 else 100.0 * molesOf(species) / total
    }

    private fun withinFlammabilityEnvelope(fuel: String): Boolean {
        val limits = GasPhase.flammabilityLimits(SPECIES.getValue(fuel).formula) ?: return false
        val fuelPct = volumePercent(fuel)
        return fuelPct >= limits.lowerVolumePercent && fuelPct <= limits.upperVolumePercent
    }

    /** Raise the gas temperature by deposited energy, using the mixture heat capacity. */
    fun heat(energyJoules: Double) {
        val heatCapacity = moles.entries.sumOf { (s, n) -> n * (SPECIES[s]?.cvOverR ?: 2.5) } *
            GasPhase.UNIVERSAL_GAS_CONSTANT
        if (heatCapacity <= 0.0) return
        temperatureKelvin += energyJoules / heatCapacity
    }

    /** Tint for rendering, or null for a colourless gas. */
    fun tintArgb(): Int? = species().firstNotNullOfOrNull { SPECIES[it]?.tintArgb }
}

/** Fixed physical data per species: formula for the flammability rule, heat capacity, combustion. */
data class GasSpeciesData(
    val formula: Map<String, Int>,
    val cvOverR: Double,
    val tintArgb: Int? = null,
    val combustion: Combustion? = null,
)

data class Combustion(
    val oxygenDemand: Double,
    val heatOfCombustionJoulesPerMole: Double,
    val products: Map<String, Double>,
)

private val SPECIES = mapOf(
    "H2" to GasSpeciesData(mapOf("H" to 2), 2.5,
        combustion = Combustion(0.5, 241_800.0, mapOf("H2O" to 1.0))),
    "CH4" to GasSpeciesData(mapOf("C" to 1, "H" to 4), 3.0,
        combustion = Combustion(2.0, 802_000.0, mapOf("CO2" to 1.0, "H2O" to 2.0))),
    "CO" to GasSpeciesData(mapOf("C" to 1, "O" to 1), 2.5,
        combustion = Combustion(0.5, 283_000.0, mapOf("CO2" to 1.0))),
    "O2" to GasSpeciesData(mapOf("O" to 2), 2.5),
    "N2" to GasSpeciesData(mapOf("N" to 2), 2.5),
    "H2O" to GasSpeciesData(mapOf("H" to 2, "O" to 1), 3.0),
    "CO2" to GasSpeciesData(mapOf("C" to 1, "O" to 2), 3.5),
    "Cl2" to GasSpeciesData(mapOf("Cl" to 2), 2.5, tintArgb = 0xFF_5E_8C_2A.toInt()),
    "NO2" to GasSpeciesData(mapOf("N" to 1, "O" to 2), 3.5, tintArgb = 0xFF_8C_4A_1E.toInt()),
)
