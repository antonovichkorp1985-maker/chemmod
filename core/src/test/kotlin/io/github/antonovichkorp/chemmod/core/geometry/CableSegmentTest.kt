package io.github.antonovichkorp.chemmod.core.geometry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CableSegmentTest {

    @Test
    fun `bare conductor never insulates`() {
        val seg = CableSegment(
            id = "bare-1",
            lengthMetres = 5.0,
            resistanceOhmsPerMetre = 0.01,
            state = CableState.BARE,
            insulationBreakdownTemperatureKelvin = 500.0,
            breakdownVoltageVolts = 1000.0,
            ambientTemperatureKelvin = 293.15,
        )
        assertEquals(CableState.BARE, seg.effectiveState(10.0, 100.0))
    }

    @Test
    fun `insulated segment stays safe below limits`() {
        val seg = CableSegment(
            id = "ins-1",
            lengthMetres = 5.0,
            resistanceOhmsPerMetre = 0.01,
            state = CableState.INSULATED,
            insulationBreakdownTemperatureKelvin = 500.0,
            breakdownVoltageVolts = 1000.0,
            ambientTemperatureKelvin = 293.15,
        )
        assertEquals(CableState.INSULATED, seg.effectiveState(0.1, 100.0))
    }

    @Test
    fun `overvoltage breaches insulation`() {
        val seg = CableSegment(
            id = "ins-2",
            lengthMetres = 5.0,
            resistanceOhmsPerMetre = 0.01,
            state = CableState.INSULATED,
            insulationBreakdownTemperatureKelvin = 500.0,
            breakdownVoltageVolts = 100.0,
            ambientTemperatureKelvin = 293.15,
        )
        assertEquals(CableState.BREACHED, seg.effectiveState(0.1, 200.0))
    }

    @Test
    fun `joule heating scales with current squared`() {
        val seg = CableSegment(
            id = "heat-1",
            lengthMetres = 10.0,
            resistanceOhmsPerMetre = 1.0,
            state = CableState.BARE,
            insulationBreakdownTemperatureKelvin = 1000.0,
            breakdownVoltageVolts = 1000.0,
            ambientTemperatureKelvin = 293.15,
        )
        val p1 = seg.jouleHeatWatts(1.0)
        val p2 = seg.jouleHeatWatts(2.0)
        assertEquals(4.0 * p1, p2, 1e-9, "Joule heat scales with I^2")
    }

    @Test
    fun `bare wet conductor shorts`() {
        val seg = CableSegment(
            id = "short-1",
            lengthMetres = 5.0,
            resistanceOhmsPerMetre = 0.01,
            state = CableState.BARE,
            insulationBreakdownTemperatureKelvin = 500.0,
            breakdownVoltageVolts = 1000.0,
            ambientTemperatureKelvin = 293.15,
            wetOrGrounded = true,
        )
        assertTrue(seg.shortCircuit())
    }

    @Test
    fun `dry bare conductor does not short`() {
        val seg = CableSegment(
            id = "short-2",
            lengthMetres = 5.0,
            resistanceOhmsPerMetre = 0.01,
            state = CableState.BARE,
            insulationBreakdownTemperatureKelvin = 500.0,
            breakdownVoltageVolts = 1000.0,
            ambientTemperatureKelvin = 293.15,
            wetOrGrounded = false,
        )
        assertFalse(seg.shortCircuit())
    }

    @Test
    fun `insulated dry conductor does not short`() {
        val seg = CableSegment(
            id = "short-3",
            lengthMetres = 5.0,
            resistanceOhmsPerMetre = 0.01,
            state = CableState.INSULATED,
            insulationBreakdownTemperatureKelvin = 500.0,
            breakdownVoltageVolts = 1000.0,
            ambientTemperatureKelvin = 293.15,
            wetOrGrounded = true,
        )
        assertFalse(seg.shortCircuit())
    }

    @Test
    fun `breached wet conductor shorts`() {
        val seg = CableSegment(
            id = "short-4",
            lengthMetres = 5.0,
            resistanceOhmsPerMetre = 0.01,
            state = CableState.BREACHED,
            insulationBreakdownTemperatureKelvin = 500.0,
            breakdownVoltageVolts = 1000.0,
            ambientTemperatureKelvin = 293.15,
            wetOrGrounded = true,
        )
        assertTrue(seg.shortCircuit())
    }
}