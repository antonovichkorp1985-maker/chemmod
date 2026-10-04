package io.github.antonovichkorp.chemmod.core.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream

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
        private val mapper = ObjectMapper()

        fun fromJson(input: InputStream): ElementTable {
            val elements: List<Element> = mapper.readValue(input, object : TypeReference<List<Element>>() {})
            return ElementTable(elements)
        }

        fun default(): ElementTable {
            val stream = ElementTable::class.java.getResourceAsStream("/chemmod/elements.json")
                ?: error("Bundled element table is missing")
            return stream.use(::fromJson)
        }
    }
}
