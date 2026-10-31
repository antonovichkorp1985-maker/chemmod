package io.github.antonovichkorp.chemmod.core.material

/**
 * Real-world material density for mass calculation.
 *
 * Density is a physical property of the substance, not a tabulated constant per block.
 * Where literature values vary, the most common room-temperature solid density is used.
 * Unknown density = null / NO_MODEL; never invented.
 */
data class MaterialDensity(
    /** Density in kg/m^3 at 293.15 K, 101325 Pa for solids/liquids; STP for gases. */
    val densityKilogramsPerCubicMeter: Double,
    /** Packing factor for the block geometry: 1.0 = solid cube, <1.0 = hollow/pipe/tank/shell. */
    val packingFactor: Double = 1.0,
) {
    init {
        require(densityKilogramsPerCubicMeter > 0.0) { "Density must be positive" }
        require(packingFactor in 0.0..1.0) { "Packing factor must be 0..1" }
    }

    /** Effective density for a block of this material with the given packing. */
    fun effectiveDensity(): Double = densityKilogramsPerCubicMeter * packingFactor
}

/** Registry of material densities, keyed by MaterialId. */
class MaterialDensityRegistry {
    private val densities = linkedMapOf<MaterialId, MaterialDensity>()

    fun register(id: MaterialId, density: MaterialDensity) {
        densities[id] = density
    }

    fun get(id: MaterialId): MaterialDensity? = densities[id]

    /** Returns null if no density is registered (NO_MODEL). */
    fun getOrNull(id: MaterialId): MaterialDensity? = densities[id]

    /** All registered densities. */
    fun all(): Map<MaterialId, MaterialDensity> = densities.toMap()

    /** Register default real-world densities for ChemMod materials. */
    fun registerDefaults() {
        // Copper: 8960 kg/m3 (pure copper at 20°C)
        register(MaterialId.of("chemmod:copper"), MaterialDensity(8960.0))
        // Quartz/Silica: 2650 kg/m3
        register(MaterialId.of("chemmod:silicate_gangue"), MaterialDensity(2650.0))
        // Iron: 7870 kg/m3
        register(MaterialId.of("chemmod:iron"), MaterialDensity(7870.0))
        // Steel (approx): 7850 kg/m3
        register(MaterialId.of("chemmod:steel"), MaterialDensity(7850.0))
        // Aluminium: 2700 kg/m3
        register(MaterialId.of("chemmod:aluminium"), MaterialDensity(2700.0))
        // Lead: 11340 kg/m3
        register(MaterialId.of("chemmod:lead"), MaterialDensity(11340.0))
        // Zinc: 7140 kg/m3
        register(MaterialId.of("chemmod:zinc"), MaterialDensity(7140.0))
        // Nickel: 8910 kg/m3
        register(MaterialId.of("chemmod:nickel"), MaterialDensity(8910.0))
        // Tin: 7310 kg/m3
        register(MaterialId.of("chemmod:tin"), MaterialDensity(7310.0))
        // Titanium: 4510 kg/m3
        register(MaterialId.of("chemmod:titanium"), MaterialDensity(4510.0))
        // Gold: 19300 kg/m3
        register(MaterialId.of("chemmod:gold"), MaterialDensity(19300.0))
        // Silver: 10490 kg/m3
        register(MaterialId.of("chemmod:silver"), MaterialDensity(10490.0))
        // Platinum: 21450 kg/m3
        register(MaterialId.of("chemmod:platinum"), MaterialDensity(21450.0))
    }

    companion object {
        fun withDefaults(): MaterialDensityRegistry = MaterialDensityRegistry().apply { registerDefaults() }
    }
}