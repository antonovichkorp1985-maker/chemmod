package io.github.antonovichkorp.chemmod.core.catalysis

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.material.MaterialCatalog
import io.github.antonovichkorp.chemmod.core.material.MaterialId
import io.github.antonovichkorp.chemmod.core.material.MaterialSource
import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val CAPABILITY_ID_PATTERN = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")
private val ELEMENT_SYMBOL_PATTERN = Regex("[A-Z][a-z]?")

/**
 * One catalytic capability and the composition that earns it. A capability is never granted
 * to a named item or substance: it is derived from the elements that are actually present.
 */
data class CatalystCapabilityRule(
    val id: String,
    val requiredElements: Set<String> = emptySet(),
    val anyElements: Set<String> = emptySet(),
) {
    init {
        require(CAPABILITY_ID_PATTERN.matches(id)) {
            "Catalyst capability ID must use namespace:path, got '$id'"
        }
        require((requiredElements + anyElements).all { ELEMENT_SYMBOL_PATTERN.matches(it) }) {
            "Catalyst capability '$id' must list element symbols"
        }
        require(requiredElements.isNotEmpty() || anyElements.isNotEmpty()) {
            "Catalyst capability '$id' needs at least one element condition"
        }
    }

    fun isEarnedBy(elements: Set<String>): Boolean =
        requiredElements.all { it in elements } &&
            (anyElements.isEmpty() || anyElements.any { it in elements })
}

/**
 * Composition → catalytic capabilities. The same derivation serves bulk materials (from their
 * mineral stoichiometry) and molecular substances (from their graph), so a rule can require a
 * capability instead of a hand-maintained list of acceptable items.
 */
class CatalystModel(rules: List<CatalystCapabilityRule>) {
    private val byId = rules.associateBy { it.id }

    init {
        require(byId.size == rules.size) { "Catalyst model has duplicate capability IDs" }
    }

    val capabilities: Set<String> = byId.keys

    fun earnedBy(elements: Set<String>): Set<String> =
        byId.values.filter { it.isEarnedBy(elements) }.map { it.id }.toSet()

    /** Elements of a bulk material, following composite components recursively. */
    fun elementsOfMaterial(materialId: String, catalog: MaterialCatalog = MaterialCatalog.bundled()): Set<String> =
        elementsOfMaterial(MaterialId.of(materialId), catalog, mutableSetOf())

    private fun elementsOfMaterial(
        materialId: MaterialId,
        catalog: MaterialCatalog,
        visiting: MutableSet<MaterialId>,
    ): Set<String> {
        val material = catalog.materials[materialId] ?: return emptySet()
        if (!visiting.add(materialId)) return emptySet()
        return when (val source = material.source) {
            is MaterialSource.Mineral ->
                catalog.minerals[source.mineralId]?.elementStoichiometry?.keys.orEmpty()
            is MaterialSource.Species ->
                catalog.species[source.speciesId]?.let { elementSymbols(it.structure) }.orEmpty()
            is MaterialSource.Composite -> source.components.keys.flatMap { component ->
                elementsOfMaterial(component, catalog, visiting)
            }.toSet()
        }
    }

    private fun elementSymbols(structure: String): Set<String> =
        FormulaCalculator.counts(Molecule.fromSMILESlike(structure).graph).keys

    companion object {
        const val CURRENT_SCHEMA = 1

        fun fromJson(input: InputStream): CatalystModel {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString(CatalystModelDocument.serializer(), text)
            require(document.schemaVersion == CURRENT_SCHEMA) {
                "Unsupported catalyst model schema: ${document.schemaVersion}"
            }
            return CatalystModel(document.capabilities.map { it.toDomain() })
        }

        @JvmStatic
        fun bundled(): CatalystModel {
            val stream = CatalystModel::class.java.getResourceAsStream("/chemmod/catalysis/default.json")
                ?: error("Bundled catalyst model is missing")
            return stream.use(::fromJson)
        }

        /** Java-friendly entry point: capabilities of a bulk material by its content ID. */
        @JvmStatic
        @JvmOverloads
        fun capabilitiesOfMaterial(materialId: String, catalog: MaterialCatalog = MaterialCatalog.bundled()): Set<String> =
            bundled().earnedBy(bundled().elementsOfMaterial(materialId, catalog))

        /** Java-friendly entry point: capabilities of a molecular substance by its structure. */
        @JvmStatic
        fun capabilitiesOfStructure(structure: String): Set<String> =
            bundled().earnedBy(elementSymbols(structure))

        private fun elementSymbols(structure: String): Set<String> =
            FormulaCalculator.counts(Molecule.fromSMILESlike(structure).graph).keys

        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }
    }
}

@Serializable
private data class CatalystModelDocument(
    val schemaVersion: Int,
    val capabilities: List<CatalystCapabilityDocument>,
)

@Serializable
private data class CatalystCapabilityDocument(
    val id: String,
    val requiredElements: Set<String> = emptySet(),
    val anyElements: Set<String> = emptySet(),
) {
    fun toDomain() = CatalystCapabilityRule(id, requiredElements, anyElements)
}
