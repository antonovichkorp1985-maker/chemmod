package io.github.antonovichkorp.chemmod.core.geometry

import io.github.antonovichkorp.chemmod.core.bond.ElectronBookkeeping
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/**
 * Local geometry of one atom: how many electron domains surround it, how many of them are lone
 * pairs, and the shape and ideal angle that VSEPR gives for that combination.
 *
 * This is not a coordinate model. It answers "is water bent and at what angle", not "where are
 * its atoms in space"; producing coordinates from these angles is the next layer.
 */
data class AtomGeometry(
    val atomId: Int,
    /** σ domains: every neighbour counts once, a triple bond is still one domain. */
    val bondingDomains: Int,
    val lonePairs: Int,
    val shape: String,
    val idealBondAngleDegrees: Double?,
) {
    val electronDomains: Int get() = bondingDomains + lonePairs
}

/**
 * VSEPR geometry derived from the covalent graph plus the lone pairs that
 * [ElectronBookkeeping] already counts. Nothing here is told that water is bent: it follows from
 * oxygen having two bonds and two lone pairs.
 */
object MolecularGeometry {
    const val TERMINAL_SHAPE = "terminal"
    const val UNCLASSIFIED_SHAPE = "unclassified"

    fun forAtom(graph: MoleculeGraph, atomId: Int): AtomGeometry {
        val bonding = graph.neighbors(atomId).size + ElectronBookkeeping.hydrogens(graph, atomId)
        val pairs = ElectronBookkeeping.lonePairs(graph, atomId)
        if (bonding < 2) {
            return AtomGeometry(atomId, bonding, pairs, TERMINAL_SHAPE, null)
        }
        val (shape, angle) = classify(bonding + pairs, pairs)
        return AtomGeometry(atomId, bonding, pairs, shape, angle)
    }

    fun all(graph: MoleculeGraph): List<AtomGeometry> = graph.atoms.map { forAtom(graph, it.id) }

    /**
     * The geometry the molecule is reported by: the atom with the most electron domains, ties
     * broken towards the one carrying lone pairs, then towards the lower atom id. For water that
     * is oxygen, so the reported shape is bent at 104.5°.
     */
    fun centralAtom(graph: MoleculeGraph): AtomGeometry? =
        all(graph)
            .filter { it.shape != TERMINAL_SHAPE }
            .maxWithOrNull(
                compareBy({ it.electronDomains }, { it.lonePairs }, { -it.atomId }),
            )

    /** Shape label of [centralAtom], or null when every atom is terminal (a diatomic, say). */
    fun molecularShape(graph: MoleculeGraph): String? = centralAtom(graph)?.shape

    fun bondAngleDegrees(graph: MoleculeGraph): Double? = centralAtom(graph)?.idealBondAngleDegrees

    private fun classify(domains: Int, lonePairs: Int): Pair<String, Double?> = when (domains) {
        2 -> "linear" to 180.0
        3 -> when (lonePairs) {
            0 -> "trigonal-planar" to 120.0
            else -> "bent" to 119.0
        }
        4 -> when (lonePairs) {
            0 -> "tetrahedral" to 109.5
            1 -> "trigonal-pyramidal" to 107.0
            else -> "bent" to 104.5
        }
        5 -> when (lonePairs) {
            0 -> "trigonal-bipyramidal" to 120.0
            1 -> "seesaw" to 120.0
            2 -> "t-shaped" to 90.0
            else -> "linear" to 180.0
        }
        6 -> when (lonePairs) {
            0 -> "octahedral" to 90.0
            1 -> "square-pyramidal" to 90.0
            else -> "square-planar" to 90.0
        }
        else -> UNCLASSIFIED_SHAPE to null
    }
}
