package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.mixture.MolecularMixture;
import io.github.antonovichkorp.chemmod.core.reaction.MolecularPortion;
import net.minecraft.world.item.ItemStack;

/**
 * Staged content state for one vial slot.
 *
 * This is deliberately not another persistent component: an ItemStack stores
 * either {@link SubstanceContents}, {@link MixtureContents}, or neither. A
 * future reactor can stage and validate these values before atomically writing
 * them to its input/output slots.
 */
public record VialContentsState(SubstanceContents substance, MixtureContents mixture) {
    public VialContentsState {
        if (substance != null && mixture != null) {
            throw new IllegalArgumentException("A vial cannot contain both a pure portion and a mixture");
        }
    }

    public static VialContentsState empty() {
        return new VialContentsState(null, null);
    }

    public static VialContentsState fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return empty();
        return new VialContentsState(
            stack.get(ChemComponents.SUBSTANCE.get()),
            stack.get(ChemComponents.MIXTURE.get())
        );
    }

    /**
     * Convert exact core quantities into the one persistent representation a
     * vial can hold. Empty residuals remain empty vials, one component becomes
     * a pure portion, and two or more components remain an explicit mixture.
     */
    public static VialContentsState fromMolecularMixture(MolecularMixture mixture) {
        if (mixture == null) throw new IllegalArgumentException("Molecular mixture cannot be null");
        var portions = mixture.portions();
        if (portions.isEmpty()) return empty();
        if (portions.size() == 1) {
            MolecularPortion portion = portions.getFirst();
            return new VialContentsState(
                SubstanceContents.fromMolecule(portion.getMolecule(), portion.getMicromoles(), 1_000_000),
                null
            );
        }
        return new VialContentsState(null, MixtureContents.fromMolecularMixture(mixture));
    }

    public boolean isEmpty() {
        return substance == null && mixture == null;
    }

    public MolecularMixture molecularMixture() {
        if (substance != null) {
            if (substance.purityPpm() != 1_000_000) {
                throw new IllegalArgumentException("Impure vial contents cannot be converted into an explicit mixture");
            }
            return new MolecularMixture(java.util.List.of(
                new MolecularPortion(substance.molecule(), substance.micromoles())
            ));
        }
        return mixture == null ? new MolecularMixture(java.util.List.of()) : mixture.mixture();
    }

    /** Replace only ChemMod molecular-content components, preserving item identity and other components. */
    public void applyTo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) throw new IllegalArgumentException("Vial stack cannot be empty");
        stack.remove(ChemComponents.SUBSTANCE.get());
        stack.remove(ChemComponents.MIXTURE.get());
        if (substance != null) stack.set(ChemComponents.SUBSTANCE.get(), substance);
        if (mixture != null) stack.set(ChemComponents.MIXTURE.get(), mixture);
    }
}
