package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.model.MoleculeGraph
import io.github.antonovichkorp.chemmod.core.properties.PropertyRuleSet

enum class HydrogenBondRole { DONOR, ACCEPTOR }

/**
 * One hydrogen-bond site: a hydrogen carried by an electronegative atom (donor) or a lone pair
 * on one (acceptor). Sites are read from the graph, never listed per substance.
 */
data class HydrogenBondSite(val atomId: Int, val role: HydrogenBondRole)

/**
 * Hydrogen bonding as a non-covalent layer over the covalent graph. The covalent model itself
 * stays untouched: a hydrogen bond is an interaction between two sites, not a bond in the graph,
 * so it cannot break canonical identity or atom conservation.
 */
class HydrogenBondModel(private val rules: InteractionRuleSet) {

    fun sites(graph: MoleculeGraph): List<HydrogenBondSite> = buildList {
        graph.atoms.forEach { atom ->
            val symbol = atom.element.symbol
            if (symbol in rules.hydrogenBondDonorElements) {
                repeat(ElectronBookkeeping.hydrogens(graph, atom.id)) {
                    add(HydrogenBondSite(atom.id, HydrogenBondRole.DONOR))
                }
            }
            if (symbol in rules.hydrogenBondAcceptorElements) {
                repeat(ElectronBookkeeping.lonePairs(graph, atom.id)) {
                    add(HydrogenBondSite(atom.id, HydrogenBondRole.ACCEPTOR))
                }
            }
        }
    }

    fun donorSites(graph: MoleculeGraph): Int =
        sites(graph).count { it.role == HydrogenBondRole.DONOR }

    fun acceptorSites(graph: MoleculeGraph): Int =
        sites(graph).count { it.role == HydrogenBondRole.ACCEPTOR }

    /**
     * Energy of the hydrogen-bond network a pure liquid of this substance can close on itself.
     * A donor needs a partner's acceptor, so the network is limited by whichever role is scarcer:
     * water and alcohols score, acetone and ethers accept but cannot donate and score zero.
     */
    fun networkKilojoulesPerMole(graph: MoleculeGraph): Double =
        minOf(donorSites(graph), acceptorSites(graph)) * rules.hydrogenBondKilojoulesPerMole

    companion object {
        @JvmStatic
        fun bundled(): HydrogenBondModel = HydrogenBondModel(PropertyRuleSet.bundled().interactions)
    }
}
