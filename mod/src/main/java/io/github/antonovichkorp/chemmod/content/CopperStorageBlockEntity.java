package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server-persistent material state for a placed nine-ingot copper storage block. */
public final class CopperStorageBlockEntity extends BlockEntity {
    public static final String MATERIAL_ID = "chemmod:copper";
    public static final String FORM = "BLOCK";
    public static final long MASS_MICROGRAMS = 9_000_000_000L;

    private MaterialBatchContents batch = defaultBatch();

    public CopperStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ChemBlockEntities.COPPER_STORAGE_BLOCK.get(), pos, state);
    }

    public static MaterialBatchContents defaultBatch() {
        return new MaterialBatchContents(MATERIAL_ID, FORM, MASS_MICROGRAMS);
    }

    public MaterialBatchContents batch() {
        return batch;
    }

    public void setBatch(MaterialBatchContents value) {
        validateBatch(value);
        batch = value;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag batchTag = new CompoundTag();
        batchTag.putInt("schema_version", batch.schemaVersion());
        batchTag.putString("material_id", batch.materialId());
        batchTag.putString("form", batch.form());
        batchTag.putLong("mass_micrograms", batch.massMicrograms());
        batchTag.putLong("primary_mass_micrograms", batch.primaryMassMicrograms());
        CompoundTag impurities = new CompoundTag();
        batch.impurityMassMicrograms().forEach(impurities::putLong);
        batchTag.put("impurity_mass_micrograms", impurities);
        tag.put("material_batch", batchTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        batch = readBatch(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static MaterialBatchContents readBatch(CompoundTag tag) {
        if (!tag.contains("material_batch", Tag.TAG_COMPOUND)) return defaultBatch();
        CompoundTag batchTag = tag.getCompound("material_batch");
        try {
            MaterialBatchContents value;
            if (batchTag.contains("primary_mass_micrograms", Tag.TAG_LONG)) {
                Map<String, Long> impurities = new LinkedHashMap<>();
                if (batchTag.contains("impurity_mass_micrograms", Tag.TAG_COMPOUND)) {
                    CompoundTag impurityTag = batchTag.getCompound("impurity_mass_micrograms");
                    for (String key : impurityTag.getAllKeys()) impurities.put(key, impurityTag.getLong(key));
                }
                value = new MaterialBatchContents(
                    batchTag.getInt("schema_version"),
                    batchTag.getString("material_id"),
                    batchTag.getString("form"),
                    batchTag.getLong("mass_micrograms"),
                    batchTag.getLong("primary_mass_micrograms"),
                    impurities
                );
            } else {
                Map<String, Integer> legacyImpurities = new LinkedHashMap<>();
                if (batchTag.contains("impurities_ppm", Tag.TAG_COMPOUND)) {
                    CompoundTag impurityTag = batchTag.getCompound("impurities_ppm");
                    for (String key : impurityTag.getAllKeys()) legacyImpurities.put(key, impurityTag.getInt(key));
                }
                value = new MaterialBatchContents(
                    batchTag.getInt("schema_version"),
                    batchTag.getString("material_id"),
                    batchTag.getString("form"),
                    batchTag.getLong("mass_micrograms"),
                    batchTag.getInt("purity_ppm"),
                    legacyImpurities
                );
            }
            validateBatch(value);
            return value;
        } catch (IllegalArgumentException exception) {
            return defaultBatch();
        }
    }

    private static void validateBatch(MaterialBatchContents value) {
        if (!MATERIAL_ID.equals(value.materialId())
            || !FORM.equals(value.form())
            || value.massMicrograms() != MASS_MICROGRAMS) {
            throw new IllegalArgumentException("Copper storage block needs exactly nine kilograms of canonical copper");
        }
    }
}
