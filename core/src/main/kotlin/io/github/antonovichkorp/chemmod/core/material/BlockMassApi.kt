package io.github.antonovichkorp.chemmod.core.material

import io.github.antonovichkorp.chemmod.core.gas.ClosedVessel
import io.github.antonovichkorp.chemmod.core.gas.GasFieldProperties
import io.github.antonovichkorp.chemmod.core.gas.GasPhase

/**
 * Public API for computing block mass from real material properties.
 *
 * Mass = volume * effective_density + gas_mass
 * where gas_mass comes from ClosedVessel contents * molar_mass
 */
object BlockMassApi {
    /** Volume of a full Minecraft block in cubic metres. */
    const val BLOCK_VOLUME_CUBIC_METRES = 1.0

    /**
     * Compute the mass of a solid block made of the given material.
     * Returns null if no density is registered (NO_MODEL).
     */
    fun solidBlockMassKilograms(registry: MaterialDensityRegistry, materialId: MaterialId): Double? {
        val density = registry.getOrNull(materialId)
        return density?.let { BLOCK_VOLUME_CUBIC_METRES * it.effectiveDensity() }
    }

    /**
     * Compute the mass of a hollow/pipe/shell/tank block.
     * packingFactor = fraction of block volume occupied by solid material.
     */
    fun hollowBlockMassKilograms(registry: MaterialDensityRegistry, materialId: MaterialId, packingFactor: Double): Double? {
        val density = registry.getOrNull(materialId)
        return density?.let { BLOCK_VOLUME_CUBIC_METRES * it.densityKilogramsPerCubicMeter * packingFactor }
    }

    /**
     * Compute mass of gas inside a ClosedVessel.
     * Sum over species: moles * molar_mass(g/mol) / 1000 = kg
     */
    fun gasMassKilograms(vessel: ClosedVessel, properties: GasFieldProperties): Double {
        var totalKg = 0.0
        for (species in vessel.species()) {
            val moles = vessel.molesOf(species)
            val molarMass = properties.molarMassGramsPerMole(species) / 1000.0
            totalKg += moles * molarMass
        }
        return totalKg
    }

    /**
     * Total mass of a gas tank block = shell mass + contained gas mass.
     * Returns null if shell material has no density (NO_MODEL).
     */
    fun gasTankTotalMassKilograms(
        registry: MaterialDensityRegistry,
        shellMaterialId: MaterialId,
        shellPackingFactor: Double,
        vessel: ClosedVessel,
        properties: GasFieldProperties,
    ): Double? {
        val shellMass = hollowBlockMassKilograms(registry, shellMaterialId, shellPackingFactor)
        return shellMass?.let { it + gasMassKilograms(vessel, properties) }
    }
}