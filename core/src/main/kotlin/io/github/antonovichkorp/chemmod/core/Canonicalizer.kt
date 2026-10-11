package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Morgan-style iterative graph fingerprint used as an order-independent M0 identity.
 * The full digest is persisted; the Long form is only a compact display/API value.
 */
object MoleculeCanonicalizer {
    fun canonicalKey(graph: MoleculeGraph): String {
        val labels = morganLabels(graph)
        val atomPart = labels.values.sorted().joinToString(";")
        val bondPart = graph.bonds.map { bond ->
            val endpoints = listOf(labels.getValue(bond.first), labels.getValue(bond.second)).sorted()
            "${endpoints[0]}:${bond.order.value}:${endpoints[1]}"
        }.sorted().joinToString(";")
        return digest("$atomPart|$bondPart")
    }

    /**
     * The converged Morgan label of every atom: a hash of what the atom is and, after iteration,
     * of the whole fragment hanging off it. Two substituents are the same substituent exactly when
     * their labels match, which is how ring symmetry is recognised without enumerating paths — both
     * ring bonds of cyclohexanol carry the same label, so its carbon is not a stereocentre.
     *
     * Exposed because identity is not the only thing that needs to compare fragments: deciding
     * whether a centre is stereogenic is the same comparison.
     */
    fun morganLabels(graph: MoleculeGraph): Map<Int, String> {
        var labels = graph.atoms.associate { atom ->
            atom.id to digest(
                listOf(
                    atom.element.atomicNumber,
                    atom.formalCharge,
                    atom.explicitHydrogens,
                    graph.implicitHydrogens(atom.id),
                    graph.bondsOf(atom.id).size,
                    graph.bondOrderSum(atom.id),
                ).joinToString(":"),
            )
        }

        repeat(maxOf(graph.atoms.size, 1)) {
            labels = graph.atoms.associate { atom ->
                val neighborhood = graph.neighbors(atom.id)
                    .map { (neighbor, order) -> "${order.value}:${labels.getValue(neighbor.id)}" }
                    .sorted()
                    .joinToString(",")
                atom.id to digest("${labels.getValue(atom.id)}|$neighborhood")
            }
        }
        return labels
    }

    fun canonicalId(graph: MoleculeGraph): Long = canonicalKey(graph).take(16).toULong(16).toLong()

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
