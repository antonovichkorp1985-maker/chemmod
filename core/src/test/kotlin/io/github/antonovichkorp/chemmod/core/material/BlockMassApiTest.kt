package io.github.antonovichkorp.chemmod.core.material

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.gas.ClosedVessel
import io.github.antonovichkorp.chemmod.core.gas.GasFieldProperties
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlockMassApiTest {

    @Test
    fun `solid copper block mass from real density`() {
        val registry = MaterialDensityRegistry.withDefaults()
        val mass = BlockMassApi.solidBlockMassKilograms(registry, MaterialId.of("chemmod:copper"))
        assertNotNull(mass)
        assertEquals(8960.0, mass!!, 1e-6, "1 m^3 copper at 8960 kg/m3")
    }

    @Test
    fun `unregistered material returns null NO_MODEL`() {
        val registry = MaterialDensityRegistry()
        val mass = BlockMassApi.solidBlockMassKilograms(registry, MaterialId.of("chemmod:unknown"))
        assertNull(mass)
    }

    @Test
    fun `hollow tank shell mass scales with packing factor`() {
        val registry = MaterialDensityRegistry.withDefaults()
        val solid = BlockMassApi.hollowBlockMassKilograms(registry, MaterialId.of("chemmod:steel"), 1.0)!!
        val half = BlockMassApi.hollowBlockMassKilograms(registry, MaterialId.of("chemmod:steel"), 0.5)!!
        assertEquals(solid / 2.0, half, 1e-6)
    }

    @Test
    fun `gas mass inside tank adds to total`() {
        val vessel = ClosedVessel(0.001, 1_000_000.0)
        vessel.addMoles("O2", 10.0) // ~0.32 kg O2 at 32 g/mol
        val gasMass = BlockMassApi.gasMassKilograms(vessel, GasSpeciesPropertiesStub())
        assertTrue(gasMass > 0.0)
        // 10 mol * 0.032 kg/mol = 0.32 kg
        assertEquals(0.32, gasMass, 1e-6)
    }

    @Test
    fun `gas tank total includes shell plus gas`() {
        val vessel = ClosedVessel(0.001, 1_000_000.0)
        vessel.addMoles("O2", 10.0)
        val total = BlockMassApi.gasTankTotalMassKilograms(
            MaterialDensityRegistry.withDefaults(),
            MaterialId.of("chemmod:steel"), 0.1, vessel, GasSpeciesPropertiesStub())
        assertNotNull(total)
        val shellOnly = BlockMassApi.hollowBlockMassKilograms(
            MaterialDensityRegistry.withDefaults(), MaterialId.of("chemmod:steel"), 0.1)
        assertTrue(total!! > shellOnly!!, "Total must exceed shell-only mass")
    }
}

/** Minimal GasFieldProperties stub for tests. */
class GasSpeciesPropertiesStub : GasFieldProperties {
    override fun molarMassGramsPerMole(species: String): Double {
        return when (species) {
            "O2" -> 32.0
            "N2" -> 28.0
            "H2" -> 2.0
            "CO2" -> 44.0
            else -> 28.97
        }
    }

    override fun diffusionSquareMetersPerSecond(species: String): Double? = null

    override fun molarMassGramsPerMole(species: Molecule): Double = 28.97

    override fun diffusionSquareMetersPerSecond(species: Molecule): Double? = null
}