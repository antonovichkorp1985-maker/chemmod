package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.ChemMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks owned by ChemMod. World placement remains data-driven. */
public final class ChemBlocks {
    private static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, ChemMod.MOD_ID);

    public static final DeferredHolder<Block, Block> NATIVE_COPPER_ORE = BLOCKS.register(
        "native_copper_ore",
        () -> new Block(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .requiresCorrectToolForDrops()
                .strength(3.0F, 3.0F)
                .sound(SoundType.STONE)
        )
    );

    public static final DeferredHolder<Block, CopperStorageBlock> COPPER_BLOCK = BLOCKS.register(
        "copper_block",
        () -> new CopperStorageBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops()
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL)
        )
    );

    public static final DeferredHolder<Block, StoneMortarBlock> STONE_MORTAR = BLOCKS.register(
        "stone_mortar",
        () -> new StoneMortarBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.0F, 6.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, OreWasherBlock> ORE_WASHER = BLOCKS.register(
        "ore_washer",
        () -> new OreWasherBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .requiresCorrectToolForDrops()
                .strength(3.0F, 6.0F)
                .sound(SoundType.STONE)
        )
    );

    public static final DeferredHolder<Block, OreSeparatorBlock> ORE_SEPARATOR = BLOCKS.register(
        "ore_separator",
        () -> new OreSeparatorBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops()
                .strength(3.0F, 6.0F)
                .sound(SoundType.METAL)
        )
    );

    public static final DeferredHolder<Block, RefractoryFurnaceBlock> REFRACTORY_FURNACE = BLOCKS.register(
        "refractory_furnace",
        () -> new RefractoryFurnaceBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN)
                .requiresCorrectToolForDrops()
                .strength(3.5F, 6.0F)
                .sound(SoundType.DEEPSLATE_BRICKS)
        )
    );

    /** M3 vessel: actual vial slots, data-rule environment checks, and 1 Hz processing. */
    public static final DeferredHolder<Block, ChemicalReactorBlock> CHEMICAL_REACTOR = BLOCKS.register(
        "chemical_reactor",
        () -> new ChemicalReactorBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_GRAY)
                .requiresCorrectToolForDrops()
                .strength(4.0F, 6.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, IngotMoldBlock> INGOT_MOLD = BLOCKS.register(
        "ingot_mold",
        () -> new IngotMoldBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(2.5F, 6.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
        )
    );

    public static final DeferredHolder<Block, MetalworkingBenchBlock> METALWORKING_BENCH = BLOCKS.register(
        "metalworking_bench",
        () -> new MetalworkingBenchBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5F, 6.0F)
                .sound(SoundType.WOOD)
        )
    );

    public static final DeferredHolder<Block, LaboratoryHolderBlock> LABORATORY_HOLDER = BLOCKS.register(
        "laboratory_holder", () -> new LaboratoryHolderBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.METAL).noOcclusion()
            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));

    private ChemBlocks() {}

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
