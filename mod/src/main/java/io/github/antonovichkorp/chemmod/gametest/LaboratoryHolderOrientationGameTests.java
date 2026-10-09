package io.github.antonovichkorp.chemmod.gametest;

import com.mojang.authlib.GameProfile;
import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaboratoryHolderOrientationGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final Direction[] DIRECTIONS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    private static LaboratoryHolderBlockEntity setup(GameTestHelper helper, Direction facing) {
        helper.setBlock(POS.below(), Blocks.STONE);
        helper.setBlock(POS, ChemBlocks.LABORATORY_HOLDER.get().defaultBlockState()
            .setValue(LaboratoryHolderBlock.RACK, true).setValue(LaboratoryHolderBlock.TRAY, true)
            .setValue(LaboratoryHolderBlock.FACING, facing));
        return (LaboratoryHolderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
    }
    private static BlockHitResult hit(BlockPos pos, Vec3 local) {
        return new BlockHitResult(local.add(pos.getX(), pos.getY(), pos.getZ()), Direction.UP, pos, false);
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void clockwiseCoordinatesMatchIndependentCornerOracle(GameTestHelper helper) {
        Vec3 input = new Vec3(0.25, 0.3, 0.125);
        Vec3[] expected = { input, new Vec3(0.875, 0.3, 0.25), new Vec3(0.75, 0.3, 0.875), new Vec3(0.125, 0.3, 0.75) };
        for (int index = 0; index < DIRECTIONS.length; index++) {
            var direction = DIRECTIONS[index];
            helper.assertTrue(LaboratoryHolderGeometry.toWorld(direction, input).distanceToSqr(expected[index]) < 1e-20,
                "wrong model rotation convention for " + direction);
            helper.assertTrue(LaboratoryHolderGeometry.toLocal(direction, expected[index]).distanceToSqr(input) < 1e-20,
                "inverse transform disagrees for " + direction);
            helper.assertTrue(LaboratoryHolderGeometry.modelRotation(direction) == index * 90, "wrong JSON angle");
        }
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void everyVesselRemainsPickableInsideItsRotatedCollisionShape(GameTestHelper helper) {
        var pos = helper.absolutePos(POS);
        for (Direction facing : DIRECTIONS) {
            var state = ChemBlocks.LABORATORY_HOLDER.get().defaultBlockState()
                .setValue(LaboratoryHolderBlock.RACK, true).setValue(LaboratoryHolderBlock.TRAY, true)
                .setValue(LaboratoryHolderBlock.FACING, facing);
            helper.assertTrue(LaboratoryHolderBlock.validArrangement(state), "rotated footprints failed core validation");
            for (int slot = 0; slot < 10; slot++) {
                var center = LaboratoryHolderGeometry.vesselCenter(facing, slot);
                helper.assertTrue(LaboratoryHolderBlock.slotAt(pos, hit(pos, center), state) == slot,
                    "rotated vessel selected wrong slot: " + facing + "/" + slot);
                var ownShape = LaboratoryHolderGeometry.shape(facing, slot < 6, slot >= 6);
                helper.assertTrue(ownShape.toAabbs().stream().anyMatch(box -> box.contains(center)), "vessel outside collision shape");
                var otherBounds = LaboratoryHolderGeometry.bounds(facing, slot >= 6);
                helper.assertTrue(!otherBounds.contains(center), "vessel moved into neighbouring holder");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void fourWorldRotationsKeepAllContentsAndIdentities(GameTestHelper helper) {
        var holder = setup(helper, Direction.NORTH);
        for (int slot = 0; slot < 10; slot++) holder.insert(slot,
            slot % 2 == 0 ? ChemItems.vial("этанол") : ChemItems.mixtureVial("вода", "этанол"));
        var level = helper.getLevel(); var pos = helper.absolutePos(POS);
        var saved = holder.saveWithoutMetadata(level.registryAccess());
        var original = holder.getBlockState();
        for (int i = 0; i < 4; i++) {
            var state = level.getBlockState(pos).rotate(Rotation.CLOCKWISE_90);
            level.setBlock(pos, state, 3);
            helper.assertTrue(level.getBlockEntity(pos) == holder, "rotation replaced block entity");
            helper.assertTrue(saved.equals(holder.saveWithoutMetadata(level.registryAccess())), "rotation changed contents/IDs");
            var encoded = NbtUtils.writeBlockState(state);
            var decoded = NbtUtils.readBlockState(level.registryAccess().lookupOrThrow(Registries.BLOCK), encoded);
            helper.assertTrue(decoded.equals(state), "orientation did not round-trip through block state NBT");
        }
        helper.assertTrue(level.getBlockState(pos).equals(original), "four turns changed layout");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void legacyStateWithoutFacingLoadsInHistoricalOrientation(GameTestHelper helper) {
        CompoundTag legacy = new CompoundTag();
        legacy.putString("Name", "chemmod:laboratory_holder");
        CompoundTag properties = new CompoundTag();
        properties.putString("rack", "true"); properties.putString("tray", "true");
        legacy.put("Properties", properties);
        var state = NbtUtils.readBlockState(helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK), legacy);
        helper.assertTrue(state.getValue(LaboratoryHolderBlock.FACING) == Direction.NORTH, "legacy layout rotated on load");
        helper.assertTrue(state.getValue(LaboratoryHolderBlock.RACK) && state.getValue(LaboratoryHolderBlock.TRAY), "legacy parts lost");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void firstPlacementFollowsPlayerAndSecondPartKeepsExistingOrientation(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(POS);
        helper.setBlock(POS.below(), Blocks.STONE);
        for (Direction facing : DIRECTIONS) {
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "lab_facing"));
            player.setPos(pos.getX() + 3, pos.getY(), pos.getZ() + 3);
            player.setYRot(facing.getOpposite().toYRot());
            var rack = new ItemStack(ChemItems.TEST_TUBE_RACK.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, rack);
            var tableHit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Direction.UP, pos.below(), false);
            helper.assertTrue(rack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, tableHit)).consumesAction(), "initial placement failed");
            helper.assertTrue(level.getBlockState(pos).getValue(LaboratoryHolderBlock.FACING) == facing, "placement ignored player direction");
            var holder = (LaboratoryHolderBlockEntity) level.getBlockEntity(pos);
            holder.insert(0, ChemItems.vial("вода"));
            var id = holder.holderId(true);
            player.setYRot(facing.toYRot()); // Stand on the opposite orientation for the second part.
            var tray = new ItemStack(ChemItems.LABORATORY_TRAY.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, tray);
            helper.assertTrue(tray.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                hit(pos, LaboratoryHolderGeometry.vesselCenter(facing, 0)))).consumesAction(), "second holder placement failed");
            helper.assertTrue(holder.getBlockState().getValue(LaboratoryHolderBlock.FACING) == facing, "second holder rotated first");
            helper.assertTrue(holder.holderId(true).equals(id) && !holder.contents(0).isEmpty(), "second holder changed first contents or ID");
            level.removeBlock(pos, false);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void rotatedSelectionRemovesTrayNotLoadedRack(GameTestHelper helper) {
        for (Direction facing : DIRECTIONS) {
            var holder = setup(helper, facing);
            holder.insert(0, ChemItems.vial("вода"));
            var level = helper.getLevel(); var pos = helper.absolutePos(POS);
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "lab_turn_pick"));
            player.setPos(pos.getX() + 3, pos.getY(), pos.getZ() + 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            holder.getBlockState().useWithoutItem(level, player, hit(pos, LaboratoryHolderGeometry.vesselCenter(facing, 6)));
            helper.assertTrue(!holder.getBlockState().getValue(LaboratoryHolderBlock.TRAY)
                && !holder.contents(0).isEmpty(), "rotated picking removed wrong part");
            helper.assertTrue(player.getInventory().countItem(ChemItems.LABORATORY_TRAY.get()) == 1, "tray pickup count changed");
            level.removeBlock(pos, false);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).forEach(ItemEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void gapOutsideFootprintsAndNonfiniteHitsNeverSelectVessels(GameTestHelper helper) {
        for (Direction facing : DIRECTIONS) {
            for (var local : new Vec3[] { new Vec3(0.5, 0.3, 0.5), new Vec3(-1, 0.3, 0.5),
                new Vec3(2, 0.3, 0.5), new Vec3(0.25, 0.3, 0), new Vec3(0.75, 0.3, 1) }) {
                helper.assertTrue(LaboratoryHolderGeometry.slotAt(facing, LaboratoryHolderGeometry.toWorld(facing, local), true, true) == -1,
                    "out-of-footprint point selected vessel");
            }
            helper.assertTrue(LaboratoryHolderGeometry.slotAt(facing, new Vec3(Double.NaN, 0, 0), true, true) == -1, "NaN selected vessel");
            helper.assertTrue(LaboratoryHolderGeometry.slotAt(facing, LaboratoryHolderGeometry.vesselCenter(facing, 0), false, true) == -1,
                "absent rack selected");
        }
        helper.succeed();
    }
}
