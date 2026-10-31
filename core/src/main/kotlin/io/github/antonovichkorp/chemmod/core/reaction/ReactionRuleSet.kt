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

/**
 * Addition across a matched multiple bond. The bond order drops and one atom of the
 * co-reactant attaches to a matched endpoint; every remaining valence is refilled by the
 * graph's implicit-hydrogen rule, so the co-reactant splits without a radical model and
 * without naming any product substance. Attaching to either endpoint is a separate
 * structural outcome, so regioisomers are enumerated rather than selected by a catalogue.
 */
data class BondAdditionPattern(
    val firstElement: String,
    val secondElement: String,
    val bondOrder: BondOrder,
    val productBondOrder: BondOrder,
    val attachElement: String,
    val requireNeutral: Boolean = true,
) : ReactionMatcher {
    init {
        require(
            listOf(firstElement, secondElement, attachElement).all { ELEMENT_SYMBOL_PATTERN.matches(it) },
        ) { "Addition-pattern elements must be symbols" }
        require(productBondOrder.value < bondOrder.value) {
            "Addition must lower the target bond order, $bondOrder -> $productBondOrder is not an addition"
        }
    }
}

/**
 * Transfers one atom of a co-reactant onto the first endpoint of a matched target bond,
 * without transferring any of the donor's other atoms or bonds. This represents selective
 * atom incorporation (for example aldehyde oxidation with O2) without retaining an O-O bond.
 */
data class AtomTransferPattern(
    val firstElement: String,
    val secondElement: String,
    val bondOrder: BondOrder,
    val attachElement: String,
    val firstMinimumHydrogens: Int = 0,
    val secondMinimumHydrogens: Int = 0,
    val requireNeutral: Boolean = true,
) : ReactionMatcher {
    init {
        require(listOf(firstElement, secondElement, attachElement).all { ELEMENT_SYMBOL_PATTERN.matches(it) }) {
            "Atom-transfer elements must be symbols"
        }
        require(firstMinimumHydrogens >= 0 && secondMinimumHydrogens >= 0) {
            "Atom-transfer hydrogen requirements cannot be negative"
        }
    }
}

data class ReactionConditions(
    val minimumTemperatureKelvin: Double? = null,
    val maximumTemperatureKelvin: Double? = null,
    val minimumPressureKilopascals: Double? = null,
    val catalystTags: Set<String> = emptySet(),
    /**
     * Capabilities the catalyst must earn from its own composition, derived by
     * [io.github.antonovichkorp.chemmod.core.catalysis.CatalystModel]. Unlike [catalystTags]
     * this never enumerates acceptable items.
     */
    val catalystCapabilities: Set<String> = emptySet(),
    /** Minimum accessible moles of each characterized surface-site type. */
    val minimumCatalystSurfaceSiteMoles: Map<String, Double> = emptyMap(),
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
        require(catalystCapabilities.all { REACTION_RULE_ID_PATTERN.matches(it) }) {
            "Catalyst capabilities must use namespace:path"
        }
        require(minimumCatalystSurfaceSiteMoles.all { (siteType, moles) ->
            REACTION_RULE_ID_PATTERN.matches(siteType) && moles.isFinite() && moles > 0.0
        }) { "Catalyst surface-site requirements need namespaced IDs and finite positive amounts" }
    }
}

/**
 * Data-owned Arrhenius parameters for a machine-scale reaction. The factor is
 * an intentionally calibrated gameplay rate, while activation energy preserves
 * the physically meaningful temperature relationship: hotter conditions make
 * a viable reaction progress faster instead of simply bypassing its gate.
 */
data class ReactionKinetics(
    val preExponentialFactorPerSecond: Double,
    val activationEnergyJoulesPerMole: Double,
    /** Optional characterized site type whose accessible amount scales this rate. */
    val surfaceSiteType: String? = null,
    /** Accessible site amount (mol) at which the data-defined Arrhenius rate is normalized. */
    val referenceSurfaceSiteMoles: Double? = null,
) {
    init {
        require(preExponentialFactorPerSecond.isFinite() && preExponentialFactorPerSecond > 0.0) {
            "Arrhenius pre-exponential factor must be finite and positive"
        }
        require(activationEnergyJoulesPerMole.isFinite() && activationEnergyJoulesPerMole >= 0.0) {
            "Activation energy must be finite and non-negative"
        }
        require((surfaceSiteType == null) == (referenceSurfaceSiteMoles == null)) {
            "Surface-dependent kinetics must define both a site type and its reference amount"
        }
        require(surfaceSiteType == null || REACTION_RULE_ID_PATTERN.matches(surfaceSiteType)) {
            "Kinetic surface-site type must use namespace:path"
        }
        require(referenceSurfaceSiteMoles == null ||
            (referenceSurfaceSiteMoles.isFinite() && referenceSurfaceSiteMoles > 0.0)
        ) { "Reference surface-site amount must be finite and positive" }
    }

    /** Exact condition checks remain the engine's responsibility; this only supplies the progress rate. */
    fun ratePerSecond(temperatureKelvin: Double): Double = ratePerSecond(temperatureKelvin, emptyMap())

    /**
     * Arrhenius rate multiplied by accessible sites / the measured reference site amount when
     * this kinetic record declares a surfaceSiteType. No declared surface model means no surface
     * correction; a declared but absent site population gives zero rate.
     */
    fun ratePerSecond(temperatureKelvin: Double, catalystSurfaceSiteMoles: Map<String, Double>): Double {
        require(temperatureKelvin.isFinite() && temperatureKelvin > 0.0) {
            "Temperature must be finite and positive"
        }
        val arrhenius = preExponentialFactorPerSecond * kotlin.math.exp(
            -activationEnergyJoulesPerMole / (GAS_CONSTANT_JOULES_PER_MOLE_KELVIN * temperatureKelvin)
        )
        val siteType = surfaceSiteType ?: return arrhenius
        val available = catalystSurfaceSiteMoles[siteType] ?: 0.0
        require(available.isFinite() && available >= 0.0) {
            "Accessible surface-site amount must be finite and non-negative"
        }
        return arrhenius * (available / requireNotNull(referenceSurfaceSiteMoles))
    }

    private companion object {
        const val GAS_CONSTANT_JOULES_PER_MOLE_KELVIN = 8.31446261815324
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
    /** Localized rule name key declared with the data rule, never inferred from a substance catalogue. */
    val displayNameKey: String = "reaction.chemmod.unnamed",
    /** Localized explanatory text the reactor's UI can show for this data rule. */
    val displayDescriptionKey: String = "reaction.chemmod.unnamed.description",
    /** Null means the rule is valid but has no timed machine execution model yet. */
    val kinetics: ReactionKinetics? = null,
    /** This ordinary chemistry rule may run in an exposed environmental gas mixture. */
    val environmental: Boolean = false,
) {
    init {
        val bondMatcher = matcher as? BondOrderPattern
        val additionMatcher = matcher as? BondAdditionPattern
        val atomTransferMatcher = matcher as? AtomTransferPattern
        require((bondMatcher != null) == (targetProductBondOrder != null)) {
            "Rule $id must define a target bond transformation exactly when it uses a bond-order matcher"
        }
        if (bondMatcher != null) {
            require(targetProductBondOrder != bondMatcher.bondOrder) {
                "Rule $id must change the target bond order"
            }
        }
        if (additionMatcher != null) {
            require(coReactantStructures.size == 1) {
                "Rule $id adds across a bond using exactly one co-reactant"
            }
            val coReactant = Molecule.fromSMILESlike(coReactantStructures.single())
            require(coReactant.graph.atoms.any { it.element.symbol == additionMatcher.attachElement }) {
                "Rule $id attaches '${additionMatcher.attachElement}', absent from its co-reactant"
            }
        }
        if (atomTransferMatcher != null) {
            require(coReactantStructures.size == 1) {
                "Rule $id transfers an atom from exactly one co-reactant"
            }
            val coReactant = Molecule.fromSMILESlike(coReactantStructures.single())
            require(coReactant.graph.atoms.any { it.element.symbol == atomTransferMatcher.attachElement }) {
                "Rule $id transfers '${atomTransferMatcher.attachElement}', absent from its co-reactant"
            }
        }
        require(
            productStructures.isNotEmpty() || bondMatcher != null || additionMatcher != null || atomTransferMatcher != null,
        ) {
            "Rule $id needs a static product or a transformed target"
        }
        require(displayNameKey.matches(Regex("[a-z][a-z0-9_.-]*(\\.[a-z0-9_.-]+)+"))) {
            "Rule $id needs a lowercase display-name localization key"
        }
        require(displayDescriptionKey.matches(Regex("[a-z][a-z0-9_.-]*(\\.[a-z0-9_.-]+)+"))) {
            "Rule $id needs a lowercase display-description localization key"
        }
        val surfaceKineticSite = kinetics?.surfaceSiteType
        require(surfaceKineticSite == null || surfaceKineticSite in conditions.minimumCatalystSurfaceSiteMoles) {
            "Rule $id uses surface-dependent kinetics but does not require that active-site inventory"
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
    val displayNameKey: String = "reaction.chemmod.unnamed",
    val displayDescriptionKey: String = "reaction.chemmod.unnamed.description",
    val kinetics: ReactionKineticsDocument? = null,
    val environmental: Boolean = false,
) {
    fun toDomain() = ReactionRule(
        id = ReactionRuleId.of(id),
        matcher = matcher.toDomain(),
        targetProductBondOrder = targetProductBondOrder?.let(::parseBondOrder),
        coReactantStructures = coReactantStructures,
        productStructures = productStructures,
        conditions = conditions.toDomain(),
        displayNameKey = displayNameKey,
        displayDescriptionKey = displayDescriptionKey,
        kinetics = kinetics?.toDomain(),
        environmental = environmental,
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
    val productBondOrder: String? = null,
    val attachElement: String? = null,
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
        "bond_addition" -> BondAdditionPattern(
            firstElement = requireNotNull(firstElement) { "Addition matcher needs firstElement" },
            secondElement = requireNotNull(secondElement) { "Addition matcher needs secondElement" },
            bondOrder = parseBondOrder(requireNotNull(bondOrder) { "Addition matcher needs bondOrder" }),
            productBondOrder = parseBondOrder(
                requireNotNull(productBondOrder) { "Addition matcher needs productBondOrder" },
            ),
            attachElement = requireNotNull(attachElement) { "Addition matcher needs attachElement" },
            requireNeutral = requireNeutral,
        )
        "atom_transfer" -> AtomTransferPattern(
            firstElement = requireNotNull(firstElement) { "Atom-transfer matcher needs firstElement" },
            secondElement = requireNotNull(secondElement) { "Atom-transfer matcher needs secondElement" },
            bondOrder = parseBondOrder(requireNotNull(bondOrder) { "Atom-transfer matcher needs bondOrder" }),
            attachElement = requireNotNull(attachElement) { "Atom-transfer matcher needs attachElement" },
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
    val catalystCapabilities: Set<String> = emptySet(),
    val minimumCatalystSurfaceSiteMoles: Map<String, Double> = emptyMap(),
) {
    fun toDomain() = ReactionConditions(
        minimumTemperatureKelvin,
        maximumTemperatureKelvin,
        minimumPressureKilopascals,
        catalystTags,
        catalystCapabilities,
        minimumCatalystSurfaceSiteMoles,
    )
}

@Serializable
private data class ReactionKineticsDocument(
    val preExponentialFactorPerSecond: Double,
    val activationEnergyJoulesPerMole: Double,
    val surfaceSiteType: String? = null,
    val referenceSurfaceSiteMoles: Double? = null,
) {
    fun toDomain() = ReactionKinetics(
        preExponentialFactorPerSecond = preExponentialFactorPerSecond,
        activationEnergyJoulesPerMole = activationEnergyJoulesPerMole,
        surfaceSiteType = surfaceSiteType,
        referenceSurfaceSiteMoles = referenceSurfaceSiteMoles,
    )
}

private fun parseBondOrder(value: String): BondOrder = enumValues<BondOrder>()
    .firstOrNull { it.name.equals(value, ignoreCase = true) }
    ?: throw IllegalArgumentException("Unknown bond order '$value'")
