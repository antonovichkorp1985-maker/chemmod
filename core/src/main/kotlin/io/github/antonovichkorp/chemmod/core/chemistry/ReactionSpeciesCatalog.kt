package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.CommonSubstances
import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEngine
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRule
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleSet

/**
 * Finite molecular participants of the bundled reaction network. The name directory is only a
 * pool of possible target graphs and localized aliases: a named entry reaches the displayed set
 * only if an authored rule actually applies to it. Rule-defined co-reactants/products and the
 * balanced products of successful rule applications are included automatically.
 *
 * This is not an enumeration of all graphs that a general matcher could theoretically accept.
 */
object ReactionSpeciesCatalog {
    private val ruleSet by lazy { ReactionRuleSet.bundled() }
    private val engine by lazy { ReactionEngine(ruleSet) }
    private val speciesByCanonicalKey: Map<String, Molecule> by lazy(::deriveSpecies)

    /** Stable, canonical-key-unique molecular participants, including generated products. */
    fun all(): List<Molecule> = speciesByCanonicalKey.values.toList()

    fun contains(canonicalKey: String): Boolean = canonicalKey in speciesByCanonicalKey

    private fun deriveSpecies(): Map<String, Molecule> {
        val species = linkedMapOf<String, Molecule>()
        fun include(molecule: Molecule) {
            if (molecule.validate().isEmpty()) species.putIfAbsent(molecule.canonicalKey(), molecule)
        }

        val targetGraphs = CommonSubstances.all()
            .map { Molecule.fromSMILESlike(it.structure) }
            .distinctBy(Molecule::canonicalKey)
        for (rule in ruleSet.rules.values) {
            val coReactants = rule.coReactantStructures.map { Molecule.fromSMILESlike(it) }
            rule.productStructures.map { Molecule.fromSMILESlike(it) }.forEach(::include)
            coReactants.forEach(::include)

            val environment = enumerationEnvironment(rule, coReactants)
            for (target in targetGraphs) {
                val outcomes = engine.apply(target, rule.id, environment)
                if (outcomes.isEmpty()) continue
                include(target)
                outcomes.forEach { outcome ->
                    outcome.reactants.forEach { include(it.molecule) }
                    outcome.products.forEach { include(it.molecule) }
                }
            }
        }
        return species.toMap()
    }

    /** Supply exactly the rule-declared conditions for cataloguing possible authored outcomes. */
    private fun enumerationEnvironment(rule: ReactionRule, coReactants: List<Molecule>) =
        ReactionEnvironment(
            temperatureKelvin = rule.conditions.minimumTemperatureKelvin
                ?: rule.conditions.maximumTemperatureKelvin
                ?: 298.15,
            pressureKilopascals = rule.conditions.minimumPressureKilopascals ?: 101.325,
            catalystTags = rule.conditions.catalystTags,
            availableCanonicalKeys = coReactants.map(Molecule::canonicalKey).toSet(),
            catalystCapabilities = rule.conditions.catalystCapabilities,
            catalystSurfaceSiteMoles = rule.conditions.minimumCatalystSurfaceSiteMoles,
        )
}
