package io.github.antonovichkorp.chemmod.core.material

data class ProcessStack(
    val materialId: MaterialId,
    val form: MaterialForm,
    val massMicrograms: Long,
    val count: Int = 1,
) {
    init {
        require(massMicrograms > 0) { "Process stack mass must be positive" }
        require(count > 0) { "Process stack count must be positive" }
    }
}

data class ProcessConditions(
    val minimumTemperatureKelvin: Double? = null,
    val maximumTemperatureKelvin: Double? = null,
    val minimumPressureKilopascals: Double? = null,
) {
    init {
        require(minimumTemperatureKelvin == null || minimumTemperatureKelvin >= 0.0) {
            "Minimum temperature cannot be below absolute zero"
        }
        require(maximumTemperatureKelvin == null || maximumTemperatureKelvin >= 0.0) {
            "Maximum temperature cannot be below absolute zero"
        }
        require(
            minimumTemperatureKelvin == null ||
                maximumTemperatureKelvin == null ||
                minimumTemperatureKelvin <= maximumTemperatureKelvin,
        ) { "Minimum temperature must not exceed maximum temperature" }
        require(minimumPressureKilopascals == null || minimumPressureKilopascals > 0.0) {
            "Minimum pressure must be positive"
        }
    }
}

/** Declarative canonical process; adapters translate compiled runtime processes to mod APIs. */
data class ProcessDefinition(
    val id: ProcessId,
    val machineTag: String,
    val inputs: List<ProcessStack>,
    val outputs: List<ProcessStack>,
    val durationTicks: Int,
    val conditions: ProcessConditions = ProcessConditions(),
    val schemaVersion: Int = CURRENT_SCHEMA,
) {
    init {
        require(machineTag.isNotBlank()) { "Process $id needs a machine tag" }
        require(inputs.isNotEmpty()) { "Process $id needs at least one input" }
        require(outputs.isNotEmpty()) { "Process $id needs at least one output" }
        require(durationTicks > 0) { "Process duration must be positive" }
        require(schemaVersion > 0) { "Schema version must be positive" }
    }

    companion object { const val CURRENT_SCHEMA = 1 }
}

data class RuntimeProcessStack(
    val material: MaterialDefinition,
    val form: MaterialForm,
    val massMicrograms: Long,
    val count: Int,
) {
    val totalMassMicrograms: Long = Math.multiplyExact(massMicrograms, count.toLong())
}

data class CompiledProcess(
    val id: ProcessId,
    val machineTag: String,
    val inputs: List<RuntimeProcessStack>,
    val outputs: List<RuntimeProcessStack>,
    val durationTicks: Int,
    val conditions: ProcessConditions,
)

/** Resolves stable IDs and rejects recipes that request forms ChemMod does not own. */
class ProcessCompiler(materials: Collection<MaterialDefinition>) {
    private val materialsById = materials.associateBy { it.id }.also { byId ->
        require(byId.size == materials.size) { "Duplicate material IDs" }
    }

    fun compile(definition: ProcessDefinition): CompiledProcess {
        val inputs = definition.inputs.map(::compileStack)
        val outputs = definition.outputs.map(::compileStack)
        val inputMass = inputs.sumOf(RuntimeProcessStack::totalMassMicrograms)
        val outputMass = outputs.sumOf(RuntimeProcessStack::totalMassMicrograms)
        require(inputMass == outputMass) {
            "Process ${definition.id} does not conserve mass: $inputMass != $outputMass micrograms"
        }
        return CompiledProcess(
            id = definition.id,
            machineTag = definition.machineTag,
            inputs = inputs,
            outputs = outputs,
            durationTicks = definition.durationTicks,
            conditions = definition.conditions,
        )
    }

    private fun compileStack(stack: ProcessStack): RuntimeProcessStack {
        val material = requireNotNull(materialsById[stack.materialId]) {
            "Unknown material ${stack.materialId}"
        }
        require(stack.form in material.supportedForms) {
            "Material ${stack.materialId} does not support form ${stack.form}"
        }
        return RuntimeProcessStack(material, stack.form, stack.massMicrograms, stack.count)
    }
}
