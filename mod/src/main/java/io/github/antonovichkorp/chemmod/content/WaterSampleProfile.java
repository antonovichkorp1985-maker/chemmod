package io.github.antonovichkorp.chemmod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic, deliberately coarse gameplay model for water collected from a biome.
 * Impurity groups are honest aggregates until the chemistry core supports the ions,
 * microorganisms, and suspended mixtures needed for a more detailed analysis.
 */
public enum WaterSampleProfile {
    OCEAN("ocean", 965_000, List.of(
        new Impurity("dissolved_salts", 32_000),
        new Impurity("suspended_solids", 1_000),
        new Impurity("organic_matter", 1_000),
        new Impurity("dissolved_gases", 1_000)
    )),
    SWAMP("swamp", 985_000, List.of(
        new Impurity("organic_matter", 8_000),
        new Impurity("suspended_solids", 4_000),
        new Impurity("dissolved_minerals", 2_000),
        new Impurity("dissolved_gases", 1_000)
    )),
    RIVER("river", 995_000, List.of(
        new Impurity("dissolved_minerals", 2_000),
        new Impurity("suspended_solids", 1_500),
        new Impurity("organic_matter", 1_000),
        new Impurity("dissolved_gases", 500)
    )),
    SNOWMELT("snowmelt", 998_000, List.of(
        new Impurity("dissolved_gases", 800),
        new Impurity("dissolved_minerals", 800),
        new Impurity("suspended_solids", 200),
        new Impurity("organic_matter", 200)
    )),
    FRESHWATER("freshwater", 997_000, List.of(
        new Impurity("dissolved_minerals", 1_500),
        new Impurity("dissolved_gases", 500),
        new Impurity("suspended_solids", 500),
        new Impurity("organic_matter", 500)
    ));

    private final String id;
    private final int purityPpm;
    private final List<Impurity> impurities;

    WaterSampleProfile(String id, int purityPpm, List<Impurity> impurities) {
        this.id = id;
        this.purityPpm = purityPpm;
        this.impurities = impurities;
        int impurityTotal = impurities.stream().mapToInt(Impurity::ppm).sum();
        if (impurityTotal != 1_000_000 - purityPpm) {
            throw new IllegalArgumentException("Water profile " + id + " does not close its ppm balance");
        }
    }

    public String id() {
        return id;
    }

    public int purityPpm() {
        return purityPpm;
    }

    public List<Impurity> impurities() {
        return impurities;
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

    public record Impurity(String id, int ppm) {
        public Impurity {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Impurity ID cannot be blank");
            if (ppm <= 0) throw new IllegalArgumentException("Impurity ppm must be positive");
        }
    }
}
