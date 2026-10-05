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

    private ChemBlocks() {}

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
