package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/**
 * Aromaticity derived from the Kekulé graph: a ring is aromatic when every member is conjugated
 * and the ring's π electrons satisfy Hückel's 4n+2 rule.
 *
 * No aromatic bond order is invented. A 1.5-order bond would break canonical identity, valence
 * filling and atom conservation at once, so aromaticity stays a derived property of the ordinary
 * covalent graph instead of a fourth bond type.
 */
object AromaticityModel {
    const val MAXIMUM_RING_SIZE = 10
    private const val CYCLE_SEARCH_LIMIT = 512
    private const val SEARCH_STEP_LIMIT = 20_000

    /** Atom ids of every aromatic ring, each ring sorted and listed once. */
    fun aromaticRings(graph: MoleculeGraph): List<List<Int>> =
        simpleCycles(graph)
            .mapNotNull { ring -> if (huckelPiElectrons(graph, ring) != null) ring else null }

    fun isAromatic(graph: MoleculeGraph): Boolean = aromaticRings(graph).isNotEmpty()

    /**
     * π electrons contributed by the ring when every member is conjugated, and `null` otherwise.
     * An atom with an endocyclic multiple bond contributes one π electron (pyridine nitrogen
     * included, its lone pair stays in the ring plane); a saturated heteroatom contributes the
     * pair that closes the sextet, as in pyrrole.
     */
    fun huckelPiElectrons(graph: MoleculeGraph, ring: List<Int>): Int? {
        if (ring.size < 3) return null
        val members = ring.toSet()
        var pi = 0
        ring.forEach { atomId ->
            val endocyclicMultiple = graph.bondsOf(atomId).any { bond ->
                bond.order != BondOrder.SINGLE && bond.other(atomId) in members
            }
            pi += when {
                endocyclicMultiple -> 1
                ElectronBookkeeping.lonePairs(graph, atomId) >= 1 -> 2
                else -> return null
            }
        }
        return if (pi >= 2 && (pi - 2) % 4 == 0) pi else null
    }

    /**
     * Every simple cycle up to [MAXIMUM_RING_SIZE]. Aromaticity is a property prediction, so the
     * search carries an explicit step budget: a pathological graph loses its aromatic rings
     * instead of stalling whatever called `properties()`.
     */
    private fun simpleCycles(graph: MoleculeGraph): List<List<Int>> {
        val found = linkedSetOf<List<Int>>()
        var steps = 0

        fun search(startId: Int, currentId: Int, path: MutableList<Int>, onPath: MutableSet<Int>) {
            if (steps++ > SEARCH_STEP_LIMIT || found.size >= CYCLE_SEARCH_LIMIT) return
            graph.neighbors(currentId).forEach { (neighbor, _) ->
                if (neighbor.id == startId) {
                    if (path.size >= 3) found.add(path.toList().sorted())
                    return@forEach
                }
                // Canonical ordering keeps each cycle discovered exactly once.
                if (neighbor.id <= startId || neighbor.id in onPath) return@forEach
                if (path.size >= MAXIMUM_RING_SIZE) return@forEach
                path.add(neighbor.id)
                onPath.add(neighbor.id)
                search(startId, neighbor.id, path, onPath)
                path.removeAt(path.size - 1)
                onPath.remove(neighbor.id)
            }
        }

        graph.atoms.forEach { start ->
            search(start.id, start.id, mutableListOf(start.id), mutableSetOf(start.id))
        }
        return found.toList()
    }
}
