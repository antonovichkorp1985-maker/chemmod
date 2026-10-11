package io.github.antonovichkorp.chemmod.core.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MaterialOntologyTest {
    private val copperMineral = MineralId.of("chemmod:native_copper")
    private val copperMaterial = MaterialId.of("chemmod:copper")

    @Test
    fun `chemical species derives stable identity from molecular structure`() {
        val ethanol = ChemicalSpecies.fromStructure(SpeciesId.of("chemmod:ethanol"), "CCO")
        val reverse = ChemicalSpecies.fromStructure(SpeciesId.of("chemmod:ethanol_reverse"), "OCC")

        assertEquals("C2H6O", ethanol.formula)
        assertEquals(ethanol.canonicalKey, reverse.canonicalKey)
        assertEquals(ChemicalSpecies.CURRENT_SCHEMA, ethanol.schemaVersion)
    }

    @Test
    fun `species mineral deposit and material remain separate domain concepts`() {
        val mineral = MineralDefinition(
            copperMineral,
            chemicalFormula = "Cu",
            elementStoichiometry = mapOf("Cu" to 1),
            hardnessMohs = 3.0,
        )
        val deposit = DepositDefinition(
            id = DepositId.of("chemmod:overworld_native_copper"),
            mineralWeights = mapOf(mineral.id to 100),
            dimensionTags = setOf("minecraft:is_overworld"),
            minY = -16,
            maxY = 96,
            attemptsPerChunk = 4,
        )
        val material = copperDefinition()

        val source = assertIs<MaterialSource.Mineral>(material.source)
        assertEquals(copperMineral, source.mineralId)
        assertTrue(deposit.mineralWeights.containsKey(copperMineral))
    }

    @Test
    fun `material batch accounts for every microgram`() {
        assertFailsWith<IllegalArgumentException> {
            MaterialBatch(
                materialId = copperMaterial,
                massMicrograms = 1_000_000,
                primaryMassMicrograms = 900_000,
                impurityMassMicrograms = mapOf(MaterialId.of("chemmod:iron") to 99_999),
            )
        }

        val exact = MaterialBatch(
            materialId = copperMaterial,
            massMicrograms = 1_000_000,
            primaryMassMicrograms = 900_000,
            impurityMassMicrograms = mapOf(MaterialId.of("chemmod:iron") to 100_000),
        )
        assertEquals(900_000, exact.purityPpm)
    }

    @Test
    fun `process compiler resolves canonical materials and forms`() {
        val process = ProcessDefinition(
            id = ProcessId.of("chemmod:cast_copper_ingot"),
            machineTag = "chemmod:casting",
            inputs = listOf(ProcessStack(copperMaterial, MaterialForm.LIQUID, 1_000_000)),
            outputs = listOf(ProcessStack(copperMaterial, MaterialForm.INGOT, 1_000_000)),
            durationTicks = 200,
            conditions = ProcessConditions(minimumTemperatureKelvin = 1_358.0),
        )

        val compiled = ProcessCompiler(listOf(copperDefinition())).compile(process)

        assertEquals(copperMaterial, compiled.inputs.single().material.id)
        assertEquals(MaterialForm.INGOT, compiled.outputs.single().form)
        assertEquals(1_358.0, compiled.conditions.minimumTemperatureKelvin)
    }

    @Test
    fun `process compiler rejects unsupported foreign form assumptions`() {
        val invalid = ProcessDefinition(
            id = ProcessId.of("chemmod:invalid_copper_gear"),
            machineTag = "chemmod:forming",
            inputs = listOf(ProcessStack(copperMaterial, MaterialForm.INGOT, 1_000_000)),
            outputs = listOf(ProcessStack(copperMaterial, MaterialForm.GEAR, 1_000_000)),
            durationTicks = 100,
        )

        val error = assertFailsWith<IllegalArgumentException> {
            ProcessCompiler(listOf(copperDefinition())).compile(invalid)
        }
        assertTrue(error.message!!.contains("does not support form GEAR"))
    }

    @Test
    fun `definition IDs reject unstable mixed case identifiers`() {
        assertFailsWith<IllegalArgumentException> { MaterialId.of("ChemMod:Copper") }
    }

    private fun copperDefinition() = MaterialDefinition(
        id = copperMaterial,
        source = MaterialSource.Mineral(copperMineral),
        supportedForms = setOf(MaterialForm.LIQUID, MaterialForm.INGOT, MaterialForm.NUGGET),
    )
}
