package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.material.MaterialMassComposition;
import io.github.antonovichkorp.chemmod.core.material.MaterialProcessExports;
import io.github.antonovichkorp.chemmod.core.material.MaterialTransitionSpec;
import io.github.antonovichkorp.chemmod.core.material.OreWashingQuality;
import io.github.antonovichkorp.chemmod.core.material.OreWashingResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Water-gated physical beneficiation adapter. It consumes the recorded water
 * sample rather than inspecting the biome at processing time, so quality is
 * reproducible from server-authoritative vial data. Removed gangue is emitted as
 * explicit tailings instead of silently becoming extra copper.
 */
public final class OreWasherBlock extends Block {
    private static final List<MaterialTransitionSpec> TRANSITIONS =
        MaterialProcessExports.bundledWashingTransitions();

    public OreWasherBlock(Properties properties) {
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

        InteractionHand waterHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack waterVial = player.getItemInHand(waterHand);
        SubstanceContents water = waterVial.get(ChemComponents.SUBSTANCE.get());
        if (water == null || !"O".equals(water.structure()) || !water.waterSample().isAnalyzed()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.chemmod.washer.needs_water_sample"), true);
            }
            return ItemInteractionResult.FAIL;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        OreWashingResult result = OreWashingQuality.wash(
            new MaterialMassComposition(batch.primaryMassMicrograms(), batch.impurityMassMicrograms()),
            water.waterSample().totalImpuritiesPpm()
        );
        MaterialBatchContents washedBatch = washedBatch(batch, transition.getOutputForm(), result);
        ItemStack concentrate = ChemItems.materialStack(washedBatch);
        concentrate.setCount(transition.getOutputCount());
        List<ItemStack> tailings = tailingStacks(result.tailingsMassMicrograms());
        if (concentrate.isEmpty() || tailings.stream().anyMatch(ItemStack::isEmpty)) return ItemInteractionResult.FAIL;

        replaceOrConsumeInput(player, hand, held, transition.getInputCount(), concentrate);
        consumeWaterVial(player, waterHand, waterVial);
        tailings.forEach(stack -> give(player, stack));
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7F, 1.25F);
        player.displayClientMessage(Component.translatable(
            "message.chemmod.washer.result",
            Component.translatable("water_profile.chemmod." + water.waterSample().profile()),
            formatPercent(batch.purityPpm()),
            formatPercent(washedBatch.purityPpm()),
            formatMass(result.getTailingsTotalMassMicrograms())
        ), true);
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

    private static void replaceOrConsumeInput(
        Player player,
        InteractionHand hand,
        ItemStack input,
        int inputCount,
        ItemStack output
    ) {
        if (input.getCount() == inputCount) {
            player.setItemInHand(hand, output);
        } else {
            input.shrink(inputCount);
            give(player, output);
        }
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void consumeWaterVial(Player player, InteractionHand waterHand, ItemStack waterVial) {
        if (player.getAbilities().instabuild) return;
        ItemStack empty = new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
        if (waterVial.getCount() == 1) {
            player.setItemInHand(waterHand, empty);
        } else {
            waterVial.shrink(1);
            give(player, empty);
        }
    }

    private static List<ItemStack> tailingStacks(Map<String, Long> tailingsMasses) {
        List<ItemStack> stacks = new ArrayList<>();
        tailingsMasses.forEach((materialId, mass) -> stacks.add(ChemItems.materialStack(
            new MaterialBatchContents(materialId, "DUST", mass)
        )));
        return stacks;
    }

    static MaterialBatchContents washedBatch(
        MaterialBatchContents input,
        String outputForm,
        OreWashingResult result
    ) {
        MaterialMassComposition concentrate = result.getConcentrate();
        return new MaterialBatchContents(
            input.materialId(),
            outputForm,
            concentrate.getTotalMassMicrograms(),
            concentrate.getPrimaryMassMicrograms(),
            concentrate.getImpurityMassMicrograms()
        );
    }

    private static String formatPercent(int ppm) {
        return String.format(java.util.Locale.ROOT, "%.4f", ppm / 10_000.0);
    }

    private static String formatMass(long micrograms) {
        return String.format(java.util.Locale.ROOT, "%.3f", micrograms / 1_000_000.0);
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
