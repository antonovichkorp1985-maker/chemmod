package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.chemistry.CatalogSolubility;
import io.github.antonovichkorp.chemmod.core.chemistry.ReferenceBookReport;
import io.github.antonovichkorp.chemmod.core.chemistry.ReferenceBookReports;
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

/** Shift-use while a reference vial is in the other hand to print modeled properties. */
public final class ChemicalReferenceBookItem extends Item {
    public ChemicalReferenceBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.chemmod.reference_book.usage"));
        tooltip.add(Component.translatable("tooltip.chemmod.reference_book.scope"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack book = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(book);

        InteractionHand sampleHand = hand == InteractionHand.MAIN_HAND
            ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack sample = player.getItemInHand(sampleHand);
        if (!sample.is(ChemItems.SUBSTANCE_VIAL.get())) return InteractionResultHolder.pass(book);
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(book, true);

        MixtureContents mixture = sample.get(ChemComponents.MIXTURE.get());
        SubstanceContents contents = sample.get(ChemComponents.SUBSTANCE.get());
        if (mixture != null) {
            player.displayClientMessage(Component.translatable("message.chemmod.reference.mixture_unsupported"), false);
            return InteractionResultHolder.sidedSuccess(book, false);
        }
        if (contents == null) {
            player.displayClientMessage(Component.translatable("message.chemmod.reference.empty_vial"), false);
            return InteractionResultHolder.sidedSuccess(book, false);
        }

        try {
            Molecule molecule = contents.molecule();
            ReferenceBookReport report = ReferenceBookReports.INSTANCE.forReference(molecule);
            if (report == null) {
                player.displayClientMessage(Component.translatable(
                    "message.chemmod.reference.not_catalogued", molecule.formula()), false);
                return InteractionResultHolder.sidedSuccess(book, false);
            }
            printReport(player, report);
        } catch (RuntimeException exception) {
            player.displayClientMessage(Component.translatable("message.chemmod.reference.invalid_vial"), false);
        }
        return InteractionResultHolder.sidedSuccess(book, false);
    }

    private static void printReport(Player player, ReferenceBookReport report) {
        player.displayClientMessage(Component.translatable(
            "message.chemmod.reference.header",
            Component.translatable("substance.chemmod." + report.getCanonicalName()),
            report.getFormula()), false);
        player.displayClientMessage(Component.translatable(
            "message.chemmod.reference.model", report.getPropertyModel()), false);
        player.displayClientMessage(Component.translatable(
            "message.chemmod.reference.molar_mass", format(report.getMolarMassGramsPerMole())), false);

        Double boilingPoint = report.getBoilingPointCelsius();
        if (boilingPoint == null) {
            player.displayClientMessage(Component.translatable(
                "message.chemmod.reference.boiling_unavailable", report.getBoilingPointBasis()), false);
        } else {
            player.displayClientMessage(Component.translatable(
                "message.chemmod.reference.boiling", format(boilingPoint), report.getBoilingPointBasis()), false);
        }

        Double combustion = report.getCombustionEnthalpyKilojoulesPerMole();
        if (combustion == null) {
            player.displayClientMessage(Component.translatable("message.chemmod.reference.combustion_unavailable"), false);
        } else {
            player.displayClientMessage(Component.translatable(
                "message.chemmod.reference.combustion", format(combustion)), false);
        }

        CatalogSolubility solubility = report.getSolubility();
        if (solubility != null) {
            player.displayClientMessage(Component.translatable(
                "message.chemmod.reference.solubility",
                Component.translatable("tooltip.chemmod.solubility." + solubility.name().toLowerCase(Locale.ROOT))), false);
        }

        player.displayClientMessage(Component.translatable("message.chemmod.reference.flags_header"), false);
        if (report.getFlags().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.chemmod.reference.flags_none"), false);
        } else {
            for (String flag : report.getFlags()) {
                player.displayClientMessage(Component.translatable(
                    "message.chemmod.reference.flag",
                    Component.translatable("property_flag.chemmod." + flag.toLowerCase(Locale.ROOT))), false);
            }
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
