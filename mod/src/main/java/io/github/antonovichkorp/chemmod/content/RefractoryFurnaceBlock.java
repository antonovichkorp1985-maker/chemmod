package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.material.MaterialProcessExports;
import io.github.antonovichkorp.chemmod.core.material.MaterialTransitionSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/** Charcoal-fired, storage-free adapter for canonical single-batch melting processes. */
public final class RefractoryFurnaceBlock extends Block {
    private static final List<MaterialTransitionSpec> TRANSITIONS =
        MaterialProcessExports.bundledMeltingTransitions();

    public RefractoryFurnaceBlock(Properties properties) {
        super(properties);
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
        MaterialBatchContents batch = held.get(ChemComponents.MATERIAL_BATCH.get());
        MaterialTransitionSpec transition = batch == null ? null : findTransition(batch);
        if (transition == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        ItemStack fuel = player.getItemInHand(
            hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND
        );
        if (!fuel.is(Items.CHARCOAL)) {
            player.displayClientMessage(Component.translatable("message.chemmod.furnace.needs_charcoal"), true);
            return ItemInteractionResult.FAIL;
        }
        int crucibleSlot = findCrucible(player);
        if (crucibleSlot < 0) {
            player.displayClientMessage(Component.translatable("message.chemmod.furnace.needs_crucible"), true);
            return ItemInteractionResult.FAIL;
        }

        double meltingPoint = transition.getMinimumTemperatureKelvin() == null
            ? 1_373.15
            : transition.getMinimumTemperatureKelvin();
        int temperatureMillikelvin = (int) Math.round(meltingPoint * 1000.0);
        ItemStack output = ChemItems.moltenStack(
            batch,
            transition.getOutputForm(),
            transition.getOutputMassMicrograms(),
            temperatureMillikelvin
        );
        if (output.isEmpty()) return ItemInteractionResult.FAIL;

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            fuel.shrink(1);
            player.getInventory().getItem(crucibleSlot).shrink(1);
        }
        if (!player.getInventory().add(output)) player.drop(output, false);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 0.7F);
        return ItemInteractionResult.SUCCESS;
    }

    private static int findCrucible(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ChemItems.CERAMIC_CRUCIBLE.get())) return slot;
        }
        return -1;
    }

    private static MaterialTransitionSpec findTransition(MaterialBatchContents batch) {
        for (MaterialTransitionSpec transition : TRANSITIONS) {
            if (transition.getMaterialId().equals(batch.materialId())
                && transition.getInputForm().equals(batch.form())
                && transition.getInputMassMicrograms() == batch.massMicrograms()) return transition;
        }
        return null;
    }
}
