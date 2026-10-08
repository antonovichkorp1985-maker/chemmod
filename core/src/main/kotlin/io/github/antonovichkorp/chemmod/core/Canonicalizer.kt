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

        val atomPart = labels.values.sorted().joinToString(";")
        val bondPart = graph.bonds.map { bond ->
            val endpoints = listOf(labels.getValue(bond.first), labels.getValue(bond.second)).sorted()
            "${endpoints[0]}:${bond.order.value}:${endpoints[1]}"
        }.sorted().joinToString(";")
        return digest("$atomPart|$bondPart")
    }

    fun canonicalId(graph: MoleculeGraph): Long = canonicalKey(graph).take(16).toULong(16).toLong()

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
