package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.naming.TrivialNameDirectory

/**
 * Compatibility facade for commands and old callers. This directory resolves
 * only human names; it never controls the existence of a molecular structure.
 */
data class CommonSubstance(
    val canonicalName: String,
    val structure: String,
)

object CommonSubstances {
    private val directory: TrivialNameDirectory by lazy(TrivialNameDirectory::bundled)

    fun find(input: String): CommonSubstance? = directory.find(input)?.let {
        CommonSubstance(it.canonicalName, it.structure)
    }

    fun findByStructure(structure: String): CommonSubstance? =
        findByCanonicalKey(Molecule.fromSMILESlike(structure).canonicalKey())

    fun findByCanonicalKey(canonicalKey: String): CommonSubstance? = directory.findByCanonicalKey(canonicalKey)?.let {
        CommonSubstance(it.canonicalName, it.structure)
    }

    fun resolve(input: String): String = find(input)?.structure ?: input

    /** Every labelled substance, in directory order. Used for calibration lists, never for identity. */
    fun all(): List<CommonSubstance> = directory.all().map {
        CommonSubstance(it.canonicalName, it.structure)
    }
}
