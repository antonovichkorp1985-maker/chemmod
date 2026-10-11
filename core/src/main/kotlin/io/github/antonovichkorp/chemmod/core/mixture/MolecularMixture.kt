package io.github.antonovichkorp.chemmod.core.mixture

import io.github.antonovichkorp.chemmod.core.reaction.MolecularPortion

/**
 * Explicit composition of a physical mixture. Components are normalized by
 * canonical molecular identity; it owns quantities only, never a hidden list
 * of substance properties or names.
 */
class MolecularMixture(portions: Iterable<MolecularPortion>) {
    private val portionsByCanonicalKey: Map<String, MolecularPortion>

    init {
        val normalized = linkedMapOf<String, MolecularPortion>()
        portions.forEach { portion ->
            val key = portion.molecule.canonicalKey()
            val previous = normalized[key]
            normalized[key] = if (previous == null) {
                portion
            } else {
                MolecularPortion(previous.molecule, Math.addExact(previous.micromoles, portion.micromoles))
            }
        }
        portionsByCanonicalKey = normalized.toSortedMap()
    }

    /** Stable, identity-sorted components. An empty mixture is a valid residual. */
    fun portions(): List<MolecularPortion> = portionsByCanonicalKey.values.toList()

    fun portion(canonicalKey: String): MolecularPortion? = portionsByCanonicalKey[canonicalKey]

    fun micromolesOf(canonicalKey: String): Long = portion(canonicalKey)?.micromoles ?: 0L

    fun totalMicromoles(): Long = portionsByCanonicalKey.values.fold(0L) { total, portion ->
        Math.addExact(total, portion.micromoles)
    }

    /**
     * Return the residual after exact per-identity withdrawals. Missing
     * identities and overdrafts are rejected; no unknown component is erased.
     */
    fun withdraw(withdrawalsMicromoles: Map<String, Long>): MolecularMixture {
        val residual = portionsByCanonicalKey.toMutableMap()
        withdrawalsMicromoles.forEach { (key, amount) ->
            require(amount > 0) { "Mixture withdrawal must be positive for $key" }
            val present = residual[key]
                ?: throw IllegalArgumentException("Mixture does not contain canonical identity $key")
            require(amount <= present.micromoles) { "Mixture withdrawal exceeds available amount for $key" }
            val remaining = present.micromoles - amount
            if (remaining == 0L) residual.remove(key)
            else residual[key] = MolecularPortion(present.molecule, remaining)
        }
        return MolecularMixture(residual.values)
    }

    fun plus(additions: Iterable<MolecularPortion>): MolecularMixture = MolecularMixture(portions() + additions)
}
