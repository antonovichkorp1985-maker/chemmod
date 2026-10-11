package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.model.Atom
import io.github.antonovichkorp.chemmod.core.model.Bond
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/**
 * Writes the supported M0 graph subset back into the project's SMILES-like
 * notation. The result is a deterministic structural witness, not a chemical
 * name and not a replacement for [Molecule.canonicalKey].
 */
object StructureNotationWriter {
    /**
     * Render a graph such that reparsing it preserves graph identity,
     * formal charges, explicit hydrogens and bond orders. M0 uses one-digit
     * ring labels, so graphs with more than ten non-tree edges are explicitly
     * outside the representable subset.
     */
    fun write(graph: MoleculeGraph): String {
        require(graph.atoms.isNotEmpty()) { "Cannot write an empty molecular graph" }

        val treeChildren = graph.atoms.associate { it.id to mutableListOf<Bond>() }
        val cycleEdges = mutableListOf<Bond>()
        val visitedAtoms = mutableSetOf<Int>()
        val processedEdges = mutableSetOf<Bond>()

        fun orderedEdges(atomId: Int): List<Bond> = graph.bondsOf(atomId).sortedWith(
            compareBy<Bond> { it.other(atomId) }
                .thenBy { it.order.value }
                .thenBy { minOf(it.first, it.second) }
                .thenBy { maxOf(it.first, it.second) },
        )

        fun visit(atomId: Int) {
            visitedAtoms += atomId
            orderedEdges(atomId).forEach { bond ->
                if (!processedEdges.add(bond)) return@forEach
                val other = bond.other(atomId)
                if (other !in visitedAtoms) {
                    treeChildren.getValue(atomId) += bond
                    visit(other)
                } else {
                    cycleEdges += bond
                }
            }
        }

        val roots = mutableListOf<Int>()
        graph.atoms.map(Atom::id).sorted().forEach { atomId ->
            if (atomId !in visitedAtoms) {
                roots += atomId
                visit(atomId)
            }
        }

        require(cycleEdges.size <= RING_LABELS.size) {
            "M0 structural notation supports at most ${RING_LABELS.size} ring closures"
        }
        val closures = graph.atoms.associate { it.id to mutableListOf<RingClosure>() }
        cycleEdges.forEachIndexed { index, bond ->
            val opening = minOf(bond.first, bond.second)
            val closing = maxOf(bond.first, bond.second)
            val label = RING_LABELS[index]
            closures.getValue(opening) += RingClosure(label, bond.order, writesBond = true)
            closures.getValue(closing) += RingClosure(label, bond.order, writesBond = false)
        }

        fun render(atomId: Int): String = buildString {
            append(atomToken(graph.atom(atomId)))
            closures.getValue(atomId).sortedBy(RingClosure::label).forEach { closure ->
                if (closure.writesBond) append(bondToken(closure.order))
                append(closure.label)
            }
            val children = treeChildren.getValue(atomId).sortedBy { it.other(atomId) }
            children.forEachIndexed { index, bond ->
                val segment = bondToken(bond.order) + render(bond.other(atomId))
                // Branches must be written before the one linear child: after a
                // linear child has been rendered, parser state is at that child.
                if (index == children.lastIndex) append(segment) else append('(').append(segment).append(')')
            }
        }

        return roots.joinToString(separator = ".") { root -> render(root) }
    }

    private fun atomToken(atom: Atom): String {
        if (atom.explicitHydrogens == 0 && atom.formalCharge == 0) return atom.element.symbol
        val hydrogens = when (atom.explicitHydrogens) {
            0 -> ""
            1 -> "H"
            else -> "H${atom.explicitHydrogens}"
        }
        val charge = when (atom.formalCharge) {
            0 -> ""
            1 -> "+"
            -1 -> "-"
            else -> if (atom.formalCharge > 0) "+${atom.formalCharge}" else atom.formalCharge.toString()
        }
        return "[${atom.element.symbol}$hydrogens$charge]"
    }

    private fun bondToken(order: BondOrder): String = if (order == BondOrder.SINGLE) "" else order.symbol

    private data class RingClosure(
        val label: Char,
        val order: BondOrder,
        val writesBond: Boolean,
    )

    private val RING_LABELS: List<Char> = ('0'..'9').toList()
}
