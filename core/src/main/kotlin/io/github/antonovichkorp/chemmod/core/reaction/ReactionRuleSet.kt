package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val REACTION_RULE_ID_PATTERN = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")

@JvmInline
value class ReactionRuleId private constructor(val value: String) {
    companion object {
        fun of(value: String): ReactionRuleId {
            require(REACTION_RULE_ID_PATTERN.matches(value)) {
                "Reaction rule ID must use namespace:path with lowercase safe characters, got '$value'"
            }
            return ReactionRuleId(value)
        }
    }

    override fun toString(): String = value
}

/** Data-defined matcher for a broad family of molecular structures. */
data class FormulaPattern(
    val allowedElements: Set<String>,
    val requiredElements: Set<String>,
    val requireNeutral: Boolean,
    val requirePositiveOxygenDemand: Boolean,
) {
    init {
        require(allowedElements.isNotEmpty()) { "A formula pattern needs allowed elements" }
        require(requiredElements.all { it in allowedElements }) {
            "Required formula elements must also be allowed"
        }
        require((allowedElements + requiredElements).all { it.matches(Regex("[A-Z][a-z]?")) }) {
            "Formula pattern elements must be symbols"
        }
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
        require(catalystTags.all { REACTION_RULE_ID_PATTERN.matches(it) }) {
            "Catalyst tags must use namespace:path"
        }
    }
}

/**
 * The generic engine owns matching, environmental gates and automatic balancing.
 * A rule contributes only content: a pattern plus co-reactant and product
 * structures. Graph-substitution matcher kinds will extend this schema without
 * changing the identity or property layers.
 */
data class ReactionRule(
    val id: ReactionRuleId,
    val matcher: FormulaPattern,
    val coReactantStructures: List<String>,
    val productStructures: List<String>,
    val conditions: ReactionConditions,
) {
    init {
        require(coReactantStructures.isNotEmpty()) { "Reaction rule $id needs a co-reactant" }
        require(productStructures.isNotEmpty()) { "Reaction rule $id needs a product" }
        (coReactantStructures + productStructures).forEach { structure ->
            val molecule = Molecule.fromSMILESlike(structure)
            require(molecule.validate().isEmpty()) { "Rule $id has an invalid structure '$structure'" }
        }
    }
}

class ReactionRuleSet private constructor(
    val schemaVersion: Int,
    rules: List<ReactionRule>,
) {
    val rules: Map<ReactionRuleId, ReactionRule> = rules.associateBy { it.id }

    init {
        require(schemaVersion == CURRENT_SCHEMA) { "Unsupported reaction rule schema: $schemaVersion" }
        require(this.rules.size == rules.size) { "Reaction rule set has duplicate IDs" }
    }

    fun rule(id: ReactionRuleId): ReactionRule = requireNotNull(rules[id]) { "Unknown reaction rule $id" }

    companion object {
        const val CURRENT_SCHEMA = 1

        fun fromJson(input: InputStream): ReactionRuleSet {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString(ReactionRuleSetDocument.serializer(), text)
            return ReactionRuleSet(document.schemaVersion, document.rules.map { it.toDomain() })
        }

        @JvmStatic
        fun bundled(): ReactionRuleSet {
            val stream = ReactionRuleSet::class.java.getResourceAsStream("/chemmod/reactions/default.json")
                ?: error("Bundled reaction rules are missing")
            return stream.use(::fromJson)
        }

        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }
    }
}

@Serializable
private data class ReactionRuleSetDocument(
    val schemaVersion: Int,
    val rules: List<ReactionRuleDocument>,
)

@Serializable
private data class ReactionRuleDocument(
    val id: String,
    val matcher: FormulaPatternDocument,
    val coReactantStructures: List<String>,
    val productStructures: List<String>,
    val conditions: ReactionConditionsDocument = ReactionConditionsDocument(),
) {
    fun toDomain() = ReactionRule(
        id = ReactionRuleId.of(id),
        matcher = matcher.toDomain(),
        coReactantStructures = coReactantStructures,
        productStructures = productStructures,
        conditions = conditions.toDomain(),
    )
}

@Serializable
private data class FormulaPatternDocument(
    val allowedElements: Set<String>,
    val requiredElements: Set<String> = emptySet(),
    val requireNeutral: Boolean = true,
    val requirePositiveOxygenDemand: Boolean = false,
) {
    fun toDomain() = FormulaPattern(allowedElements, requiredElements, requireNeutral, requirePositiveOxygenDemand)
}

@Serializable
private data class ReactionConditionsDocument(
    val minimumTemperatureKelvin: Double? = null,
    val maximumTemperatureKelvin: Double? = null,
    val minimumPressureKilopascals: Double? = null,
    val catalystTags: Set<String> = emptySet(),
) {
    fun toDomain() = ReactionConditions(
        minimumTemperatureKelvin,
        maximumTemperatureKelvin,
        minimumPressureKilopascals,
        catalystTags,
    )
}
