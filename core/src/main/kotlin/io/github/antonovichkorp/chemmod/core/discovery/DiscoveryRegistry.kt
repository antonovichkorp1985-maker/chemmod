package io.github.antonovichkorp.chemmod.core.discovery

import io.github.antonovichkorp.chemmod.core.Molecule
import java.time.Instant

private val CANONICAL_KEY_PATTERN = Regex("[0-9a-f]{64}")

/**
 * World/player progress metadata for a molecule. It deliberately stores only
 * the canonical molecular identity and discovery facts: never properties,
 * reaction definitions, or a molecule object acting as a hidden catalogue.
 */
data class DiscoveryRecord(
    val canonicalKey: String,
    val discoverer: String,
    val discoveredAt: Instant,
    val trivialName: String? = null,
) {
    init {
        require(CANONICAL_KEY_PATTERN.matches(canonicalKey)) { "Discovery key must be a full canonical hash" }
        require(discoverer.isNotBlank()) { "Discoverer cannot be blank" }
        require(trivialName == null || trivialName.isNotBlank()) { "Trivial name cannot be blank when supplied" }
    }
}

/**
 * Mutable, server-owned discovery ledger. The first synthesis record is
 * immutable: later synthesis attempts return the original metadata instead of
 * overwriting its discoverer, date, or player-assigned trivial name.
 *
 * Persistence is deliberately outside core; a world data component can save
 * [records] and reconstruct this ledger without coupling molecular identity to
 * Minecraft types.
 */
class DiscoveryRegistry(initialRecords: Iterable<DiscoveryRecord> = emptyList()) {
    private val byCanonicalKey = linkedMapOf<String, DiscoveryRecord>()

    init {
        initialRecords.forEach { record ->
            require(byCanonicalKey.putIfAbsent(record.canonicalKey, record) == null) {
                "Discovery registry contains duplicate canonical key ${record.canonicalKey}"
            }
        }
    }

    @Synchronized
    fun recordSynthesis(
        molecule: Molecule,
        discoverer: String,
        discoveredAt: Instant,
        trivialName: String? = null,
    ): DiscoveryRecord {
        require(molecule.validate().isEmpty()) { "Only a valid molecular graph can be recorded as synthesized" }
        val key = molecule.canonicalKey()
        return byCanonicalKey.getOrPut(key) {
            DiscoveryRecord(key, discoverer, discoveredAt, trivialName)
        }
    }

    @Synchronized
    fun find(canonicalKey: String): DiscoveryRecord? = byCanonicalKey[canonicalKey]

    @Synchronized
    fun records(): List<DiscoveryRecord> = byCanonicalKey.values.toList()
}
