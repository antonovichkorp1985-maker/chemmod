package io.github.antonovichkorp.chemmod.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * Handheld instrument. Its first calibration mode measures an isotope sample
 * in the opposite hand; field scanning is added only once world sources and
 * their shielding traversal are persisted server-side.
 */
public final class DosimeterItem extends Item {
    public DosimeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack dosimeter = player.getItemInHand(hand);
        ItemStack sample = player.getItemInHand(hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND
            : InteractionHand.MAIN_HAND);
        RadioactiveContents contents = sample.get(ChemComponents.RADIOACTIVE_CONTENTS.get());

        if (!level.isClientSide) {
            if (contents == null) {
                player.displayClientMessage(
                    Component.translatable("message.chemmod.dosimeter.no_sample").withStyle(ChatFormatting.YELLOW),
                    true
                );
            } else {
                try {
                    double activity = RadiationNuclides.activityBecquerel(contents);
                    player.displayClientMessage(
                        Component.translatable(
                            "message.chemmod.dosimeter.reading",
                            sample.getHoverName(),
                            String.format(Locale.ROOT, "%.3f", activity)
                        ).withStyle(ChatFormatting.RED),
                        true
                    );
                } catch (IllegalArgumentException exception) {
                    player.displayClientMessage(
                        Component.translatable("message.chemmod.dosimeter.invalid", exception.getMessage())
                            .withStyle(ChatFormatting.RED),
                        true
                    );
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(dosimeter, level.isClientSide);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        tooltip.add(Component.translatable("tooltip.chemmod.dosimeter.usage")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.chemmod.dosimeter.field_pending")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
