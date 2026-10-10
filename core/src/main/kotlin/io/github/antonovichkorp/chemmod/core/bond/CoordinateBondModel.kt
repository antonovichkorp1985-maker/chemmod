package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
import io.github.antonovichkorp.chemmod.core.properties.PropertyRuleSet

/** One donor→acceptor coordinate (dative) bond: both electrons come from the donor. */
data class CoordinatePair(val donorAtomId: Int, val acceptorAtomId: Int)

/**
 * Coordinate bonding as a predicted interaction rather than a stored bond kind. The donor is any
 * atom with a lone pair; the acceptor is any atom with a real vacancy, meaning an incomplete octet
 * (or duet for hydrogen). That single criterion covers boron and aluminium halides, carbocations
 * and a bare proton without naming any of them.
 *
 * The covalent graph keeps only covalent bonds, so an adduct's identity is still the identity of
 * its parts until an explicit adduct rule is written.
 */
class CoordinateBondModel(private val rules: InteractionRuleSet) {

    fun donorAtoms(graph: MoleculeGraph): List<Int> =
        graph.atoms.filter { ElectronBookkeeping.lonePairs(graph, it.id) >= 1 }.map { it.id }

    fun acceptorAtoms(graph: MoleculeGraph): List<Int> =
        graph.atoms.filter { ElectronBookkeeping.hasVacantOrbital(graph, it.id) }.map { it.id }

    /**
     * Adduct bonds between two separate molecules. Each vacancy takes one lone pair, so the
     * number of bonds is limited by whichever side runs out first.
     */
    fun pairs(donorGraph: MoleculeGraph, acceptorGraph: MoleculeGraph): List<CoordinatePair> =
        donorAtoms(donorGraph).zip(acceptorAtoms(acceptorGraph)) { donor, acceptor ->
            CoordinatePair(donor, acceptor)
        }

    fun estimatedKilojoulesPerMole(pairs: List<CoordinatePair>): Double =
        pairs.size * rules.coordinateBondKilojoulesPerMole

    companion object {
        @JvmStatic
        fun bundled(): CoordinateBondModel = CoordinateBondModel(PropertyRuleSet.bundled().interactions)
    }
}
