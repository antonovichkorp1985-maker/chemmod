package io.github.antonovichkorp.chemmod.integration;

import io.github.antonovichkorp.chemmod.content.WaterSampleProfile;
import io.github.antonovichkorp.chemmod.integration.tfc.TerraFirmaCraftWaterAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

import java.util.List;
import java.util.Optional;

/** Ordered registry: specific optional adapters run before the vanilla fallback. */
public final class WaterFluidAdapters {
    private static final List<RegisteredAdapter> ADAPTERS = List.of(
        new RegisteredAdapter(TerraFirmaCraftWaterAdapter.INSTANCE, "terrafirmacraft_fluid"),
        new RegisteredAdapter(
            (level, position, fluid) -> fluid.is(FluidTags.WATER)
                ? Optional.of(WaterSampleProfile.at(level, position))
                : Optional.empty(),
            null
        )
    );

    private WaterFluidAdapters() {}

    public static Optional<ClassifiedWater> classify(Level level, BlockPos position, FluidState fluid) {
        for (RegisteredAdapter registered : ADAPTERS) {
            Optional<WaterSampleProfile> profile = registered.adapter().classify(level, position, fluid);
            if (profile.isPresent()) {
                return Optional.of(new ClassifiedWater(profile.get(), registered.evidenceFactor()));
            }
        }
        return Optional.empty();
    }

    private record RegisteredAdapter(WaterFluidAdapter adapter, String evidenceFactor) {}

    public record ClassifiedWater(WaterSampleProfile profile, String evidenceFactor) {}
}
