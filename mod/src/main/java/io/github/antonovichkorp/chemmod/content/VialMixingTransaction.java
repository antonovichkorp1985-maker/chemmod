package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.mixture.MolecularMixture;

/**
 * Exact, side-effect-free mixing of two physical vial states.
 *
 * Callers must construct this result before replacing either input stack. That
 * ordering makes stack changes transactional: an invalid/impure state or an
 * overflowing amount leaves both inputs untouched.
 */
public final class VialMixingTransaction {
    private VialMixingTransaction() {}

    public static VialContentsState combine(VialContentsState first, VialContentsState second) {
        if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
            throw new IllegalArgumentException("Mixing needs two non-empty vial states");
        }
        MolecularMixture combined = first.molecularMixture().plus(second.molecularMixture().portions());
        return VialContentsState.fromMolecularMixture(combined);
    }
}
