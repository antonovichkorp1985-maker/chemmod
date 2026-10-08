package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/** The measured composition and environmental evidence frozen into a collected water sample. */
public record WaterSampleData(
    String profile,
    int dissolvedSaltsPpm,
    int dissolvedMineralsPpm,
    int suspendedSolidsPpm,
    int organicMatterPpm,
    int dissolvedGasesPpm,
    int nutrientsPpm,
    List<String> factors
) {
    public static final WaterSampleData NONE = new WaterSampleData(
        "none", 0, 0, 0, 0, 0, 0, List.of()
    );

    public static final Codec<WaterSampleData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("profile").forGetter(WaterSampleData::profile),
        Codec.INT.fieldOf("dissolved_salts_ppm").forGetter(WaterSampleData::dissolvedSaltsPpm),
        Codec.INT.fieldOf("dissolved_minerals_ppm").forGetter(WaterSampleData::dissolvedMineralsPpm),
        Codec.INT.fieldOf("suspended_solids_ppm").forGetter(WaterSampleData::suspendedSolidsPpm),
        Codec.INT.fieldOf("organic_matter_ppm").forGetter(WaterSampleData::organicMatterPpm),
        Codec.INT.fieldOf("dissolved_gases_ppm").forGetter(WaterSampleData::dissolvedGasesPpm),
        Codec.INT.fieldOf("nutrients_ppm").forGetter(WaterSampleData::nutrientsPpm),
        Codec.STRING.listOf().optionalFieldOf("factors", List.of()).forGetter(WaterSampleData::factors)
    ).apply(instance, WaterSampleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WaterSampleData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public WaterSampleData decode(RegistryFriendlyByteBuf buffer) {
            String profile = ByteBufCodecs.STRING_UTF8.decode(buffer);
            int salts = ByteBufCodecs.VAR_INT.decode(buffer);
            int minerals = ByteBufCodecs.VAR_INT.decode(buffer);
            int solids = ByteBufCodecs.VAR_INT.decode(buffer);
            int organic = ByteBufCodecs.VAR_INT.decode(buffer);
            int gases = ByteBufCodecs.VAR_INT.decode(buffer);
            int nutrients = ByteBufCodecs.VAR_INT.decode(buffer);
            int factorCount = ByteBufCodecs.VAR_INT.decode(buffer);
            if (factorCount < 0 || factorCount > 32) {
                throw new IllegalArgumentException("Invalid water factor count: " + factorCount);
            }
            List<String> factors = new ArrayList<>(factorCount);
            for (int i = 0; i < factorCount; i++) {
                factors.add(ByteBufCodecs.STRING_UTF8.decode(buffer));
            }
            return new WaterSampleData(profile, salts, minerals, solids, organic, gases, nutrients, factors);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, WaterSampleData value) {
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.profile());
            ByteBufCodecs.VAR_INT.encode(buffer, value.dissolvedSaltsPpm());
            ByteBufCodecs.VAR_INT.encode(buffer, value.dissolvedMineralsPpm());
            ByteBufCodecs.VAR_INT.encode(buffer, value.suspendedSolidsPpm());
            ByteBufCodecs.VAR_INT.encode(buffer, value.organicMatterPpm());
            ByteBufCodecs.VAR_INT.encode(buffer, value.dissolvedGasesPpm());
            ByteBufCodecs.VAR_INT.encode(buffer, value.nutrientsPpm());
            ByteBufCodecs.VAR_INT.encode(buffer, value.factors().size());
            for (String factor : value.factors()) {
                ByteBufCodecs.STRING_UTF8.encode(buffer, factor);
            }
        }
    };

    public WaterSampleData {
        if (profile == null || profile.isBlank()) {
            throw new IllegalArgumentException("Water profile cannot be blank");
        }
        int[] values = {
            dissolvedSaltsPpm,
            dissolvedMineralsPpm,
            suspendedSolidsPpm,
            organicMatterPpm,
            dissolvedGasesPpm,
            nutrientsPpm
        };
        for (int value : values) {
            if (value < 0 || value > 1_000_000) {
                throw new IllegalArgumentException("Water impurity ppm must be 0..1,000,000");
            }
        }
        factors = List.copyOf(factors);
        if (factors.size() > 32 || factors.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Invalid water environmental factors");
        }
        long total = 0;
        for (int value : values) total += value;
        if (total > 1_000_000) {
            throw new IllegalArgumentException("Water impurities exceed 1,000,000 ppm");
        }
    }

    public boolean isAnalyzed() {
        return !"none".equals(profile);
    }

    public int totalImpuritiesPpm() {
        return dissolvedSaltsPpm + dissolvedMineralsPpm + suspendedSolidsPpm
            + organicMatterPpm + dissolvedGasesPpm + nutrientsPpm;
    }

    public int purityPpm() {
        return 1_000_000 - totalImpuritiesPpm();
    }

    public List<Impurity> impurities() {
        List<Impurity> result = new ArrayList<>();
        addIfPresent(result, "dissolved_salts", dissolvedSaltsPpm);
        addIfPresent(result, "dissolved_minerals", dissolvedMineralsPpm);
        addIfPresent(result, "suspended_solids", suspendedSolidsPpm);
        addIfPresent(result, "organic_matter", organicMatterPpm);
        addIfPresent(result, "dissolved_gases", dissolvedGasesPpm);
        addIfPresent(result, "nutrients", nutrientsPpm);
        return List.copyOf(result);
    }

    private static void addIfPresent(List<Impurity> result, String id, int ppm) {
        if (ppm > 0) result.add(new Impurity(id, ppm));
    }

    public record Impurity(String id, int ppm) {}
}
