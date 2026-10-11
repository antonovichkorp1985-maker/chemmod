package io.github.antonovichkorp.chemmod.core

import io.github.antonovichkorp.chemmod.core.material.MaterialCatalog
import io.github.antonovichkorp.chemmod.core.model.ElementTable
import io.github.antonovichkorp.chemmod.core.naming.TrivialNameDirectory
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coherence guards for bundled content: mineral stoichiometry may only use elements the
 * table defines, and every trivial label must resolve to the identity of its own graph.
 * None of these tests enumerates substances — they only check that content agrees.
 */
class ContentCoherenceTest {
    private val elements = ElementTable.default()

    @Test
    fun `mineral stoichiometry only uses elements from the bundled table`() {
        val unknown = MaterialCatalog.bundled().minerals.values.flatMap { mineral ->
            mineral.elementStoichiometry.keys.mapNotNull { symbol ->
                if (elements.find(symbol) == null) "${mineral.id} needs unknown element '$symbol'" else null
            }
        }
        assertEquals(emptyList(), unknown)
    }

    @Test
    fun `element table covers the whole periodic table exactly once`() {
        val all = elements.all().toList()
        assertEquals(118, all.size, "the bundled table must stay a complete periodic table")
        assertEquals((1..118).toList(), all.map { it.atomicNumber }.sorted())
        assertEquals(all.size, all.map { it.symbol }.distinct().size)
        assertTrue(all.all { it.valences.isNotEmpty() }, "An element needs at least one valence")
        assertTrue(all.all { element -> element.valences.all { it >= 0 } }, "Valences cannot be negative")
    }

    @Test
    fun `polarizability covers the periodic table except the element the source refuses`() {
        val bySymbol = elements.all().associateBy { it.symbol }
        val missing = bySymbol.values.filter { it.polarizibilityCubicAngstroms == null }
        assertEquals(
            listOf("Lv"),
            missing.map { it.symbol },
            "the 2018 table of static dipole polarizabilities has no reliable value for livermorium",
        )

        // The source quotes atomic units and its own conversion, 1 a.u. = 0.14818471 A^3.
        // Pinning hydrogen (4.50 a.u.) and carbon (11.3 a.u.) pins that conversion.
        val auToCubicAngstrom = 0.14818471
        val hydrogen = bySymbol.getValue("H").polarizibilityCubicAngstroms!!
        assertTrue(
            abs(hydrogen - 4.50 * auToCubicAngstrom) < 0.001,
            "hydrogen must be the source's 4.50 a.u. converted, got $hydrogen",
        )
        val carbon = bySymbol.getValue("C").polarizibilityCubicAngstroms!!
        assertTrue(
            abs(carbon - 11.3 * auToCubicAngstrom) < 0.001,
            "carbon must be the source's 11.3 a.u. converted, got $carbon",
        )

        fun chain(vararg symbols: String) =
            symbols.map { bySymbol.getValue(it).polarizibilityCubicAngstroms!! }

        // Down a group the electron cloud only grows, across a period it only shrinks. Any
        // transcription slip in the generated table breaks one of these two walks.
        val group18 = chain("He", "Ne", "Ar", "Kr", "Xe", "Rn")
        assertTrue(
            group18.zipWithNext().all { (lighter, heavier) -> lighter < heavier },
            "group 18 polarizability must grow downward, got $group18",
        )
        val group17 = chain("F", "Cl", "Br", "I", "At")
        assertTrue(
            group17.zipWithNext().all { (lighter, heavier) -> lighter < heavier },
            "group 17 polarizability must grow downward, got $group17",
        )
        val period3 = chain("Na", "Mg", "Al", "Si", "P", "S", "Cl", "Ar")
        assertTrue(
            period3.zipWithNext().all { (left, right) -> left > right },
            "period 3 polarizability must shrink to the right, got $period3",
        )
        assertNull(bySymbol.getValue("Lv").polarizibilityCubicAngstroms)
    }

    @Test
    fun `inert elements exist as monatomic substances and refuse bonds`() {
        val helium = Molecule.fromSMILESlike("He")
        assertEquals(emptyList(), helium.validate())
        assertEquals("He", helium.formula())

        val bonded = Molecule.fromSMILESlike("HeC")
        assertTrue(
            bonded.validate().any { it.code == "VALENCE_EXCEEDED" },
            "a zero-valence element must not accept a bond",
        )
    }

    @Test
    fun `every trivial label resolves to the identity of its own structure`() {
        val directory = TrivialNameDirectory.bundled()
        assertTrue(directory.all().isNotEmpty())
        directory.all().forEach { named ->
            val parsed = Molecule.fromSMILESlike(named.structure)
            assertEquals(emptyList(), parsed.validate().map { it.message }, "${named.canonicalName} is not a valid graph")
            assertEquals(named.canonicalKey, parsed.canonicalKey())
            assertEquals(named, directory.findByCanonicalKey(named.canonicalKey))
            named.aliases.forEach { alias ->
                assertEquals(named.canonicalKey, directory.find(alias)?.canonicalKey, "alias '$alias' drifted")
            }
        }
    }

    @Test
    fun `labelled substances stay distinct and reachable through the facade`() {
        val all = CommonSubstances.all()
        assertTrue(all.size >= 30, "expected the bundled directory to label the inorganic baseline, got ${all.size}")
        val identities = all.map { CommonSubstances.findByStructure(it.structure)?.canonicalName }
        assertEquals(all.size, identities.distinct().size, "two labels describe the same molecule")
        assertTrue(identities.all { it != null })

        val sulfuric = CommonSubstances.find("серная_кислота")
        assertNotNull(sulfuric, "the bundled directory lost its Russian alias for sulfuric acid")
        assertEquals(
            Molecule.fromSMILESlike("O=S(=O)(O)O").canonicalKey(),
            Molecule.fromSMILESlike(sulfuric.structure).canonicalKey(),
        )
    }
}
