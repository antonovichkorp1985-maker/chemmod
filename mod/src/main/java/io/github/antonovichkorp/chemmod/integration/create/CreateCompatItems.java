package io.github.antonovichkorp.chemmod.integration.create;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Optional block item and creative-tab insertion for the Create bridge. */
public final class CreateCompatItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ChemMod.MOD_ID);

    public static final DeferredHolder<Item, BlockItem> KINETIC_ORE_SEPARATOR = ITEMS.register(
        "kinetic_ore_separator",
        () -> new BlockItem(CreateCompatBlocks.KINETIC_ORE_SEPARATOR.get(), new Item.Properties())
    );

    private CreateCompatItems() {}

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        eventBus.addListener(CreateCompatItems::addToChemModCreativeTab);
    }

    private static void addToChemModCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ChemItems.MAIN_TAB.get()) {
            event.accept(new ItemStack(KINETIC_ORE_SEPARATOR.get()));
        }
    }
}
