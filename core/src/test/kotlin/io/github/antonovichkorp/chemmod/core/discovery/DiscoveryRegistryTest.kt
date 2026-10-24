package io.github.antonovichkorp.chemmod.core.discovery

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.naming.TrivialNameDirectory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryRegistryTest {
    @Test
    fun `first synthesis records metadata for an unnamed valid molecule without creating its identity`() {
        val butan2ol = Molecule.fromSMILESlike("CC(O)CC")
        val registry = DiscoveryRegistry()
        val first = registry.recordSynthesis(
            molecule = butan2ol,
            discoverer = "player-7",
            discoveredAt = Instant.parse("2026-10-06T18:00:00Z"),
            trivialName = "бутан-2-ол",
        )

        assertTrue(butan2ol.validate().isEmpty())
        assertNull(TrivialNameDirectory.bundled().findByCanonicalKey(butan2ol.canonicalKey()))
        assertEquals(butan2ol.canonicalKey(), first.canonicalKey)
        assertEquals("player-7", first.discoverer)
        assertEquals("бутан-2-ол", first.trivialName)
        assertEquals(first, registry.find(butan2ol.canonicalKey()))
    }

    @Test
    fun `later discoveries cannot overwrite the first discovery facts`() {
        val molecule = Molecule.fromSMILESlike("CCO")
        val registry = DiscoveryRegistry()
        val first = registry.recordSynthesis(
            molecule,
            discoverer = "first-player",
            discoveredAt = Instant.parse("2026-10-06T18:00:00Z"),
            trivialName = "first-name",
        )
        val replay = registry.recordSynthesis(
            molecule,
            discoverer = "later-player",
            discoveredAt = Instant.parse("2026-10-07T18:00:00Z"),
            trivialName = "replacement-name",
        )

        assertEquals(first, replay)
        assertEquals(listOf(first), registry.records())
    }
}
