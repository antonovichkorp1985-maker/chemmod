package io.github.antonovichkorp.chemmod.core.model

data class Atom(
    val id: Int,
    val element: Element,
    val formalCharge: Int = 0,
    val explicitHydrogens: Int = 0,
)

enum class BondOrder(val value: Int, val symbol: String) {
    SINGLE(1, "-"),
    DOUBLE(2, "="),
    TRIPLE(3, "#"),
}

data class Bond(
    val first: Int,
    val second: Int,
    val order: BondOrder,
) {
    init {
        require(first != second) { "An atom cannot be bonded to itself" }
    }

    fun other(atomId: Int): Int = when (atomId) {
        first -> second
        second -> first
        else -> throw IllegalArgumentException("Atom $atomId is not part of this bond")
    }
}

class MoleculeGraph(
    atoms: List<Atom>,
    bonds: List<Bond>,
) {
    val atoms: List<Atom> = atoms.toList()
    val bonds: List<Bond> = bonds.toList()
    private val atomsById = this.atoms.associateBy(Atom::id)
    private val bondsByAtom = this.atoms.associate { atom ->
        atom.id to this.bonds.filter { it.first == atom.id || it.second == atom.id }
    }

    init {
        require(atomsById.size == this.atoms.size) { "Atom IDs must be unique" }
        require(this.bonds.all { it.first in atomsById && it.second in atomsById }) {
            "Every bond endpoint must refer to an atom"
        }
        require(this.bonds.map { setOf(it.first, it.second) }.distinct().size == this.bonds.size) {
            "Only one bond is allowed between two atoms"
        }
    }

    fun atom(id: Int): Atom = atomsById[id] ?: throw IllegalArgumentException("Unknown atom $id")

    fun bondsOf(atomId: Int): List<Bond> = bondsByAtom[atomId].orEmpty()

    fun neighbors(atomId: Int): List<Pair<Atom, BondOrder>> =
        bondsOf(atomId).map { atom(it.other(atomId)) to it.order }

    fun bondOrderSum(atomId: Int): Int = bondsOf(atomId).sumOf { it.order.value }

    fun implicitHydrogens(atomId: Int): Int {
        val atom = atom(atomId)
        if (atom.element.symbol == "H") return 0
        val occupied = bondOrderSum(atomId) + atom.explicitHydrogens
        val valences = effectiveValences(atom).filter { it >= occupied }
        return ((valences.minOrNull() ?: occupied) - occupied).coerceAtLeast(0)
    }

    private fun effectiveValences(atom: Atom): List<Int> = when {
        atom.element.symbol == "N" && atom.formalCharge > 0 -> (atom.element.valences + 4).distinct()
        atom.element.symbol == "O" && atom.formalCharge > 0 -> (atom.element.valences + 3).distinct()
        atom.element.symbol == "O" && atom.formalCharge < 0 -> (atom.element.valences + 1).distinct()
        else -> atom.element.valences
    }
}
