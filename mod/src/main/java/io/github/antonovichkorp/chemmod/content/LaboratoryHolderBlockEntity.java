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
        if (level == null || level.isClientSide() || dropped || !available(slot)
            || !contents.get(slot).isEmpty() || !held.is(ChemItems.SUBSTANCE_VIAL.get())) return false;
        contents.set(slot, held.split(1));
        changed();
        return true;
    }
    public ItemStack extract(int slot) {
        if (level == null || level.isClientSide() || dropped || !available(slot)) return ItemStack.EMPTY;
        ItemStack result = contents.get(slot);
        contents.set(slot, ItemStack.EMPTY);
        if (!result.isEmpty()) changed();
        return result;
    }
    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    /** Destruction/support loss drains once; loot table is deliberately empty. */
    public void dropAll() {
        if (level == null || level.isClientSide() || dropped) return;
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
