package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import io.github.antonovichkorp.chemmod.ChemMod;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ChemComponents {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ChemMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SubstanceContents>> SUBSTANCE =
        COMPONENTS.register("substance", () -> DataComponentType.<SubstanceContents>builder()
            .persistent(SubstanceContents.CODEC)
            .networkSynchronized(SubstanceContents.STREAM_CODEC)
            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MixtureContents>> MIXTURE =
        COMPONENTS.register("mixture", () -> DataComponentType.<MixtureContents>builder()
            .persistent(MixtureContents.CODEC)
            .networkSynchronized(MixtureContents.STREAM_CODEC)
            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MaterialBatchContents>> MATERIAL_BATCH =
        COMPONENTS.register("material_batch", () -> DataComponentType.<MaterialBatchContents>builder()
            .persistent(MaterialBatchContents.CODEC)
            .networkSynchronized(MaterialBatchContents.STREAM_CODEC)
            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MATERIAL_TEMPERATURE =
        COMPONENTS.register("material_temperature_millikelvin", () -> DataComponentType.<Integer>builder()
            .persistent(Codec.INT)
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RadioactiveContents>> RADIOACTIVE_CONTENTS =
        COMPONENTS.register("radioactive_contents", () -> DataComponentType.<RadioactiveContents>builder()
            .persistent(RadioactiveContents.CODEC)
            .networkSynchronized(RadioactiveContents.STREAM_CODEC)
            .build());

    private ChemComponents() {}

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
