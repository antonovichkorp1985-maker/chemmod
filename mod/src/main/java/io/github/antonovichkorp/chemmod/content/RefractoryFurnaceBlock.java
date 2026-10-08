package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.material.MaterialMassComposition;
import io.github.antonovichkorp.chemmod.core.material.MaterialProcessExports;
import io.github.antonovichkorp.chemmod.core.material.MaterialTransitionSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.ItemInteractionResult;

import java.util.List;

/**
 * Charcoal-fired melting adapter. Compatible molten crucibles are deliberately
 * accumulated here so physically separated sub-ingot copper batches can later be
 * cast without manufacturing mass.
 */
public final class RefractoryFurnaceBlock extends Block {
    private static final List<MaterialTransitionSpec> TRANSITIONS =
        MaterialProcessExports.bundledMeltingTransitions();
    private static final long MAX_CRUCIBLE_MASS_MICROGRAMS = 9_000_000_000L;

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
        if (held.getCount() < transition.getInputCount()) return needsCount(level, player, transition);
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        ItemStack fuel = player.getItemInHand(otherHand(hand));
        if (!fuel.is(Items.CHARCOAL)) {
            player.displayClientMessage(Component.translatable("message.chemmod.furnace.needs_charcoal"), true);
            return ItemInteractionResult.FAIL;
        }

        int moltenSlot = findCompatibleMoltenCrucible(player, batch.materialId());
        int emptyCrucibleSlot = moltenSlot < 0 ? findEmptyCrucible(player) : -1;
        if (moltenSlot < 0 && emptyCrucibleSlot < 0) {
            player.displayClientMessage(Component.translatable("message.chemmod.furnace.needs_crucible"), true);
            return ItemInteractionResult.FAIL;
        }

        MaterialMassComposition combined = new MaterialMassComposition(
            batch.primaryMassMicrograms(), batch.impurityMassMicrograms()
        );
        if (moltenSlot >= 0) {
            MaterialBatchContents existing = player.getInventory().getItem(moltenSlot)
                .get(ChemComponents.MATERIAL_BATCH.get());
            combined = combined.mergedWith(new MaterialMassComposition(
                existing.primaryMassMicrograms(), existing.impurityMassMicrograms()
            ));
        }
        if (combined.getTotalMassMicrograms() > MAX_CRUCIBLE_MASS_MICROGRAMS) {
            player.displayClientMessage(Component.translatable(
                "message.chemmod.furnace.crucible_full",
                formatMass(MAX_CRUCIBLE_MASS_MICROGRAMS)
            ), true);
            return ItemInteractionResult.FAIL;
        }

        double meltingPoint = transition.getMinimumTemperatureKelvin() == null
            ? 1_373.15
            : transition.getMinimumTemperatureKelvin();
        int temperatureMillikelvin = (int) Math.round(meltingPoint * 1000.0);
        MaterialBatchContents combinedInput = new MaterialBatchContents(
            batch.materialId(),
            batch.form(),
            combined.getTotalMassMicrograms(),
            combined.getPrimaryMassMicrograms(),
            combined.getImpurityMassMicrograms()
        );
        ItemStack output = ChemItems.moltenStack(
            combinedInput,
            transition.getOutputForm(),
            combined.getTotalMassMicrograms(),
            temperatureMillikelvin
        );
        if (output.isEmpty()) return ItemInteractionResult.FAIL;

        if (!player.getAbilities().instabuild) {
            held.shrink(transition.getInputCount());
            fuel.shrink(1);
            if (moltenSlot >= 0) {
                player.getInventory().getItem(moltenSlot).shrink(1);
            } else {
                player.getInventory().getItem(emptyCrucibleSlot).shrink(1);
            }
        }
        give(player, output);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 0.7F);
        return ItemInteractionResult.SUCCESS;
    }

    private static InteractionHand otherHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
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

    private static int findEmptyCrucible(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ChemItems.CERAMIC_CRUCIBLE.get())) return slot;
        }
        return -1;
    }

    private static int findCompatibleMoltenCrucible(Player player, String materialId) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            MaterialBatchContents batch = stack.get(ChemComponents.MATERIAL_BATCH.get());
            if (batch != null && "LIQUID".equals(batch.form()) && materialId.equals(batch.materialId())) {
                return slot;
            }
        }
        return -1;
    }

    private static String formatMass(long micrograms) {
        return String.format(java.util.Locale.ROOT, "%.3f", micrograms / 1_000_000.0);
    }

    private static MaterialTransitionSpec findTransition(MaterialBatchContents batch) {
        for (MaterialTransitionSpec transition : TRANSITIONS) {
            if (transition.getMaterialId().equals(batch.materialId())
                && transition.getInputForm().equals(batch.form())
                && (transition.getInputMassMicrograms() == batch.massMicrograms() || isMassScalable(transition))) {
                return transition;
            }
        }
        return null;
    }

    private static boolean isMassScalable(MaterialTransitionSpec transition) {
        return transition.getInputCount() == 1
            && transition.getOutputCount() == 1
            && transition.getInputMassMicrograms() == transition.getOutputMassMicrograms();
    }
}
