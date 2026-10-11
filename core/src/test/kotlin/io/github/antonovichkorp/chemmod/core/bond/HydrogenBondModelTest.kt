package io.github.antonovichkorp.chemmod.core.bond

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Hydrogen bonding is derived from lone pairs and polar hydrogens, so no substance is listed.
 */
class HydrogenBondModelTest {
    private val model = HydrogenBondModel.bundled()

    private fun graph(structure: String) = Molecule.fromSMILESlike(structure).graph

    @Test
    fun `water donates and accepts two sites each`() {
        assertEquals(2, model.donorSites(graph("O")))
        assertEquals(2, model.acceptorSites(graph("O")))
        assertEquals(42.0, model.networkKilojoulesPerMole(graph("O")))
    }

    @Test
    fun `an alcohol donates once and accepts twice`() {
        assertEquals(1, model.donorSites(graph("CCO")))
        assertEquals(2, model.acceptorSites(graph("CCO")))
    }

    @Test
    fun `ammonia donates three hydrogens and keeps one pair`() {
        assertEquals(3, model.donorSites(graph("N")))
        assertEquals(1, model.acceptorSites(graph("N")))
    }

    @Test
    fun `a carbonyl accepts but cannot donate, so its network is zero`() {
        assertEquals(0, model.donorSites(graph("CC(C)=O")))
        assertEquals(2, model.acceptorSites(graph("CC(C)=O")))
        assertEquals(0.0, model.networkKilojoulesPerMole(graph("CC(C)=O")))
    }

    @Test
    fun `an ether accepts without donating`() {
        assertEquals(0, model.donorSites(graph("CCOCC")))
        assertEquals(2, model.acceptorSites(graph("CCOCC")))
    }

    @Test
    fun `a hydrocarbon has no hydrogen-bond sites at all`() {
        assertEquals(0, model.donorSites(graph("CC")))
        assertEquals(0, model.acceptorSites(graph("CC")))
        assertEquals(0.0, model.networkKilojoulesPerMole(graph("CC")))
    }

    @Test
    fun `sites are reported per atom so a caller can localise them`() {
        val sites = model.sites(graph("CCO"))
        assertTrue(sites.any { it.role == HydrogenBondRole.DONOR })
        assertTrue(sites.any { it.role == HydrogenBondRole.ACCEPTOR })
        assertTrue(sites.all { it.atomId >= 0 })
    }

    @Test
    fun `an ammonium cation has donated its lone pair`() {
        val ammonium = graph("[NH4+]")
        assertEquals(0, model.acceptorSites(ammonium))
        assertEquals(4, model.donorSites(ammonium))
        assertFalse(model.sites(ammonium).any { it.role == HydrogenBondRole.ACCEPTOR })
    }
}
