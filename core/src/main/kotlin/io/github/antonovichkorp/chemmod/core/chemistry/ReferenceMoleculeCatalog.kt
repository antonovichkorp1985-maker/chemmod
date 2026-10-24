package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.CommonSubstance
import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/** Solubility knowledge shown for a reference sample; unknown is not an insoluble result. */
enum class CatalogSolubility {
    UNKNOWN,
}

data class ReferenceMoleculeDescriptor(
    /** The graph-derived classes, in a stable presentation order. */
    val classes: List<String>,
    /** Length of the longest simple path through carbon atoms; zero for carbon-free substances. */
    val longestCarbonChainLength: Int,
    /** The current salt rules do not model aqueous solubility of neutral molecular samples. */
    val solubility: CatalogSolubility,
)

/**
 * Graph-derived descriptors and ordering for the finite set of bundled reference samples.
 * This is deliberately not a generator for arbitrary molecular graphs.
 */
object ReferenceMoleculeCatalog {
    private val classOrder = listOf(
        FunctionalGroups.ALKANE,
        FunctionalGroups.CYCLOALKANE,
        FunctionalGroups.ALKENE,
        FunctionalGroups.DIENE,
        FunctionalGroups.ALKYNE,
        FunctionalGroups.ARENE,
        FunctionalGroups.ALCOHOL,
        FunctionalGroups.POLYOL,
        FunctionalGroups.ETHER_CLASS,
        FunctionalGroups.ALDEHYDE,
        FunctionalGroups.KETONE,
        FunctionalGroups.CARBOXYLIC_ACID,
        FunctionalGroups.ESTER_CLASS,
        FunctionalGroups.PRIMARY_AMINE,
        FunctionalGroups.SECONDARY_AMINE,
        FunctionalGroups.TERTIARY_AMINE,
        FunctionalGroups.AMINE_CLASS,
        FunctionalGroups.NITRILE_CLASS,
        FunctionalGroups.HALIDE_CLASS,
        FunctionalGroups.THIOL_CLASS,
        FunctionalGroups.THIOETHER_CLASS,
        FunctionalGroups.HYDROCARBON,
    )

    fun describe(graph: MoleculeGraph): ReferenceMoleculeDescriptor {
        val derived = FunctionalGroups.classes(graph).toMutableSet()
        if (derived.any {
                it in setOf(
                    FunctionalGroups.ALKANE,
                    FunctionalGroups.CYCLOALKANE,
                    FunctionalGroups.ALKENE,
                    FunctionalGroups.DIENE,
                    FunctionalGroups.ALKYNE,
                    FunctionalGroups.ARENE,
                )
            }
        ) {
            derived.remove(FunctionalGroups.HYDROCARBON)
        }
        if (derived.any {
                it in setOf(
                    FunctionalGroups.PRIMARY_AMINE,
                    FunctionalGroups.SECONDARY_AMINE,
                    FunctionalGroups.TERTIARY_AMINE,
                )
            }
        ) {
            derived.remove(FunctionalGroups.AMINE_CLASS)
        }
        val ordered = classOrder.filter { it in derived }.toMutableList()
        // Molecules outside the current organic classifiers (for example water or oxygen) still
        // get a useful group rather than being mistaken for a carbon compound.
        if (ordered.isEmpty()) ordered += if (graph.atoms.any { it.element.symbol == "C" }) "other" else "inorganic"
        return ReferenceMoleculeDescriptor(
            classes = ordered,
            longestCarbonChainLength = longestCarbonChainLength(graph),
            // Solubility.isSoluble is a salt-rule API (cation + anion group), not a general
            // aqueous-solubility predictor. Do not infer values for these molecular references.
            solubility = CatalogSolubility.UNKNOWN,
        )
    }

    /** Sorts class-first, then by graph-derived chain length, then by stable sample ID. */
    fun sorted(samples: List<CommonSubstance>): List<CommonSubstance> = samples
        .map { sample ->
            val molecule = Molecule.fromSMILESlike(sample.structure)
            Triple(sample, describe(molecule.graph), molecule.canonicalKey())
        }
        .sortedWith(
            compareBy<Triple<CommonSubstance, ReferenceMoleculeDescriptor, String>> { (_, descriptor, _) ->
                descriptor.classes.firstOrNull()?.let(classOrder::indexOf)?.takeIf { it >= 0 }
                    ?: classOrder.size
            }
                .thenBy { (_, descriptor, _) -> descriptor.longestCarbonChainLength }
                .thenBy { (sample, _, _) -> sample.canonicalName }
                .thenBy { (_, _, key) -> key },
        )
        .map { it.first }

    /**
     * Exact longest simple path on the carbon-only subgraph. Bundled reference structures are
     * small; the general longest-path problem is NP-hard, so this is intentionally scoped to that
     * finite set rather than exposed as a scalable arbitrary-graph operation.
     */
    fun longestCarbonChainLength(graph: MoleculeGraph): Int {
        val carbonIds = graph.atoms.filter { it.element.symbol == "C" }.map { it.id }.toSet()
        if (carbonIds.isEmpty()) return 0
        val adjacency = carbonIds.associateWith { id ->
            graph.neighbors(id).map { it.first.id }.filter { it in carbonIds }
        }
        var longest = 1
        fun visit(current: Int, visited: MutableSet<Int>, length: Int) {
            if (length > longest) longest = length
            for (next in adjacency.getValue(current)) {
                if (visited.add(next)) {
                    visit(next, visited, length + 1)
                    visited.remove(next)
                }
            }
        }
        for (start in carbonIds) visit(start, mutableSetOf(start), 1)
        return longest
    }
}
