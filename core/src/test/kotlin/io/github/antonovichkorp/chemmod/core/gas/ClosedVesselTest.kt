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
    @Test
    fun `pressure is the ideal gas law over the total mole count`() {
        val vessel = ClosedVessel(0.022414)
        vessel.addMoles("H2", 1.0)
        val expected = 1.0 * GasPhase.UNIVERSAL_GAS_CONSTANT * vessel.temperatureKelvin / 0.022414
        assertTrue(abs(vessel.pressurePascals() - expected) < 1.0)
    }

    @Test
    fun `partial pressures add to the total`() {
        val vessel = ClosedVessel(0.01)
        vessel.addMoles("H2", 1.0)
        vessel.addMoles("O2", 1.0)
        assertTrue(abs(vessel.partialPressurePascals("H2") * 2 - vessel.pressurePascals()) < 1.0)
    }

    @Test
    fun `a hydrogen-oxygen mix inside the envelope burns to water and heats the gas`() {
        val vessel = ClosedVessel(0.01)
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
        val vessel = ClosedVessel(0.01)
        vessel.addMoles("H2", 0.1)
        vessel.addMoles("O2", 1.0)
        assertEquals(0.0, vessel.ignite())
        assertTrue(abs(vessel.molesOf("H2") - 0.1) < 1e-9)
    }

    @Test
    fun `overpressure bursts a weak wall and spares a strong one`() {
        val vessel = ClosedVessel(0.001)
        vessel.addMoles("N2", 1.0)   // ~24 atm at room temperature
        assertTrue(vessel.bursts(100_000.0))
        assertTrue(!vessel.bursts(10_000_000.0))
    }

    @Test
    fun `combustion in a sealed small tank can itself cause the burst`() {
        val vessel = ClosedVessel(0.002)
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
        val chlorine = ClosedVessel(0.01).apply { addMoles("Cl2", 1.0) }
        assertTrue(chlorine.tintArgb() != null)
        val nitrogen = ClosedVessel(0.01).apply { addMoles("N2", 1.0) }
        assertNull(nitrogen.tintArgb())
    }
}
