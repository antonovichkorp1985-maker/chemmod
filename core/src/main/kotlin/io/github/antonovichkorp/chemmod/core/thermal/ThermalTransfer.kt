package io.github.antonovichkorp.chemmod.core.thermal

import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln

/** Constant total heat capacity, not specific heat. SI units: J/K and K.
 * Callers must supply a justified capacity for a vessel/sample; unknown data
 * must not be replaced by a guessed universal value.
 */
data class ThermalBody(val heatCapacityJoulesPerKelvin: Double, val temperatureKelvin: Double) {
    init {
        require(heatCapacityJoulesPerKelvin.isFinite() && heatCapacityJoulesPerKelvin > 0.0) {
            "Heat capacity must be finite and positive"
        }
        require(temperatureKelvin.isFinite() && temperatureKelvin >= 0.0) {
            "Temperature must be finite and nonnegative in kelvin"
        }
    }
}

data class ContactTransfer(
    val first: ThermalBody,
    val second: ThermalBody,
    /** Signed heat: positive from first to second, negative in the opposite direction. */
    val heatFromFirstToSecondJoules: Double,
)

data class ReservoirTransfer(
    val body: ThermalBody,
    /** Signed heat from the maintained reservoir into the body; negative when the body cools into it. */
    val heatFromReservoirJoules: Double,
)

data class PoweredTransfer(
    val body: ThermalBody,
    val suppliedEnergyJoules: Double,
    /** Signed heat to the ambient reservoir; negative means ambient heating of the body. */
    val heatToAmbientJoules: Double,
) {
    val netEnergyIntoBodyJoules: Double get() = suppliedEnergyJoules - heatToAmbientJoules
}

/**
 * Closed-form primitives for constant-capacity, single-phase, spatially uniform bodies.
 * These are NOT a coupled network solver, combustion model or phase-change model.
 * No temperature clamping/discarded energy, automatic heat ratings, or guessed properties.
 * The energy ledger is floating-point SI, not the chemistry module's exact matter ledger.
 */
object ThermalTransfer {
    /** Exact isolated two-body relaxation for constant thermal conductance (W/K). */
    @JvmStatic
    fun contact(
        first: ThermalBody,
        second: ThermalBody,
        conductanceWattsPerKelvin: Double,
        elapsedSeconds: Double,
    ): ContactTransfer {
        nonnegative(conductanceWattsPerKelvin, "Conductance")
        nonnegative(elapsedSeconds, "Elapsed time")
        if (conductanceWattsPerKelvin == 0.0 || elapsedSeconds == 0.0 ||
            first.temperatureKelvin == second.temperatureKelvin) return ContactTransfer(first, second, 0.0)

        val ca = first.heatCapacityJoulesPerKelvin
        val cb = second.heatCapacityJoulesPerKelvin
        // ca*cb/(ca+cb), without overflowing either the product or sum.
        val reducedCapacity = if (ca <= cb) ca / (1.0 + ca / cb) else cb / (1.0 + cb / ca)
        if (reducedCapacity == 0.0) throw ArithmeticException("Reduced capacity underflow")
        val fraction = -expm1(-relaxation(conductanceWattsPerKelvin, elapsedSeconds, reducedCapacity))
        val difference = first.temperatureKelvin - second.temperatureKelvin
        val heat = finite(difference * (reducedCapacity * fraction), "Contact heat")
        // Convex temperature updates avoid an explicit-Euler overshoot, even after a long pause.
        val nextFirst = first.temperatureKelvin - difference * ((reducedCapacity / ca) * fraction)
        val nextSecond = second.temperatureKelvin + difference * ((reducedCapacity / cb) * fraction)
        return ContactTransfer(
            first.copy(temperatureKelvin = finite(nextFirst, "First temperature")),
            second.copy(temperatureKelvin = finite(nextSecond, "Second temperature")),
            heat,
        )
    }

    /** Solve C*dT/dt = P - G*(T - ambient) with constant P, G and ambient.
     * P is nonnegative heat actually delivered to this body, not an electrical
     * nameplate rating or free energy obtained from a nearby block's temperature.
     */
    @JvmStatic
    fun powered(
        body: ThermalBody,
        deliveredPowerWatts: Double,
        ambientTemperatureKelvin: Double,
        ambientConductanceWattsPerKelvin: Double,
        elapsedSeconds: Double,
    ): PoweredTransfer {
        nonnegative(deliveredPowerWatts, "Delivered power")
        nonnegative(ambientTemperatureKelvin, "Ambient temperature")
        nonnegative(ambientConductanceWattsPerKelvin, "Ambient conductance")
        nonnegative(elapsedSeconds, "Elapsed time")
        if (elapsedSeconds == 0.0) return PoweredTransfer(body, 0.0, 0.0)
        val supplied = finite(deliveredPowerWatts * elapsedSeconds, "Supplied energy")
        val capacity = body.heatCapacityJoulesPerKelvin
        if (ambientConductanceWattsPerKelvin == 0.0) {
            val next = finite(body.temperatureKelvin + supplied / capacity, "Adiabatic temperature")
            return PoweredTransfer(body.copy(temperatureKelvin = next), supplied, 0.0)
        }
        val x = relaxation(ambientConductanceWattsPerKelvin, elapsedSeconds, capacity)
        val fraction = -expm1(-x)
        // 1 - (1-exp(-x))/x. Taylor expansion avoids catastrophic cancellation
        // when a short tick transfers almost all supplied heat into the body.
        val lossFraction = when {
            x < 1.0e-4 -> x * (0.5 - x / 6.0 + x * x / 24.0)
            x.isInfinite() -> 1.0
            else -> 1.0 - fraction / x
        }
        val retainedHeating = if (x < 1.0) (supplied / capacity) * (1.0 - lossFraction)
            else (deliveredPowerWatts / ambientConductanceWattsPerKelvin) * fraction
        val next = finite(body.temperatureKelvin +
            (ambientTemperatureKelvin - body.temperatureKelvin) * fraction + retainedHeating, "Body temperature")
        val ambientHeat = finite((capacity * fraction) * (body.temperatureKelvin - ambientTemperatureKelvin) +
            supplied * lossFraction, "Heat to ambient")
        finite(supplied - ambientHeat, "Net energy")
        return PoweredTransfer(body.copy(temperatureKelvin = next), supplied, ambientHeat)
    }

    /**
     * Exact relaxation of one body against a maintained-temperature reservoir:
     * a flame or heated surface whose own temperature is held by its fuel or
     * power supply. The reservoir is not charged and does not change
     * temperature, so the exchanged heat is accounted to the body alone. The
     * body approaches the reservoir temperature asymptotically and never
     * crosses it, whatever the step length.
     */
    @JvmStatic
    fun reservoir(
        body: ThermalBody,
        reservoirTemperatureKelvin: Double,
        conductanceWattsPerKelvin: Double,
        elapsedSeconds: Double,
    ): ReservoirTransfer {
        nonnegative(reservoirTemperatureKelvin, "Reservoir temperature")
        nonnegative(conductanceWattsPerKelvin, "Conductance")
        nonnegative(elapsedSeconds, "Elapsed time")
        if (conductanceWattsPerKelvin == 0.0 || elapsedSeconds == 0.0 ||
            body.temperatureKelvin == reservoirTemperatureKelvin) return ReservoirTransfer(body, 0.0)
        val difference = body.temperatureKelvin - reservoirTemperatureKelvin
        val decay = exp(-relaxation(conductanceWattsPerKelvin, elapsedSeconds, body.heatCapacityJoulesPerKelvin))
        val next = finite(reservoirTemperatureKelvin + difference * decay, "Body temperature")
        val heat = finite(body.heatCapacityJoulesPerKelvin * (next - body.temperatureKelvin), "Reservoir heat")
        return ReservoirTransfer(body.copy(temperatureKelvin = next), heat)
    }

    private fun nonnegative(value: Double, name: String) {
        require(value.isFinite() && value >= 0.0) { "$name must be finite and nonnegative" }
    }
    private fun finite(value: Double, name: String): Double {
        if (!value.isFinite()) throw ArithmeticException("$name is not representable")
        return value
    }
    private fun relaxation(conductance: Double, seconds: Double, capacity: Double): Double {
        val rate = conductance / capacity
        val x = if (rate > 0.0 && rate.isFinite()) rate * seconds
            else exp(ln(conductance) + ln(seconds) - ln(capacity))
        // Positive infinity is an intentional saturated exponential, not an infinite temperature.
        if (x == 0.0 || x.isNaN()) throw ArithmeticException("Relaxation interval is not representable")
        return x
    }
}
