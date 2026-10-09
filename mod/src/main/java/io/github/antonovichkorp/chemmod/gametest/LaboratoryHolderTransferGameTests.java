package io.github.antonovichkorp.chemmod.gametest;

import com.mojang.authlib.GameProfile;
import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.*;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LaboratoryHolderTransferGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final BlockPos DEST = new BlockPos(2, 2, 1);
    private static LaboratoryHolderBlockEntity setup(GameTestHelper helper, boolean rack, boolean tray) {
        helper.setBlock(POS.below(), Blocks.STONE);
        helper.setBlock(POS, ChemBlocks.LABORATORY_HOLDER.get().defaultBlockState()
            .setValue(LaboratoryHolderBlock.RACK, rack).setValue(LaboratoryHolderBlock.TRAY, tray));
        return (LaboratoryHolderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(POS));
    }
    private static FakePlayer player(GameTestHelper helper, ItemStack held, boolean creative) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "lab_transfer"));
        var pos = helper.absolutePos(POS);
        player.setPos(pos.getX() + 3, pos.getY(), pos.getZ() + 3);
        player.setYRot(Direction.WEST.toYRot()); // Holder faces east at its new location.
        player.getAbilities().instabuild = creative;
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }
    private static UseOnContext tableContext(GameTestHelper helper, FakePlayer player) {
        var pos = helper.absolutePos(DEST);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(
            new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), Direction.UP, pos.below(), false));
    }
    private static UseOnContext hostContext(GameTestHelper helper, FakePlayer player) {
        var pos = helper.absolutePos(POS);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }
    private static void same(GameTestHelper helper, ItemStack expected, ItemStack actual, String message) {
        helper.assertTrue(expected.getCount() == actual.getCount() && ItemStack.isSameItemSameComponents(expected, actual), message);
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void pickupPreservesHolesComponentsAndIdentityWithoutTouchingNeighbour(GameTestHelper helper) {
        var holder = setup(helper, true, true);
        holder.insert(0, ChemItems.vial("вода"));
        var mixture = ChemItems.mixtureVial("вода", "этанол");
        mixture.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Sample A"));
        holder.insert(9, mixture);
        var expected = holder.contents(9); var neighbour = holder.contents(0);
        var id = holder.holderId(false); var neighbourId = holder.holderId(true);
        var packed = holder.takeHolder(false);
        var data = packed.get(ChemComponents.PACKED_HOLDER.get());
        helper.assertTrue(packed.getCount() == 1 && packed.getMaxStackSize() == 1 && data != null, "holder not packed as single item");
        helper.assertTrue(data.holderId().equals(id) && !data.rack() && data.occupiedCount() == 1, "packed identity/type/count changed");
        var slots = data.copySlots();
        helper.assertTrue(slots.size() == 4 && slots.get(0).isEmpty() && slots.get(2).isEmpty(), "empty positions collapsed");
        same(helper, expected, slots.get(3), "last vial components changed");
        slots.get(3).setCount(5);
        helper.assertTrue(data.copySlots().get(3).getCount() == 1, "mutable copy changed packed contents");
        same(helper, neighbour, holder.contents(0), "neighbour contents changed");
        helper.assertTrue(holder.holderId(true).equals(neighbourId) && holder.contents(9).isEmpty(), "neighbour ID changed or source retained vial");
        helper.assertTrue(holder.takeHolder(false).isEmpty(), "second pickup duplicated tray");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(1)).isEmpty(), "pickup also dropped contents");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void carriedItemRoundTripsThroughDiskAndNetworkCodecs(GameTestHelper helper) {
        var holder = setup(helper, true, false);
        holder.insert(5, ChemItems.mixtureVial("вода", "этанол"));
        var packed = holder.takeHolder(true);
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, packed).getOrThrow();
        var restored = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        same(helper, packed, restored, "item NBT round-trip changed snapshot");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, restored);
            var decoded = ItemStack.STREAM_CODEC.decode(buffer);
            same(helper, packed, decoded, "item network round-trip changed snapshot");
            helper.assertTrue(buffer.readableBytes() == 0, "unconsumed bytes after item decode");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void filledRackMovesToAnotherCellAndOrientationWithNoLooseDrops(GameTestHelper helper) {
        var holder = setup(helper, true, false);
        var expected = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 6; slot++) {
            holder.insert(slot, slot % 2 == 0 ? ChemItems.vial("этанол") : ChemItems.mixtureVial("вода", "этанол"));
            expected.add(holder.contents(slot));
        }
        var id = holder.holderId(true);
        var packed = holder.takeHolder(true);
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(POS)).isAir(), "source host remains");
        holder.dropAll(); // detached source must not duplicate the transfer
        helper.assertTrue(holder.extract(0).isEmpty(), "detached source still exposes contents");
        helper.setBlock(DEST.below(), Blocks.STONE);
        var player = player(helper, packed, false);
        helper.assertTrue(packed.useOn(tableContext(helper, player)).consumesAction() && packed.isEmpty(), "survival placement did not consume packed item");
        var placed = (LaboratoryHolderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEST));
        helper.assertTrue(placed.holderId(true).equals(id) && placed.getBlockState().getValue(LaboratoryHolderBlock.FACING) == Direction.EAST, "new orientation/identity wrong");
        for (int slot = 0; slot < 6; slot++) same(helper, expected.get(slot), placed.contents(slot), "placed vessel changed");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(POS)).inflate(2)).isEmpty(), "normal transfer spilled or duplicated items");
        helper.assertTrue(!placed.saveWithoutMetadata(helper.getLevel().registryAccess()).getCompound("components")
            .contains("chemmod:packed_laboratory_holder"), "BE retained stale packed snapshot");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void creativeTransferConsumesPackedItemButNotOrdinaryEmptyHolder(GameTestHelper helper) {
        var holder = setup(helper, false, true);
        holder.insert(9, ChemItems.vial("вода"));
        var packed = holder.takeHolder(false); var id = packed.get(ChemComponents.PACKED_HOLDER.get()).holderId();
        helper.setBlock(DEST.below(), Blocks.STONE);
        var player = player(helper, packed, true);
        helper.assertTrue(packed.useOn(tableContext(helper, player)).consumesAction() && packed.isEmpty(), "creative cloned packed contents");
        var placed = (LaboratoryHolderBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEST));
        helper.assertTrue(placed.holderId(false).equals(id) && !placed.contents(9).isEmpty(), "creative transfer lost contents");
        var empty = new ItemStack(ChemItems.TEST_TUBE_RACK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, empty);
        var pos = helper.absolutePos(DEST);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(empty.useOn(context).consumesAction() && empty.getCount() == 1, "ordinary creative placement behaviour changed");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void packedTrayMergesWithoutChangingNeighbourAndCannotBePlacedTwice(GameTestHelper helper) {
        var holder = setup(helper, true, true);
        holder.insert(0, ChemItems.vial("вода")); holder.insert(8, ChemItems.vial("этанол"));
        var rackId = holder.holderId(true); var trayId = holder.holderId(false);
        var packed = holder.takeHolder(false);
        var player = player(helper, packed, true);
        helper.assertTrue(packed.useOn(hostContext(helper, player)).consumesAction() && packed.isEmpty(), "merge cloned or refused packed item");
        helper.assertTrue(holder.holderId(true).equals(rackId) && holder.holderId(false).equals(trayId)
            && !holder.contents(0).isEmpty() && !holder.contents(8).isEmpty(), "merge changed contents/IDs");
        helper.assertTrue(ChemItems.LABORATORY_TRAY.get().useOn(hostContext(helper, player)) == net.minecraft.world.InteractionResult.FAIL,
            "empty held stack installed a second holder");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void placementRefusalsPreserveCarriedSnapshotAndWorld(GameTestHelper helper) {
        var holder = setup(helper, true, true);
        holder.insert(6, ChemItems.vial("этанол"));
        var packed = holder.takeHolder(false);
        // Reinstall a distinct empty tray, making the target occupied.
        var pos = helper.absolutePos(POS); var level = helper.getLevel();
        level.setBlock(pos, holder.getBlockState().setValue(LaboratoryHolderBlock.TRAY, true), 3);
        var before = holder.saveWithoutMetadata(level.registryAccess());
        var expected = packed.copy(); var player = player(helper, packed, false);
        helper.assertTrue(!packed.useOn(hostContext(helper, player)).consumesAction(), "occupied target accepted tray");
        same(helper, expected, packed, "refused merge consumed carried data");
        helper.assertTrue(before.equals(holder.saveWithoutMetadata(level.registryAccess())), "refused merge mutated target");
        // Non-sturdy support at the destination: no ghost holder or consumption.
        helper.setBlock(DEST.below(), Blocks.TORCH);
        helper.assertTrue(!packed.useOn(tableContext(helper, player)).consumesAction(), "unsupported placement accepted");
        same(helper, expected, packed, "unsupported placement consumed data");
        helper.assertTrue(!level.getBlockState(helper.absolutePos(DEST)).is(ChemBlocks.LABORATORY_HOLDER.get()), "refusal left ghost holder");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void malformedPayloadsAndNestedContainersAreRejected(GameTestHelper helper) {
        var holder = setup(helper, true, false);
        holder.insert(0, ChemItems.vial("вода"));
        var packed = holder.takeHolder(true);
        helper.setBlock(DEST.below(), Blocks.STONE);
        var player = player(helper, packed, false);
        // Transfer type does not match the held item.
        var wrongType = new ItemStack(ChemItems.LABORATORY_TRAY.get());
        wrongType.set(ChemComponents.PACKED_HOLDER.get(), packed.get(ChemComponents.PACKED_HOLDER.get()));
        wrongType.set(DataComponents.MAX_STACK_SIZE, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrongType);
        helper.assertTrue(!wrongType.useOn(tableContext(helper, player)).consumesAction(), "wrong holder type accepted");
        var stacked = packed.copyWithCount(2); player.setItemInHand(InteractionHand.MAIN_HAND, stacked);
        helper.assertTrue(!stacked.useOn(tableContext(helper, player)).consumesAction() && stacked.getCount() == 2, "stacked snapshots accepted");
        var tagged = packed.copy(); var tag = new CompoundTag(); tag.putString("id", "chemmod:laboratory_holder");
        tagged.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag)); player.setItemInHand(InteractionHand.MAIN_HAND, tagged);
        helper.assertTrue(!tagged.useOn(tableContext(helper, player)).consumesAction(), "raw BE tag bypass accepted");
        var invalidLists = new ArrayList<List<ItemStack>>();
        invalidLists.add(List.of(new ItemStack(Items.DIRT)));
        invalidLists.add(List.of(new ItemStack(ChemItems.SUBSTANCE_VIAL.get(), 2)));
        invalidLists.add(List.of(packed));
        var tooMany = new ArrayList<ItemStack>(); for (int i = 0; i < 7; i++) tooMany.add(ChemItems.vial("вода"));
        invalidLists.add(tooMany);
        var nested = ChemItems.vial("вода"); nested.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY); invalidLists.add(List.of(nested));
        for (var values : invalidLists) {
            boolean refused = false;
            try { new PackedLaboratoryHolder(true, UUID.randomUUID(), ItemContainerContents.fromItems(values)); }
            catch (IllegalArgumentException expected) { refused = true; }
            helper.assertTrue(refused, "invalid vessel list accepted");
        }
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(DEST)).isAir(), "malformed payload changed world");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void fullInventoryDeliveryDropsOnePackedItemWithoutLosingContents(GameTestHelper helper) {
        var holder = setup(helper, false, true);
        holder.insert(9, ChemItems.mixtureVial("вода", "этанол"));
        var expected = holder.contents(9); var id = holder.holderId(false);
        var player = player(helper, ItemStack.EMPTY, false);
        // Exercise delivery fallback directly; ordinary empty-hand interaction normally leaves
        // one inventory slot available. This does not pretend to simulate a network race.
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        var packed = holder.takeHolder(false);
        if (!player.getInventory().add(packed)) player.drop(packed, false);
        var pos = player.position();
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos, pos).inflate(3));
        var matching = drops.stream().filter(entity -> entity.getItem().has(ChemComponents.PACKED_HOLDER.get())).toList();
        helper.assertTrue(matching.size() == 1 && matching.getFirst().getItem().getCount() == 1, "full inventory lost or duplicated packed item");
        var data = matching.getFirst().getItem().get(ChemComponents.PACKED_HOLDER.get());
        helper.assertTrue(data.holderId().equals(id), "dropped holder identity changed");
        same(helper, expected, data.copySlots().get(3), "dropped packed vial changed");
        helper.succeed();
    }
}
