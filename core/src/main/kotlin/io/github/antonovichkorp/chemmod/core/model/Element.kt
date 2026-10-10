package io.github.antonovichkorp.chemmod.core.model

data class Element(
    val symbol: String,
    val name: String,
    val atomicNumber: Int,
    val atomicMass: Double,
    val valences: List<Int>,
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
    }
}
