package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.bond.AromaticityModel
import io.github.antonovichkorp.chemmod.core.bond.CoordinateBondModel
import io.github.antonovichkorp.chemmod.core.bond.HydrogenBondModel
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/** A derived view; it is never stored in, or allowed to define, a substance. */
data class PredictedProperties(
    val boilingPointC: Double?,
    val molarMass: Double,
    val flags: Set<String>,
    val combustionEnthalpyKilojoulesPerMole: Double?,
    val model: String,
    /** Hydrogen-bond donor sites: hydrogens carried by N, O or F. */
    val hydrogenBondDonors: Int = 0,
    /** Hydrogen-bond acceptor sites: lone pairs on N, O or F. */
    val hydrogenBondAcceptors: Int = 0,
    /** Energy of the hydrogen-bond network a pure liquid of this substance can close. */
    val hydrogenBondNetworkKilojoulesPerMole: Double = 0.0,
    /** Number of Hückel-aromatic rings found in the Kekulé graph. */
    val aromaticRings: Int = 0,
    /** Resonance stabilization carried by those aromatic rings. */
    val aromaticResonanceKilojoulesPerMole: Double = 0.0,
    /** Lewis-acid sites: atoms with an incomplete octet or duet. */
    val coordinateBondAcceptors: Int = 0,
)

/**
 * Pure structure → properties evaluator. All coefficients live in
 * [PropertyRuleSet], not in named substance records or gameplay code.
 */
class PropertyEngine(private val rules: PropertyRuleSet) {
    private val hydrogenBonds = HydrogenBondModel(rules.interactions)
    private val coordinateBonds = CoordinateBondModel(rules.interactions)

    fun predict(graph: MoleculeGraph): PredictedProperties {
        val formula = FormulaCalculator.counts(graph)
        val boilingPoint = predictBoilingPoint(graph, formula)
        val combustion = estimateCombustionEnthalpy(graph, formula)
        val hydrogenBondDonors = hydrogenBonds.donorSites(graph)
        val hydrogenBondAcceptors = hydrogenBonds.acceptorSites(graph)
        val aromaticRingCount = AromaticityModel.aromaticRings(graph).size
        val flags = buildSet {
            if (graph.bonds.any { bond ->
                    bond.order == BondOrder.SINGLE &&
                        graph.atom(bond.first).element.symbol == "O" &&
                        graph.atom(bond.second).element.symbol == "O"
                }
            ) add("PEROXIDE_BOND")
            if (graph.atoms.any { it.formalCharge != 0 }) add("FORMAL_CHARGE")
            if (graph.bonds.any { it.order == BondOrder.TRIPLE }) add("TRIPLE_BOND")
            if (hasCycle(graph)) add("CYCLIC_STRUCTURE")
            if (graph.bonds.any { bond ->
                    setOf(
                        graph.atom(bond.first).element.symbol,
                        graph.atom(bond.second).element.symbol,
                    ) == NITROGEN_OXYGEN_PAIR
                }
            ) add("NITROGEN_OXYGEN_BOND")
            if (graph.atoms.any { it.element.symbol in HALOGEN_SYMBOLS }) add("HALOGENATED")
            if (hasThreeMemberedRing(graph)) add("SMALL_RING_STRAIN")
            if (combustion != null && combustion < 0.0) add("COMBUSTIBLE_ESTIMATE")
            if (hydrogenBondDonors > 0) add("HYDROGEN_BOND_DONOR")
            if (hydrogenBondAcceptors > 0) add("HYDROGEN_BOND_ACCEPTOR")
            if (aromaticRingCount > 0) add("AROMATIC_RING")
            if (coordinateBonds.acceptorAtoms(graph).isNotEmpty()) add("COORDINATE_BOND_ACCEPTOR")
        }
        return PredictedProperties(
            boilingPointC = boilingPoint,
            molarMass = FormulaCalculator.molarMass(graph),
            flags = flags,
            combustionEnthalpyKilojoulesPerMole = combustion,
            model = rules.modelId,
            hydrogenBondDonors = hydrogenBondDonors,
            hydrogenBondAcceptors = hydrogenBondAcceptors,
            hydrogenBondNetworkKilojoulesPerMole = hydrogenBonds.networkKilojoulesPerMole(graph),
            aromaticRings = aromaticRingCount,
            aromaticResonanceKilojoulesPerMole =
                aromaticRingCount * rules.interactions.aromaticResonanceKilojoulesPerMolePerRing,
            coordinateBondAcceptors = coordinateBonds.acceptorAtoms(graph).size,
        )
    }

    private fun predictBoilingPoint(graph: MoleculeGraph, formula: Map<String, Int>): Double? {
        val formulaText = FormulaCalculator.hillFormula(graph)
        rules.boiling.exactFormulaCelsius[formulaText]?.let { return it }
        if (hasCycle(graph)) return null
        val carbon = formula["C"] ?: 0
        val oxygenAtoms = graph.atoms.filter { it.element.symbol == "O" }
        return when {
            oxygenAtoms.size == 1 && isHydroxyl(graph, oxygenAtoms.single().id) ->
                rules.boiling.hydroxylFirstCarbonCelsius +
                    rules.boiling.hydroxylAdditionalCarbonCelsius * (carbon - 1).coerceAtLeast(0)
            oxygenAtoms.size == 1 && graph.bondsOf(oxygenAtoms.single().id).size == 2 ->
                rules.boiling.etherBaseCelsius +
                    rules.boiling.etherAdditionalCarbonCelsius * (carbon - 2).coerceAtLeast(0)
            carbon > 0 && oxygenAtoms.isEmpty() -> {
                val additionalCarbon = carbon - 1
                rules.boiling.hydrocarbonFirstCarbonCelsius +
                    rules.boiling.hydrocarbonLinearCarbonCelsius * additionalCarbon -
                    rules.boiling.hydrocarbonQuadraticCarbonCelsius * additionalCarbon * (carbon - 2)
            }
            else -> null
        }
    }

    /** Mean-bond-energy ΔH estimate for neutral C/H/O complete combustion only. */
    private fun estimateCombustionEnthalpy(graph: MoleculeGraph, formula: Map<String, Int>): Double? {
        if (graph.atoms.any { it.formalCharge != 0 }) return null
        if (formula.keys.any { it !in setOf("C", "H", "O") }) return null
        val carbon = formula["C"] ?: return null
        val hydrogen = formula["H"] ?: 0
        val oxygen = formula["O"] ?: 0
        val oxygenMoles = carbon + hydrogen / 4.0 - oxygen / 2.0
        if (oxygenMoles <= 0.0) return null
        val reactantBondEnergy = graphBondEnergy(graph) ?: return null
        val oxygenBondEnergy = oxygenMoles * (rules.bondEnergy("O", "O", BondOrder.DOUBLE) ?: return null)
        val productBondEnergy = carbon * 2.0 * rules.carbonDioxideCarbonOxygenDoubleBondKilojoulesPerMole +
            (hydrogen / 2.0) * 2.0 * (rules.bondEnergy("O", "H", BondOrder.SINGLE) ?: return null)
        return reactantBondEnergy + oxygenBondEnergy - productBondEnergy
    }

    private fun graphBondEnergy(graph: MoleculeGraph): Double? {
        var total = 0.0
        graph.bonds.forEach { bond ->
            total += rules.bondEnergy(
                graph.atom(bond.first).element.symbol,
                graph.atom(bond.second).element.symbol,
                bond.order,
            ) ?: return null
        }
        graph.atoms.forEach { atom ->
            val hydrogenCount = atom.explicitHydrogens + graph.implicitHydrogens(atom.id)
            if (hydrogenCount > 0) {
                total += hydrogenCount * (rules.bondEnergy(atom.element.symbol, "H", BondOrder.SINGLE) ?: return null)
            }
        }
        return total
    }

    private fun isHydroxyl(graph: MoleculeGraph, oxygenId: Int): Boolean =
        graph.bondsOf(oxygenId).size == 1 && graph.implicitHydrogens(oxygenId) + graph.atom(oxygenId).explicitHydrogens > 0

    private fun hasCycle(graph: MoleculeGraph): Boolean {
        val unseen = graph.atoms.map { it.id }.toMutableSet()
        var components = 0
        while (unseen.isNotEmpty()) {
            components++
            val pending = ArrayDeque<Int>()
            pending.add(unseen.first())
            while (pending.isNotEmpty()) {
                val atom = pending.removeFirst()
                if (!unseen.remove(atom)) continue
                graph.neighbors(atom).forEach { (neighbor, _) ->
                    if (neighbor.id in unseen) pending.add(neighbor.id)
                }
            }
        }
        return graph.bonds.size > graph.atoms.size - components
    }

    /**
     * Three mutually bonded atoms: the smallest strained cycle. Detected locally because
     * ring strain is a structural fact of the graph, not a property of a named substance.
     */
    private fun hasThreeMemberedRing(graph: MoleculeGraph): Boolean = graph.atoms.any { atom ->
        val neighbors = graph.neighbors(atom.id).map { (neighbor, _) -> neighbor.id }
        neighbors.any { first ->
            val firstNeighbors = graph.neighbors(first).map { (neighbor, _) -> neighbor.id }
            neighbors.any { second -> second != first && second in firstNeighbors }
        }
    }

    private companion object {
        val NITROGEN_OXYGEN_PAIR = setOf("N", "O")
        val HALOGEN_SYMBOLS = setOf("F", "Cl", "Br", "I")
    }
}

/** Compatibility facade for the public M0 API: mol.properties(). */
object PropertyPredictor {
    private val defaultEngine by lazy { PropertyEngine(PropertyRuleSet.bundled()) }

    fun predict(graph: MoleculeGraph): PredictedProperties = defaultEngine.predict(graph)
}
