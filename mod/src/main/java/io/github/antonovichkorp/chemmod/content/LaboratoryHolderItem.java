package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.Direction;
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
    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        if (!state.is(getBlock())) return super.useOn(context);
        var player = context.getPlayer();
        if (state.getValue(part) || player == null || !player.mayBuild()
            || !player.mayUseItemAt(pos, Direction.UP, context.getItemInHand())) return InteractionResult.FAIL;
        var replacement = state.setValue(part, true);
        if (!LaboratoryHolderBlock.validArrangement(replacement) || !replacement.canSurvive(level, pos) || !level.isUnobstructed(replacement, pos,
            net.minecraft.world.phys.shapes.CollisionContext.of(player))) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            if (!level.setBlock(pos, replacement, 3)) return InteractionResult.FAIL;
            if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WOOD_PLACE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
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
