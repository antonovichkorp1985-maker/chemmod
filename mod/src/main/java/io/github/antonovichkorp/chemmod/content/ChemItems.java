package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.core.CommonSubstance;
import io.github.antonovichkorp.chemmod.core.CommonSubstances;
import io.github.antonovichkorp.chemmod.core.material.MaterialItemExports;
import io.github.antonovichkorp.chemmod.core.material.MaterialItemSpec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public final class ChemItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ChemMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChemMod.MOD_ID);

    public static final DeferredHolder<Item, SubstanceVialItem> SUBSTANCE_VIAL = ITEMS.register(
        "substance_vial",
        () -> new SubstanceVialItem(new Item.Properties().stacksTo(16))
    );

    public static final DeferredHolder<Item, BlockItem> NATIVE_COPPER_ORE = ITEMS.register(
        "native_copper_ore",
        () -> new BlockItem(ChemBlocks.NATIVE_COPPER_ORE.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> STONE_MORTAR = ITEMS.register(
        "stone_mortar",
        () -> new BlockItem(ChemBlocks.STONE_MORTAR.get(), new Item.Properties())
    );

    private static final List<String> TEST_SUBSTANCES = List.of(
        "вода", "водород", "кислород", "углекислый_газ", "метан", "метанол",
        "этанол", "диметиловый_эфир", "пропан", "уксусная_кислота", "хлор"
    );

    private static final List<MaterialItemRegistration> MATERIAL_ITEMS = registerMaterialItems();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.chemmod"))
            .icon(() -> vial("вода"))
            .displayItems((parameters, output) -> {
                output.accept(new ItemStack(SUBSTANCE_VIAL.get()));
                TEST_SUBSTANCES.forEach(alias -> output.accept(vial(alias)));
                output.accept(new ItemStack(NATIVE_COPPER_ORE.get()));
                output.accept(new ItemStack(STONE_MORTAR.get()));
                MATERIAL_ITEMS.forEach(registration -> output.accept(new ItemStack(registration.holder().get())));
            })
            .build()
    );

    private static List<MaterialItemRegistration> registerMaterialItems() {
        List<MaterialItemRegistration> registrations = new ArrayList<>();
        for (MaterialItemSpec spec : MaterialItemExports.bundledSolidForms()) {
            DeferredHolder<Item, MaterialFormItem> holder = ITEMS.register(
                spec.getRegistryPath(),
                () -> new MaterialFormItem(
                    new Item.Properties()
                        .stacksTo(64)
                        .component(ChemComponents.MATERIAL_BATCH.get(), defaultBatch(spec)),
                    spec.getMaterialId(),
                    spec.getFormName()
                )
            );
            registrations.add(new MaterialItemRegistration(spec, holder));
        }
        return List.copyOf(registrations);
    }

    private static MaterialBatchContents defaultBatch(MaterialItemSpec spec) {
        long massMicrograms = switch (spec.getFormName()) {
            case "NUGGET" -> 111_111_111L;
            case "WIRE" -> 250_000_000L;
            default -> 1_000_000_000L;
        };
        return new MaterialBatchContents(spec.getMaterialId(), spec.getFormName(), massMicrograms, 1_000_000);
    }

    private ChemItems() {}

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        TABS.register(eventBus);
    }

    public static ItemStack vial(String input) {
        CommonSubstance common = CommonSubstances.INSTANCE.find(input);
        String structure = common == null ? CommonSubstances.INSTANCE.resolve(input) : common.getStructure();
        return vialFromStructure(structure, 1_000_000L, 1_000_000);
    }

    public static ItemStack vialFromStructure(String structure, long micromoles, int purityPpm) {
        ItemStack stack = new ItemStack(SUBSTANCE_VIAL.get());
        stack.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, micromoles, purityPpm));
        return stack;
    }

    public static ItemStack waterSample(long micromoles, WaterSampleData analysis) {
        ItemStack stack = new ItemStack(SUBSTANCE_VIAL.get());
        stack.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents("O", micromoles, analysis));
        return stack;
    }

    public static ItemStack materialStack(
        MaterialBatchContents input,
        String outputForm,
        long outputMassMicrograms
    ) {
        for (MaterialItemRegistration registration : MATERIAL_ITEMS) {
            MaterialItemSpec spec = registration.spec();
            if (spec.getMaterialId().equals(input.materialId()) && spec.getFormName().equals(outputForm)) {
                ItemStack result = new ItemStack(registration.holder().get());
                result.set(ChemComponents.MATERIAL_BATCH.get(), new MaterialBatchContents(
                    MaterialBatchContents.CURRENT_SCHEMA,
                    input.materialId(),
                    outputForm,
                    outputMassMicrograms,
                    input.purityPpm(),
                    input.impuritiesPpm()
                ));
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    private record MaterialItemRegistration(
        MaterialItemSpec spec,
        DeferredHolder<Item, MaterialFormItem> holder
    ) {}
}
