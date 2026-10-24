package io.github.antonovichkorp.chemmod.core.phase

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.gas.Atmosphere
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AtmosphericPhaseEstimateTest {
    @Test
    fun `phase follows graph predicted boiling point and current atmospheric conditions`() {
        val ethanol = Molecule.fromSMILESlike("CCO")
        val methane = Molecule.fromSMILESlike("C")

        val ethanolAtSeaLevel = AtmosphericPhaseEstimateModel.at(
            ethanol,
            Atmosphere.EARTH,
            0.0,
            "Earth ISA",
        )
        val methaneAtSeaLevel = AtmosphericPhaseEstimateModel.at(
            methane,
            Atmosphere.EARTH,
            0.0,
            "Earth ISA",
        )

        assertEquals(288.15, ethanolAtSeaLevel.temperatureKelvin)
        assertEquals(101_325.0, ethanolAtSeaLevel.pressurePascals)
        assertEquals(Phase.CONDENSED, ethanolAtSeaLevel.verdict.phase)
        assertEquals(Phase.GAS, methaneAtSeaLevel.verdict.phase)
        assertTrue(ethanolAtSeaLevel.verdict.basis.contains("boiling-point"))
    }

    @Test
    fun `altitude changes phase conditions without substance-specific records`() {
        val molecule = Molecule.fromSMILESlike("CCO")
        val seaLevel = AtmosphericPhaseEstimateModel.at(molecule, Atmosphere.EARTH, 0.0, "Earth ISA")
        val highAltitude = AtmosphericPhaseEstimateModel.at(molecule, Atmosphere.EARTH, 5_000.0, "Earth ISA")

        assertTrue(highAltitude.temperatureKelvin < seaLevel.temperatureKelvin)
        assertTrue(highAltitude.pressurePascals < seaLevel.pressurePascals)
        assertEquals(PhaseAtPressure.of(molecule.properties(), highAltitude.temperatureKelvin, highAltitude.pressurePascals),
            highAltitude.verdict)
    }
}
