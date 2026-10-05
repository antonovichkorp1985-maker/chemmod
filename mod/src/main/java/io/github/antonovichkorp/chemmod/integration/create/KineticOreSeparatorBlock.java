package io.github.antonovichkorp.chemmod.integration.create;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A Create-driven adapter for the canonical dry-separation operation.
 *
 * The machine never has a static output recipe: it reads the persisted component
 * masses from the actual batch in its input and emits those exact masses through
 * ordinary NeoForge item handlers. The front face accepts input; the opposite
 * face exposes output; a vertical Create shaft supplies rotation.
 */
public final class KineticOreSeparatorBlock extends KineticBlock implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public KineticOreSeparatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return Direction.Axis.Y;
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis().isVertical();
    }

    @Override
    public IRotate.SpeedLevel getMinimumRequiredSpeedLevel() {
        return IRotate.SpeedLevel.MEDIUM;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticOreSeparatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        if (type != CreateCompatBlockEntities.KINETIC_ORE_SEPARATOR.get()) return null;
        return (tickerLevel, tickerPos, tickerState, blockEntity) ->
            ((KineticOreSeparatorBlockEntity) blockEntity).tick();
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack held,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (!(level.getBlockEntity(pos) instanceof KineticOreSeparatorBlockEntity separator)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (held.isEmpty()) {
            if (!separator.hasOutput()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
            ItemStack output = separator.extractNextOutput();
            if (!output.isEmpty()) {
                if (!player.getInventory().add(output)) player.drop(output, false);
                player.displayClientMessage(Component.translatable("message.chemmod.kinetic_separator.output"), true);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.35F, 0.9F);
            }
            return ItemInteractionResult.SUCCESS;
        }

        if (held.get(ChemComponents.MATERIAL_BATCH.get()) == null || !separator.canAccept(held)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        ItemStack remainder = separator.insertOne(held.copyWithCount(1));
        if (!remainder.isEmpty()) return ItemInteractionResult.FAIL;
        held.shrink(1);
        player.displayClientMessage(Component.translatable("message.chemmod.kinetic_separator.input"), true);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.45F, 0.9F);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState replacement,
        boolean moving
    ) {
        if (!moving && state.getBlock() != replacement.getBlock()
            && level.getBlockEntity(pos) instanceof KineticOreSeparatorBlockEntity separator) {
            separator.dropContents();
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
