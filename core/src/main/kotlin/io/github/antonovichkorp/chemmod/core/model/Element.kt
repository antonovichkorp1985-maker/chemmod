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
        require(valences.isNotEmpty() && valences.all { it > 0 }) { "At least one positive valence is required" }
    }
}
