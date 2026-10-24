package io.github.antonovichkorp.chemmod.core.catalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalystSurfaceModelTest {
    private val siteType = "chemmod:fe3_defect_site"
    private val profile = CatalystSurfaceProfile(
        id = "chemmod:test_hematite_sample",
        materialId = "chemmod:hematite",
        formName = "DUST",
        phaseId = "chemmod:alpha_hematite_0001_defective",
        specificSurfaceAreaSquareMetersPerGram = 10.0,
        activeSiteMolesPerSquareMeter = mapOf(siteType to 2.0e-6),
        evidence = "test fixture; arithmetic only",
    )

    @Test
    fun `accessible sites scale with characterized area and catalyst mass`() {
        val model = CatalystSurfaceModel(listOf(profile))
        val inventory = model.inventory(profile.id, catalystMassMicrograms = 2_000_000_000L)

        assertEquals(20.0, inventory.exposedAreaSquareMeters, 1e-12)
        assertEquals(4.0e-5, inventory.accessibleSiteMoles.getValue(siteType), 1e-15)
    }

    @Test
    fun `adsorbate coverage blocks only its matching active site type`() {
        val otherSite = "chemmod:oxygen_vacancy_site"
        val model = CatalystSurfaceModel(
            listOf(profile.copy(activeSiteMolesPerSquareMeter = mapOf(
                siteType to 2.0e-6,
                otherSite to 5.0e-7,
            )))
        )
        val inventory = model.inventory(
            profile.id,
            catalystMassMicrograms = 1_000_000_000L,
            occupiedFractions = mapOf(siteType to 0.75),
        )

        assertEquals(5.0e-6, inventory.accessibleSiteMoles.getValue(siteType), 1e-15)
        assertEquals(5.0e-6, inventory.accessibleSiteMoles.getValue(otherSite), 1e-15)
    }

    @Test
    fun `surface model refuses uncharacterized profiles and impossible coverage`() {
        val model = CatalystSurfaceModel(listOf(profile))
        assertFailsWith<IllegalArgumentException> { model.inventory("chemmod:unknown", 1_000_000_000L) }
        assertFailsWith<IllegalArgumentException> {
            model.inventory(profile.id, 1_000_000_000L, mapOf(siteType to 1.01))
        }
        assertFailsWith<IllegalArgumentException> {
            model.inventory(profile.id, 1_000_000_000L, mapOf("chemmod:other_site" to 0.5))
        }
    }

    @Test
    fun `material-form lookup uses only an exact characterized profile`() {
        val model = CatalystSurfaceModel(listOf(profile))
        assertEquals(
            2.0e-5,
            model.siteMolesForMaterialForm("chemmod:hematite", "DUST", 1_000_000_000L).getValue(siteType),
            1e-15,
        )
        assertTrue(model.siteMolesForMaterialForm("chemmod:hematite", "INGOT", 1_000_000_000L).isEmpty())
        assertTrue(model.siteMolesForMaterialForm("chemmod:iron", "INGOT", 1_000_000_000L).isEmpty())
    }

    @Test
    fun `empty bundled profiles do not invent activity from bulk composition`() {
        val model = CatalystSurfaceModel.bundled()
        assertTrue(model.profile("chemmod:iron_scale") == null)
        assertTrue(model.siteMolesForMaterialForm("minecraft:iron_ingot", "INGOT", 1_000_000_000L).isEmpty())
    }
}
