package io.github.antonovichkorp.chemmod.client;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only screen registration; the server never loads renderer classes. */
@EventBusSubscriber(modid = ChemMod.MOD_ID, value = Dist.CLIENT)
public final class ChemClientEvents {
    @SubscribeEvent
    public static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(io.github.antonovichkorp.chemmod.content.ChemBlockEntities.LABORATORY_HOLDER.get(),
            LaboratoryHolderRenderer::new);
    }

    @SubscribeEvent
    public static void clientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(
            io.github.antonovichkorp.chemmod.content.ChemItems.SUBSTANCE_VIAL.get(),
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "contents"),
            (stack, level, entity, seed) -> stack.has(io.github.antonovichkorp.chemmod.content.ChemComponents.MIXTURE.get()) ? 2F
                : stack.has(io.github.antonovichkorp.chemmod.content.ChemComponents.SUBSTANCE.get()) ? 1F : 0F));
    }

    private ChemClientEvents() {}

    @SubscribeEvent
    public static void registerMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ChemMenus.CHEMICAL_REACTOR.get(), ChemicalReactorScreen::new);
    }
}
