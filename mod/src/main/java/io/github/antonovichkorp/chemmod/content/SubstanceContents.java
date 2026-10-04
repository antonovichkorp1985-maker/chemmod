package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record SubstanceContents(int schemaVersion, String structure, long micromoles, int purityPpm) {
    public static final int CURRENT_SCHEMA = 1;

    public static final Codec<SubstanceContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA).forGetter(SubstanceContents::schemaVersion),
        Codec.STRING.fieldOf("structure").forGetter(SubstanceContents::structure),
        Codec.LONG.fieldOf("micromoles").forGetter(SubstanceContents::micromoles),
        Codec.INT.fieldOf("purity_ppm").forGetter(SubstanceContents::purityPpm)
    ).apply(instance, SubstanceContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubstanceContents> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        SubstanceContents::schemaVersion,
        ByteBufCodecs.STRING_UTF8,
        SubstanceContents::structure,
        ByteBufCodecs.VAR_LONG,
        SubstanceContents::micromoles,
        ByteBufCodecs.VAR_INT,
        SubstanceContents::purityPpm,
        SubstanceContents::new
    );

    public SubstanceContents(String structure, long micromoles, int purityPpm) {
        this(CURRENT_SCHEMA, structure, micromoles, purityPpm);
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
    }
}
