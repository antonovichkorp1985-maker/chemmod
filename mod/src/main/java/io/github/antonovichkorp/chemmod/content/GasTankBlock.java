package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A sealed gas tank. Touching an adjacent fire ignites the contents; using it reads out the
 * stored pressure so the player can see a tank climb toward its burst point.
 */
public class GasTankBlock extends Block {
    public GasTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide()) return;
        net.minecraft.world.level.block.state.BlockState neighbour = level.getBlockState(fromPos);
        boolean fire = neighbour.is(net.minecraft.world.level.block.Blocks.FIRE)
            || neighbour.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE);
        if (fire && level.getBlockEntity(pos) instanceof GasTankBlockEntity tank) {
            tank.ignite();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof GasTankBlockEntity tank) {
            player.displayClientMessage(Component.translatable(
                "message.chemmod.gas_tank.readout",
                String.format("%.0f", tank.pressurePascals() / 1000.0),
                String.format("%.0f", tank.vessel().temperatureKelvin())), true);
        }
        return InteractionResult.SUCCESS;
    }
}
