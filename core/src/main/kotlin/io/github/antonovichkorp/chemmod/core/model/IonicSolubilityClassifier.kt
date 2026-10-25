package io.github.antonovichkorp.chemmod.core.model

/**
 * Applies the existing ion-class solubility rules to a charge-balanced salt graph.
 *
 * A supported salt is represented as disconnected, explicitly charged ions (for example
 * `[Ca+2].[Cl-].[Cl-]`). The anion family is recognized from its graph composition and total
 * charge; the cation is a single atom with a declared common ion charge. This is not a Ksp or
 * general aqueous-solubility model: it returns null for unrecognized ions, mixed salts, and
 * unbalanced charge.
 */
object IonicSolubilityClassifier {
    data class Result(
        val soluble: Boolean,
        val anionGroup: Solubility.AnionGroup,
        /** Conventional cation-first formula ordering for this recognized ion pair. */
        val formulaElementOrder: List<String>,
    )

    fun isChargeSeparatedCompound(graph: MoleculeGraph): Boolean {
        val charges = connectedComponents(graph).map { component ->
            component.sumOf { graph.atom(it).formalCharge }
        }
        return charges.size >= 2 && charges.sum() == 0 && charges.all { it != 0 } &&
            charges.any { it > 0 } && charges.any { it < 0 }
    }

    fun classify(graph: MoleculeGraph): Result? {
        val components = connectedComponents(graph)
        if (components.size < 2) return null
        val charges = components.map { component -> component.sumOf { graph.atom(it).formalCharge } }
        if (charges.sum() != 0 || charges.any { it == 0 }) return null

        val cationComponents = components.indices.filter { charges[it] > 0 }.map(components::get)
        val anionComponents = components.indices.filter { charges[it] < 0 }.map(components::get)
        if (cationComponents.isEmpty() || anionComponents.isEmpty()) return null
        if (charges.filter { it > 0 }.sum() != -charges.filter { it < 0 }.sum()) return null

        val cations = cationComponents.map { component ->
            if (component.size != 1) return null
            val atom = graph.atom(component.single())
            when {
                atom.element.symbol == "N" && atom.formalCharge == 1 &&
                    atom.explicitHydrogens + graph.implicitHydrogens(atom.id) == 4 ->
                    CationComponent(Solubility.Cation.Ammonium, "ammonium", 1, listOf("N", "H"))
                atom.formalCharge > 0 && atom.formalCharge in atom.element.commonIonCharges ->
                    CationComponent(
                        Solubility.Cation.Elemental(atom.element),
                        "${atom.element.symbol}:${atom.formalCharge}",
                        atom.formalCharge,
                        listOf(atom.element.symbol),
                    )
                else -> return null
            }
        }
        if (cations.map(CationComponent::identity).distinct().size != 1) return null
        if (cations.sumOf(CationComponent::charge) != charges.filter { it > 0 }.sum()) return null

        val anionGroups = anionComponents.map { component -> classifyAnion(graph, component) ?: return null }
        if (anionGroups.distinct().size != 1) return null
        val anionGroup = anionGroups.first()
        val soluble = Solubility.isSoluble(cations.first().model, anionGroup) ?: return null
        val formulaOrder = buildList {
            cations.flatMap(CationComponent::formulaSymbols).distinct().forEach(::add)
            when (anionGroup) {
                Solubility.AnionGroup.NITRATE -> addAll(listOf("N", "O"))
                Solubility.AnionGroup.HALIDE -> addAll(anionComponents.flatten().map { graph.atom(it).element.symbol }.distinct())
                Solubility.AnionGroup.SULFATE -> addAll(listOf("S", "O"))
                Solubility.AnionGroup.CARBONATE -> addAll(listOf("C", "O"))
                Solubility.AnionGroup.PHOSPHATE -> addAll(listOf("P", "O"))
                Solubility.AnionGroup.SULFIDE -> add("S")
                Solubility.AnionGroup.HYDROXIDE -> addAll(listOf("O", "H"))
            }
        }.distinct()
        return Result(soluble, anionGroup, formulaOrder)
    }

    private data class CationComponent(
        val model: Solubility.Cation,
        val identity: String,
        val charge: Int,
        val formulaSymbols: List<String>,
    )

    private fun classifyAnion(graph: MoleculeGraph, atomIds: List<Int>): Solubility.AnionGroup? {
        val atoms = atomIds.map(graph::atom)
        val charge = atoms.sumOf { it.formalCharge }
        val counts = atoms.groupingBy { it.element.symbol }.eachCount().toMutableMap()
        val hydrogens = atoms.sumOf { it.explicitHydrogens + graph.implicitHydrogens(it.id) }
        if (hydrogens > 0) counts["H"] = hydrogens
        fun hasCentralOxoanion(centerSymbol: String, oxygenCount: Int): Boolean {
            val center = atoms.singleOrNull { it.element.symbol == centerSymbol } ?: return false
            val oxygens = atoms.filter { it.element.symbol == "O" }
            return oxygens.size == oxygenCount && oxygens.all { oxygen ->
                graph.neighbors(center.id).any { it.first.id == oxygen.id }
            }
        }
        return when {
            atoms.size == 1 && atoms.single().element.symbol in HALOGENS && charge == -1 ->
                Solubility.AnionGroup.HALIDE
            counts == mapOf("N" to 1, "O" to 3) && charge == -1 && hasCentralOxoanion("N", 3) ->
                Solubility.AnionGroup.NITRATE
            counts == mapOf("S" to 1, "O" to 4) && charge == -2 && hasCentralOxoanion("S", 4) ->
                Solubility.AnionGroup.SULFATE
            counts == mapOf("C" to 1, "O" to 3) && charge == -2 && hasCentralOxoanion("C", 3) ->
                Solubility.AnionGroup.CARBONATE
            counts == mapOf("P" to 1, "O" to 4) && charge == -3 && hasCentralOxoanion("P", 4) ->
                Solubility.AnionGroup.PHOSPHATE
            counts == mapOf("S" to 1) && charge == -2 -> Solubility.AnionGroup.SULFIDE
            counts == mapOf("O" to 1, "H" to 1) && charge == -1 -> Solubility.AnionGroup.HYDROXIDE
            else -> null
        }
    }

    private fun connectedComponents(graph: MoleculeGraph): List<List<Int>> {
        val unseen = graph.atoms.map { it.id }.toMutableSet()
        val components = mutableListOf<List<Int>>()
        while (unseen.isNotEmpty()) {
            val pending = ArrayDeque<Int>()
            val component = mutableListOf<Int>()
            pending.add(unseen.first())
            while (pending.isNotEmpty()) {
                val atomId = pending.removeFirst()
                if (!unseen.remove(atomId)) continue
                component += atomId
                graph.neighbors(atomId).forEach { pending.add(it.first.id) }
            }
            components += component
        }
        return components
    }

    private val HALOGENS = setOf("F", "Cl", "Br", "I")
}
