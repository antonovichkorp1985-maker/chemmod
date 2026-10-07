package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.naming.TrivialNameDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReactionEngineTest {
    private val engine = ReactionEngine.bundled()

    @Test
    fun `bundled content supplies five data-defined M1 reaction rules`() {
        assertEquals(5, ReactionRuleSet.bundled().rules.size)
    }

    @Test
    fun `bundled machine rules declare localized names and Arrhenius kinetics`() {
        val rules = ReactionRuleSet.bundled().rules.values
        assertTrue(rules.all { it.displayNameKey.startsWith("reaction.chemmod.") })
        assertTrue(rules.all { it.kinetics != null })

        val ethanolRule = ReactionRuleSet.bundled().rule(ReactionRuleId.of("chemmod:alcohol_dehydrogenation"))
        val kinetics = assertNotNull(ethanolRule.kinetics)
        assertTrue(kinetics.ratePerSecond(750.0) > kinetics.ratePerSecond(600.0))
        assertTrue(kinetics.ratePerSecond(600.0) > 0.0)
    }

    @Test
    fun `data rule combusts an unlisted molecular graph with automatic balancing`() {
        val butanol = Molecule.fromSMILESlike("CCCCO")
        assertTrue(butanol.validate().isEmpty())
        assertTrue(butanol.canonicalKey().isNotBlank())
        assertNotNull(butanol.properties().boilingPointC)
        assertNotNull(butanol.properties().combustionEnthalpyKilojoulesPerMole)
        kotlin.test.assertNull(TrivialNameDirectory.bundled().findByCanonicalKey(butanol.canonicalKey()))

        val oxygen = Molecule.fromSMILESlike("O=O")
        val outcome = applySingle(
            butanol,
            "chemmod:complete_combustion",
            environment(temperatureKelvin = 600.0, coReactants = setOf(oxygen)),
        )

        assertEquals("C4H10O + 6 O2 -> 4 CO2 + 5 H2O", outcome.formatEquation())
        assertTrue(outcome.isConserved())
    }

    @Test
    fun `hydrogenation changes only the matching carbon carbon bond`() {
        val hydrogen = Molecule.fromSMILESlike("[H][H]")
        val outcome = applySingle(
            Molecule.fromSMILESlike("C=CC"),
            "chemmod:alkene_hydrogenation",
            environment(
                temperatureKelvin = 350.0,
                catalysts = setOf("chemmod:palladium"),
                coReactants = setOf(hydrogen),
            ),
        )

        assertEquals("C3H6 + H2 -> C3H8", outcome.formatEquation())
        assertTrue(outcome.isConserved())
    }

    @Test
    fun `dehydrogenation creates a distinct unsaturated graph and hydrogen`() {
        val outcome = applySingle(
            Molecule.fromSMILESlike("CCC"),
            "chemmod:alkane_dehydrogenation",
            environment(temperatureKelvin = 650.0, catalysts = setOf("chemmod:chromium_oxide")),
        )

        assertEquals("C3H8 -> C3H6 + H2", outcome.formatEquation())
        assertTrue(outcome.isConserved())
    }

    @Test
    fun `carbonyl hydrogenation and alcohol dehydrogenation are structural inverses`() {
        val hydrogen = Molecule.fromSMILESlike("[H][H]")
        val reduction = applySingle(
            Molecule.fromSMILESlike("CC=O"),
            "chemmod:carbonyl_hydrogenation",
            environment(
                temperatureKelvin = 350.0,
                catalysts = setOf("chemmod:nickel"),
                coReactants = setOf(hydrogen),
            ),
        )
        val oxidation = applySingle(
            Molecule.fromSMILESlike("CCO"),
            "chemmod:alcohol_dehydrogenation",
            environment(temperatureKelvin = 600.0, catalysts = setOf("chemmod:copper")),
        )

        assertEquals("C2H4O + H2 -> C2H6O", reduction.formatEquation())
        assertEquals("C2H6O -> C2H4O + H2", oxidation.formatEquation())
        assertEquals(
            reduction.products.first().molecule.canonicalKey(),
            Molecule.fromSMILESlike("CCO").canonicalKey(),
        )
        assertEquals(
            oxidation.products.first().molecule.canonicalKey(),
            Molecule.fromSMILESlike("CC=O").canonicalKey(),
        )
    }

    @Test
    fun `missing co reactant catalyst or temperature makes a rule inapplicable`() {
        val ethanol = Molecule.fromSMILESlike("CCO")
        val oxygen = Molecule.fromSMILESlike("O=O")
        val alkene = Molecule.fromSMILESlike("C=C")
        val hydrogen = Molecule.fromSMILESlike("[H][H]")

        assertTrue(engine.apply(ethanol, ReactionRuleId.of("chemmod:complete_combustion"), environment(600.0)).isEmpty())
        assertTrue(
            engine.apply(
                ethanol,
                ReactionRuleId.of("chemmod:complete_combustion"),
                environment(298.15, coReactants = setOf(oxygen)),
            ).isEmpty(),
        )
        assertTrue(
            engine.apply(
                alkene,
                ReactionRuleId.of("chemmod:alkene_hydrogenation"),
                environment(350.0, coReactants = setOf(hydrogen)),
            ).isEmpty(),
        )
    }

    @Test
    fun `formula matcher rejects fully oxidized and unsupported molecules`() {
        val oxygen = Molecule.fromSMILESlike("O=O")
        val environment = environment(600.0, coReactants = setOf(oxygen))
        val rule = ReactionRuleId.of("chemmod:complete_combustion")

        assertTrue(engine.apply(Molecule.fromSMILESlike("O=C=O"), rule, environment).isEmpty())
        assertTrue(engine.apply(Molecule.fromSMILESlike("Cl-Cl"), rule, environment).isEmpty())
    }

    private fun applySingle(target: Molecule, rule: String, environment: ReactionEnvironment): AppliedReaction {
        val outcomes = engine.apply(target, ReactionRuleId.of(rule), environment)
        assertEquals(1, outcomes.size, "Expected exactly one structural product for $rule")
        return outcomes.single()
    }

    private fun environment(
        temperatureKelvin: Double,
        catalysts: Set<String> = emptySet(),
        coReactants: Set<Molecule> = emptySet(),
    ) = ReactionEnvironment(
        temperatureKelvin = temperatureKelvin,
        pressureKilopascals = 101.325,
        catalystTags = catalysts,
        availableCanonicalKeys = coReactants.map { molecule -> molecule.canonicalKey() }.toSet(),
    )
}
