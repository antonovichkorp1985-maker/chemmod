package io.github.antonovichkorp.chemmod.core.model

import io.github.antonovichkorp.chemmod.core.bond.BondCharacter

/**
 * Whether an ion exchange actually proceeds, derived from the properties of the ions involved.
 *
 * A double displacement has no driving force of its own: it goes only if a partner leaves the
 * solution, as a precipitate, a gas, or a weakly dissociating molecule. Everything needed to
 * decide that is already a property of the ions - their charges say which pairs can exchange at
 * all, their electronegativities say whether the bonds are ionic enough to be in solution as ions,
 * and their class says whether the product stays dissolved. So the answer for a reaction nobody
 * ever wrote down falls out of four ions.
 *
 * A null solubility anywhere yields [Outcome.NO_MODEL], never a guess. Reporting "no driving
 * force" for a pair we cannot classify would be a false negative about chemistry we simply have
 * not modelled.
 */
object MetathesisDrivingForce {
    enum class Outcome {
        /** At least one product leaves solution, so the exchange proceeds. */
        PROCEEDS,

        /** Both products stay dissolved: mixing the solutions changes nothing. */
        NO_DRIVING_FORCE,

        /** Some ion has no solubility rule, so nothing may be asserted. */
        NO_MODEL,

        /** Ions are locked in a solid lattice or absent as ions: no exchange can occur. */
        NO_FREE_IONS,
    }

    /**
     * The two products of exchanging partners between two salts, used for display and for tests.
     */
    data class Exchange(
        val firstCation: Solubility.Cation,
        val firstAnion: Solubility.AnionGroup,
        val secondCation: Solubility.Cation,
        val secondAnion: Solubility.AnionGroup,
    ) {
        val productOne: Pair<Solubility.Cation, Solubility.AnionGroup> get() = firstCation to secondAnion
        val productTwo: Pair<Solubility.Cation, Solubility.AnionGroup> get() = secondCation to firstAnion
    }

    /**
     * Whether exchanging the anions of two salts produces something that leaves solution, as a
     * precipitate, a gas, or a weakly dissociating molecule.
     *
     * @param phase the physical phase of the mixture; only [Phase.AQUEOUS] and [Phase.MOLTEN] have
     * free mobile ions, so any other phase yields [Outcome.NO_FREE_IONS].
     */
    fun assess(
        firstCation: Solubility.Cation,
        firstAnion: Solubility.AnionGroup,
        secondCation: Solubility.Cation,
        secondAnion: Solubility.AnionGroup,
        phase: Phase,
    ): Outcome {
        if (!phase.hasFreeMobileIons) return Outcome.NO_FREE_IONS

        val products = listOf(
            firstCation to secondAnion,
            secondCation to firstAnion,
        )

        for ((cation, anion) in products) {
            val soluble = Solubility.isSoluble(cation, anion)
            if (soluble == null) return Outcome.NO_MODEL
            if (!soluble) return Outcome.PROCEEDS
            if (Solubility.formsGas(cation, anion) == true) return Outcome.PROCEEDS
            if (Solubility.formsWeakElectrolyte(cation, anion) == true) return Outcome.PROCEEDS
        }

        return Outcome.NO_DRIVING_FORCE
    }

    /**
     * Whether the two cations can even trade anions, which needs both bonds to be ionic enough to
     * exist as dissociated ions in the first place. Null when an electronegativity is missing.
     */
    fun partnersCanExchange(firstCation: Element, secondCation: Element): Boolean? {
        val first = BondCharacter.isIonic(firstCation, HalogenRepresentative) ?: return null
        val second = BondCharacter.isIonic(secondCation, HalogenRepresentative) ?: return null
        return first && second
    }

    /**
     * Chlorine stands in for a typical anion when asking whether a cation forms ionic bonds at
     * all. It is the most electronegative element that forms a simple monatomic anion, so it is
     * the strictest test a common anion can offer.
     */
    private val HalogenRepresentative: Element by lazy { ElementTable.default().require("Cl") }
}
