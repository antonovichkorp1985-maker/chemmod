package io.github.antonovichkorp.chemmod.core.bond

/**
 * Coefficients for the non-covalent layer. Like every other rule set these live in content
 * data (`chemmod/properties/default.json`), never in code or in substance records.
 */
data class InteractionRuleSet(
    val hydrogenBondDonorElements: Set<String>,
    val hydrogenBondAcceptorElements: Set<String>,
    val hydrogenBondKilojoulesPerMole: Double,
    val coordinateBondKilojoulesPerMole: Double,
    val aromaticResonanceKilojoulesPerMolePerRing: Double,
) {
    init {
        require(hydrogenBondDonorElements.isNotEmpty()) { "Hydrogen bonding needs donor elements" }
        require(hydrogenBondAcceptorElements.isNotEmpty()) { "Hydrogen bonding needs acceptor elements" }
        require(ELEMENT_PATTERN.matches(hydrogenBondDonorElements.joinToString(""))) {
            "Hydrogen bond donor entries must be element symbols"
        }
        require(ELEMENT_PATTERN.matches(hydrogenBondAcceptorElements.joinToString(""))) {
            "Hydrogen bond acceptor entries must be element symbols"
        }
        listOf(
            hydrogenBondKilojoulesPerMole,
            coordinateBondKilojoulesPerMole,
            aromaticResonanceKilojoulesPerMolePerRing,
        ).forEach {
            require(it.isFinite() && it >= 0.0) { "Interaction energies must be finite and non-negative" }
        }
    }

    private companion object {
        val ELEMENT_PATTERN = Regex("(?:[A-Z][a-z]?)*")
    }
}
