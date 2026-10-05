package io.github.antonovichkorp.chemmod.core.material

/** Java-friendly single-input transition consumed by simple game processing blocks. */
data class MaterialTransitionSpec(
    val processId: String,
    val machineTag: String,
    val materialId: String,
    val inputForm: String,
    val outputForm: String,
    val inputMassMicrograms: Long,
    val outputMassMicrograms: Long,
    val durationTicks: Int,
    val minimumTemperatureKelvin: Double?,
    val maximumTemperatureKelvin: Double?,
)

object MaterialProcessExports {
    @JvmStatic
    fun bundledManualTransitions(): List<MaterialTransitionSpec> =
        bundledTransitions(setOf("chemmod:crushing", "chemmod:grinding"))

    @JvmStatic
    fun bundledMeltingTransitions(): List<MaterialTransitionSpec> =
        bundledTransitions(setOf("chemmod:melting"))

    @JvmStatic
    fun bundledCastingTransitions(): List<MaterialTransitionSpec> =
        bundledTransitions(setOf("chemmod:casting"))

    private fun bundledTransitions(machineTags: Set<String>): List<MaterialTransitionSpec> =
        MaterialCatalog.bundled().compiledProcesses.values
            .asSequence()
            .filter { it.machineTag in machineTags }
            .filter { it.inputs.size == 1 && it.outputs.size == 1 }
            .map { process ->
                val input = process.inputs.single()
                val output = process.outputs.single()
                require(input.material.id == output.material.id) {
                    "Single-batch process ${process.id} cannot change material identity"
                }
                MaterialTransitionSpec(
                    processId = process.id.value,
                    machineTag = process.machineTag,
                    materialId = input.material.id.value,
                    inputForm = input.form.name,
                    outputForm = output.form.name,
                    inputMassMicrograms = input.massMicrograms,
                    outputMassMicrograms = output.massMicrograms,
                    durationTicks = process.durationTicks,
                    minimumTemperatureKelvin = process.conditions.minimumTemperatureKelvin,
                    maximumTemperatureKelvin = process.conditions.maximumTemperatureKelvin,
                )
            }
            .sortedBy(MaterialTransitionSpec::processId)
            .toList()
}
