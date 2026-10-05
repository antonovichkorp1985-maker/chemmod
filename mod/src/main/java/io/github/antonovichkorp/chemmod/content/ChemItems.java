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

    public static final DeferredHolder<Item, MaterialBlockItem> COPPER_BLOCK = ITEMS.register(
        "copper_block",
        () -> new MaterialBlockItem(
            new Item.Properties()
                .stacksTo(1)
                .component(ChemComponents.MATERIAL_BATCH.get(), CopperStorageBlockEntity.defaultBatch()),
            ChemBlocks.COPPER_BLOCK.get(),
            CopperStorageBlockEntity.MATERIAL_ID,
            CopperStorageBlockEntity.FORM
        )
    );

    public static final DeferredHolder<Item, BlockItem> STONE_MORTAR = ITEMS.register(
        "stone_mortar",
        () -> new BlockItem(ChemBlocks.STONE_MORTAR.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> ORE_WASHER = ITEMS.register(
        "ore_washer",
        () -> new BlockItem(ChemBlocks.ORE_WASHER.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> REFRACTORY_FURNACE = ITEMS.register(
        "refractory_furnace",
        () -> new BlockItem(ChemBlocks.REFRACTORY_FURNACE.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> INGOT_MOLD = ITEMS.register(
        "ingot_mold",
        () -> new BlockItem(ChemBlocks.INGOT_MOLD.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, BlockItem> METALWORKING_BENCH = ITEMS.register(
        "metalworking_bench",
        () -> new BlockItem(ChemBlocks.METALWORKING_BENCH.get(), new Item.Properties())
    );

    public static final DeferredHolder<Item, Item> CERAMIC_CRUCIBLE = ITEMS.register(
        "ceramic_crucible",
        () -> new Item(new Item.Properties().stacksTo(16))
    );

    public static final DeferredHolder<Item, Item> METALWORKING_HAMMER = ITEMS.register(
        "metalworking_hammer",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, Item> METALWORKING_CHISEL = ITEMS.register(
        "metalworking_chisel",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    public static final DeferredHolder<Item, Item> DRAWPLATE = ITEMS.register(
        "drawplate",
        () -> new Item(new Item.Properties().stacksTo(1))
    );

    private static final List<String> TEST_SUBSTANCES = List.of(
        "вода", "водород", "кислород", "углекислый_газ", "метан", "метанол",
        "этанол", "диметиловый_эфир", "пропан", "уксусная_кислота", "хлор"
    );

    private static final List<MaterialItemRegistration> MATERIAL_ITEMS = registerMaterialItems();
    private static final List<MoltenItemRegistration> MOLTEN_ITEMS = registerMoltenItems();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.chemmod"))
            .icon(() -> vial("вода"))
            .displayItems((parameters, output) -> {
                output.accept(new ItemStack(SUBSTANCE_VIAL.get()));
                TEST_SUBSTANCES.forEach(alias -> output.accept(vial(alias)));
                output.accept(new ItemStack(NATIVE_COPPER_ORE.get()));
                output.accept(copperStorageBlockStack(CopperStorageBlockEntity.defaultBatch()));
                output.accept(new ItemStack(STONE_MORTAR.get()));
                output.accept(new ItemStack(ORE_WASHER.get()));
                output.accept(new ItemStack(REFRACTORY_FURNACE.get()));
                output.accept(new ItemStack(INGOT_MOLD.get()));
                output.accept(new ItemStack(METALWORKING_BENCH.get()));
                output.accept(new ItemStack(CERAMIC_CRUCIBLE.get()));
                output.accept(new ItemStack(METALWORKING_HAMMER.get()));
                output.accept(new ItemStack(METALWORKING_CHISEL.get()));
                output.accept(new ItemStack(DRAWPLATE.get()));
                MATERIAL_ITEMS.forEach(registration -> output.accept(new ItemStack(registration.holder().get())));
                MOLTEN_ITEMS.forEach(registration -> output.accept(defaultMoltenStack(registration)));
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

    private static List<MoltenItemRegistration> registerMoltenItems() {
        List<MoltenItemRegistration> registrations = new ArrayList<>();
        for (MaterialItemSpec spec : MaterialItemExports.bundledMoltenContainers()) {
            DeferredHolder<Item, MoltenMaterialItem> holder = ITEMS.register(
                spec.getRegistryPath(),
                () -> new MoltenMaterialItem(
                    new Item.Properties()
                        .stacksTo(1)
                        .component(ChemComponents.MATERIAL_BATCH.get(), defaultBatch(spec))
                        .component(ChemComponents.MATERIAL_TEMPERATURE.get(), 1_357_770),
                    spec.getMaterialId()
                )
            );
            registrations.add(new MoltenItemRegistration(spec, holder));
        }
        return List.copyOf(registrations);
    }

    private static ItemStack defaultMoltenStack(MoltenItemRegistration registration) {
        return new ItemStack(registration.holder().get());
    }

    private static MaterialBatchContents defaultBatch(MaterialItemSpec spec) {
        long massMicrograms = switch (spec.getFormName()) {
            case "NUGGET" -> 125_000_000L;
            case "BLOCK" -> CopperStorageBlockEntity.MASS_MICROGRAMS;
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

    /** Copies a batch into another canonical form without changing its measured composition. */
    public static ItemStack materialStack(
        MaterialBatchContents input,
        String outputForm,
        long outputMassMicrograms
    ) {
        return materialStack(new MaterialBatchContents(
            MaterialBatchContents.CURRENT_SCHEMA,
            input.materialId(),
            outputForm,
            outputMassMicrograms,
            input.purityPpm(),
            input.impuritiesPpm()
        ));
    }

    /** Creates a canonical item from an already measured batch, for processes that alter composition. */
    public static ItemStack materialStack(MaterialBatchContents batch) {
        if (CopperStorageBlockEntity.FORM.equals(batch.form())
            && CopperStorageBlockEntity.MATERIAL_ID.equals(batch.materialId())) {
            return copperStorageBlockStack(batch);
        }
        for (MaterialItemRegistration registration : MATERIAL_ITEMS) {
            MaterialItemSpec spec = registration.spec();
            if (spec.getMaterialId().equals(batch.materialId()) && spec.getFormName().equals(batch.form())) {
                ItemStack result = new ItemStack(registration.holder().get());
                result.set(ChemComponents.MATERIAL_BATCH.get(), batch);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Creates the only supported placeable material form while retaining the exact material batch. */
    public static ItemStack copperStorageBlockStack(MaterialBatchContents batch) {
        if (!CopperStorageBlockEntity.MATERIAL_ID.equals(batch.materialId())
            || !CopperStorageBlockEntity.FORM.equals(batch.form())
            || batch.massMicrograms() != CopperStorageBlockEntity.MASS_MICROGRAMS) {
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(COPPER_BLOCK.get());
        result.set(ChemComponents.MATERIAL_BATCH.get(), batch);
        return result;
    }

    public static ItemStack moltenStack(
        MaterialBatchContents input,
        String outputForm,
        long outputMassMicrograms,
        int temperatureMillikelvin
    ) {
        for (MoltenItemRegistration registration : MOLTEN_ITEMS) {
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
                result.set(ChemComponents.MATERIAL_TEMPERATURE.get(), temperatureMillikelvin);
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    private record MaterialItemRegistration(
        MaterialItemSpec spec,
        DeferredHolder<Item, MaterialFormItem> holder
    ) {}

    private record MoltenItemRegistration(
        MaterialItemSpec spec,
        DeferredHolder<Item, MoltenMaterialItem> holder
    ) {}
}
