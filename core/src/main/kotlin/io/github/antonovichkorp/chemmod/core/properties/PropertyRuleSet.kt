package io.github.antonovichkorp.chemmod.core.properties

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
    val exactFormulaCelsius: Map<String, Double>,
    val hydroxylFirstCarbonCelsius: Double,
    val hydroxylAdditionalCarbonCelsius: Double,
    val etherBaseCelsius: Double,
    val etherAdditionalCarbonCelsius: Double,
    val hydrocarbonFirstCarbonCelsius: Double,
    val hydrocarbonLinearCarbonCelsius: Double,
    val hydrocarbonQuadraticCarbonCelsius: Double,
)

@Serializable
private data class PropertyRuleSetDocument(
    val schemaVersion: Int,
    val modelId: String,
    val bondEnergies: List<BondEnergyDocument>,
    val carbonDioxideCarbonOxygenDoubleBondKilojoulesPerMole: Double,
    val boiling: BoilingRuleSetDocument,
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
    val hydroxylFirstCarbonCelsius: Double,
    val hydroxylAdditionalCarbonCelsius: Double,
    val etherBaseCelsius: Double,
    val etherAdditionalCarbonCelsius: Double,
    val hydrocarbonFirstCarbonCelsius: Double,
    val hydrocarbonLinearCarbonCelsius: Double,
    val hydrocarbonQuadraticCarbonCelsius: Double,
) {
    fun toDomain(): BoilingRuleSet {
        val values = listOf(
            hydroxylFirstCarbonCelsius,
            hydroxylAdditionalCarbonCelsius,
            etherBaseCelsius,
            etherAdditionalCarbonCelsius,
            hydrocarbonFirstCarbonCelsius,
            hydrocarbonLinearCarbonCelsius,
            hydrocarbonQuadraticCarbonCelsius,
        ) + exactFormulaCelsius.values
        require(values.all { it.isFinite() }) { "Boiling rule coefficients must be finite" }
        return BoilingRuleSet(
            exactFormulaCelsius = exactFormulaCelsius.toMap(),
            hydroxylFirstCarbonCelsius = hydroxylFirstCarbonCelsius,
            hydroxylAdditionalCarbonCelsius = hydroxylAdditionalCarbonCelsius,
            etherBaseCelsius = etherBaseCelsius,
            etherAdditionalCarbonCelsius = etherAdditionalCarbonCelsius,
            hydrocarbonFirstCarbonCelsius = hydrocarbonFirstCarbonCelsius,
            hydrocarbonLinearCarbonCelsius = hydrocarbonLinearCarbonCelsius,
            hydrocarbonQuadraticCarbonCelsius = hydrocarbonQuadraticCarbonCelsius,
        )
    }
}
