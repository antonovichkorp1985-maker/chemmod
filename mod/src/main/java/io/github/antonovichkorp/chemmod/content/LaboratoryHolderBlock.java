package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two independently installed small holders share one host cell. No ticking or chemical processing. */
public final class LaboratoryHolderBlock extends BaseEntityBlock {
    public static final BooleanProperty RACK = BooleanProperty.create("rack");
    public static final BooleanProperty TRAY = BooleanProperty.create("tray");
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
        net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    private static final MapCodec<LaboratoryHolderBlock> CODEC = simpleCodec(LaboratoryHolderBlock::new);
    public LaboratoryHolderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RACK, false).setValue(TRAY, false).setValue(FACING, Direction.NORTH));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(RACK, TRAY, FACING); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LaboratoryHolderBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return LaboratoryHolderGeometry.shape(state.getValue(FACING), state.getValue(RACK), state.getValue(TRAY));
    }
    @Override protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        // Initial adapter deliberately accepts only a full-height sturdy top, not arbitrary furniture/bit geometry.
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
            LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
    /** First world adapter: 1/16-block units and two fixed, non-overlapping holder footprints. */
    public static boolean validArrangement(BlockState state) {
        var layout = new io.github.antonovichkorp.chemmod.core.equipment.EquipmentLayout();
        var support = java.util.List.of(new io.github.antonovichkorp.chemmod.core.equipment.HorizontalContact(
            new io.github.antonovichkorp.chemmod.core.equipment.Footprint(0, 0, 16, 16), 0));
        for (boolean rack : new boolean[] { true, false }) {
            if (!state.getValue(rack ? RACK : TRAY)) continue;
            var footprint = LaboratoryHolderGeometry.footprint(state.getValue(FACING), rack);
            var geometry = new io.github.antonovichkorp.chemmod.core.equipment.EquipmentGeometry(
                java.util.List.of(new io.github.antonovichkorp.chemmod.core.equipment.OccupiedBox(footprint, 0, rack ? 10 : 6)),
                java.util.List.of(new io.github.antonovichkorp.chemmod.core.equipment.HorizontalContact(footprint, 0)));
            var result = layout.place(new io.github.antonovichkorp.chemmod.core.equipment.PlacedEquipment(
                rack ? "rack" : "tray", geometry), support, java.util.List.of());
            if (!(result instanceof io.github.antonovichkorp.chemmod.core.equipment.PlacementResult.Accepted accepted)) return false;
            layout = accepted.getLayout();
        }
        return true;
    }

    public static int slotAt(BlockPos pos, BlockHitResult hit, BlockState state) {
        var worldLocal = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return LaboratoryHolderGeometry.slotAt(state.getValue(FACING), worldLocal, state.getValue(RACK), state.getValue(TRAY));
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!held.is(ChemItems.SUBSTANCE_VIAL.get())) return held.isEmpty()
            ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        int slot = slotAt(pos, hit, state);
        if (!player.mayBuild() || slot < 0) return ItemInteractionResult.FAIL;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof LaboratoryHolderBlockEntity holder) || !holder.insert(slot, held)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.chemmod.holder.occupied"), true);
            return ItemInteractionResult.FAIL;
        }
        // A physical stored vial is transferred even in creative; this interaction must not clone contents.
        return ItemInteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int slot = slotAt(pos, hit, state);
        if (!player.mayBuild() || !player.getMainHandItem().isEmpty() || slot < 0) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof LaboratoryHolderBlockEntity holder) {
            ItemStack taken = player.isShiftKeyDown() ? holder.takeEmptyHolder(slot < 6) : holder.extract(slot);
            if (player.isShiftKeyDown() && taken.isEmpty()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.chemmod.holder.empty_first"), true);
            }
            if (!taken.isEmpty()) {
                if (!player.getInventory().add(taken)) player.drop(taken, false);
            }
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide()
            && level.getBlockEntity(pos) instanceof LaboratoryHolderBlockEntity holder) holder.dropAll();
        super.onRemove(state, level, pos, replacement, moved);
    }
}
