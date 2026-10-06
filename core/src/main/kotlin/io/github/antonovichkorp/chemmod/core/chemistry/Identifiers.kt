package io.github.antonovichkorp.chemmod.core.chemistry

private val CHEMICAL_ID_PATTERN = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")

private fun checkedChemicalId(value: String, label: String): String {
    require(CHEMICAL_ID_PATTERN.matches(value)) {
        "$label must use namespace:path with lowercase safe characters, got '$value'"
    }
    return value
}

/** Stable ID for one molecular identity in the chemistry catalog. */
@JvmInline
value class SubstanceId private constructor(val value: String) {
    val path: String
        get() = value.substringAfter(':')

    companion object {
        fun of(value: String) = SubstanceId(checkedChemicalId(value, "Substance ID"))
    }

    override fun toString(): String = value
}

/** Stable ID for a declared, stoichiometrically verified chemical reaction. */
@JvmInline
value class ChemicalReactionId private constructor(val value: String) {
    companion object {
        fun of(value: String) = ChemicalReactionId(checkedChemicalId(value, "Chemical reaction ID"))
    }

    override fun toString(): String = value
}
