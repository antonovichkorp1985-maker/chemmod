package io.github.antonovichkorp.chemmod.core.composition

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/**
 * How the mass of one substance splits over the elements it is built from.
 *
 * This is the seam between a molecular graph and everything that carries mass. The gas field needs
 * the mass of each element in a cell to know how much radon that cell holds; the radiation model
 * turns an element mass into an activity through the natural isotopic composition of that element.
 * Neither has to know what the molecule was, and neither needs a record for it — the split follows
 * from the graph and the element table, so a new compound brings its own partition.
 *
 * Masses stay exact integer micrograms and every microgram is assigned, mirroring the invariant
 * `MaterialMassComposition` keeps for impurities: a partition rounded to percentages would quietly
 * create or lose mass.
 */
object ElementMassPartition {
    /**
     * Mass fraction of each element, keyed by symbol. The values sum to 1 for any molecule with a
     * positive molar mass. Every element of the formula is present as a key.
     */
    fun massFractions(graph: MoleculeGraph): Map<String, Double> {
        val perElement = FormulaCalculator.elementMolarMasses(graph)
        require(perElement.isNotEmpty()) { "A molecule needs at least one atom to have a mass" }
        val total = perElement.values.sum()
        require(total > 0.0) { "A molecule needs a positive molar mass" }
        return perElement.mapValues { (_, mass) -> mass / total }
    }

    /**
     * [totalMicrograms] of this substance split over its elements. The parts always add up to
     * exactly [totalMicrograms]: the micrograms left over by integer division go to the elements
     * whose exact share was cut the most, ties broken by symbol so the result is reproducible.
     *
     * An element present in the formula keeps a key even when it receives zero micrograms, because
     * "this sample is too small to hold a whole microgram of hydrogen" is a true statement about
     * the sample, not an absent element.
     */
    fun partition(graph: MoleculeGraph, totalMicrograms: Long): Map<String, Long> {
        require(totalMicrograms >= 0L) { "Mass cannot be negative, got $totalMicrograms" }
        if (totalMicrograms == 0L) return emptyMap()
        val exact = massFractions(graph).mapValues { (_, fraction) -> fraction * totalMicrograms }
        val assigned = exact.mapValues { (_, value) -> kotlin.math.floor(value).toLong() }.toMutableMap()
        var unassigned = totalMicrograms - assigned.values.sum()
        val byLargestRemainder = exact.entries.sortedWith(
            compareByDescending<Map.Entry<String, Double>> { it.value - kotlin.math.floor(it.value) }
                .thenBy { it.key },
        )
        for (entry in byLargestRemainder) {
            if (unassigned <= 0L) break
            assigned[entry.key] = assigned.getValue(entry.key) + 1L
            unassigned--
        }
        check(unassigned == 0L) { "Partition of $totalMicrograms µg left $unassigned µg unassigned" }
        return assigned
    }

    /**
     * Mass of one element inside [totalMicrograms] of this substance, in micrograms. Convenience for
     * callers that need a single element — the radiation model asking how much uranium a rock
     * holds — without building the whole map.
     */
    fun massOfElementMicrograms(graph: MoleculeGraph, symbol: String, totalMicrograms: Long): Long =
        partition(graph, totalMicrograms)[symbol] ?: 0L
}
