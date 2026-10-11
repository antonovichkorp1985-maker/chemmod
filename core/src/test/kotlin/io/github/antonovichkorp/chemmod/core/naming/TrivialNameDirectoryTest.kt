package io.github.antonovichkorp.chemmod.core.naming

import io.github.antonovichkorp.chemmod.core.Molecule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrivialNameDirectoryTest {
    @Test
    fun `directory supplies a human name without governing molecular existence`() {
        val directory = TrivialNameDirectory.bundled()

        assertEquals("ethanol", directory.find("этанол")!!.canonicalName)
        assertNull(directory.find("CCCCO"))
        assertEquals("C4H10O", Molecule.fromSMILESlike("CCCCO").formula())
    }
}
