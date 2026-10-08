package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A placeable nine-ingot copper storage form. The block entity makes the batch
 * (mass, purity, and known impurities) survive placing, saving, mining, and pickup.
 */
public final class CopperStorageBlock extends Block implements EntityBlock {
    public CopperStorageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperStorageBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
        Level level,
        BlockPos pos,
        BlockState state,
        @Nullable LivingEntity placer,
        ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CopperStorageBlockEntity storage) {
            MaterialBatchContents batch = stack.get(ChemComponents.MATERIAL_BATCH.get());
            storage.setBatch(batch == null ? CopperStorageBlockEntity.defaultBatch() : batch);
        }
    }

    @Override
    public void playerDestroy(
        Level level,
        Player player,
        BlockPos pos,
        BlockState state,
        @Nullable BlockEntity blockEntity,
        ItemStack tool
    ) {
        if (!level.isClientSide()
            && !player.getAbilities().instabuild
            && blockEntity instanceof CopperStorageBlockEntity storage) {
            Block.popResource(level, pos, ChemItems.copperStorageBlockStack(storage.batch()));
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof CopperStorageBlockEntity storage) {
            return ChemItems.copperStorageBlockStack(storage.batch());
        }
        return ChemItems.copperStorageBlockStack(CopperStorageBlockEntity.defaultBatch());
    }
}
