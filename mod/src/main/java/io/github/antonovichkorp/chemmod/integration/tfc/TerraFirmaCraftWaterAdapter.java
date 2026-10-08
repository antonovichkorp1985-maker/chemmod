package io.github.antonovichkorp.chemmod.integration.tfc;

import io.github.antonovichkorp.chemmod.content.WaterSampleProfile;
import io.github.antonovichkorp.chemmod.integration.WaterFluidAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;

/**
 * Registry-only TFC integration: no TerraFirmaCraft classes are linked, so ChemMod
 * remains loadable when TFC is absent or updated independently.
 */
public final class TerraFirmaCraftWaterAdapter implements WaterFluidAdapter {
    public static final TerraFirmaCraftWaterAdapter INSTANCE = new TerraFirmaCraftWaterAdapter();

    private TerraFirmaCraftWaterAdapter() {}

    @Override
    public Optional<WaterSampleProfile> classify(Level level, BlockPos position, FluidState fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getType());
        if (!"tfc".equals(id.getNamespace())) return Optional.empty();

        String path = normalize(id.getPath());
        return switch (path) {
            case "salt_water" -> Optional.of(WaterSampleProfile.OCEAN);
            case "river_water" -> Optional.of(WaterSampleProfile.RIVER);
            case "spring_water" -> Optional.of(WaterSampleProfile.SPRING);
            case "fresh_water" -> Optional.of(WaterSampleProfile.at(level, position));
            default -> Optional.empty();
        };
    }

    private static String normalize(String path) {
        String normalized = path.startsWith("fluid/") ? path.substring("fluid/".length()) : path;
        boolean changed;
        do {
            changed = false;
            if (normalized.startsWith("flowing_")) {
                normalized = normalized.substring("flowing_".length());
                changed = true;
            }
            if (normalized.startsWith("finite_")) {
                normalized = normalized.substring("finite_".length());
                changed = true;
            }
        } while (changed);
        return normalized;
    }
}
