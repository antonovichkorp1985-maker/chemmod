package io.github.antonovichkorp.chemmod.content;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Two-phase mutation bridge from a checked reaction plan to actual vial slots.
 *
 * {@link #preparePure(ItemStack, List, PureSubstanceReactionPlanner.PlannedSubstanceReaction, List)}
 * and {@link #prepareMixture(ItemStack, MixtureSubstanceReactionPlanner.PlannedMixtureReaction, List)}
 * validate every source snapshot and reserve one empty vial for every product.
 * {@link #commit()} re-validates all slots before changing any of them. It has
 * no world or discovery side effect: a future reactor must call discovery only
 * after a successful commit.
 */
public final class VialReactionTransaction {
    private final List<InputChange> inputs;
    private final List<OutputChange> outputs;
    private boolean committed;

    private VialReactionTransaction(List<InputChange> inputs, List<OutputChange> outputs) {
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        validateAll();
    }

    /** Prepare a reaction whose target and co-reactants are separate pure-vial slots. */
    public static VialReactionTransaction preparePure(
        ItemStack targetSlot,
        List<ItemStack> coReactantSlots,
        PureSubstanceReactionPlanner.PlannedSubstanceReaction plan,
        List<ItemStack> productSlots
    ) {
        if (plan == null) throw new IllegalArgumentException("Pure reaction plan cannot be null");
        if (coReactantSlots == null || productSlots == null) {
            throw new IllegalArgumentException("Reactor slot lists cannot be null");
        }
        if (coReactantSlots.size() != plan.coReactantInputs().size()
            || coReactantSlots.size() != plan.coReactants().size()) {
            throw new IllegalArgumentException("Pure reaction plan and co-reactant slots have different sizes");
        }

        List<InputChange> inputs = new ArrayList<>();
        inputs.add(new InputChange(
            targetSlot,
            new VialContentsState(plan.targetInput(), null),
            stateFor(plan.target().remainder())
        ));
        for (int index = 0; index < coReactantSlots.size(); index++) {
            inputs.add(new InputChange(
                coReactantSlots.get(index),
                new VialContentsState(plan.coReactantInputs().get(index), null),
                stateFor(plan.coReactants().get(index).remainder())
            ));
        }
        return new VialReactionTransaction(inputs, productChanges(productSlots, plan.products()));
    }

    /** Prepare a reaction whose target/co-reactants were selected from one explicit mixture vial. */
    public static VialReactionTransaction prepareMixture(
        ItemStack sourceSlot,
        MixtureSubstanceReactionPlanner.PlannedMixtureReaction plan,
        List<ItemStack> productSlots
    ) {
        if (plan == null) throw new IllegalArgumentException("Mixture reaction plan cannot be null");
        if (productSlots == null) throw new IllegalArgumentException("Reactor product slots cannot be null");
        return new VialReactionTransaction(
            List.of(new InputChange(
                sourceSlot,
                new VialContentsState(null, plan.sourceInput()),
                plan.sourceReplacement()
            )),
            productChanges(productSlots, plan.products())
        );
    }

    /**
     * Apply every previously validated component replacement. A stale source,
     * occupied output, or changed stack count aborts before the first mutation.
     */
    public void commit() {
        if (committed) throw new IllegalStateException("Reaction transaction was already committed");
        validateAll();
        inputs.forEach(change -> change.replacement().applyTo(change.slot()));
        outputs.forEach(change -> change.contents().applyTo(change.slot()));
        committed = true;
    }

    public boolean isCommitted() {
        return committed;
    }

    private void validateAll() {
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (InputChange input : inputs) {
            if (!seen.add(input.slot())) {
                throw new IllegalArgumentException("A reactor slot cannot be used by more than one input/output role");
            }
            validateSingleVial(input.slot(), "reaction input");
            VialContentsState actual = VialContentsState.fromStack(input.slot());
            if (!input.expected().equals(actual)) {
                throw new IllegalStateException("Reaction input changed after its plan was prepared");
            }
        }
        for (OutputChange output : outputs) {
            if (!seen.add(output.slot())) {
                throw new IllegalArgumentException("A reactor slot cannot be used by more than one input/output role");
            }
            validateSingleVial(output.slot(), "reaction output");
            if (!VialContentsState.fromStack(output.slot()).isEmpty()) {
                throw new IllegalStateException("Reaction output vial is no longer empty");
            }
        }
    }

    private static List<OutputChange> productChanges(List<ItemStack> productSlots, List<SubstanceContents> products) {
        if (productSlots.size() != products.size()) {
            throw new IllegalArgumentException("A reactor needs one reserved empty vial per product portion");
        }
        List<OutputChange> outputs = new ArrayList<>();
        for (int index = 0; index < products.size(); index++) {
            SubstanceContents product = products.get(index);
            if (product == null) throw new IllegalArgumentException("Reaction product cannot be null");
            outputs.add(new OutputChange(productSlots.get(index), new VialContentsState(product, null)));
        }
        return outputs;
    }

    private static VialContentsState stateFor(SubstanceContents contents) {
        return contents == null ? VialContentsState.empty() : new VialContentsState(contents, null);
    }

    private static void validateSingleVial(ItemStack slot, String role) {
        if (slot == null || slot.isEmpty() || !slot.is(ChemItems.SUBSTANCE_VIAL.get()) || slot.getCount() != 1) {
            throw new IllegalArgumentException("A " + role + " must be exactly one ChemMod vial");
        }
    }

    private record InputChange(ItemStack slot, VialContentsState expected, VialContentsState replacement) {
        private InputChange {
            if (slot == null || expected == null || replacement == null) {
                throw new IllegalArgumentException("Reaction input change cannot contain null");
            }
        }
    }

    private record OutputChange(ItemStack slot, VialContentsState contents) {
        private OutputChange {
            if (slot == null || contents == null || contents.isEmpty()) {
                throw new IllegalArgumentException("Reaction output change needs a non-empty vial state");
            }
        }
    }
}
