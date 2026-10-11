package io.github.antonovichkorp.chemmod.core.gas

/**
 * One vertical column of an enclosed gas volume — the smallest model of a sealed room or a
 * tank. Index 0 is the bottom layer. It carries a single trace gas's volume fraction per layer
 * and advances it by three mechanisms the plan names for the field: Fick diffusion between
 * neighbours, buoyant settling (heavier-than-air gas sinks, lighter rises), and ventilation
 * that exchanges the volume with the outside.
 *
 * Each mechanism is conservative or explicitly removing, so a closed column conserves the trace
 * gas exactly and a vented one loses it monotonically — both properties are asserted in tests.
 */
class GasColumn(val densityRatio: Double, val layers: Int) {
    val fraction = DoubleArray(layers)

    fun fill(volumeFraction: Double) { fraction.fill(volumeFraction) }
    fun total(): Double = fraction.sum()

    fun advance(diffusion: Double, settling: Double, ventilation: Double) {
        require(diffusion in 0.0..0.5) { "Explicit diffusion is stable up to 0.5" }
        require(settling in 0.0..0.5) { "Settling moves at most half a layer per step" }
        require(ventilation in 0.0..1.0) { "Ventilation exchanges at most the whole volume" }

        // Fick diffusion between adjacent layers, explicit scheme.
        for (i in 0 until layers - 1) {
            val flux = diffusion * (fraction[i + 1] - fraction[i])
            fraction[i] += flux
            fraction[i + 1] -= flux
        }

        // Buoyant settling by one layer per step toward the side the gas falls to.
        val dir = when {
            densityRatio > 1.0 -> -1
            densityRatio < 1.0 -> 1
            else -> 0
        }
        if (dir != 0) {
            val moved = DoubleArray(layers)
            for (i in 0 until layers) {
                val j = i + dir
                if (j in 0 until layers) {
                    val m = settling * fraction[i]
                    moved[i] -= m
                    moved[j] += m
                }
            }
            for (i in 0 until layers) fraction[i] += moved[i]
        }

        // Ventilation exchanges the volume with fresh air.
        if (ventilation > 0.0) {
            for (i in 0 until layers) fraction[i] *= 1.0 - ventilation
        }
    }
}
