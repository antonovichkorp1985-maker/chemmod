package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaboratoryHolderGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static LaboratoryHolderBlockEntity setup(GameTestHelper helper, boolean tray) {
        helper.setBlock(POS.below(), Blocks.STONE);
        helper.setBlock(POS, ChemBlocks.LABORATORY_HOLDER.get().defaultBlockState()
            .setValue(LaboratoryHolderBlock.RACK, true).setValue(LaboratoryHolderBlock.TRAY, tray));
        return (LaboratoryHolderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
    }
    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void emptyRackCanBeRemovedWithoutDisturbingLoadedTray(GameTestHelper helper) {
        var holder = setup(helper, true);
        var vial = ChemItems.mixtureVial("вода", "этанол");
        var expected = vial.copy();
        holder.insert(9, vial);
        var trayId = holder.holderId(false);
        var rackId = holder.holderId(true);
        var result = holder.takeEmptyHolder(true);
        helper.assertTrue(result.is(ChemItems.TEST_TUBE_RACK.get()) && result.getCount() == 1, "empty rack not returned");
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(POS)) == holder, "neighbour host replaced");
        helper.assertTrue(!holder.getBlockState().getValue(LaboratoryHolderBlock.RACK)
            && holder.getBlockState().getValue(LaboratoryHolderBlock.TRAY), "wrong part removed");
        helper.assertTrue(holder.holderId(false).equals(trayId), "neighbour identity changed");
        helper.assertTrue(!holder.holderId(true).equals(rackId), "removed identity reused for next installation");
        helper.assertTrue(ItemStack.isSameItemSameComponents(expected, holder.contents(9)), "neighbour contents changed");
        helper.assertTrue(holder.takeEmptyHolder(true).isEmpty(), "repeated pickup duplicated rack");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void occupiedHolderPickupRefusesWithoutChangingEitherPart(GameTestHelper helper) {
        var holder = setup(helper, true);
        holder.insert(5, ChemItems.vial("этанол"));
        holder.insert(9, ChemItems.mixtureVial("вода", "этанол"));
        var before = holder.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(holder.takeEmptyHolder(true).isEmpty() && holder.takeEmptyHolder(false).isEmpty(), "loaded holder removed");
        helper.assertTrue(before.equals(holder.saveWithoutMetadata(helper.getLevel().registryAccess())), "refusal mutated inventory or identity");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void removingLastEmptyHolderDoesNotLeaveGhostOrDuplicateDrop(GameTestHelper helper) {
        var holder = setup(helper, true);
        var tray = holder.takeEmptyHolder(false);
        helper.assertTrue(tray.is(ChemItems.LABORATORY_TRAY.get()) && tray.getCount() == 1, "tray not returned");
        var rack = holder.takeEmptyHolder(true);
        helper.assertTrue(rack.is(ChemItems.TEST_TUBE_RACK.get()) && rack.getCount() == 1, "last rack not returned");
        var pos = helper.absolutePos(POS);
        helper.assertTrue(helper.getLevel().getBlockState(pos).isAir()
            && helper.getLevel().getBlockEntity(pos) == null, "empty host remains");
        helper.assertTrue(holder.takeEmptyHolder(true).isEmpty(), "detached host duplicated holder");
        holder.dropAll();
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1)).isEmpty(),
            "pickup also produced world drops");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void sneakEmptyHandInteractionRemovesOnlySelectedTray(GameTestHelper helper) {
        var holder = setup(helper, true);
        holder.insert(0, ChemItems.vial("вода"));
        var level = helper.getLevel(); var pos = helper.absolutePos(POS);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "lab_pickup"));
        player.setPos(pos.getX() + 3, pos.getY(), pos.getZ() + 3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        var hit = new BlockHitResult(new Vec3(pos.getX() + 12.0 / 16, pos.getY() + 0.25, pos.getZ() + 3.0 / 16), Direction.UP, pos, false);
        holder.getBlockState().useWithoutItem(level, player, hit);
        helper.assertTrue(!holder.getBlockState().getValue(LaboratoryHolderBlock.TRAY)
            && !holder.contents(0).isEmpty(), "interaction removed wrong part or contents");
        helper.assertTrue(player.getInventory().countItem(ChemItems.LABORATORY_TRAY.get()) == 1, "pickup did not give one tray");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void firstHolderPlacesThroughSurvivalItemPathWithoutReplacingTable(GameTestHelper helper) {
        helper.setBlock(POS.below(), Blocks.STONE);
        var level = helper.getLevel(); var pos = helper.absolutePos(POS);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "lab_place"));
        player.setPos(pos.getX() + 3, pos.getY(), pos.getZ() + 3);
        var held = new ItemStack(ChemItems.TEST_TUBE_RACK.get(), 2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        var hit = new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5),
            Direction.UP, pos.below(), false);
        var result = held.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(result.consumesAction() && held.getCount() == 1, "first placement did not consume one rack");
        helper.assertTrue(level.getBlockState(pos.below()).is(Blocks.STONE), "holder replaced table");
        helper.assertTrue(level.getBlockEntity(pos) instanceof LaboratoryHolderBlockEntity
            && level.getBlockState(pos).getValue(LaboratoryHolderBlock.RACK)
            && !level.getBlockState(pos).getValue(LaboratoryHolderBlock.TRAY), "wrong initial holder state");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void heldTrayReachesItemPlacementInsteadOfStealingRackVial(GameTestHelper helper) {
        var holder = setup(helper, false);
        holder.insert(0, ChemItems.vial("вода"));
        var level = helper.getLevel(); var pos = helper.absolutePos(POS);
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "lab_test"));
        player.setPos(pos.getX() + 3, pos.getY(), pos.getZ() + 3);
        var held = new ItemStack(ChemItems.LABORATORY_TRAY.get(), 2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        var hit = new BlockHitResult(new Vec3(pos.getX() + 0.25, pos.getY() + 0.5, pos.getZ() + 2.5 / 16), Direction.UP, pos, false);
        var result = holder.getBlockState().useItemOn(held, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(result == net.minecraft.world.ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
            "held tray was intercepted by empty-hand extraction");
        var placement = held.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(placement.consumesAction() && held.getCount() == 1, "tray did not install exactly once");
        helper.assertTrue(holder.getBlockState().getValue(LaboratoryHolderBlock.TRAY) && !holder.contents(0).isEmpty(),
            "tray installation removed rack contents");
        var duplicate = held.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        helper.assertTrue(!duplicate.consumesAction() && held.getCount() == 1, "duplicate tray consumed item");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void physicalTransferPreservesComponentsAndRefusesOccupiedSlot(GameTestHelper helper) {
        var holder = setup(helper, false);
        ItemStack held = ChemItems.vial("этанол");
        held.setCount(3);
        ItemStack original = held.copyWithCount(1);
        helper.assertTrue(holder.insert(0, held) && held.getCount() == 2, "insertion did not transfer exactly one vial");
        helper.assertTrue(!holder.insert(0, held) && held.getCount() == 2, "occupied slot consumed or duplicated a vial");
        helper.assertTrue(!holder.insert(6, held), "absent tray accepted a vial");
        helper.assertTrue(!holder.insert(-1, held) && !holder.insert(10, held), "invalid slot accepted");
        helper.assertTrue(!holder.insert(1, new ItemStack(Items.DIRT)), "non-vial accepted");
        var copy = holder.contents(0); copy.setCount(8);
        var extracted = holder.extract(0);
        helper.assertTrue(extracted.getCount() == 1 && ItemStack.isSameItemSameComponents(original, extracted), "vial components/count changed");
        helper.assertTrue(holder.extract(0).isEmpty(), "repeated extraction duplicated contents");
        helper.succeed();
    }
    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void secondHolderSharesCellWithoutReplacingFirstInventory(GameTestHelper helper) {
        var holder = setup(helper, false);
        holder.insert(2, ChemItems.vial("вода"));
        var id = holder.holderId(true);
        var contents = holder.contents(2);
        var pos = helper.absolutePos(POS);
        var state = holder.getBlockState().setValue(LaboratoryHolderBlock.TRAY, true);
        helper.assertTrue(LaboratoryHolderBlock.validArrangement(state), "core placement rejected disjoint holders");
        helper.getLevel().setBlock(pos, state, 3);
        helper.assertTrue(helper.getLevel().getBlockEntity(pos) == holder, "adding tray replaced host");
        helper.assertTrue(holder.holderId(true).equals(id) && !id.equals(holder.holderId(false)), "holder identity changed or aliased");
        helper.assertTrue(ItemStack.isSameItemSameComponents(contents, holder.contents(2)), "adding tray changed rack vial");
        helper.assertTrue(holder.insert(6, ChemItems.mixtureVial("вода", "этанол")), "tray refused mixture");
        helper.assertTrue(state.getRenderShape() == RenderShape.MODEL, "host is invisible");
        helper.succeed();
    }
    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void saveReloadPreservesBothHolderIdsAndAllTenPositions(GameTestHelper helper) {
        var holder = setup(helper, true);
        for (int slot = 0; slot < 10; slot++) helper.assertTrue(holder.insert(slot,
            slot % 2 == 0 ? ChemItems.vial("этанол") : ChemItems.mixtureVial("вода", "этанол")), "position refused vial");
        var level = helper.getLevel(); var pos = helper.absolutePos(POS);
        var saved = holder.saveWithoutMetadata(level.registryAccess());
        var restored = new LaboratoryHolderBlockEntity(pos, holder.getBlockState());
        restored.loadWithComponents(saved, level.registryAccess());
        level.removeBlockEntity(pos); level.setBlockEntity(restored);
        helper.assertTrue(saved.equals(restored.saveWithoutMetadata(level.registryAccess())), "reload changed contents/identity");
        helper.assertTrue(saved.equals(restored.getUpdateTag(level.registryAccess())), "client sync omits saved contents");
        helper.assertTrue(restored.extract(9).getCount() == 1, "restored last position unusable");
        helper.succeed();
    }
    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void removingSupportDropsBothHoldersAndExactVialContentsOnce(GameTestHelper helper) {
        var holder = setup(helper, true);
        var vial = ChemItems.mixtureVial("вода", "этанол");
        var original = vial.copy(); holder.insert(0, vial);
        var pos = helper.absolutePos(POS); var level = helper.getLevel();
        level.destroyBlock(pos.below(), false);
        helper.assertTrue(level.getBlockState(pos).isAir(), "unsupported holders floated");
        holder.dropAll(); // onRemove already drained it: must be idempotent.
        var items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1));
        int racks = 0, trays = 0, vials = 0;
        for (var entity : items) {
            var stack = entity.getItem();
            if (stack.is(ChemItems.TEST_TUBE_RACK.get())) racks += stack.getCount();
            if (stack.is(ChemItems.LABORATORY_TRAY.get())) trays += stack.getCount();
            if (stack.is(ChemItems.SUBSTANCE_VIAL.get())) {
                vials += stack.getCount();
                helper.assertTrue(ItemStack.isSameItemSameComponents(stack, original), "dropped vial contents changed");
            }
        }
        helper.assertTrue(racks == 1 && trays == 1 && vials == 1, "support loss lost or duplicated items");
        helper.succeed();
    }
    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void hitSelectionAddressesEveryPositionAndRejectsGap(GameTestHelper helper) {
        var holder = setup(helper, true); var pos = helper.absolutePos(POS);
        for (int slot = 0; slot < 10; slot++) {
            boolean rack = slot < 6;
            var point = new Vec3(pos.getX() + (rack ? 4.0 : 12.0) / 16, pos.getY() + 0.5,
                pos.getZ() + (rack ? 2.5 + slot * 2.2 : 3 + (slot - 6) * 3.3) / 16);
            helper.assertTrue(LaboratoryHolderBlock.slotAt(pos, new BlockHitResult(point, Direction.UP, pos, false), holder.getBlockState()) == slot,
                "hit selected wrong vial position");
        }
        helper.assertTrue(LaboratoryHolderBlock.slotAt(pos, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false),
            holder.getBlockState()) == -1, "gap selected a holder");
        helper.succeed();
    }
}
