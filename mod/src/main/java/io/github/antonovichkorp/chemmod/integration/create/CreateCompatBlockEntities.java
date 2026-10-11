package io.github.antonovichkorp.chemmod.integration.create;

import io.github.antonovichkorp.chemmod.ChemMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persistent machine state for the optional Create-powered separator. */
public final class CreateCompatBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChemMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KineticOreSeparatorBlockEntity>>
        KINETIC_ORE_SEPARATOR = BLOCK_ENTITY_TYPES.register(
            "kinetic_ore_separator",
            () -> BlockEntityType.Builder.of(
                KineticOreSeparatorBlockEntity::new,
                CreateCompatBlocks.KINETIC_ORE_SEPARATOR.get()
            ).build(null)
        );

    private CreateCompatBlockEntities() {}

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
