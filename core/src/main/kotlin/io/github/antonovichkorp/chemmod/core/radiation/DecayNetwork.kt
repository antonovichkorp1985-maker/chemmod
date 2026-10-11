package io.github.antonovichkorp.chemmod.core.radiation

import kotlin.math.exp
import kotlin.math.ln

/**
 * Validated, immutable decay network.
 *
 * This class is intentionally a pure Kotlin model. World persistence and the
 * eventual gas/radiation field adapt this API; neither is allowed to invent a
 * second copy of nuclide conservation or branch data.
 */
class DecayNetwork private constructor(
    private val schemesByParent: Map<Nuclide, DecayScheme>,
) {
    val nuclides: Set<Nuclide>
        get() = schemesByParent.keys

    fun scheme(nuclide: Nuclide): DecayScheme =
        requireNotNull(schemesByParent[nuclide]) { "No decay scheme registered for ${nuclide.displayName}" }

    fun isStable(nuclide: Nuclide): Boolean = scheme(nuclide).isStable

    /** λ in s⁻¹. Stable nuclides have zero activity by definition. */
    fun decayConstantPerSecond(nuclide: Nuclide): Double {
        val halfLife = scheme(nuclide).halfLifeSeconds ?: return 0.0
        return LN_2 / halfLife
    }

    /** Remaining nuclei after [elapsedSeconds] under ordinary exponential decay. */
    fun remainingNuclei(nuclide: Nuclide, initialNuclei: Double, elapsedSeconds: Double): Double {
        require(initialNuclei >= 0.0 && initialNuclei.isFinite()) { "Initial nuclei must be finite and non-negative" }
        require(elapsedSeconds >= 0.0 && elapsedSeconds.isFinite()) { "Elapsed time must be finite and non-negative" }
        return initialNuclei * exp(-decayConstantPerSecond(nuclide) * elapsedSeconds)
    }

    /**
     * Activity in becquerels for an isolated sample. Atomic mass is approximated
     * by [Nuclide.massNumber] g/mol, a deliberate gameplay-grade approximation
     * that is exact enough for isotope ordering and dose scaling.
     */
    fun activityBecquerel(nuclide: Nuclide, massKilograms: Double): Double {
        require(massKilograms >= 0.0 && massKilograms.isFinite()) { "Mass must be finite and non-negative" }
        val moles = massKilograms * 1_000.0 / nuclide.massNumber.toDouble()
        return decayConstantPerSecond(nuclide) * moles * AVOGADRO
    }

    /**
     * Probability of reaching every descendant from [root]. This is useful for
     * checking a branching chain and for the long-time secular-equilibrium
     * limit; stable end products are included in the returned map.
     */
    fun branchFractionsFrom(root: Nuclide): Map<Nuclide, Double> {
        scheme(root)
        val fractions = linkedMapOf(root to 1.0)
        val pending = ArrayDeque<Pair<Nuclide, Double>>()
        pending.add(root to 1.0)
        while (pending.isNotEmpty()) {
            val (parent, incomingFraction) = pending.removeFirst()
            scheme(parent).branches.forEach { branch ->
                val daughter = branch.daughter
                val contribution = incomingFraction * branch.branchingFraction
                fractions[daughter] = fractions.getOrDefault(daughter, 0.0) + contribution
                if (!isStable(daughter)) pending.add(daughter to contribution)
            }
        }
        return fractions.toMap()
    }

    /**
     * In the secular-equilibrium approximation a short-lived descendant has an
     * activity equal to the parent activity multiplied by its branch fraction.
     * Callers must decide whether that approximation is appropriate for their
     * elapsed game time; this method never silently simulates time.
     */
    fun secularEquilibriumActivityBecquerel(root: Nuclide, rootActivityBecquerel: Double): Map<Nuclide, Double> {
        require(rootActivityBecquerel >= 0.0 && rootActivityBecquerel.isFinite()) {
            "Root activity must be finite and non-negative"
        }
        return branchFractionsFrom(root)
            .filterKeys { !isStable(it) }
            .mapValues { (_, fraction) -> rootActivityBecquerel * fraction }
    }

    companion object {
        const val AVOGADRO: Double = 6.022_140_76e23
        private const val LN_2: Double = 0.693_147_180_559_945_3
        private const val BRANCH_TOLERANCE: Double = 1.0e-9

        fun of(vararg schemes: DecayScheme): DecayNetwork = of(schemes.asList())

        fun of(schemes: Collection<DecayScheme>): DecayNetwork {
            require(schemes.isNotEmpty()) { "A decay network cannot be empty" }
            val duplicateParents = schemes.groupingBy { it.parent }.eachCount().filterValues { it > 1 }.keys
            require(duplicateParents.isEmpty()) {
                "Duplicate decay scheme(s): ${duplicateParents.joinToString { it.displayName }}"
            }
            val byParent = schemes.associateBy { it.parent }
            val errors = mutableListOf<String>()
            schemes.forEach { scheme ->
                if (!scheme.isStable) {
                    val total = scheme.branches.sumOf { it.branchingFraction }
                    if (kotlin.math.abs(total - 1.0) > BRANCH_TOLERANCE) {
                        errors += "${scheme.parent.displayName} branch total is $total, not 1.0"
                    }
                    scheme.branches.forEach { branch ->
                        val expectedAtomicNumber = scheme.parent.atomicNumber + branch.mode.daughterAtomicNumberDelta
                        val expectedMassNumber = scheme.parent.massNumber + branch.mode.daughterMassNumberDelta
                        if (branch.daughter.atomicNumber != expectedAtomicNumber ||
                            branch.daughter.massNumber != expectedMassNumber
                        ) {
                            errors += "${scheme.parent.displayName} ${branch.mode} branch does not conserve A/Z " +
                                "(expected Z=$expectedAtomicNumber, A=$expectedMassNumber; got " +
                                "${branch.daughter.displayName})"
                        }
                        if (branch.daughter !in byParent) {
                            errors += "${scheme.parent.displayName} references unregistered daughter " +
                                branch.daughter.displayName
                        }
                    }
                }
            }
            errors += cycleErrors(byParent)
            require(errors.isEmpty()) { errors.joinToString(separator = "; ") }
            return DecayNetwork(byParent)
        }

        private fun cycleErrors(schemes: Map<Nuclide, DecayScheme>): List<String> {
            val visiting = mutableSetOf<Nuclide>()
            val visited = mutableSetOf<Nuclide>()
            val errors = mutableListOf<String>()

            fun visit(nuclide: Nuclide) {
                // Missing daughters are reported by the structural validation
                // above; skipping them here preserves that helpful error.
                val scheme = schemes[nuclide] ?: return
                if (nuclide in visited || errors.isNotEmpty()) return
                if (!visiting.add(nuclide)) {
                    errors += "Decay network contains a cycle through ${nuclide.displayName}"
                    return
                }
                scheme.branches.forEach { visit(it.daughter) }
                visiting.remove(nuclide)
                visited += nuclide
            }

            schemes.keys.forEach(::visit)
            return errors
        }
    }
}
