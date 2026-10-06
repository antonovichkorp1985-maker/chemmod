package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.properties.ReferencePhase
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChemicalCatalogTest {
    @Test
    fun `bundled chemistry catalog owns structural substances reactions and estimates`() {
        val catalog = ChemicalCatalog.bundled()

        assertEquals(ChemicalCatalog.CURRENT_SCHEMA, catalog.schemaVersion)
        assertEquals(13, catalog.substances.size)
        assertEquals(5, catalog.reactions.size)

        val ethanol = assertNotNull(catalog.findSubstance("этанол"))
        assertEquals(SubstanceId.of("chemmod:ethanol"), ethanol.id)
        assertEquals("C2H6O", ethanol.properties.formula)
        assertEquals(ReferencePhase.CONDENSED, ethanol.properties.referencePhaseAt20C)
        assertTrue(ethanol.properties.combustionEnthalpyEstimateKilojoulesPerMole!! < 0.0)
        assertTrue("COMBUSTIBLE_ESTIMATE" in ethanol.properties.flags)

        val methane = assertNotNull(catalog.findSubstance("CH4"))
        assertEquals(ReferencePhase.GAS, methane.properties.referencePhaseAt20C)
        assertEquals(
            "C2H6O + 3 O2 -> 2 CO2 + 3 H2O",
            catalog.formatEquation(catalog.reactions.getValue(ChemicalReactionId.of("chemmod:ethanol_combustion"))),
        )
    }

    @Test
    fun `catalog rejects a reaction with non minimal or non conserved coefficients`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ChemicalCatalog.fromJson(
                ByteArrayInputStream(
                    """
                    {
                      "schemaVersion":1,
                      "substances":[
                        {"id":"chemmod:hydrogen","structure":"[H][H]"},
                        {"id":"chemmod:oxygen","structure":"O=O"},
                        {"id":"chemmod:water","structure":"O"}
                      ],
                      "reactions":[{
                        "id":"chemmod:bad_water",
                        "reactants":[
                          {"substance":"chemmod:hydrogen","coefficient":1},
                          {"substance":"chemmod:oxygen","coefficient":1}
                        ],
                        "products":[{"substance":"chemmod:water","coefficient":1}]
                      }]
                    }
                    """.trimIndent().toByteArray(),
                ),
            )
        }

        assertTrue(error.message!!.contains("minimal conserved coefficients"))
    }

    @Test
    fun `catalog rejects aliases shared by different molecular identities`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ChemicalCatalog.fromJson(
                ByteArrayInputStream(
                    """
                    {
                      "schemaVersion":1,
                      "substances":[
                        {"id":"chemmod:methane","aliases":["fuel"],"structure":"C"},
                        {"id":"chemmod:ethanol","aliases":["fuel"],"structure":"CCO"}
                      ],
                      "reactions":[]
                    }
                    """.trimIndent().toByteArray(),
                ),
            )
        }

        assertTrue(error.message!!.contains("Alias 'fuel'"))
    }

    @Test
    fun `structure analyzer does not invent combustion data for unsupported atoms`() {
        val chlorine = ChemicalCatalog.bundled().substance(SubstanceId.of("chemmod:chlorine"))

        assertEquals(null, chlorine.properties.combustionEnthalpyEstimateKilojoulesPerMole)
        assertEquals(ReferencePhase.UNKNOWN, chlorine.properties.referencePhaseAt20C)
    }
}
