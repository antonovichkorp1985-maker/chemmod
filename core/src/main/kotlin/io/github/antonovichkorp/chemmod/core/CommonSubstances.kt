package io.github.antonovichkorp.chemmod.core

import java.util.Locale

data class CommonSubstance(
    val canonicalName: String,
    val structure: String,
)

/**
 * Human-friendly entry points for the small M0 substance set.
 * The chemistry engine still receives a structure; aliases are only an input layer.
 * Ambiguous molecular formulae (for example C2H6O) are intentionally not aliases.
 */
object CommonSubstances {
    private val substances = listOf(
        entry("water", "O", "water", "вода", "h2o"),
        entry("hydrogen", "[H][H]", "hydrogen", "водород", "h2"),
        entry("oxygen", "O=O", "oxygen", "кислород", "o2"),
        entry("carbon_dioxide", "O=C=O", "carbon_dioxide", "carbon-dioxide", "co2", "углекислый_газ"),
        entry("methane", "C", "methane", "метан", "ch4"),
        entry("methanol", "CO", "methanol", "метанол", "ch3oh"),
        entry("ethanol", "CCO", "ethanol", "этанол", "c2h5oh"),
        entry("dimethyl_ether", "COC", "dimethyl_ether", "dimethyl-ether", "диметиловый_эфир"),
        entry("propane", "CCC", "propane", "пропан", "c3h8"),
        entry("cyclopropane", "C1CC1", "cyclopropane", "циклопропан"),
        entry("acetic_acid", "CC(=O)O", "acetic_acid", "acetic-acid", "уксусная_кислота", "ch3cooh"),
        entry("chlorine", "Cl-Cl", "chlorine", "хлор", "cl2"),
    )

    private val byAlias: Map<String, CommonSubstance> = buildMap {
        substances.forEach { (substance, aliases) ->
            aliases.forEach { alias -> put(normalize(alias), substance) }
        }
    }

    fun find(input: String): CommonSubstance? = byAlias[normalize(input)]

    fun resolve(input: String): String = find(input)?.structure ?: input

    private fun entry(
        canonicalName: String,
        structure: String,
        vararg aliases: String,
    ): Pair<CommonSubstance, List<String>> =
        CommonSubstance(canonicalName, structure) to aliases.toList()

    private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT).replace(' ', '_')
}
