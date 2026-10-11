package io.github.antonovichkorp.chemmod.core.gas

/**
 * Toxicity by mechanism, per ADR-0004 §4.
 *
 * The plan forbids a dose number per substance: identity and properties come from the molecule, not
 * from a catalogue row. So this model does not know that "chlorine is toxic". It recognises
 * structural situations that are hazardous for a stated reason — a hydrogen halide or an acidic
 * oxide meeting moisture makes acid on a wet surface; carbon monoxide binds haemoglobin; a gas that
 * is not respirable harms by displacing oxygen rather than by being poisonous; a strong oxidiser
 * attacks tissue and fuels. Each finding names its mechanism, because "toxic" without a reason is
 * exactly the number the ADR rules out.
 *
 * Where no mechanism applies the model says so. [ToxicityAssessment.NoModel] is a real answer and
 * must never be filled in with an invented dose: an unmodelled gas is not a safe gas, it is a gas
 * this model has nothing to say about.
 */
object GasToxicity {
    /** Oxygen fraction below which air no longer supports consciousness (OSHA uses 19.5%). */
    const val SAFE_OXYGEN_FRACTION = 0.195

    private val HALOGENS = setOf("F", "Cl", "Br", "I")

    sealed interface ToxicityAssessment {
        /** No mechanism applies. Not a claim of safety. */
        data object NoModel : ToxicityAssessment

        data class ByMechanism(val mechanism: Mechanism, val species: String, val detail: String) :
            ToxicityAssessment
    }

    enum class Mechanism {
        /** Acid gas plus moisture yields acid on wet tissue. */
        ACID_WITH_MOISTURE,

        /** Binds haemoglobin, so blood stops carrying oxygen. */
        CARBON_MONOXIDE_BINDING,

        /** Not respirable: harms by displacing oxygen. */
        OXYGEN_DISPLACEMENT,

        /** Attacks tissue and fuels by taking electrons. */
        OXIDISER,
    }

    /**
     * Assess a volume given its contents in moles. Returns one finding per mechanism that applies;
     * empty means nothing in this model explains a hazard.
     */
    fun assess(molesBySpecies: Map<String, Double>): List<ToxicityAssessment> {
        val total = molesBySpecies.values.sum()
        if (total <= 0.0) return listOf(ToxicityAssessment.NoModel)
        val findings = mutableListOf<ToxicityAssessment>()
        val wet = (molesBySpecies["H2O"] ?: 0.0) / total > 0.01

        for ((species, amount) in molesBySpecies) {
            if (amount <= 0.0) continue
            val fraction = amount / total
            val formula = parse(species) ?: continue

            if (isCarbonMonoxide(formula) && fraction > 0.0) {
                findings += ToxicityAssessment.ByMechanism(
                    Mechanism.CARBON_MONOXIDE_BINDING, species,
                    "carbon monoxide binds haemoglobin, so the blood stops carrying oxygen",
                )
            }
            if ((isHydrogenHalide(formula) || isAcidicOxide(formula) || isHalogen(formula)) && wet) {
                findings += ToxicityAssessment.ByMechanism(
                    Mechanism.ACID_WITH_MOISTURE, species,
                    "an acid gas meeting moisture makes acid on wet tissue",
                )
            }
            if ((isHalogen(formula) || isAcidicOxide(formula)) && fraction > 0.0) {
                findings += ToxicityAssessment.ByMechanism(
                    Mechanism.OXIDISER, species,
                    "a strong oxidiser takes electrons from tissue and from fuels",
                )
            }
        }

        // A gas that is not respirable is not poisonous; it harms by taking the oxygen's place.
        val oxygenFraction = (molesBySpecies["O2"] ?: 0.0) / total
        if (oxygenFraction < SAFE_OXYGEN_FRACTION) {
            val diluent = molesBySpecies.entries
                .filter { (species, amount) -> amount > 0.0 && species != "O2" && !isRespirable(parse(species)) }
                .maxByOrNull { it.value }
            if (diluent != null) {
                findings += ToxicityAssessment.ByMechanism(
                    Mechanism.OXYGEN_DISPLACEMENT, diluent.key,
                    "displaces oxygen: the mixture holds %.1f%% O2 against a safe %.1f%%"
                        .format(oxygenFraction * 100.0, SAFE_OXYGEN_FRACTION * 100.0),
                )
            }
        }

        return if (findings.isEmpty()) listOf(ToxicityAssessment.NoModel) else findings
    }

    // ------------------------------------------------------------- structural rules

    /** CO exactly: one carbon, one oxygen, nothing else. */
    private fun isCarbonMonoxide(formula: Map<String, Int>) =
        formula.size == 2 && formula["C"] == 1 && formula["O"] == 1

    /** HX: hydrogen with exactly one halogen, 1:1. */
    private fun isHydrogenHalide(formula: Map<String, Int>): Boolean {
        val halogen = formula.keys.singleOrNull { it in HALOGENS } ?: return false
        return formula.size == 2 && formula["H"] == 1 && formula[halogen] == 1
    }

    /** X2: a diatomic halogen. */
    private fun isHalogen(formula: Map<String, Int>): Boolean {
        if (formula.size != 1) return false
        val (symbol, count) = formula.entries.single()
        return symbol in HALOGENS && count == 2
    }

    /** An oxide of a non-metal: nitrogen or sulfur oxide, which hydrates to an acid. */
    private fun isAcidicOxide(formula: Map<String, Int>): Boolean {
        if (formula.keys.any { it !in setOf("N", "S", "O") }) return false
        val oxygen = formula["O"] ?: return false
        val central = (formula["N"] ?: 0) + (formula["S"] ?: 0)
        return central > 0 && oxygen >= 2
    }

    /** O2 itself is respirable; everything else that dilutes it is not. */
    private fun isRespirable(formula: Map<String, Int>?) =
        formula != null && formula.size == 1 && formula["O"] == 2

    private fun parse(formula: String): Map<String, Int>? {
        val counts = linkedMapOf<String, Int>()
        var index = 0
        while (index < formula.length) {
            val first = formula[index]
            if (!first.isUpperCase()) return null
            val symbol = StringBuilder().append(first)
            index++
            while (index < formula.length && formula[index].isLowerCase()) {
                symbol.append(formula[index])
                index++
            }
            val start = index
            while (index < formula.length && formula[index].isDigit()) index++
            val count = if (start == index) 1 else formula.substring(start, index).toIntOrNull() ?: return null
            if (count < 1) return null
            counts[symbol.toString()] = (counts[symbol.toString()] ?: 0) + count
        }
        return counts.ifEmpty { null }
    }
}
