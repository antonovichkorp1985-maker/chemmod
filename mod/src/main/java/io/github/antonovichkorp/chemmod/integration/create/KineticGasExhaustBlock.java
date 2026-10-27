package io.github.antonovichkorp.chemmod.integration.create;

import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Create-driven mechanical gas exhaust. A connected vertical shaft is its only power input. */
public final class KineticGasExhaustBlock extends KineticBlock implements EntityBlock {
    public KineticGasExhaustBlock(Properties properties) {
        super(properties);
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
        return IRotate.SpeedLevel.SLOW;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticGasExhaustBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        if (type != CreateCompatBlockEntities.KINETIC_GAS_EXHAUST.get()) return null;
        return (tickerLevel, tickerPos, tickerState, blockEntity) ->
            ((KineticGasExhaustBlockEntity) blockEntity).tick();
    }
}
