package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Server-authoritative isotope sample state. This component stores physical
 * active mass in micrograms; activity and future dose calculations are always
 * derived from the canonical radiation core, never cached on the item.
 */
public record RadioactiveContents(
    int schemaVersion,
    String nuclideId,
    long activeMassMicrograms,
    boolean secularEquilibrium
) {
    public static final int CURRENT_SCHEMA = 1;

    public static final Codec<RadioactiveContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA).forGetter(RadioactiveContents::schemaVersion),
        Codec.STRING.fieldOf("nuclide_id").forGetter(RadioactiveContents::nuclideId),
        Codec.LONG.fieldOf("active_mass_micrograms").forGetter(RadioactiveContents::activeMassMicrograms),
        Codec.BOOL.optionalFieldOf("secular_equilibrium", false).forGetter(RadioactiveContents::secularEquilibrium)
    ).apply(instance, RadioactiveContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RadioactiveContents> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        RadioactiveContents::schemaVersion,
        ByteBufCodecs.STRING_UTF8,
        RadioactiveContents::nuclideId,
        ByteBufCodecs.VAR_LONG,
        RadioactiveContents::activeMassMicrograms,
        ByteBufCodecs.BOOL,
        RadioactiveContents::secularEquilibrium,
        RadioactiveContents::new
    );

    public RadioactiveContents(String nuclideId, long activeMassMicrograms, boolean secularEquilibrium) {
        this(CURRENT_SCHEMA, nuclideId, activeMassMicrograms, secularEquilibrium);
    }

    public RadioactiveContents {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported radioactive contents schema: " + schemaVersion);
        }
        if (nuclideId == null || !nuclideId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid radioactive nuclide id: " + nuclideId);
        }
        if (activeMassMicrograms <= 0) {
            throw new IllegalArgumentException("Radioactive active mass must be positive");
        }
    }
}
