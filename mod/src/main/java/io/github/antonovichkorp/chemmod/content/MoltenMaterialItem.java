package io.github.antonovichkorp.chemmod.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/** A sealed ceramic crucible carrying a server-authoritative molten material batch. */
public final class MoltenMaterialItem extends Item {
    private final String materialPath;

    public MoltenMaterialItem(Properties properties, String materialId) {
        super(properties);
        this.materialPath = materialId.substring(materialId.indexOf(':') + 1);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(
            "item.chemmod.molten_crucible",
            Component.translatable("material.chemmod." + materialPath)
        );
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        MaterialBatchContents batch = stack.get(ChemComponents.MATERIAL_BATCH.get());
        Integer temperature = stack.get(ChemComponents.MATERIAL_TEMPERATURE.get());
        if (batch == null || temperature == null) {
            tooltip.add(Component.translatable("tooltip.chemmod.material.missing").withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable(
            "tooltip.chemmod.material.temperature",
            String.format(Locale.ROOT, "%.2f", temperature / 1000.0)
        ).withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable(
            "tooltip.chemmod.material.mass",
            String.format(Locale.ROOT, "%.3f", batch.massMicrograms() / 1_000_000.0)
        ).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(
            "tooltip.chemmod.purity",
            String.format(Locale.ROOT, "%.4f", batch.purityPpm() / 10_000.0)
        ).withStyle(batch.purityPpm() == 1_000_000 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        if (!batch.impuritiesPpm().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.chemmod.material.impurities").withStyle(ChatFormatting.YELLOW));
            batch.impuritiesPpm().forEach((id, ppm) -> tooltip.add(Component.translatable(
                "tooltip.chemmod.material.impurity_entry",
                id,
                String.format(Locale.ROOT, "%.4f", ppm / 10_000.0)
            ).withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (flag.isAdvanced()) {
            tooltip.add(Component.translatable("tooltip.chemmod.material.id", batch.materialId())
                .withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.chemmod.schema", batch.schemaVersion())
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
