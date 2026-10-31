package io.github.antonovichkorp.chemmod.content;

import net.minecraft.world.item.ItemStack;

/**
 * Plans a whole-portion transfer from one filled vial into one empty steel flask.
 *
 * The transaction moves the existing component payload unchanged: graph identity, exact amount,
 * purity, water analysis, and explicit-mixture parts are never reconstructed from a formula or lost.
 * It deliberately makes no material-compatibility, corrosion, or capacity claim; those are the
 * separate A4/Stage-B vessel model.
 */
public final class VialTransferTransaction {
    private VialTransferTransaction() {}

    public static Plan plan(ItemStack vial, ItemStack flask) {
        if (vial == null || flask == null
            || vial.isEmpty() || flask.isEmpty()
            || !vial.is(ChemItems.SUBSTANCE_VIAL.get())
            || !flask.is(ChemItems.STEEL_FLASK.get())
            || vial.getCount() != 1 || flask.getCount() != 1) {
            throw new IllegalArgumentException("Transfer needs one vial and one steel flask");
        }

        VialContentsState source = VialContentsState.fromStack(vial);
        VialContentsState destination = VialContentsState.fromStack(flask);
        if (source.isEmpty()) throw new IllegalArgumentException("Source vial is empty");
        if (!destination.isEmpty()) throw new IllegalArgumentException("Steel flask is not empty");

        return new Plan(vial.copy(), flask.copy(), VialContentsState.empty(), source);
    }

    public static final class Plan {
        private final ItemStack expectedVial;
        private final ItemStack expectedFlask;
        private final VialContentsState vialAfter;
        private final VialContentsState flaskAfter;

        private Plan(
            ItemStack expectedVial,
            ItemStack expectedFlask,
            VialContentsState vialAfter,
            VialContentsState flaskAfter
        ) {
            this.expectedVial = expectedVial;
            this.expectedFlask = expectedFlask;
            this.vialAfter = vialAfter;
            this.flaskAfter = flaskAfter;
        }

        /** Revalidate both snapshots immediately before replacing either component payload. */
        public boolean commit(ItemStack vial, ItemStack flask) {
            if (vial == null || flask == null
                || !ItemStack.isSameItemSameComponents(expectedVial, vial)
                || !ItemStack.isSameItemSameComponents(expectedFlask, flask)
                || expectedVial.getCount() != vial.getCount()
                || expectedFlask.getCount() != flask.getCount()) {
                return false;
            }
            // Both staged states are already validated. No fallible chemistry work remains here.
            flaskAfter.applyTo(flask);
            vialAfter.applyTo(vial);
            return true;
        }
    }
}
