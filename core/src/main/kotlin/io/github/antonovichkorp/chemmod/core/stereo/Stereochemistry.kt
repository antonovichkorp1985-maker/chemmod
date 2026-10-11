package io.github.antonovichkorp.chemmod.core.stereo

import io.github.antonovichkorp.chemmod.core.MoleculeCanonicalizer
import io.github.antonovichkorp.chemmod.core.model.Bond
import io.github.antonovichkorp.chemmod.core.model.BondOrder
import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph

/**
 * Which parts of a molecule have a spatial arrangement to speak of — and which of those arrangements
 * actually survive as separate substances.
 *
 * Three questions, and they do not have the same answer:
 *
 * 1. **Is there a choice at all?** Derived, here, from the graph alone. A centre with four different
 *    substituents has two arrangements whether anyone says so or not.
 * 2. **Which arrangement is this one?** Not derived and not derivable: the graph records who is
 *    bonded to whom, and carries no left or right. This is the one thing that has to be told.
 * 3. **Does the choice survive?** Mostly derived, and the criterion is a *barrier*, not a bond type.
 *    Around 1–10 kcal/mol the arrangements flow into each other and are conformers of one
 *    substance: butane eclipses at 3.5–5 kcal/mol, a cyclohexane ring flips at about 10 kcal/mol
 *    some 10⁵ times a second. Above roughly 22–25 kcal/mol they are isolable species.
 *
 * Question 3 is why "a single bond rotates, so it is a conformer" is not a safe rule. IUPAC defines
 * atropisomers as exactly the conformers that can be isolated as separate chemical species because
 * rotation about a single bond is hindered; Oki's line is a half-life above 1000 s, and measured
 * barriers sit near 24 kcal/mol (101 kJ/mol). BINAP and 1,1'-bi-2-naphthol are single-bond molecules
 * that are nevertheless two different substances.
 *
 * What this object decides today is the cheap and reliable part of question 3: pyramidal inversion
 * depends on the element, not on the substituents. Nitrogen inverts over a ~6 kcal/mol barrier —
 * ammonia does it 4·10¹⁰ times a second — so an amine with three different groups is chiral and
 * still cannot be resolved. Phosphorus needs ~30 kcal/mol and its enantiomers stand. Steric
 * hindrance in biaryls is the remaining part and needs a size estimate, so it is not decided here.
 */
object Stereochemistry {
    /** Stand-in descriptor for a hydrogen that is implied rather than drawn. */
    private const val HYDROGEN_SUBSTITUENT = "hydrogen"

    /**
     * Elements whose three-coordinate, lone-pair-bearing form is pyramidal, so three different
     * substituents make it a chirality centre in principle. The group-15 series only: chalcogens
     * are three-coordinate only as cations, and the valence table would hand a neutral
     * three-bonded sulfur an implied hydrogen instead.
     */
    private val PYRAMIDAL_ELEMENTS = setOf("N", "P", "As", "Sb", "Bi")

    /**
     * Elements whose pyramidal centre inverts fast enough at ordinary temperature that the two
     * arrangements are one substance. Nitrogen alone, on a ~6 kcal/mol barrier; phosphorus and the
     * heavier congeners need roughly five times that and stay put.
     */
    private val RAPIDLY_INVERTING_ELEMENTS = setOf("N")

    /**
     * sp³ atoms carrying four different substituents: two arrangements exist here. Symmetry is
     * handled for free, because two substituents are the same exactly when their Morgan labels
     * match — so neither of the two methyls in isopropanol, nor the two ring paths of cyclohexanol,
     * make their carbon a centre.
     */
    fun stereocentres(graph: MoleculeGraph): List<Int> {
        val labels = MoleculeCanonicalizer.morganLabels(graph)
        return graph.atoms.filter { atom ->
            graph.bondsOf(atom.id).all { it.order == BondOrder.SINGLE } &&
                substituents(graph, atom.id, labels, excluding = null).let {
                    it.size == 4 && it.distinct().size == 4
                }
        }.map { it.id }
    }

    /**
     * Double bonds with two different substituents at each end: *cis* and *trans* exist here, and
     * the π bond that stops them interconverting is worth tens of kcal/mol.
     *
     * Ring double bonds are excluded, because the ring already ties the two ends together and E/Z
     * has nothing to say. That also drops trans-cycloalkenes, which from cyclooctene up are real and
     * separable — a known gap, recorded rather than guessed at.
     */
    fun stereogenicDoubleBonds(graph: MoleculeGraph): List<Bond> {
        val labels = MoleculeCanonicalizer.morganLabels(graph)
        return graph.bonds.filter { bond ->
            bond.order == BondOrder.DOUBLE &&
                !liesInRing(graph, bond) &&
                endIsStereogenic(graph, bond.first, bond.second, labels) &&
                endIsStereogenic(graph, bond.second, bond.first, labels)
        }
    }

    /**
     * Three-coordinate pyramidal atoms with three different substituents. Chiral in principle —
     * whether that chirality can be held onto is [rapidlyInvertingCentres]'s business.
     */
    fun pyramidalChiralityCentres(graph: MoleculeGraph): List<Int> {
        val labels = MoleculeCanonicalizer.morganLabels(graph)
        return graph.atoms.filter { atom ->
            atom.element.symbol in PYRAMIDAL_ELEMENTS &&
                graph.bondsOf(atom.id).all { it.order == BondOrder.SINGLE } &&
                substituents(graph, atom.id, labels, excluding = null).let {
                    it.size == 3 && it.distinct().size == 3
                }
        }.map { it.id }
    }

    /**
     * The subset of [pyramidalChiralityCentres] that invert too fast to be separate substances:
     * open-chain amines, on a barrier of about 25 kJ/mol. A reaction that produced one arrangement
     * of these would not have produced a different substance, and the model must not pretend
     * otherwise.
     */
    fun rapidlyInvertingCentres(graph: MoleculeGraph): List<Int> {
        val labels = MoleculeCanonicalizer.morganLabels(graph)
        return graph.atoms.filter { atom ->
            atom.element.symbol in RAPIDLY_INVERTING_ELEMENTS &&
                graph.bondsOf(atom.id).all { it.order == BondOrder.SINGLE } &&
                substituents(graph, atom.id, labels, excluding = null).let {
                    it.size == 3 && it.distinct().size == 3
                }
        }.map { it.id }
    }

    /** Descriptors of everything attached to [atomId]: one per neighbour, one per implied hydrogen. */
    private fun substituents(
        graph: MoleculeGraph,
        atomId: Int,
        labels: Map<Int, String>,
        excluding: Int?,
    ): List<String> {
        val bonds = graph.bondsOf(atomId).filter { bond -> excluding == null || bond.other(atomId) != excluding }
        val atom = graph.atom(atomId)
        val hydrogens = atom.explicitHydrogens + graph.implicitHydrogens(atomId)
        return bonds.map { labels.getValue(it.other(atomId)) } + List(hydrogens) { HYDROGEN_SUBSTITUENT }
    }

    private fun endIsStereogenic(
        graph: MoleculeGraph,
        atomId: Int,
        partnerId: Int,
        labels: Map<Int, String>,
    ): Boolean {
        val descriptors = substituents(graph, atomId, labels, excluding = partnerId)
        return descriptors.size == 2 && descriptors.distinct().size == 2
    }

    /** True when the two endpoints stay connected with [bond] taken out, which is what a ring is. */
    private fun liesInRing(graph: MoleculeGraph, bond: Bond): Boolean {
        val start = bond.first
        val target = bond.second
        val pending = ArrayDeque<Int>()
        val seen = mutableSetOf(start)
        pending.add(start)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            for (neighbor in graph.bondsOf(current)) {
                val isTheBondItself =
                    (neighbor.first == bond.first && neighbor.second == bond.second) ||
                        (neighbor.first == bond.second && neighbor.second == bond.first)
                if (isTheBondItself) continue
                val next = neighbor.other(current)
                if (next == target) return true
                if (seen.add(next)) pending.add(next)
            }
        }
        return false
    }
}
