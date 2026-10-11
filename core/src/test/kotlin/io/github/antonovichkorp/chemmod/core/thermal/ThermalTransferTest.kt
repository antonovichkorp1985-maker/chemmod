package io.github.antonovichkorp.chemmod.core.thermal

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThermalTransferTest {
    private fun close(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(abs(expected - actual) <= tolerance * maxOf(1.0, abs(expected)), "$expected != $actual")
    }
    private val hot = ThermalBody(100.0, 500.0)
    private val cold = ThermalBody(400.0, 300.0)

    @Test fun finiteContactDoesNotInstantlyEqualizeTemperatures() {
        val result = ThermalTransfer.contact(hot, cold, 8.0, 1.0)
        val expectedHeat = 80.0 * 200.0 * (1.0 - exp(-0.1))
        close(expectedHeat, result.heatFromFirstToSecondJoules)
        close(500.0 - expectedHeat / 100.0, result.first.temperatureKelvin)
        close(300.0 + expectedHeat / 400.0, result.second.temperatureKelvin)
        assertTrue(result.first.temperatureKelvin > result.second.temperatureKelvin)
    }

    @Test fun isolatedContactConservesEnergyAcrossDifferentCapacities() {
        for (duration in listOf(0.001, 0.5, 1.0, 20.0, 1e8)) {
            val result = ThermalTransfer.contact(hot, cold, 8.0, duration)
            close(hot.heatCapacityJoulesPerKelvin * hot.temperatureKelvin + cold.heatCapacityJoulesPerKelvin * cold.temperatureKelvin,
                result.first.heatCapacityJoulesPerKelvin * result.first.temperatureKelvin + result.second.heatCapacityJoulesPerKelvin * result.second.temperatureKelvin)
            close(result.heatFromFirstToSecondJoules, 100.0 * (500.0 - result.first.temperatureKelvin))
            close(result.heatFromFirstToSecondJoules, 400.0 * (result.second.temperatureKelvin - 300.0))
        }
    }

    @Test fun contactIsSymmetricAndHeatSignIsExplicit() {
        val forward = ThermalTransfer.contact(hot, cold, 7.0, 4.0)
        val reverse = ThermalTransfer.contact(cold, hot, 7.0, 4.0)
        close(forward.first.temperatureKelvin, reverse.second.temperatureKelvin)
        close(forward.second.temperatureKelvin, reverse.first.temperatureKelvin)
        close(forward.heatFromFirstToSecondJoules, -reverse.heatFromFirstToSecondJoules)
    }

    @Test fun contactTimestepSubdivisionMatchesOneLongStep() {
        val full = ThermalTransfer.contact(hot, cold, 8.0, 10.0)
        var a = hot; var b = cold; var total = 0.0
        repeat(200) {
            val next = ThermalTransfer.contact(a, b, 8.0, 0.05)
            a = next.first; b = next.second; total += next.heatFromFirstToSecondJoules
        }
        close(full.first.temperatureKelvin, a.temperatureKelvin)
        close(full.second.temperatureKelvin, b.temperatureKelvin)
        close(full.heatFromFirstToSecondJoules, total)
    }

    @Test fun largeConductanceNeverOvershootsWeightedEquilibrium() {
        val result = ThermalTransfer.contact(hot, cold, Double.MAX_VALUE, 1e5)
        close(340.0, result.first.temperatureKelvin)
        close(340.0, result.second.temperatureKelvin)
        close(16000.0, result.heatFromFirstToSecondJoules)
    }

    @Test fun zeroContactTimeOrConductanceAndEqualTemperaturesDoNothing() {
        for ((conductance, time) in listOf(0.0 to 3.0, 2.0 to 0.0)) {
            val result = ThermalTransfer.contact(hot, cold, conductance, time)
            assertSame(hot, result.first); assertSame(cold, result.second)
            assertEquals(0.0, result.heatFromFirstToSecondJoules)
        }
        val result = ThermalTransfer.contact(hot, cold.copy(temperatureKelvin = 500.0), 3.0, 10.0)
        assertEquals(0.0, result.heatFromFirstToSecondJoules)
    }

    @Test fun heaterAccountsForFiniteEnergyAndThermalMass() {
        val result = ThermalTransfer.powered(cold, 100.0, 290.0, 0.0, 4.0)
        close(400.0, result.suppliedEnergyJoules)
        close(301.0, result.body.temperatureKelvin)
        assertEquals(0.0, result.heatToAmbientJoules)
    }

    @Test fun passiveCoolingApproachesAmbientWithoutCrossingIt() {
        val result = ThermalTransfer.powered(hot, 0.0, 300.0, 10.0, 10.0)
        close(300.0 + 200.0 * exp(-1.0), result.body.temperatureKelvin)
        close(100.0 * (500.0 - result.body.temperatureKelvin), result.heatToAmbientJoules)
        val long = ThermalTransfer.powered(hot, 0.0, 300.0, 10.0, 1e10)
        close(300.0, long.body.temperatureKelvin)
    }

    @Test fun ambientCanHeatAColdBodyAndIsNotFreeUnrecordedEnergy() {
        val body = ThermalBody(400.0, 270.0)
        val result = ThermalTransfer.powered(body, 0.0, 300.0, 10.0, 40.0)
        close(300.0 - 30.0 * exp(-1.0), result.body.temperatureKelvin)
        assertTrue(result.heatToAmbientJoules < 0.0)
        close(-result.heatToAmbientJoules, 400.0 * (result.body.temperatureKelvin - body.temperatureKelvin))
    }

    @Test fun poweredLossyBodyApproachesPowerDependentSteadyState() {
        val body = ThermalBody(100.0, 300.0)
        val result = ThermalTransfer.powered(body, 200.0, 300.0, 10.0, 10.0)
        close(300.0 + 20.0 * (1.0 - exp(-1.0)), result.body.temperatureKelvin)
        close(2000.0, result.suppliedEnergyJoules)
        close(100.0 * (result.body.temperatureKelvin - 300.0), result.netEnergyIntoBodyJoules)
        val steady = ThermalTransfer.powered(body, 200.0, 300.0, 10.0, 1e5)
        close(320.0, steady.body.temperatureKelvin)
    }

    @Test fun poweredTimestepSubdivisionPreservesTemperatureAndEnergyLedger() {
        val full = ThermalTransfer.powered(hot, 75.0, 290.0, 5.0, 10.0)
        var body = hot; var supplied = 0.0; var lost = 0.0
        repeat(200) {
            val next = ThermalTransfer.powered(body, 75.0, 290.0, 5.0, 0.05)
            body = next.body; supplied += next.suppliedEnergyJoules; lost += next.heatToAmbientJoules
        }
        close(full.body.temperatureKelvin, body.temperatureKelvin)
        close(full.suppliedEnergyJoules, supplied)
        close(full.heatToAmbientJoules, lost)
    }

    @Test fun shortStepStillAccountsForVerySmallAmbientLoss() {
        val result = ThermalTransfer.powered(ThermalBody(100.0, 300.0), 100.0, 300.0, 1.0, 1e-7)
        // Starting at ambient, loss is P*dt*x/2 to leading order, with x = 1e-9.
        assertTrue(result.heatToAmbientJoules > 0.0)
        assertTrue(abs(result.heatToAmbientJoules / 5e-15 - 1.0) < 1e-8)
    }

    @Test fun zeroPoweredTimeDoesNotChangeStateOrSupplyEnergy() {
        val result = ThermalTransfer.powered(hot, 100.0, 300.0, 5.0, 0.0)
        assertSame(hot, result.body)
        assertEquals(0.0, result.suppliedEnergyJoules)
        assertEquals(0.0, result.heatToAmbientJoules)
    }

    @Test fun absoluteZeroAndLargeHeatCapacitiesDoNotCreateNegativeTemperatures() {
        val result = ThermalTransfer.contact(ThermalBody(1e300, 0.0), ThermalBody(1e300, 1.0), 1e300, 1.0)
        assertTrue(result.first.temperatureKelvin in 0.0..1.0)
        assertTrue(result.second.temperatureKelvin in 0.0..1.0)
        close(1.0, result.first.temperatureKelvin + result.second.temperatureKelvin)
        val cooled = ThermalTransfer.powered(hot, 0.0, 0.0, Double.MAX_VALUE, 1.0)
        assertEquals(0.0, cooled.body.temperatureKelvin)
    }

    @Test fun invalidParametersAreRefusedEvenOnOtherwiseNoOpSteps() {
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { ThermalBody(bad, 300.0) }
            assertFailsWith<IllegalArgumentException> { ThermalBody(10.0, bad) }
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.contact(hot, cold, bad, 0.0) }
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.contact(hot, cold, 0.0, bad) }
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.powered(hot, bad, 300.0, 0.0, 0.0) }
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.powered(hot, 0.0, bad, 0.0, 0.0) }
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.powered(hot, 0.0, 300.0, bad, 0.0) }
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.powered(hot, 0.0, 300.0, 0.0, bad) }
        }
        assertFailsWith<IllegalArgumentException> { ThermalBody(0.0, 300.0) }
    }

    @Test fun unrepresentableEnergyRefusesWithoutMutatingInputs() {
        assertFailsWith<ArithmeticException> { ThermalTransfer.powered(hot, Double.MAX_VALUE, 300.0, 0.0, 2.0) }
        assertFailsWith<ArithmeticException> { ThermalTransfer.contact(ThermalBody(1e300, 1e300), ThermalBody(1e300, 0.0), 1e300, 10.0) }
        assertEquals(ThermalBody(100.0, 500.0), hot)
        assertEquals(ThermalBody(400.0, 300.0), cold)
    }

    @Test fun reservoirApproachesMaintainedTemperatureWithoutChargingItself() {
        val result = ThermalTransfer.reservoir(cold, 900.0, 240.0, 1.0)
        close(900.0 - 600.0 * exp(-0.6), result.body.temperatureKelvin)
        close(400.0 * (result.body.temperatureKelvin - 300.0), result.heatFromReservoirJoules)
        assertTrue(result.body.temperatureKelvin in 300.0..900.0)
    }

    @Test fun reservoirHeatingAndCoolingShareOneSignedLedger() {
        val heated = ThermalTransfer.reservoir(cold, 900.0, 240.0, 1.0)
        val cooled = ThermalTransfer.reservoir(hot, 300.0, 20.0, 5.0)
        close(300.0 + 200.0 * exp(-1.0), cooled.body.temperatureKelvin)
        assertTrue(heated.heatFromReservoirJoules > 0.0)
        assertTrue(cooled.heatFromReservoirJoules < 0.0)
        close(100.0 * (cooled.body.temperatureKelvin - 500.0), cooled.heatFromReservoirJoules)
    }

    @Test fun reservoirTimestepSubdivisionMatchesOneLongStep() {
        val full = ThermalTransfer.reservoir(hot, 320.0, 25.0, 10.0)
        var body = hot
        var total = 0.0
        repeat(200) {
            val next = ThermalTransfer.reservoir(body, 320.0, 25.0, 0.05)
            body = next.body
            total += next.heatFromReservoirJoules
        }
        close(full.body.temperatureKelvin, body.temperatureKelvin)
        close(full.heatFromReservoirJoules, total)
    }

    @Test fun zeroReservoirConductanceTimeOrEqualTemperatureDoesNothing() {
        for ((conductance, time) in listOf(0.0 to 4.0, 25.0 to 0.0)) {
            val result = ThermalTransfer.reservoir(cold, 900.0, conductance, time)
            assertSame(cold, result.body)
            assertEquals(0.0, result.heatFromReservoirJoules)
        }
        val result = ThermalTransfer.reservoir(cold, 300.0, 25.0, 10.0)
        assertSame(cold, result.body)
        assertEquals(0.0, result.heatFromReservoirJoules)
    }

    @Test fun saturatedReservoirConductanceReachesReservoirTemperatureExactly() {
        val result = ThermalTransfer.reservoir(hot, 300.0, Double.MAX_VALUE, 1e5)
        assertEquals(300.0, result.body.temperatureKelvin)
        close(-20000.0, result.heatFromReservoirJoules)
    }

    @Test fun reservoirRejectsInvalidParametersWithoutMutatingTheBody() {
        for (bad in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.reservoir(cold, bad, 10.0, 1.0) }
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.reservoir(cold, 400.0, bad, 1.0) }
            assertFailsWith<IllegalArgumentException> { ThermalTransfer.reservoir(cold, 400.0, 10.0, bad) }
        }
        assertEquals(ThermalBody(400.0, 300.0), cold)
    }
}
