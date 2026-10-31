package io.github.antonovichkorp.chemmod.core.catalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalystSurfaceModelTest {
    private val siteType = "chemmod:cu110_step_site"
    private val otherSite = "chemmod:cu110_terrace_site"
    private val profile = CatalystSurfaceProfile(
        id = "chemmod:test_copper110_sample",
        materialId = "chemmod:copper",
        formName = "CATALYST_COUPON",
        phaseId = "chemmod:copper_fcc_110",
        specificSurfaceAreaSquareMetersPerGram = 10.0,
        facet = FccSurfaceFacet.FCC_110,
        latticeParameterMeters = 3.6147e-10,
        activeSiteFractions = mapOf(siteType to 0.25),
        siteDisplayNameKeys = mapOf(siteType to "surface_site.chemmod.cu110_step"),
        displayNameKey = "catalyst_surface.chemmod.test_copper110",
        displayDescriptionKey = "catalyst_surface.chemmod.test_copper110.description",
        evidence = "test fixture; arithmetic only",
    )

    @Test
    fun `accessible sites derive from lattice facet active fraction area and physical mass`() {
        val model = CatalystSurfaceModel(listOf(profile))
        val inventory = model.inventory(profile.id, catalystMassMicrograms = 2_000_000L)

        assertEquals(20.0, inventory.exposedAreaSquareMeters, 1e-12)
        assertEquals(4.493239684323945e-6, profile.activeSiteMolesPerSquareMeter(siteType), 1e-17)
        assertEquals(8.98647936864789e-5, inventory.accessibleSiteMoles.getValue(siteType), 1e-15)
    }

    @Test
    fun `one kilogram converts to one thousand grams before surface area scaling`() {
        val physicalCopperProfile = profile.copy(specificSurfaceAreaSquareMetersPerGram = 4.47589058277345e-4)
        val model = CatalystSurfaceModel(listOf(physicalCopperProfile))
        val inventory = model.inventory(profile.id, catalystMassMicrograms = 1_000_000_000L)

        assertEquals(0.447589058277345, inventory.exposedAreaSquareMeters, 1e-12)
        assertEquals(
            inventory.exposedAreaSquareMeters * physicalCopperProfile.activeSiteMolesPerSquareMeter(siteType),
            inventory.accessibleSiteMoles.getValue(siteType),
            1e-15,
        )
    }

    @Test
    fun `adsorbate coverage blocks only its matching active site type`() {
        val model = CatalystSurfaceModel(
            listOf(profile.copy(activeSiteFractions = mapOf(siteType to 0.25, otherSite to 0.05),
                siteDisplayNameKeys = mapOf(
                    siteType to "surface_site.chemmod.cu110_step",
                    otherSite to "surface_site.chemmod.cu110_terrace",
                )))
        )
        val inventory = model.inventory(
            profile.id,
            catalystMassMicrograms = 1_000_000L,
            occupiedFractions = mapOf(siteType to 0.75),
        )

        assertEquals(1.123309921080986e-5, inventory.accessibleSiteMoles.getValue(siteType), 1e-16)
        assertEquals(8.98647936864789e-6, inventory.accessibleSiteMoles.getValue(otherSite), 1e-16)
    }

    @Test
    fun `surface model refuses uncharacterized profiles and impossible coverage`() {
        val model = CatalystSurfaceModel(listOf(profile))
        assertFailsWith<IllegalArgumentException> { model.inventory("chemmod:unknown", 1_000_000L) }
        assertFailsWith<IllegalArgumentException> {
            model.inventory(profile.id, 1_000_000L, mapOf(siteType to 1.01))
        }
        assertFailsWith<IllegalArgumentException> {
            model.inventory(profile.id, 1_000_000L, mapOf("chemmod:other_site" to 0.5))
        }
        assertFailsWith<IllegalArgumentException> {
            CatalystSurfaceProfile(
                id = "chemmod:overlapping_sites",
                materialId = "chemmod:copper",
                formName = "CATALYST_COUPON",
                phaseId = "chemmod:copper_fcc_110",
                specificSurfaceAreaSquareMetersPerGram = 1.0,
                facet = FccSurfaceFacet.FCC_110,
                latticeParameterMeters = 3.6147e-10,
                activeSiteFractions = mapOf(siteType to 0.7, otherSite to 0.4),
                siteDisplayNameKeys = mapOf(
                    siteType to "surface_site.chemmod.cu110_step",
                    otherSite to "surface_site.chemmod.cu110_terrace",
                ),
                displayNameKey = "catalyst_surface.chemmod.test_copper110",
                displayDescriptionKey = "catalyst_surface.chemmod.test_copper110.description",
                evidence = "test fixture",
            )
        }
    }

    @Test
    fun `material-form lookup uses only an exact characterized profile`() {
        val model = CatalystSurfaceModel(listOf(profile))
        assertEquals(
            4.493239684323945e-5,
            model.siteMolesForMaterialForm("chemmod:copper", "CATALYST_COUPON", 1_000_000L).getValue(siteType),
            1e-15,
        )
        assertTrue(model.siteMolesForMaterialForm("chemmod:copper", "INGOT", 1_000_000_000L).isEmpty())
        assertTrue(model.siteMolesForMaterialForm("chemmod:iron", "INGOT", 1_000_000_000L).isEmpty())
    }

    @Test
    fun `bundled prepared copper catalyst profile is available and ordinary ingot remains uncharacterized`() {
        val model = CatalystSurfaceModel.bundled()
        val copperCoupon = model.profileForMaterialForm("chemmod:copper", "CATALYST_COUPON")
        assertNotNull(copperCoupon)
        assertEquals(FccSurfaceFacet.FCC_110, copperCoupon.facet)
        assertEquals(0.07, copperCoupon.activeSiteFractions.getValue("chemmod:cu110_step_site"), 1e-12)
        assertEquals(
            5.631149772978659e-10,
            model.siteMolesForMaterialForm("chemmod:copper", "CATALYST_COUPON", 1_000_000L)
                .getValue("chemmod:cu110_step_site"),
            1e-24,
        )
        assertNull(model.profileForMaterialForm("chemmod:copper", "INGOT"))
        assertTrue(model.siteMolesForMaterialForm("chemmod:copper", "INGOT", 1_000_000_000L).isEmpty())
        assertTrue(model.profile("chemmod:iron_scale") == null)
        assertTrue(model.siteMolesForMaterialForm("minecraft:iron_ingot", "INGOT", 1_000_000_000L).isEmpty())
    }
}
