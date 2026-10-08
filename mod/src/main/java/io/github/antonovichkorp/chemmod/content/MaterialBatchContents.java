package io.github.antonovichkorp.chemmod.content;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Persistent server-authoritative physical material state.
 *
 * Schema 2 stores the exact mass of primary material and every named impurity.
 * Percentages are derived only for UI. The persistent codec upgrades schema 1
 * (ppm-only) item stacks when they are read, preserving all represented mass.
 */
public record MaterialBatchContents(
    int schemaVersion,
    String materialId,
    String form,
    long massMicrograms,
    long primaryMassMicrograms,
    Map<String, Long> impurityMassMicrograms
) {
    public static final int CURRENT_SCHEMA = 2;
    private static final long PARTS_PER_MILLION = 1_000_000L;
    // Schema 1 only shipped copper batches; its unallocated analytical remainder
    // was already defined as silicate gangue by the v0.6 washer fallback.
    private static final String LEGACY_UNCLASSIFIED_GANGUE = "chemmod:silicate_gangue";

    private static final Codec<MaterialBatchContents> CURRENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA).forGetter(MaterialBatchContents::schemaVersion),
        Codec.STRING.fieldOf("material_id").forGetter(MaterialBatchContents::materialId),
        Codec.STRING.fieldOf("form").forGetter(MaterialBatchContents::form),
        Codec.LONG.fieldOf("mass_micrograms").forGetter(MaterialBatchContents::massMicrograms),
        Codec.LONG.fieldOf("primary_mass_micrograms").forGetter(MaterialBatchContents::primaryMassMicrograms),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("impurity_mass_micrograms", Map.of())
            .forGetter(MaterialBatchContents::impurityMassMicrograms)
    ).apply(instance, MaterialBatchContents::new));

    private static final Codec<LegacyBatchContents> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", 1).forGetter(LegacyBatchContents::schemaVersion),
        Codec.STRING.fieldOf("material_id").forGetter(LegacyBatchContents::materialId),
        Codec.STRING.fieldOf("form").forGetter(LegacyBatchContents::form),
        Codec.LONG.fieldOf("mass_micrograms").forGetter(LegacyBatchContents::massMicrograms),
        Codec.INT.fieldOf("purity_ppm").forGetter(LegacyBatchContents::purityPpm),
        Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("impurities_ppm", Map.of())
            .forGetter(LegacyBatchContents::impuritiesPpm)
    ).apply(instance, LegacyBatchContents::new));

    /** Writes schema 2 while accepting schema 1 data from prior worlds. */
    public static final Codec<MaterialBatchContents> CODEC = Codec.either(CURRENT_CODEC, LEGACY_CODEC)
        .xmap(
            value -> value.map(Function.identity(), LegacyBatchContents::upgrade),
            Either::left
        );

    /** Network peers run one mod version, so packets carry compact schema 2 only. */
    public static final StreamCodec<RegistryFriendlyByteBuf, MaterialBatchContents> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MaterialBatchContents decode(RegistryFriendlyByteBuf buffer) {
            int schema = ByteBufCodecs.VAR_INT.decode(buffer);
            if (schema != CURRENT_SCHEMA) {
                throw new IllegalArgumentException("Unsupported network material batch schema: " + schema);
            }
            String materialId = ByteBufCodecs.STRING_UTF8.decode(buffer);
            String form = ByteBufCodecs.STRING_UTF8.decode(buffer);
            long mass = ByteBufCodecs.VAR_LONG.decode(buffer);
            long primaryMass = ByteBufCodecs.VAR_LONG.decode(buffer);
            int impurityCount = ByteBufCodecs.VAR_INT.decode(buffer);
            if (impurityCount < 0 || impurityCount > 64) {
                throw new IllegalArgumentException("Invalid material impurity count: " + impurityCount);
            }
            Map<String, Long> impurities = new LinkedHashMap<>();
            for (int i = 0; i < impurityCount; i++) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buffer);
                long impurityMass = ByteBufCodecs.VAR_LONG.decode(buffer);
                if (impurities.put(id, impurityMass) != null) {
                    throw new IllegalArgumentException("Duplicate material impurity: " + id);
                }
            }
            return new MaterialBatchContents(schema, materialId, form, mass, primaryMass, impurities);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MaterialBatchContents value) {
            ByteBufCodecs.VAR_INT.encode(buffer, CURRENT_SCHEMA);
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.materialId());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.form());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.massMicrograms());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.primaryMassMicrograms());
            ByteBufCodecs.VAR_INT.encode(buffer, value.impurityMassMicrograms().size());
            value.impurityMassMicrograms().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ByteBufCodecs.STRING_UTF8.encode(buffer, entry.getKey());
                    ByteBufCodecs.VAR_LONG.encode(buffer, entry.getValue());
                });
        }
    };

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
        if (primaryMassMicrograms < 0) throw new IllegalArgumentException("Primary material mass cannot be negative");
        impurityMassMicrograms = Map.copyOf(impurityMassMicrograms);
        long impurityMass = 0L;
        for (Map.Entry<String, Long> entry : impurityMassMicrograms.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")
                || entry.getValue() == null || entry.getValue() <= 0L) {
                throw new IllegalArgumentException("Invalid material impurity entry");
            }
            if (entry.getKey().equals(materialId)) {
                throw new IllegalArgumentException("A material cannot be its own impurity");
            }
            impurityMass = Math.addExact(impurityMass, entry.getValue());
        }
        if (Math.addExact(primaryMassMicrograms, impurityMass) != massMicrograms) {
            throw new IllegalArgumentException("Every microgram in a material batch must be assigned to a component");
        }
    }

    public MaterialBatchContents(String materialId, String form, long massMicrograms, long primaryMassMicrograms,
                                 Map<String, Long> impurityMassMicrograms) {
        this(CURRENT_SCHEMA, materialId, form, massMicrograms, primaryMassMicrograms, impurityMassMicrograms);
    }

    /** Creates a pure reference batch. Creative/admin stacks therefore remain 100% pure. */
    public MaterialBatchContents(String materialId, String form, long massMicrograms) {
        this(CURRENT_SCHEMA, materialId, form, massMicrograms, massMicrograms, Map.of());
    }

    /** Compatibility constructor for Java callers that still express a pure batch in ppm. */
    public MaterialBatchContents(String materialId, String form, long massMicrograms, int purityPpm) {
        this(new LegacyBatchContents(1, materialId, form, massMicrograms, purityPpm, Map.of()).upgrade());
    }

    /** Compatibility constructor for old save/block-entity paths using ppm composition. */
    public MaterialBatchContents(int legacySchema, String materialId, String form, long massMicrograms,
                                 int purityPpm, Map<String, Integer> impuritiesPpm) {
        this(new LegacyBatchContents(legacySchema, materialId, form, massMicrograms, purityPpm, impuritiesPpm).upgrade());
    }

    private MaterialBatchContents(MaterialBatchContents upgraded) {
        this(
            upgraded.schemaVersion(), upgraded.materialId(), upgraded.form(), upgraded.massMicrograms(),
            upgraded.primaryMassMicrograms(), upgraded.impurityMassMicrograms()
        );
    }

    /** Rounded UI value. Exact processing must use [primaryMassMicrograms]. */
    public int purityPpm() {
        return proportionPpm(primaryMassMicrograms);
    }

    /** Rounded UI values for each named impurity. */
    public Map<String, Integer> impuritiesPpm() {
        Map<String, Integer> result = new LinkedHashMap<>();
        impurityMassMicrograms.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> result.put(entry.getKey(), proportionPpm(entry.getValue())));
        return Map.copyOf(result);
    }

    private int proportionPpm(long componentMass) {
        BigInteger numerator = BigInteger.valueOf(componentMass)
            .multiply(BigInteger.valueOf(PARTS_PER_MILLION))
            .add(BigInteger.valueOf(massMicrograms / 2));
        return numerator.divide(BigInteger.valueOf(massMicrograms)).intValueExact();
    }

    private record LegacyBatchContents(
        int schemaVersion,
        String materialId,
        String form,
        long massMicrograms,
        int purityPpm,
        Map<String, Integer> impuritiesPpm
    ) {
        private MaterialBatchContents upgrade() {
            if (schemaVersion != 1) {
                throw new IllegalArgumentException("Unsupported legacy material batch schema: " + schemaVersion);
            }
            if (massMicrograms <= 0 || purityPpm < 0 || purityPpm > PARTS_PER_MILLION) {
                throw new IllegalArgumentException("Invalid legacy material batch composition");
            }
            Map<String, Integer> known = new LinkedHashMap<>(impuritiesPpm == null ? Map.of() : impuritiesPpm);
            long knownPpm = 0L;
            for (Map.Entry<String, Integer> entry : known.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0) {
                    throw new IllegalArgumentException("Invalid legacy impurity concentration");
                }
                knownPpm = Math.addExact(knownPpm, entry.getValue());
            }
            long unnamedPpm = PARTS_PER_MILLION - purityPpm - knownPpm;
            if (unnamedPpm < 0) {
                throw new IllegalArgumentException("Legacy impurities exceed the non-primary fraction");
            }
            if (unnamedPpm > 0) {
                known.merge(LEGACY_UNCLASSIFIED_GANGUE, Math.toIntExact(unnamedPpm), Math::addExact);
            }

            Map<String, Long> weights = new LinkedHashMap<>();
            weights.put("__primary_component__", (long) purityPpm);
            known.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> weights.put(entry.getKey(), entry.getValue().longValue()));
            Map<String, Long> allocated = allocateByWeight(weights, massMicrograms);
            long primaryMass = allocated.getOrDefault("__primary_component__", 0L);
            Map<String, Long> impurities = new LinkedHashMap<>(allocated);
            impurities.remove("__primary_component__");
            return new MaterialBatchContents(CURRENT_SCHEMA, materialId, form, massMicrograms, primaryMass, impurities);
        }
    }

    private static Map<String, Long> allocateByWeight(Map<String, Long> weights, long requestedMass) {
        long weightTotal = weights.values().stream().mapToLong(Long::longValue).reduce(0L, Math::addExact);
        if (weightTotal != PARTS_PER_MILLION) {
            throw new IllegalArgumentException("Legacy composition must sum to one million ppm");
        }
        BigInteger denominator = BigInteger.valueOf(weightTotal);
        BigInteger requested = BigInteger.valueOf(requestedMass);
        record Allocation(String id, long base, BigInteger remainder) {}
        List<Allocation> allocations = new ArrayList<>();
        for (Map.Entry<String, Long> entry : weights.entrySet()) {
            BigInteger product = BigInteger.valueOf(entry.getValue()).multiply(requested);
            allocations.add(new Allocation(
                entry.getKey(),
                product.divide(denominator).longValueExact(),
                product.remainder(denominator)
            ));
        }
        long alreadyAllocated = allocations.stream().mapToLong(Allocation::base).reduce(0L, Math::addExact);
        long remainder = Math.subtractExact(requestedMass, alreadyAllocated);
        Map<String, Long> result = new LinkedHashMap<>();
        allocations.forEach(allocation -> result.put(allocation.id(), allocation.base()));
        allocations.stream()
            .sorted(Comparator.comparing(Allocation::remainder).reversed().thenComparing(Allocation::id))
            .limit(remainder)
            .forEach(allocation -> result.merge(allocation.id(), 1L, Math::addExact));
        return result.entrySet().stream()
            .filter(entry -> entry.getValue() > 0)
            .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }
}
