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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Two independently installed small holders share one host cell. No ticking or chemical processing. */
public final class LaboratoryHolderBlock extends BaseEntityBlock {
    public static final BooleanProperty RACK = BooleanProperty.create("rack");
    public static final BooleanProperty TRAY = BooleanProperty.create("tray");
    private static final MapCodec<LaboratoryHolderBlock> CODEC = simpleCodec(LaboratoryHolderBlock::new);
    // Include the reserved vessel space in selection/collision, so inserted vials can be targeted.
    private static final VoxelShape RACK_SHAPE = Block.box(1, 0, 1, 7, 10, 15);
    private static final VoxelShape TRAY_SHAPE = Block.box(9, 0, 1, 15, 6, 15);

    public LaboratoryHolderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RACK, false).setValue(TRAY, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(RACK, TRAY); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LaboratoryHolderBlockEntity(pos, state); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.or(state.getValue(RACK) ? RACK_SHAPE : Shapes.empty(), state.getValue(TRAY) ? TRAY_SHAPE : Shapes.empty());
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
            var footprint = new io.github.antonovichkorp.chemmod.core.equipment.Footprint(rack ? 1 : 9, 1, rack ? 7 : 15, 15);
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
        double x = (hit.getLocation().x - pos.getX()) * 16;
        double z = (hit.getLocation().z - pos.getZ()) * 16;
        if (x <= 7 && state.getValue(RACK)) return Math.clamp((int) Math.round((z - 2.5) / 2.2), 0, 5);
        if (x >= 9 && state.getValue(TRAY)) return 6 + Math.clamp((int) Math.round((z - 3) / 3.3), 0, 3);
        return -1;
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!held.is(ChemItems.SUBSTANCE_VIAL.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
        if (!player.mayBuild() || slot < 0) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof LaboratoryHolderBlockEntity holder) {
            ItemStack taken = holder.extract(slot);
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
