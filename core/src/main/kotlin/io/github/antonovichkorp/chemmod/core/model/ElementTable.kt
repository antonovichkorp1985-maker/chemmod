package io.github.antonovichkorp.chemmod.core.model

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
        /**
         * Loads the intentionally small, data-only M0 element schema without tying
         * the chemistry core to a large JSON runtime. Unknown/missing fields fail
         * loudly; a full JSON-schema validator is planned with datapack loading.
         */
        fun fromJson(input: InputStream): ElementTable {
            val json = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val elements = OBJECT.findAll(json).map { objectMatch ->
                val body = objectMatch.groupValues[1]
                Element(
                    symbol = stringField(body, "symbol"),
                    name = stringField(body, "name"),
                    atomicNumber = integerField(body, "atomicNumber"),
                    atomicMass = decimalField(body, "atomicMass"),
                    valences = integerArrayField(body, "valences"),
                )
            }.toList()
            require(elements.isNotEmpty()) { "Element JSON must contain at least one object" }
            return ElementTable(elements)
        }

        fun default(): ElementTable {
            val stream = ElementTable::class.java.getResourceAsStream("/chemmod/elements.json")
                ?: error("Bundled element table is missing")
            return stream.use(::fromJson)
        }

        private fun stringField(body: String, name: String): String =
            Regex("\\\"$name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("Missing string field '$name' in element JSON")

        private fun integerField(body: String, name: String): Int =
            numberText(body, name).toInt()

        private fun decimalField(body: String, name: String): Double =
            numberText(body, name).toDouble()

        private fun numberText(body: String, name: String): String =
            Regex("\\\"$name\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").find(body)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("Missing numeric field '$name' in element JSON")

        private fun integerArrayField(body: String, name: String): List<Int> {
            val content = Regex("\\\"$name\\\"\\s*:\\s*\\[([^]]*)]").find(body)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("Missing array field '$name' in element JSON")
            return content.split(',').map(String::trim).filter(String::isNotEmpty).map(String::toInt)
        }

        private val OBJECT = Regex("\\{([^{}]+)}")
    }
}
