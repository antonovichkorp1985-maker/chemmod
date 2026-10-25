package io.github.antonovichkorp.chemmod.core.catalysis

import java.io.InputStream
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val SURFACE_ID_PATTERN = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")
private val TRANSLATION_KEY_PATTERN = Regex("[a-z][a-z0-9_.-]*(\\.[a-z0-9_.-]+)+")

/** Supported low-index facets of an FCC crystal, with atoms per geometric surface area. */
@Serializable
enum class FccSurfaceFacet {
    FCC_100,
    FCC_110,
    FCC_111;

    fun atomicSitesPerSquareMeter(latticeParameterMeters: Double): Double {
        require(latticeParameterMeters.isFinite() && latticeParameterMeters > 0.0) {
            "FCC lattice parameter must be finite and positive"
        }
        val aSquared = latticeParameterMeters * latticeParameterMeters
        return when (this) {
            FCC_100 -> 2.0 / aSquared
            FCC_110 -> sqrt(2.0) / aSquared
            FCC_111 -> 4.0 / (sqrt(3.0) * aSquared)
        }
    }
}

/**
 * A characterized catalyst sample, not a guess based on its bulk element list. The sample's
 * nominal exposed area is tied to its defined physical form; active-site density is derived from
 * the crystal facet's atomic density and a measured/estimated active-site fraction.
 */
data class CatalystSurfaceProfile(
    val id: String,
    val materialId: String,
    val formName: String,
    val phaseId: String,
    val specificSurfaceAreaSquareMetersPerGram: Double,
    val facet: FccSurfaceFacet,
    val latticeParameterMeters: Double,
    val activeSiteFractions: Map<String, Double>,
    val siteDisplayNameKeys: Map<String, String>,
    val displayNameKey: String,
    val displayDescriptionKey: String,
    val evidence: String,
) {
    init {
        require(SURFACE_ID_PATTERN.matches(id)) { "Surface profile ID must use namespace:path" }
        require(SURFACE_ID_PATTERN.matches(materialId)) { "Surface material ID must use namespace:path" }
        require(formName.matches(Regex("[A-Z][A-Z_]*"))) { "Surface material form must be uppercase" }
        require(SURFACE_ID_PATTERN.matches(phaseId)) { "Surface phase ID must use namespace:path" }
        require(specificSurfaceAreaSquareMetersPerGram.isFinite() && specificSurfaceAreaSquareMetersPerGram > 0.0) {
            "Specific exposed area must be finite and positive"
        }
        require(latticeParameterMeters.isFinite() && latticeParameterMeters > 0.0) {
            "Crystal lattice parameter must be finite and positive"
        }
        require(activeSiteFractions.isNotEmpty()) { "A surface profile needs at least one active-site type" }
        require(activeSiteFractions.all { (site, fraction) ->
            SURFACE_ID_PATTERN.matches(site) && fraction.isFinite() && fraction > 0.0 && fraction <= 1.0
        } && activeSiteFractions.values.sum() <= 1.0 + FRACTION_TOLERANCE) {
            "Active-site fractions must be finite, positive, at most one, and not overlap"
        }
        require(siteDisplayNameKeys.keys == activeSiteFractions.keys &&
            siteDisplayNameKeys.values.all(TRANSLATION_KEY_PATTERN::matches)
        ) { "Each active-site type needs a localized display-name key" }
        require(TRANSLATION_KEY_PATTERN.matches(displayNameKey) &&
            TRANSLATION_KEY_PATTERN.matches(displayDescriptionKey)
        ) { "Surface profile needs localized name and description keys" }
        require(evidence.isNotBlank()) { "Surface profile needs traceable evidence" }
    }

    /** Surface atom density times experimentally supported active fraction, converted to mol/m². */
    fun activeSiteMolesPerSquareMeter(siteType: String): Double {
        val fraction = activeSiteFractions[siteType] ?: return 0.0
        return facet.atomicSitesPerSquareMeter(latticeParameterMeters) * fraction / AVOGADRO_CONSTANT
    }

    companion object {
        private const val FRACTION_TOLERANCE = 1e-12
        private const val AVOGADRO_CONSTANT = 6.02214076e23
    }
}

/** Accessible surface inventory; coverage is a fraction of each site type occupied/poisoned. */
data class CatalystSurfaceInventory(
    val exposedAreaSquareMeters: Double,
    val accessibleSiteMoles: Map<String, Double>,
)

/**
 * Quantitative bridge from a characterized catalyst specimen to the reaction environment.
 * No surface area, phase, facet, defect density, or adsorption behavior is inferred from bulk
 * elemental composition. Missing profiles therefore mean no modeled surface sites.
 */
class CatalystSurfaceModel(profiles: List<CatalystSurfaceProfile>) {
    private val byId = profiles.associateBy { it.id }
    private val byMaterialForm = profiles.associateBy { it.materialId to it.formName }

    init {
        require(byId.size == profiles.size) { "Catalyst surface model has duplicate profile IDs" }
        require(byMaterialForm.size == profiles.size) { "Catalyst surface model has duplicate material/form profiles" }
    }

    fun inventory(
        profileId: String,
        catalystMassMicrograms: Long,
        occupiedFractions: Map<String, Double> = emptyMap(),
    ): CatalystSurfaceInventory {
        require(catalystMassMicrograms > 0) { "Catalyst mass must be positive" }
        require(occupiedFractions.all { (site, fraction) ->
            SURFACE_ID_PATTERN.matches(site) && fraction.isFinite() && fraction in 0.0..1.0
        }) { "Surface occupancy fractions must be finite values from zero to one" }
        val profile = requireNotNull(byId[profileId]) { "Unknown catalyst surface profile '$profileId'" }
        require(occupiedFractions.keys.all { it in profile.activeSiteFractions }) {
            "Occupancy was supplied for a site not present in surface profile '$profileId'"
        }

        // Material batches are stored in micrograms: 1 g = 1,000,000 micrograms.
        val massGrams = catalystMassMicrograms / MICROGRAMS_PER_GRAM
        val area = massGrams * profile.specificSurfaceAreaSquareMetersPerGram
        val sites = profile.activeSiteFractions.keys.associateWith { site ->
            val unoccupied = 1.0 - (occupiedFractions[site] ?: 0.0)
            area * profile.activeSiteMolesPerSquareMeter(site) * unoccupied
        }
        return CatalystSurfaceInventory(area, sites)
    }

    /** Surface inventory for the exact material-form profile; unknown forms have no assumed sites. */
    fun siteMolesForMaterialForm(
        materialId: String,
        formName: String,
        primaryMassMicrograms: Long,
        occupiedFractions: Map<String, Double> = emptyMap(),
    ): Map<String, Double> {
        if (primaryMassMicrograms <= 0L) return emptyMap()
        val profile = byMaterialForm[materialId to formName] ?: return emptyMap()
        return inventory(profile.id, primaryMassMicrograms, occupiedFractions).accessibleSiteMoles
    }

    fun profileForMaterialForm(materialId: String, formName: String): CatalystSurfaceProfile? =
        byMaterialForm[materialId to formName]

    fun profile(id: String): CatalystSurfaceProfile? = byId[id]

    companion object {
        const val CURRENT_SCHEMA = 1
        private const val MICROGRAMS_PER_GRAM = 1_000_000.0

        private val BUNDLED: CatalystSurfaceModel by lazy {
            val stream = CatalystSurfaceModel::class.java.getResourceAsStream("/chemmod/catalysis/surfaces.json")
                ?: error("Bundled catalyst surface data is missing")
            stream.use(::fromJson)
        }

        @JvmStatic
        fun bundled(): CatalystSurfaceModel = BUNDLED

        fun fromJson(input: InputStream): CatalystSurfaceModel {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString(CatalystSurfaceDocument.serializer(), text)
            require(document.schemaVersion == CURRENT_SCHEMA) {
                "Unsupported catalyst surface schema: ${document.schemaVersion}"
            }
            return CatalystSurfaceModel(document.profiles.map { it.toDomain() })
        }

        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }
    }
}

@Serializable
private data class CatalystSurfaceDocument(
    val schemaVersion: Int,
    val profiles: List<CatalystSurfaceProfileDocument>,
)

@Serializable
private data class CatalystSurfaceProfileDocument(
    val id: String,
    val materialId: String,
    val formName: String,
    val phaseId: String,
    val specificSurfaceAreaSquareMetersPerGram: Double,
    val facet: FccSurfaceFacet,
    val latticeParameterMeters: Double,
    val activeSiteFractions: Map<String, Double>,
    val siteDisplayNameKeys: Map<String, String>,
    val displayNameKey: String,
    val displayDescriptionKey: String,
    val evidence: String,
) {
    fun toDomain() = CatalystSurfaceProfile(
        id,
        materialId,
        formName,
        phaseId,
        specificSurfaceAreaSquareMetersPerGram,
        facet,
        latticeParameterMeters,
        activeSiteFractions,
        siteDisplayNameKeys,
        displayNameKey,
        displayDescriptionKey,
        evidence,
    )
}
