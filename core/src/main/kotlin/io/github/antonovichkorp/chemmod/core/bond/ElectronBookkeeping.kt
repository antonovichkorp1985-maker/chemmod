package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/**
 * Electron bookkeeping on top of the covalent graph. Every count below is derived from the
 * element's own valence electrons and the bonds actually drawn, so non-covalent chemistry
 * never needs a per-substance record.
 *
 * A formal charge shifts the electron count of that one atom: +1 removes an electron,
 * -1 adds one. Remaining pairs that are not used in a bond are lone pairs.
 */
object ElectronBookkeeping {
    /** Bonds to heavy neighbours plus every hydrogen the atom carries, explicit or implicit. */
    fun bondedAtomCount(graph: MoleculeGraph, atomId: Int): Int {
        val atom = graph.atom(atomId)
        return graph.bondOrderSum(atomId) + atom.explicitHydrogens + graph.implicitHydrogens(atomId)
    }

    fun hydrogens(graph: MoleculeGraph, atomId: Int): Int {
        val atom = graph.atom(atomId)
        return atom.explicitHydrogens + graph.implicitHydrogens(atomId)
    }

    /** Electrons the atom keeps for itself: its valence shell minus bonds and charge. */
    fun nonBondingElectrons(graph: MoleculeGraph, atomId: Int): Int {
        val atom = graph.atom(atomId)
        return atom.element.valenceElectrons - atom.formalCharge - bondedAtomCount(graph, atomId)
    }

    fun lonePairs(graph: MoleculeGraph, atomId: Int): Int =
        (nonBondingElectrons(graph, atomId) / 2).coerceAtLeast(0)

    /**
     * Electrons surrounding the atom, counting each shared pair once for the atom. Eight is a
     * complete octet; two is a complete duet. Anything less leaves a real vacancy, which is what
     * makes an atom a Lewis acid rather than merely a light one.
     */
    fun surroundingElectrons(graph: MoleculeGraph, atomId: Int): Int =
        2 * bondedAtomCount(graph, atomId) + nonBondingElectrons(graph, atomId).coerceAtLeast(0)

    fun hasVacantOrbital(graph: MoleculeGraph, atomId: Int): Boolean {
        val symbol = graph.atom(atomId).element.symbol
        val full = if (symbol == "H" || symbol == "He") 2 else 8
        return surroundingElectrons(graph, atomId) < full
    }
}
