package io.github.antonovichkorp.chemmod.integration.create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.stress.BlockStressValues;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

/**
 * Optional Create 6.x bridge. Nothing in this package is touched unless Create
 * is present, so ChemMod remains a valid standalone NeoForge mod.
 */
public final class CreateIntegration {
    public static final String MOD_ID = "create";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double KINETIC_SEPARATOR_STRESS_PER_RPM = 4.0D;

    private CreateIntegration() {}

    public static void register(IEventBus eventBus) {
        CreateCompatBlocks.register(eventBus);
        CreateCompatBlockEntities.register(eventBus);
        CreateCompatItems.register(eventBus);
        eventBus.addListener(CreateIntegration::registerCapabilities);
        eventBus.addListener(CreateIntegration::registerStressImpact);
        LOGGER.info("ChemMod Create compatibility adapter enabled: kinetic ore separator available");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            CreateCompatBlockEntities.KINETIC_ORE_SEPARATOR.get(),
            KineticOreSeparatorBlockEntity::itemHandlerFor
        );
    }

    private static void registerStressImpact(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockStressValues.IMPACTS.register(
            CreateCompatBlocks.KINETIC_ORE_SEPARATOR.get(),
            () -> KINETIC_SEPARATOR_STRESS_PER_RPM
        ));
    }
}
