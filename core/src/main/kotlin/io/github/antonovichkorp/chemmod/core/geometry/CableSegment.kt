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
}

/** Physical state of a cable segment, including measured current and temperature. */
data class CableSegment(
    val id: String,
    val lengthMetres: Double,
    val resistanceOhmsPerMetre: Double,
    val state: CableState,
    val insulationBreakdownTemperatureKelvin: Double,
    val breakdownVoltageVolts: Double,
    /** Ambient temperature in K for this segment's environment. */
    val ambientTemperatureKelvin: Double,
    /** Water/earth contact flag: true if segment is submerged or grounded. */
    val wetOrGrounded: Boolean = false,
) {
    init {
        require(lengthMetres > 0.0) { "Cable length must be positive" }
        require(resistanceOhmsPerMetre > 0.0) { "Resistance per metre must be positive" }
        require(insulationBreakdownTemperatureKelvin > 0.0) { "Breakdown temperature must be positive" }
        require(breakdownVoltageVolts > 0.0) { "Breakdown voltage must be positive" }
        require(ambientTemperatureKelvin > 0.0) { "Ambient temperature must be positive" }
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

    /** Whether this segment shorts (bare or breached, in water/ground). */
    fun shortCircuit(): Boolean = (state == CableState.BARE || state == CableState.BREACHED) && wetOrGrounded

    /** Effective state after applying current/voltage stress. */
    fun effectiveState(currentAmps: Double, voltageVolts: Double): CableState {
        if (state == CableState.BARE) return CableState.BARE
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