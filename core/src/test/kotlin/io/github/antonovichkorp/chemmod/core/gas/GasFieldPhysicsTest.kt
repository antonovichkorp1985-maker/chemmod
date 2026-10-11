package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The sparse world gas field of ПЛАН-ДРУГА §5 / ADR-0004 stage B: diffusion by concentration
 * gradient, a vertical buoyancy bias, ventilation, and sinks for filtration.
 */
class GasFieldPhysicsTest {
    private class Props(
        private val molarMass: Map<String, Double>,
        private val diffusion: Map<String, Double?>,
    ) : GasFieldProperties {
        override fun molarMassGramsPerMole(species: String): Double =
            molarMass[species] ?: error("no molar mass for $species")

        override fun diffusionSquareMetersPerSecond(species: String): Double? = diffusion[species]
    }

    private val air = mapOf(
        "N2" to 28.014, "O2" to 31.999, "CO2" to 44.009,
        "H2" to 2.016, "Cl2" to 70.906, "Rn" to 222.018,
    )

    @Test
    fun `a concentration gradient diffuses away and conserves the gas`() {
        val field = GasField(cellSizeMetres = 1.0)
        field.addMoles(GasCell(0, 0, 0), "Cl2", 1.0)
        val properties = Props(air, mapOf("Cl2" to 1.2e-5))

        // Diffusion over a metre is genuinely slow: the characteristic time L^2/D is ~8e4 s, so
        // the step is thousands of seconds and buoyancy is switched off to isolate the gradient.
        repeat(100) { field.advance(5_000.0, properties, buoyantDriftMetresPerSecond = 0.0) }

        val left = field.molesOf(GasCell(0, 0, 0), "Cl2")
        val right = field.molesOf(GasCell(1, 0, 0), "Cl2")
        assertTrue(abs(left - right) < 1e-3, "gradient did not even out: $left vs $right")
        assertEquals(0.5, left, 1e-3, "the gas did not spread evenly between the two cells")
        assertEquals(1.0, field.totalMoles("Cl2"), 1e-6, "diffusion created or destroyed gas")
    }

    @Test
    fun `a closed field conserves every species to within trace pruning`() {
        val field = GasField(cellSizeMetres = 1.0)
        field.addMoles(GasCell(0, 0, 0), "Cl2", 3.0)
        field.addMoles(GasCell(2, 1, 0), "H2", 1.5)
        val properties = Props(air, mapOf("Cl2" to 1.2e-5, "H2" to 7.2e-5))

        repeat(200) { field.advance(0.5, properties) }

        // Diffusion and drift only move moles; the only loss is the trace threshold discarding
        // cells that fall below TRACE_MOLES, which is bounded and tiny.
        assertEquals(3.0, field.totalMoles("Cl2"), 1e-6, "chlorine was not conserved")
        assertEquals(1.5, field.totalMoles("H2"), 1e-6, "hydrogen was not conserved")
    }

    @Test
    fun `gas denser than air settles and lighter gas rises`() {
        val sinking = GasField(cellSizeMetres = 1.0)
        sinking.addMoles(GasCell(0, 0, 0), "CO2", 1.0)
        val rising = GasField(cellSizeMetres = 1.0)
        rising.addMoles(GasCell(0, 0, 0), "H2", 1.0)
        val properties = Props(air, mapOf("CO2" to null, "H2" to null))

        sinking.advance(1.0, properties, buoyantDriftMetresPerSecond = 0.4)
        rising.advance(1.0, properties, buoyantDriftMetresPerSecond = 0.4)

        assertTrue(
            sinking.molesOf(GasCell(0, -1, 0), "CO2") > 0.0,
            "carbon dioxide did not settle into the cell below",
        )
        assertTrue(
            rising.molesOf(GasCell(0, 1, 0), "H2") > 0.0,
            "hydrogen did not rise into the cell above",
        )
    }

    @Test
    fun `ventilation clears an open cell monotonically`() {
        val field = GasField(cellSizeMetres = 1.0)
        field.addMoles(GasCell(0, 0, 0), "Cl2", 1.0)
        val properties = Props(air, mapOf("Cl2" to null))

        var previous = 1.0
        repeat(20) {
            field.advance(1.0, properties, openToAtmosphere = { true }, airChangesPerSecond = 0.5)
            val now = field.totalMoles("Cl2")
            assertTrue(now <= previous + 1e-12, "ventilation was not monotonic: $previous -> $now")
            previous = now
        }
        assertTrue(previous < 1e-3, "ventilation did not clear the volume, $previous left")
    }

    @Test
    fun `a filter removes its species and leaves the rest alone`() {
        val field = GasField(cellSizeMetres = 1.0)
        field.addMoles(GasCell(0, 0, 0), "Cl2", 1.0)
        field.addMoles(GasCell(0, 0, 0), "N2", 1.0)
        val properties = Props(air, mapOf("Cl2" to null, "N2" to null))

        repeat(20) {
            field.advance(
                1.0,
                properties,
                sinkPerSecond = { _, species -> if (species == "Cl2") 0.5 else 0.0 },
            )
        }

        assertTrue(field.totalMoles("Cl2") < 1e-3, "the filter did not scrub chlorine")
        assertEquals(1.0, field.totalMoles("N2"), 1e-9, "the filter took nitrogen it should not touch")
    }

    @Test
    fun `occupancy is announced so a carried radiation source can follow the gas`() {
        val field = GasField(cellSizeMetres = 1.0)
        val entered = mutableListOf<Pair<GasCell, String>>()
        val left = mutableListOf<Pair<GasCell, String>>()
        field.addOccupancyListener(object : GasFieldOccupancyListener {
            override fun speciesEntered(cell: GasCell, species: String) { entered += cell to species }
            override fun speciesLeft(cell: GasCell, species: String) { left += cell to species }
        })
        val properties = Props(air, mapOf("Rn" to null))

        field.addMoles(GasCell(5, 5, 5), "Rn", 1.0)
        assertEquals(listOf(GasCell(5, 5, 5) to "Rn"), entered, "entry into a cell was not announced")

        repeat(40) {
            field.advance(1.0, properties, openToAtmosphere = { true }, airChangesPerSecond = 1.0)
        }
        assertTrue(
            left.any { it.first == GasCell(5, 5, 5) && it.second == "Rn" },
            "leaving a cell was not announced, so a source would be left behind",
        )
    }

    @Test
    fun `an unknown diffusion coefficient does not read as a small one`() {
        val field = GasField(cellSizeMetres = 1.0)
        field.addMoles(GasCell(0, 0, 0), "Xx", 1.0)
        val properties = Props(air + ("Xx" to 28.97), mapOf("Xx" to null))

        repeat(10) { field.advance(1.0, properties) }

        assertEquals(1.0, field.molesOf(GasCell(0, 0, 0), "Xx"), 1e-12, "an uncovered species diffused anyway")
        assertEquals(0.0, field.molesOf(GasCell(1, 0, 0), "Xx"), 1e-12)
    }

    @Test
    fun `work scales with occupied cells and not with the coordinate span`() {
        // Both fields hold the same 200 isolated cells; only the coordinate span differs. If the
        // field were dense in space, the wide one would cost enormously more.
        val tight = GasField(cellSizeMetres = 1.0)
        val wide = GasField(cellSizeMetres = 1.0)
        val properties = Props(air, mapOf("Cl2" to 1.2e-5))

        for (i in 0 until 200) {
            tight.addMoles(GasCell(2 * (i % 10), 2 * ((i / 10) % 10), 2 * (i / 100)), "Cl2", 0.01)
            wide.addMoles(GasCell(i * 977, i * 31, i * 6151), "Cl2", 0.01)
        }

        tight.resetWorkUnits()
        wide.resetWorkUnits()
        tight.advance(1.0, properties)
        wide.advance(1.0, properties)

        assertEquals(
            tight.workUnits,
            wide.workUnits,
            "the field's work depended on where the cells sit, so it is not sparse",
        )
        assertTrue(
            tight.workUnits < 5_000,
            "200 isolated cells cost ${tight.workUnits} visits, far above a linear budget",
        )
    }

    @Test
    fun `a coarse LOD cell aggregates the fine cells inside it`() {
        val field = GasField(cellSizeMetres = 1.0)
        field.addMoles(GasCell(0, 0, 0), "Cl2", 0.25)
        field.addMoles(GasCell(1, 0, 0), "Cl2", 0.25)
        field.addMoles(GasCell(0, 1, 0), "Cl2", 0.25)
        field.addMoles(GasCell(1, 1, 0), "Cl2", 0.25)

        assertEquals(0.25, field.totalMolesAround(GasCell(0, 0, 0), 0, "Cl2"), 1e-12)
        assertEquals(1.0, field.totalMolesAround(GasCell(0, 0, 0), 1, "Cl2"), 1e-9)
        assertEquals(
            GasCell(0, 0, 0),
            field.cellOf(0.5, 0.5, 0.5),
            "a position did not map to its cell",
        )
        assertEquals(
            GasCell(0, 0, 0),
            field.cellOf(1.5, 1.5, 1.5, level = 1),
            "a position did not map to its coarse cell of size 2",
        )
        assertEquals(
            GasCell(3, 0, 1),
            field.cellOf(7.0, 1.0, 2.0, level = 1),
            "a position did not map to its coarse cell of size 2",
        )
    }
}
