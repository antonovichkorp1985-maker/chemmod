package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

data class PredictedProperties(
    val boilingPointC: Double?,
    val molarMass: Double,
    val flags: Set<String>,
    val model: String = "m0-group-contribution-v1",
)

/**
 * Deliberately small, transparent M0 group-contribution model.
 * It is a gameplay predictor, not a reference-property database. Coefficients are
 * frozen by acceptance fixtures from the project prototype and will be expanded
 * from datapack groups in later milestones.
 */
object PropertyPredictor {
    fun predict(graph: MoleculeGraph): PredictedProperties {
        val formula = FormulaCalculator.counts(graph)
        val carbon = formula["C"] ?: 0
        val oxygenAtoms = graph.atoms.filter { it.element.symbol == "O" }

        val boilingPoint = when {
            hasCycle(graph) -> null // M0 has no calibrated ring-strain/group correction yet.
            formula == mapOf("O" to 1, "H" to 2) -> 100.0
            oxygenAtoms.size == 1 && isHydroxyl(graph, oxygenAtoms.single().id) ->
                65.0 - (carbon - 1).coerceAtLeast(0)
            oxygenAtoms.size == 1 && graph.bondsOf(oxygenAtoms.single().id).size == 2 ->
                -2.0 + 20.0 * (carbon - 2).coerceAtLeast(0)
            carbon > 0 && oxygenAtoms.isEmpty() ->
                -161.5 + 48.0 * (carbon - 1) - 3.5 * (carbon - 1) * (carbon - 2)
            else -> null
        }

        val flags = buildSet {
            if (graph.bonds.any { bond ->
                    bond.order == BondOrder.SINGLE &&
                        graph.atom(bond.first).element.symbol == "O" &&
                        graph.atom(bond.second).element.symbol == "O"
                }
            ) add("PEROXIDE_BOND")
            if (graph.atoms.any { it.formalCharge != 0 }) add("FORMAL_CHARGE")
            if (graph.bonds.any { it.order == BondOrder.TRIPLE }) add("TRIPLE_BOND")
        }

        return PredictedProperties(boilingPoint, FormulaCalculator.molarMass(graph), flags)
    }

    private fun isHydroxyl(graph: MoleculeGraph, oxygenId: Int): Boolean =
        graph.bondsOf(oxygenId).size == 1 && graph.implicitHydrogens(oxygenId) + graph.atom(oxygenId).explicitHydrogens > 0

    private fun hasCycle(graph: MoleculeGraph): Boolean {
        val unseen = graph.atoms.map { it.id }.toMutableSet()
        var components = 0
        while (unseen.isNotEmpty()) {
            components++
            val pending = ArrayDeque<Int>()
            pending.add(unseen.first())
            while (pending.isNotEmpty()) {
                val atom = pending.removeFirst()
                if (!unseen.remove(atom)) continue
                graph.neighbors(atom).forEach { (neighbor, _) ->
                    if (neighbor.id in unseen) pending.add(neighbor.id)
                }
            }
        }
        return graph.bonds.size > graph.atoms.size - components
    }
}
