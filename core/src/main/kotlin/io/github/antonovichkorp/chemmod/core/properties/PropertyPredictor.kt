package io.github.antonovichkorp.chemmod.core.properties

import io.github.antonovichkorp.chemmod.core.FormulaCalculator
import io.github.antonovichkorp.chemmod.core.bond.AromaticityModel
import io.github.antonovichkorp.chemmod.core.bond.CoordinateBondModel
import io.github.antonovichkorp.chemmod.core.bond.HydrogenBondModel
import io.github.antonovichkorp.chemmod.core.chemistry.FunctionalGroups
import io.github.antonovichkorp.chemmod.core.geometry.MolecularGeometry
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
import kotlin.math.pow

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
    /**
     * Where the boiling point came from: a fitted family, or a calibration anchor naming one
     * substance. An anchor is a debt, so it is reported instead of hiding behind a number.
     */
    val boilingPointBasis: String = "no-model",
    /** VSEPR shape at the atom with the most electron domains; water is bent, methane tetrahedral. */
    val molecularShape: String? = null,
    /** Ideal bond angle at that atom in degrees, when the shape has an angle at all. */
    val bondAngleDegrees: Double? = null,
    /**
     * Sum of atomic polarizibilities in cubic angstroms, or null if the table has no value for
     * some element present. Tetrachloromethane reaches 10.5 A^3 where methane reaches 4.4, which
     * molar mass cannot express: 154 g/mol of CCl4 is mostly chlorine's electron cloud.
     */
    val polarizibilityCubicAngstroms: Double? = null,
    /**
     * Compound classes derived from the graph: `alkane`, `alkene`, `diene`, `arene`, `alcohol`,
     * `ether`, `ketone`, `amine` and the rest. A class is a statement about bonds, so no substance
     * is ever listed to get one.
     */
    val compoundClasses: Set<String> = emptySet(),
    /** Functional groups found, one entry per kind. */
    val functionalGroups: Set<String> = emptySet(),
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
        val geometry = MolecularGeometry.centralAtom(graph)
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
            if (geometry?.shape == "bent") add("BENT_GEOMETRY")
        }
        return PredictedProperties(
            boilingPointC = boilingPoint.celsius,
            boilingPointBasis = boilingPoint.basis,
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
            molecularShape = geometry?.shape,
            bondAngleDegrees = geometry?.idealBondAngleDegrees,
            polarizibilityCubicAngstroms = FormulaCalculator.polarizibilityCubicAngstroms(graph),
            compoundClasses = FunctionalGroups.classes(graph),
            functionalGroups = FunctionalGroups.groups(graph).map { it.kind }.toSet(),
        )
    }

    /** One boiling point value plus the branch that produced it. */
    private data class BoilingEstimate(val celsius: Double?, val basis: String)

    /**
     * Dispersion plus the interactions the graph actually has:
     *
     *  * a calibration anchor, reported as such, for substances the model cannot derive yet;
 *  * saturated acyclic monohydric alcohols: dispersion, carbon count, hydrogen-bond-site
 *    density, OH-centre shielding, skeleton branching and (for linear secondary C5-C6) OH-arm
 *    length imbalance, all derived from the graph;
     *  * polyols: the older dispersion plus hydrogen-bond-network branch; its glycol residual is
     *    documented as open rather than treated as accepted;
     *  * amines: dispersion plus one term per hydrogen the nitrogen can donate, which is what
     *    orders the three C4H11N isomers (RMS 4.4 K);
     *  * ketones and aldehydes, ethers: dispersion plus ring and aromaticity corrections
     *    (RMS 2.6 K and 1.1 K);
     *  * monohalides: dispersion plus one term per halogen element, read from
     *    [FunctionalGroups.halogenCounts] so the branch names no element itself (RMS 4.9 K);
     *  * nitriles: dispersion plus the ring, aromaticity and skeleton-branching corrections, on
     *    saturated and aryl nitriles (RMS 1.6 K). Conjugated alkenenitriles are refused;
     *  * esters: the same shape of correction on eight sourced esters (RMS 3.6 K). The branching
     *    coefficient is shared with the nitrile branch and fitted jointly over both;
     *  * hydrocarbons: dispersion `a + b * M^(2/3)` plus ring and aromaticity corrections,
     *    fitted on alkanes, alkenes, cycloalkanes and arenes (RMS 6.3 K);
     *  * anything else: no value at all. Molar mass is a stand-in for polarizibility and it
     *    collapses outside carbon and hydrogen - measured against experiment the old branch gave
     *    tetrachloromethane +131 K, tetrafluoromethane +196 K, acetonitrile -137 K, methylamine
     *    -81 K. A refused number is honest; a wrong one is not. Polyhalides are refused for the
     *    same reason: geminal and vicinal arrangements differ through permanent dipoles this
     *    model has no term for.
     */
    private fun predictBoilingPoint(graph: MoleculeGraph, formula: Map<String, Int>): BoilingEstimate {
        val formulaText = FormulaCalculator.hillFormula(graph)
        rules.boiling.exactFormulaCelsius[formulaText]?.let {
            return BoilingEstimate(it, "calibration-anchor:$formulaText")
        }
        val dispersion = FormulaCalculator.molarMass(graph).pow(DISPERSION_MASS_EXPONENT)
        val classes = FunctionalGroups.classes(graph)
        val hydroxylFamily = FunctionalGroups.ALCOHOL in classes &&
            classes.none { it in FunctionalGroups.OXYGEN_CLASSES_BEYOND_HYDROXYL }
        if (hydroxylFamily) {
            val network = hydrogenBonds.networkKilojoulesPerMole(graph)
            val hydroxylCount = FunctionalGroups.groups(graph).count { it.kind == FunctionalGroups.HYDROXYL }
            if (hydroxylCount == 1) {
                val carbonCount = formula["C"] ?: 0
                val supportedElements = graph.atoms.all { it.element.symbol in setOf("C", "H", "O") }
                val saturatedAcyclic = graph.bonds.all { it.order == BondOrder.SINGLE } &&
                    FunctionalGroups.cycleRank(graph) == 0
                val hydroxylSubstitution = FunctionalGroups.hydroxylCarbonSubstitution(graph)
                val skeletonBranchPoints = FunctionalGroups.carbonBranchPointsOutsideHydroxylCentres(graph)
                if (carbonCount !in 1..6 || !supportedElements || !saturatedAcyclic ||
                    hydroxylSubstitution > 2 || skeletonBranchPoints > 1
                ) {
                    return BoilingEstimate(null, "no-model:monohydric-alcohol-fit-covers-C1-C6-saturated-acyclic-C-H-O")
                }
                val distalBranchPoints = FunctionalGroups.carbonBranchPointsDistalToHydroxylCentres(graph)
                val linearSecondaryC5C6 = hydroxylSubstitution == 1 &&
                    carbonCount in 5..6 && skeletonBranchPoints == 0
                if (hydroxylSubstitution == 0 && carbonCount == 6 && distalBranchPoints > 0) {
                    // C6 primary distal-branch holdouts still miss by up to 2.15 K. Keep this
                    // structural subgroup refused until the branch-position dependence is fitted.
                    return BoilingEstimate(null, "no-model:monohydric-alcohol-distal-branch-C6-not-validated")
                }
                if (hydroxylSubstitution > 0 && carbonCount > 4 && !linearSecondaryC5C6) {
                    // C5-C6 tertiary and branched secondary holdouts miss by up to 9.6 K. Do not
                    // extrapolate the C3-C4 substitution fit into these unresolved topologies.
                    return BoilingEstimate(
                        null,
                        "no-model:monohydric-alcohol-fit-excludes-branched-or-tertiary-C5-C6",
                    )
                }
                val longSecondaryCorrection = if (linearSecondaryC5C6) {
                    rules.boiling.monohydricAlcoholLongSecondaryBaseCelsius +
                        rules.boiling.monohydricAlcoholArmImbalanceCelsiusPerCarbon *
                        FunctionalGroups.hydroxylCarbonArmLengthImbalance(graph)
                } else {
                    0.0
                }
                val basis = when {
                    linearSecondaryC5C6 ->
                        "monohydric-alcohol-structure+dispersion+association+OH-arm-topology"
                    distalBranchPoints > 0 ->
                        "monohydric-alcohol-structure+dispersion+association+branch-distance"
                    skeletonBranchPoints > 0 ->
                        "monohydric-alcohol-structure+dispersion+association+branching"
                    else -> "monohydric-alcohol-structure+dispersion+association"
                }
                return BoilingEstimate(
                    rules.boiling.monohydricAlcoholBaseCelsius +
                        rules.boiling.monohydricAlcoholDispersionCelsius * dispersion +
                        rules.boiling.monohydricAlcoholCarbonCelsius * carbonCount +
                        rules.boiling.monohydricAlcoholHydrogenBondDensityCelsiusPerKilojoulePerCarbon *
                        network / carbonCount +
                        rules.boiling.monohydricAlcoholHydroxylSubstitutionCelsius * hydroxylSubstitution +
                        rules.boiling.monohydricAlcoholHydroxylSubstitutionPerCarbonCelsius *
                        hydroxylSubstitution * carbonCount +
                        rules.boiling.monohydricAlcoholBranchPointCelsius * skeletonBranchPoints +
                        rules.boiling.monohydricAlcoholDistalBranchPointCelsius * distalBranchPoints +
                        longSecondaryCorrection,
                    basis,
                )
            }
            return BoilingEstimate(
                rules.boiling.hydroxylBaseCelsius +
                    rules.boiling.hydroxylDispersionCelsius * dispersion +
                    rules.boiling.hydrogenBondCelsiusPerKilojoulePerMole * network,
                "polyol-family+hydrogen-bond-network",
            )
        }
        val rings = FunctionalGroups.cycleRank(graph)
        val aromatic = AromaticityModel.aromaticRings(graph).size
        val donors = hydrogenBonds.donorSites(graph)
        val polarClasses = setOf(
            FunctionalGroups.ALCOHOL,
            FunctionalGroups.ALDEHYDE,
            FunctionalGroups.KETONE,
            FunctionalGroups.CARBOXYLIC_ACID,
            FunctionalGroups.ESTER_CLASS,
            FunctionalGroups.AMINE_CLASS,
            FunctionalGroups.NITRILE_CLASS,
        )
        if (FunctionalGroups.AMINE_CLASS in classes &&
            classes.none { it in polarClasses - FunctionalGroups.AMINE_CLASS }
        ) {
            return BoilingEstimate(
                rules.boiling.amineBaseCelsius +
                    rules.boiling.amineDispersionCelsius * dispersion +
                    rules.boiling.amineHydrogenBondDonorCelsius * donors +
                    rules.boiling.ringCelsius * rings +
                    rules.boiling.aromaticRingCelsius * aromatic,
                "amine+hydrogen-bond-donors",
            )
        }
        val carbonylClasses = setOf(FunctionalGroups.ALDEHYDE, FunctionalGroups.KETONE)
        if (classes.any { it in carbonylClasses } && classes.none { it in polarClasses - carbonylClasses }) {
            return BoilingEstimate(
                rules.boiling.carbonylBaseCelsius +
                    rules.boiling.carbonylDispersionCelsius * dispersion +
                    rules.boiling.ringCelsius * rings +
                    rules.boiling.aromaticRingCelsius * aromatic,
                "carbonyl+dispersion",
            )
        }
        if (FunctionalGroups.ETHER_CLASS in classes && classes.none { it in polarClasses }) {
            return BoilingEstimate(
                rules.boiling.etherBaseCelsius +
                    rules.boiling.etherDispersionCelsius * dispersion +
                    rules.boiling.ringCelsius * rings +
                    rules.boiling.aromaticRingCelsius * aromatic,
                "ether+dispersion",
            )
        }
        if (FunctionalGroups.NITRILE_CLASS in classes &&
            classes.none { it in polarClasses - FunctionalGroups.NITRILE_CLASS }
        ) {
            // Saturated and aryl nitriles only. An alpha,beta-unsaturated nitrile conjugates its
            // C=C with the C#N and boils some 20 K below a saturated nitrile of the same mass,
            // which no dispersion term can see: measured, this fit would put acrylonitrile out by
            // that much. Refused, not extrapolated.
            if (FunctionalGroups.hasConjugatedNitrile(graph)) {
                return BoilingEstimate(null, "no-model:nitrile-fit-excludes-conjugated-alkenenitriles")
            }
            return BoilingEstimate(
                rules.boiling.nitrileBaseCelsius +
                    rules.boiling.nitrileDispersionCelsius * dispersion +
                    rules.boiling.branchPointCelsius * FunctionalGroups.branchPoints(graph) +
                    rules.boiling.ringCelsius * rings +
                    rules.boiling.aromaticRingCelsius * aromatic,
                "nitrile+dispersion+branching",
            )
        }
        if (FunctionalGroups.ESTER_CLASS in classes &&
            classes.none { it in polarClasses - FunctionalGroups.ESTER_CLASS }
        ) {
            return BoilingEstimate(
                rules.boiling.esterBaseCelsius +
                    rules.boiling.esterDispersionCelsius * dispersion +
                    rules.boiling.branchPointCelsius * FunctionalGroups.branchPoints(graph) +
                    rules.boiling.ringCelsius * rings +
                    rules.boiling.aromaticRingCelsius * aromatic,
                "ester+dispersion+branching",
            )
        }
        if (FunctionalGroups.CARBOXYLIC_ACID in classes &&
            // A carboxyl always brings a hydroxyl with it, so ALCOHOL is expected here and must
            // not disqualify the class - unlike every other polar class, which means a second
            // functional family is present and the fit no longer applies.
            classes.none { it in polarClasses - FunctionalGroups.CARBOXYLIC_ACID - FunctionalGroups.ALCOHOL }
        ) {
            // Monocarboxylic acids: every member of the fit donates exactly one hydrogen bond.
            // A dicarboxylic acid donates twice and its two carboxyls interact with each other as
            // well as with a partner, which is a different association than the fitted dimer, so
            // it is refused rather than extrapolated.
            if (donors != 1) {
                return BoilingEstimate(null, "no-model:acid-fit-covers-monocarboxylic-only")
            }
            return BoilingEstimate(
                rules.boiling.acidBaseCelsius +
                    rules.boiling.acidDispersionCelsius * dispersion +
                    rules.boiling.branchPointCelsius * FunctionalGroups.branchPoints(graph) +
                    rules.boiling.ringCelsius * rings +
                    rules.boiling.aromaticRingCelsius * aromatic,
                "acid+dispersion+branching",
            )
        }
        if (FunctionalGroups.HALIDE_CLASS in classes && classes.none { it in polarClasses }) {
            // One halogen per molecule is the fitted domain. Polyhalides are refused rather than
            // extrapolated: measured, these coefficients put dichlorodifluoromethane +131 K and
            // tetrachloromethane +106 K out, because geminal and vicinal arrangements differ
            // through permanent dipoles this model has no term for.
            val halogens = FunctionalGroups.halogenCounts(graph)
            val perAtom = halogens.mapNotNull { (symbol, count) ->
                rules.boiling.halideCelsiusPerAtom[symbol]?.let { it * count }
            }
            if (halogens.values.sum() == 1 && perAtom.size == halogens.size) {
                return BoilingEstimate(
                    rules.boiling.halideBaseCelsius +
                        rules.boiling.halideDispersionCelsius * dispersion +
                        perAtom.sum() +
                        rules.boiling.ringCelsius * rings +
                        rules.boiling.aromaticRingCelsius * aromatic,
                    "monohalide+dispersion",
                )
            }
            return BoilingEstimate(null, "no-model:halide-fit-covers-monohalides-only")
        }
        if (FunctionalGroups.HYDROCARBON in classes) {
            return BoilingEstimate(
                rules.boiling.hydrocarbonBaseCelsius +
                    rules.boiling.hydrocarbonDispersionCelsius * dispersion +
                    rules.boiling.ringCelsius * rings +
                    rules.boiling.aromaticRingCelsius * aromatic,
                "dispersion+ring+aromatic",
            )
        }
        if ((formula["C"] ?: 0) > 0) {
            return BoilingEstimate(null, "no-model:no-fitted-branch-for-these-classes")
        }
        return BoilingEstimate(null, "no-model")
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
        const val DISPERSION_MASS_EXPONENT = 2.0 / 3.0
    }
}

/** Compatibility facade for the public M0 API: mol.properties(). */
object PropertyPredictor {
    private val defaultEngine by lazy { PropertyEngine(PropertyRuleSet.bundled()) }

    fun predict(graph: MoleculeGraph): PredictedProperties = defaultEngine.predict(graph)
}
