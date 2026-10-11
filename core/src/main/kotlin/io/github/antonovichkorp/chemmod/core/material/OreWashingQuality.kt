package io.github.antonovichkorp.chemmod.core.material

/**
 * Exact output of physical ore washing.
 *
 * Water quality affects how much gangue remains attached to the copper
 * concentrate. It never creates primary material: all removed mass is recorded
 * as a named tailings output.
 */
data class OreWashingResult(
    val concentrate: MaterialMassComposition,
    val tailingsMassMicrograms: Map<String, Long>,
) {
    init {
        require(tailingsMassMicrograms.keys.all { it.matches(MATERIAL_ID) }) {
            "Tailings need valid material IDs"
        }
        require(tailingsMassMicrograms.values.all { it > 0 }) { "Tailings masses must be positive" }
    }

    val tailingsTotalMassMicrograms: Long = tailingsMassMicrograms.values.fold(0L, Math::addExact)

    companion object {
        private val MATERIAL_ID = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")
    }
}

/** Deterministic, mass-conserving washing model shared by runtime adapters. */
object OreWashingQuality {
    private const val PPM = MaterialMassComposition.PARTS_PER_MILLION
    private const val BASE_RETAINED_PPM = 90_000
    private const val MAX_WATER_PENALTY_PPM = 300_000

    /**
     * Produces a denser concentrate and explicit tailings from [input].
     *
     * The historical quality curve is retained for player continuity: clean water
     * targets 98.65% copper and 35,000 ppm saline water targets 96.025% copper
     * for the former 85% natural-copper reference. Unlike schema v1, the copper
     * mass is never increased to reach that display purity.
     */
    @JvmStatic
    fun wash(input: MaterialMassComposition, waterImpuritiesPpm: Int): OreWashingResult {
        require(waterImpuritiesPpm in 0..PPM) { "Water impurities must be 0..1,000,000 ppm" }
        val sourceGangueMass = input.impurityMassMicrograms.values.fold(0L, Math::addExact)
        if (sourceGangueMass == 0L) return OreWashingResult(input, emptyMap())

        val sourceImpurityPpm = PPM - input.purityPpm()
        val waterPenalty = (waterImpuritiesPpm * 5).coerceAtMost(MAX_WATER_PENALTY_PPM)
        val retainedFractionPpm = BASE_RETAINED_PPM + waterPenalty
        val targetPurityPpm = PPM - ceilingProduct(sourceImpurityPpm, retainedFractionPpm)
        require(targetPurityPpm in 1..PPM) { "Washing cannot produce a zero-purity concentrate" }

        // The primary copper mass is invariant. Calculate the smallest integer
        // concentrate mass that reaches the target display purity, then allocate
        // its retained gangue proportionally and send the remainder to tailings.
        val concentrateMass = ceilingDivision(
            Math.multiplyExact(input.primaryMassMicrograms, PPM.toLong()),
            targetPurityPpm.toLong(),
        )
        val retainedGangueMass = Math.subtractExact(concentrateMass, input.primaryMassMicrograms)
        require(retainedGangueMass in 0..sourceGangueMass) {
            "Washing model cannot retain more gangue than the input contains"
        }

        val retainedGangue = MaterialMassComposition.distributeProportionally(
            input.impurityMassMicrograms,
            retainedGangueMass,
        )
        val tailings = linkedMapOf<String, Long>()
        input.impurityMassMicrograms.toSortedMap().forEach { (id, inputMass) ->
            val removed = Math.subtractExact(inputMass, retainedGangue.getOrDefault(id, 0L))
            if (removed > 0) tailings[id] = removed
        }
        val concentrate = MaterialMassComposition(input.primaryMassMicrograms, retainedGangue)
        check(Math.addExact(concentrate.totalMassMicrograms, tailings.values.fold(0L, Math::addExact)) == input.totalMassMicrograms) {
            "Washing must conserve total physical mass"
        }
        return OreWashingResult(concentrate, tailings)
    }

    private fun ceilingProduct(amount: Int, multiplierPpm: Int): Int =
        ((amount.toLong() * multiplierPpm + PPM - 1L) / PPM).toInt()

    private fun ceilingDivision(dividend: Long, divisor: Long): Long =
        Math.addExact(dividend, divisor - 1L) / divisor
}
