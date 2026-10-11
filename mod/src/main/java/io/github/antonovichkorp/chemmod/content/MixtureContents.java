package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.mixture.MolecularMixture;
import io.github.antonovichkorp.chemmod.core.reaction.MolecularPortion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit multi-molecule vial contents. It is separate from
 * {@link SubstanceContents}: a stack must hold one or the other, never an
 * implicit "pure substance with unnamed impurities".
 */
public record MixtureContents(int schemaVersion, List<MixturePart> parts) {
    public static final int CURRENT_SCHEMA = 1;

    public static final Codec<MixturePart> PART_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("canonical_key").forGetter(MixturePart::canonicalKey),
        Codec.STRING.fieldOf("structure").forGetter(MixturePart::structuralWitness),
        Codec.LONG.fieldOf("micromoles").forGetter(MixturePart::micromoles)
    ).apply(instance, MixturePart::new));

    public static final Codec<MixtureContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("schema_version", CURRENT_SCHEMA).forGetter(MixtureContents::schemaVersion),
        PART_CODEC.listOf().fieldOf("parts").forGetter(MixtureContents::parts)
    ).apply(instance, MixtureContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MixturePart> PART_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MixturePart decode(RegistryFriendlyByteBuf buffer) {
            return new MixturePart(
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.STRING_UTF8.decode(buffer),
                ByteBufCodecs.VAR_LONG.decode(buffer)
            );
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MixturePart part) {
            ByteBufCodecs.STRING_UTF8.encode(buffer, part.canonicalKey());
            ByteBufCodecs.STRING_UTF8.encode(buffer, part.structuralWitness());
            ByteBufCodecs.VAR_LONG.encode(buffer, part.micromoles());
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, MixtureContents> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MixtureContents decode(RegistryFriendlyByteBuf buffer) {
            int schema = ByteBufCodecs.VAR_INT.decode(buffer);
            int size = ByteBufCodecs.VAR_INT.decode(buffer);
            if (size < 0 || size > 64) throw new IllegalArgumentException("Invalid mixture component count: " + size);
            List<MixturePart> parts = new ArrayList<>(size);
            for (int index = 0; index < size; index++) parts.add(PART_STREAM_CODEC.decode(buffer));
            return new MixtureContents(schema, parts);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MixtureContents contents) {
            ByteBufCodecs.VAR_INT.encode(buffer, contents.schemaVersion());
            ByteBufCodecs.VAR_INT.encode(buffer, contents.parts().size());
            contents.parts().forEach(part -> PART_STREAM_CODEC.encode(buffer, part));
        }
    };

    public MixtureContents {
        if (schemaVersion != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported mixture schema version: " + schemaVersion);
        }
        parts = normalize(parts);
        if (parts.size() < 2) {
            throw new IllegalArgumentException("Mixture contents need at least two distinct molecular identities");
        }
        if (parts.size() > 64) {
            throw new IllegalArgumentException("Mixture contents support at most 64 molecular identities");
        }
    }

    public static MixtureContents fromMolecularMixture(MolecularMixture mixture) {
        if (mixture == null) throw new IllegalArgumentException("Molecular mixture cannot be null");
        return new MixtureContents(
            CURRENT_SCHEMA,
            mixture.portions().stream()
                .map(portion -> new MixturePart(
                    portion.getMolecule().canonicalKey(),
                    portion.getMolecule().structuralWitness(),
                    portion.getMicromoles()
                ))
                .toList()
        );
    }

    public MolecularMixture mixture() {
        return new MolecularMixture(parts.stream()
            .map(part -> new MolecularPortion(part.molecule(), part.micromoles()))
            .toList());
    }

    public long totalMicromoles() {
        return parts.stream().mapToLong(MixturePart::micromoles).reduce(Math::addExact).orElseThrow();
    }

    private static List<MixturePart> normalize(List<MixturePart> input) {
        if (input == null) throw new IllegalArgumentException("Mixture parts cannot be null");
        Map<String, MixturePart> normalized = new LinkedHashMap<>();
        input.forEach(part -> {
            if (part == null) throw new IllegalArgumentException("Mixture part cannot be null");
            MixturePart previous = normalized.get(part.canonicalKey());
            normalized.put(
                part.canonicalKey(),
                previous == null ? part : new MixturePart(
                    previous.canonicalKey(),
                    previous.structuralWitness(),
                    Math.addExact(previous.micromoles(), part.micromoles())
                )
            );
        });
        return normalized.values().stream()
            .sorted(Comparator.comparing(MixturePart::canonicalKey))
            .toList();
    }

    /** One named-by-graph component of a mixture; names and properties stay outside this payload. */
    public record MixturePart(String canonicalKey, String structuralWitness, long micromoles) {
        public MixturePart {
            if (canonicalKey == null || canonicalKey.isBlank()) {
                throw new IllegalArgumentException("Mixture component canonical key cannot be blank");
            }
            if (structuralWitness == null || structuralWitness.isBlank()) {
                throw new IllegalArgumentException("Mixture component structure cannot be blank");
            }
            if (micromoles <= 0) {
                throw new IllegalArgumentException("Mixture component amount must be positive");
            }
            Molecule molecule = Molecule.Companion.fromSMILESlike(structuralWitness);
            if (!molecule.validate().isEmpty() || !molecule.canonicalKey().equals(canonicalKey)) {
                throw new IllegalArgumentException("Mixture component key does not match a valid structural witness");
            }
        }

        public Molecule molecule() {
            return Molecule.Companion.fromSMILESlike(structuralWitness);
        }
    }
}
