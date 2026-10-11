package io.github.antonovichkorp.chemmod.core.geometry

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.properties.PropertyEngine
import io.github.antonovichkorp.chemmod.core.properties.PropertyRuleSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Shape is derived, not declared: nobody writes that water is bent. It follows from oxygen
 * carrying two bonds and two lone pairs, which the electron bookkeeping already counts.
 */
class MolecularGeometryTest {
    private val engine = PropertyEngine(PropertyRuleSet.bundled())

    private fun graph(structure: String) = Molecule.fromSMILESlike(structure).graph

    private fun shape(structure: String) = MolecularGeometry.molecularShape(graph(structure))

    private fun angle(structure: String) = MolecularGeometry.bondAngleDegrees(graph(structure))

    @Test
    fun `water is bent at 104_5 degrees`() {
        assertEquals("bent", shape("O"))
        assertEquals(104.5, angle("O"))
        assertTrue("BENT_GEOMETRY" in engine.predict(graph("O")).flags)
    }

    @Test
    fun `ammonia is a trigonal pyramid, not a plane`() {
        assertEquals("trigonal-pyramidal", shape("N"))
        assertEquals(107.0, angle("N"))
    }

    @Test
    fun `methane is tetrahedral`() {
        assertEquals("tetrahedral", shape("C"))
        assertEquals(109.5, angle("C"))
    }

    @Test
    fun `carbon dioxide is linear`() {
        assertEquals("linear", shape("O=C=O"))
        assertEquals(180.0, angle("O=C=O"))
    }

    @Test
    fun `boron trifluoride is trigonal planar because boron keeps no lone pair`() {
        assertEquals("trigonal-planar", shape("FB(F)F"))
        assertEquals(120.0, angle("FB(F)F"))
    }

    @Test
    fun `a carbonyl carbon is planar while its neighbour stays tetrahedral`() {
        val acetone = graph("CC(C)=O")
        assertEquals("trigonal-planar", MolecularGeometry.forAtom(acetone, 1).shape)
        assertEquals("tetrahedral", MolecularGeometry.forAtom(acetone, 0).shape)
        assertEquals(120.0, MolecularGeometry.forAtom(acetone, 1).idealBondAngleDegrees)
    }

    @Test
    fun `hypervalent sulfur is octahedral`() {
        assertEquals("octahedral", shape("FS(F)(F)(F)(F)F"))
        assertEquals(90.0, angle("FS(F)(F)(F)(F)F"))
    }

    @Test
    fun `an alcohol reports its oxygen centre and keeps tetrahedral carbons`() {
        val ethanol = graph("CCO")
        assertEquals("bent", MolecularGeometry.molecularShape(ethanol))
        assertEquals(104.5, MolecularGeometry.bondAngleDegrees(ethanol))
        assertEquals(
            listOf("tetrahedral", "tetrahedral"),
            MolecularGeometry.all(ethanol).take(2).map { it.shape },
        )
    }

    @Test
    fun `a diatomic has no angle to report`() {
        assertNull(shape("O=O"))
        assertNull(engine.predict(graph("O=O")).bondAngleDegrees)
    }

    @Test
    fun `the glycine zwitterion is a real graph, not only the neutral form`() {
        val zwitterion = Molecule.fromSMILESlike("[NH3+]CC(=O)[O-]")
        assertEquals(emptyList(), zwitterion.validate().map { it.message })
        assertEquals("C2H5NO2", zwitterion.formula())
        val charges = zwitterion.graph.atoms.map { it.formalCharge }
        assertEquals(1, charges.count { it == 1 })
        assertEquals(1, charges.count { it == -1 })
        // Ammonium nitrogen is tetrahedral, the alpha carbon too, the carboxyl carbon planar.
        assertEquals("tetrahedral", MolecularGeometry.forAtom(zwitterion.graph, 0).shape)
        assertEquals("tetrahedral", MolecularGeometry.forAtom(zwitterion.graph, 1).shape)
        assertEquals("trigonal-planar", MolecularGeometry.forAtom(zwitterion.graph, 2).shape)
        // The deprotonated oxygen keeps three lone pairs and stays a terminal atom.
        assertEquals(3, MolecularGeometry.forAtom(zwitterion.graph, 4).lonePairs)
    }
}
