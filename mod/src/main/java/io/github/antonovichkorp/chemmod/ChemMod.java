package io.github.antonovichkorp.chemmod;

import io.github.antonovichkorp.chemmod.content.ChemBlockEntities;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.ChemMenus;
import io.github.antonovichkorp.chemmod.integration.create.CreateIntegration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

/** Entry point for gameplay registrations only; diagnostic chemistry stays in the development CLI and CI. */
@Mod(ChemMod.MOD_ID)
public final class ChemMod {
    public static final String MOD_ID = "chemmod";

    public ChemMod(IEventBus modEventBus) {
        ChemComponents.register(modEventBus);
        ChemBlocks.register(modEventBus);
        ChemBlockEntities.register(modEventBus);
        ChemItems.register(modEventBus);
        ChemMenus.register(modEventBus);
        if (ModList.get().isLoaded(CreateIntegration.MOD_ID)) {
            CreateIntegration.register(modEventBus);
        }
    }
}
