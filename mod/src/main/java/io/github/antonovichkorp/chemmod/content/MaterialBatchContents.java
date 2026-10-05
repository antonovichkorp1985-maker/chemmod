package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent server-authoritative state for a canonical material form. */
public record MaterialBatchContents(
    int schemaVersion,
    String materialId,
    String form,
    long massMicrograms,
    int purityPpm,
    Map<String, Integer> impuritiesPpm
) {
    public static final int CURRENT_SCHEMA = 1;

    public static final Codec<MaterialBatchContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA).forGetter(MaterialBatchContents::schemaVersion),
        Codec.STRING.fieldOf("material_id").forGetter(MaterialBatchContents::materialId),
        Codec.STRING.fieldOf("form").forGetter(MaterialBatchContents::form),
        Codec.LONG.fieldOf("mass_micrograms").forGetter(MaterialBatchContents::massMicrograms),
        Codec.INT.fieldOf("purity_ppm").forGetter(MaterialBatchContents::purityPpm),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("impurities_ppm", Map.of())
            .forGetter(MaterialBatchContents::impuritiesPpm)
    ).apply(instance, MaterialBatchContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialBatchContents> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MaterialBatchContents decode(RegistryFriendlyByteBuf buffer) {
            int schema = ByteBufCodecs.VAR_INT.decode(buffer);
            String materialId = ByteBufCodecs.STRING_UTF8.decode(buffer);
            String form = ByteBufCodecs.STRING_UTF8.decode(buffer);
            long mass = ByteBufCodecs.VAR_LONG.decode(buffer);
            int purity = ByteBufCodecs.VAR_INT.decode(buffer);
            int impurityCount = ByteBufCodecs.VAR_INT.decode(buffer);
            if (impurityCount < 0 || impurityCount > 64) {
                throw new IllegalArgumentException("Invalid material impurity count: " + impurityCount);
            }
            Map<String, Integer> impurities = new LinkedHashMap<>();
            for (int i = 0; i < impurityCount; i++) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buffer);
                int ppm = ByteBufCodecs.VAR_INT.decode(buffer);
                if (impurities.put(id, ppm) != null) {
                    throw new IllegalArgumentException("Duplicate material impurity: " + id);
                }
            }
            return new MaterialBatchContents(schema, materialId, form, mass, purity, impurities);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MaterialBatchContents value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.schemaVersion());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.materialId());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.form());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.massMicrograms());
            ByteBufCodecs.VAR_INT.encode(buffer, value.purityPpm());
            ByteBufCodecs.VAR_INT.encode(buffer, value.impuritiesPpm().size());
            value.impuritiesPpm().forEach((id, ppm) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, id);
                ByteBufCodecs.VAR_INT.encode(buffer, ppm);
            });
        }
    };

    public MaterialBatchContents(String materialId, String form, long massMicrograms, int purityPpm) {
        this(CURRENT_SCHEMA, materialId, form, massMicrograms, purityPpm, Map.of());
    }

    public MaterialBatchContents {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported material batch schema: " + schemaVersion);
        }
        if (materialId == null || !materialId.matches("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid material ID: " + materialId);
        }
        if (form == null || !form.matches("[A-Z][A-Z_]*")) {
            throw new IllegalArgumentException("Invalid material form: " + form);
        }
        if (massMicrograms <= 0) throw new IllegalArgumentException("Material mass must be positive");
        if (purityPpm < 0 || purityPpm > 1_000_000) {
            throw new IllegalArgumentException("Material purity must be 0..1,000,000 ppm");
        }
        impuritiesPpm = Map.copyOf(impuritiesPpm);
        long knownImpurities = 0;
        for (Map.Entry<String, Integer> entry : impuritiesPpm.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Invalid material impurity entry");
            }
            if (entry.getKey().equals(materialId)) {
                throw new IllegalArgumentException("A material cannot be its own impurity");
            }
            knownImpurities += entry.getValue();
        }
        if (knownImpurities > 1_000_000L - purityPpm) {
            throw new IllegalArgumentException("Known impurities exceed the non-primary material fraction");
        }
    }
}
