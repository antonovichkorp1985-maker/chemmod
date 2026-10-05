package io.github.antonovichkorp.chemmod.core.material

/** Java-friendly single-input transition consumed by simple manual processing blocks. */
data class MaterialTransitionSpec(
    val processId: String,
    val machineTag: String,
    val materialId: String,
    val inputForm: String,
    val outputForm: String,
    val inputMassMicrograms: Long,
    val outputMassMicrograms: Long,
    val durationTicks: Int,
)

object MaterialProcessExports {
    private val manualMachineTags = setOf("chemmod:crushing", "chemmod:grinding")

    @JvmStatic
    fun bundledManualTransitions(): List<MaterialTransitionSpec> =
        MaterialCatalog.bundled().compiledProcesses.values
            .asSequence()
            .filter { it.machineTag in manualMachineTags }
            .filter { it.inputs.size == 1 && it.outputs.size == 1 }
            .map { process ->
                val input = process.inputs.single()
                val output = process.outputs.single()
                require(input.material.id == output.material.id) {
                    "Manual process ${process.id} cannot change material identity"
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
                )
            }
            .sortedBy(MaterialTransitionSpec::processId)
            .toList()
}
