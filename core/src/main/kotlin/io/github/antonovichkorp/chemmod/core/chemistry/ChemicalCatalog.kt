package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.properties.StructuralProperties
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBalancer
import java.io.InputStream
import java.math.BigInteger
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Declarative registry of named/approved chemistry before mixtures or machines.
 * It does not own molecular existence: [MolecularSubstance] can be created from
 * any valid graph. The catalog adds player-facing names, aliases and explicitly
 * declared gameplay reactions to selected identities.
 */
class ChemicalCatalog private constructor(
    val schemaVersion: Int,
    substances: List<ChemicalSubstance>,
    reactions: List<ChemicalReaction>,
) {
    val substances: Map<SubstanceId, ChemicalSubstance> = uniqueById("substances", substances) { it.id }
    val reactions: Map<ChemicalReactionId, ChemicalReaction> = uniqueById("reactions", reactions) { it.id }
    private val aliases: Map<String, SubstanceId>

    init {
        require(schemaVersion == CURRENT_SCHEMA) { "Unsupported chemistry catalog schema: $schemaVersion" }
        val canonicalKeys = substances.groupBy { it.canonicalKey }.filterValues { it.size > 1 }
        require(canonicalKeys.isEmpty()) {
            "Several substances declare the same molecular identity: " +
                canonicalKeys.values.joinToString { entries -> entries.joinToString { it.id.toString() } }
        }
        aliases = buildAliasIndex()
        this.reactions.values.forEach(::validateReaction)
    }

    fun findSubstance(input: String): ChemicalSubstance? = aliases[normalizeAlias(input)]?.let(substances::get)

    fun substance(id: SubstanceId): ChemicalSubstance =
        requireNotNull(substances[id]) { "Unknown substance $id" }

    fun formatEquation(reaction: ChemicalReaction): String =
        formatSide(reaction.reactants) + " -> " + formatSide(reaction.products)

    private fun buildAliasIndex(): Map<String, SubstanceId> {
        val index = linkedMapOf<String, SubstanceId>()
        substances.values.forEach { substance ->
            (substance.aliases + substance.id.path).forEach { alias ->
                val normalized = normalizeAlias(alias)
                require(normalized.isNotBlank()) { "Substance ${substance.id} has an empty alias" }
                val existing = index.putIfAbsent(normalized, substance.id)
                require(existing == null || existing == substance.id) {
                    "Alias '$alias' is shared by $existing and ${substance.id}"
                }
            }
        }
        return index.toMap()
    }

    private fun validateReaction(reaction: ChemicalReaction) {
        require(reaction.reactants.map { it.substanceId }.distinct().size == reaction.reactants.size) {
            "Reaction ${reaction.id} repeats a reactant"
        }
        require(reaction.products.map { it.substanceId }.distinct().size == reaction.products.size) {
            "Reaction ${reaction.id} repeats a product"
        }
        require(reaction.reactants.map { it.substanceId }.intersect(reaction.products.map { it.substanceId }.toSet()).isEmpty()) {
            "Reaction ${reaction.id} contains a substance on both sides"
        }
        val reactants = reaction.reactants.map { term -> substance(term.substanceId).molecule }
        val products = reaction.products.map { term -> substance(term.substanceId).molecule }
        val balanced = ReactionBalancer.balance(reactants, products)
        val expectedReactants = reaction.reactants.map { BigInteger.valueOf(it.coefficient) }
        val expectedProducts = reaction.products.map { BigInteger.valueOf(it.coefficient) }
        require(balanced.reactantCoefficients == expectedReactants && balanced.productCoefficients == expectedProducts) {
            "Reaction ${reaction.id} is not expressed using the minimal conserved coefficients; expected " +
                "${balanced.reactantCoefficients} -> ${balanced.productCoefficients}"
        }
    }

    private fun formatSide(terms: List<ReactionTerm>): String = terms.joinToString(" + ") { term ->
        val coefficient = if (term.coefficient == 1L) "" else "${term.coefficient} "
        coefficient + substance(term.substanceId).properties.formula
    }

    companion object {
        const val CURRENT_SCHEMA = 1

        fun fromJson(input: InputStream): ChemicalCatalog {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString(ChemicalCatalogDocument.serializer(), text)
            return ChemicalCatalog(
                schemaVersion = document.schemaVersion,
                substances = document.substances.map { it.toDomain() },
                reactions = document.reactions.map { it.toDomain() },
            )
        }

        @JvmStatic
        fun bundled(): ChemicalCatalog {
            val stream = ChemicalCatalog::class.java.getResourceAsStream("/chemmod/chemistry/catalog.json")
                ?: error("Bundled chemistry catalog is missing")
            return stream.use(::fromJson)
        }

        private val JSON = Json {
            ignoreUnknownKeys = false
            isLenient = false
        }

        private fun <K, V> uniqueById(label: String, values: List<V>, key: (V) -> K): Map<K, V> {
            val result = values.associateBy(key)
            require(result.size == values.size) { "Chemistry catalog contains duplicate $label IDs" }
            return result
        }
    }
}

/** A game-facing name and aliases for an otherwise catalog-independent molecular identity. */
class ChemicalSubstance private constructor(
    val id: SubstanceId,
    val aliases: Set<String>,
    val molecular: MolecularSubstance,
) {
    val structure: String
        get() = molecular.structure
    val molecule: Molecule
        get() = molecular.molecule
    val canonicalKey: String
        get() = molecular.canonicalKey
    val properties: StructuralProperties
        get() = molecular.properties

    companion object {
        fun fromStructure(id: SubstanceId, aliases: Set<String>, structure: String): ChemicalSubstance =
            ChemicalSubstance(id, aliases.toSet(), MolecularSubstance.fromStructure(structure))
    }
}

data class ReactionTerm(
    val substanceId: SubstanceId,
    val coefficient: Long,
) {
    init {
        require(coefficient > 0) { "Reaction coefficients must be positive" }
    }
}

data class ReactionConditions(
    val minimumTemperatureKelvin: Double? = null,
    val maximumTemperatureKelvin: Double? = null,
    val minimumPressureKilopascals: Double? = null,
    val catalystTags: Set<String> = emptySet(),
) {
    init {
        listOfNotNull(minimumTemperatureKelvin, maximumTemperatureKelvin, minimumPressureKilopascals).forEach {
            require(it.isFinite() && it > 0.0) { "Reaction conditions must be finite and positive" }
        }
        require(minimumTemperatureKelvin == null || maximumTemperatureKelvin == null ||
            minimumTemperatureKelvin <= maximumTemperatureKelvin
        ) { "Minimum reaction temperature cannot exceed maximum temperature" }
        require(catalystTags.all { it.matches(Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")) }) {
            "Catalyst tags must use namespace:path"
        }
    }
}

data class ChemicalReaction(
    val id: ChemicalReactionId,
    val reactants: List<ReactionTerm>,
    val products: List<ReactionTerm>,
    val conditions: ReactionConditions = ReactionConditions(),
) {
    init {
        require(reactants.isNotEmpty()) { "Reaction $id needs a reactant" }
        require(products.isNotEmpty()) { "Reaction $id needs a product" }
    }
}

@Serializable
private data class ChemicalCatalogDocument(
    val schemaVersion: Int,
    val substances: List<SubstanceDocument>,
    val reactions: List<ReactionDocument>,
)

@Serializable
private data class SubstanceDocument(
    val id: String,
    val aliases: Set<String> = emptySet(),
    val structure: String,
) {
    fun toDomain(): ChemicalSubstance = ChemicalSubstance.fromStructure(SubstanceId.of(id), aliases, structure)
}

@Serializable
private data class ReactionDocument(
    val id: String,
    val reactants: List<ReactionTermDocument>,
    val products: List<ReactionTermDocument>,
    val conditions: ReactionConditionsDocument = ReactionConditionsDocument(),
) {
    fun toDomain(): ChemicalReaction = ChemicalReaction(
        id = ChemicalReactionId.of(id),
        reactants = reactants.map { it.toDomain() },
        products = products.map { it.toDomain() },
        conditions = conditions.toDomain(),
    )
}

@Serializable
private data class ReactionTermDocument(val substance: String, val coefficient: Long) {
    fun toDomain() = ReactionTerm(SubstanceId.of(substance), coefficient)
}

@Serializable
private data class ReactionConditionsDocument(
    val minimumTemperatureKelvin: Double? = null,
    val maximumTemperatureKelvin: Double? = null,
    val minimumPressureKilopascals: Double? = null,
    val catalystTags: Set<String> = emptySet(),
) {
    fun toDomain() = ReactionConditions(
        minimumTemperatureKelvin = minimumTemperatureKelvin,
        maximumTemperatureKelvin = maximumTemperatureKelvin,
        minimumPressureKilopascals = minimumPressureKilopascals,
        catalystTags = catalystTags,
    )
}

private fun normalizeAlias(value: String): String = value.trim().lowercase(Locale.ROOT).replace(' ', '_')
