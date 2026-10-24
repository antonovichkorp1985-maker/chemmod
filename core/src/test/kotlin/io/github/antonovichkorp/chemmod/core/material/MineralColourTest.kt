package io.github.antonovichkorp.chemmod.core.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mineral colour held as data next to hardness. The point of the test is the boundary: a mineral
 * that is genuinely multicoloured must carry no colour at all, rather than one arbitrary hue.
 */
class MineralColourTest {
    private val catalogue = MaterialCatalog.bundled()
    private val minerals = catalogue.minerals.values.associateBy { it.id.value }

    private fun colour(id: String) = minerals.getValue("chemmod:$id").colourArgb

    @Test
    fun `malachite is green and azurite is blue`() {
        val malachite = colour("malachite")!!
        val azurite = colour("azurite")!!
        assertTrue(malachite and 0xFF00 == 0x8B00, "malachite green channel was wrong")
        assertTrue(azurite and 0xFF > 0x90, "azurite did not read as blue")
    }

    @Test
    fun `native copper carries the colour of copper`() {
        assertEquals(-4689101, colour("native_copper"))
    }

    @Test
    fun `a colour is always opaque`() {
        catalogue.minerals.values.forEach { mineral ->
            val value = mineral.colourArgb ?: return@forEach
            assertEquals(0xFF, (value ushr 24) and 0xFF, "${mineral.id} was not opaque")
        }
    }

    @Test
    fun `the famously multicoloured minerals carry no colour at all`() {
        for (id in listOf("quartz", "calcite", "fluorite", "apatite")) {
            assertNull(colour(id), "$id was pinned to a single hue")
        }
    }

    @Test
    fun `most of the catalogue is coloured`() {
        val coloured = catalogue.minerals.values.count { it.colourArgb != null }
        assertEquals(28, coloured)
        assertEquals(32, catalogue.minerals.size)
    }
}
