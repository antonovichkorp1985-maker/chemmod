package io.github.antonovichkorp.chemmod.core.model

import kotlin.math.min

/**
 * Which lattice an ionic solid adopts, derived from the radii of its two ions.
 *
 * The radius-ratio rules are geometry, not a lookup: a cation fits in the hole left by the anions
 * around it only while it stays large enough to touch them, and the coordination number that
 * follows decides the structure. So sodium chloride is rock salt because of how big its ions are,
 * and a salt nobody ever listed gets the same answer from its own two radii.
 *
 * This is a first-order model. It says nothing about covalent or metallic solids, ignores
 * polarisation, and radius-ratio rules are known to mispredict borderline cases such as lithium
 * iodide. A null means "no model", never "no structure".
 */
object CrystalLattice {
    /** Coordination of the cation, and the structure it corresponds to. */
    enum class Structure(val coordination: Int, val example: String) {
        LINEAR(2, "carbon dioxide"),
        TRIANGULAR(3, "boron trioxide"),
        TETRAHEDRAL(4, "zinc sulfide"),
        OCTAHEDRAL(6, "sodium chloride"),
        CUBIC(8, "caesium chloride"),
        CLOSE_PACKED(12, "sodium chloride under pressure"),
    }

    /** Upper radius-ratio bound for each coordination, ascending. */
    private val BOUNDS = listOf(
        0.155 to Structure.LINEAR,
        0.225 to Structure.TRIANGULAR,
        0.414 to Structure.TETRAHEDRAL,
        0.732 to Structure.OCTAHEDRAL,
        1.000 to Structure.CUBIC,
    )

    /**
     * The lattice predicted for a cation and an anion, or null when either radius is unknown.
     * Radii are taken regardless of sign because the ratio is about size, not charge.
     */
    fun predict(cation: Element, anion: Element): Structure? {
        val small = cation.ionicRadiusPicometres ?: return null
        val large = anion.ionicRadiusPicometres ?: return null
        if (small <= 0.0 || large <= 0.0) return null
        // The smaller ion occupies the hole, whichever of the two that turns out to be.
        val ratio = min(small, large) / maxOf(small, large)
        for ((bound, structure) in BOUNDS) if (ratio < bound) return structure
        return Structure.CLOSE_PACKED
    }

    /** The radius ratio itself, exposed so a borderline case can be seen as borderline. */
    fun radiusRatio(cation: Element, anion: Element): Double? {
        val small = cation.ionicRadiusPicometres ?: return null
        val large = anion.ionicRadiusPicometres ?: return null
        if (small <= 0.0 || large <= 0.0) return null
        return min(small, large) / maxOf(small, large)
    }
}
