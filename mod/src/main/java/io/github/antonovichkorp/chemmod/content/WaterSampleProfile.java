package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/** Baseline water composition selected by biome before local environmental modifiers. */
public enum WaterSampleProfile {
    OCEAN("ocean", 32_000, 0, 1_000, 1_000, 1_000),
    SWAMP("swamp", 0, 2_000, 4_000, 8_000, 1_000),
    RIVER("river", 0, 2_000, 1_500, 1_000, 500),
    SNOWMELT("snowmelt", 0, 800, 200, 200, 800),
    FRESHWATER("freshwater", 0, 1_500, 500, 500, 500);

    private final String id;
    private final int dissolvedSaltsPpm;
    private final int dissolvedMineralsPpm;
    private final int suspendedSolidsPpm;
    private final int organicMatterPpm;
    private final int dissolvedGasesPpm;

    WaterSampleProfile(
        String id,
        int dissolvedSaltsPpm,
        int dissolvedMineralsPpm,
        int suspendedSolidsPpm,
        int organicMatterPpm,
        int dissolvedGasesPpm
    ) {
        this.id = id;
        this.dissolvedSaltsPpm = dissolvedSaltsPpm;
        this.dissolvedMineralsPpm = dissolvedMineralsPpm;
        this.suspendedSolidsPpm = suspendedSolidsPpm;
        this.organicMatterPpm = organicMatterPpm;
        this.dissolvedGasesPpm = dissolvedGasesPpm;
    }

    public String id() {
        return id;
    }

    public WaterSampleData baseline() {
        return new WaterSampleData(
            id,
            dissolvedSaltsPpm,
            dissolvedMineralsPpm,
            suspendedSolidsPpm,
            organicMatterPpm,
            dissolvedGasesPpm,
            0,
            List.of()
        );
    }

    public static WaterSampleProfile at(Level level, BlockPos position) {
        String biomePath = level.getBiome(position).unwrapKey()
            .map(key -> key.location().getPath())
            .orElse("unknown")
            .toLowerCase(Locale.ROOT);

        if (biomePath.contains("ocean")) return OCEAN;
        if (biomePath.contains("swamp") || biomePath.contains("mangrove")) return SWAMP;
        if (biomePath.contains("river")) return RIVER;
        if (biomePath.contains("frozen") || biomePath.contains("snow") || biomePath.contains("ice")) {
            return SNOWMELT;
        }
        return FRESHWATER;
    }

    public static WaterSampleProfile byId(String id) {
        for (WaterSampleProfile profile : values()) {
            if (profile.id.equals(id)) return profile;
        }
        return null;
    }
}
