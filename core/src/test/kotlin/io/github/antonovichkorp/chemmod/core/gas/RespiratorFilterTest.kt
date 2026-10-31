package io.github.antonovichkorp.chemmod.core.gas

import io.github.antonovichkorp.chemmod.core.gas.GasTransport.RespiratorFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Tests for the RespiratorFilter finite-sorbent-capacity model.
 * CM-2: inhalation exposure with consumable sorbent.
 */
class RespiratorFilterTest {
    private val filter100 = RespiratorFilter(capacityMicromoles = 100.0)

    @Test
    fun `filter starts with full capacity`() {
        assertEquals(100.0, filter100.remainingCapacity())
    }

    @Test
    fun `adsorb within capacity succeeds`() {
        val f2 = filter100.adsorb(30.0)
        assertNotNull(f2)
        assertEquals(70.0, f2!!.remainingCapacity())
    }

    @Test
    fun `adsorb exceeding capacity fails`() {
        val result = filter100.adsorb(200.0)
        assertNull(result, "adsorbing more than capacity must return null")
    }

@Test
    fun `chained adsorptions accumulate`() {
        val f1 = filter100.adsorb(40.0)!!
        val f2 = f1!!.adsorb(30.0)
        assertNotNull(f2)
        assertEquals(30.0, f2!!.remainingCapacity())
        // 40 + 30 = 70 consumed, remaining 30; one more mole exceeds capacity
        val f3c = f2!!.adsorb(31.0)
        assertNull(f3c, "adding more than remaining capacity must fail")
        // exactly fill: 70 + 30 = 100
        val f3b = f2!!.adsorb(30.0)
        assertNotNull(f3b)
        assertEquals(0.0, f3b!!.remainingCapacity())
    }

    @Test
    fun `zero-capacity filter rejects all`() {
        val zero = RespiratorFilter(capacityMicromoles = 0.0)
        assertNull(zero.adsorb(1.0))
        assertEquals(0.0, zero.remainingCapacity())
    }
}