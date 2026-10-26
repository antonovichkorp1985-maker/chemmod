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
     * Legacy polyol family: dispersion plus the hydrogen-bond network. Monohydric alcohols use
     * the separate structure model below; the ethylene-glycol residual remains open.
     */
    val hydroxylBaseCelsius: Double,
    val hydroxylDispersionCelsius: Double,
    val monohydricAlcoholBaseCelsius: Double,
    /** Dispersion by molar mass plus explicit carbon-skeleton size. */
    val monohydricAlcoholDispersionCelsius: Double,
    val monohydricAlcoholCarbonCelsius: Double,
    /** Cohesion contribution normalized by carbon count, so one OH site dilutes with chain size. */
    val monohydricAlcoholHydrogenBondDensityCelsiusPerKilojoulePerCarbon: Double,
    /** Steric shielding at a secondary/tertiary OH centre, derived from its carbon neighbours. */
    val monohydricAlcoholHydroxylSubstitutionCelsius: Double,
    val monohydricAlcoholHydroxylSubstitutionPerCarbonCelsius: Double,
    /** Carbon-skeleton branch penalty excluding the OH-bearing carbon. */
    val monohydricAlcoholBranchPointCelsius: Double,
    val hydrogenBondCelsiusPerKilojoulePerMole: Double,
    /**
     * Amines: dispersion plus one term per hydrogen the nitrogen can donate, which is what
     * separates a primary amine from a secondary one and both from a tertiary one that has none.
     */
    val amineBaseCelsius: Double,
    val amineDispersionCelsius: Double,
    val amineHydrogenBondDonorCelsius: Double,
    /** Ketones and aldehydes: dipolar, but with no hydrogen to donate. */
    val carbonylBaseCelsius: Double,
    val carbonylDispersionCelsius: Double,
    /** Ethers: an oxygen that accepts but never donates. */
    val etherBaseCelsius: Double,
    val etherDispersionCelsius: Double,
    /**
     * Monohalides: dispersion plus one term per halogen element, chlorine being the reference of
     * the fit (`halideCelsiusPerAtom["Cl"]` is zero by construction). The per-element map is data,
     * so widening the family means widening the map, not editing the predictor.
     */
    val halideBaseCelsius: Double,
    val halideDispersionCelsius: Double,
    val halideCelsiusPerAtom: Map<String, Double>,
    /**
     * Nitriles: dispersion plus the skeleton corrections. Fitted on five sourced nitriles that are
     * saturated or aryl — RMS 1.5 K, worst propionitrile at +2.6 K. α,β-unsaturated nitriles are
     * outside the fit and refused by [FunctionalGroups.hasConjugatedNitrile].
     */
    val nitrileBaseCelsius: Double,
    val nitrileDispersionCelsius: Double,
    /**
     * Esters: dispersion plus the skeleton and ring corrections. Fitted on eight sourced esters —
     * RMS 3.6 K, worst methyl benzoate at −6.6 K, because the aromaticity correction was fitted on
     * hydrocarbons and an ester carbonyl conjugated with a ring is not quite that.
     */
    val esterBaseCelsius: Double,
    val esterDispersionCelsius: Double,
    /**
     * Carboxylic acids: dispersion plus the skeleton and ring corrections. Fitted on seven sourced
     * monocarboxylic acids — RMS 3.1 K, worst benzoic acid at −5.6 K. The high intercept is the
     * cyclic dimer: two acids hold each other with two hydrogen bonds, so the class boils far above
     * an ester of the same mass. Every fitted member donates exactly one hydrogen bond, so a
     * dicarboxylic acid is refused rather than extrapolated.
     */
    val acidBaseCelsius: Double,
    val acidDispersionCelsius: Double,
    /**
     * Per skeleton branch point, shared by every branch that uses it. A branch compacts the
     * molecule and lowers the boiling point. Fitted jointly on the nitriles and the esters rather
     * than per class, because it is one physical effect and two fitted values for it would be a
     * shared coefficient pretending to be independent. The joint value, −15.33 K, sits inside the
     * 8–18 K the alkane series shows on its own, from n-butane against isobutane to isopentane
     * against neopentane.
     */
    val branchPointCelsius: Double,
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
    val monohydricAlcoholBaseCelsius: Double,
    /** Dispersion by molar mass plus explicit carbon-skeleton size. */
    val monohydricAlcoholDispersionCelsius: Double,
    val monohydricAlcoholCarbonCelsius: Double,
    /** Cohesion contribution normalized by carbon count, so one OH site dilutes with chain size. */
    val monohydricAlcoholHydrogenBondDensityCelsiusPerKilojoulePerCarbon: Double,
    /** Steric shielding at a secondary/tertiary OH centre, derived from its carbon neighbours. */
    val monohydricAlcoholHydroxylSubstitutionCelsius: Double,
    val monohydricAlcoholHydroxylSubstitutionPerCarbonCelsius: Double,
    /** Carbon-skeleton branch penalty excluding the OH-bearing carbon. */
    val monohydricAlcoholBranchPointCelsius: Double,
    val hydrogenBondCelsiusPerKilojoulePerMole: Double,
    val amineBaseCelsius: Double,
    val amineDispersionCelsius: Double,
    val amineHydrogenBondDonorCelsius: Double,
    val carbonylBaseCelsius: Double,
    val carbonylDispersionCelsius: Double,
    val etherBaseCelsius: Double,
    val etherDispersionCelsius: Double,
    val halideBaseCelsius: Double,
    val halideDispersionCelsius: Double,
    val halideCelsiusPerAtom: Map<String, Double> = emptyMap(),
    val nitrileBaseCelsius: Double,
    val nitrileDispersionCelsius: Double,
    val esterBaseCelsius: Double,
    val esterDispersionCelsius: Double,
    val acidBaseCelsius: Double,
    val acidDispersionCelsius: Double,
    val branchPointCelsius: Double,
) {
    fun toDomain(): BoilingRuleSet {
        val values = listOf(
            hydrocarbonBaseCelsius,
            hydrocarbonDispersionCelsius,
            ringCelsius,
            aromaticRingCelsius,
            hydroxylBaseCelsius,
            hydroxylDispersionCelsius,
            monohydricAlcoholBaseCelsius,
            monohydricAlcoholDispersionCelsius,
            monohydricAlcoholCarbonCelsius,
            monohydricAlcoholHydrogenBondDensityCelsiusPerKilojoulePerCarbon,
            monohydricAlcoholHydroxylSubstitutionCelsius,
            monohydricAlcoholHydroxylSubstitutionPerCarbonCelsius,
            monohydricAlcoholBranchPointCelsius,
            hydrogenBondCelsiusPerKilojoulePerMole,
            amineDispersionCelsius,
            amineHydrogenBondDonorCelsius,
            carbonylDispersionCelsius,
            etherDispersionCelsius,
            halideDispersionCelsius,
            nitrileDispersionCelsius,
            esterDispersionCelsius,
            acidDispersionCelsius,
            branchPointCelsius,
        ) + exactFormulaCelsius.values + halideCelsiusPerAtom.values
        require(values.all { it.isFinite() }) { "Boiling rule coefficients must be finite" }
        require(monohydricAlcoholDispersionCelsius > 0.0 && monohydricAlcoholCarbonCelsius > 0.0 &&
            monohydricAlcoholHydrogenBondDensityCelsiusPerKilojoulePerCarbon > 0.0
        ) { "Alcohol dispersion, carbon skeleton and association density contributions must be positive" }
        require(monohydricAlcoholHydroxylSubstitutionCelsius <= 0.0 &&
            monohydricAlcoholHydroxylSubstitutionPerCarbonCelsius <= 0.0 &&
            monohydricAlcoholBranchPointCelsius <= 0.0
        ) { "OH shielding and carbon skeleton branching cannot raise a monohydric alcohol boiling point" }
        require(hydrogenBondCelsiusPerKilojoulePerMole >= 0.0) {
            "A hydrogen bond cannot lower the boiling point"
        }
        require(amineHydrogenBondDonorCelsius >= 0.0) {
            "An N-H donor cannot lower the boiling point"
        }
        require(
            amineDispersionCelsius > 0.0 && carbonylDispersionCelsius > 0.0 &&
                etherDispersionCelsius > 0.0 && halideDispersionCelsius > 0.0 &&
                nitrileDispersionCelsius > 0.0 && esterDispersionCelsius > 0.0 &&
                acidDispersionCelsius > 0.0
        ) { "A heavier molecule of the same class cannot boil lower" }
        require(branchPointCelsius <= 0.0) {
            "A branch reduces the surface a molecule touches, so it cannot raise the boiling point"
        }
        require(halideCelsiusPerAtom.isNotEmpty()) {
            "A halide branch with no per-element coefficient would silently refuse every halide"
        }
        return BoilingRuleSet(
            exactFormulaCelsius = exactFormulaCelsius.toMap(),
            hydrocarbonBaseCelsius = hydrocarbonBaseCelsius,
            hydrocarbonDispersionCelsius = hydrocarbonDispersionCelsius,
            ringCelsius = ringCelsius,
            aromaticRingCelsius = aromaticRingCelsius,
            hydroxylBaseCelsius = hydroxylBaseCelsius,
            hydroxylDispersionCelsius = hydroxylDispersionCelsius,
            monohydricAlcoholBaseCelsius = monohydricAlcoholBaseCelsius,
            monohydricAlcoholDispersionCelsius = monohydricAlcoholDispersionCelsius,
            monohydricAlcoholCarbonCelsius = monohydricAlcoholCarbonCelsius,
            monohydricAlcoholHydrogenBondDensityCelsiusPerKilojoulePerCarbon =
                monohydricAlcoholHydrogenBondDensityCelsiusPerKilojoulePerCarbon,
            monohydricAlcoholHydroxylSubstitutionCelsius = monohydricAlcoholHydroxylSubstitutionCelsius,
            monohydricAlcoholHydroxylSubstitutionPerCarbonCelsius =
                monohydricAlcoholHydroxylSubstitutionPerCarbonCelsius,
            monohydricAlcoholBranchPointCelsius = monohydricAlcoholBranchPointCelsius,
            hydrogenBondCelsiusPerKilojoulePerMole = hydrogenBondCelsiusPerKilojoulePerMole,
            amineBaseCelsius = amineBaseCelsius,
            amineDispersionCelsius = amineDispersionCelsius,
            amineHydrogenBondDonorCelsius = amineHydrogenBondDonorCelsius,
            carbonylBaseCelsius = carbonylBaseCelsius,
            carbonylDispersionCelsius = carbonylDispersionCelsius,
            etherBaseCelsius = etherBaseCelsius,
            etherDispersionCelsius = etherDispersionCelsius,
            halideBaseCelsius = halideBaseCelsius,
            halideDispersionCelsius = halideDispersionCelsius,
            halideCelsiusPerAtom = halideCelsiusPerAtom.toMap(),
            nitrileBaseCelsius = nitrileBaseCelsius,
            nitrileDispersionCelsius = nitrileDispersionCelsius,
            esterBaseCelsius = esterBaseCelsius,
            esterDispersionCelsius = esterDispersionCelsius,
            acidBaseCelsius = acidBaseCelsius,
            acidDispersionCelsius = acidDispersionCelsius,
            branchPointCelsius = branchPointCelsius,
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
