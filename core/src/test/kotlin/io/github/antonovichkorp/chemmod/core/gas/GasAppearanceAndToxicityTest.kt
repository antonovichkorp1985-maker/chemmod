package io.github.antonovichkorp.chemmod.core.gas

import io.github.antonovichkorp.chemmod.core.gas.GasToxicity.Mechanism
import io.github.antonovichkorp.chemmod.core.gas.GasToxicity.ToxicityAssessment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * ADR-0004 §4 and decision 7: how a gas looks and why it is hazardous, both derived from the
 * substance rather than written per substance.
 */
class GasAppearanceAndToxicityTest {
    private fun mechanisms(findings: List<ToxicityAssessment>): Set<Mechanism> =
        findings.filterIsInstance<ToxicityAssessment.ByMechanism>().map { it.mechanism }.toSet()

    // ---------------------------------------------------------------- appearance

    @Test
    fun `a pure coloured gas keeps exactly its recorded tint`() {
        assertEquals(0xFF_5E_8C_2A.toInt(), GasAppearance.tintArgb(mapOf("Cl2" to 1.0)))
        assertEquals(0xFF_5E_8C_2A.toInt(), GasAppearance.tintArgb("Cl2"))
    }

    @Test
    fun `diluting a coloured gas makes it faint without changing its hue`() {
        val tint = GasAppearance.tintArgb(mapOf("Cl2" to 1.0, "N2" to 3.0))!!
        assertEquals(0x3F, (tint ushr 24) and 0xFF, "a quarter-strength cloud was not a quarter opaque")
        assertEquals(0x5E_8C_2A, tint and 0xFF_FF_FF, "dilution changed the hue instead of the opacity")
    }

    @Test
    fun `a colourless gas reports no tint rather than a placeholder hue`() {
        assertNull(GasAppearance.tintArgb(mapOf("N2" to 1.0, "O2" to 1.0)))
        assertNull(GasAppearance.tintArgb(mapOf("H2" to 1.0)))
        assertNull(GasAppearance.tintArgb(emptyMap()))
        assertNull(GasAppearance.tintArgb("Xx"), "an unknown species must not get a default colour")
    }

    // ------------------------------------------------------------------ toxicity

    @Test
    fun `an acid gas is only acidic where there is moisture`() {
        assertTrue(
            Mechanism.ACID_WITH_MOISTURE in mechanisms(
                GasToxicity.assess(mapOf("HCl" to 0.01, "H2O" to 0.02, "N2" to 0.9, "O2" to 0.07)),
            ),
            "hydrogen chloride in damp air was not reported as making acid",
        )
        assertTrue(
            Mechanism.ACID_WITH_MOISTURE !in mechanisms(
                GasToxicity.assess(mapOf("HCl" to 0.01, "N2" to 0.92, "O2" to 0.07)),
            ),
            "dry hydrogen chloride was reported as making acid with no water present",
        )
    }

    @Test
    fun `carbon monoxide is reported by its binding, not by a dose number`() {
        val findings = GasToxicity.assess(mapOf("CO" to 0.001, "N2" to 0.78, "O2" to 0.209))
        assertEquals(setOf(Mechanism.CARBON_MONOXIDE_BINDING), mechanisms(findings))
        assertTrue(
            findings.filterIsInstance<ToxicityAssessment.ByMechanism>().all { it.detail.isNotBlank() },
            "a mechanism was reported without saying why it is hazardous",
        )
    }

    @Test
    fun `a gas that is not respirable harms by displacing oxygen`() {
        assertTrue(
            Mechanism.OXYGEN_DISPLACEMENT in mechanisms(
                GasToxicity.assess(mapOf("CO2" to 0.5, "N2" to 0.4, "O2" to 0.1)),
            ),
            "an oxygen-starved mixture was not reported as asphyxiating",
        )
        assertEquals(
            listOf(ToxicityAssessment.NoModel),
            GasToxicity.assess(mapOf("N2" to 0.79, "O2" to 0.21)),
            "clean air was called hazardous",
        )
    }

    @Test
    fun `chlorine is reported as an oxidiser and, when wet, as an acid`() {
        val wet = mechanisms(GasToxicity.assess(mapOf("Cl2" to 0.05, "H2O" to 0.05, "N2" to 0.85, "O2" to 0.05)))
        assertTrue(Mechanism.OXIDISER in wet, "chlorine was not reported as an oxidiser")
        assertTrue(Mechanism.ACID_WITH_MOISTURE in wet, "wet chlorine was not reported as making acid")
    }

    @Test
    fun `a gas with no mechanism reports no model instead of an invented dose`() {
        val findings = GasToxicity.assess(mapOf("CH4" to 0.01, "N2" to 0.78, "O2" to 0.21))
        assertEquals(
            listOf(ToxicityAssessment.NoModel),
            findings,
            "a trace of methane in air was given a toxicity mechanism it does not have",
        )
        assertEquals(listOf(ToxicityAssessment.NoModel), GasToxicity.assess(emptyMap()))
    }
}
