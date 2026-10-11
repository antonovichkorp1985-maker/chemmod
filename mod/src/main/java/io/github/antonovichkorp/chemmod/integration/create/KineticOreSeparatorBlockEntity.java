package io.github.antonovichkorp.chemmod.integration.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.MaterialBatchContents;
import io.github.antonovichkorp.chemmod.core.material.MaterialMassComposition;
import io.github.antonovichkorp.chemmod.core.material.MaterialPartitionSpec;
import io.github.antonovichkorp.chemmod.core.material.MaterialProcessExports;
import io.github.antonovichkorp.chemmod.core.material.OreSeparation;
import io.github.antonovichkorp.chemmod.core.material.OreSeparationResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-authoritative, inventory-backed Create adapter for the canonical
 * ChemMod partition process. It deliberately calculates its output from the
 * loaded item component, never from a static or random Create recipe.
 */
public final class KineticOreSeparatorBlockEntity extends KineticBlockEntity {
    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOTS = 65; // one primary component plus schema-v2's 64 named impurities
    private static final float MAX_WORK_PER_TICK = 256.0F;
    private static final List<MaterialPartitionSpec> PROCESSES =
        MaterialProcessExports.bundledPartitionProcesses();

    private final ItemStackHandler input = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == INPUT_SLOT && findProcess(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            workDone = 0.0F;
            inventoryChanged();
        }
    };

    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
            inventoryChanged();
        }
    };

    private float workDone;
    private int unsavedWorkTicks;

    public KineticOreSeparatorBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCompatBlockEntities.KINETIC_ORE_SEPARATOR.get(), pos, state);
    }

    /** Input is the marked front, output is the opposite face; shaft faces expose no item capability. */
    public IItemHandler itemHandlerFor(Direction side) {
        if (side == null) return null;
        Direction inputFace = getBlockState().getValue(KineticOreSeparatorBlock.FACING);
        if (side == inputFace) return input;
        if (side == inputFace.getOpposite()) return output;
        return null;
    }

    public boolean canAccept(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return input.insertItem(INPUT_SLOT, stack.copyWithCount(1), true).isEmpty();
    }

    public ItemStack insertOne(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return input.insertItem(INPUT_SLOT, stack, false);
    }

    public boolean hasOutput() {
        for (int slot = 0; slot < output.getSlots(); slot++) {
            if (!output.getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    public ItemStack extractNextOutput() {
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack extracted = output.extractItem(slot, Integer.MAX_VALUE, false);
            if (!extracted.isEmpty()) return extracted;
        }
        return ItemStack.EMPTY;
    }

    public void dropContents() {
        if (level == null || level.isClientSide()) return;
        dropHandler(input);
        dropHandler(output);
    }

    private void dropHandler(ItemStackHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            while (!handler.getStackInSlot(slot).isEmpty()) {
                ItemStack stack = handler.extractItem(slot, Integer.MAX_VALUE, false);
                if (stack.isEmpty()) break;
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;
        processKineticWork();
    }

    @Override
    protected boolean isNoisy() {
        return false;
    }

    private void processKineticWork() {
        ItemStack source = input.getStackInSlot(INPUT_SLOT);
        MaterialPartitionSpec process = findProcess(source);
        if (process == null) {
            if (workDone != 0.0F) workDone = 0.0F;
            return;
        }
        if (!isSpeedRequirementFulfilled()) return;

        List<ItemStack> results = outputsFor(source, process);
        if (results.isEmpty() || !canStore(results)) return;

        float requiredWork = Math.max(1.0F, process.getDurationTicks() * 16.0F);
        workDone += Math.min(MAX_WORK_PER_TICK, Math.abs(getSpeed()));
        if (workDone < requiredWork) {
            if (++unsavedWorkTicks >= 20) {
                unsavedWorkTicks = 0;
                setChanged();
            }
            return;
        }

        ItemStack consumed = input.extractItem(INPUT_SLOT, 1, false);
        if (consumed.isEmpty()) return;
        insertOutputs(results);
        workDone = 0.0F;
        unsavedWorkTicks = 0;
        setChanged();
        level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.55F, 0.85F);
    }

    private static MaterialPartitionSpec findProcess(ItemStack stack) {
        MaterialBatchContents batch = stack.get(ChemComponents.MATERIAL_BATCH.get());
        if (batch == null) return null;
        for (MaterialPartitionSpec process : PROCESSES) {
            if (process.getInputMaterialId().equals(batch.materialId())
                && process.getInputForm().equals(batch.form())) {
                return process;
            }
        }
        return null;
    }

    private static List<ItemStack> outputsFor(ItemStack source, MaterialPartitionSpec process) {
        MaterialBatchContents batch = source.get(ChemComponents.MATERIAL_BATCH.get());
        if (batch == null) return List.of();
        OreSeparationResult separation = OreSeparation.separate(
            new MaterialMassComposition(batch.primaryMassMicrograms(), batch.impurityMassMicrograms())
        );

        List<ItemStack> results = new ArrayList<>();
        ItemStack primary = ChemItems.materialStack(new MaterialBatchContents(
            batch.materialId(),
            process.getPrimaryOutputForm(),
            separation.getPrimaryMassMicrograms()
        ));
        if (primary.isEmpty()) return List.of();
        results.add(primary);

        for (Map.Entry<String, Long> impurity : separation.getSeparatedImpurityMassMicrograms().entrySet()) {
            ItemStack tailing = ChemItems.materialStack(new MaterialBatchContents(
                impurity.getKey(),
                process.getImpurityOutputForm(),
                impurity.getValue()
            ));
            if (tailing.isEmpty()) return List.of();
            results.add(tailing);
        }
        return List.copyOf(results);
    }

    /** Simulates exact component-aware stack placement before any input is removed. */
    private boolean canStore(List<ItemStack> candidates) {
        List<ItemStack> projected = new ArrayList<>(output.getSlots());
        for (int slot = 0; slot < output.getSlots(); slot++) {
            projected.add(output.getStackInSlot(slot).copy());
        }

        for (ItemStack candidate : candidates) {
            int remaining = candidate.getCount();
            for (int slot = 0; slot < projected.size() && remaining > 0; slot++) {
                ItemStack existing = projected.get(slot);
                if (!ItemStack.isSameItemSameComponents(existing, candidate)) continue;
                int inserted = Math.min(remaining, Math.max(0, existing.getMaxStackSize() - existing.getCount()));
                if (inserted > 0) {
                    projected.set(slot, existing.copyWithCount(existing.getCount() + inserted));
                    remaining -= inserted;
                }
            }
            if (remaining > 0) {
                for (int slot = 0; slot < projected.size() && remaining > 0; slot++) {
                    if (!projected.get(slot).isEmpty()) continue;
                    int inserted = Math.min(remaining, candidate.getMaxStackSize());
                    projected.set(slot, candidate.copyWithCount(inserted));
                    remaining -= inserted;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private void insertOutputs(List<ItemStack> results) {
        for (ItemStack result : results) {
            int remaining = result.getCount();
            for (int slot = 0; slot < output.getSlots() && remaining > 0; slot++) {
                ItemStack existing = output.getStackInSlot(slot);
                if (!ItemStack.isSameItemSameComponents(existing, result)) continue;
                int inserted = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
                if (inserted <= 0) continue;
                output.setStackInSlot(slot, existing.copyWithCount(existing.getCount() + inserted));
                remaining -= inserted;
            }
            for (int slot = 0; slot < output.getSlots() && remaining > 0; slot++) {
                if (!output.getStackInSlot(slot).isEmpty()) continue;
                int inserted = Math.min(remaining, result.getMaxStackSize());
                output.setStackInSlot(slot, result.copyWithCount(inserted));
                remaining -= inserted;
            }
            if (remaining != 0) {
                throw new IllegalStateException("Kinetic separator output capacity changed during a completed process");
            }
        }
    }

    private void inventoryChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Input", input.serializeNBT(registries));
        tag.put("Output", output.serializeNBT(registries));
        tag.putFloat("WorkDone", workDone);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Input")) input.deserializeNBT(registries, tag.getCompound("Input"));
        if (tag.contains("Output")) output.deserializeNBT(registries, tag.getCompound("Output"));
        workDone = Math.max(0.0F, tag.getFloat("WorkDone"));
    }
}
