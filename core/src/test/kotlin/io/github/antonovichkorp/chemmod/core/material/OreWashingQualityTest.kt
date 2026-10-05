package io.github.antonovichkorp.chemmod.core.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OreWashingQualityTest {
    @Test
    fun `clean water retains less measured gangue than saline water`() {
        val naturalCopper = mapOf("chemmod:silicate_gangue" to 150_000)

        val clean = OreWashingQuality.wash(850_000, naturalCopper, 0, "chemmod:silicate_gangue")
        val saline = OreWashingQuality.wash(850_000, naturalCopper, 35_000, "chemmod:silicate_gangue")

        assertEquals(986_500, clean.purityPpm)
        assertEquals(mapOf("chemmod:silicate_gangue" to 13_500), clean.impuritiesPpm)
        assertEquals(960_250, saline.purityPpm)
        assertEquals(mapOf("chemmod:silicate_gangue" to 39_750), saline.impuritiesPpm)
        assertTrue(clean.purityPpm > saline.purityPpm)
    }

    @Test
    fun `washing keeps pure reference material pure`() {
        val washed = OreWashingQuality.wash(1_000_000, emptyMap(), 250_000, "chemmod:silicate_gangue")

        assertEquals(1_000_000, washed.purityPpm)
        assertTrue(washed.impuritiesPpm.isEmpty())
    }

    @Test
    fun `unidentified impurity remains visible as fallback gangue`() {
        val washed = OreWashingQuality.wash(850_000, emptyMap(), 0, "chemmod:silicate_gangue")

        assertEquals(986_500, washed.purityPpm)
        assertEquals(mapOf("chemmod:silicate_gangue" to 13_500), washed.impuritiesPpm)
    }
}
