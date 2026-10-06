package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.antonovichkorp.chemmod.core.Molecule;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A physical amount of substance. Its stable identity is the complete
 * canonical graph key; the structural notation is retained as a verifiable
 * reconstruction witness, not as an ad-hoc item name or catalogue reference.
 */
public record SubstanceContents(
    int schemaVersion,
    String canonicalKey,
    String structuralWitness,
    long micromoles,
    int purityPpm,
    WaterSampleData waterSample
) {
    public static final int CURRENT_SCHEMA = 4;
    private static final String UNSPECIFIED_PROFILE = "unspecified";
    private static final String WATER_CANONICAL_KEY = canonicalKeyFor("O");

    public static final Codec<SubstanceContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", 1).forGetter(SubstanceContents::schemaVersion),
        Codec.STRING.optionalFieldOf("canonical_key", "").forGetter(SubstanceContents::canonicalKey),
        Codec.STRING.fieldOf("structure").forGetter(SubstanceContents::structuralWitness),
        Codec.LONG.fieldOf("micromoles").forGetter(SubstanceContents::micromoles),
        Codec.INT.fieldOf("purity_ppm").forGetter(SubstanceContents::purityPpm),
        Codec.STRING.optionalFieldOf("sample_profile", UNSPECIFIED_PROFILE)
            .forGetter(value -> UNSPECIFIED_PROFILE),
        WaterSampleData.CODEC.optionalFieldOf("water_sample", WaterSampleData.NONE)
            .forGetter(SubstanceContents::waterSample)
    ).apply(instance, SubstanceContents::fromSerialized));

    /** Network peers always share this mod version, so schema-v4 is encoded directly. */
    public static final StreamCodec<RegistryFriendlyByteBuf, SubstanceContents> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SubstanceContents decode(RegistryFriendlyByteBuf buffer) {
            return new SubstanceContents(
                ByteBufCodecs.VAR_INT.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_LONG.decode(buffer),
                ByteBufCodecs.VAR_INT.decode(buffer),
                WaterSampleData.STREAM_CODEC.decode(buffer)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SubstanceContents value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.schemaVersion());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.canonicalKey());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.structuralWitness());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.micromoles());
            ByteBufCodecs.VAR_INT.encode(buffer, value.purityPpm());
            WaterSampleData.STREAM_CODEC.encode(buffer, value.waterSample());
        }
    };

    public SubstanceContents(String structure, long micromoles, int purityPpm) {
        this(CURRENT_SCHEMA, canonicalKeyFor(structure), structure, micromoles, purityPpm, WaterSampleData.NONE);
    }

    public SubstanceContents(String structure, long micromoles, WaterSampleData waterSample) {
        this(CURRENT_SCHEMA, canonicalKeyFor(structure), structure, micromoles, waterSample.purityPpm(), waterSample);
    }

    private static SubstanceContents fromSerialized(
        int serializedSchema,
        String serializedCanonicalKey,
        String structure,
        long micromoles,
        int purityPpm,
        String legacyProfile,
        WaterSampleData waterSample
    ) {
        if (serializedSchema < 1 || serializedSchema > CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported substance schema version: " + serializedSchema);
        }

        String canonicalKey = canonicalKeyFor(structure);
        if (serializedSchema >= CURRENT_SCHEMA && !canonicalKey.equals(serializedCanonicalKey)) {
            throw new IllegalArgumentException("Substance canonical key does not match its structural witness");
        }

        WaterSampleData migrated = waterSample;
        if (serializedSchema < CURRENT_SCHEMA && !migrated.isAnalyzed()) {
            String profileId = legacyProfile;
            if (serializedSchema == 1 && WATER_CANONICAL_KEY.equals(canonicalKey) && purityPpm == 997_000) {
                profileId = WaterSampleProfile.FRESHWATER.id();
            }
            WaterSampleProfile profile = WaterSampleProfile.byId(profileId);
            if (profile != null && profile.baseline().purityPpm() == purityPpm) {
                migrated = profile.baseline();
            }
        }
        return new SubstanceContents(CURRENT_SCHEMA, canonicalKey, structure, micromoles, purityPpm, migrated);
    }

    public SubstanceContents {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported substance schema version: " + schemaVersion);
        }
        if (structuralWitness == null || structuralWitness.isBlank()) {
            throw new IllegalArgumentException("Substance structure cannot be blank");
        }
        if (!canonicalKeyFor(structuralWitness).equals(canonicalKey)) {
            throw new IllegalArgumentException("Substance canonical key does not match its structural witness");
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
            if (!WATER_CANONICAL_KEY.equals(canonicalKey)) {
                throw new IllegalArgumentException("Water analysis can only be attached to H2O");
            }
            if (purityPpm != waterSample.purityPpm()) {
                throw new IllegalArgumentException("Water purity does not match measured impurities");
            }
        }
    }

    /** Reconstruct the validated molecule described by this component's identity witness. */
    public Molecule molecule() {
        return Molecule.Companion.fromSMILESlike(structuralWitness);
    }

    /** Whether this graph identity is the structural H2O identity accepted by water-only mechanics. */
    public boolean isWater() {
        return WATER_CANONICAL_KEY.equals(canonicalKey);
    }

    private static String canonicalKeyFor(String structure) {
        if (structure == null || structure.isBlank()) {
            throw new IllegalArgumentException("Substance structure cannot be blank");
        }
        Molecule molecule = Molecule.Companion.fromSMILESlike(structure);
        if (!molecule.validate().isEmpty()) {
            throw new IllegalArgumentException("Substance structure is not a valid molecular graph");
        }
        return molecule.canonicalKey();
    }
}
