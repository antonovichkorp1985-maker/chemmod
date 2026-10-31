package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.roundToInt

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
class ClosedVessel(val volumeCubicMeters: Double, val wallStrengthPascals: Double) {
    private val moles = linkedMapOf<String, Double>()
    var temperatureKelvin: Double = 293.15
        private set

    init {
        require(volumeCubicMeters > 0.0) { "A vessel must have positive volume" }
        require(wallStrengthPascals > 0.0) { "Wall strength must be positive" }
    }

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

    /** Release gas when pressure exceeds a threshold in pascals; returns moles released or 0. */
    fun releaseAtPressure(maxPressurePascals: Double): Double {
        val current = pressurePascals()
        if (current <= maxPressurePascals) return 0.0
        // Ideal gas: P * V = n * R * T, so n at max pressure = P_max * V / (R * T)
        val targetMoles = maxPressurePascals * volumeCubicMeters / (GasPhase.UNIVERSAL_GAS_CONSTANT * temperatureKelvin)
        val releaseAmount = totalMoles() - targetMoles
        if (releaseAmount <= 0.0) return 0.0
        // Proportional release from all species
        val factor = releaseAmount / totalMoles()
        moles.keys.toList().forEach { species ->
            val currentAmount = moles[species] ?: 0.0
            val newAmount = currentAmount * (1.0 - factor)
            moles[species] = if (newAmount < 1e-12) { moles.remove(species); 0.0 } else newAmount
        }
        return releaseAmount
    }

    /** Manual vent: remove the given moles proportionally from all species; returns moles actually removed. */
    fun ventMoles(requestedMoles: Double): Double {
        require(requestedMoles >= 0.0) { "Cannot vent negative moles" }
        val available = totalMoles()
        if (available <= 0.0) return 0.0
        val actual = minOf(requestedMoles, available)
        val factor = actual / available
        moles.keys.toList().forEach { species ->
            val currentAmount = moles[species] ?: 0.0
            val newAmount = currentAmount * (1.0 - factor)
            moles[species] = if (newAmount < 1e-12) { moles.remove(species); 0.0 } else newAmount
        }
        return actual
    }

    /** Empty the vessel completely; returns the total moles removed. */
    fun empty(): Double = ventMoles(totalMoles())

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
    fun tintArgb(): Int? = GasAppearance.tintArgb(species().associateWith { molesOf(it) })
}

/**
 * How a gas looks, derived from the same per-species table the vessel's physics uses, so the
 * colour a player sees and the substance that is actually there cannot drift apart.
 *
 * The tint of a mixture is weighted by mole fraction, and the alpha carries how much of the volume
 * is coloured at all: a trace of chlorine in air is a faint green, not the same green at full
 * strength. A gas with no recorded tint contributes nothing, and if nothing in the volume is
 * coloured the answer is null rather than a placeholder hue - colourless is a real answer.
 */
object GasAppearance {
    /** Tint of a single species, or null when that gas is colourless. */
    fun tintArgb(species: String): Int? = SPECIES[species]?.tintArgb

    /** Tint of a volume given its contents in moles, or null when nothing in it is coloured. */
    fun tintArgb(molesBySpecies: Map<String, Double>): Int? {
        val total = molesBySpecies.values.sum()
        if (total <= 0.0) return null
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var colouredFraction = 0.0
        for ((species, amount) in molesBySpecies) {
            val tint = SPECIES[species]?.tintArgb ?: continue
            val weight = amount / total
            colouredFraction += weight
            red += ((tint shr 16) and 0xFF) * weight
            green += ((tint shr 8) and 0xFF) * weight
            blue += (tint and 0xFF) * weight
        }
        if (colouredFraction <= 0.0) return null
        // The hue is the average over the coloured part only, so diluting chlorine with air keeps it
        // green and makes it faint; dividing by the whole volume instead would darken it, which is
        // not what a dilute gas looks like.
        val alpha = (255.0 * colouredFraction.coerceAtMost(1.0)).toInt().coerceIn(1, 255)
        return (alpha shl 24) or
            ((red / colouredFraction).roundToInt().coerceIn(0, 255) shl 16) or
            ((green / colouredFraction).roundToInt().coerceIn(0, 255) shl 8) or
            (blue / colouredFraction).roundToInt().coerceIn(0, 255)
    }
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
