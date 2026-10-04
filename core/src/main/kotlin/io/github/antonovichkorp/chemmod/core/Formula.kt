package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

object FormulaCalculator {
    fun counts(graph: MoleculeGraph): Map<String, Int> {
        val counts = linkedMapOf<String, Int>()
        graph.atoms.forEach { atom ->
            counts[atom.element.symbol] = counts.getOrDefault(atom.element.symbol, 0) + 1
            val hydrogenCount = atom.explicitHydrogens + graph.implicitHydrogens(atom.id)
            if (hydrogenCount > 0) counts["H"] = counts.getOrDefault("H", 0) + hydrogenCount
        }
        return counts
    }

    fun hillFormula(graph: MoleculeGraph): String {
        val counts = counts(graph)
        val order = when {
            "C" in counts -> listOf("C", "H") + counts.keys.filterNot { it == "C" || it == "H" }.sorted()
            else -> counts.keys.sorted()
        }
        return order.filter { it in counts }.joinToString("") { symbol ->
            val count = counts.getValue(symbol)
            symbol + if (count == 1) "" else count
        }
    }

    fun molarMass(graph: MoleculeGraph): Double {
        val elements = graph.atoms.associate { it.element.symbol to it.element }
        val hydrogen = graph.atoms.firstOrNull { it.element.symbol == "H" }?.element
            ?: io.github.antonovichkorp.chemmod.core.model.ElementTable.default().require("H")
        return counts(graph).entries.sumOf { (symbol, count) ->
            (elements[symbol] ?: if (symbol == "H") hydrogen else error("Missing element $symbol")).atomicMass * count
        }
    }
}
