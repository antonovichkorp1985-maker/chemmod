package io.github.antonovichkorp.chemmod.core.material

import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Validated, immutable source of truth consumed by game and optional-mod adapters. */
class MaterialCatalog private constructor(
    val schemaVersion: Int,
    species: List<ChemicalSpecies>,
    minerals: List<MineralDefinition>,
    deposits: List<DepositDefinition>,
    materials: List<MaterialDefinition>,
    processes: List<ProcessDefinition>,
) {
    val species: Map<SpeciesId, ChemicalSpecies> = uniqueById("species", species) { it.id }
    val minerals: Map<MineralId, MineralDefinition> = uniqueById("minerals", minerals) { it.id }
    val deposits: Map<DepositId, DepositDefinition> = uniqueById("deposits", deposits) { it.id }
    val materials: Map<MaterialId, MaterialDefinition> = uniqueById("materials", materials) { it.id }
    val processes: Map<ProcessId, ProcessDefinition> = uniqueById("processes", processes) { it.id }
    val compiledProcesses: Map<ProcessId, CompiledProcess>

    init {
        require(schemaVersion == CURRENT_SCHEMA) { "Unsupported material catalog schema: $schemaVersion" }
        validateReferences()
        validateCompositeCycles()
        val compiler = ProcessCompiler(this.materials.values)
        compiledProcesses = this.processes.mapValues { (_, process) -> compiler.compile(process) }
    }

    private fun validateReferences() {
        deposits.values.forEach { deposit ->
            deposit.mineralWeights.keys.forEach { mineralId ->
                require(mineralId in minerals) { "Deposit ${deposit.id} references unknown mineral $mineralId" }
            }
        }
        materials.values.forEach { material ->
            when (val source = material.source) {
                is MaterialSource.Species -> require(source.speciesId in species) {
                    "Material ${material.id} references unknown species ${source.speciesId}"
                }
                is MaterialSource.Mineral -> require(source.mineralId in minerals) {
                    "Material ${material.id} references unknown mineral ${source.mineralId}"
                }
                is MaterialSource.Composite -> source.components.keys.forEach { componentId ->
                    require(componentId in materials) {
                        "Material ${material.id} references unknown component $componentId"
                    }
                    require(componentId != material.id) { "Material ${material.id} directly contains itself" }
                }
            }
        }
    }

    private fun validateCompositeCycles() {
        val visiting = mutableSetOf<MaterialId>()
        val visited = mutableSetOf<MaterialId>()

        fun visit(id: MaterialId) {
            if (id in visited) return
            require(visiting.add(id)) { "Composite material cycle contains $id" }
            val source = materials.getValue(id).source
            if (source is MaterialSource.Composite) source.components.keys.forEach(::visit)
            visiting.remove(id)
            visited.add(id)
        }

        materials.keys.forEach(::visit)
    }

    companion object {
        const val CURRENT_SCHEMA = 1

        fun fromJson(input: InputStream): MaterialCatalog {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString(CatalogDocument.serializer(), text)
            require(document.schemaVersion == CURRENT_SCHEMA) {
                "Unsupported material catalog schema: ${document.schemaVersion}"
            }
            return MaterialCatalog(
                schemaVersion = document.schemaVersion,
                species = document.species.map { it.toDomain() },
                minerals = document.minerals.map { it.toDomain() },
                deposits = document.deposits.map { it.toDomain() },
                materials = document.materials.map { it.toDomain() },
                processes = document.processes.map { it.toDomain() },
            )
        }

        fun default(): MaterialCatalog {
            val stream = MaterialCatalog::class.java.getResourceAsStream("/chemmod/materials/catalog.json")
                ?: error("Bundled material catalog is missing")
            return stream.use(::fromJson)
        }

        private val JSON = Json {
            ignoreUnknownKeys = false
            isLenient = false
        }

        private fun <K, V> uniqueById(label: String, values: List<V>, key: (V) -> K): Map<K, V> {
            val result = values.associateBy(key)
            require(result.size == values.size) { "Material catalog contains duplicate $label IDs" }
            return result
        }
    }
}

@Serializable
private data class CatalogDocument(
    val schemaVersion: Int,
    val species: List<SpeciesDocument> = emptyList(),
    val minerals: List<MineralDocument> = emptyList(),
    val deposits: List<DepositDocument> = emptyList(),
    val materials: List<MaterialDocument> = emptyList(),
    val processes: List<ProcessDocument> = emptyList(),
)

@Serializable
private data class SpeciesDocument(val id: String, val structure: String) {
    fun toDomain(): ChemicalSpecies = ChemicalSpecies.fromStructure(SpeciesId.of(id), structure)
}

@Serializable
private data class MineralDocument(
    val id: String,
    val chemicalFormula: String,
    val elementStoichiometry: Map<String, Int>,
    val hardnessMohs: Double? = null,
) {
    fun toDomain() = MineralDefinition(
        id = MineralId.of(id),
        chemicalFormula = chemicalFormula,
        elementStoichiometry = elementStoichiometry,
        hardnessMohs = hardnessMohs,
    )
}

@Serializable
private data class DepositDocument(
    val id: String,
    val mineralWeights: Map<String, Int>,
    val dimensionTags: Set<String>,
    val minY: Int,
    val maxY: Int,
    val attemptsPerChunk: Int,
) {
    fun toDomain() = DepositDefinition(
        id = DepositId.of(id),
        mineralWeights = mineralWeights.mapKeys { MineralId.of(it.key) },
        dimensionTags = dimensionTags,
        minY = minY,
        maxY = maxY,
        attemptsPerChunk = attemptsPerChunk,
    )
}

@Serializable
private data class MaterialDocument(
    val id: String,
    val source: MaterialSourceDocument,
    val supportedForms: Set<String>,
) {
    fun toDomain() = MaterialDefinition(
        id = MaterialId.of(id),
        source = source.toDomain(),
        supportedForms = supportedForms.mapTo(linkedSetOf()) { value ->
            enumValue<MaterialForm>(value, "material form")
        },
    )
}

@Serializable
private data class MaterialSourceDocument(
    val type: String,
    val id: String? = null,
    val components: Map<String, Int> = emptyMap(),
) {
    fun toDomain(): MaterialSource = when (type) {
        "species" -> MaterialSource.Species(SpeciesId.of(requireNotNull(id) { "Species source needs an ID" }))
        "mineral" -> MaterialSource.Mineral(MineralId.of(requireNotNull(id) { "Mineral source needs an ID" }))
        "composite" -> MaterialSource.Composite(components.mapKeys { MaterialId.of(it.key) })
        else -> throw IllegalArgumentException("Unknown material source type '$type'")
    }
}

@Serializable
private data class ProcessDocument(
    val id: String,
    val machineTag: String,
    val inputs: List<ProcessStackDocument>,
    val outputs: List<ProcessStackDocument>,
    val durationTicks: Int,
    val conditions: ProcessConditionsDocument = ProcessConditionsDocument(),
) {
    fun toDomain() = ProcessDefinition(
        id = ProcessId.of(id),
        machineTag = machineTag,
        inputs = inputs.map { it.toDomain() },
        outputs = outputs.map { it.toDomain() },
        durationTicks = durationTicks,
        conditions = conditions.toDomain(),
    )
}

@Serializable
private data class ProcessStackDocument(
    val material: String,
    val form: String,
    val massMicrograms: Long,
) {
    fun toDomain() = ProcessStack(
        materialId = MaterialId.of(material),
        form = enumValue(form, "process material form"),
        massMicrograms = massMicrograms,
    )
}

@Serializable
private data class ProcessConditionsDocument(
    val minimumTemperatureKelvin: Double? = null,
    val maximumTemperatureKelvin: Double? = null,
    val minimumPressureKilopascals: Double? = null,
) {
    fun toDomain() = ProcessConditions(
        minimumTemperatureKelvin = minimumTemperatureKelvin,
        maximumTemperatureKelvin = maximumTemperatureKelvin,
        minimumPressureKilopascals = minimumPressureKilopascals,
    )
}

private inline fun <reified T : Enum<T>> enumValue(value: String, label: String): T =
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw IllegalArgumentException("Unknown $label '$value'")
