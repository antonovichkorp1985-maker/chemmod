package io.github.antonovichkorp.chemmod.core.radiation

import io.github.antonovichkorp.chemmod.core.model.ElementTable
import java.io.InputStream
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The natural isotopic composition of an element, as content.
 *
 * Until this table existed a substance was only a formula: "uranium" meant U with its standard
 * atomic weight and no nuclei behind it, so nothing could answer how radioactive a piece of it is.
 * These are the atom fractions the element actually carries, and the specific activity follows
 * from them and the half-lives in `chemmod/isotopes.json`. No substance is ever told its own
 * activity, and no element is hardcoded in the model - widening the table is a data change.
 *
 * Two invariants are enforced on load: every nuclide named here must exist in the isotope table,
 * so the two files cannot disagree about what exists, and the fractions of one element must sum
 * to one.
 */
class IsotopeComposition private constructor(
    private val compositions: Map<String, List<IsotopeAbundance>>,
) {

    /** One nuclide and the atom fraction of the natural element it accounts for. */
    data class IsotopeAbundance(val nuclide: Nuclide, val fraction: Double)

    /** Elements whose natural composition the data describes. */
    fun symbols(): Set<String> = compositions.keys

    /** The composition of one element, or null where no natural composition is held. */
    fun find(symbol: String): List<IsotopeAbundance>? = compositions[symbol]

    fun of(symbol: String): List<IsotopeAbundance> =
        compositions[symbol] ?: error("No natural isotopic composition for '$symbol'")

    /**
     * Activity of one gram of the natural element, in becquerels:
     * `A = (N_A / M) * sum(f_i * ln2 / T_i)`.
     *
     * One gram of element holds `N_A / M` atoms whatever the isotope mix, and [IsotopeAbundance.fraction]
     * is an *atom* fraction, so the normalisation uses the standard atomic weight from the element
     * table - the abundance-weighted mass - and not a mass number. Isotopes the table holds as
     * stable contribute nothing; that includes Pb-204, whose real alpha half-life is of order
     * 1e17 years and is deliberately not modelled.
     */
    fun specificActivityBecquerelsPerGram(symbol: String): Double {
        val molarMass = ElementTable.default().require(symbol).atomicMass
        val atomsPerGram = DecayNetwork.AVOGADRO / molarMass
        val isotopes = IsotopeData.default()
        return of(symbol).sumOf { abundance ->
            val halfLife = isotopes
                .find(abundance.nuclide.symbol, abundance.nuclide.massNumber)
                ?.halfLifeSeconds
                ?: return@sumOf 0.0
            abundance.fraction * atomsPerGram * LN_2 / halfLife
        }
    }

    /**
     * Activity of a bulk sample of the natural element. This is the number a world-side radiation
     * zone needs: mass in, becquerels out, and [RadiationExposure] turns that into a dose rate.
     */
    fun activityBecquerel(symbol: String, massKilograms: Double): Double {
        require(massKilograms >= 0.0 && massKilograms.isFinite()) {
            "Mass must be finite and non-negative"
        }
        return specificActivityBecquerelsPerGram(symbol) * massKilograms * GRAMS_PER_KILOGRAM
    }

    companion object {
        const val CURRENT_SCHEMA = 1

        private const val LN_2 = 0.693_147_180_559_945_3
        private const val GRAMS_PER_KILOGRAM = 1_000.0
        private const val FRACTION_SUM_TOLERANCE = 1.0e-4
        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }

        @Volatile
        private var cached: IsotopeComposition? = null

        fun fromJson(input: InputStream): IsotopeComposition {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString<IsotopeCompositionDocument>(text)
            require(document.schemaVersion == CURRENT_SCHEMA) {
                "Unsupported isotopic composition schema: ${document.schemaVersion}"
            }
            require(document.abundances.isNotEmpty()) { "Isotopic composition data cannot be empty" }
            val elements = ElementTable.default()
            val isotopes = IsotopeData.default()
            val compositions = linkedMapOf<String, List<IsotopeAbundance>>()
            document.abundances.forEach { entry ->
                val element = elements.find(entry.symbol)
                    ?: throw IllegalArgumentException(
                        "Isotopic composition names unknown element '${entry.symbol}'",
                    )
                require(compositions[element.symbol] == null) {
                    "Duplicate isotopic composition for '${entry.symbol}'"
                }
                require(entry.isotopes.isNotEmpty()) {
                    "Isotopic composition of '${entry.symbol}' lists no isotopes"
                }
                val seen = mutableSetOf<Int>()
                val abundances = entry.isotopes.map { record ->
                    require(record.fraction > 0.0 && record.fraction <= 1.0) {
                        "${entry.symbol}-${record.massNumber} has fraction ${record.fraction}"
                    }
                    require(seen.add(record.massNumber)) {
                        "Duplicate ${entry.symbol}-${record.massNumber} in the isotopic composition"
                    }
                    val isotope = isotopes.find(entry.symbol, record.massNumber)
                        ?: throw IllegalArgumentException(
                            "${entry.symbol}-${record.massNumber} is named by the isotopic " +
                                "composition but is not in the isotope table",
                        )
                    IsotopeAbundance(isotope.nuclide, record.fraction)
                }
                val total = abundances.sumOf { it.fraction }
                require(abs(total - 1.0) <= FRACTION_SUM_TOLERANCE) {
                    "Isotopic composition of '${entry.symbol}' sums to $total, not 1.0"
                }
                compositions[element.symbol] = abundances
            }
            return IsotopeComposition(compositions)
        }

        @JvmStatic
        fun default(): IsotopeComposition = cached ?: synchronized(this) {
            cached ?: run {
                val stream = IsotopeComposition::class.java
                    .getResourceAsStream("/chemmod/isotope-abundances.json")
                    ?: error("Bundled isotopic composition data is missing")
                stream.use(::fromJson).also { cached = it }
            }
        }
    }
}

@Serializable
private data class IsotopeCompositionDocument(
    val schemaVersion: Int,
    val abundances: List<IsotopeAbundanceDocument>,
)

@Serializable
private data class IsotopeAbundanceDocument(
    val symbol: String,
    val isotopes: List<IsotopeFractionDocument>,
)

@Serializable
private data class IsotopeFractionDocument(
    val massNumber: Int,
    val fraction: Double,
)
