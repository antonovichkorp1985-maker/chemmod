package io.github.antonovichkorp.chemmod.core.model

data class Element(
    val symbol: String,
    val name: String,
    val atomicNumber: Int,
    val atomicMass: Double,
    val valences: List<Int>,
    /**
     * Outer-shell electrons available for bonding and lone pairs. Derived from the element's
     * position in the table, so lone-pair chemistry never needs a per-substance record.
     */
    val valenceElectrons: Int,
    /**
     * Atomic polarizibility volume in cubic angstroms, or null where no measured value is held.
     * Dispersion between molecules follows how easily electron clouds deform, not mass: chlorine
     * is barely heavier than a methyl group yet far more polarizable. Sources: the CCCBDB atomic
     * list (Miller & Bederson 1977) for H through Kr, Miller 1990 for iodine.
     */
    val polarizibilityCubicAngstroms: Double? = null,
    /**
     * Pauling electronegativity, or null where no accepted value is held.
     *
     * This is the property bond character is derived from rather than recorded: the difference
     * between two bonded atoms decides how ionic the bond is, and from that follow whether partners
     * can exchange in a metathesis, which product leaves solution, and whether a lattice is ionic or
     * molecular at all. Storing it per bond or per substance would be the table this design avoids.
     * Values are Pauling's scale as revised by Allred (1961); noble gases and the heaviest elements
     * are left null rather than guessed, and a null must be reported as "no model", never as zero.
     */
    val electronegativity: Double? = null,
) {
    init {
        require(symbol.matches(Regex("[A-Z][a-z]?"))) { "Invalid element symbol: $symbol" }
        require(atomicNumber > 0) { "Atomic number must be positive" }
        require(atomicMass > 0.0) { "Atomic mass must be positive" }
        // A valence of exactly zero marks an inert element: it may exist as a monatomic
        // substance but can never form a bond, because no occupied sum can exceed zero
        // only when the atom has no bonds at all.
        require(valences.isNotEmpty() && valences.all { it >= 0 }) {
            "At least one non-negative valence is required"
        }
        require(valenceElectrons > 0) { "An element needs at least one valence electron" }
        require(polarizibilityCubicAngstroms == null || polarizibilityCubicAngstroms > 0.0) {
            "Polarizibility must be positive where it is known"
        }
        require(electronegativity == null || electronegativity > 0.0) {
            "Electronegativity must be positive where it is known"
        }
    }
}
