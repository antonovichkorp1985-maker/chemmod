package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val REACTION_RULE_ID_PATTERN = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")
private val ELEMENT_SYMBOL_PATTERN = Regex("[A-Z][a-z]?")

data class ReactionRuleId private constructor(val value: String) {
    companion object {
        @JvmStatic
        fun of(value: String): ReactionRuleId {
            require(REACTION_RULE_ID_PATTERN.matches(value)) {
                "Reaction rule ID must use namespace:path with lowercase safe characters, got '$value'"
            }
            return ReactionRuleId(value)
        }
    }

    override fun toString(): String = value
}

/** A data-defined matcher. No matcher references a named substance registry. */
sealed interface ReactionMatcher

/** Formula-level matcher for transformations such as complete combustion. */
data class FormulaPattern(
    val allowedElements: Set<String>,
    val requiredElements: Set<String>,
    val requireNeutral: Boolean,
    val requirePositiveOxygenDemand: Boolean,
) : ReactionMatcher {
    init {
        require(allowedElements.isNotEmpty()) { "A formula pattern needs allowed elements" }
        require(requiredElements.all { it in allowedElements }) {
            "Required formula elements must also be allowed"
        }
        require((allowedElements + requiredElements).all { ELEMENT_SYMBOL_PATTERN.matches(it) }) {
            "Formula pattern elements must be symbols"
        }
    }
}

/**
 * A local graph matcher. The engine considers either orientation of the bond,
 * so a rule is independent of the order in which the input graph was drawn.
 */
data class BondOrderPattern(
    val firstElement: String,
    val secondElement: String,
    val bondOrder: BondOrder,
    val firstMinimumHydrogens: Int = 0,
    val secondMinimumHydrogens: Int = 0,
    val requireNeutral: Boolean = true,
) : ReactionMatcher {
    init {
        require(ELEMENT_SYMBOL_PATTERN.matches(firstElement) && ELEMENT_SYMBOL_PATTERN.matches(secondElement)) {
            "Bond-pattern elements must be symbols"
        }
        require(firstMinimumHydrogens >= 0 && secondMinimumHydrogens >= 0) {
            "Bond-pattern hydrogen requirements cannot be negative"
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
        ) { "Minimum reaction temperature cannot exceed maximum reaction temperature" }
        require(catalystTags.all { REACTION_RULE_ID_PATTERN.matches(it) }) {
            "Catalyst tags must use namespace:path"
        }
    }
}

/**
 * Content for one chemistry rule. A graph-bond rule rewrites only its matched
 * target bond; all other target atoms and bonds survive unchanged. Static
 * products and co-reactants supply the remaining stoichiometric species.
 */
data class ReactionRule(
    val id: ReactionRuleId,
    val matcher: ReactionMatcher,
    val targetProductBondOrder: BondOrder? = null,
    val coReactantStructures: List<String> = emptyList(),
    val productStructures: List<String> = emptyList(),
    val conditions: ReactionConditions = ReactionConditions(),
) {
    init {
        val bondMatcher = matcher as? BondOrderPattern
        require((bondMatcher != null) == (targetProductBondOrder != null)) {
            "Rule $id must define a target bond transformation exactly when it uses a bond matcher"
        }
        if (bondMatcher != null) {
            require(targetProductBondOrder != bondMatcher.bondOrder) {
                "Rule $id must change the target bond order"
            }
        }
        require(productStructures.isNotEmpty() || bondMatcher != null) {
            "Rule $id needs a static product or a transformed target"
        }
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
        const val CURRENT_SCHEMA = 2

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
    val matcher: ReactionMatcherDocument,
    val targetProductBondOrder: String? = null,
    val coReactantStructures: List<String> = emptyList(),
    val productStructures: List<String> = emptyList(),
    val conditions: ReactionConditionsDocument = ReactionConditionsDocument(),
) {
    fun toDomain() = ReactionRule(
        id = ReactionRuleId.of(id),
        matcher = matcher.toDomain(),
        targetProductBondOrder = targetProductBondOrder?.let(::parseBondOrder),
        coReactantStructures = coReactantStructures,
        productStructures = productStructures,
        conditions = conditions.toDomain(),
    )
}

@Serializable
private data class ReactionMatcherDocument(
    val kind: String,
    val allowedElements: Set<String> = emptySet(),
    val requiredElements: Set<String> = emptySet(),
    val requirePositiveOxygenDemand: Boolean = false,
    val firstElement: String? = null,
    val secondElement: String? = null,
    val bondOrder: String? = null,
    val firstMinimumHydrogens: Int = 0,
    val secondMinimumHydrogens: Int = 0,
    val requireNeutral: Boolean = true,
) {
    fun toDomain(): ReactionMatcher = when (kind) {
        "formula" -> FormulaPattern(
            allowedElements = allowedElements,
            requiredElements = requiredElements,
            requireNeutral = requireNeutral,
            requirePositiveOxygenDemand = requirePositiveOxygenDemand,
        )
        "bond_order" -> BondOrderPattern(
            firstElement = requireNotNull(firstElement) { "Bond matcher needs firstElement" },
            secondElement = requireNotNull(secondElement) { "Bond matcher needs secondElement" },
            bondOrder = parseBondOrder(requireNotNull(bondOrder) { "Bond matcher needs bondOrder" }),
            firstMinimumHydrogens = firstMinimumHydrogens,
            secondMinimumHydrogens = secondMinimumHydrogens,
            requireNeutral = requireNeutral,
        )
        else -> throw IllegalArgumentException("Unknown reaction matcher kind '$kind'")
    }
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

private fun parseBondOrder(value: String): BondOrder = enumValues<BondOrder>()
    .firstOrNull { it.name.equals(value, ignoreCase = true) }
    ?: throw IllegalArgumentException("Unknown bond order '$value'")
