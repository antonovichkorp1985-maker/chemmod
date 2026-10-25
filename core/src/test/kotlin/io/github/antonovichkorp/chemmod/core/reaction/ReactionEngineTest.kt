package io.github.antonovichkorp.chemmod.core.reaction

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.naming.TrivialNameDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReactionEngineTest {
    private val engine = ReactionEngine.bundled()
    private val copper110Sites = mapOf("chemmod:cu110_step_site" to 5.631149772978659e-10)

    @Test
    fun `bundled content supplies data-defined reaction rules for every covered element class`() {
        val rules = ReactionRuleSet.bundled().rules
        assertTrue(rules.size >= 8, "expected the bundled rule set to keep growing, got ${rules.size}")
        assertTrue(rules.values.any { it.matcher is FormulaPattern }, "formula-level rule types disappeared")
        assertTrue(rules.values.any { it.matcher is BondOrderPattern }, "bond-level rule types disappeared")
        assertTrue(rules.values.any { it.matcher is AtomTransferPattern }, "atom-transfer rule type disappeared")

        // Combustion is one rule per heteroatom class: the matcher is keyed by elements,
        // never by a list of combustible substances.
        val covered = rules.values
            .filter { it.coReactantStructures == listOf("O=O") }
            .mapNotNull { rule -> (rule.matcher as? FormulaPattern)?.requiredElements?.sorted()?.joinToString("+") }
            .toSet()
        assertEquals(setOf("C", "C+Cl", "C+N", "C+S"), covered)
    }

    @Test
    fun `bundled machine rules declare localized names and Arrhenius kinetics`() {
        val rules = ReactionRuleSet.bundled().rules.values
        assertTrue(rules.all { it.displayNameKey.startsWith("reaction.chemmod.") })
        assertTrue(rules.all { it.displayDescriptionKey.startsWith("reaction.chemmod.") })
        assertTrue(rules.all { it.kinetics != null })

        val ethanolRule = ReactionRuleSet.bundled().rule(ReactionRuleId.of("chemmod:alcohol_dehydrogenation"))
        val kinetics = assertNotNull(ethanolRule.kinetics)
        assertTrue(kinetics.ratePerSecond(750.0, copper110Sites) > kinetics.ratePerSecond(600.0, copper110Sites))
        assertTrue(kinetics.ratePerSecond(600.0, copper110Sites) > 0.0)
        assertEquals(0.0, kinetics.ratePerSecond(600.0), 1e-12)
    }

    @Test
    fun `bundled ethanol dehydrogenation requires copper capability and characterized Cu110 sites`() {
        val ethanol = Molecule.fromSMILESlike("CCO")
        val rule = ReactionRuleId.of("chemmod:alcohol_dehydrogenation")
        val copperCapability = setOf("chemmod:dehydrogenation_metal")

        assertTrue(engine.apply(ethanol, rule, environment(600.0, capabilities = copperCapability)).isEmpty())
        assertTrue(engine.apply(ethanol, rule, environment(600.0, catalystSurfaceSiteMoles = copper110Sites)).isEmpty())
        assertEquals(
            1,
            engine.apply(
                ethanol,
                rule,
                environment(600.0, capabilities = copperCapability, catalystSurfaceSiteMoles = copper110Sites),
            ).size,
        )
    }

    @Test
    fun `surface site population scales declared Arrhenius kinetics`() {
        val siteType = "chemmod:fe3_defect_site"
        val kinetics = ReactionKinetics(
            preExponentialFactorPerSecond = 2.0,
            activationEnergyJoulesPerMole = 0.0,
            surfaceSiteType = siteType,
            referenceSurfaceSiteMoles = 2.0e-6,
        )

        assertEquals(0.0, kinetics.ratePerSecond(500.0, emptyMap()), 1e-12)
        assertEquals(1.0, kinetics.ratePerSecond(500.0, mapOf(siteType to 1.0e-6)), 1e-12)
        assertEquals(2.0, kinetics.ratePerSecond(500.0, mapOf(siteType to 2.0e-6)), 1e-12)
    }

    @Test
    fun `data rule combusts an unlisted molecular graph with automatic balancing`() {
        val butan2ol = Molecule.fromSMILESlike("CC(O)CC")
        assertTrue(butan2ol.validate().isEmpty())
        assertTrue(butan2ol.canonicalKey().isNotBlank())
        assertNotNull(butan2ol.properties().boilingPointC)
        assertNotNull(butan2ol.properties().combustionEnthalpyKilojoulesPerMole)
        kotlin.test.assertNull(TrivialNameDirectory.bundled().findByCanonicalKey(butan2ol.canonicalKey()))

        val oxygen = Molecule.fromSMILESlike("O=O")
        val outcome = applySingle(
            butan2ol,
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
            environment(
                temperatureKelvin = 600.0,
                capabilities = setOf("chemmod:dehydrogenation_metal"),
                catalystSurfaceSiteMoles = copper110Sites,
            ),
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
    fun `aldehyde oxidation inserts one oxygen atom and balances two acetic acid pathways`() {
        val oxygen = Molecule.fromSMILESlike("O=O")
        val water = Molecule.fromSMILESlike("O")
        val oxidationCatalyst = setOf("chemmod:oxidation_metal")
        val acetaldehyde = Molecule.fromSMILESlike("CC=O")
        val aceticAcid = Molecule.fromSMILESlike("CC(=O)O")

        val direct = applySingle(
            acetaldehyde,
            "chemmod:aldehyde_oxidation",
            environment(500.0, coReactants = setOf(oxygen), capabilities = oxidationCatalyst),
        )
        assertEquals("2 C2H4O + O2 -> 2 C2H4O2", direct.formatEquation())
        assertEquals(aceticAcid.canonicalKey(), direct.products.first().molecule.canonicalKey())
        assertTrue(direct.isConserved())

        val hydrate = applySingle(
            acetaldehyde,
            "chemmod:carbonyl_hydration",
            environment(300.0, coReactants = setOf(water)),
        )
        assertEquals("C2H4O + H2O -> C2H6O2", hydrate.formatEquation())
        val hydratedAldehyde = hydrate.products.first().molecule
        val dehydrogenationRoute = applySingle(
            hydratedAldehyde,
            "chemmod:alcohol_dehydrogenation",
            environment(
                600.0,
                capabilities = setOf("chemmod:dehydrogenation_metal"),
                catalystSurfaceSiteMoles = copper110Sites,
            ),
        )
        assertEquals("C2H6O2 -> C2H4O2 + H2", dehydrogenationRoute.formatEquation())
        assertTrue(
            dehydrogenationRoute.products.any { it.molecule.canonicalKey() == aceticAcid.canonicalKey() },
            "the hydrate/dehydrogenation route must reach the same structural acetic-acid product",
        )
        assertTrue(dehydrogenationRoute.isConserved())
    }

    @Test
    fun `aldehyde oxygen transfer does not misclassify ketones and needs catalyst`() {
        val oxygen = Molecule.fromSMILESlike("O=O")
        val ketone = Molecule.fromSMILESlike("CC(=O)C")
        val rule = ReactionRuleId.of("chemmod:aldehyde_oxidation")

        assertTrue(
            engine.apply(
                ketone,
                rule,
                environment(500.0, coReactants = setOf(oxygen), capabilities = setOf("chemmod:oxidation_metal")),
            ).isEmpty(),
        )
        assertTrue(
            engine.apply(ketone, rule, environment(500.0, coReactants = setOf(oxygen))).isEmpty(),
        )
        assertTrue(
            engine.apply(
                Molecule.fromSMILESlike("CC=O"),
                rule,
                environment(500.0, coReactants = setOf(oxygen)),
            ).isEmpty(),
            "an aldehyde must not oxidize without the declared catalyst capability",
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
    fun `surface-gated reactions require characterized accessible active sites`() {
        val json = """
            {
              "schemaVersion": 2,
              "rules": [{
                "id": "chemmod:surface_test_combustion",
                "matcher": {
                  "kind": "formula",
                  "allowedElements": ["C", "H", "O"],
                  "requiredElements": ["C"],
                  "requireNeutral": true,
                  "requirePositiveOxygenDemand": true
                },
                "coReactantStructures": ["O=O"],
                "productStructures": ["O=C=O", "O"],
                "conditions": {
                  "minimumCatalystSurfaceSiteMoles": {"chemmod:fe3_defect_site": 1.0e-8}
                },
                "displayNameKey": "reaction.chemmod.surface_test",
                "displayDescriptionKey": "reaction.chemmod.surface_test.description",
                "kinetics": {
                  "preExponentialFactorPerSecond": 1.0,
                  "activationEnergyJoulesPerMole": 0.0,
                  "surfaceSiteType": "chemmod:fe3_defect_site",
                  "referenceSurfaceSiteMoles": 1.0e-8
                }
              }]
            }
        """.trimIndent()
        val ruleSet = ReactionRuleSet.fromJson(json.byteInputStream())
        val engine = ReactionEngine(ruleSet)
        val ethanol = Molecule.fromSMILESlike("CCO")
        val oxygen = Molecule.fromSMILESlike("O=O")
        val id = ReactionRuleId.of("chemmod:surface_test_combustion")
        val baseEnvironment = environment(600.0, coReactants = setOf(oxygen))

        assertTrue(engine.apply(ethanol, id, baseEnvironment).isEmpty())
        assertTrue(
            engine.apply(
                ethanol,
                id,
                baseEnvironment.copy(catalystSurfaceSiteMoles = mapOf("chemmod:fe3_defect_site" to 0.5e-8)),
            ).isEmpty(),
        )
        val reaction = engine.apply(
            ethanol,
            id,
            baseEnvironment.copy(catalystSurfaceSiteMoles = mapOf("chemmod:fe3_defect_site" to 1.0e-8)),
        ).single()
        assertEquals("C2H6O + 3 O2 -> 2 CO2 + 3 H2O", reaction.formatEquation())
        assertTrue(reaction.isConserved())
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
        capabilities: Set<String> = emptySet(),
        catalystSurfaceSiteMoles: Map<String, Double> = emptyMap(),
    ) = ReactionEnvironment(
        temperatureKelvin = temperatureKelvin,
        pressureKilopascals = 101.325,
        catalystTags = catalysts,
        availableCanonicalKeys = coReactants.map { molecule -> molecule.canonicalKey() }.toSet(),
        catalystCapabilities = capabilities,
        catalystSurfaceSiteMoles = catalystSurfaceSiteMoles,
    )
}
