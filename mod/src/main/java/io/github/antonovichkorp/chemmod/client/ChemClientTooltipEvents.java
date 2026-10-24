package io.github.antonovichkorp.chemmod.client;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import io.github.antonovichkorp.chemmod.core.gas.Atmosphere;
import io.github.antonovichkorp.chemmod.core.phase.AtmosphericPhaseEstimate;
import io.github.antonovichkorp.chemmod.core.phase.AtmosphericPhaseEstimateModel;
import io.github.antonovichkorp.chemmod.core.phase.Phase;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/** Adds the graph-derived phase estimate using the inspecting player's current atmospheric height. */
@EventBusSubscriber(modid = ChemMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ChemClientTooltipEvents {
    private ChemClientTooltipEvents() {}

    @SubscribeEvent
    public static void appendAtmosphericPhase(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) return; // Startup search-tree tooltips have no world position.

        ItemStack stack = event.getItemStack();
        if (!stack.is(ChemItems.SUBSTANCE_VIAL.get()) && !stack.is(ChemItems.STEEL_FLASK.get())) return;
        if (stack.has(ChemComponents.MIXTURE.get())) {
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.chemmod.phase.mixture")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        SubstanceContents contents = stack.get(ChemComponents.SUBSTANCE.get());
        if (contents == null) return;

        Level level = player.level();
        if (!Level.OVERWORLD.equals(level.dimension())) {
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.chemmod.phase.environment_unknown")
                .withStyle(ChatFormatting.GRAY));
            return;
        }
        double altitude = Math.max(0.0, player.getY() - level.getSeaLevel());
        AtmosphericPhaseEstimate estimate;
        try {
            estimate = AtmosphericPhaseEstimateModel.INSTANCE.at(
                contents.molecule(), Atmosphere.EARTH, altitude, "Earth ISA");
        } catch (IllegalArgumentException unsupported) {
            event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.chemmod.phase.unknown")
                .withStyle(ChatFormatting.GRAY));
            return;
        }

        String phaseKey;
        if (estimate.getVerdict().getPhase() == Phase.GAS) {
            phaseKey = "tooltip.chemmod.phase.gas";
        } else if (estimate.getVerdict().getPhase() == Phase.CONDENSED) {
            phaseKey = "tooltip.chemmod.phase.condensed";
        } else {
            phaseKey = "tooltip.chemmod.phase.unknown";
        }
        event.getToolTip().add(net.minecraft.network.chat.Component.translatable(phaseKey)
            .withStyle(ChatFormatting.BLUE));
        event.getToolTip().add(net.minecraft.network.chat.Component.translatable(
                "tooltip.chemmod.phase.conditions",
                String.format(Locale.ROOT, "%.2f", estimate.getTemperatureKelvin()),
                String.format(Locale.ROOT, "%.0f", estimate.getPressurePascals()))
            .withStyle(ChatFormatting.DARK_GRAY));
        event.getToolTip().add(net.minecraft.network.chat.Component.translatable("tooltip.chemmod.phase.model.earth_isa")
            .withStyle(ChatFormatting.GRAY));
    }
}
