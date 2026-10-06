package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.chemistry.ChemicalCatalog
import io.github.antonovichkorp.chemmod.core.chemistry.ChemicalSubstance

/**
 * Compatibility input facade for commands and legacy callers. Molecular names,
 * aliases and structures now have one authoritative data source in the
 * declarative chemistry catalog rather than a second hand-written list.
 */
data class CommonSubstance(
    val canonicalName: String,
    val structure: String,
)

object CommonSubstances {
    private val catalog: ChemicalCatalog by lazy(ChemicalCatalog::bundled)

    fun find(input: String): CommonSubstance? = catalog.findSubstance(input)?.asCommon()

    fun findByStructure(structure: String): CommonSubstance? =
        catalog.substances.values.firstOrNull { it.structure == structure }?.asCommon()

    fun resolve(input: String): String = find(input)?.structure ?: input

    private fun ChemicalSubstance.asCommon(): CommonSubstance = CommonSubstance(id.path, structure)
}
