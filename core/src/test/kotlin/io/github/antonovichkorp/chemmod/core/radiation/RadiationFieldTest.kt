package io.github.antonovichkorp.chemmod.core.radiation

import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The world radiation zone as a core model. The specification asks for dose rate computed on
 * demand from sources by distance and shielding, with only the accumulated dose stored in a
 * sparse grid - so these tests pin the geometry, the sparsity and the level of detail, and never
 * a voxel volume.
 */
class RadiationFieldTest {

    private val gammaOnly = RadiationSourceProfile(
        id = "test.gamma",
        emissions = listOf(RadiationEmission(RadiationKind.GAMMA, 100.0)),
    )

    private val alphaAndGamma = RadiationSourceProfile(
        id = "test.mixed",
        emissions = listOf(
            RadiationEmission(RadiationKind.ALPHA, 100.0),
            RadiationEmission(RadiationKind.GAMMA, 100.0),
        ),
    )

    /** Stops alpha outright at 5 cm, and attenuates gamma by exp(-0.1 x). */
    private val paper = ShieldingMaterial(
        id = "test.paper",
        alphaStoppingThicknessCentimetres = 5.0,
        betaLinearAttenuationPerCentimetre = 0.5,
        gammaLinearAttenuationPerCentimetre = 0.1,
        neutronLinearAttenuationPerCentimetre = 0.05,
    )

    private fun assertNear(expected: Double, actual: Double, tolerance: Double = 1e-9) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "expected $expected +/- $tolerance but got $actual",
        )
    }

    @Test
    fun `dose rate falls with the square of the distance`() {
        val field = RadiationField()
        field.addSource(FieldSource(FieldPosition(0.0, 0.0, 0.0), gammaOnly))

        assertNear(100.0, field.doseRateMicrosievertPerHourAt(FieldPosition(1.0, 0.0, 0.0)))
        assertNear(25.0, field.doseRateMicrosievertPerHourAt(FieldPosition(2.0, 0.0, 0.0)))
        assertNear(6.25, field.doseRateMicrosievertPerHourAt(FieldPosition(4.0, 0.0, 0.0)))
        // Diagonals use the true distance, not a per-axis sum.
        assertNear(100.0 / 3.0, field.doseRateMicrosievertPerHourAt(FieldPosition(1.0, 1.0, 1.0)))
    }

    @Test
    fun `sampling closer than the source size is clamped, not singular`() {
        val field = RadiationField()
        field.addSource(FieldSource(FieldPosition(0.0, 0.0, 0.0), gammaOnly))
        // The default minimum distance is 0.25 m, so the rate saturates at 100 / 0.25^2.
        assertNear(1_600.0, field.doseRateMicrosievertPerHourAt(FieldPosition(0.0, 0.0, 0.0)))
        assertNear(1_600.0, field.doseRateMicrosievertPerHourAt(FieldPosition(0.1, 0.0, 0.0)))
    }

    @Test
    fun `sources superpose and can be removed again`() {
        val field = RadiationField()
        val source = FieldSource(FieldPosition(0.0, 0.0, 0.0), gammaOnly)
        val target = FieldPosition(2.0, 0.0, 0.0)
        field.addSource(source)
        field.addSource(FieldSource(FieldPosition(0.0, 0.0, 0.0), gammaOnly))
        assertNear(50.0, field.doseRateMicrosievertPerHourAt(target))
        assertTrue(field.removeSource(source))
        assertNear(25.0, field.doseRateMicrosievertPerHourAt(target))
    }

    @Test
    fun `shielding comes from the caller and alpha is stopped where gamma is only weakened`() {
        val field = RadiationField()
        field.addSource(FieldSource(FieldPosition(0.0, 0.0, 0.0), alphaAndGamma))
        val target = FieldPosition(1.0, 0.0, 0.0)
        assertNear(200.0, field.doseRateMicrosievertPerHourAt(target))

        val shielded = field.doseRateMicrosievertPerHourAt(target) {
            listOf(ShieldingLayer(paper, 5.0))
        }
        // Alpha: 5 cm of paper reaches the stopping thickness, so nothing gets through.
        // Gamma: exp(-0.1 * 5) of 100.
        assertNear(100.0 * exp(-0.5), shielded, 1e-9)
    }

    @Test
    fun `nothing is stored where no dose was ever recorded`() {
        val field = RadiationField(cellSizeMetres = 4.0)
        assertEquals(emptySet(), field.contaminatedCells())
        assertEquals(0.0, field.accumulatedDoseMicrosievert(FieldCell(0, 0, 0)))
        assertEquals(0.0, field.accumulatedDoseMicrosievert(FieldCell(-7, 3, 12)))
    }

    @Test
    fun `accumulated dose is the rate times the time and lands in one cell`() {
        val field = RadiationField(cellSizeMetres = 4.0)
        val dose = field.recordExposure(FieldPosition(1.0, 1.0, 1.0), 3_600.0, 3_600.0)
        assertNear(3_600.0, dose)
        assertEquals(setOf(FieldCell(0, 0, 0)), field.contaminatedCells())
        assertNear(3_600.0, field.accumulatedDoseMicrosievert(FieldCell(0, 0, 0)))

        field.recordExposure(FieldPosition(2.0, 2.0, 2.0), 1_800.0, 3_600.0)
        // Same cell, so the dose adds up rather than spreading.
        assertNear(5_400.0, field.accumulatedDoseMicrosievert(FieldCell(0, 0, 0)))
        assertEquals(1, field.contaminatedCells().size)

        field.clearAccumulated()
        assertEquals(emptySet(), field.contaminatedCells())
    }

    @Test
    fun `positions on both sides of the origin quantise into the right cells`() {
        val field = RadiationField(cellSizeMetres = 4.0)
        assertEquals(FieldCell(0, 0, -1), field.cellOf(FieldPosition(0.0, 3.9, -0.1)))
        assertEquals(FieldCell(1, 0, 0), field.cellOf(FieldPosition(4.0, 0.0, 0.0)))
        assertEquals(FieldCell(-1, -1, -2), field.cellOf(FieldPosition(-0.1, -4.0, -8.0)))
    }

    @Test
    fun `level of detail aggregates the finer cells instead of keeping a second copy`() {
        val field = RadiationField(cellSizeMetres = 4.0)
        // Two positions in neighbouring level-0 cells that share one level-1 cell of 8 m.
        field.recordExposure(FieldPosition(1.0, 0.0, 0.0), 3_600.0, 3_600.0)
        field.recordExposure(FieldPosition(5.0, 0.0, 0.0), 3_600.0, 3_600.0)

        assertEquals(FieldCell(0, 0, 0), field.cellOf(FieldPosition(1.0, 0.0, 0.0)))
        assertEquals(FieldCell(1, 0, 0), field.cellOf(FieldPosition(5.0, 0.0, 0.0)))
        assertEquals(FieldCell(0, 0, 0), field.cellOf(FieldPosition(5.0, 0.0, 0.0), level = 1))

        assertNear(3_600.0, field.accumulatedDoseMicrosievertAround(FieldPosition(1.0, 0.0, 0.0)))
        assertNear(
            7_200.0,
            field.accumulatedDoseMicrosievertAround(FieldPosition(1.0, 0.0, 0.0), level = 1),
        )
        // Storage stayed at the fine resolution: only two cells exist, not a coarse duplicate.
        assertEquals(2, field.contaminatedCells().size)
    }

    @Test
    fun `a cell can be queried at its centre, which is what a sensor block reads`() {
        val field = RadiationField(cellSizeMetres = 4.0)
        field.addSource(FieldSource(FieldPosition(2.0, 2.0, 2.0), gammaOnly))
        // Cell (1, 0, 0) has its centre at (6, 2, 2), four metres from the source.
        assertNear(6.25, field.doseRateMicrosievertPerHourInCell(FieldCell(1, 0, 0)))
    }

    @Test
    fun `a field and a grid reject impossible arguments`() {
        assertFailsWith<IllegalArgumentException> { RadiationField(cellSizeMetres = 0.0) }
        val field = RadiationField()
        assertFailsWith<IllegalArgumentException> {
            field.cellOf(FieldPosition(0.0, 0.0, 0.0), level = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            field.recordExposure(FieldPosition(0.0, 0.0, 0.0), -1.0, 1.0)
        }
        assertFailsWith<IllegalArgumentException> { FieldPosition(Double.NaN, 0.0, 0.0) }
    }
}
