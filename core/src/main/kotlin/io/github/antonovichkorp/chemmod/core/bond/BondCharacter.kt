package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.model.Element
import kotlin.math.abs
import kotlin.math.exp

/**
 * What kind of bond two atoms form, derived from a single property of each atom.
 *
 * Nothing here is recorded per bond or per substance. Pauling's relation gives the ionic fraction
 * of a bond from the electronegativity difference alone, `1 − exp(−¼·Δχ²)`, and everything that
 * follows in the model — whether partners can exchange in a metathesis, whether a product leaves
 * solution as a precipitate, whether a lattice is ionic or a molecular solid — is downstream of
 * that number. Writing those outcomes per substance is exactly the table this avoids.
 *
 * Where either atom has no accepted electronegativity the answer is null. A gap in the data must
 * not be read as a non-polar bond: a null means "no model", and callers must say so rather than
 * quietly treating the bond as covalent.
 */
object BondCharacter {
    /** Above this ionic fraction a bond is treated as ionic rather than polar covalent. */
    const val IONIC_FRACTION_THRESHOLD = 0.5

    /**
     * Pauling ionic fraction of a bond between two elements, in 0..1, or null when either
     * electronegativity is unknown.
     */
    fun ionicFraction(first: Element, second: Element): Double? {
        val chiA = first.electronegativity ?: return null
        val chiB = second.electronegativity ?: return null
        val difference = abs(chiA - chiB)
        return 1.0 - exp(-0.25 * difference * difference)
    }

    /** Whether the bond is ionic enough for its partners to exchange in a metathesis. */
    fun isIonic(first: Element, second: Element): Boolean? {
        val fraction = ionicFraction(first, second) ?: return null
        return fraction >= IONIC_FRACTION_THRESHOLD
    }

    /** True only when both atoms are the same element: a purely covalent, non-polar bond. */
    fun isPurelyCovalent(first: Element, second: Element): Boolean =
        first.symbol == second.symbol
}
