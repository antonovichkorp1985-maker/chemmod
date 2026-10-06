package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.CommonSubstance;
import io.github.antonovichkorp.chemmod.core.CommonSubstances;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.integration.WaterFluidAdapters;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Locale;

public final class SubstanceVialItem extends Item {
    public SubstanceVialItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.has(ChemComponents.SUBSTANCE.get())) {
            return InteractionResultHolder.pass(held);
        }

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(held);
        }
        BlockPos position = hit.getBlockPos();
        var fluid = level.getFluidState(position);
        var classifiedWater = WaterFluidAdapters.classify(level, position, fluid);
        if (classifiedWater.isEmpty() || !fluid.isSource()) {
            return InteractionResultHolder.pass(held);
        }

        if (!level.isClientSide) {
            var classification = classifiedWater.get();
            WaterSampleData analysis = WaterSampleAnalyzer.analyze(
                level,
                position,
                classification.profile(),
                classification.evidenceFactor()
            );
            ItemStack filled = ChemItems.waterSample(1_000_000L, analysis);
            if (held.getCount() == 1 && !player.getAbilities().instabuild) {
                player.setItemInHand(hand, filled);
            } else {
                if (!player.getAbilities().instabuild) held.shrink(1);
                if (!player.getInventory().add(filled)) player.drop(filled, false);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public Component getName(ItemStack stack) {
        SubstanceContents contents = stack.get(ChemComponents.SUBSTANCE.get());
        if (contents == null) {
            return Component.translatable("item.chemmod.substance_vial.empty");
        }
        CommonSubstance common = CommonSubstances.INSTANCE.findByCanonicalKey(contents.canonicalKey());
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
            Molecule molecule = contents.molecule();
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
            if (contents.purityPpm() < 1_000_000) {
                tooltip.add(Component.translatable(
                    "tooltip.chemmod.impurities",
                    String.format(Locale.ROOT, "%.4f", (1_000_000 - contents.purityPpm()) / 10_000.0)
                ).withStyle(ChatFormatting.YELLOW));
            }
            WaterSampleData waterSample = contents.waterSample();
            if (waterSample.isAnalyzed()) {
                tooltip.add(Component.translatable(
                    "tooltip.chemmod.water_source",
                    Component.translatable("water_profile.chemmod." + waterSample.profile())
                ).withStyle(ChatFormatting.BLUE));
                for (WaterSampleData.Impurity impurity : waterSample.impurities()) {
                    tooltip.add(Component.translatable(
                        "tooltip.chemmod.impurity_entry",
                        Component.translatable("impurity.chemmod." + impurity.id()),
                        String.format(Locale.ROOT, "%.4f", impurity.ppm() / 10_000.0)
                    ).withStyle(ChatFormatting.DARK_GRAY));
                }
                if (!waterSample.factors().isEmpty()) {
                    tooltip.add(Component.translatable("tooltip.chemmod.environmental_factors")
                        .withStyle(ChatFormatting.DARK_AQUA));
                    for (String factor : waterSample.factors()) {
                        tooltip.add(Component.translatable(
                            "tooltip.chemmod.environmental_factor",
                            Component.translatable("water_factor.chemmod." + factor)
                        ).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
            tooltip.add(Component.translatable(
                "tooltip.chemmod.molar_mass",
                String.format(Locale.ROOT, "%.3f", molecule.molarMass())
            ).withStyle(ChatFormatting.DARK_GRAY));
            if (flag.isAdvanced()) {
                tooltip.add(Component.translatable("tooltip.chemmod.schema", contents.schemaVersion())
                    .withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable("tooltip.chemmod.structure", contents.structuralWitness())
                    .withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable(
                    "tooltip.chemmod.canonical_id",
                    contents.canonicalKey()
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
