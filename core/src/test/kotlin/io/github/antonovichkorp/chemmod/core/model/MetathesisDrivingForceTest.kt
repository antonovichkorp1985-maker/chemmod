package io.github.antonovichkorp.chemmod.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Ion exchange derived from four ions. None of these reactions is written down anywhere in the
 * model; each answer comes from the charges, electronegativities and solubility classes of the
 * ions taking part.
 */
class MetathesisDrivingForceTest {
    private val table = ElementTable.default()

    private fun assess(
        cationA: String,
        anionA: Solubility.AnionGroup,
        cationB: String,
        anionB: Solubility.AnionGroup,
        phase: Phase = Phase.AQUEOUS,
    ) = MetathesisDrivingForce.assess(
        Solubility.Cation.Elemental(table.require(cationA)), anionA,
        Solubility.Cation.Elemental(table.require(cationB)), anionB,
        phase,
    )

    @Test
    fun `silver chloride precipitating drives the exchange`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.PROCEEDS,
            assess("Ag", Solubility.AnionGroup.NITRATE, "Na", Solubility.AnionGroup.HALIDE),
        )
    }

    @Test
    fun `barium sulfate precipitating drives the exchange`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.PROCEEDS,
            assess("Ba", Solubility.AnionGroup.HALIDE, "Na", Solubility.AnionGroup.SULFATE),
        )
    }

    @Test
    fun `calcium carbonate precipitating drives the exchange`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.PROCEEDS,
            assess("Ca", Solubility.AnionGroup.HALIDE, "K", Solubility.AnionGroup.CARBONATE),
        )
    }

    @Test
    fun `two fully soluble products leave the exchange with no driving force`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.NO_DRIVING_FORCE,
            assess("Na", Solubility.AnionGroup.NITRATE, "K", Solubility.AnionGroup.HALIDE),
        )
        assertEquals(
            MetathesisDrivingForce.Outcome.NO_DRIVING_FORCE,
            assess("K", Solubility.AnionGroup.NITRATE, "Na", Solubility.AnionGroup.HALIDE),
        )
    }

    @Test
    fun `the outcome does not depend on the order the two salts are named in`() {
        assertEquals(
            assess("Ag", Solubility.AnionGroup.NITRATE, "Na", Solubility.AnionGroup.HALIDE),
            assess("Na", Solubility.AnionGroup.HALIDE, "Ag", Solubility.AnionGroup.NITRATE),
        )
    }

    @Test
    fun `an alkali cation forms ionic bonds and hydrogen does not`() {
        assertEquals(true, MetathesisDrivingForce.partnersCanExchange(table.require("Na"), table.require("K")))
        assertEquals(false, MetathesisDrivingForce.partnersCanExchange(table.require("H"), table.require("K")))
    }

    @Test
    fun `a cation with no electronegativity reports no model`() {
        val unknown = table.all().first { it.electronegativity == null }
        assertEquals(null, MetathesisDrivingForce.partnersCanExchange(unknown, table.require("Na")))
    }

    @Test
    fun `acid plus carbonate forms gas and proceeds`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.PROCEEDS,
            MetathesisDrivingForce.assess(
                Solubility.Cation.Elemental(table.require("Na")),
                Solubility.AnionGroup.CARBONATE,
                Solubility.Cation.Elemental(table.require("H")),
                Solubility.AnionGroup.HALIDE,
                Phase.AQUEOUS,
            ),
        )
    }

    @Test
    fun `acid plus sulfide forms gas and proceeds`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.PROCEEDS,
            MetathesisDrivingForce.assess(
                Solubility.Cation.Elemental(table.require("Na")),
                Solubility.AnionGroup.SULFIDE,
                Solubility.Cation.Elemental(table.require("H")),
                Solubility.AnionGroup.HALIDE,
                Phase.AQUEOUS,
            ),
        )
    }

    @Test
    fun `neutralisation forms weak electrolyte and proceeds`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.PROCEEDS,
            MetathesisDrivingForce.assess(
                Solubility.Cation.Elemental(table.require("Na")),
                Solubility.AnionGroup.HYDROXIDE,
                Solubility.Cation.Elemental(table.require("H")),
                Solubility.AnionGroup.HALIDE,
                Phase.AQUEOUS,
            ),
        )
    }

    @Test
    fun `ammonium hydroxide forms gas and proceeds`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.PROCEEDS,
            MetathesisDrivingForce.assess(
                Solubility.Cation.Elemental(table.require("Na")),
                Solubility.AnionGroup.HYDROXIDE,
                Solubility.Cation.Ammonium,
                Solubility.AnionGroup.HALIDE,
                Phase.AQUEOUS,
            ),
        )
    }

    @Test
    fun `solid phase has no free ions and cannot exchange`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.NO_FREE_IONS,
            MetathesisDrivingForce.assess(
                Solubility.Cation.Elemental(table.require("Ag")),
                Solubility.AnionGroup.NITRATE,
                Solubility.Cation.Elemental(table.require("Na")),
                Solubility.AnionGroup.HALIDE,
                Phase.SOLID,
            ),
        )
        assertEquals(
            MetathesisDrivingForce.Outcome.NO_FREE_IONS,
            MetathesisDrivingForce.assess(
                Solubility.Cation.Elemental(table.require("Ag")),
                Solubility.AnionGroup.NITRATE,
                Solubility.Cation.Elemental(table.require("Na")),
                Solubility.AnionGroup.HALIDE,
                Phase.GAS,
            ),
        )
    }

    @Test
    fun `molten phase permits exchange`() {
        assertEquals(
            MetathesisDrivingForce.Outcome.PROCEEDS,
            MetathesisDrivingForce.assess(
                Solubility.Cation.Elemental(table.require("Ag")),
                Solubility.AnionGroup.NITRATE,
                Solubility.Cation.Elemental(table.require("Na")),
                Solubility.AnionGroup.HALIDE,
                Phase.MOLTEN,
            ),
        )
    }
}
