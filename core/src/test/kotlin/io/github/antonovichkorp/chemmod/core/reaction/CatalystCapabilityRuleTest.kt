package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rules gated by a derived catalyst capability. The rule names a capability; the catalyst earns
 * it from its composition, so no rule enumerates acceptable items.
 */
class CatalystCapabilityRuleTest {
    private val engine = ReactionEngine.bundled()
    private val hydrogen = Molecule.fromSMILESlike("[H][H]")

    private fun environment(capabilities: Set<String>, temperatureKelvin: Double = 600.0) = ReactionEnvironment(
        temperatureKelvin = temperatureKelvin,
        pressureKilopascals = 101.325,
        availableCanonicalKeys = setOf(hydrogen.canonicalKey()),
        catalystCapabilities = capabilities,
    )

    private fun apply(structure: String, ruleId: String, environment: ReactionEnvironment) =
        engine.apply(Molecule.fromSMILESlike(structure), ReactionRuleId.of(ruleId), environment)

    @Test
    fun `a nitrile becomes an imine over a hydrogenation metal`() {
        val outcome = apply("CC#N", "chemmod:nitrile_hydrogenation", environment(setOf("chemmod:hydrogenation_metal"))).single()
        assertTrue(outcome.isConserved())
        assertEquals("C2H5N", outcome.products.single().molecule.formula())
    }

    @Test
    fun `an imine becomes an amine over the same capability`() {
        val outcome = apply("CC=N", "chemmod:imine_hydrogenation", environment(setOf("chemmod:hydrogenation_metal"))).single()
        assertTrue(outcome.isConserved())
        assertEquals("C2H7N", outcome.products.single().molecule.formula())
    }

    @Test
    fun `an amine gives the imine back over a dehydrogenation metal`() {
        val outcome = apply("CCN", "chemmod:amine_dehydrogenation", environment(setOf("chemmod:dehydrogenation_metal"))).single()
        assertTrue(outcome.isConserved())
        assertEquals(
            setOf("C2H5N", "H2"),
            outcome.products.map { it.molecule.formula() }.toSet(),
        )
    }

    @Test
    fun `the wrong capability is not a catalyst for the rule`() {
        assertEquals(
            emptyList(),
            apply("CC#N", "chemmod:nitrile_hydrogenation", environment(setOf("chemmod:oxidation_metal"))),
        )
        assertEquals(emptyList(), apply("CC#N", "chemmod:nitrile_hydrogenation", environment(emptySet())))
    }

    @Test
    fun `capability rules stay gated by temperature and by the co-reactant`() {
        assertEquals(
            emptyList(),
            apply("CC#N", "chemmod:nitrile_hydrogenation", environment(setOf("chemmod:hydrogenation_metal"), 300.0)),
        )
        val noHydrogen = ReactionEnvironment(
            temperatureKelvin = 600.0,
            pressureKilopascals = 101.325,
            catalystCapabilities = setOf("chemmod:hydrogenation_metal"),
        )
        assertEquals(emptyList(), apply("CC#N", "chemmod:nitrile_hydrogenation", noHydrogen))
    }
}
