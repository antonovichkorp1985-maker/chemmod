package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReferenceBookReportTest {
    @Test
    fun `catalog sample report uses graph-derived ethanol properties`() {
        val report = ReferenceBookReports.forReference(Molecule.fromSMILESlike("CCO"))

        assertNotNull(report)
        assertEquals("C2H6O", report.formula)
        assertEquals(46.069, report.molarMassGramsPerMole, 0.01)
        assertNotNull(report.boilingPointCelsius)
        assertTrue(report.boilingPointBasis.isNotBlank())
        assertNotNull(report.combustionEnthalpyKilojoulesPerMole)
        assertTrue(report.propertyModel.isNotBlank())
    }

    @Test
    fun `unknown solubility is omitted and unrelated solubility fixtures stay out of the book`() {
        val water = ReferenceBookReports.forReference(Molecule.fromSMILESlike("O"))
        val salt = Molecule.fromSMILESlike("[Na+].[Cl-]")

        assertNotNull(water)
        assertNull(water.solubility)
        assertEquals(CatalogSolubility.SOLUBLE, ReferenceMoleculeCatalog.describe(salt.graph).solubility)
        assertNull(ReferenceBookReports.forReference(salt))
    }

    @Test
    fun `book reports cover rule participants and exclude unrelated structures`() {
        val reactionSpecies = ReactionSpeciesCatalog.all().map(Molecule::canonicalKey).toSet()
        val expectedParticipants = listOf("CCO", "O=O", "O=C=O", "O")
            .map { Molecule.fromSMILESlike(it).canonicalKey() }
        assertTrue(expectedParticipants.all { it in reactionSpecies })

        val unrelated = Molecule.fromSMILESlike("C1CCCCCCCCCCC1")
        assertNull(ReferenceBookReports.forReference(unrelated))
        val testOnlySalt = Molecule.fromSMILESlike("[Na+].[Cl-]")
        assertFalse(testOnlySalt.canonicalKey() in reactionSpecies,
            "a named solubility fixture with no bundled reaction must not become a creative sample")
    }
}
