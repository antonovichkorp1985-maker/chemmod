package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `unknown solubility is omitted while supported ionic result is retained`() {
        val water = ReferenceBookReports.forReference(Molecule.fromSMILESlike("O"))
        val salt = ReferenceBookReports.forReference(Molecule.fromSMILESlike("[Na+].[Cl-]"))

        assertNotNull(water)
        assertNull(water.solubility)
        assertNotNull(salt)
        assertEquals(CatalogSolubility.SOLUBLE, salt.solubility)
    }

    @Test
    fun `book reports do not extend beyond the finite reference catalog`() {
        assertNull(ReferenceBookReports.forReference(Molecule.fromSMILESlike("CC(C)O")))
    }
}
