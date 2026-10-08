package io.github.antonovichkorp.chemmod.core.material

/** Java-friendly single-input transition consumed by simple game processing blocks. */
data class MaterialTransitionSpec(
    val processId: String,
    val machineTag: String,
    val materialId: String,
    val inputForm: String,
    val outputForm: String,
    val inputMassMicrograms: Long,
    val inputCount: Int,
    val outputMassMicrograms: Long,
    val outputCount: Int,
    val durationTicks: Int,
    val minimumTemperatureKelvin: Double?,
    val maximumTemperatureKelvin: Double?,
)

/**
 * Java-friendly declaration for a composition-driven physical partition. Static
 * output masses describe the catalog's reference batch only; adapters calculate
 * their actual output masses from persisted component masses at runtime.
 */
data class MaterialPartitionSpec(
    val processId: String,
    val machineTag: String,
    val inputMaterialId: String,
    val inputForm: String,
    val primaryOutputForm: String,
    val impurityOutputForm: String,
    val referenceInputMassMicrograms: Long,
    val durationTicks: Int,
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

    @JvmStatic
    fun bundledWashingTransitions(): List<MaterialTransitionSpec> =
        bundledTransitions(setOf("chemmod:washing"))

    @JvmStatic
    fun bundledPartitionProcesses(): List<MaterialPartitionSpec> =
        MaterialCatalog.bundled().compiledProcesses.values
            .asSequence()
            .filter { it.machineTag == "chemmod:separating" }
            .map { process ->
                require(process.inputs.size == 1 && process.outputs.size >= 2) {
                    "Partition process ${process.id} must have one input and at least two outputs"
                }
                val input = process.inputs.single()
                val primary = requireNotNull(process.outputs.singleOrNull { it.material.id == input.material.id }) {
                    "Partition process ${process.id} must emit its primary material"
                }
                val impurity = requireNotNull(process.outputs.firstOrNull { it.material.id != input.material.id }) {
                    "Partition process ${process.id} must emit a physical impurity output"
                }
                MaterialPartitionSpec(
                    processId = process.id.value,
                    machineTag = process.machineTag,
                    inputMaterialId = input.material.id.value,
                    inputForm = input.form.name,
                    primaryOutputForm = primary.form.name,
                    impurityOutputForm = impurity.form.name,
                    referenceInputMassMicrograms = input.totalMassMicrograms,
                    durationTicks = process.durationTicks,
                )
            }
            .sortedBy(MaterialPartitionSpec::processId)
            .toList()

    @JvmStatic
    fun bundledFormingTransitions(): List<MaterialTransitionSpec> =
        bundledTransitions(setOf("chemmod:forming_hammer", "chemmod:drawing", "chemmod:cutting"))

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
                    inputCount = input.count,
                    outputMassMicrograms = output.massMicrograms,
                    outputCount = output.count,
                    durationTicks = process.durationTicks,
                    minimumTemperatureKelvin = process.conditions.minimumTemperatureKelvin,
                    maximumTemperatureKelvin = process.conditions.maximumTemperatureKelvin,
                )
            }
            .sortedBy(MaterialTransitionSpec::processId)
            .toList()
}
