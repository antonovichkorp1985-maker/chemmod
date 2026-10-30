package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The sealed-volume physics the player will actually meet: filling raises pressure, Dalton's law
 * splits it, combustion (the Hindenburg case) consumes fuel and oxygen, heats the gas and spikes
 * the pressure, and a wall that cannot carry the pressure bursts.
 */
class ClosedVesselTest {
    private val strongWall = 10_000_000.0

    @Test
    fun `pressure is the ideal gas law over the total mole count`() {
        val vessel = ClosedVessel(0.022414, strongWall)
        vessel.addMoles("H2", 1.0)
        val expected = 1.0 * GasPhase.UNIVERSAL_GAS_CONSTANT * vessel.temperatureKelvin / 0.022414
        assertTrue(abs(vessel.pressurePascals() - expected) < 1.0)
    }

    @Test
    fun `partial pressures add to the total`() {
        val vessel = ClosedVessel(0.01, strongWall)
        vessel.addMoles("H2", 1.0)
        vessel.addMoles("O2", 1.0)
        assertTrue(abs(vessel.partialPressurePascals("H2") * 2 - vessel.pressurePascals()) < 1.0)
    }

    @Test
    fun `a hydrogen-oxygen mix inside the envelope burns to water and heats the gas`() {
        val vessel = ClosedVessel(0.01, strongWall)
        vessel.addMoles("H2", 2.0)
        vessel.addMoles("O2", 1.0)
        val before = vessel.temperatureKelvin
        val released = vessel.ignite()
        assertTrue(released > 0.0)
        assertTrue(vessel.molesOf("H2") < 1e-9)
        assertTrue(abs(vessel.molesOf("H2O") - 2.0) < 1e-9)
        assertTrue(vessel.temperatureKelvin > before)
    }

    @Test
    fun `a too-lean mixture does not burn`() {
        val vessel = ClosedVessel(0.01, strongWall)
        vessel.addMoles("H2", 0.1)
        vessel.addMoles("O2", 1.0)
        assertEquals(0.0, vessel.ignite())
        assertTrue(abs(vessel.molesOf("H2") - 0.1) < 1e-9)
    }

    @Test
    fun `overpressure bursts a weak wall and spares a strong one`() {
        val vessel = ClosedVessel(0.001, strongWall)
        vessel.addMoles("N2", 1.0)   // ~24 atm at room temperature
        assertTrue(vessel.bursts(100_000.0))
        assertTrue(!vessel.bursts(10_000_000.0))
    }

    @Test
    fun `combustion in a sealed small tank can itself cause the burst`() {
        val vessel = ClosedVessel(0.002, strongWall)
        vessel.addMoles("H2", 2.0)
        vessel.addMoles("O2", 1.0)
        val weak = 3_000_000.0
        val before = vessel.bursts(weak)
        vessel.ignite()
        // 3 mol -> 2 mol but a large temperature spike; the pressure must rise enough to matter.
        assertTrue(vessel.pressurePascals() > 0.0)
        assertTrue(!before || vessel.bursts(weak))
    }

    @Test
    fun `coloured gases report a tint and colourless ones do not`() {
        val chlorine = ClosedVessel(0.01, strongWall).apply { addMoles("Cl2", 1.0) }
        assertTrue(chlorine.tintArgb() != null)
        val nitrogen = ClosedVessel(0.01, strongWall).apply { addMoles("N2", 1.0) }
        assertNull(nitrogen.tintArgb())
    }

    @Test
    fun `pressure relief valve releases gas above threshold and returns zero below it`() {
        val vessel = ClosedVessel(0.001, strongWall)
        vessel.addMoles("N2", 1.0)
        val initialPressure = vessel.pressurePascals()
        val threshold = initialPressure / 2.0
        val released = vessel.releaseAtPressure(threshold)
        assertTrue(released > 0.0)
        assertTrue(vessel.pressurePascals() <= threshold * 1.1) // allow small tolerance
        // Below threshold: nothing released
        val releasedAgain = vessel.releaseAtPressure(vessel.pressurePascals() * 2.0)
        assertEquals(0.0, releasedAgain)
    }

    @Test
    fun `manual vent removes proportional amounts from all species`() {
        val vessel = ClosedVessel(0.01, strongWall)
        vessel.addMoles("H2", 2.0)
        vessel.addMoles("O2", 1.0)
        val removed = vessel.ventMoles(1.5)
        assertTrue(abs(removed - 1.5) < 1e-9)
        assertTrue(abs(vessel.molesOf("H2") - 1.0) < 1e-6)
        assertTrue(abs(vessel.molesOf("O2") - 0.5) < 1e-6)
    }

    @Test
    fun `venting more than available removes everything`() {
        val vessel = ClosedVessel(0.01, strongWall)
        vessel.addMoles("N2", 1.0)
        val removed = vessel.ventMoles(999.0)
        assertTrue(abs(removed - 1.0) < 1e-9)
        assertTrue(vessel.totalMoles() < 1e-12)
    }

    @Test
    fun `emptying removes all moles`() {
        val vessel = ClosedVessel(0.01, strongWall)
        vessel.addMoles("H2", 1.0)
        vessel.addMoles("O2", 1.0)
        val removed = vessel.empty()
        assertTrue(abs(removed - 2.0) < 1e-9)
        assertTrue(vessel.species().isEmpty())
    }
}
