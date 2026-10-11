package io.github.antonovichkorp.chemmod.core.catalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A catalyst is not a listed item: it earns capabilities from the elements it is made of.
 * These tests pin that derivation for both bulk materials and molecular substances.
 */
class CatalystModelTest {
    private val model = CatalystModel.bundled()

    @Test
    fun `copper earns hydrogenation and dehydrogenation from its composition`() {
        val earned = model.earnedBy(setOf("Cu"))
        assertTrue("chemmod:hydrogenation_metal" in earned)
        assertTrue("chemmod:dehydrogenation_metal" in earned)
        assertFalse("chemmod:oxidation_metal" in earned)
    }

    @Test
    fun `a silicate earns no metal capability`() {
        assertEquals(emptySet(), model.earnedBy(setOf("Si", "O")))
    }

    @Test
    fun `capabilities follow the bulk material content`() {
        assertTrue("chemmod:hydrogenation_metal" in CatalystModel.capabilitiesOfMaterial("chemmod:copper"))
        assertEquals(emptySet(), CatalystModel.capabilitiesOfMaterial("chemmod:silicate_gangue"))
    }

    @Test
    fun `an unknown material earns nothing instead of failing`() {
        assertEquals(emptySet(), CatalystModel.capabilitiesOfMaterial("chemmod:not_a_material"))
    }

    @Test
    fun `a molecular substance is judged by its own graph`() {
        assertEquals(emptySet(), CatalystModel.capabilitiesOfStructure("CCO"))
    }

    @Test
    fun `every declared capability has an element condition`() {
        assertTrue(model.capabilities.isNotEmpty())
        assertTrue(model.capabilities.all { it.startsWith("chemmod:") })
    }
}
