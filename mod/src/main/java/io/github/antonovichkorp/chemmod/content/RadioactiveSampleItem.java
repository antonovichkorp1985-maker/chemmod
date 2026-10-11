package io.github.antonovichkorp.chemmod.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** A sealed calibration sample for the first radiation-gameplay acceptance path. */
public final class RadioactiveSampleItem extends Item {
    public RadioactiveSampleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        RadioactiveContents contents = stack.get(ChemComponents.RADIOACTIVE_CONTENTS.get());
        if (contents == null) {
            tooltip.add(Component.translatable("tooltip.chemmod.radiation.contents_missing")
                .withStyle(ChatFormatting.RED));
            return;
        }

        try {
            double activity = RadiationNuclides.activityBecquerel(contents);
            tooltip.add(Component.translatable(
                "tooltip.chemmod.radiation.nuclide",
                Component.translatable("nuclide.chemmod." + contents.nuclideId().substring("chemmod:".length()))
            ).withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable(
                "tooltip.chemmod.radiation.active_mass",
                String.format(Locale.ROOT, "%.6f", contents.activeMassMicrograms() / 1_000_000.0)
            ).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.chemmod.purity", "100.0000")
                .withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.translatable(
                "tooltip.chemmod.radiation.activity",
                String.format(Locale.ROOT, "%.3f", activity)
            ).withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable("tooltip.chemmod.radiation.sealed")
                .withStyle(ChatFormatting.DARK_AQUA));
            if (contents.secularEquilibrium()) {
                tooltip.add(Component.translatable("tooltip.chemmod.radiation.secular_equilibrium")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        } catch (IllegalArgumentException exception) {
            tooltip.add(Component.translatable("tooltip.chemmod.radiation.invalid", exception.getMessage())
                .withStyle(ChatFormatting.RED));
        }
    }
}
