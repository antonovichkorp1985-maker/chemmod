package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Structural risk flags are derived from the graph. They never come from a substance record,
 * so a structure nobody named still reports its own hazards.
 */
class PropertyFlagsTest {
    private fun flags(structure: String): Set<String> =
        PropertyPredictor.predict(Molecule.fromSMILESlike(structure).graph).flags

    @Test
    fun `a three-membered ring reports strain on top of being cyclic`() {
        val cyclopropane = flags("C1CC1")
        assertTrue("CYCLIC_STRUCTURE" in cyclopropane)
        assertTrue("SMALL_RING_STRAIN" in cyclopropane)
    }

    @Test
    fun `a strain-free ring stays cyclic without the strain flag`() {
        val cyclohexane = flags("C1CCCCC1")
        assertTrue("CYCLIC_STRUCTURE" in cyclohexane)
        assertFalse("SMALL_RING_STRAIN" in cyclohexane)
    }

    @Test
    fun `a nitrogen-oxygen bond is reported`() {
        assertTrue("NITROGEN_OXYGEN_BOND" in flags("NO"))
        assertFalse("NITROGEN_OXYGEN_BOND" in flags("CN"))
    }

    @Test
    fun `halogen content is reported for every halogen`() {
        listOf("CF", "CCl", "CBr", "CI").forEach { structure ->
            assertTrue("HALOGENATED" in flags(structure), "$structure should be flagged as halogenated")
        }
        assertFalse("HALOGENATED" in flags("CCO"))
    }

    @Test
    fun `an ordinary alcohol earns none of the new hazard flags`() {
        val ethanol = flags("CCO")
        assertFalse("SMALL_RING_STRAIN" in ethanol)
        assertFalse("NITROGEN_OXYGEN_BOND" in ethanol)
        assertFalse("HALOGENATED" in ethanol)
        assertFalse("CYCLIC_STRUCTURE" in ethanol)
        assertTrue("COMBUSTIBLE_ESTIMATE" in ethanol, "ethanol is a C/H/O fuel and keeps its existing estimate")
    }

    @Test
    fun `existing peroxide detection is unchanged`() {
        assertTrue("PEROXIDE_BOND" in flags("OO"))
        assertFalse("PEROXIDE_BOND" in flags("O"))
    }
}
