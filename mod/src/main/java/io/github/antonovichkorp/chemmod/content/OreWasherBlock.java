package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.material.MaterialProcessExports;
import io.github.antonovichkorp.chemmod.core.material.MaterialTransitionSpec;
import io.github.antonovichkorp.chemmod.core.material.OreWashingQuality;
import io.github.antonovichkorp.chemmod.core.material.WashedComposition;
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
 * Water-gated beneficiation adapter. It intentionally consumes the recorded water
 * sample instead of inspecting the biome at processing time, so quality is fully
 * server-authoritative and reproducible from the vial component.
 */
public final class OreWasherBlock extends Block {
    private static final String SILICATE_GANGUE = "chemmod:silicate_gangue";
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
        if (held.getCount() < transition.getInputCount()) {
            return needsCount(level, player, transition);
        }

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

        MaterialBatchContents washedBatch = washedBatch(batch, transition.getOutputForm(), transition.getOutputMassMicrograms(), water);
        ItemStack output = ChemItems.materialStack(washedBatch);
        output.setCount(transition.getOutputCount());
        if (output.isEmpty()) return ItemInteractionResult.FAIL;

        if (held.getCount() == transition.getInputCount()) {
            player.setItemInHand(hand, output);
        } else {
            held.shrink(transition.getInputCount());
            if (!player.getInventory().add(output)) player.drop(output, false);
        }
        consumeWaterVial(player, waterHand, waterVial);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7F, 1.25F);
        player.displayClientMessage(Component.translatable(
            "message.chemmod.washer.result",
            Component.translatable("water_profile.chemmod." + water.waterSample().profile()),
            formatPercent(batch.purityPpm()),
            formatPercent(washedBatch.purityPpm())
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

    private static void consumeWaterVial(Player player, InteractionHand waterHand, ItemStack waterVial) {
        if (player.getAbilities().instabuild) return;
        ItemStack empty = new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
        if (waterVial.getCount() == 1) {
            player.setItemInHand(waterHand, empty);
        } else {
            waterVial.shrink(1);
            if (!player.getInventory().add(empty)) player.drop(empty, false);
        }
    }

    /**
     * Keeps the batch's nominal mass exactly constant. The current material model
     * tracks gangue as a measured concentration, so washing retains a deterministic
     * fraction based on the recorded water analysis; explicit tailings are reserved
     * for the later multi-output separator milestone.
     */
    static MaterialBatchContents washedBatch(
        MaterialBatchContents input,
        String outputForm,
        long outputMassMicrograms,
        SubstanceContents water
    ) {
        WashedComposition composition = OreWashingQuality.wash(
            input.purityPpm(),
            input.impuritiesPpm(),
            water.waterSample().totalImpuritiesPpm(),
            SILICATE_GANGUE
        );
        return new MaterialBatchContents(
            MaterialBatchContents.CURRENT_SCHEMA,
            input.materialId(),
            outputForm,
            outputMassMicrograms,
            composition.getPurityPpm(),
            composition.getImpuritiesPpm()
        );
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
