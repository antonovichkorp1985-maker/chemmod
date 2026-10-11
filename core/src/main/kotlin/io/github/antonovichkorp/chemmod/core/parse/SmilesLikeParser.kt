package io.github.antonovichkorp.chemmod.core.parse

import io.github.antonovichkorp.chemmod.core.model.Atom
import io.github.antonovichkorp.chemmod.core.model.Bond
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.ElementTable
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
import java.util.ArrayDeque

class MoleculeParseException(message: String, val position: Int) :
    IllegalArgumentException("$message at position $position")

class SmilesLikeParser(private val elements: ElementTable = ElementTable.default()) {
    fun parse(input: String): MoleculeGraph {
        if (input.isBlank()) throw MoleculeParseException("Molecule cannot be empty", 0)

        val atoms = mutableListOf<Atom>()
        val bonds = mutableListOf<Bond>()
        val branches = ArrayDeque<Int>()
        val rings = mutableMapOf<Char, RingStart>()
        var current: Int? = null
        var pendingBond: BondOrder? = null
        var index = 0

        fun attach(atom: Atom) {
            atoms += atom
            current?.let { bonds += Bond(it, atom.id, pendingBond ?: BondOrder.SINGLE) }
            current = atom.id
            pendingBond = null
        }

        while (index < input.length) {
            when (val char = input[index]) {
                '-', '=', '#' -> {
                    if (current == null || pendingBond != null) fail("Unexpected bond '$char'", index)
                    pendingBond = when (char) {
                        '-' -> BondOrder.SINGLE
                        '=' -> BondOrder.DOUBLE
                        else -> BondOrder.TRIPLE
                    }
                    index++
                }
                '(' -> {
                    val atom = current ?: fail("Branch has no parent atom", index)
                    if (pendingBond != null) fail("Bond must be followed by an atom", index)
                    branches.addLast(atom)
                    index++
                }
                ')' -> {
                    if (pendingBond != null) fail("Bond must be followed by an atom", index)
                    current = branches.pollLast() ?: fail("Unmatched ')'", index)
                    index++
                }
                '.' -> {
                    if (pendingBond != null) fail("Bond must be followed by an atom", index)
                    current = null
                    index++
                }
                in '0'..'9' -> {
                    val atom = current ?: fail("Ring closure has no atom", index)
                    val start = rings.remove(char)
                    if (start == null) {
                        rings[char] = RingStart(atom, pendingBond)
                    } else {
                        val order = when {
                            start.order != null && pendingBond != null && start.order != pendingBond ->
                                fail("Conflicting ring bond orders", index)
                            else -> pendingBond ?: start.order ?: BondOrder.SINGLE
                        }
                        bonds += Bond(start.atomId, atom, order)
                    }
                    pendingBond = null
                    index++
                }
                '[' -> {
                    val close = input.indexOf(']', index + 1)
                    if (close < 0) fail("Unclosed bracket atom", index)
                    val token = input.substring(index + 1, close)
                    attach(parseBracketAtom(token, atoms.size, index))
                    index = close + 1
                }
                else -> {
                    if (!char.isUpperCase()) {
                        val hint = if (char.isLowerCase()) "Aromatic atoms are not supported in M0" else "Unexpected '$char'"
                        fail(hint, index)
                    }
                    val twoChars = input.substring(index, minOf(index + 2, input.length))
                    val symbol = if (twoChars.length == 2 && elements.find(twoChars) != null) twoChars else char.toString()
                    val element = elements.find(symbol) ?: fail("Unknown element '$symbol'", index)
                    attach(Atom(atoms.size, element))
                    index += symbol.length
                }
            }
        }

        if (pendingBond != null) fail("Trailing bond", input.length - 1)
        if (branches.isNotEmpty()) fail("Unclosed branch", input.length)
        if (rings.isNotEmpty()) fail("Unclosed ring '${rings.keys.first()}'", input.length)
        return MoleculeGraph(atoms, bonds)
    }

    private fun parseBracketAtom(token: String, id: Int, position: Int): Atom {
        val match = BRACKET_PATTERN.matchEntire(token)
            ?: fail("Unsupported bracket atom '[$token]'", position)
        val symbol = match.groupValues[1]
        val element = elements.find(symbol) ?: fail("Unknown element '$symbol'", position)
        val hydrogens = when {
            match.groupValues[2].isEmpty() -> 0
            match.groupValues[3].isEmpty() -> 1
            else -> match.groupValues[3].toInt()
        }
        val charge = when (match.groupValues[4]) {
            "+" -> match.groupValues[5].ifEmpty { "1" }.toInt()
            "-" -> -match.groupValues[5].ifEmpty { "1" }.toInt()
            else -> 0
        }
        return Atom(id, element, charge, hydrogens)
    }

    private fun fail(message: String, position: Int): Nothing = throw MoleculeParseException(message, position)

    private data class RingStart(val atomId: Int, val order: BondOrder?)

    companion object {
        private val BRACKET_PATTERN = Regex("([A-Z][a-z]?)(H(\\d*)?)?([+-](\\d*)?)?")
    }
}
