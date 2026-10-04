package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SubstanceContents(
    int schemaVersion,
    String structure,
    long micromoles,
    int purityPpm,
    WaterSampleData waterSample
) {
    public static final int CURRENT_SCHEMA = 3;
    private static final String UNSPECIFIED_PROFILE = "unspecified";

    public static final Codec<SubstanceContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", 1).forGetter(SubstanceContents::schemaVersion),
        Codec.STRING.fieldOf("structure").forGetter(SubstanceContents::structure),
        Codec.LONG.fieldOf("micromoles").forGetter(SubstanceContents::micromoles),
        Codec.INT.fieldOf("purity_ppm").forGetter(SubstanceContents::purityPpm),
        Codec.STRING.optionalFieldOf("sample_profile", UNSPECIFIED_PROFILE)
            .forGetter(value -> UNSPECIFIED_PROFILE),
        WaterSampleData.CODEC.optionalFieldOf("water_sample", WaterSampleData.NONE)
            .forGetter(SubstanceContents::waterSample)
    ).apply(instance, SubstanceContents::fromSerialized));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubstanceContents> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        SubstanceContents::schemaVersion,
        ByteBufCodecs.STRING_UTF8,
        SubstanceContents::structure,
        ByteBufCodecs.VAR_LONG,
        SubstanceContents::micromoles,
        ByteBufCodecs.VAR_INT,
        SubstanceContents::purityPpm,
        WaterSampleData.STREAM_CODEC,
        SubstanceContents::waterSample,
        SubstanceContents::new
    );

    public SubstanceContents(String structure, long micromoles, int purityPpm) {
        this(CURRENT_SCHEMA, structure, micromoles, purityPpm, WaterSampleData.NONE);
    }

    public SubstanceContents(String structure, long micromoles, WaterSampleData waterSample) {
        this(CURRENT_SCHEMA, structure, micromoles, waterSample.purityPpm(), waterSample);
    }

    private static SubstanceContents fromSerialized(
        int serializedSchema,
        String structure,
        long micromoles,
        int purityPpm,
        String legacyProfile,
        WaterSampleData waterSample
    ) {
        if (serializedSchema < 1 || serializedSchema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported substance schema version: " + serializedSchema);
        }

        WaterSampleData migrated = waterSample;
        if (serializedSchema < CURRENT_SCHEMA && !migrated.isAnalyzed()) {
            String profileId = legacyProfile;
            if (serializedSchema == 1 && "O".equals(structure) && purityPpm == 997_000) {
                profileId = WaterSampleProfile.FRESHWATER.id();
            }
            WaterSampleProfile profile = WaterSampleProfile.byId(profileId);
            if (profile != null && profile.baseline().purityPpm() == purityPpm) {
                migrated = profile.baseline();
            }
        }
        return new SubstanceContents(CURRENT_SCHEMA, structure, micromoles, purityPpm, migrated);
    }

    public SubstanceContents {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported substance schema version: " + schemaVersion);
        }
        if (structure == null || structure.isBlank()) {
            throw new IllegalArgumentException("Substance structure cannot be blank");
        }
        if (micromoles <= 0) {
            throw new IllegalArgumentException("Substance amount must be positive");
        }
        if (purityPpm < 0 || purityPpm > 1_000_000) {
            throw new IllegalArgumentException("Purity must be between 0 and 1,000,000 ppm");
        }
        if (waterSample == null) {
            throw new IllegalArgumentException("Water sample data cannot be null");
        }
        if (waterSample.isAnalyzed()) {
            if (!"O".equals(structure)) {
                throw new IllegalArgumentException("Water analysis can only be attached to H2O");
            }
            if (purityPpm != waterSample.purityPpm()) {
                throw new IllegalArgumentException("Water purity does not match measured impurities");
            }
        }
    }
}
