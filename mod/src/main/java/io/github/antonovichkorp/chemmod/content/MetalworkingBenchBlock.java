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

/** Tool-selected, storage-free adapter for canonical plate, rod, and wire forming. */
public final class MetalworkingBenchBlock extends Block {
    private static final List<MaterialTransitionSpec> TRANSITIONS =
        MaterialProcessExports.bundledFormingTransitions();

    public MetalworkingBenchBlock(Properties properties) {
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
        if (batch == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        ItemStack tool = player.getItemInHand(
            hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND
        );
        String machineTag;
        if (tool.is(ChemItems.METALWORKING_HAMMER.get())) {
            machineTag = "chemmod:forming_hammer";
        } else if (tool.is(ChemItems.METALWORKING_CHISEL.get())) {
            machineTag = "chemmod:cutting";
        } else if (tool.is(ChemItems.DRAWPLATE.get())) {
            machineTag = "chemmod:drawing";
        } else {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.chemmod.bench.needs_tool"), true);
            }
            return ItemInteractionResult.FAIL;
        }

        MaterialTransitionSpec transition = findTransition(batch, machineTag);
        if (transition == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (held.getCount() < transition.getInputCount()) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable(
                    "message.chemmod.process.needs_count", transition.getInputCount()
                ), true);
            }
            return ItemInteractionResult.FAIL;
        }
        if (level.isClientSide()) return ItemInteractionResult.SUCCESS;

        ItemStack output = ChemItems.materialStack(
            batch,
            transition.getOutputForm(),
            transition.getOutputMassMicrograms()
        );
        output.setCount(transition.getOutputCount());
        if (output.isEmpty()) return ItemInteractionResult.FAIL;

        if (held.getCount() == transition.getInputCount()) {
            player.setItemInHand(hand, output);
        } else {
            held.shrink(transition.getInputCount());
            if (!player.getInventory().add(output)) player.drop(output, false);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.65F, 1.2F);
        return ItemInteractionResult.SUCCESS;
    }

    private static MaterialTransitionSpec findTransition(MaterialBatchContents batch, String machineTag) {
        for (MaterialTransitionSpec transition : TRANSITIONS) {
            if (transition.getMachineTag().equals(machineTag)
                && transition.getMaterialId().equals(batch.materialId())
                && transition.getInputForm().equals(batch.form())
                && transition.getInputMassMicrograms() == batch.massMicrograms()) return transition;
        }
        return null;
    }
}
