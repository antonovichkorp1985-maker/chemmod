package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import java.util.List;

/** Normal vanilla placement plus a second holder in the existing ChemMod host; never replace furniture. */
public final class LaboratoryHolderItem extends BlockItem {
    private final BooleanProperty part;
    public LaboratoryHolderItem(Properties properties, boolean rack) {
        super(ChemBlocks.LABORATORY_HOLDER.get(), properties);
        part = rack ? LaboratoryHolderBlock.RACK : LaboratoryHolderBlock.TRAY;
    }
    @Override protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = getBlock().defaultBlockState().setValue(part, true)
            .setValue(LaboratoryHolderBlock.FACING, context.getHorizontalDirection().getOpposite());
        return LaboratoryHolderBlock.validArrangement(state) && canPlace(context, state) ? state : null;
    }
    private boolean rack() { return part == LaboratoryHolderBlock.RACK; }

    /** Validate before any world mutation or vanilla placement component application. */
    private boolean validTransferStack(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(this) || stack.has(DataComponents.BLOCK_ENTITY_DATA)
            || stack.has(DataComponents.BLOCK_STATE) || stack.has(DataComponents.CONTAINER)) return false;
        var packed = stack.get(ChemComponents.PACKED_HOLDER.get());
        return packed == null || (stack.getCount() == 1 && stack.getMaxStackSize() == 1 && packed.rack() == rack());
    }
    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var before = level.getBlockState(pos);
        if (level.getBlockEntity(pos) != null) return false; // Do not replace another mod's persistent contents.
        if (!super.placeBlock(context, state)) return false;
        var packed = context.getItemInHand().get(ChemComponents.PACKED_HOLDER.get());
        if (!level.isClientSide() && packed != null) {
            if (!(level.getBlockEntity(pos) instanceof LaboratoryHolderBlockEntity holder)) {
                level.setBlock(pos, before, 3);
                return false;
            }
            if (!holder.restorePacked(rack(), packed)) {
                holder.cancelEmptyPlacement();
                level.setBlock(pos, before, 3);
                return false;
            }
        }
        return true;
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        ItemStack held = context.getItemInHand();
        if (!validTransferStack(held)) return InteractionResult.FAIL;
        var packed = held.get(ChemComponents.PACKED_HOLDER.get());
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        var player = context.getPlayer();
        if (!state.is(getBlock())) {
            var result = super.useOn(context);
            // Vanilla skips consumption in creative. A packed physical transfer must not clone matter.
            if (packed != null && !level.isClientSide() && result.consumesAction()
                && player != null && player.getAbilities().instabuild) held.shrink(1);
            return result;
        }
        if (state.getValue(part) || player == null || !player.mayBuild()
            || !player.mayUseItemAt(pos, Direction.UP, held)) return InteractionResult.FAIL;
        var replacement = state.setValue(part, true);
        if (!LaboratoryHolderBlock.validArrangement(replacement) || !replacement.canSurvive(level, pos) || !level.isUnobstructed(replacement, pos,
            net.minecraft.world.phys.shapes.CollisionContext.of(player))) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            if (!(level.getBlockEntity(pos) instanceof LaboratoryHolderBlockEntity holder)) return InteractionResult.FAIL;
            int start = rack() ? 0 : 6;
            for (int slot = start; slot < (rack() ? 6 : 10); slot++) if (!holder.contents(slot).isEmpty()) return InteractionResult.FAIL;
            if (packed != null && !holder.canRestorePacked(rack(), packed)) return InteractionResult.FAIL;
            if (!level.setBlock(pos, replacement, 3)) return InteractionResult.FAIL;
            if (packed != null && !holder.restorePacked(rack(), packed)) {
                level.setBlock(pos, state, 3);
                return InteractionResult.FAIL;
            }
            if (packed != null || !player.getAbilities().instabuild) held.shrink(1);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOD_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var packed = stack.get(ChemComponents.PACKED_HOLDER.get());
        if (packed != null) {
            tooltip.add(Component.translatable("tooltip.chemmod.holder.packed", packed.occupiedCount(), rack() ? 6 : 4));
            var slots = packed.copySlots();
            for (int i = 0; i < slots.size(); i++) {
                if (!slots.get(i).isEmpty()) tooltip.add(Component.translatable("tooltip.chemmod.holder.position", i + 1, slots.get(i).getHoverName()));
            }
        }
        tooltip.add(Component.translatable(part == LaboratoryHolderBlock.RACK ? "tooltip.chemmod.rack" : "tooltip.chemmod.tray"));
        tooltip.add(Component.translatable("tooltip.chemmod.holder.use"));
        tooltip.add(Component.translatable("tooltip.chemmod.holder.combine"));
        tooltip.add(Component.translatable("tooltip.chemmod.holder.remove"));
        tooltip.add(Component.translatable("tooltip.chemmod.holder.orientation"));
    }
    @Override public String getDescriptionId() {
        return part == LaboratoryHolderBlock.RACK ? "item.chemmod.test_tube_rack" : "item.chemmod.laboratory_tray";
    }
}
