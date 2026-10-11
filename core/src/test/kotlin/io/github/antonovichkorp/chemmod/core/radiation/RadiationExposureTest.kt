package io.github.antonovichkorp.chemmod.core.radiation

import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RadiationExposureTest {
    private val laboratorySource = RadiationSourceProfile(
        "test.lab_source",
        listOf(
            RadiationEmission(RadiationKind.ALPHA, 30.0),
            RadiationEmission(RadiationKind.GAMMA, 100.0),
        ),
    )
    private val halfValueGammaLayer = ShieldingMaterial(
        id = "test.half_value",
        alphaStoppingThicknessCentimetres = 0.01,
        betaLinearAttenuationPerCentimetre = 0.0,
        gammaLinearAttenuationPerCentimetre = ln(2.0),
        neutronLinearAttenuationPerCentimetre = 0.0,
    )

    @Test
    fun `unshielded radiation follows inverse square dilution`() {
        val oneMetre = RadiationExposure.doseRateMicrosievertPerHour(laboratorySource, 1.0)
        val twoMetres = RadiationExposure.doseRateMicrosievertPerHour(laboratorySource, 2.0)

        assertEquals(130.0, oneMetre, absoluteTolerance = 1.0e-12)
        assertEquals(32.5, twoMetres, absoluteTolerance = 1.0e-12)
    }

    @Test
    fun `alpha is stopped and gamma uses exponential layer attenuation`() {
        val unshielded = RadiationExposure.doseRateMicrosievertPerHour(laboratorySource, 1.0)
        val shielded = RadiationExposure.doseRateMicrosievertPerHour(
            laboratorySource,
            1.0,
            listOf(ShieldingLayer(halfValueGammaLayer, 1.0)),
        )

        assertEquals(130.0, unshielded, absoluteTolerance = 1.0e-12)
        assertEquals(50.0, shielded, absoluteTolerance = 1.0e-12)
    }

    @Test
    fun `dose is integrated in physical time rather than Minecraft ticks`() {
        val dose = RadiationDose()
            .addExposure(doseRateMicrosievertPerHour = 720.0, elapsedSeconds = 30.0)

        assertEquals(6.0, dose.microsievert, absoluteTolerance = 1.0e-12)
    }

    @Test
    fun `minimum distance keeps an in block source finite`() {
        val doseAtCentre = RadiationExposure.doseRateMicrosievertPerHour(laboratorySource, 0.0)

        assertEquals(2_080.0, doseAtCentre, absoluteTolerance = 1.0e-12)
    }

    @Test
    fun `invalid shielding and source input is rejected at the core boundary`() {
        assertFailsWith<IllegalArgumentException> {
            RadiationEmission(RadiationKind.GAMMA, Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            ShieldingLayer(halfValueGammaLayer, -0.01)
        }
        val error = assertFailsWith<IllegalArgumentException> {
            RadiationExposure.doseRateMicrosievertPerHour(laboratorySource, -1.0)
        }
        assertTrue(error.message!!.contains("Distance"))
    }
}
