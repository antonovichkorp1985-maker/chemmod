package io.github.antonovichkorp.chemmod.core.material

/** Java-friendly projection used by the NeoForge registry without leaking Minecraft into core. */
data class MaterialItemSpec(
    val materialId: String,
    val formName: String,
    val registryPath: String,
)

object MaterialItemExports {
    private val itemForms = setOf(
        MaterialForm.ORE,
        MaterialForm.CRUSHED_ORE,
        MaterialForm.DUST,
        MaterialForm.INGOT,
        MaterialForm.NUGGET,
        MaterialForm.PLATE,
        MaterialForm.ROD,
        MaterialForm.WIRE,
        MaterialForm.GEAR,
    )

    /**
     * Forms represented by ordinary items. Placeable BLOCK forms are registered by
     * the Minecraft adapter because they need a block entity to preserve batch state.
     */
    @JvmStatic
    fun bundledSolidForms(): List<MaterialItemSpec> = forms(itemForms) { materialPath, form ->
        "${materialPath}_${form.name.lowercase()}"
    }

    @JvmStatic
    fun bundledMoltenContainers(): List<MaterialItemSpec> = forms(setOf(MaterialForm.LIQUID)) { materialPath, _ ->
        "molten_${materialPath}_crucible"
    }

    private fun forms(
        accepted: Set<MaterialForm>,
        registryPath: (String, MaterialForm) -> String,
    ): List<MaterialItemSpec> = MaterialCatalog.bundled().materials.values
        .flatMap { material ->
            material.supportedForms
                .filter { it in accepted }
                .map { form ->
                    val materialPath = material.id.value.substringAfter(':')
                    MaterialItemSpec(material.id.value, form.name, registryPath(materialPath, form))
                }
        }
        .sortedBy(MaterialItemSpec::registryPath)
}
