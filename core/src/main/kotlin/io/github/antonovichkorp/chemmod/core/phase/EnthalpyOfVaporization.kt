package io.github.antonovichkorp.chemmod.core.phase

/**
 * The enthalpy of vaporization plus where it came from, so an estimate never travels as a bare
 * number.
 */
data class VaporizationEstimate(val joulesPerMole: Double, val basis: String)

/**
 * Enthalpy of vaporization from Trouton's rule: the entropy of vaporization at the normal boiling
 * point is close to constant, so `ΔH_vap ≈ ΔS_vap · T_b` and the only input needed is a boiling
 * point the graph already produces.
 *
 * The constant is not one number but two physical regimes, and which regime applies is decided by
 * the molecule, not by a table:
 *
 * - **86.2 J/(mol·K)** for liquids that do not hydrogen-bond to themselves. Mean of five sourced
 *   liquids — n-pentane 85.3, n-hexane 84.5, benzene 87.1, toluene 86.6, nonane 87.7. Broader
 *   compilations quote ~88; this set is the one actually fitted here.
 * - **109.3 J/(mol·K)** for liquids that do. Mean of water 108.9 and ethanol 109.7 — two points,
 *   which is a thin calibration and is reported as such rather than dressed up as a fit.
 *
 * The discriminator is the count of hydrogen-bond **donors**, not acceptors, and that is measured,
 * not assumed: diethyl ether and acetone accept hydrogen bonds and still sit in the non-associated
 * band (86.1 and 88.4), while water, ethanol and HF donate and sit far above it.
 *
 * Known failure: carboxylic acids dimerize in the vapour, which lowers their entropy of
 * vaporization instead of raising it — formic and acetic acid fall to ~61 J/(mol·K). This rule will
 * overestimate them. There is no carboxylic-acid boiling branch yet either, so the gap is
 * consistent, but it must be closed before acids are modelled.
 */
object EnthalpyOfVaporization {
    const val NON_ASSOCIATED_JOULES_PER_MOLE_KELVIN = 86.2
    const val HYDROGEN_BONDED_JOULES_PER_MOLE_KELVIN = 109.3

    fun of(normalBoilingPointKelvin: Double, hydrogenBondDonors: Int): VaporizationEstimate {
        require(!normalBoilingPointKelvin.isNaN()) { "A boiling point must be a number" }
        require(normalBoilingPointKelvin > 0.0) {
            "A boiling point must be positive in kelvin, got $normalBoilingPointKelvin"
        }
        require(hydrogenBondDonors >= 0) { "A donor count cannot be negative" }
        return if (hydrogenBondDonors > 0) {
            VaporizationEstimate(
                HYDROGEN_BONDED_JOULES_PER_MOLE_KELVIN * normalBoilingPointKelvin,
                "trouton:hydrogen-bonded",
            )
        } else {
            VaporizationEstimate(
                NON_ASSOCIATED_JOULES_PER_MOLE_KELVIN * normalBoilingPointKelvin,
                "trouton:non-associated",
            )
        }
    }
}
