package io.github.antonovichkorp.chemmod.core.radiation

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * A position in metres. The core deliberately has no Minecraft coordinate type: a field is
 * simulated identically in a unit test and on a dedicated server, and the world adapter converts
 * block coordinates on the way in.
 */
data class FieldPosition(val x: Double, val y: Double, val z: Double) {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Position must be finite" }
    }

    fun distanceTo(other: FieldPosition): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/** One emitting body: where it is, and the calibrated profile the data gives it. */
data class FieldSource(val position: FieldPosition, val profile: RadiationSourceProfile)

/** Integer cell of the accumulation grid. */
data class FieldCell(val x: Int, val y: Int, val z: Int)

/**
 * The radiation zone of a world, as a core model.
 *
 * The design follows the specification rather than the obvious voxel field: instantaneous dose
 * rate is computed **on demand** from the sources by distance and shielding, and only the
 * *accumulated* dose is stored, in a sparse grid with a configurable cell size. Nothing is
 * allocated where no dose has been recorded, so an untouched chunk costs nothing, and a region
 * that is never visited never enters the grid.
 *
 * Two things are deliberately not here. The conversion from decay activity to an ambient dose
 * equivalent stays in data ([RadiationSourceProfile] is calibrated per metre), because it depends
 * on isotope, spectrum and balancing policy. And no source knows anything about a block: the
 * adapter supplies [FieldPosition] and, when asked, the shielding layers between source and
 * target.
 */
class RadiationField(val cellSizeMetres: Double = DEFAULT_CELL_SIZE_METRES) {

    init {
        require(cellSizeMetres > 0.0 && cellSizeMetres.isFinite()) {
            "Cell size must be finite and positive"
        }
    }

    private val sources = mutableListOf<FieldSource>()
    private val accumulated = linkedMapOf<FieldCell, Double>()

    fun addSource(source: FieldSource) {
        sources += source
    }

    fun removeSource(source: FieldSource): Boolean = sources.remove(source)

    fun sources(): List<FieldSource> = sources.toList()

    /** The cell of level [level] containing [position]; level 0 is the storage resolution. */
    fun cellOf(position: FieldPosition, level: Int = 0): FieldCell {
        require(level >= 0) { "LOD level must not be negative" }
        val size = cellSize(level)
        // floor() and not Math.floorDiv: the latter has an (Int, Int) overload that Kotlin picks
        // over the (Double, Double) one, and floor has the same semantics for negative
        // coordinates - -0.1 in a 4 m cell belongs to cell -1, not cell 0.
        return FieldCell(
            floor(position.x / size).toInt(),
            floor(position.y / size).toInt(),
            floor(position.z / size).toInt(),
        )
    }

    /**
     * Instantaneous dose rate at [position], summed over every source. Shielding is supplied by
     * the caller because only the world knows what stands between two points.
     */
    fun doseRateMicrosievertPerHourAt(
        position: FieldPosition,
        shieldingFor: (FieldSource) -> Iterable<ShieldingLayer> = { emptyList() },
    ): Double = sources.sumOf { source ->
        RadiationExposure.doseRateMicrosievertPerHour(
            profile = source.profile,
            distanceMetres = position.distanceTo(source.position),
            layers = shieldingFor(source),
        )
    }

    /** Dose rate at the centre of a cell - what a sensor block or a coarse LOD query would read. */
    fun doseRateMicrosievertPerHourInCell(
        cell: FieldCell,
        level: Int = 0,
        shieldingFor: (FieldSource) -> Iterable<ShieldingLayer> = { emptyList() },
    ): Double {
        require(level >= 0) { "LOD level must not be negative" }
        val size = cellSize(level)
        return doseRateMicrosievertPerHourAt(
            FieldPosition(
                (cell.x + 0.5) * size,
                (cell.y + 0.5) * size,
                (cell.z + 0.5) * size,
            ),
            shieldingFor,
        )
    }

    /** Record exposure at one position for [seconds]; the dose lands in that position's cell. */
    fun recordExposure(
        position: FieldPosition,
        doseRateMicrosievertPerHour: Double,
        seconds: Double,
    ): Double {
        val dose = RadiationExposure.accumulatedDoseMicrosievert(
            doseRateMicrosievertPerHour,
            seconds,
        )
        val cell = cellOf(position)
        accumulated[cell] = (accumulated[cell] ?: 0.0) + dose
        return dose
    }

    /** Accumulated dose in the level-0 [cell]; zero where nothing was ever recorded. */
    fun accumulatedDoseMicrosievert(cell: FieldCell): Double = accumulated[cell] ?: 0.0

    /**
     * Accumulated dose inside the level-[level] cell containing [position], aggregated over the
     * finer cells it covers. Level of detail is therefore a query property, not a second copy of
     * the state: the grid is always stored at the finest resolution.
     */
    fun accumulatedDoseMicrosievertAround(position: FieldPosition, level: Int = 0): Double {
        require(level >= 0) { "LOD level must not be negative" }
        if (level == 0) return accumulatedDoseMicrosievert(cellOf(position))
        val coarse = cellOf(position, level)
        val span = 1 shl level
        var total = 0.0
        for (dx in 0 until span) {
            for (dy in 0 until span) {
                for (dz in 0 until span) {
                    total += accumulated[
                        FieldCell(
                            coarse.x * span + dx,
                            coarse.y * span + dy,
                            coarse.z * span + dz,
                        ),
                    ] ?: 0.0
                }
            }
        }
        return total
    }

    /** Cells that actually hold dose. Sparse by construction: untouched space is absent. */
    fun contaminatedCells(): Set<FieldCell> = accumulated.keys.toSet()

    fun clearAccumulated() = accumulated.clear()

    private fun cellSize(level: Int): Double = cellSizeMetres * (1 shl level)

    companion object {
        const val DEFAULT_CELL_SIZE_METRES = 4.0
    }
}
