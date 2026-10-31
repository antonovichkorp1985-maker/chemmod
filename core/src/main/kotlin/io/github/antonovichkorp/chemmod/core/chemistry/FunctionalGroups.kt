package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.bond.AromaticityModel
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/** One functional group, tied to the atom it sits on. */
data class FunctionalGroup(val kind: String, val atomId: Int)

/**
 * Functional groups and compound classes, derived from the graph.
 *
 * A class here is a statement about bonds, never a substance: butane and isobutane are both
 * alkanes because neither carries a multiple bond or a heteroatom, and nothing had to be told
 * their names. Property branches and reaction gates read these labels instead of inspecting atoms
 * themselves, so adding a class means adding a derivation rule, not a row per substance.
 */
object FunctionalGroups {
    // --- functional groups ---
    const val HYDROXYL = "hydroxyl"
    const val ETHER = "ether"
    const val CARBONYL = "carbonyl"
    const val CARBOXYL = "carboxyl"
    const val ESTER = "ester"
    const val AMINE = "amine"
    const val NITRILE = "nitrile"
    const val HALIDE = "halide"
    const val THIOL = "thiol"
    const val THIOETHER = "thioether"

    // --- compound classes ---
    const val HYDROCARBON = "hydrocarbon"
    const val ALKANE = "alkane"
    const val CYCLOALKANE = "cycloalkane"
    const val ALKENE = "alkene"
    const val DIENE = "diene"
    const val ALKYNE = "alkyne"
    const val ARENE = "arene"
    const val ALCOHOL = "alcohol"
    const val POLYOL = "polyol"
    const val ETHER_CLASS = "ether"
    const val ALDEHYDE = "aldehyde"
    const val KETONE = "ketone"
    const val CARBOXYLIC_ACID = "carboxylic-acid"
    const val ESTER_CLASS = "ester"
    const val AMINE_CLASS = "amine"
    const val PRIMARY_AMINE = "primary-amine"
    const val SECONDARY_AMINE = "secondary-amine"
    const val TERTIARY_AMINE = "tertiary-amine"
    const val NITRILE_CLASS = "nitrile"
    const val HALIDE_CLASS = "halide"
    const val THIOL_CLASS = "thiol"
    const val THIOETHER_CLASS = "thioether"

    /**
     * Oxygen-bearing classes other than the hydroxyl. A molecule whose every oxygen sits in a
     * hydroxyl is the alcohol and polyol family; one carbonyl or ether oxygen takes it out of it.
     */
    val OXYGEN_CLASSES_BEYOND_HYDROXYL: Set<String> =
        setOf(ETHER_CLASS, ALDEHYDE, KETONE, CARBOXYLIC_ACID, ESTER_CLASS)

    private val HALOGENS = setOf("F", "Cl", "Br", "I")

    /** Every functional group in the graph, one entry per occurrence. */
    fun groups(graph: MoleculeGraph): List<FunctionalGroup> = buildList {
        graph.atoms.forEach { atom ->
            val neighbours = graph.neighbors(atom.id)
            val hydrogens = hydrogens(graph, atom.id)
            val carbonOnly = neighbours.isNotEmpty() && neighbours.all { it.first.element.symbol == "C" }
            when (atom.element.symbol) {
                "O" -> when {
                    neighbours.size == 1 && hydrogens > 0 -> add(FunctionalGroup(HYDROXYL, atom.id))
                    neighbours.size == 2 && carbonOnly && hydrogens == 0 -> add(FunctionalGroup(ETHER, atom.id))
                }
                "N" -> when {
                    neighbours.any { it.second == BondOrder.TRIPLE && it.first.element.symbol == "C" } ->
                        add(FunctionalGroup(NITRILE, atom.id))
                    neighbours.isNotEmpty() &&
                        neighbours.all { it.second == BondOrder.SINGLE } &&
                        neighbours.all { it.first.element.symbol == "C" || it.first.element.symbol == "H" } ->
                        add(FunctionalGroup(AMINE, atom.id))
                }
                "S" -> when {
                    neighbours.size == 1 && hydrogens > 0 -> add(FunctionalGroup(THIOL, atom.id))
                    neighbours.size == 2 && carbonOnly && hydrogens == 0 -> add(FunctionalGroup(THIOETHER, atom.id))
                }
                "C" -> oxygenatedCarbon(graph, atom.id)?.let { add(FunctionalGroup(it, atom.id)) }
                in HALOGENS -> add(FunctionalGroup(HALIDE, atom.id))
            }
        }
    }.distinct()

    /**
     * How many atoms of each halogen the graph carries, keyed by element symbol. The halogen set
     * lives here and nowhere else: a property branch that needs it reads this map instead of
     * naming elements itself, so the day astatine gets a coefficient nothing moves in code.
     */
    fun halogenCounts(graph: MoleculeGraph): Map<String, Int> =
        graph.atoms.asSequence()
            .map { it.element.symbol }
            .filter { it in HALOGENS }
            .groupingBy { it }
            .eachCount()

    /**
     * Skeleton branch points: heavy atoms joined to three or more other heavy atoms, all through
     * single bonds. A branch makes a molecule more compact, so it touches its neighbours over less
     * surface and boils lower — n-butane at −0.5 °C against isobutane at −11.8 °C.
     *
     * Restricted to single bonds so an aromatic carbon carrying a substituent is not counted:
     * benzonitrile's ring carbon has three heavy neighbours and is not branched in any sense that
     * reduces its surface. Ring atoms are not branches either, since a ring atom has two.
     *
     * Coarse by construction — isopentane and neopentane both report one and boil 18 K apart. It
     * counts branch points, not how crowded they are.
     */
    fun branchPoints(graph: MoleculeGraph): Int =
        graph.atoms.count { atom ->
            val bonds = graph.bondsOf(atom.id)
            bonds.all { it.order == BondOrder.SINGLE } &&
                bonds.count { graph.atom(it.other(atom.id)).element.symbol != "H" } >= 3
        }

    /**
     * True when a nitrile carbon sits beside a carbon of an isolated C=C: an α,β-unsaturated
     * nitrile, where the double bond is conjugated with the C≡N. That conjugation is worth some
     * 20 K against a saturated nitrile of the same mass — acrylonitrile boils at 77 °C at 53 g/mol
     * while propionitrile boils at 97 °C at 55 — and the mass-only dispersion term has nothing to
     * say about it, so such molecules are refused rather than mispredicted.
     *
     * Aromatic double bonds do not count. Benzonitrile is conjugated too, but its ring is a
     * different electronic situation and it fits the saturated-and-aryl branch to within 0.6 K, so
     * treating it as an alkenenitrile would refuse a molecule the model actually predicts well.
     */
    fun hasConjugatedNitrile(graph: MoleculeGraph): Boolean {
        val aromatic = AromaticityModel.aromaticRings(graph).flatten().toSet()
        return graph.atoms.any { atom ->
            atom.element.symbol == "C" &&
                graph.neighbors(atom.id).any { (neighbor, order) ->
                    neighbor.element.symbol == "N" && order == BondOrder.TRIPLE
                } &&
                graph.neighbors(atom.id).any { (neighbor, order) ->
                    order == BondOrder.SINGLE && neighbor.element.symbol == "C" &&
                        neighbor.id !in aromatic &&
                        graph.neighbors(neighbor.id).any { (next, nextOrder) ->
                            next.id != atom.id && nextOrder == BondOrder.DOUBLE && next.id !in aromatic
                        }
                }
        }
    }

    /** The compound classes a graph belongs to; a molecule usually carries several. */
    fun classes(graph: MoleculeGraph): Set<String> {
        val groups = groups(graph)
        val counts = groups.groupingBy { it.kind }.eachCount()
        val symbols = graph.atoms.map { it.element.symbol }.toSet()
        val hydrocarbon = symbols.all { it == "C" || it == "H" }
        val tripleBonds = carbonCarbonBonds(graph, BondOrder.TRIPLE)
        val aromaticRings = AromaticityModel.aromaticRings(graph)
        val aromatic = aromaticRings.isNotEmpty()
        // Kekule double bonds inside an aromatic ring make an arene, not an alkene: benzene is
        // not a diene, and only double bonds outside the sextet count as one.
        val aromaticPairs = aromaticRings.flatMap { ring ->
            (ring.zipWithNext() + (ring.last() to ring.first())).map { setOf(it.first, it.second) }
        }.toSet()
        val doubleBonds = carbonCarbonBonds(graph, BondOrder.DOUBLE) { bond ->
            setOf(bond.first, bond.second) !in aromaticPairs
        }
        val cyclic = cycleRank(graph) > 0
        return linkedSetOf<String>().apply {
            if (hydrocarbon) add(HYDROCARBON)
            if (doubleBonds >= 1) add(ALKENE)
            if (doubleBonds >= 2) add(DIENE)
            if (tripleBonds >= 1) add(ALKYNE)
            if (aromatic) add(ARENE)
            if (hydrocarbon && doubleBonds == 0 && tripleBonds == 0 && !aromatic) {
                add(if (cyclic) CYCLOALKANE else ALKANE)
            }
            if ((counts[HYDROXYL] ?: 0) > 0) {
                add(ALCOHOL)
                if ((counts[HYDROXYL] ?: 0) >= 2) add(POLYOL)
            }
            if ((counts[ETHER] ?: 0) > 0) add(ETHER_CLASS)
            if ((counts[CARBOXYL] ?: 0) > 0) add(CARBOXYLIC_ACID)
            if ((counts[ESTER] ?: 0) > 0) add(ESTER_CLASS)
            groups.filter { it.kind == CARBONYL }.forEach { group ->
                add(if (hydrogens(graph, group.atomId) > 0) ALDEHYDE else KETONE)
            }
            if ((counts[AMINE] ?: 0) > 0) {
                add(AMINE_CLASS)
                groups.filter { it.kind == AMINE }.forEach { group ->
                    when (hydrogens(graph, group.atomId)) {
                        0 -> add(TERTIARY_AMINE)
                        1 -> add(SECONDARY_AMINE)
                        else -> add(PRIMARY_AMINE)
                    }
                }
            }
            if ((counts[NITRILE] ?: 0) > 0) add(NITRILE_CLASS)
            if ((counts[HALIDE] ?: 0) > 0) add(HALIDE_CLASS)
            if ((counts[THIOL] ?: 0) > 0) add(THIOL_CLASS)
            if ((counts[THIOETHER] ?: 0) > 0) add(THIOETHER_CLASS)
        }
    }

    /**
     * Count how many carbon neighbours beyond a primary alcohol centre are attached to the
     * hydroxyl-bearing carbon. Primary alcohols (and methanol) contribute zero, secondary centres
     * one, tertiary centres two. This is a graph-derived steric descriptor, not an alcohol list.
     */
    fun hydroxylCarbonSubstitution(graph: MoleculeGraph): Int =
        groups(graph).asSequence()
            .filter { it.kind == HYDROXYL }
            .mapNotNull { hydroxyl ->
                graph.neighbors(hydroxyl.atomId)
                    .firstOrNull { it.first.element.symbol == "C" }
                    ?.first?.id
            }
            .sumOf { carbonId ->
                (graph.neighbors(carbonId).count { it.first.element.symbol == "C" } - 1).coerceAtLeast(0)
            }

    /**
     * Difference between the largest and smallest carbon-arm sizes attached to each OH-bearing
     * carbon, measured after cutting the OH centre out of the carbon skeleton. A linear secondary
     * alcohol therefore distinguishes 2-pentanol (1 vs 3 carbons) from 3-pentanol (2 vs 2).
     * This is a graph descriptor; it contains no names or formula-specific cases.
     */
    fun hydroxylCarbonArmLengthImbalance(graph: MoleculeGraph): Int =
        groups(graph).asSequence()
            .filter { it.kind == HYDROXYL }
            .mapNotNull { hydroxyl ->
                graph.neighbors(hydroxyl.atomId)
                    .firstOrNull { it.first.element.symbol == "C" }
                    ?.first?.id
            }
            .sumOf { hydroxylCarbonId ->
                val arms = graph.neighbors(hydroxylCarbonId)
                    .filter { it.first.element.symbol == "C" }
                    .map { (start, _) ->
                        val visited = mutableSetOf(hydroxylCarbonId)
                        val pending = ArrayDeque<Int>()
                        pending.add(start.id)
                        while (pending.isNotEmpty()) {
                            val atomId = pending.removeFirst()
                            if (!visited.add(atomId)) continue
                            graph.neighbors(atomId)
                                .filter { it.first.element.symbol == "C" }
                                .forEach { (neighbor, _) -> if (neighbor.id !in visited) pending.add(neighbor.id) }
                        }
                        visited.count { it != hydroxylCarbonId && graph.atom(it).element.symbol == "C" }
                    }
                if (arms.size < 2) 0 else arms.maxOrNull()!! - arms.minOrNull()!!
            }

    /**
     * Saturated carbon branch points away from hydroxyl-bearing carbons. Excluding the OH centre
     * keeps alcohol steric shielding separate from carbon-skeleton compactness.
     */
    fun carbonBranchPointsOutsideHydroxylCentres(graph: MoleculeGraph): Int {
        val hydroxylCentres = groups(graph).asSequence()
            .filter { it.kind == HYDROXYL }
            .mapNotNull { hydroxyl ->
                graph.neighbors(hydroxyl.atomId)
                    .firstOrNull { it.first.element.symbol == "C" }
                    ?.first?.id
            }
            .toSet()
        return graph.atoms.count { atom ->
            atom.element.symbol == "C" && atom.id !in hydroxylCentres &&
                graph.bondsOf(atom.id).all { it.order == BondOrder.SINGLE } &&
                graph.neighbors(atom.id).count { it.first.element.symbol == "C" } >= 3
        }
    }

    /**
     * Count branch points at least two C-C bonds from an OH-bearing carbon. The OH-centre
     * distance distinguishes the proximal and distal skeleton branches of primary alcohols.
     */
    fun carbonBranchPointsDistalToHydroxylCentres(graph: MoleculeGraph): Int {
        val hydroxylCentres = groups(graph).asSequence()
            .filter { it.kind == HYDROXYL }
            .mapNotNull { hydroxyl ->
                graph.neighbors(hydroxyl.atomId)
                    .firstOrNull { it.first.element.symbol == "C" }
                    ?.first?.id
            }
            .toSet()
        if (hydroxylCentres.isEmpty()) return 0

        val distances = mutableMapOf<Int, Int>()
        val pending = ArrayDeque<Int>()
        hydroxylCentres.forEach { centre ->
            distances[centre] = 0
            pending.add(centre)
        }
        while (pending.isNotEmpty()) {
            val atomId = pending.removeFirst()
            val nextDistance = distances.getValue(atomId) + 1
            graph.neighbors(atomId)
                .filter { it.first.element.symbol == "C" }
                .forEach { (neighbor, _) ->
                    if (neighbor.id !in distances) {
                        distances[neighbor.id] = nextDistance
                        pending.add(neighbor.id)
                    }
                }
        }

        return graph.atoms.count { atom ->
            atom.element.symbol == "C" && atom.id !in hydroxylCentres &&
                graph.bondsOf(atom.id).all { it.order == BondOrder.SINGLE } &&
                graph.neighbors(atom.id).count { it.first.element.symbol == "C" } >= 3 &&
                (distances[atom.id]?.let { it > 1 } ?: false)
        }
    }

    /**
     * Cycle-basis rank: bonds - atoms + components. One for cyclohexane, two for naphthalene,
     * zero for anything acyclic. Shared with the boiling-point model so a ring means one thing.
     */
    fun cycleRank(graph: MoleculeGraph): Int {
        val unseen = graph.atoms.map { it.id }.toMutableSet()
        var components = 0
        while (unseen.isNotEmpty()) {
            components++
            val pending = ArrayDeque<Int>()
            pending.add(unseen.first())
            while (pending.isNotEmpty()) {
                val atom = pending.removeFirst()
                if (!unseen.remove(atom)) continue
                graph.neighbors(atom).forEach { pending.add(it.first.id) }
            }
        }
        return graph.bonds.size - graph.atoms.size + components
    }

    private fun carbonCarbonBonds(
        graph: MoleculeGraph,
        order: BondOrder,
        extra: (io.github.antonovichkorp.chemmod.core.model.Bond) -> Boolean = { true },
    ): Int = graph.bonds.count { bond ->
        bond.order == order &&
            graph.atom(bond.first).element.symbol == "C" &&
            graph.atom(bond.second).element.symbol == "C" &&
            extra(bond)
    }

    /** Carbonyl, carboxyl or ester, decided by what else hangs off the carbon. */
    private fun oxygenatedCarbon(graph: MoleculeGraph, carbonId: Int): String? {
        val neighbours = graph.neighbors(carbonId)
        if (neighbours.none { it.second == BondOrder.DOUBLE && it.first.element.symbol == "O" }) return null
        val singleBondedOxygen = neighbours.firstOrNull {
            it.second == BondOrder.SINGLE && it.first.element.symbol == "O"
        }?.first?.id
        return when {
            singleBondedOxygen == null -> CARBONYL
            hydrogens(graph, singleBondedOxygen) > 0 -> CARBOXYL
            graph.neighbors(singleBondedOxygen).any { it.first.element.symbol == "C" && it.first.id != carbonId } ->
                ESTER
            else -> CARBONYL
        }
    }

    private fun hydrogens(graph: MoleculeGraph, atomId: Int): Int =
        graph.implicitHydrogens(atomId) + graph.atom(atomId).explicitHydrogens
}
