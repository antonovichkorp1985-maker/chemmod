package io.github.antonovichkorp.chemmod.core.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class EquipmentPlacementTest {
    private fun rect(x0: Long, z0: Long, x1: Long, z1: Long) = Footprint(x0, z0, x1, z1)
    private val top = listOf(HorizontalContact(rect(-32, -32, 64, 64), 16))
    private fun geometry(x0: Long = 0, x1: Long = 4, y: Long = 16): EquipmentGeometry {
        val footprint = rect(x0, 0, x1, 4)
        return EquipmentGeometry(listOf(OccupiedBox(footprint, y, y + 8)), listOf(HorizontalContact(footprint, y)))
    }
    private fun accepted(result: PlacementResult) = assertIs<PlacementResult.Accepted>(result).layout
    private fun refused(result: PlacementResult, reason: PlacementRefusal, original: EquipmentLayout) {
        val refusal = assertIs<PlacementResult.Refused>(result)
        assertEquals(reason, refusal.reason)
        assertSame(original, refusal.layout)
    }

    @Test fun severalIndependentObjectsFitOneCellAndMayTouch() {
        val empty = EquipmentLayout()
        val one = accepted(empty.place(PlacedEquipment("flask", geometry()), top))
        val two = accepted(one.place(PlacedEquipment("cylinder", geometry(4, 8)), top))
        assertEquals(listOf("flask", "cylinder"), two.equipment.map { it.id })
        assertEquals(0, empty.equipment.size)
        assertEquals(1, one.equipment.size)
    }

    @Test fun overlapAndDuplicateIdentityAreRefusedWithoutMutation() {
        val one = accepted(EquipmentLayout().place(PlacedEquipment("flask", geometry()), top))
        refused(one.place(PlacedEquipment("other", geometry(3, 7)), top), PlacementRefusal.COLLISION, one)
        refused(one.place(PlacedEquipment("flask", geometry(8, 12)), top), PlacementRefusal.DUPLICATE_ID, one)
    }

    @Test fun separateSurfacePiecesCanJointlySupportOneVessel() {
        val pieces = listOf(HorizontalContact(rect(0, 0, 2, 4), 16), HorizontalContact(rect(2, 0, 4, 4), 16))
        accepted(EquipmentLayout().place(PlacedEquipment("flask", geometry()), pieces))
        accepted(EquipmentLayout().place(PlacedEquipment("flask", geometry()), pieces.reversed()))
    }

    @Test fun overlappingSupportAreaCannotHideAHole() {
        val empty = EquipmentLayout()
        val half = HorizontalContact(rect(0, 0, 2, 4), 16)
        refused(empty.place(PlacedEquipment("flask", geometry()), listOf(half, half)), PlacementRefusal.UNSUPPORTED, empty)
    }

    @Test fun internalGapWrongHeightAndEdgeOnlyContactAreRejected() {
        val empty = EquipmentLayout()
        val badSurfaces = listOf(
            emptyList(),
            listOf(HorizontalContact(rect(0, 0, 4, 4), 15)),
            listOf(HorizontalContact(rect(4, 0, 8, 4), 16)),
            listOf(HorizontalContact(rect(0, 0, 4, 1), 16), HorizontalContact(rect(0, 2, 4, 4), 16)),
            listOf(HorizontalContact(rect(0, 0, 1, 4), 16), HorizontalContact(rect(2, 0, 4, 4), 16)),
        )
        for (surfaces in badSurfaces) {
            refused(empty.place(PlacedEquipment("flask", geometry()), surfaces), PlacementRefusal.UNSUPPORTED, empty)
        }
    }

    @Test fun negativeCoordinatesAndCrossCellEquipmentAreAllowed() {
        accepted(EquipmentLayout().place(PlacedEquipment("rack", geometry(-8, 40)), top))
    }

    @Test fun furnitureObstacleCanRejectOtherwiseSupportedPlacement() {
        val empty = EquipmentLayout()
        val obstacle = OccupiedBox(rect(1, 1, 2, 2), 20, 30)
        refused(empty.place(PlacedEquipment("flask", geometry()), top, listOf(obstacle)), PlacementRefusal.COLLISION, empty)
        val tableBody = OccupiedBox(rect(-32, -32, 64, 64), 0, 16)
        accepted(empty.place(PlacedEquipment("flask", geometry()), top, listOf(tableBody)))
    }

    @Test fun moveRetainsIdentityAndDoesNotCollideWithOldSelf() {
        val one = accepted(EquipmentLayout().place(PlacedEquipment("flask", geometry()), top))
        val moved = accepted(one.move("flask", geometry(1, 5), top))
        assertEquals(listOf("flask"), moved.equipment.map { it.id })
        assertEquals(1L, moved.equipment.single().geometry.boxes.single().footprint.minX)
        assertEquals(0L, one.equipment.single().geometry.boxes.single().footprint.minX)
    }

    @Test fun failedMovePreservesBothObjectsAndUnknownMoveDoesNotInsert() {
        val one = accepted(EquipmentLayout().place(PlacedEquipment("flask", geometry()), top))
        val two = accepted(one.place(PlacedEquipment("cylinder", geometry(8, 12)), top))
        refused(two.move("flask", geometry(7, 11), top), PlacementRefusal.COLLISION, two)
        refused(two.move("flask", geometry(20, 24), emptyList()), PlacementRefusal.UNSUPPORTED, two)
        refused(two.move("missing", geometry(20, 24), top), PlacementRefusal.UNKNOWN_ID, two)
    }

    @Test fun compoundShapeLeavesEmptySpaceBetweenRackLegs() {
        val legs = listOf(OccupiedBox(rect(0, 0, 2, 4), 16, 24), OccupiedBox(rect(10, 0, 12, 4), 16, 24))
        val rack = EquipmentGeometry(legs, legs.map { HorizontalContact(it.footprint, it.minY) })
        val layout = accepted(EquipmentLayout().place(PlacedEquipment("rack", rack), top))
        accepted(layout.place(PlacedEquipment("flask", geometry(4, 8)), top))
        val empty = EquipmentLayout()
        refused(empty.place(PlacedEquipment("rack", rack), listOf(top.single().copy(footprint = rect(0, 0, 2, 4)))),
            PlacementRefusal.UNSUPPORTED, empty)
    }

    @Test fun everyThreeByThreeSupportMaskMatchesDiscreteCoverageOracle() {
        val target = rect(0, 0, 3, 3)
        val vessel = PlacedEquipment("vessel", EquipmentGeometry(
            listOf(OccupiedBox(target, 16, 24)), listOf(HorizontalContact(target, 16))))
        for (mask in 0 until 512) {
            val patches = (0 until 9).filter { mask and (1 shl it) != 0 }.map {
                val x = (it % 3).toLong()
                val z = (it / 3).toLong()
                HorizontalContact(rect(x, z, x + 1, z + 1), 16)
            }
            val empty = EquipmentLayout()
            // Duplicate surfaces must not conceal missing cells; order must not matter.
            val result = empty.place(vessel, patches + patches.reversed())
            if (mask == 511) accepted(result)
            else refused(result, PlacementRefusal.UNSUPPORTED, empty)
        }
    }

    @Test fun extremeCoordinatesDoNotOverflowCoverage() {
        val footprint = rect(Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)
        val geometry = EquipmentGeometry(listOf(OccupiedBox(footprint, -1, 1)), listOf(HorizontalContact(footprint, -1)))
        accepted(EquipmentLayout().place(PlacedEquipment("wide", geometry), listOf(HorizontalContact(footprint, -1))))
    }

    @Test fun malformedGeometryAndFloatingSupportPatchesAreRejected() {
        assertFailsWith<IllegalArgumentException> { rect(0, 0, 0, 4) }
        assertFailsWith<IllegalArgumentException> { OccupiedBox(rect(0, 0, 4, 4), 5, 5) }
        assertFailsWith<IllegalArgumentException> { EquipmentGeometry(emptyList(), emptyList()) }
        assertFailsWith<IllegalArgumentException> { PlacedEquipment(" ", geometry()) }
        assertFailsWith<IllegalArgumentException> {
            EquipmentGeometry(geometry().boxes, listOf(HorizontalContact(rect(0, 0, 8, 8), 16)))
        }
        assertFailsWith<IllegalArgumentException> {
            EquipmentGeometry(geometry().boxes, listOf(HorizontalContact(rect(0, 0, 4, 4), 17)))
        }
    }

    @Test fun callerOwnedListsCannotChangeRegisteredGeometry() {
        val boxes = geometry().boxes.toMutableList()
        val contacts = geometry().contacts.toMutableList()
        val geometry = EquipmentGeometry(boxes, contacts)
        val layout = accepted(EquipmentLayout().place(PlacedEquipment("flask", geometry), top))
        boxes.clear()
        contacts.clear()
        assertEquals(1, layout.equipment.single().geometry.boxes.size)
        assertEquals(1, layout.equipment.single().geometry.contacts.size)
    }
}
