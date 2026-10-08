package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.material.MaterialMassComposition;
import io.github.antonovichkorp.chemmod.core.material.MaterialPartitionSpec;
import io.github.antonovichkorp.chemmod.core.material.MaterialProcessExports;
import io.github.antonovichkorp.chemmod.core.material.OreSeparation;
import io.github.antonovichkorp.chemmod.core.material.OreSeparationResult;
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
 * Low-throughput dry mineral separator. It materializes the exact copper and
 * remaining gangue masses already measured in a washed concentrate. Future
 * kinetic adapters may drive the same canonical partition process faster; this
 * block deliberately owns no foreign power API.
 */
public final class OreSeparatorBlock extends Block {
    private static final List<MaterialPartitionSpec> PROCESSES =
        MaterialProcessExports.bundledPartitionProcesses();

    public OreSeparatorBlock(Properties properties) {
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
        MaterialPartitionSpec process = batch == null ? null : findProcess(batch);
        if (process == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        OreSeparationResult result = OreSeparation.separate(
            new MaterialMassComposition(batch.primaryMassMicrograms(), batch.impurityMassMicrograms())
        );
        ItemStack primary = ChemItems.materialStack(new MaterialBatchContents(
            batch.materialId(),
            process.getPrimaryOutputForm(),
            result.getPrimaryMassMicrograms()
        ));
        List<ItemStack> gangue = impurityStacks(result.getSeparatedImpurityMassMicrograms(), process.getImpurityOutputForm());
        if (primary.isEmpty() || gangue.stream().anyMatch(ItemStack::isEmpty)) return ItemInteractionResult.FAIL;

        if (held.getCount() == 1) {
            player.setItemInHand(hand, primary);
        } else {
            held.shrink(1);
            give(player, primary);
        }
        gangue.forEach(stack -> give(player, stack));
        level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.85F, 0.9F);
        player.displayClientMessage(Component.translatable(
            "message.chemmod.separator.result",
            formatMass(result.getPrimaryMassMicrograms()),
            formatMass(result.getSeparatedTotalMassMicrograms())
        ), true);
        return ItemInteractionResult.SUCCESS;
    }

    private static List<ItemStack> impurityStacks(Map<String, Long> impurities, String outputForm) {
        List<ItemStack> stacks = new ArrayList<>();
        impurities.forEach((materialId, mass) -> stacks.add(ChemItems.materialStack(
            new MaterialBatchContents(materialId, outputForm, mass)
        )));
        return stacks;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static String formatMass(long micrograms) {
        return String.format(java.util.Locale.ROOT, "%.3f", micrograms / 1_000_000.0);
    }

    private static MaterialPartitionSpec findProcess(MaterialBatchContents batch) {
        for (MaterialPartitionSpec process : PROCESSES) {
            if (process.getInputMaterialId().equals(batch.materialId())
                && process.getInputForm().equals(batch.form())) {
                return process;
            }
        }
        return null;
    }
}
