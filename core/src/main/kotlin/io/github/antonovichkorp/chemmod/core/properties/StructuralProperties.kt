package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/**
 * The only phase assertion that can be made from the current structure-only
 * model. A condensed substance can still be liquid or solid; that distinction
 * requires a calibrated melting model and is intentionally not guessed.
 */
enum class ReferencePhase {
    GAS,
    CONDENSED,
    UNKNOWN,
}

/**
 * Transparent estimates derived from bonds and the existing group-contribution
 * boiling model. They are deliberately labelled estimates: no tabulated
 * per-substance property is stored here.
 */
data class StructuralProperties(
    val formula: String,
    val molarMassGramsPerMole: Double,
    val boilingPointEstimateCelsius: Double?,
    val referencePhaseAt20C: ReferencePhase,
    val combustionEnthalpyEstimateKilojoulesPerMole: Double?,
    val flags: Set<String>,
    val model: String = "structure-bond-energy-v1",
)

object StructuralPropertyAnalyzer {
    private const val REFERENCE_TEMPERATURE_C = 20.0

    /** Mean bond dissociation energies in kJ/mol used only for a transparent estimate. */
    private val BOND_ENERGY_KJ_PER_MOL = mapOf(
        BondKey.of("C", "C", BondOrder.SINGLE) to 347.0,
        BondKey.of("C", "C", BondOrder.DOUBLE) to 614.0,
        BondKey.of("C", "C", BondOrder.TRIPLE) to 839.0,
        BondKey.of("C", "H", BondOrder.SINGLE) to 413.0,
        BondKey.of("C", "O", BondOrder.SINGLE) to 358.0,
        BondKey.of("C", "O", BondOrder.DOUBLE) to 745.0,
        BondKey.of("O", "H", BondOrder.SINGLE) to 463.0,
        BondKey.of("O", "O", BondOrder.SINGLE) to 146.0,
        BondKey.of("O", "O", BondOrder.DOUBLE) to 498.0,
    )
    private const val CARBON_DIOXIDE_CARBON_OXYGEN_DOUBLE_BOND = 799.0

    fun analyze(graph: MoleculeGraph): StructuralProperties {
        val basic = PropertyPredictor.predict(graph)
        val formula = FormulaCalculator.counts(graph)
        val combustion = estimateCombustionEnthalpy(graph, formula)
        val flags = buildSet {
            addAll(basic.flags)
            if (combustion != null && combustion < 0.0) add("COMBUSTIBLE_ESTIMATE")
            if (formula["C"].orZero() > 0 && formula["H"].orZero() == 0) add("HYDROGEN_FREE_CARBON")
        }
        return StructuralProperties(
            formula = FormulaCalculator.hillFormula(graph),
            molarMassGramsPerMole = FormulaCalculator.molarMass(graph),
            boilingPointEstimateCelsius = basic.boilingPointC,
            referencePhaseAt20C = phaseAtReferenceTemperature(basic.boilingPointC),
            combustionEnthalpyEstimateKilojoulesPerMole = combustion,
            flags = flags,
        )
    }

    private fun phaseAtReferenceTemperature(boilingPointCelsius: Double?): ReferencePhase = when {
        boilingPointCelsius == null -> ReferencePhase.UNKNOWN
        boilingPointCelsius < REFERENCE_TEMPERATURE_C -> ReferencePhase.GAS
        else -> ReferencePhase.CONDENSED
    }

    /**
     * Estimates ΔH for complete combustion of a neutral C/H/O molecule into
     * CO₂ and H₂O. Unsupported bonds or atoms return null rather than inventing
     * a number. The result is per mole of input molecule and is not a lookup.
     */
    private fun estimateCombustionEnthalpy(
        graph: MoleculeGraph,
        formula: Map<String, Int>,
    ): Double? {
        if (graph.atoms.any { it.formalCharge != 0 }) return null
        if (formula.keys.any { it !in setOf("C", "H", "O") }) return null
        val carbon = formula["C"].orZero()
        val hydrogen = formula["H"].orZero()
        val oxygen = formula["O"].orZero()
        if (carbon == 0) return null

        val oxygenMoles = carbon + hydrogen / 4.0 - oxygen / 2.0
        if (oxygenMoles <= 0.0) return null
        val reactantBondEnergy = graphBondEnergy(graph) ?: return null
        val oxygenBondEnergy = oxygenMoles * BOND_ENERGY_KJ_PER_MOL.getValue(BondKey.of("O", "O", BondOrder.DOUBLE))
        val productBondEnergy = carbon * 2.0 * CARBON_DIOXIDE_CARBON_OXYGEN_DOUBLE_BOND +
            (hydrogen / 2.0) * 2.0 * BOND_ENERGY_KJ_PER_MOL.getValue(BondKey.of("O", "H", BondOrder.SINGLE))
        return reactantBondEnergy + oxygenBondEnergy - productBondEnergy
    }

    private fun graphBondEnergy(graph: MoleculeGraph): Double? {
        var total = 0.0
        for (bond in graph.bonds) {
            val key = BondKey.of(graph.atom(bond.first).element.symbol, graph.atom(bond.second).element.symbol, bond.order)
            total += BOND_ENERGY_KJ_PER_MOL[key] ?: return null
        }
        for (atom in graph.atoms) {
            val hydrogenCount = atom.explicitHydrogens + graph.implicitHydrogens(atom.id)
            if (hydrogenCount == 0) continue
            total += hydrogenCount * (BOND_ENERGY_KJ_PER_MOL[
                BondKey.of(atom.element.symbol, "H", BondOrder.SINGLE)
            ] ?: return null)
        }
        return total
    }

    private data class BondKey(
        val first: String,
        val second: String,
        val order: BondOrder,
    ) {
        companion object {
            fun of(left: String, right: String, order: BondOrder): BondKey = BondKey(
                minOf(left, right),
                maxOf(left, right),
                order,
            )
        }
    }

    private fun Int?.orZero(): Int = this ?: 0
}
