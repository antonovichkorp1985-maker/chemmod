package io.github.antonovichkorp.chemmod.integration.create;

import io.github.antonovichkorp.chemmod.ChemMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry content which exists only when the Create adapter is activated. */
public final class CreateCompatBlocks {
    private static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(Registries.BLOCK, ChemMod.MOD_ID);

    public static final DeferredHolder<Block, KineticOreSeparatorBlock> KINETIC_ORE_SEPARATOR = BLOCKS.register(
        "kinetic_ore_separator",
        () -> new KineticOreSeparatorBlock(
            BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops()
                .strength(3.5F, 6.0F)
                .sound(SoundType.METAL)
        )
    );

    private CreateCompatBlocks() {}

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
