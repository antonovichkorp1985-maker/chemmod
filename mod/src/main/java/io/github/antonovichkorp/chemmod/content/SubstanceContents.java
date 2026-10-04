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
    String sampleProfile
) {
    public static final int CURRENT_SCHEMA = 2;
    public static final String LABORATORY_PROFILE = "laboratory";
    public static final String UNSPECIFIED_PROFILE = "unspecified";

    public static final Codec<SubstanceContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", 1).forGetter(SubstanceContents::schemaVersion),
        Codec.STRING.fieldOf("structure").forGetter(SubstanceContents::structure),
        Codec.LONG.fieldOf("micromoles").forGetter(SubstanceContents::micromoles),
        Codec.INT.fieldOf("purity_ppm").forGetter(SubstanceContents::purityPpm),
        Codec.STRING.optionalFieldOf("sample_profile", UNSPECIFIED_PROFILE)
            .forGetter(SubstanceContents::sampleProfile)
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
        ByteBufCodecs.STRING_UTF8,
        SubstanceContents::sampleProfile,
        SubstanceContents::new
    );

    public SubstanceContents(String structure, long micromoles, int purityPpm) {
        this(CURRENT_SCHEMA, structure, micromoles, purityPpm, LABORATORY_PROFILE);
    }

    public SubstanceContents(String structure, long micromoles, int purityPpm, String sampleProfile) {
        this(CURRENT_SCHEMA, structure, micromoles, purityPpm, sampleProfile);
    }

    private static SubstanceContents fromSerialized(
        int serializedSchema,
        String structure,
        long micromoles,
        int purityPpm,
        String sampleProfile
    ) {
        if (serializedSchema < 1 || serializedSchema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported substance schema version: " + serializedSchema);
        }
        String migratedProfile = sampleProfile;
        if (serializedSchema == 1 && "O".equals(structure) && purityPpm == 997_000) {
            migratedProfile = WaterSampleProfile.FRESHWATER.id();
        }
        return new SubstanceContents(CURRENT_SCHEMA, structure, micromoles, purityPpm, migratedProfile);
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
        if (sampleProfile == null || sampleProfile.isBlank()) {
            throw new IllegalArgumentException("Sample profile cannot be blank");
        }
        if (!LABORATORY_PROFILE.equals(sampleProfile)
            && !UNSPECIFIED_PROFILE.equals(sampleProfile)
            && WaterSampleProfile.byId(sampleProfile) == null) {
            throw new IllegalArgumentException("Unknown sample profile: " + sampleProfile);
        }
    }
}
