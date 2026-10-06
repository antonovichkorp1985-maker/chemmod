package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.properties.StructuralProperties

/**
 * A substance identity derived from any valid molecular graph.
 *
 * This is deliberately independent of [ChemicalCatalog]. A catalog entry gives
 * an identity a game-facing name, aliases and approved processes; it never
 * decides whether an otherwise valid molecular structure exists in the core.
 */
class MolecularSubstance private constructor(
    /** Original structural notation, retained for round-tripping and diagnostics. */
    val structure: String,
    val molecule: Molecule,
    val canonicalKey: String,
    val properties: StructuralProperties,
) {
    val canonicalId: Long
        get() = molecule.canonicalId()

    override fun equals(other: Any?): Boolean =
        other is MolecularSubstance && canonicalKey == other.canonicalKey

    override fun hashCode(): Int = canonicalKey.hashCode()

    override fun toString(): String = properties.formula

    companion object {
        fun fromStructure(structure: String): MolecularSubstance {
            require(structure.isNotBlank()) { "A molecular structure cannot be blank" }
            val molecule = Molecule.fromSMILESlike(structure)
            val issues = molecule.validate()
            require(issues.isEmpty()) {
                "Invalid molecular structure: ${issues.joinToString { it.message }}"
            }
            return MolecularSubstance(
                structure = structure,
                molecule = molecule,
                canonicalKey = molecule.canonicalKey(),
                properties = molecule.structuralProperties(),
            )
        }
    }
}
