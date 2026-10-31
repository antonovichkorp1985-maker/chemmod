package io.github.antonovichkorp.chemmod.core.electrical

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Stage E2: Power grid equipment — switches, breakers, multi-tap transformers,
 * wire insulation breakdown, arc flash, zero redstone.
 * All computations in SI units. No substance lookup tables.
 */

/** Switch state for manual/automatic disconnection. */
enum class SwitchState {
    CLOSED,    // Conducting, negligible resistance
    OPEN,      // Non-conducting, infinite resistance
    WELDED     // Failed closed due to arc welding
}

/** Simple on/off switch with optional voltage/current ratings. */
data class SwitchBranch(
    override val fromNode: Int,
    override val toNode: Int,
    val ratedVoltageVolts: Double,
    val ratedCurrentAmps: Double,
    var state: SwitchState = SwitchState.CLOSED,
) : Branch() {
    init {
        require(ratedVoltageVolts > 0.0) { "Rated voltage must be positive" }
        require(ratedCurrentAmps > 0.0) { "Rated current must be positive" }
    }

    /** Conductance when closed (very low resistance). */
    val closedConductanceSiemens = 1e6

    /** Whether the switch can safely interrupt current at given voltage. */
    fun canInterrupt(currentAmps: Double, voltageVolts: Double): Boolean =
        currentAmps <= ratedCurrentAmps * 1.5 && voltageVolts <= ratedVoltageVolts * 1.2

    /** Force open (manual or trip). Returns true if state changed. */
    fun open(): Boolean {
        if (state == SwitchState.OPEN) return false
        state = SwitchState.OPEN
        return true
    }

    /** Force close (manual reset). Returns true if state changed. */
    fun close(): Boolean {
        if (state == SwitchState.CLOSED) return false
        state = SwitchState.CLOSED
        return true
    }
}