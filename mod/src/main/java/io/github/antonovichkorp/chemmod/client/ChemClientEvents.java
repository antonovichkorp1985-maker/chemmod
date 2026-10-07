package io.github.antonovichkorp.chemmod.client;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only screen registration; the server never loads renderer classes. */
@EventBusSubscriber(modid = ChemMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ChemClientEvents {
    private ChemClientEvents() {}

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ChemMenus.CHEMICAL_REACTOR.get(), ChemicalReactorScreen::new);
    }
}
