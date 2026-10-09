package io.github.antonovichkorp.chemmod.content;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Ten fixed physical positions, exact ItemStack components, no chemical conversion or discovery. */
public final class LaboratoryHolderBlockEntity extends BlockEntity {
    private final NonNullList<ItemStack> contents = NonNullList.withSize(10, ItemStack.EMPTY);
    private UUID rackId = UUID.randomUUID();
    private UUID trayId = UUID.randomUUID();
    private boolean dropped;

    public LaboratoryHolderBlockEntity(BlockPos pos, BlockState state) {
        super(ChemBlockEntities.LABORATORY_HOLDER.get(), pos, state);
    }
    public UUID holderId(boolean rack) { return rack ? rackId : trayId; }
    public ItemStack contents(int slot) { return slot >= 0 && slot < 10 ? contents.get(slot).copy() : ItemStack.EMPTY; }
    private boolean available(int slot) {
        return slot >= 0 && slot < 10 && getBlockState().getValue(slot < 6 ? LaboratoryHolderBlock.RACK : LaboratoryHolderBlock.TRAY);
    }
    public boolean insert(int slot, ItemStack held) {
        if (!liveServerHolder() || !available(slot)
            || !contents.get(slot).isEmpty() || !held.is(ChemItems.SUBSTANCE_VIAL.get())) return false;
        contents.set(slot, held.split(1));
        changed();
        return true;
    }
    public ItemStack extract(int slot) {
        if (!liveServerHolder() || !available(slot)) return ItemStack.EMPTY;
        ItemStack result = contents.get(slot);
        contents.set(slot, ItemStack.EMPTY);
        if (!result.isEmpty()) changed();
        return result;
    }
    /** Remove only an empty selected holder; never spill the neighbouring holder's contents. */
    public ItemStack takeEmptyHolder(boolean rack) {
        if (level == null || level.isClientSide() || dropped
            || level.getBlockEntity(worldPosition) != this) return ItemStack.EMPTY;
        var part = rack ? LaboratoryHolderBlock.RACK : LaboratoryHolderBlock.TRAY;
        var other = rack ? LaboratoryHolderBlock.TRAY : LaboratoryHolderBlock.RACK;
        var state = getBlockState();
        if (!state.getValue(part)) return ItemStack.EMPTY;
        int start = rack ? 0 : 6;
        int end = rack ? 6 : 10;
        for (int slot = start; slot < end; slot++) {
            if (!contents.get(slot).isEmpty()) return ItemStack.EMPTY;
        }
        if (state.getValue(other)) {
            if (!level.setBlock(worldPosition, state.setValue(part, false), 3)) return ItemStack.EMPTY;
            // The removed holder is now an ordinary empty item. A future installation
            // gets a fresh ID; the still-installed neighbour retains its identity.
            if (rack) rackId = UUID.randomUUID(); else trayId = UUID.randomUUID();
            changed();
        } else {
            // Refuse even malformed saves with orphan contents: do not delete matter.
            if (contents.stream().anyMatch(stack -> !stack.isEmpty())) return ItemStack.EMPTY;
            dropped = true; // onRemove must not also drop the item being returned.
            if (!level.removeBlock(worldPosition, false)) {
                dropped = false;
                return ItemStack.EMPTY;
            }
        }
        return new ItemStack(rack ? ChemItems.TEST_TUBE_RACK.get() : ChemItems.LABORATORY_TRAY.get());
    }

    /** Transfer a whole selected holder to one non-stackable item, including empty slot positions. */
    public ItemStack takeHolder(boolean rack) {
        if (!liveServerHolder()) return ItemStack.EMPTY;
        var part = rack ? LaboratoryHolderBlock.RACK : LaboratoryHolderBlock.TRAY;
        var other = rack ? LaboratoryHolderBlock.TRAY : LaboratoryHolderBlock.RACK;
        var state = getBlockState();
        if (!state.getValue(part)) return ItemStack.EMPTY;
        int start = rack ? 0 : 6;
        int end = rack ? 6 : 10;
        // Hidden contents in an absent neighbour must not disappear with the last host.
        if (!state.getValue(other)) {
            for (int slot = 0; slot < 10; slot++) {
                if ((slot < start || slot >= end) && !contents.get(slot).isEmpty()) return ItemStack.EMPTY;
            }
        }
        var copies = new java.util.ArrayList<ItemStack>();
        for (int slot = start; slot < end; slot++) copies.add(contents.get(slot).copy());
        final PackedLaboratoryHolder packed;
        try {
            packed = new PackedLaboratoryHolder(rack, holderId(rack),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(copies));
        } catch (IllegalArgumentException invalid) {
            return ItemStack.EMPTY; // Do not erase malformed saved contents.
        }
        ItemStack result = new ItemStack(rack ? ChemItems.TEST_TUBE_RACK.get() : ChemItems.LABORATORY_TRAY.get());
        result.set(ChemComponents.PACKED_HOLDER.get(), packed);
        result.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 1);
        if (state.getValue(other)) {
            if (!level.setBlock(worldPosition, state.setValue(part, false), 3)) return ItemStack.EMPTY;
        } else {
            dropped = true; // No loose contents/holder drop in addition to the returned packed item.
            if (!level.removeBlock(worldPosition, false)) {
                dropped = false;
                return ItemStack.EMPTY;
            }
        }
        for (int slot = start; slot < end; slot++) contents.set(slot, ItemStack.EMPTY);
        if (rack) rackId = UUID.randomUUID(); else trayId = UUID.randomUUID();
        if (!dropped) changed();
        return result;
    }

    private boolean liveServerHolder() {
        return level != null && !level.isClientSide() && !dropped && level.getBlockEntity(worldPosition) == this;
    }
    /** Can be checked before adding the part to an existing host. No mutation on refusal. */
    public boolean canRestorePacked(boolean rack, PackedLaboratoryHolder packed) {
        if (!liveServerHolder() || packed.rack() != rack) return false;
        var other = rack ? LaboratoryHolderBlock.TRAY : LaboratoryHolderBlock.RACK;
        if (getBlockState().getValue(other) && holderId(!rack).equals(packed.holderId())) return false;
        for (int slot = rack ? 0 : 6; slot < (rack ? 6 : 10); slot++) {
            if (!contents.get(slot).isEmpty()) return false;
        }
        return true;
    }
    public boolean restorePacked(boolean rack, PackedLaboratoryHolder packed) {
        if (!canRestorePacked(rack, packed)
            || !getBlockState().getValue(rack ? LaboratoryHolderBlock.RACK : LaboratoryHolderBlock.TRAY)) return false;
        var slots = packed.copySlots();
        for (int slot = 0; slot < slots.size(); slot++) contents.set((rack ? 0 : 6) + slot, slots.get(slot));
        if (rack) rackId = packed.holderId(); else trayId = packed.holderId();
        changed();
        return true;
    }
    /** Rollback guard for a freshly placed, still-empty host if restoration is refused. */
    public void cancelEmptyPlacement() {
        if (liveServerHolder() && contents.stream().allMatch(ItemStack::isEmpty)) dropped = true;
    }
    @Override protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        // Placement already restored the snapshot. Do not keep a second, stale copy on the BE.
        input.get(ChemComponents.PACKED_HOLDER.get());
        input.get(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    /** Destruction/support loss drains once; loot table is deliberately empty. */
    public void dropAll() {
        if (level == null || level.isClientSide() || dropped || level.restoringBlockSnapshots) return;
        dropped = true;
        for (int i = 0; i < contents.size(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), contents.get(i));
            contents.set(i, ItemStack.EMPTY);
        }
        if (getBlockState().getValue(LaboratoryHolderBlock.RACK))
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), new ItemStack(ChemItems.TEST_TUBE_RACK.get()));
        if (getBlockState().getValue(LaboratoryHolderBlock.TRAY))
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), new ItemStack(ChemItems.LABORATORY_TRAY.get()));
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, contents, registries);
        tag.putUUID("rack_id", rackId);
        tag.putUUID("tray_id", trayId);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents.clear();
        ContainerHelper.loadAllItems(tag, contents, registries);
        if (tag.hasUUID("rack_id")) rackId = tag.getUUID("rack_id");
        if (tag.hasUUID("tray_id")) trayId = tag.getUUID("tray_id");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
