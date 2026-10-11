package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Server-side environmental sampler. The result is persisted, never inferred again from the tooltip. */
public final class WaterSampleAnalyzer {
    private static final int HORIZONTAL_RADIUS = 2;
    private static final int VERTICAL_RADIUS = 1;

    private WaterSampleAnalyzer() {}

    public static WaterSampleData analyze(Level level, BlockPos source) {
        return analyze(level, source, WaterSampleProfile.at(level, source), null);
    }

    public static WaterSampleData analyze(
        Level level,
        BlockPos source,
        WaterSampleProfile profile,
        String sourceEvidenceFactor
    ) {
        WaterSampleData baseline = profile.baseline();
        int sedimentBlocks = 0;
        int vegetationBlocks = 0;
        int agricultureBlocks = 0;
        int oreBlocks = 0;
        int geothermalBlocks = 0;

        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                    BlockState state = level.getBlockState(source.offset(dx, dy, dz));
                    String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath()
                        .toLowerCase(Locale.ROOT);
                    if (isSediment(path)) sedimentBlocks++;
                    if (isVegetation(path)) vegetationBlocks++;
                    if (isAgriculture(path)) agricultureBlocks++;
                    if (isOre(path)) oreBlocks++;
                    if (isGeothermal(path)) geothermalBlocks++;
                }
            }
        }

        Set<String> factors = new LinkedHashSet<>();
        if (sourceEvidenceFactor != null && !sourceEvidenceFactor.isBlank()) {
            factors.add(sourceEvidenceFactor);
        }
        int minerals = baseline.dissolvedMineralsPpm();
        int solids = baseline.suspendedSolidsPpm();
        int organic = baseline.organicMatterPpm();
        int gases = baseline.dissolvedGasesPpm();
        int nutrients = baseline.nutrientsPpm();

        if (sedimentBlocks > 0) {
            solids += Math.min(sedimentBlocks, 20) * 75;
            factors.add("nearby_sediment");
        }
        if (vegetationBlocks > 0) {
            int count = Math.min(vegetationBlocks, 20);
            organic += count * 50;
            gases += count * 10;
            factors.add("nearby_vegetation");
        }
        if (agricultureBlocks > 0) {
            int count = Math.min(agricultureBlocks, 20);
            nutrients += count * 100;
            organic += count * 25;
            factors.add("nearby_agriculture");
        }
        if (oreBlocks > 0) {
            minerals += Math.min(oreBlocks, 12) * 125;
            factors.add("nearby_ores");
        }
        if (geothermalBlocks > 0) {
            int count = Math.min(geothermalBlocks, 8);
            minerals += count * 300;
            gases += count * 100;
            factors.add("geothermal_activity");
        }
        if (source.getY() < 32) {
            minerals += 750;
            gases += 250;
            factors.add("underground");
        }
        if (level.isRainingAt(source.above())) {
            solids += 500;
            organic += 100;
            factors.add("rain_runoff");
        }

        return new WaterSampleData(
            baseline.profile(),
            baseline.dissolvedSaltsPpm(),
            minerals,
            solids,
            organic,
            gases,
            nutrients,
            factors.stream().toList()
        );
    }

    private static boolean isSediment(String path) {
        return path.contains("sand") || path.contains("gravel") || path.contains("clay")
            || path.contains("dirt") || path.contains("mud");
    }

    private static boolean isVegetation(String path) {
        return path.contains("leaves") || path.contains("log") || path.contains("grass")
            || path.contains("moss") || path.contains("kelp") || path.contains("seagrass")
            || path.contains("lily_pad");
    }

    private static boolean isAgriculture(String path) {
        return path.contains("farmland") || path.contains("wheat") || path.contains("carrot")
            || path.contains("potato") || path.contains("beetroot") || path.contains("crop");
    }

    private static boolean isOre(String path) {
        return path.endsWith("_ore") || path.contains("ore_");
    }

    private static boolean isGeothermal(String path) {
        return path.contains("lava") || path.contains("magma");
    }
}
