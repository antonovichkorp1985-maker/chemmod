package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.CommonSubstance;
import io.github.antonovichkorp.chemmod.core.CommonSubstances;
import io.github.antonovichkorp.chemmod.core.Molecule;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/** Minimal M2 metal transfer vessel; A4 chemical-resistance and vessel-failure behaviour is separate work. */
public final class SteelFlaskItem extends Item {
    public SteelFlaskItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack flask = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack vial = player.getItemInHand(otherHand);
        if (!vial.is(ChemItems.SUBSTANCE_VIAL.get())) return InteractionResultHolder.pass(flask);

        final VialTransferTransaction.Plan plan;
        try {
            plan = VialTransferTransaction.plan(vial, flask);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.chemmod.transfer.refused"), true);
            }
            return InteractionResultHolder.fail(flask);
        }

        if (!level.isClientSide()) {
            if (!plan.commit(vial, flask)) {
                player.displayClientMessage(Component.translatable("message.chemmod.transfer.stale"), true);
                return InteractionResultHolder.fail(flask);
            }
            player.displayClientMessage(Component.translatable("message.chemmod.transfer.success"), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(flask, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        VialContentsState state;
        try {
            state = VialContentsState.fromStack(stack);
        } catch (IllegalArgumentException invalid) {
            return Component.translatable("item.chemmod.steel_flask.invalid");
        }
        if (state.substance() != null) {
            CommonSubstance common = CommonSubstances.INSTANCE.findByCanonicalKey(state.substance().canonicalKey());
            Component name = common == null
                ? Component.translatable("substance.chemmod.custom")
                : Component.translatable("substance.chemmod." + common.getCanonicalName());
            return Component.translatable("item.chemmod.steel_flask.filled", name);
        }
        if (state.mixture() != null) {
            return Component.translatable("item.chemmod.steel_flask.mixture", state.mixture().parts().size());
        }
        return Component.translatable("item.chemmod.steel_flask.empty");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        final VialContentsState state;
        try {
            state = VialContentsState.fromStack(stack);
        } catch (IllegalArgumentException invalid) {
            tooltip.add(Component.translatable("tooltip.chemmod.invalid", invalid.getMessage())
                .withStyle(ChatFormatting.RED));
            return;
        }
        if (state.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.chemmod.steel_flask.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.chemmod.steel_flask.transfer").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (state.substance() != null) {
            SubstanceContents contents = state.substance();
            Molecule molecule = contents.molecule();
            tooltip.add(Component.translatable("tooltip.chemmod.formula", molecule.formula())
                .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.chemmod.amount",
                String.format(Locale.ROOT, "%.6f", contents.micromoles() / 1_000_000.0))
                .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.chemmod.purity",
                String.format(Locale.ROOT, "%.4f", contents.purityPpm() / 10_000.0))
                .withStyle(contents.purityPpm() >= 999_000 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            if (contents.purityPpm() < 1_000_000) {
                tooltip.add(Component.translatable("tooltip.chemmod.impurities",
                    String.format(Locale.ROOT, "%.4f", (1_000_000 - contents.purityPpm()) / 10_000.0))
                    .withStyle(ChatFormatting.YELLOW));
            }
            WaterSampleData waterSample = contents.waterSample();
            if (waterSample.isAnalyzed()) {
                tooltip.add(Component.translatable("tooltip.chemmod.water_source",
                    Component.translatable("water_profile.chemmod." + waterSample.profile()))
                    .withStyle(ChatFormatting.BLUE));
                for (WaterSampleData.Impurity impurity : waterSample.impurities()) {
                    tooltip.add(Component.translatable("tooltip.chemmod.impurity_entry",
                        Component.translatable("impurity.chemmod." + impurity.id()),
                        String.format(Locale.ROOT, "%.4f", impurity.ppm() / 10_000.0))
                        .withStyle(ChatFormatting.DARK_GRAY));
                }
                if (!waterSample.factors().isEmpty()) {
                    tooltip.add(Component.translatable("tooltip.chemmod.environmental_factors")
                        .withStyle(ChatFormatting.DARK_AQUA));
                    for (String factor : waterSample.factors()) {
                        tooltip.add(Component.translatable("tooltip.chemmod.environmental_factor",
                            Component.translatable("water_factor.chemmod." + factor))
                            .withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
            tooltip.add(Component.translatable("tooltip.chemmod.molar_mass",
                String.format(Locale.ROOT, "%.3f", molecule.molarMass()))
                .withStyle(ChatFormatting.DARK_GRAY));
            if (flag.isAdvanced()) {
                tooltip.add(Component.translatable("tooltip.chemmod.schema", contents.schemaVersion())
                    .withStyle(ChatFormatting.DARK_GRAY));
                tooltip.add(Component.translatable("tooltip.chemmod.canonical_id", contents.canonicalKey())
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
            return;
        }

        MixtureContents mixture = state.mixture();
        tooltip.add(Component.translatable("tooltip.chemmod.mixture", mixture.parts().size())
            .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.chemmod.amount",
            String.format(Locale.ROOT, "%.6f", mixture.totalMicromoles() / 1_000_000.0))
            .withStyle(ChatFormatting.GRAY));
        for (MixtureContents.MixturePart part : mixture.parts()) {
            tooltip.add(Component.translatable("tooltip.chemmod.mixture.component",
                part.molecule().formula(), String.format(Locale.ROOT, "%.6f", part.micromoles() / 1_000_000.0))
                .withStyle(ChatFormatting.DARK_GRAY));
            if (flag.isAdvanced()) {
                tooltip.add(Component.translatable("tooltip.chemmod.mixture.component_key", part.canonicalKey())
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
