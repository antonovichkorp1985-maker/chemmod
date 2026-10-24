package io.github.antonovichkorp.chemmod.core.catalysis

import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val SURFACE_ID_PATTERN = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")

/**
 * A characterized catalyst sample, not a guess based on its bulk element list. Surface phase,
 * BET area, and active-site density are explicit because all three vary with preparation and
 * reaction. The site densities are moles of a named site per square metre of characterized area.
 */
data class CatalystSurfaceProfile(
    val id: String,
    val materialId: String,
    val formName: String,
    val phaseId: String,
    val specificSurfaceAreaSquareMetersPerGram: Double,
    val activeSiteMolesPerSquareMeter: Map<String, Double>,
    val evidence: String,
) {
    init {
        require(SURFACE_ID_PATTERN.matches(id)) { "Surface profile ID must use namespace:path" }
        require(SURFACE_ID_PATTERN.matches(materialId)) { "Surface material ID must use namespace:path" }
        require(formName.matches(Regex("[A-Z][A-Z_]*"))) { "Surface material form must be uppercase" }
        require(SURFACE_ID_PATTERN.matches(phaseId)) { "Surface phase ID must use namespace:path" }
        require(specificSurfaceAreaSquareMetersPerGram.isFinite() && specificSurfaceAreaSquareMetersPerGram > 0.0) {
            "Specific surface area must be finite and positive"
        }
        require(activeSiteMolesPerSquareMeter.isNotEmpty()) { "A surface profile needs at least one active-site type" }
        require(activeSiteMolesPerSquareMeter.all { (site, density) ->
            SURFACE_ID_PATTERN.matches(site) && density.isFinite() && density > 0.0
        }) { "Active-site densities need namespaced IDs and finite positive values" }
        require(evidence.isNotBlank()) { "Surface profile needs traceable evidence" }
    }
}

/** Accessible surface inventory; coverage is a fraction of each site type occupied/poisoned. */
data class CatalystSurfaceInventory(
    val exposedAreaSquareMeters: Double,
    val accessibleSiteMoles: Map<String, Double>,
)

/**
 * Quantitative bridge from a characterized catalyst specimen to the reaction environment.
 * No surface area, phase, defect density, or adsorption behavior is inferred from elemental
 * composition. Missing profiles therefore mean no modeled surface sites, never a bulk-metal guess.
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
        require(occupiedFractions.keys.all { it in profile.activeSiteMolesPerSquareMeter }) {
            "Occupancy was supplied for a site not present in surface profile '$profileId'"
        }

        // micrograms -> grams; BET area is specific area (m²/g); site density is mol/m².
        val massGrams = catalystMassMicrograms / MICROGRAMS_PER_GRAM
        val area = massGrams * profile.specificSurfaceAreaSquareMetersPerGram
        val sites = profile.activeSiteMolesPerSquareMeter.mapValues { (site, density) ->
            val unoccupied = 1.0 - (occupiedFractions[site] ?: 0.0)
            area * density * unoccupied
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

    fun profile(id: String): CatalystSurfaceProfile? = byId[id]

    companion object {
        const val CURRENT_SCHEMA = 1
        private const val MICROGRAMS_PER_GRAM = 1_000_000_000.0

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
    val activeSiteMolesPerSquareMeter: Map<String, Double>,
    val evidence: String,
) {
    fun toDomain() = CatalystSurfaceProfile(
        id,
        materialId,
        formName,
        phaseId,
        specificSurfaceAreaSquareMetersPerGram,
        activeSiteMolesPerSquareMeter,
        evidence,
    )
}
