package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.naming.TrivialNameDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReactionEngineTest {
    @Test
    fun `data rule combusts an unlisted molecular graph with automatic balancing`() {
        val butanol = Molecule.fromSMILESlike("CCCCO")
        assertTrue(butanol.validate().isEmpty())
        assertTrue(butanol.canonicalKey().isNotBlank())
        assertNotNull(butanol.properties().boilingPointC)
        assertNotNull(butanol.properties().combustionEnthalpyKilojoulesPerMole)
        assertNull(TrivialNameDirectory.bundled().findByCanonicalKey(butanol.canonicalKey()))

        val oxygen = Molecule.fromSMILESlike("O=O")
        val environment = ReactionEnvironment(
            temperatureKelvin = 600.0,
            pressureKilopascals = 101.325,
            availableCanonicalKeys = setOf(oxygen.canonicalKey()),
        )

        val outcome = assertNotNull(
            ReactionEngine.bundled().apply(butanol, ReactionRuleId.of("chemmod:complete_combustion"), environment)
        )

        assertEquals("C4H10O + 6 O2 -> 4 CO2 + 5 H2O", outcome.formatEquation())
    }

    @Test
    fun `reaction rule cannot consume a co reactant missing from the environment`() {
        val outcome = ReactionEngine.bundled().apply(
            Molecule.fromSMILESlike("CCO"),
            ReactionRuleId.of("chemmod:complete_combustion"),
            ReactionEnvironment(600.0, 101.325),
        )

        assertNull(outcome)
    }

    @Test
    fun `data rule requires its environmental temperature`() {
        val oxygen = Molecule.fromSMILESlike("O=O")
        val outcome = ReactionEngine.bundled().apply(
            Molecule.fromSMILESlike("CCO"),
            ReactionRuleId.of("chemmod:complete_combustion"),
            ReactionEnvironment(298.15, 101.325, availableCanonicalKeys = setOf(oxygen.canonicalKey())),
        )

        assertNull(outcome)
    }

    @Test
    fun `formula matcher rejects fully oxidized and unsupported molecules`() {
        val oxygen = Molecule.fromSMILESlike("O=O")
        val environment = ReactionEnvironment(600.0, 101.325, availableCanonicalKeys = setOf(oxygen.canonicalKey()))
        val engine = ReactionEngine.bundled()
        val rule = ReactionRuleId.of("chemmod:complete_combustion")

        assertNull(engine.apply(Molecule.fromSMILESlike("O=C=O"), rule, environment))
        assertNull(engine.apply(Molecule.fromSMILESlike("Cl-Cl"), rule, environment))
    }
}
