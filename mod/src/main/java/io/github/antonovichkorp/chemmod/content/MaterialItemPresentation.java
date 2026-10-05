package io.github.antonovichkorp.chemmod.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** Shared localized name and batch tooltip for canonical material items, including storage blocks. */
final class MaterialItemPresentation {
    private MaterialItemPresentation() {}

    static Component name(String materialPath, String formName) {
        return Component.translatable(
            "item.chemmod.material_form",
            Component.translatable("material.chemmod." + materialPath),
            Component.translatable("material_form.chemmod." + formName)
        );
    }

    static void appendHoverText(
        ItemStack stack,
        Item.TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        MaterialBatchContents batch = stack.get(ChemComponents.MATERIAL_BATCH.get());
        if (batch == null) {
            tooltip.add(Component.translatable("tooltip.chemmod.material.missing").withStyle(ChatFormatting.RED));
            return;
        }

        tooltip.add(Component.translatable(
            "tooltip.chemmod.material.mass",
            String.format(Locale.ROOT, "%.3f", batch.massMicrograms() / 1_000_000.0)
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
            "tooltip.chemmod.purity",
            String.format(Locale.ROOT, "%.4f", batch.purityPpm() / 10_000.0)
        ).withStyle(batch.purityPpm() == 1_000_000 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));

        if (!batch.impuritiesPpm().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.chemmod.material.impurities")
                .withStyle(ChatFormatting.YELLOW));
            batch.impuritiesPpm().forEach((id, ppm) -> tooltip.add(Component.translatable(
                "tooltip.chemmod.material.impurity_entry",
                id,
                String.format(Locale.ROOT, "%.4f", ppm / 10_000.0)
            ).withStyle(ChatFormatting.DARK_GRAY)));
        }

        if (flag.isAdvanced()) {
            tooltip.add(Component.translatable("tooltip.chemmod.material.id", batch.materialId())
                .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.chemmod.material.form", batch.form())
                .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.chemmod.schema", batch.schemaVersion())
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
