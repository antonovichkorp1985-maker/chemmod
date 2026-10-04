package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.CommonSubstance;
import io.github.antonovichkorp.chemmod.core.CommonSubstances;
import io.github.antonovichkorp.chemmod.core.Molecule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

public final class SubstanceVialItem extends Item {
    public SubstanceVialItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        SubstanceContents contents = stack.get(ChemComponents.SUBSTANCE.get());
        if (contents == null) {
            return Component.translatable("item.chemmod.substance_vial.empty");
        }
        CommonSubstance common = CommonSubstances.INSTANCE.findByStructure(contents.structure());
        Component substanceName = common == null
            ? Component.translatable("substance.chemmod.custom")
            : Component.translatable("substance.chemmod." + common.getCanonicalName());
        return Component.translatable("item.chemmod.substance_vial.filled", substanceName);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        SubstanceContents contents = stack.get(ChemComponents.SUBSTANCE.get());
        if (contents == null) {
            tooltip.add(Component.translatable("tooltip.chemmod.vial.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        try {
            Molecule molecule = Molecule.Companion.fromSMILESlike(contents.structure());
            tooltip.add(Component.translatable("tooltip.chemmod.formula", molecule.formula())
                .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(
                "tooltip.chemmod.amount",
                String.format(Locale.ROOT, "%.6f", contents.micromoles() / 1_000_000.0)
            ).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(
                "tooltip.chemmod.purity",
                String.format(Locale.ROOT, "%.4f", contents.purityPpm() / 10_000.0)
            ).withStyle(contents.purityPpm() >= 999_000 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            tooltip.add(Component.translatable(
                "tooltip.chemmod.molar_mass",
                String.format(Locale.ROOT, "%.3f", molecule.molarMass())
            ).withStyle(ChatFormatting.DARK_GRAY));
            if (flag.isAdvanced()) {
                tooltip.add(Component.translatable("tooltip.chemmod.structure", contents.structure())
                    .withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable(
                    "tooltip.chemmod.canonical_id",
                    Long.toUnsignedString(molecule.canonicalId())
                ).withStyle(ChatFormatting.DARK_GRAY));
            } else {
                tooltip.add(Component.translatable("tooltip.chemmod.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        } catch (Exception exception) {
            tooltip.add(Component.translatable("tooltip.chemmod.invalid", exception.getMessage())
                .withStyle(ChatFormatting.RED));
        }
    }
}
