package io.github.antonovichkorp.chemmod.core.material

import java.math.BigInteger

/**
 * Exact physical composition of one homogeneous material batch.
 *
 * The primary material is carried separately because its identity is supplied by
 * the containing batch. Every microgram is named either as primary material or as
 * a known impurity; percentages are derived only for presentation.
 */
data class MaterialMassComposition(
    val primaryMassMicrograms: Long,
    val impurityMassMicrograms: Map<String, Long> = emptyMap(),
) {
    init {
        require(primaryMassMicrograms >= 0) { "Primary material mass cannot be negative" }
        require(impurityMassMicrograms.keys.all(::isMaterialId)) { "Impurity needs a valid material ID" }
        require(impurityMassMicrograms.values.all { it > 0 }) { "Impurity masses must be positive" }
    }

    val totalMassMicrograms: Long = impurityMassMicrograms.values.fold(primaryMassMicrograms, Math::addExact)

    init {
        require(totalMassMicrograms > 0) { "Composition must contain physical mass" }
    }

    /** Rounded only for display; calculations always use the exact mass fields. */
    @JvmOverloads
    fun purityPpm(partsPerMillion: Int = PARTS_PER_MILLION): Int {
        require(partsPerMillion > 0) { "Parts-per-million scale must be positive" }
        val scaled = Math.addExact(
            Math.multiplyExact(primaryMassMicrograms, partsPerMillion.toLong()),
            totalMassMicrograms / 2,
        )
        return (scaled / totalMassMicrograms).toInt()
    }

    /** Rounded display fractions of the named impurity components. */
    fun impuritiesPpm(): Map<String, Int> = impurityMassMicrograms
        .toSortedMap()
        .mapValues { (_, mass) ->
            val scaled = Math.addExact(
                Math.multiplyExact(mass, PARTS_PER_MILLION.toLong()),
                totalMassMicrograms / 2,
            )
            (scaled / totalMassMicrograms).toInt()
        }

    fun mergedWith(other: MaterialMassComposition): MaterialMassComposition {
        val mergedImpurities = linkedMapOf<String, Long>()
        (impurityMassMicrograms.keys + other.impurityMassMicrograms.keys)
            .toSortedSet()
            .forEach { id ->
                val mass = Math.addExact(
                    impurityMassMicrograms.getOrDefault(id, 0L),
                    other.impurityMassMicrograms.getOrDefault(id, 0L),
                )
                if (mass > 0) mergedImpurities[id] = mass
            }
        return MaterialMassComposition(
            primaryMassMicrograms = Math.addExact(primaryMassMicrograms, other.primaryMassMicrograms),
            impurityMassMicrograms = mergedImpurities,
        )
    }

    /**
     * Splits a batch by total mass while preserving every component exactly across
     * the two results. The first result receives [extractedMassMicrograms].
     */
    fun split(extractedMassMicrograms: Long): MaterialMassSplit {
        require(extractedMassMicrograms in 1..totalMassMicrograms) {
            "Extracted mass must be within the physical batch"
        }
        val parts = linkedMapOf(PRIMARY_KEY to primaryMassMicrograms)
        impurityMassMicrograms.toSortedMap().forEach { (id, mass) -> parts[id] = mass }
        val extractedParts = distributeProportionally(parts, extractedMassMicrograms)
        val remainderParts = linkedMapOf<String, Long>()
        parts.forEach { (id, mass) ->
            val remainder = Math.subtractExact(mass, extractedParts.getOrDefault(id, 0L))
            if (remainder > 0) remainderParts[id] = remainder
        }
        return MaterialMassSplit(
            extracted = fromParts(extractedParts),
            remainder = if (remainderParts.isEmpty()) null else fromParts(remainderParts),
        )
    }

    companion object {
        const val PARTS_PER_MILLION = 1_000_000
        private const val PRIMARY_KEY = "__primary_component__"
        private val MATERIAL_ID = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")

        private fun isMaterialId(value: String): Boolean = MATERIAL_ID.matches(value)

        private fun fromParts(parts: Map<String, Long>): MaterialMassComposition = MaterialMassComposition(
            primaryMassMicrograms = parts.getOrDefault(PRIMARY_KEY, 0L),
            impurityMassMicrograms = parts
                .filterKeys { it != PRIMARY_KEY }
                .filterValues { it > 0 },
        )

        /**
         * Deterministically allocates [requestedMass] proportionally. Remainders
         * are assigned by largest fractional remainder, then material ID, so no
         * microgram disappears during any split.
         */
        internal fun distributeProportionally(
            parts: Map<String, Long>,
            requestedMass: Long,
        ): Map<String, Long> {
            val sourceMass = parts.values.fold(0L, Math::addExact)
            require(requestedMass in 0..sourceMass) { "Requested mass exceeds source composition" }
            if (requestedMass == 0L) return emptyMap()

            val denominator = BigInteger.valueOf(sourceMass)
            val multiplier = BigInteger.valueOf(requestedMass)
            data class Allocation(val id: String, val base: Long, val remainder: BigInteger)
            val allocations = parts
                .filterValues { it > 0 }
                .toSortedMap()
                .map { (id, mass) ->
                    val product = BigInteger.valueOf(mass).multiply(multiplier)
                    Allocation(id, product.divide(denominator).longValueExact(), product.remainder(denominator))
                }
            val allocated = allocations.sumOf(Allocation::base)
            var remainder = Math.subtractExact(requestedMass, allocated)
            val result = allocations.associateTo(linkedMapOf()) { it.id to it.base }
            allocations
                .sortedWith(compareByDescending<Allocation> { it.remainder }.thenBy { it.id })
                .forEach { allocation ->
                    if (remainder > 0) {
                        result[allocation.id] = Math.addExact(result.getValue(allocation.id), 1L)
                        remainder--
                    }
                }
            check(remainder == 0L) { "Proportional allocation left unassigned mass" }
            return result.filterValues { it > 0 }
        }
    }
}

data class MaterialMassSplit(
    val extracted: MaterialMassComposition,
    val remainder: MaterialMassComposition?,
)
