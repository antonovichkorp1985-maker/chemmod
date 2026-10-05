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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/** Cools a molten single-material batch into the catalog's cast output form. */
public final class IngotMoldBlock extends Block {
    private static final List<MaterialTransitionSpec> TRANSITIONS =
        MaterialProcessExports.bundledCastingTransitions();

    public IngotMoldBlock(Properties properties) {
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
        Integer temperature = held.get(ChemComponents.MATERIAL_TEMPERATURE.get());
        MaterialTransitionSpec transition = batch == null ? null : findTransition(batch);
        if (transition == null || temperature == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        Double maximum = transition.getMaximumTemperatureKelvin();
        if (maximum != null && temperature > Math.round(maximum * 1000.0)) {
            player.displayClientMessage(Component.translatable("message.chemmod.mold.too_hot"), true);
            return ItemInteractionResult.FAIL;
        }

        ItemStack output = ChemItems.materialStack(
            batch,
            transition.getOutputForm(),
            transition.getOutputMassMicrograms()
        );
        if (output.isEmpty()) return ItemInteractionResult.FAIL;

        player.setItemInHand(hand, output);
        ItemStack emptyCrucible = new ItemStack(ChemItems.CERAMIC_CRUCIBLE.get());
        if (!player.getInventory().add(emptyCrucible)) player.drop(emptyCrucible, false);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
        return ItemInteractionResult.SUCCESS;
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
