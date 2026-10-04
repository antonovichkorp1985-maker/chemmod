package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.core.CommonSubstance;
import io.github.antonovichkorp.chemmod.core.CommonSubstances;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ChemItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ChemMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChemMod.MOD_ID);

    public static final DeferredHolder<Item, SubstanceVialItem> SUBSTANCE_VIAL = ITEMS.register(
        "substance_vial",
        () -> new SubstanceVialItem(new Item.Properties().stacksTo(16))
    );

    private static final List<String> TEST_SUBSTANCES = List.of(
        "вода", "водород", "кислород", "углекислый_газ", "метан", "метанол",
        "этанол", "диметиловый_эфир", "пропан", "уксусная_кислота", "хлор"
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.chemmod"))
            .icon(() -> vial("вода"))
            .displayItems((parameters, output) -> {
                output.accept(new ItemStack(SUBSTANCE_VIAL.get()));
                TEST_SUBSTANCES.forEach(alias -> output.accept(vial(alias)));
            })
            .build()
    );

    private ChemItems() {}

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        TABS.register(eventBus);
    }

    public static ItemStack vial(String input) {
        CommonSubstance common = CommonSubstances.INSTANCE.find(input);
        String structure = common == null ? CommonSubstances.INSTANCE.resolve(input) : common.getStructure();
        return vialFromStructure(structure, 1_000_000L, 999_000);
    }

    public static ItemStack vialFromStructure(String structure, long micromoles, int purityPpm) {
        ItemStack stack = new ItemStack(SUBSTANCE_VIAL.get());
        stack.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, micromoles, purityPpm));
        return stack;
    }
}
