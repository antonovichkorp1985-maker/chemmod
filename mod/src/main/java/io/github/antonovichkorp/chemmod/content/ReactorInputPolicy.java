package io.github.antonovichkorp.chemmod.content;

/**
 * Content boundary for M3 reactor inputs.
 *
 * <p>A vial may carry one molecular component while still representing an
 * analytically impure sample. The pure-vial planner deliberately has no loss
 * model for that impurity, so admitting it would either discard matter or let
 * a validation exception escape a server tick. M3 consequently accepts only
 * an exactly pure component; explicit mixture/impurity processing belongs to
 * a later machine layer.</p>
 */
final class ReactorInputPolicy {
    private static final int PURE_PPM = 1_000_000;

    private ReactorInputPolicy() {}

    /** A dependency-free value boundary that is safe to exercise in JVM CI. */
    static boolean isExactlyPurePpm(int purityPpm) {
        return purityPpm == PURE_PPM;
    }
}
