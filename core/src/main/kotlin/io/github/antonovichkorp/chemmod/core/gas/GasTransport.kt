package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Binary diffusion coefficients from Chapman–Enskog kinetic theory.
 *
 * The only per-species inputs are the Lennard-Jones size σ and well depth ε/k, tabulated once
 * (Bird, Stewart & Lightfoot, *Transport Phenomena*, Table E.1); everything else — the
 * temperature dependence, the reduced collision integral, the mixing rules — is theory, so a
 * pair that was never measured still gets a coefficient at any temperature.
 */
object GasTransport {
    /** Lennard-Jones parameters, σ in Å, ε/k in K (Bird et al. Table E.1). */
    private val LENNARD_JONES = mapOf(
        "He" to (2.551 to 10.22),
        "H2" to (2.827 to 59.7),
        "N2" to (3.798 to 71.4),
        "O2" to (3.467 to 106.7),
        "Ar" to (3.542 to 93.3),
        "CO" to (3.690 to 91.7),
        "CO2" to (3.941 to 195.2),
        "CH4" to (3.758 to 148.6),
        "H2O" to (2.641 to 809.1),
        "C2H6" to (4.443 to 215.7),
        "SF6" to (5.128 to 329.6),
    )

    /**
     * Binary diffusion coefficient D_AB in m²/s at temperature T and pressure P.
     * `molarMass` supplies M_A and M_B in g/mol. Returns null if either species has no
     * Lennard-Jones parameters, because a gap in the table must not read as a small coefficient.
     */
    fun binaryDiffusionSquareMetersPerSecond(
        a: String,
        b: String,
        molarMassA: Double,
        molarMassB: Double,
        temperatureKelvin: Double,
        pressurePascals: Double = 101_325.0,
    ): Double? {
        val la = LENNARD_JONES[a] ?: return null
        val lb = LENNARD_JONES[b] ?: return null
        require(temperatureKelvin > 0.0 && pressurePascals > 0.0) { "T and P must be positive" }

        val sigma = (la.first + lb.first) / 2.0                      // Å
        val epsilonOverK = sqrt(la.second * lb.second)              // K
        val reducedT = temperatureKelvin / epsilonOverK
        val omega = collisionIntegral(reducedT)
        val pressureAtm = pressurePascals / 101_325.0

        // Bird et al. eq. 17.2-1: D in cm²/s with T in K, P in atm, σ in Å.
        val dCm2PerS = 0.0018583 *
            sqrt(temperatureKelvin.pow(3) * (1.0 / molarMassA + 1.0 / molarMassB)) /
            (pressureAtm * sigma * sigma * omega)
        return dCm2PerS * 1.0e-4
    }

    /** Neufeld, Janzen & Aziz (1972) fit of the reduced collision integral Ω_D. */
    private fun collisionIntegral(reducedT: Double): Double =
        1.06036 / reducedT.pow(0.15610) +
            0.19300 * exp(-0.47635 * reducedT) +
            1.03587 * exp(-1.52996 * reducedT) +
            1.76474 * exp(-3.89411 * reducedT)
}
