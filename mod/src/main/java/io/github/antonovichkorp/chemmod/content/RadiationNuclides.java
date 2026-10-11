package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.radiation.DecayNetwork;
import io.github.antonovichkorp.chemmod.core.radiation.Nuclide;
import io.github.antonovichkorp.chemmod.core.radiation.Thorium232Series;

import java.util.Map;
import java.util.Optional;

/**
 * The intentionally small Minecraft boundary for canonical radiation data.
 * A datapack registry can replace this table once isotope-bearing materials and
 * their processing route are part of the playable progression.
 */
public final class RadiationNuclides {
    public static final String THORIUM_232 = "chemmod:thorium_232";

    private static final Map<String, NuclideDefinition> DEFINITIONS = Map.of(
        THORIUM_232,
        new NuclideDefinition(Thorium232Series.THORIUM_232, Thorium232Series.network)
    );

    private RadiationNuclides() {}

    public static Optional<NuclideDefinition> find(String id) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static double activityBecquerel(RadioactiveContents contents) {
        NuclideDefinition definition = find(contents.nuclideId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown radioactive nuclide: " + contents.nuclideId()));
        return definition.network().activityBecquerel(
            definition.nuclide(),
            contents.activeMassMicrograms() / 1_000_000_000.0
        );
    }

    public record NuclideDefinition(Nuclide nuclide, DecayNetwork network) {}
}
