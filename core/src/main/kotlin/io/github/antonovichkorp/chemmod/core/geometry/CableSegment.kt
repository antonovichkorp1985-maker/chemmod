package io.github.antonovichkorp.chemmod.core.geometry

import kotlin.math.max

/**
 * One cable segment between two terminals, in exactly one of three physical states.
 *
 * No redstone: a cable carries current by ohmic conduction, dissipates I^2*R heat,
 * melts insulation above the material breakdown temperature, and shorts when breached
 * in water or on earth ground.
 */
enum class CableState {
    /** Bare conductor, 1x1 px core. Contacts, shorts, arcs. */
    BARE,
    /** Core 1x1 px + insulation shell 3x3 px (polymer/rubber). Safe to touch within limits. */
    INSULATED,
    /** Insulation melted or ruptured by Joule heat or overvoltage; segment behaves as bare. */
    BREACHED,
    /** Conductor melted open by Joule heat; infinite resistance, no current. */
    OPEN_MELTED,
}

/** Physical state of a cable segment, including measured current and temperature. */
data class CableSegment(
    val id: String,
    val lengthMetres: Double,
    val resistanceOhmsPerMetre: Double,
    val state: CableState,
    val insulationBreakdownTemperatureKelvin: Double,
    val breakdownVoltageVolts: Double,
    /** Conductor melting temperature in K (Cu=1357.77, Al=933.47, Pb=600.61, Fe=1811.0, etc.). Null = NO_MODEL. */
    val conductorMeltingTemperatureKelvin: Double? = null,
    /** Ambient temperature in K for this segment's environment. */
    val ambientTemperatureKelvin: Double,
    /** Water/earth contact flag: true if segment is submerged or grounded. */
    val wetOrGrounded: Boolean = false,
    /** Water type for ground-fault conductance: 0=none, 1=fresh, 2=brackish, 3=sea/electrolyte. */
    val waterConductivityClass: Int = 0,
) {
    init {
        require(lengthMetres > 0.0) { "Cable length must be positive" }
        require(resistanceOhmsPerMetre > 0.0) { "Resistance per metre must be positive" }
        require(insulationBreakdownTemperatureKelvin > 0.0) { "Breakdown temperature must be positive" }
        require(breakdownVoltageVolts > 0.0) { "Breakdown voltage must be positive" }
        require(ambientTemperatureKelvin > 0.0) { "Ambient temperature must be positive" }
        conductorMeltingTemperatureKelvin?.let { require(it > 0.0) { "Melting temperature must be positive" } }
        require(waterConductivityClass in 0..3) { "Water conductivity class must be 0..3" }
    }

    /** Total resistance of the segment. */
    fun totalResistance(): Double = lengthMetres * resistanceOhmsPerMetre

    /** Joule heat power at current I (amps): P = I^2 * R (watts). */
    fun jouleHeatWatts(currentAmps: Double): Double = currentAmps * currentAmps * totalResistance()

    /** Equilibrium temperature rise above ambient for given current (simple thermal model). */
    fun temperatureKelvin(currentAmps: Double): Double {
        val heat = jouleHeatWatts(currentAmps)
        // Simplified: ΔT = P / (h*A) with h=10 W/m²K, A = surface area per metre
        val surfaceAreaMetres2 = lengthMetres * 0.01 // 1 cm wide cable
        val deltaT = heat / (10.0 * surfaceAreaMetres2)
        return ambientTemperatureKelvin + deltaT
    }

    /** Whether insulation has failed due to temperature. */
    fun insulationFailed(currentAmps: Double): Boolean =
        state != CableState.BARE && temperatureKelvin(currentAmps) >= insulationBreakdownTemperatureKelvin

    /** Whether voltage exceeds insulation breakdown. */
    fun overVoltage(voltageVolts: Double): Boolean = voltageVolts > breakdownVoltageVolts

    /** Whether conductor has melted open (fuse action). */
    fun conductorMelted(currentAmps: Double): Boolean =
        conductorMeltingTemperatureKelvin != null && temperatureKelvin(currentAmps) >= conductorMeltingTemperatureKelvin!!

    /** Ground-fault conductance to earth (Siemens) for water/ground contact. */
    fun groundFaultConductance(): Double = when {
        state == CableState.BARE || state == CableState.BREACHED -> when (waterConductivityClass) {
            3 -> 1.0   // seawater / electrolyte ~1 S
            2 -> 0.1   // brackish ~0.1 S
            1 -> 0.01  // fresh water ~0.01 S
            else -> 0.0
        }
        else -> 0.0
    }

    /** Effective state after applying current/voltage stress. */
    fun effectiveState(currentAmps: Double, voltageVolts: Double): CableState {
        if (state == CableState.BARE) return CableState.BARE
        if (state == CableState.OPEN_MELTED) return CableState.OPEN_MELTED
        if (conductorMelted(currentAmps)) return CableState.OPEN_MELTED
        if (insulationFailed(currentAmps) || overVoltage(voltageVolts)) return CableState.BREACHED
        return state
    }
}

/** Terminal/endpoint where cables connect. */
data class CableTerminal(
    val id: String,
    /** Maximum voltage this terminal can withstand before flashover. */
    val maxVoltageVolts: Double,
    /** Whether this terminal is an earth ground connection. */
    val isGround: Boolean = false,
)