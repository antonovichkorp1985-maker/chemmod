package io.github.antonovichkorp.chemmod.core.material

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OreWashingQualityTest {
    private val naturalCopper = MaterialMassComposition(
        primaryMassMicrograms = 850_000_000,
        impurityMassMicrograms = mapOf("chemmod:silicate_gangue" to 150_000_000),
    )

    @Test
    fun `clean water retains less gangue than saline water without creating copper`() {
        val clean = OreWashingQuality.wash(naturalCopper, 0)
        val saline = OreWashingQuality.wash(naturalCopper, 35_000)

        assertEquals(850_000_000, clean.concentrate.primaryMassMicrograms)
        assertEquals(11_632_033, clean.concentrate.impurityMassMicrograms.getValue("chemmod:silicate_gangue"))
        assertEquals(861_632_033, clean.concentrate.totalMassMicrograms)
        assertEquals(986_500, clean.concentrate.purityPpm())
        assertEquals(mapOf("chemmod:silicate_gangue" to 138_367_967L), clean.tailingsMassMicrograms)

        assertEquals(850_000_000, saline.concentrate.primaryMassMicrograms)
        assertEquals(35_186_150, saline.concentrate.impurityMassMicrograms.getValue("chemmod:silicate_gangue"))
        assertEquals(885_186_150, saline.concentrate.totalMassMicrograms)
        assertEquals(960_250, saline.concentrate.purityPpm())
        assertEquals(mapOf("chemmod:silicate_gangue" to 114_813_850L), saline.tailingsMassMicrograms)
        assertTrue(clean.concentrate.purityPpm() > saline.concentrate.purityPpm())

        assertMassConserved(naturalCopper, clean)
        assertMassConserved(naturalCopper, saline)
    }

    @Test
    fun `washing keeps a pure reference batch unchanged and produces no tailings`() {
        val pureCopper = MaterialMassComposition(primaryMassMicrograms = 1_000_000_000)
        val washed = OreWashingQuality.wash(pureCopper, 250_000)

        assertEquals(pureCopper, washed.concentrate)
        assertTrue(washed.tailingsMassMicrograms.isEmpty())
        assertMassConserved(pureCopper, washed)
    }

    @Test
    fun `dry separation materializes the residual gangue without changing recovered copper`() {
        val washed = OreWashingQuality.wash(naturalCopper, 0)
        val separated = OreSeparation.separate(washed.concentrate)

        assertEquals(850_000_000, separated.primaryMassMicrograms)
        assertEquals(
            washed.concentrate.impurityMassMicrograms,
            separated.separatedImpurityMassMicrograms,
        )
        assertEquals(
            washed.concentrate.totalMassMicrograms,
            separated.primaryMassMicrograms + separated.separatedTotalMassMicrograms,
        )
    }

    @Test
    fun `component splits preserve every microgram`() {
        val split = naturalCopper.split(333_333_333)
        val remainder = requireNotNull(split.remainder)

        assertEquals(333_333_333, split.extracted.totalMassMicrograms)
        assertEquals(666_666_667, remainder.totalMassMicrograms)
        assertEquals(naturalCopper.primaryMassMicrograms,
            split.extracted.primaryMassMicrograms + remainder.primaryMassMicrograms)
        assertEquals(
            naturalCopper.impurityMassMicrograms.getValue("chemmod:silicate_gangue"),
            split.extracted.impurityMassMicrograms.getValue("chemmod:silicate_gangue") +
                remainder.impurityMassMicrograms.getValue("chemmod:silicate_gangue"),
        )
    }

    private fun assertMassConserved(input: MaterialMassComposition, result: OreWashingResult) {
        assertEquals(
            input.totalMassMicrograms,
            result.concentrate.totalMassMicrograms + result.tailingsTotalMassMicrograms,
        )
        assertEquals(input.primaryMassMicrograms, result.concentrate.primaryMassMicrograms)
    }
}
