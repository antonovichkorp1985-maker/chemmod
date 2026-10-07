package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.mixture.MixtureReactionPlan;
import io.github.antonovichkorp.chemmod.core.mixture.MixtureReactionPlanner;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionBatchPlan;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId;

import java.util.List;

/**
 * Mod-content adapter for exact reactions of an explicitly serialized mixture.
 *
 * It performs no world mutation and no discovery write. That separation lets a
 * gameplay vessel reserve every output slot and apply all replacements before
 * {@code DiscoverySavedData} is changed.
 */
public final class MixtureSubstanceReactionPlanner {
    private final MixtureReactionPlanner planner;

    public MixtureSubstanceReactionPlanner(MixtureReactionPlanner planner) {
        this.planner = planner;
    }

    public static MixtureSubstanceReactionPlanner bundled() {
        return new MixtureSubstanceReactionPlanner(MixtureReactionPlanner.bundled());
    }

    public List<PlannedMixtureReaction> plan(
        MixtureContents input,
        String targetCanonicalKey,
        ReactionRuleId ruleId,
        ReactionEnvironment environment
    ) {
        if (input == null) throw new IllegalArgumentException("Mixture input cannot be null");
        return planner.plan(input.mixture(), targetCanonicalKey, ruleId, environment).stream()
            .map(plan -> toContentsPlan(input, plan))
            .toList();
    }

    private static PlannedMixtureReaction toContentsPlan(MixtureContents input, MixtureReactionPlan plan) {
        List<SubstanceContents> products = plan.getProducts().stream()
            .map(portion -> SubstanceContents.fromMolecule(
                portion.getMolecule(),
                portion.getMicromoles(),
                1_000_000
            ))
            .toList();
        return new PlannedMixtureReaction(
            input,
            plan.getBatchPlan(),
            VialContentsState.fromMolecularMixture(plan.getResidual()),
            products
        );
    }

    /**
     * A complete, un-applied operation: the replacement state for the source
     * vial plus distinct product portions. Products are intentionally not
     * merged: output inventory policy belongs to the transactional vessel.
     */
    public record PlannedMixtureReaction(
        MixtureContents sourceInput,
        ReactionBatchPlan batchPlan,
        VialContentsState sourceReplacement,
        List<SubstanceContents> products
    ) {
        public PlannedMixtureReaction {
            if (sourceInput == null || batchPlan == null || sourceReplacement == null) {
                throw new IllegalArgumentException("A mixture reaction needs an input snapshot and source replacement");
            }
            products = List.copyOf(products);
            if (products.isEmpty()) throw new IllegalArgumentException("A mixture reaction needs products");
        }
    }
}
