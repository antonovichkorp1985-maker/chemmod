package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

data class ValidationIssue(
    val atomId: Int?,
    val code: String,
    val message: String,
)

object MoleculeValidator {
    fun validate(graph: MoleculeGraph): List<ValidationIssue> = buildList {
        if (graph.atoms.isEmpty()) {
            add(ValidationIssue(null, "EMPTY", "A molecule must contain at least one atom"))
        }

        graph.atoms.forEach { atom ->
            val occupied = graph.bondOrderSum(atom.id) + atom.explicitHydrogens
            val maxValence = when {
                atom.element.symbol == "N" && atom.formalCharge > 0 -> maxOf(atom.element.valences.max(), 4)
                atom.element.symbol == "O" && atom.formalCharge > 0 -> maxOf(atom.element.valences.max(), 3)
                else -> atom.element.valences.max()
            }
            if (occupied > maxValence) {
                add(
                    ValidationIssue(
                        atom.id,
                        "VALENCE_EXCEEDED",
                        "${atom.element.symbol} atom ${atom.id} uses valence $occupied, maximum is $maxValence",
                    ),
                )
            }
            if (atom.formalCharge !in -2..2) {
                add(
                    ValidationIssue(
                        atom.id,
                        "CHARGE_OUT_OF_RANGE",
                        "Formal charge ${atom.formalCharge} is outside the supported M0 range -2..2",
                    ),
                )
            }
        }
    }
}
