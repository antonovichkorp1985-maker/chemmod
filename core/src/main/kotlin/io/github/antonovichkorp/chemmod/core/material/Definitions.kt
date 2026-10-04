package io.github.antonovichkorp.chemmod.core.material

import io.github.antonovichkorp.chemmod.core.Molecule

/** A molecular identity. Minerals and bulk materials deliberately use other types. */
data class ChemicalSpecies private constructor(
    val id: SpeciesId,
    val structure: String,
    val canonicalKey: String,
    val formula: String,
    val schemaVersion: Int,
) {
    companion object {
        const val CURRENT_SCHEMA = 1

        fun fromStructure(id: SpeciesId, structure: String): ChemicalSpecies {
            val molecule = Molecule.fromSMILESlike(structure)
            val issues = molecule.validate()
            require(issues.isEmpty()) {
                "Invalid structure for $id: ${issues.joinToString { it.message }}"
            }
            return ChemicalSpecies(
                id = id,
                structure = structure,
                canonicalKey = molecule.canonicalKey(),
                formula = molecule.formula(),
                schemaVersion = CURRENT_SCHEMA,
            )
        }
    }
}

/** A naturally occurring crystalline substance, not a molecule or inventory stack. */
data class MineralDefinition(
    val id: MineralId,
    val chemicalFormula: String,
    val elementStoichiometry: Map<String, Int>,
    val hardnessMohs: Double? = null,
    val schemaVersion: Int = CURRENT_SCHEMA,
) {
    init {
        require(chemicalFormula.isNotBlank()) { "Mineral $id needs a chemical formula" }
        require(elementStoichiometry.isNotEmpty()) { "Mineral $id needs an elemental composition" }
        require(elementStoichiometry.keys.all { it.matches(ELEMENT_SYMBOL) }) {
            "Mineral element keys must be chemical symbols"
        }
        require(elementStoichiometry.values.all { it > 0 }) { "Mineral stoichiometry must be positive" }
        require(hardnessMohs == null || hardnessMohs in 0.0..10.0) { "Mohs hardness must be 0..10" }
        require(schemaVersion > 0) { "Schema version must be positive" }
    }

    companion object {
        const val CURRENT_SCHEMA = 1
        private val ELEMENT_SYMBOL = Regex("[A-Z][a-z]?")
    }
}

/** Canonical world-generation policy for a mixture of minerals. */
data class DepositDefinition(
    val id: DepositId,
    val mineralWeights: Map<MineralId, Int>,
    val dimensionTags: Set<String>,
    val minY: Int,
    val maxY: Int,
    val attemptsPerChunk: Int,
    val schemaVersion: Int = CURRENT_SCHEMA,
) {
    init {
        require(mineralWeights.isNotEmpty()) { "Deposit $id needs at least one mineral" }
        require(mineralWeights.values.all { it > 0 }) { "Mineral weights must be positive" }
        require(dimensionTags.isNotEmpty()) { "Deposit $id needs at least one dimension tag" }
        require(minY <= maxY) { "Deposit minimum Y must not exceed maximum Y" }
        require(attemptsPerChunk > 0) { "Deposit attempts per chunk must be positive" }
        require(schemaVersion > 0) { "Schema version must be positive" }
    }

    companion object { const val CURRENT_SCHEMA = 1 }
}

sealed interface MaterialSource {
    data class Species(val speciesId: SpeciesId) : MaterialSource
    data class Mineral(val mineralId: MineralId) : MaterialSource
    data class Composite(val components: Map<MaterialId, Int>) : MaterialSource {
        init {
            require(components.isNotEmpty()) { "Composite material needs components" }
            require(components.values.all { it > 0 }) { "Composite component weights must be positive" }
        }
    }
}

enum class MaterialForm {
    ORE,
    CRUSHED_ORE,
    DUST,
    INGOT,
    NUGGET,
    BLOCK,
    PLATE,
    ROD,
    WIRE,
    GEAR,
    LIQUID,
    GAS,
    SOLUTION,
}

/** A processable bulk material and the physical forms ChemMod owns for it. */
data class MaterialDefinition(
    val id: MaterialId,
    val source: MaterialSource,
    val supportedForms: Set<MaterialForm>,
    val schemaVersion: Int = CURRENT_SCHEMA,
) {
    init {
        require(supportedForms.isNotEmpty()) { "Material $id needs at least one form" }
        require(schemaVersion > 0) { "Schema version must be positive" }
    }

    companion object { const val CURRENT_SCHEMA = 1 }
}

/** Server-authoritative material state carried by an item, tank, or machine slot. */
data class MaterialBatch(
    val materialId: MaterialId,
    val massMicrograms: Long,
    val purityPpm: Int,
    val impuritiesPpm: Map<MaterialId, Int> = emptyMap(),
    val schemaVersion: Int = CURRENT_SCHEMA,
) {
    init {
        require(massMicrograms > 0) { "Batch mass must be positive" }
        require(purityPpm in 0..PARTS_PER_MILLION) { "Purity must be 0..1,000,000 ppm" }
        require(impuritiesPpm.values.all { it > 0 }) { "Impurity concentrations must be positive" }
        require(impuritiesPpm.keys.none { it == materialId }) { "A material cannot be its own impurity" }
        require(impuritiesPpm.values.sumOf { it.toLong() } <= (PARTS_PER_MILLION - purityPpm).toLong()) {
            "Known impurities exceed the non-primary fraction"
        }
        require(schemaVersion > 0) { "Schema version must be positive" }
    }

    companion object {
        const val CURRENT_SCHEMA = 1
        const val PARTS_PER_MILLION = 1_000_000
    }
}
