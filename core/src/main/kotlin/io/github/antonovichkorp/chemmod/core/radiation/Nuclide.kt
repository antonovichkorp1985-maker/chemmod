package io.github.antonovichkorp.chemmod.core.radiation

/**
 * An atomic nucleus identified only by quantities that are conserved by the
 * radioactive-decay model. The core deliberately does not depend on a
 * Minecraft registry or on a hand-maintained periodic-table display name.
 */
data class Nuclide(
    val atomicNumber: Int,
    val massNumber: Int,
    val symbol: String,
) {
    init {
        require(atomicNumber > 0) { "Atomic number must be positive" }
        require(massNumber >= atomicNumber) {
            "Mass number $massNumber cannot be below atomic number $atomicNumber"
        }
        require(symbol.matches(Regex("[A-Z][a-z]?"))) { "Invalid nuclide symbol: $symbol" }
    }

    val neutronNumber: Int
        get() = massNumber - atomicNumber

    val displayName: String
        get() = "$symbol-$massNumber"
}

/** Nuclear transformations currently modelled by ChemMod's radiation core. */
enum class DecayMode(
    val daughterAtomicNumberDelta: Int,
    val daughterMassNumberDelta: Int,
) {
    ALPHA(-2, -4),
    BETA_MINUS(1, 0),
    BETA_PLUS(-1, 0),
    ELECTRON_CAPTURE(-1, 0),
}

/**
 * One physically possible daughter of a radioactive parent.
 *
 * [branchingFraction] is a probability, rather than a percentage. The
 * emitted-particle charge is implicit in [mode], so the network can validate
 * mass and nuclear-charge conservation independently of a presentation layer.
 */
data class DecayBranch(
    val mode: DecayMode,
    val daughter: Nuclide,
    val branchingFraction: Double,
    val releasedEnergyMeV: Double,
) {
    init {
        require(branchingFraction > 0.0 && branchingFraction <= 1.0) {
            "Branching fraction must be in (0, 1]"
        }
        require(releasedEnergyMeV >= 0.0 && releasedEnergyMeV.isFinite()) {
            "Released energy must be finite and non-negative"
        }
    }
}

/**
 * The decay data for one nuclide. A null half-life and no branches denotes a
 * stable nuclide. Half-lives are stored in SI seconds so an adapter can use
 * the same source of truth for ticks, real time, or an analytical calculation.
 */
data class DecayScheme(
    val parent: Nuclide,
    val halfLifeSeconds: Double?,
    val branches: List<DecayBranch>,
) {
    init {
        when (halfLifeSeconds) {
            null -> require(branches.isEmpty()) { "A stable nuclide cannot have decay branches" }
            else -> {
                require(halfLifeSeconds > 0.0 && halfLifeSeconds.isFinite()) {
                    "Radioactive half-life must be finite and positive"
                }
                require(branches.isNotEmpty()) { "A radioactive nuclide needs at least one branch" }
            }
        }
    }

    val isStable: Boolean
        get() = halfLifeSeconds == null

    companion object {
        fun stable(nuclide: Nuclide): DecayScheme = DecayScheme(nuclide, null, emptyList())
    }
}
