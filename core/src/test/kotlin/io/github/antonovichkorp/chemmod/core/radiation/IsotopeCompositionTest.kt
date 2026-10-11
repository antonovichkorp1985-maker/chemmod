package io.github.antonovichkorp.chemmod.core.radiation

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A substance used to be only a formula, so nothing could say how radioactive a piece of uranium
 * is. These tests pin the natural composition table and the activity that follows from it.
 *
 * The three numbers that matter are checked against independently published values, not against
 * the code that produced them: natural uranium 25.4 kBq/g, natural thorium 4.07 kBq/g, natural
 * potassium about 31 Bq/g. A unit slip or a wrong constant breaks all three at once.
 */
class IsotopeCompositionTest {
    private val composition = IsotopeComposition.default()

    private fun assertNear(expected: Double, actual: Double, tolerance: Double) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "expected $expected +/- $tolerance but got $actual",
        )
    }

    @Test
    fun `every listed element sums to one and names nuclides the isotope table knows`() {
        assertEquals(setOf("Bi", "H", "K", "Pb", "Rb", "Th", "U"), composition.symbols())
        val data = IsotopeData.default()
        composition.symbols().forEach { symbol ->
            val abundances = composition.of(symbol)
            assertNear(1.0, abundances.sumOf { it.fraction }, 1.0e-9)
            abundances.forEach { abundance ->
                assertEquals(
                    symbol,
                    abundance.nuclide.symbol,
                    "${symbol} composition names a nuclide of another element",
                )
                assertTrue(
                    data.find(abundance.nuclide.symbol, abundance.nuclide.massNumber) != null,
                    "${abundance.nuclide.displayName} is not in the isotope table",
                )
            }
        }
    }

    @Test
    fun `the primordial isotopes give the published specific activity of their element`() {
        // Natural uranium: 25.4 kBq/g. U-234 is 55 ppm of the atoms and still contributes about
        // half the activity, which is the whole point of carrying a composition at all.
        assertNear(25_400.0, composition.specificActivityBecquerelsPerGram("U"), 300.0)
        // Natural thorium: 4.07 kBq/g, essentially all Th-232.
        assertNear(4_070.0, composition.specificActivityBecquerelsPerGram("Th"), 50.0)
        // Natural potassium: about 31 Bq/g from 117 ppm of K-40 - the everyday background source.
        assertNear(31.0, composition.specificActivityBecquerelsPerGram("K"), 3.0)
        // No independently sourced value was found for natural rubidium this slice, so Rb-87 is
        // only bounded as a units check: hundreds of Bq/g, not thousands and not tens.
        val rubidium = composition.specificActivityBecquerelsPerGram("Rb")
        assertTrue(rubidium in 500.0..1_500.0, "rubidium came out at $rubidium Bq/g")
        assertTrue(
            composition.specificActivityBecquerelsPerGram("U") >
                composition.specificActivityBecquerelsPerGram("Th"),
        )
        assertTrue(rubidium > composition.specificActivityBecquerelsPerGram("K"))
    }

    @Test
    fun `elements the table holds as stable contribute no activity`() {
        // Pb-204 really does alpha-decay, on a half-life of order 1e17 years; the table holds it
        // as stable and the model must not invent an activity for it.
        assertEquals(0.0, composition.specificActivityBecquerelsPerGram("Pb"))
        // Hydrogen: H-3 exists in the isotope table but is cosmogenic, not part of a fixed
        // natural composition, so it is not listed and does not make hydrogen radioactive.
        assertEquals(0.0, composition.specificActivityBecquerelsPerGram("H"))
        assertEquals(2, composition.of("H").size)
        // Bismuth-209 is in the table with its real 2e19-year alpha half-life, so it is not
        // exactly zero - a nearly stable nuclide must stay nearly zero rather than vanish.
        val bismuth = composition.specificActivityBecquerelsPerGram("Bi")
        assertTrue(bismuth > 0.0 && bismuth < 1.0e-3, "bismuth came out at $bismuth Bq/g")
    }

    @Test
    fun `bulk activity is the specific activity times the mass in grams`() {
        val perGram = composition.specificActivityBecquerelsPerGram("U")
        assertNear(perGram, composition.activityBecquerel("U", 0.001), perGram * 1e-9)
        assertNear(perGram * 1_000.0, composition.activityBecquerel("U", 1.0), perGram * 1e-6)
        assertEquals(0.0, composition.activityBecquerel("U", 0.0))
        assertFailsWith<IllegalArgumentException> { composition.activityBecquerel("U", -1.0) }
    }

    @Test
    fun `an element without a composition reports none instead of guessing`() {
        assertNull(composition.find("Cs"))
        // Caesium is in the element table but has no composition row, so it is a lookup miss.
        assertFailsWith<IllegalStateException> { composition.of("Cs") }
    }

    @Test
    fun `a composition cannot name a nuclide the isotope table does not know`() {
        assertFailsWith<IllegalArgumentException> {
            parse(
                """
                {"schemaVersion":1,"abundances":[
                  {"symbol":"Cs","isotopes":[{"massNumber":133,"fraction":1.0}]}
                ]}
                """,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            parse(
                """
                {"schemaVersion":1,"abundances":[
                  {"symbol":"Xx","isotopes":[{"massNumber":1,"fraction":1.0}]}
                ]}
                """,
            )
        }
    }

    @Test
    fun `fractions that do not sum to one are refused`() {
        assertFailsWith<IllegalArgumentException> {
            parse(
                """
                {"schemaVersion":1,"abundances":[
                  {"symbol":"U","isotopes":[{"massNumber":238,"fraction":0.5}]}
                ]}
                """,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            parse(
                """
                {"schemaVersion":1,"abundances":[
                  {"symbol":"U","isotopes":[
                    {"massNumber":238,"fraction":0.992745},
                    {"massNumber":235,"fraction":0.5}
                  ]}
                ]}
                """,
            )
        }
    }

    private fun parse(json: String): IsotopeComposition =
        IsotopeComposition.fromJson(json.trimIndent().byteInputStream())
}
