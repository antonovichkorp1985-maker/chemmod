package io.github.antonovichkorp.chemmod.core.material

/** Exact physical outputs from separating a washed mineral concentrate. */
data class OreSeparationResult(
    val primaryMassMicrograms: Long,
    val separatedImpurityMassMicrograms: Map<String, Long>,
) {
    init {
        require(primaryMassMicrograms > 0) { "A mineral concentrate needs primary material" }
        require(separatedImpurityMassMicrograms.values.all { it > 0 }) {
            "Separated impurity masses must be positive"
        }
    }

    val separatedTotalMassMicrograms: Long =
        separatedImpurityMassMicrograms.values.fold(0L, Math::addExact)
}

/**
 * Final dry mineral separation after washing. This operation does not improve
 * recovery by chance: it simply materializes every residual non-primary component
 * into its own physical output while keeping the primary material mass exact.
 */
object OreSeparation {
    @JvmStatic
    fun separate(input: MaterialMassComposition): OreSeparationResult = OreSeparationResult(
        primaryMassMicrograms = input.primaryMassMicrograms,
        separatedImpurityMassMicrograms = input.impurityMassMicrograms.toSortedMap(),
    )
}
