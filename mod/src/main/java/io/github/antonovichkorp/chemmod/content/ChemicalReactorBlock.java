package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Opens the first reactor's real server inventory. Its dedicated, localized
 * menu exposes restricted target/co-reactant/output/catalyst roles instead of
 * a synthetic command or an unrestricted chest inventory.
 */
public final class ChemicalReactorBlock extends BaseEntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public ChemicalReactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChemicalReactorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        return level.isClientSide()
            ? null
            : createTickerHelper(type, ChemBlockEntities.CHEMICAL_REACTOR.get(), ChemicalReactorBlockEntity::serverTick);
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
        return open(level, pos, player);
    }

    @Override
    public InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hit
    ) {
        return level.isClientSide() ? InteractionResult.SUCCESS : openServer(level, pos, player);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
            && level.getBlockEntity(pos) instanceof ChemicalReactorBlockEntity reactor) {
            reactor.setLastOperator(player);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level.getBlockEntity(pos) instanceof ChemicalReactorBlockEntity reactor) {
            Containers.dropContents(level, pos, reactor);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    private static ItemInteractionResult open(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        return openServer(level, pos, player) == InteractionResult.SUCCESS
            ? ItemInteractionResult.SUCCESS
            : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static InteractionResult openServer(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ChemicalReactorBlockEntity reactor)) {
            return InteractionResult.PASS;
        }
        reactor.setLastOperator(player);
        MenuProvider provider = new SimpleMenuProvider(
            (containerId, inventory, ignoredPlayer) -> new ChemicalReactorMenu(containerId, inventory, reactor),
            reactor.menuTitle()
        );
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(provider, buffer -> buffer.writeBlockPos(pos));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
