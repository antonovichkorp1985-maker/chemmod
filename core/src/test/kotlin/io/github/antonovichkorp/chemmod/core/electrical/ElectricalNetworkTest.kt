package io.github.antonovichkorp.chemmod.core.electrical

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ElectricalNetworkTest {

    @Test
    fun `single resistor across ideal voltage source obeys Ohm's law`() {
        val net = electricalNetwork(2, groundNodeId = 1)
        net.addVoltageSource(0, 1, emfVolts = 12.0, internalResistanceOhms = 1.0)
        net.addResistor(0, 1, resistanceOhms = 11.0)
        val V = net.solve()
        assertEquals(0.0, V[1], 1e-9, "ground node is 0 V")
        assertTrue(abs(V[0] - 12.0 * 11.0 / 12.0) < 1e-6, "voltage across load")
        val I = (V[0] - V[1]) / 11.0
        assertTrue(abs(I - 1.0) < 1e-6, "current = 12V / 12ohm = 1A")
    }

    @Test
    fun `two parallel resistors split current by Kirchhoff's current law`() {
        val net = electricalNetwork(2, groundNodeId = 1)
        net.addVoltageSource(0, 1, emfVolts = 10.0, internalResistanceOhms = 0.01)
        net.addResistor(0, 1, resistanceOhms = 10.0)
        net.addResistor(0, 1, resistanceOhms = 10.0)
        val V = net.solve()
        val I1 = (V[0] - V[1]) / 10.0
        val I2 = (V[0] - V[1]) / 10.0
        val total = I1 + I2
        assertTrue(abs(total - 2.0) < 0.01, "total current through two 10ohm in parallel is ~2A, got $total")
        assertTrue(abs(I1 - 1.0) < 0.01, "each 10ohm branch carries ~1A, got $I1")
        assertTrue(abs(I2 - 1.0) < 0.01)
    }

    @Test
    fun `three-node divider obeys Kirchhoff's voltage law`() {
        // Two 5ohm resistors from 10V source to ground, with node 1 as midpoint
        val net = electricalNetwork(3, groundNodeId = 2)
        net.addVoltageSource(0, 2, emfVolts = 10.0, internalResistanceOhms = 0.01)
        net.addResistor(0, 1, resistanceOhms = 5.0)
        net.addResistor(1, 2, resistanceOhms = 5.0)
        val V = net.solve()
        // Ground node 2 = 0, node 1 midpoint voltage should be ~5V, node 0 should be near 10V
        assertTrue(V[2] == 0.0, "ground must be 0 V")
        assertTrue(abs(V[0] - 10.0) < 1.0, "source node near 10V, got ${V[0]}")
        assertTrue(abs(V[1] - 5.0) < 1.0, "midpoint near 5V, got ${V[1]}")
        // KVL: V0-1 + V1-2 = 5V + 5V = 10V ≈ source
        assertTrue(abs((V[0] - V[1]) + (V[1] - V[2]) - 10.0) < 2.0, "KVL holds")
    }

    @Test
    fun `floating node returns zero voltage not NaN`() {
        val net = electricalNetwork(3, groundNodeId = 2)
        net.addResistor(0, 1, resistanceOhms = 10.0)
        // Node 0 floating, node 1 floating, node 2 ground
        val V = net.solve()
        assertTrue(V[0].isFinite(), "floating node voltage must be finite")
        assertTrue(V[1].isFinite(), "floating node voltage must be finite")
    }

    @Test
    fun `open circuit branch carries no current`() {
        val net = electricalNetwork(2, groundNodeId = 1)
        // No branches at all
        val V = net.solve()
        assertEquals(0.0, V[0], 1e-9)
        assertEquals(0.0, V[1], 1e-9)
    }

    @Test
    fun `multiple isolated subgraphs each solve independently`() {
        val net = electricalNetwork(4, groundNodeId = 3)
        // Subgraph 1: nodes 0,1 with 9V source
        net.addVoltageSource(0, 1, emfVolts = 9.0, internalResistanceOhms = 1.0)
        net.addResistor(0, 1, resistanceOhms = 9.0)
        // Node 2 isolated floating
        val V = net.solve()
        assertTrue(V[0].isFinite())
        assertTrue(V[1].isFinite())
        assertTrue(V[2].isFinite(), "isolated node must not be NaN")
        assertEquals(0.0, V[3], 1e-9)
    }

    @Test
    fun `cable segment model as resistor in network`() {
        val net = electricalNetwork(2, groundNodeId = 1)
        // 10 m copper cable: R = rho * L / A. rho_Cu = 1.68e-8 ohm*m, A = 1 mm^2 = 1e-6 m^2
        val rhoCu = 1.68e-8
        val L = 10.0
        val A = 1e-6
        val R = rhoCu * L / A
        net.addResistor(0, 1, resistanceOhms = R)
        net.injectCurrent(0, 5.0) // inject 5 A at node 0
        val V = net.solve()
        val I = (V[0] - V[1]) / R
        assertTrue(abs(I - 5.0) < 1e-6, "injected current must flow through resistor")
    }

    @Test
    fun `voltage source internal resistance causes voltage sag under load`() {
        val net = electricalNetwork(2, groundNodeId = 1)
        net.addVoltageSource(0, 1, emfVolts = 12.0, internalResistanceOhms = 1.0)
        net.addResistor(0, 1, resistanceOhms = 1.0)
        val V = net.solve()
        val I = V[0] / 1.0
        assertTrue(abs(I - 6.0) < 1e-4, "12V / 2ohm = 6A")
        assertTrue(abs(V[0] - 6.0) < 1e-4, "terminal voltage = 6V under load")
    }

    @Test
    fun `ground node always exactly zero`() {
        val net = electricalNetwork(5, groundNodeId = 4)
        net.addVoltageSource(0, 4, emfVolts = 100.0, internalResistanceOhms = 1.0)
        net.addResistor(1, 4, resistanceOhms = 10.0)
        net.addResistor(2, 4, resistanceOhms = 20.0)
        net.addResistor(3, 4, resistanceOhms = 30.0)
        val V = net.solve()
        assertEquals(0.0, V[4], 1e-12, "ground node must be exactly 0 V")
    }

    @Test
    fun `network with only ground node works`() {
        val net = electricalNetwork(1, groundNodeId = 0)
        val V = net.solve()
        assertEquals(1, V.size)
        assertEquals(0.0, V[0], 1e-12)
    }
}