package io.github.antonovichkorp.chemmod.core.model

import java.io.InputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Element content loaded independently from molecular identity. The same schema
 * can be provided by a future datapack loader instead of the bundled file.
 */
class ElementTable private constructor(elements: List<Element>) {
    private val bySymbol = elements.associateBy(Element::symbol)

    init {
        require(bySymbol.size == elements.size) { "Element symbols must be unique" }
    }

    fun find(symbol: String): Element? = bySymbol[symbol]

    fun require(symbol: String): Element =
        find(symbol) ?: throw IllegalArgumentException("Unknown element '$symbol'")

    fun all(): Collection<Element> = bySymbol.values

    companion object {
        const val CURRENT_SCHEMA = 1

        fun fromJson(input: InputStream): ElementTable {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString<ElementTableDocument>(text)
            require(document.schemaVersion == CURRENT_SCHEMA) {
                "Unsupported element table schema: ${document.schemaVersion}"
            }
            return ElementTable(document.elements.map(ElementDocument::toDomain))
        }

        @JvmStatic
        fun default(): ElementTable {
            val stream = ElementTable::class.java.getResourceAsStream("/chemmod/elements.json")
                ?: error("Bundled element table is missing")
            return stream.use(::fromJson)
        }

        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }
    }
}

@Serializable
private data class ElementTableDocument(
    val schemaVersion: Int,
    val elements: List<ElementDocument>,
)

@Serializable
private data class ElementDocument(
    val symbol: String,
    val name: String,
    val atomicNumber: Int,
    val atomicMass: Double,
    val valences: List<Int>,
) {
    fun toDomain() = Element(symbol, name, atomicNumber, atomicMass, valences)
}
