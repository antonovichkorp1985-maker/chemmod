package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.ChemMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered persistent holders for canonical material state that cannot fit in a block state. */
public final class ChemBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChemMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopperStorageBlockEntity>> COPPER_STORAGE_BLOCK =
        BLOCK_ENTITY_TYPES.register(
            "copper_storage_block",
            () -> BlockEntityType.Builder.of(
                CopperStorageBlockEntity::new,
                ChemBlocks.COPPER_BLOCK.get()
            ).build(null)
        );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChemicalReactorBlockEntity>> CHEMICAL_REACTOR =
        BLOCK_ENTITY_TYPES.register(
            "chemical_reactor",
            () -> BlockEntityType.Builder.of(
                ChemicalReactorBlockEntity::new,
                ChemBlocks.CHEMICAL_REACTOR.get()
            ).build(null)
        );

    private ChemBlockEntities() {}

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
