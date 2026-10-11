package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.equipment.Footprint;
import java.util.EnumMap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One coordinate contract for world collision, core placement, vessel rendering and picking.
 * North is the historical test.7 layout. All points below use block-local units [0,1].
 */
public final class LaboratoryHolderGeometry {
    private static final double EDGE_EPSILON = 1.0e-7;
    private static final EnumMap<Direction, VoxelShape[]> SHAPES = new EnumMap<>(Direction.class);
    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape rack = Shapes.create(bounds(facing, true));
            VoxelShape tray = Shapes.create(bounds(facing, false));
            SHAPES.put(facing, new VoxelShape[] { Shapes.empty(), rack, tray, Shapes.or(rack, tray) });
        }
    }
    private LaboratoryHolderGeometry() {}

    /** Clockwise rotation looking down, matching block-model JSON y rotations. */
    public static Vec3 toWorld(Direction facing, Vec3 local) {
        return switch (facing) {
            case NORTH -> local;
            case EAST -> new Vec3(1 - local.z, local.y, local.x);
            case SOUTH -> new Vec3(1 - local.x, local.y, 1 - local.z);
            case WEST -> new Vec3(local.z, local.y, 1 - local.x);
            default -> throw new IllegalArgumentException("Holder facing must be horizontal");
        };
    }
    public static Vec3 toLocal(Direction facing, Vec3 world) {
        return toWorld(switch (facing) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.WEST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.EAST;
            default -> throw new IllegalArgumentException("Holder facing must be horizontal");
        }, world);
    }
    public static int modelRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> throw new IllegalArgumentException("Holder facing must be horizontal");
        };
    }
    public static Vec3 vesselCenter(Direction facing, int slot) {
        if (slot < 0 || slot >= 10) throw new IllegalArgumentException("Invalid holder position");
        boolean rack = slot < 6;
        return toWorld(facing, new Vec3((rack ? 4.0 : 12.0) / 16, (rack ? 5.5 : 3.2) / 16,
            (rack ? 2.5 + slot * 2.2 : 3.0 + (slot - 6) * 3.3) / 16));
    }
    public static AABB bounds(Direction facing, boolean rack) {
        Vec3 a = toWorld(facing, new Vec3((rack ? 1.0 : 9.0) / 16, 0, 1.0 / 16));
        Vec3 b = toWorld(facing, new Vec3((rack ? 7.0 : 15.0) / 16, (rack ? 10.0 : 6.0) / 16, 15.0 / 16));
        return new AABB(Math.min(a.x, b.x), 0, Math.min(a.z, b.z),
            Math.max(a.x, b.x), b.y, Math.max(a.z, b.z));
    }
    public static Footprint footprint(Direction facing, boolean rack) {
        AABB box = bounds(facing, rack);
        return new Footprint(Math.round(box.minX * 16), Math.round(box.minZ * 16),
            Math.round(box.maxX * 16), Math.round(box.maxZ * 16));
    }
    public static VoxelShape shape(Direction facing, boolean rack, boolean tray) {
        var shapes = SHAPES.get(facing);
        if (shapes == null) throw new IllegalArgumentException("Holder facing must be horizontal");
        return shapes[(rack ? 1 : 0) | (tray ? 2 : 0)];
    }
    /** Bounded horizontal picking, not clamping arbitrarily distant points into a slot. */
    public static int slotAt(Direction facing, Vec3 world, boolean rack, boolean tray) {
        if (!Double.isFinite(world.x) || !Double.isFinite(world.y) || !Double.isFinite(world.z)) return -1;
        Vec3 local = toLocal(facing, world);
        if (!between(local.z, 1.0 / 16, 15.0 / 16)) return -1;
        if (rack && between(local.x, 1.0 / 16, 7.0 / 16))
            return Math.clamp(Math.round((local.z * 16 - 2.5) / 2.2), 0, 5);
        if (tray && between(local.x, 9.0 / 16, 15.0 / 16))
            return 6 + Math.clamp(Math.round((local.z * 16 - 3.0) / 3.3), 0, 3);
        return -1;
    }
    private static boolean between(double value, double min, double max) {
        return value >= min - EDGE_EPSILON && value <= max + EDGE_EPSILON;
    }
}
