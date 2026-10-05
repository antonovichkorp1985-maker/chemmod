package io.github.antonovichkorp.chemmod.core.material

/**
 * Deterministic beneficiation quality model shared by runtime adapters.
 *
 * Batch mass is intentionally outside this calculation: the M3 washing stage
 * changes the measured composition of one canonical batch while preserving its
 * nominal mass. A later separator stage will turn recovered gangue into explicit
 * mass-partitioned outputs.
 */
data class WashedComposition(
    val purityPpm: Int,
    val impuritiesPpm: Map<String, Int>,
) {
    init {
        require(purityPpm in 0..PARTS_PER_MILLION) { "Purity must be 0..1,000,000 ppm" }
        require(impuritiesPpm.values.all { it > 0 }) { "Known impurity concentrations must be positive" }
        require(impuritiesPpm.values.sumOf { it.toLong() } <= PARTS_PER_MILLION - purityPpm) {
            "Known impurities exceed the non-primary fraction"
        }
    }

    companion object { const val PARTS_PER_MILLION = 1_000_000 }
}

object OreWashingQuality {
    private const val PPM = WashedComposition.PARTS_PER_MILLION
    private const val BASE_RETAINED_PPM = 90_000
    private const val MAX_WATER_PENALTY_PPM = 300_000

    /**
     * Returns the remaining gangue after one wash. Cleaner recorded water retains
     * less gangue. Unknown impurity remainder is assigned to [fallbackGangueId]
     * rather than disappearing from the measured batch.
     */
    @JvmStatic
    fun wash(
        inputPurityPpm: Int,
        inputImpuritiesPpm: Map<String, Int>,
        waterImpuritiesPpm: Int,
        fallbackGangueId: String,
    ): WashedComposition {
        require(inputPurityPpm in 0..PPM) { "Input purity must be 0..1,000,000 ppm" }
        require(waterImpuritiesPpm in 0..PPM) { "Water impurities must be 0..1,000,000 ppm" }
        require(fallbackGangueId.matches(Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+"))) {
            "Fallback gangue needs a valid material ID"
        }
        require(inputImpuritiesPpm.values.all { it > 0 }) { "Known impurity concentrations must be positive" }

        val sourceImpurities = PPM - inputPurityPpm
        require(inputImpuritiesPpm.values.sumOf { it.toLong() } <= sourceImpurities.toLong()) {
            "Known impurities exceed the non-primary fraction"
        }
        if (sourceImpurities == 0) return WashedComposition(PPM, emptyMap())

        val waterPenalty = (waterImpuritiesPpm * 5).coerceAtMost(MAX_WATER_PENALTY_PPM)
        val retainedFractionPpm = BASE_RETAINED_PPM + waterPenalty
        val residualImpurities = ceilingProduct(sourceImpurities, retainedFractionPpm)
        val retained = linkedMapOf<String, Int>()
        var allocated = 0
        inputImpuritiesPpm.forEach { (id, ppm) ->
            val retainedPpm = ((ppm.toLong() * residualImpurities) / sourceImpurities).toInt()
            if (retainedPpm > 0) {
                retained[id] = retainedPpm
                allocated += retainedPpm
            }
        }
        val unallocated = residualImpurities - allocated
        if (unallocated > 0) {
            retained[fallbackGangueId] = retained.getOrDefault(fallbackGangueId, 0) + unallocated
        }

        return WashedComposition(PPM - residualImpurities, retained)
    }

    private fun ceilingProduct(amount: Int, multiplierPpm: Int): Int =
        ((amount.toLong() * multiplierPpm + PPM - 1L) / PPM).toInt()
}
