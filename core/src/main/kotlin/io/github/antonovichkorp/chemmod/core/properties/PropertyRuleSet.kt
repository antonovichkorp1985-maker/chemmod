package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.bond.InteractionRuleSet
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Content-only coefficients for the structure property engine. The bundled file
 * is the default rule set; the schema is deliberately independent of Minecraft
 * so a datapack loader can supply the same data later.
 */
class PropertyRuleSet private constructor(
    val schemaVersion: Int,
    val modelId: String,
    val bondEnergiesKilojoulesPerMole: Map<BondDescriptor, Double>,
    val carbonDioxideCarbonOxygenDoubleBondKilojoulesPerMole: Double,
    val boiling: BoilingRuleSet,
    val interactions: InteractionRuleSet,
) {
    init {
        require(schemaVersion == CURRENT_SCHEMA) { "Unsupported property rule schema: $schemaVersion" }
        require(modelId.isNotBlank()) { "Property rule model ID cannot be blank" }
        require(bondEnergiesKilojoulesPerMole.isNotEmpty()) { "Property rules need bond energies" }
        require(bondEnergiesKilojoulesPerMole.values.all { it > 0.0 && it.isFinite() }) {
            "Bond energies must be finite and positive"
        }
        require(carbonDioxideCarbonOxygenDoubleBondKilojoulesPerMole > 0.0 &&
            carbonDioxideCarbonOxygenDoubleBondKilojoulesPerMole.isFinite()
        ) { "Carbon dioxide bond energy must be finite and positive" }
    }

    fun bondEnergy(first: String, second: String, order: BondOrder): Double? =
        bondEnergiesKilojoulesPerMole[BondDescriptor.of(first, second, order)]

    companion object {
        const val CURRENT_SCHEMA = 1

        fun fromJson(input: InputStream): PropertyRuleSet {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString(PropertyRuleSetDocument.serializer(), text)
            val energies = document.bondEnergies.associate { entry ->
                BondDescriptor.of(entry.first, entry.second, bondOrder(entry.order)) to entry.kilojoulesPerMole
            }
            require(energies.size == document.bondEnergies.size) { "Property rules contain duplicate bond entries" }
            return PropertyRuleSet(
                schemaVersion = document.schemaVersion,
                modelId = document.modelId,
                bondEnergiesKilojoulesPerMole = energies,
                carbonDioxideCarbonOxygenDoubleBondKilojoulesPerMole =
                    document.carbonDioxideCarbonOxygenDoubleBondKilojoulesPerMole,
                boiling = document.boiling.toDomain(),
                interactions = document.interactions.toDomain(),
            )
        }

        @JvmStatic
        fun bundled(): PropertyRuleSet {
            val stream = PropertyRuleSet::class.java.getResourceAsStream("/chemmod/properties/default.json")
                ?: error("Bundled property rules are missing")
            return stream.use(::fromJson)
        }

        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }

        private fun bondOrder(value: String): BondOrder = enumValues<BondOrder>()
            .firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown bond order '$value'")
    }
}

data class BondDescriptor private constructor(
    val first: String,
    val second: String,
    val order: BondOrder,
) {
    companion object {
        fun of(left: String, right: String, order: BondOrder) = BondDescriptor(
            minOf(left, right),
            maxOf(left, right),
            order,
        )
    }
}

/** Formula and functional-group contribution constants, supplied as content rather than species records. */
data class BoilingRuleSet(
    /**
     * Explicit calibration anchors. Every entry is a substance row, i.e. exactly what this
     * model is trying to avoid; [io.github.antonovichkorp.chemmod.core.properties.PredictedProperties.boilingPointBasis]
     * names them so no anchor can pass for a prediction.
     */
    val exactFormulaCelsius: Map<String, Double>,
    /** Dispersion term: a + b * molarMass^(2/3), fitted on alkanes, alkenes, cycloalkanes, arenes. */
    val hydrocarbonBaseCelsius: Double,
    val hydrocarbonDispersionCelsius: Double,
    /** Cyclization and aromaticity corrections on top of the dispersion term. */
    val ringCelsius: Double,
    val aromaticRingCelsius: Double,
    /**
     * Alcohols and polyols: the same dispersion term plus the hydrogen-bond network, so a
     * diol or a triol boils higher because of the sites it actually has.
     */
    val hydroxylBaseCelsius: Double,
    val hydroxylDispersionCelsius: Double,
    val hydrogenBondCelsiusPerKilojoulePerMole: Double,
)

@Serializable
private data class PropertyRuleSetDocument(
    val schemaVersion: Int,
    val modelId: String,
    val bondEnergies: List<BondEnergyDocument>,
    val carbonDioxideCarbonOxygenDoubleBondKilojoulesPerMole: Double,
    val boiling: BoilingRuleSetDocument,
    val interactions: InteractionRuleSetDocument = InteractionRuleSetDocument(),
)

@Serializable
private data class BondEnergyDocument(
    val first: String,
    val second: String,
    val order: String,
    val kilojoulesPerMole: Double,
)

@Serializable
private data class BoilingRuleSetDocument(
    val exactFormulaCelsius: Map<String, Double> = emptyMap(),
    val hydrocarbonBaseCelsius: Double,
    val hydrocarbonDispersionCelsius: Double,
    val ringCelsius: Double,
    val aromaticRingCelsius: Double,
    val hydroxylBaseCelsius: Double,
    val hydroxylDispersionCelsius: Double,
    val hydrogenBondCelsiusPerKilojoulePerMole: Double,
) {
    fun toDomain(): BoilingRuleSet {
        val values = listOf(
            hydrocarbonBaseCelsius,
            hydrocarbonDispersionCelsius,
            ringCelsius,
            aromaticRingCelsius,
            hydroxylBaseCelsius,
            hydroxylDispersionCelsius,
            hydrogenBondCelsiusPerKilojoulePerMole,
        ) + exactFormulaCelsius.values
        require(values.all { it.isFinite() }) { "Boiling rule coefficients must be finite" }
        require(hydrogenBondCelsiusPerKilojoulePerMole >= 0.0) {
            "A hydrogen bond cannot lower the boiling point"
        }
        return BoilingRuleSet(
            exactFormulaCelsius = exactFormulaCelsius.toMap(),
            hydrocarbonBaseCelsius = hydrocarbonBaseCelsius,
            hydrocarbonDispersionCelsius = hydrocarbonDispersionCelsius,
            ringCelsius = ringCelsius,
            aromaticRingCelsius = aromaticRingCelsius,
            hydroxylBaseCelsius = hydroxylBaseCelsius,
            hydroxylDispersionCelsius = hydroxylDispersionCelsius,
            hydrogenBondCelsiusPerKilojoulePerMole = hydrogenBondCelsiusPerKilojoulePerMole,
        )
    }
}

@Serializable
private data class InteractionRuleSetDocument(
    val hydrogenBondDonorElements: Set<String> = setOf("N", "O", "F"),
    val hydrogenBondAcceptorElements: Set<String> = setOf("N", "O", "F"),
    val hydrogenBondKilojoulesPerMole: Double = 21.0,
    val coordinateBondKilojoulesPerMole: Double = 100.0,
    val aromaticResonanceKilojoulesPerMolePerRing: Double = 150.0,
) {
    fun toDomain() = InteractionRuleSet(
        hydrogenBondDonorElements = hydrogenBondDonorElements,
        hydrogenBondAcceptorElements = hydrogenBondAcceptorElements,
        hydrogenBondKilojoulesPerMole = hydrogenBondKilojoulesPerMole,
        coordinateBondKilojoulesPerMole = coordinateBondKilojoulesPerMole,
        aromaticResonanceKilojoulesPerMolePerRing = aromaticResonanceKilojoulesPerMolePerRing,
    )
}
