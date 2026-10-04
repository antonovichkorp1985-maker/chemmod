package io.github.antonovichkorp.chemmod.core.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MaterialCatalogTest {
    @Test
    fun `bundled catalog loads and compiles canonical copper processes`() {
        val catalog = MaterialCatalog.bundled()

        assertEquals(MaterialCatalog.CURRENT_SCHEMA, catalog.schemaVersion)
        assertEquals("H2O", catalog.species.getValue(SpeciesId.of("chemmod:water")).formula)

        val nativeCopper = catalog.minerals.getValue(MineralId.of("chemmod:native_copper"))
        assertEquals("Cu", nativeCopper.chemicalFormula)
        assertEquals(mapOf("Cu" to 1), nativeCopper.elementStoichiometry)

        val copper = catalog.materials.getValue(MaterialId.of("chemmod:copper"))
        assertIs<MaterialSource.Mineral>(copper.source)
        assertTrue(MaterialForm.INGOT in copper.supportedForms)
        assertTrue(MaterialForm.LIQUID in copper.supportedForms)

        val melting = catalog.compiledProcesses.getValue(ProcessId.of("chemmod:melt_copper"))
        assertEquals(MaterialForm.DUST, melting.inputs.single().form)
        assertEquals(MaterialForm.LIQUID, melting.outputs.single().form)
        assertEquals(1357.77, melting.conditions.minimumTemperatureKelvin)
    }

    @Test
    fun `catalog rejects a deposit that references an unknown mineral`() {
        val error = assertFailsWith<IllegalArgumentException> {
            MaterialCatalog.fromJson(
                catalogJson(
                    deposits = """
                        [{
                          "id":"chemmod:broken_deposit",
                          "mineralWeights":{"chemmod:missing":1},
                          "dimensionTags":["minecraft:is_overworld"],
                          "minY":0,
                          "maxY":10,
                          "attemptsPerChunk":1
                        }]
                    """.trimIndent(),
                ).byteInputStream(),
            )
        }

        assertTrue(error.message!!.contains("unknown mineral"))
    }

    @Test
    fun `catalog rejects a process form the material does not own`() {
        val error = assertFailsWith<IllegalArgumentException> {
            MaterialCatalog.fromJson(
                catalogJson(
                    materials = """
                        [{
                          "id":"chemmod:test_material",
                          "source":{"type":"species","id":"chemmod:water"},
                          "supportedForms":["liquid"]
                        }]
                    """.trimIndent(),
                    processes = """
                        [{
                          "id":"chemmod:invalid_process",
                          "machineTag":"chemmod:test",
                          "inputs":[{"material":"chemmod:test_material","form":"liquid","massMicrograms":1}],
                          "outputs":[{"material":"chemmod:test_material","form":"ingot","massMicrograms":1}],
                          "durationTicks":1
                        }]
                    """.trimIndent(),
                ).byteInputStream(),
            )
        }

        assertTrue(error.message!!.contains("does not support form INGOT"))
    }

    private fun catalogJson(
        deposits: String = "[]",
        materials: String = "[]",
        processes: String = "[]",
    ): String = """
        {
          "schemaVersion":1,
          "species":[{"id":"chemmod:water","structure":"O"}],
          "minerals":[],
          "deposits":$deposits,
          "materials":$materials,
          "processes":$processes
        }
    """.trimIndent()
}
