package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.material.MaterialMassComposition;
import io.github.antonovichkorp.chemmod.core.material.MaterialMassSplit;
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

/**
 * Cools exactly one ingot mass from a molten batch. Larger accumulated crucibles
 * retain their physically exact molten remainder; sub-ingot batches must first be
 * accumulated in the refractory furnace.
 */
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
        MaterialTransitionSpec matching = batch == null ? null : matchingTransition(batch);
        MaterialTransitionSpec transition = matching != null && batch.massMicrograms() >= matching.getOutputMassMicrograms()
            ? matching
            : null;
        if (transition == null || temperature == null) {
            if (matching != null && !level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                    "message.chemmod.mold.needs_mass",
                    formatMass(matching.getOutputMassMicrograms())
                ), true);
                return ItemInteractionResult.FAIL;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (held.getCount() < transition.getInputCount()) return needsCount(level, player, transition);
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        Double maximum = transition.getMaximumTemperatureKelvin();
        if (maximum != null && temperature > Math.round(maximum * 1000.0)) {
            player.displayClientMessage(Component.translatable("message.chemmod.mold.too_hot"), true);
            return ItemInteractionResult.FAIL;
        }

        long ingotMass = transition.getOutputMassMicrograms();
        MaterialMassSplit split = new MaterialMassComposition(
            batch.primaryMassMicrograms(), batch.impurityMassMicrograms()
        ).split(ingotMass);
        ItemStack ingot = ChemItems.materialStack(new MaterialBatchContents(
            batch.materialId(),
            transition.getOutputForm(),
            ingotMass,
            split.getExtracted().getPrimaryMassMicrograms(),
            split.getExtracted().getImpurityMassMicrograms()
        ));
        if (ingot.isEmpty()) return ItemInteractionResult.FAIL;

        if (split.getRemainder() == null) {
            player.setItemInHand(hand, ingot);
            give(player, new ItemStack(ChemItems.CERAMIC_CRUCIBLE.get()));
        } else {
            MaterialMassComposition remainder = split.getRemainder();
            ItemStack residualMelt = ChemItems.moltenStack(
                new MaterialBatchContents(
                    batch.materialId(),
                    batch.form(),
                    remainder.getTotalMassMicrograms(),
                    remainder.getPrimaryMassMicrograms(),
                    remainder.getImpurityMassMicrograms()
                ),
                batch.form(),
                remainder.getTotalMassMicrograms(),
                temperature
            );
            if (residualMelt.isEmpty()) return ItemInteractionResult.FAIL;
            player.setItemInHand(hand, ingot);
            give(player, residualMelt);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.4F);
        return ItemInteractionResult.SUCCESS;
    }

    private static ItemInteractionResult needsCount(Level level, Player player, MaterialTransitionSpec transition) {
        if (!level.isClientSide()) {
            player.displayClientMessage(Component.translatable(
                "message.chemmod.process.needs_count", transition.getInputCount()
            ), true);
        }
        return ItemInteractionResult.FAIL;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static String formatMass(long micrograms) {
        return String.format(java.util.Locale.ROOT, "%.3f", micrograms / 1_000_000.0);
    }

    private static MaterialTransitionSpec matchingTransition(MaterialBatchContents batch) {
        for (MaterialTransitionSpec transition : TRANSITIONS) {
            if (transition.getMaterialId().equals(batch.materialId())
                && transition.getInputForm().equals(batch.form())
                && transition.getInputCount() == 1
                && transition.getOutputCount() == 1) {
                return transition;
            }
        }
        return null;
    }
}
