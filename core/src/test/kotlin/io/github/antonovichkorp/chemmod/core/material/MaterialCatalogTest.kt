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

        val deposit = catalog.deposits.getValue(DepositId.of("chemmod:overworld_native_copper"))
        assertEquals(-32, deposit.minY)
        assertEquals(80, deposit.maxY)
        assertEquals(1, deposit.attemptsPerChunk)
        assertEquals(setOf("minecraft:is_overworld"), deposit.dimensionTags)

        val copper = catalog.materials.getValue(MaterialId.of("chemmod:copper"))
        assertIs<MaterialSource.Mineral>(copper.source)
        assertTrue(MaterialForm.INGOT in copper.supportedForms)
        assertTrue(MaterialForm.LIQUID in copper.supportedForms)

        val melting = catalog.compiledProcesses.getValue(ProcessId.of("chemmod:melt_copper"))
        assertEquals(MaterialForm.DUST, melting.inputs.single().form)
        assertEquals(MaterialForm.LIQUID, melting.outputs.single().form)
        assertEquals(1_000_000_000L, melting.inputs.single().massMicrograms)
        assertEquals(1357.77, melting.conditions.minimumTemperatureKelvin)

        val itemForms = MaterialItemExports.bundledSolidForms()
        assertEquals(8, itemForms.size)
        assertTrue(itemForms.any { it.registryPath == "copper_ingot" })
        assertTrue(itemForms.none { it.formName == MaterialForm.LIQUID.name })

        val manualTransitions = MaterialProcessExports.bundledManualTransitions()
        assertEquals(2, manualTransitions.size)
        assertEquals("ORE", manualTransitions.first().inputForm)
        assertEquals("CRUSHED_ORE", manualTransitions.first().outputForm)
        assertEquals("DUST", manualTransitions.last().outputForm)
        assertTrue(manualTransitions.all { it.inputMassMicrograms == it.outputMassMicrograms })

        val moltenContainers = MaterialItemExports.bundledMoltenContainers()
        assertEquals("molten_copper_crucible", moltenContainers.single().registryPath)
        assertEquals("LIQUID", moltenContainers.single().formName)

        val meltingTransition = MaterialProcessExports.bundledMeltingTransitions().single()
        assertEquals("DUST", meltingTransition.inputForm)
        assertEquals("LIQUID", meltingTransition.outputForm)
        assertEquals(1357.77, meltingTransition.minimumTemperatureKelvin)
        val castingTransition = MaterialProcessExports.bundledCastingTransitions().single()
        assertEquals("LIQUID", castingTransition.inputForm)
        assertEquals("INGOT", castingTransition.outputForm)
        assertEquals(1357.77, castingTransition.maximumTemperatureKelvin)

        val formingTransitions = MaterialProcessExports.bundledFormingTransitions()
        assertEquals(3, formingTransitions.size)
        assertTrue(formingTransitions.any { it.inputForm == "INGOT" && it.outputForm == "PLATE" })
        assertTrue(formingTransitions.any { it.inputForm == "INGOT" && it.outputForm == "ROD" })
        assertTrue(formingTransitions.any { it.inputForm == "ROD" && it.outputForm == "WIRE" })
        assertTrue(formingTransitions.all { it.inputMassMicrograms == it.outputMassMicrograms })
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
