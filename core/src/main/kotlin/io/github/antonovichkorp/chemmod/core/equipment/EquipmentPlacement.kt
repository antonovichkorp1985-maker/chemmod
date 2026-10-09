package io.github.antonovichkorp.chemmod.core.equipment

/**
 * Exact, adapter-defined spatial units. All geometry in one layout must share
 * the same scale and origin. Coordinates are not limited to one Minecraft cell.
 * No subtraction/area multiplication is needed, so extreme Long coordinates
 * cannot wrap during collision or coverage checks.
 */
data class Footprint(val minX: Long, val minZ: Long, val maxX: Long, val maxZ: Long) {
    init { require(minX < maxX && minZ < maxZ) { "Footprint must have positive area" } }

    fun contains(other: Footprint): Boolean = minX <= other.minX && minZ <= other.minZ &&
        maxX >= other.maxX && maxZ >= other.maxZ
}

data class OccupiedBox(val footprint: Footprint, val minY: Long, val maxY: Long) {
    init { require(minY < maxY) { "Occupied box must have positive height" } }

    /** Touching faces/edges are permitted; only positive-volume overlap collides. */
    fun intersects(other: OccupiedBox): Boolean =
        minY < other.maxY && maxY > other.minY &&
            footprint.minX < other.footprint.maxX && footprint.maxX > other.footprint.minX &&
            footprint.minZ < other.footprint.maxZ && footprint.maxZ > other.footprint.minZ
}

/** Required contact patch, or a supplied supporting surface, at an exact height. */
data class HorizontalContact(val footprint: Footprint, val y: Long)

/** Collision geometry and required support are deliberately separate from a render mesh. */
class EquipmentGeometry(boxes: List<OccupiedBox>, contacts: List<HorizontalContact>) {
    private val occupied = boxes.toList()
    private val required = contacts.toList()
    val boxes: List<OccupiedBox> get() = occupied.toList()
    val contacts: List<HorizontalContact> get() = required.toList()

    init {
        require(occupied.isNotEmpty()) { "Equipment needs collision geometry" }
        require(required.isNotEmpty()) { "This placement policy requires explicit support" }
        require(required.all { contact ->
            occupied.any { it.minY == contact.y && it.footprint.contains(contact.footprint) }
        }) { "Each contact patch must belong to a bottom face of the equipment" }
    }
}

data class PlacedEquipment(val id: String, val geometry: EquipmentGeometry) {
    init { require(id.isNotBlank()) { "Equipment identity must not be blank" } }
}

enum class PlacementRefusal { DUPLICATE_ID, UNKNOWN_ID, COLLISION, UNSUPPORTED }

sealed interface PlacementResult {
    data class Accepted(val layout: EquipmentLayout) : PlacementResult
    /** The original snapshot is returned unchanged: callers cannot partially apply a refusal. */
    data class Refused(val reason: PlacementRefusal, val layout: EquipmentLayout) : PlacementResult
}

/**
 * Immutable geometry-only layout. This does not own inventories or world blocks.
 * A server adapter must supply a consistent obstacle/support snapshot, validate
 * authority and commit world/content changes atomically; it must not trust client
 * geometry. Support removal/load/stability, picking and persistence are separate
 * concerns, not simulated by this conservative full-contact policy.
 */
class EquipmentLayout private constructor(private val objects: Map<String, PlacedEquipment>) {
    constructor() : this(emptyMap())

    val equipment: List<PlacedEquipment> get() = objects.values.toList()

    fun place(
        candidate: PlacedEquipment,
        supports: List<HorizontalContact>,
        obstacles: List<OccupiedBox> = emptyList(),
    ): PlacementResult {
        if (candidate.id in objects) return PlacementResult.Refused(PlacementRefusal.DUPLICATE_ID, this)
        return validateAndReplace(candidate, supports, obstacles)
    }

    /** Replace only spatial geometry, retaining the object's identity, never another object's ID. */
    fun move(
        id: String,
        geometry: EquipmentGeometry,
        supports: List<HorizontalContact>,
        obstacles: List<OccupiedBox> = emptyList(),
    ): PlacementResult {
        if (id !in objects) return PlacementResult.Refused(PlacementRefusal.UNKNOWN_ID, this)
        return validateAndReplace(PlacedEquipment(id, geometry), supports, obstacles)
    }

    private fun validateAndReplace(
        candidate: PlacedEquipment,
        supports: List<HorizontalContact>,
        obstacles: List<OccupiedBox>,
    ): PlacementResult {
        val occupied = obstacles + objects.values.filter { it.id != candidate.id }.flatMap { it.geometry.boxes }
        if (candidate.geometry.boxes.any { box -> occupied.any(box::intersects) }) {
            return PlacementResult.Refused(PlacementRefusal.COLLISION, this)
        }
        if (candidate.geometry.contacts.any { !fullySupported(it, supports) }) {
            return PlacementResult.Refused(PlacementRefusal.UNSUPPORTED, this)
        }
        return PlacementResult.Accepted(EquipmentLayout(objects + (candidate.id to candidate)))
    }
}

/** Exact rectangle-union coverage, not summed area (which double-counts overlaps). */
private fun fullySupported(contact: HorizontalContact, surfaces: List<HorizontalContact>): Boolean {
    val target = contact.footprint
    val clipped = surfaces.filter { it.y == contact.y }.mapNotNull {
        val x0 = maxOf(target.minX, it.footprint.minX)
        val x1 = minOf(target.maxX, it.footprint.maxX)
        val z0 = maxOf(target.minZ, it.footprint.minZ)
        val z1 = minOf(target.maxZ, it.footprint.maxZ)
        if (x0 < x1 && z0 < z1) Footprint(x0, z0, x1, z1) else null
    }
    val cuts = (listOf(target.minX, target.maxX) + clipped.flatMap { listOf(it.minX, it.maxX) })
        .distinct().sorted()
    for ((left, right) in cuts.zipWithNext()) {
        val strips = clipped.filter { it.minX <= left && it.maxX >= right }.sortedBy { it.minZ }
        var reached = target.minZ
        for (strip in strips) {
            if (strip.minZ > reached) return false
            reached = maxOf(reached, strip.maxZ)
            if (reached == target.maxZ) break
        }
        if (reached != target.maxZ) return false
    }
    return true
}
