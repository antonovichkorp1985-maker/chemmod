package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.reaction.MolecularPortion;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBatchPlan;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBatchPlanner;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft-content adapter for pure molecular vials. It turns a core batch
 * plan into component replacements, but deliberately does not mutate stacks,
 * choose a recipe UI, or record discoveries. A real transactional reactor must
 * apply the returned replacements first and only then call DiscoverySavedData.
 *
 * Impure samples are rejected rather than silently deleting unknown material.
 * Their handling belongs to the later explicit-mixture model.
 */
public final class PureSubstanceReactionPlanner {
    private final ReactionBatchPlanner planner;

    public PureSubstanceReactionPlanner(ReactionBatchPlanner planner) {
        this.planner = planner;
    }

    public static PureSubstanceReactionPlanner bundled() {
        return new PureSubstanceReactionPlanner(ReactionBatchPlanner.bundled());
    }

    public List<PlannedSubstanceReaction> plan(
        SubstanceContents target,
        List<SubstanceContents> coReactants,
        ReactionRuleId ruleId,
        ReactionEnvironment environment
    ) {
        MolecularPortion targetPortion = purePortion(target);
        List<MolecularPortion> coReactantPortions = coReactants.stream()
            .map(PureSubstanceReactionPlanner::purePortion)
            .toList();

        return planner.plan(targetPortion, coReactantPortions, ruleId, environment).stream()
            .map(plan -> toContentsPlan(target, coReactants, plan))
            .toList();
    }

    private static MolecularPortion purePortion(SubstanceContents contents) {
        if (contents.purityPpm() != 1_000_000) {
            throw new IllegalArgumentException("Impure or mixed substance contents require an explicit mixture process");
        }
        return new MolecularPortion(contents.molecule(), contents.micromoles());
    }

    private static PlannedSubstanceReaction toContentsPlan(
        SubstanceContents target,
        List<SubstanceContents> coReactants,
        ReactionBatchPlan plan
    ) {
        List<Long> coConsumed = plan.getCoReactantConsumedMicromoles();
        if (coReactants.size() != coConsumed.size()) {
            throw new IllegalStateException("Core reaction plan does not match supplied co-reactants");
        }
        List<InputConsumption> coChanges = new ArrayList<>();
        for (int index = 0; index < coReactants.size(); index++) {
            coChanges.add(change(coReactants.get(index), coConsumed.get(index)));
        }
        List<SubstanceContents> products = plan.getProducts().stream()
            .map(portion -> SubstanceContents.fromMolecule(portion.getMolecule(), portion.getMicromoles(), 1_000_000))
            .toList();
        return new PlannedSubstanceReaction(
            plan,
            change(target, plan.getTargetConsumedMicromoles()),
            coChanges,
            products
        );
    }

    private static InputConsumption change(SubstanceContents input, long consumedMicromoles) {
        if (consumedMicromoles <= 0 || consumedMicromoles > input.micromoles()) {
            throw new IllegalStateException("Reaction plan consumes an invalid input amount");
        }
        long remaining = input.micromoles() - consumedMicromoles;
        return new InputConsumption(consumedMicromoles, remaining == 0 ? null : input.withMicromoles(remaining));
    }

    /** Component change for one input vial; a null remainder means the vial is empty. */
    public record InputConsumption(long consumedMicromoles, SubstanceContents remainder) {
        public InputConsumption {
            if (consumedMicromoles <= 0) throw new IllegalArgumentException("Consumed amount must be positive");
        }

        public boolean isFullyConsumed() {
            return remainder == null;
        }
    }

    /** A complete, not-yet-applied pure-vial reaction transaction. */
    public record PlannedSubstanceReaction(
        ReactionBatchPlan batchPlan,
        InputConsumption target,
        List<InputConsumption> coReactants,
        List<SubstanceContents> products
    ) {
        public PlannedSubstanceReaction {
            coReactants = List.copyOf(coReactants);
            products = List.copyOf(products);
            if (products.isEmpty()) throw new IllegalArgumentException("A planned reaction needs products");
        }
    }
}
