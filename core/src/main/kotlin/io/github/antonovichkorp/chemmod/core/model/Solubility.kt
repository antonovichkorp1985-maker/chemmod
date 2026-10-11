package io.github.antonovichkorp.chemmod.core.model

/**
 * Whether an ionic solid stays dissolved, decided by the class of its ions rather than by the
 * salt.
 *
 * This is the last input the metathesis driving force needs: a double displacement proceeds when
 * a partner leaves solution, and "leaves solution" is a property of ion classes, not of individual
 * compounds. Nitrates and alkali salts always dissolve; chlorides fail with silver, lead and
 * mercury; sulfates fail with barium, strontium and lead; the weak-acid anions fail with almost
 * everything except the alkali metals and ammonium. A compound nobody ever listed is classified by
 * its own two ions.
 *
 * Deliberately coarse. Real solubility is a continuum with a solubility product behind it, and
 * borderline salts such as calcium sulfate are called one way here and would need Ksp to resolve.
 * A class this model does not cover returns null: no model, not "insoluble".
 */
object Solubility {
    /** Anion classes whose solubility rules differ. Anything else is not modelled. */
    enum class AnionGroup { NITRATE, HALIDE, SULFATE, CARBONATE, PHOSPHATE, SULFIDE, HYDROXIDE }

    /** Alkali metals by position in the table: lithium, sodium, potassium, rubidium, caesium. */
    private val ALKALI_METALS = setOf(3, 11, 19, 37, 55)

    /** Alkaline earths: beryllium, magnesium, calcium, strontium, barium. */
    private val ALKALINE_EARTHS = setOf(4, 12, 20, 38, 56)

    /** Cations that break the halide rule. */
    private val INSOLUBLE_HALIDES = setOf("Ag", "Pb", "Hg", "Tl", "Cu")

    /** Cations that break the sulfate rule. */
    private val INSOLUBLE_SULFATES = setOf("Ba", "Sr", "Pb")

    /** Ammonium behaves like an alkali metal in every solubility rule. */
    private fun isAlkaliLike(cation: Element): Boolean =
        cation.atomicNumber in ALKALI_METALS

    /**
     * Whether a salt of this cation and this anion class dissolves, or null where the class is
     * not covered by a rule.
     */
    fun isSoluble(cation: Element, anion: AnionGroup): Boolean? = when (anion) {
        AnionGroup.NITRATE -> true
        AnionGroup.HALIDE -> cation.symbol !in INSOLUBLE_HALIDES
        AnionGroup.SULFATE -> cation.symbol !in INSOLUBLE_SULFATES
        AnionGroup.CARBONATE, AnionGroup.PHOSPHATE, AnionGroup.SULFIDE -> isAlkaliLike(cation)
        // Hydroxides follow the same rule, but the heavy alkaline earths are sparingly soluble
        // enough to count as soluble for the purposes of a driving force.
        AnionGroup.HYDROXIDE -> isAlkaliLike(cation) ||
            cation.atomicNumber in ALKALINE_EARTHS && cation.atomicNumber >= 20
    }

    /** True when the pairing leaves solution, which is what makes a metathesis proceed. */
    fun precipitates(cation: Element, anion: AnionGroup): Boolean? {
        val soluble = isSoluble(cation, anion) ?: return null
        return !soluble
    }
}
