package io.github.antonovichkorp.chemmod.core.radiation

import io.github.antonovichkorp.chemmod.core.model.ElementTable
import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Nuclides as content rather than as code.
 *
 * Half-lives, decay modes and branching fractions live in `chemmod/isotopes.json`, so adding a
 * decay family is a data change and never a change to the model. Everything a nuclide claims is
 * checked against the element table on load: a symbol must exist there with the same atomic
 * number, and a daughter symbol must be the element the decay mode actually produces. Adding a
 * decay chain therefore cannot quietly invent an element.
 */
class IsotopeData private constructor(private val isotopes: List<Isotope>) {

    /** One nuclide together with the scheme the data describes for it. */
    data class Isotope(val nuclide: Nuclide, val scheme: DecayScheme) {
        val isStable: Boolean get() = scheme.isStable
        val halfLifeSeconds: Double? get() = scheme.halfLifeSeconds
    }

    fun all(): List<Isotope> = isotopes

    fun find(symbol: String, massNumber: Int): Isotope? =
        isotopes.firstOrNull { it.nuclide.symbol == symbol && it.nuclide.massNumber == massNumber }

    /** Every known isotope of one element; empty means the element has no nuclear data yet. */
    fun of(symbol: String): List<Isotope> = isotopes.filter { it.nuclide.symbol == symbol }

    /** The validated network; [DecayNetwork] re-checks A/Z conservation, branch totals and cycles. */
    fun network(): DecayNetwork = DecayNetwork.of(isotopes.map { it.scheme })

    companion object {
        const val CURRENT_SCHEMA = 1

        private val MODES = mapOf(
            "alpha" to DecayMode.ALPHA,
            "beta-" to DecayMode.BETA_MINUS,
            "beta+" to DecayMode.BETA_PLUS,
            "electron-capture" to DecayMode.ELECTRON_CAPTURE,
        )

        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }

        @Volatile
        private var cached: IsotopeData? = null

        fun fromJson(input: InputStream): IsotopeData {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString<IsotopeTableDocument>(text)
            require(document.schemaVersion == CURRENT_SCHEMA) {
                "Unsupported isotope table schema: ${document.schemaVersion}"
            }
            val elements = ElementTable.default()
            val byAtomicNumber = elements.all().associateBy { it.atomicNumber }
            val seen = mutableSetOf<Pair<String, Int>>()
            val isotopes = document.isotopes.map { record ->
                val element = elements.find(record.symbol)
                    ?: throw IllegalArgumentException("Isotope data names unknown element '${record.symbol}'")
                val nuclide = Nuclide(element.atomicNumber, record.massNumber, element.symbol)
                require(seen.add(record.symbol to record.massNumber)) {
                    "Duplicate isotope ${nuclide.displayName} in isotope data"
                }
                val branches = record.branches.map { branch ->
                    val mode = MODES[branch.mode]
                        ?: throw IllegalArgumentException(
                            "Unknown decay mode '${branch.mode}' for ${nuclide.displayName}",
                        )
                    val expectedAtomicNumber = nuclide.atomicNumber + mode.daughterAtomicNumberDelta
                    val daughterElement = byAtomicNumber[expectedAtomicNumber]
                        ?: throw IllegalArgumentException(
                            "${nuclide.displayName} $mode would produce atomic number " +
                                "$expectedAtomicNumber, which is not an element",
                        )
                    require(daughterElement.symbol == branch.daughterSymbol) {
                        "${nuclide.displayName} $mode must produce ${daughterElement.symbol}, " +
                            "not ${branch.daughterSymbol}"
                    }
                    DecayBranch(
                        mode,
                        Nuclide(expectedAtomicNumber, record.massNumber + mode.daughterMassNumberDelta,
                            daughterElement.symbol),
                        branch.fraction,
                        branch.energyMeV,
                    )
                }
                Isotope(nuclide, DecayScheme(nuclide, record.halfLifeSeconds, branches))
            }
            require(isotopes.isNotEmpty()) { "Isotope data cannot be empty" }
            return IsotopeData(isotopes)
        }

        @JvmStatic
        fun default(): IsotopeData = cached ?: synchronized(this) {
            cached ?: run {
                val stream = IsotopeData::class.java.getResourceAsStream("/chemmod/isotopes.json")
                    ?: error("Bundled isotope data is missing")
                stream.use(::fromJson).also { cached = it }
            }
        }
    }
}

@Serializable
private data class IsotopeTableDocument(
    val schemaVersion: Int,
    val isotopes: List<IsotopeDocument>,
)

@Serializable
private data class IsotopeDocument(
    val symbol: String,
    val massNumber: Int,
    val halfLifeSeconds: Double? = null,
    val branches: List<BranchDocument> = emptyList(),
)

@Serializable
private data class BranchDocument(
    val mode: String,
    val daughterSymbol: String,
    val daughterMassNumber: Int,
    val fraction: Double,
    val energyMeV: Double,
)
