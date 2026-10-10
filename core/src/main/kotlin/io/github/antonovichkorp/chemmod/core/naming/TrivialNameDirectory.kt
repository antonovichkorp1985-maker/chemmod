package io.github.antonovichkorp.chemmod.core.naming

import io.github.antonovichkorp.chemmod.core.Molecule
import java.io.InputStream
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Presentation-only directory. A missing entry never means a molecule is
 * invalid or unavailable to the chemistry core; it simply has no trivial name.
 */
class TrivialNameDirectory private constructor(entries: List<NamedMolecule>) {
    private val aliases: Map<String, NamedMolecule>
    private val canonicalKeys: Map<String, NamedMolecule>
    private val declared: List<NamedMolecule> = entries.toList()

    init {
        val index = linkedMapOf<String, NamedMolecule>()
        entries.forEach { entry ->
            entry.aliases.forEach { alias ->
                val normalized = normalize(alias)
                require(normalized.isNotBlank()) { "A trivial name alias cannot be blank" }
                val previous = index.putIfAbsent(normalized, entry)
                require(previous == null || previous.canonicalKey == entry.canonicalKey) {
                    "Trivial name alias '$alias' refers to different molecular structures"
                }
            }
        }
        aliases = index.toMap()
        canonicalKeys = entries.associateBy(NamedMolecule::canonicalKey)
        require(canonicalKeys.size == entries.size) { "Several trivial names use the same molecular identity" }
    }

    fun find(input: String): NamedMolecule? = aliases[normalize(input)]

    fun findByCanonicalKey(canonicalKey: String): NamedMolecule? = canonicalKeys[canonicalKey]

    /** Every labelled identity, in declaration order. Labels never define existence. */
    fun all(): List<NamedMolecule> = declared

    companion object {
        fun fromJson(input: InputStream): TrivialNameDirectory {
            val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val document = JSON.decodeFromString(DirectoryDocument.serializer(), text)
            require(document.schemaVersion == CURRENT_SCHEMA) {
                "Unsupported trivial-name directory schema: ${document.schemaVersion}"
            }
            return TrivialNameDirectory(document.entries.map { it.toDomain() })
        }

        @JvmStatic
        fun bundled(): TrivialNameDirectory {
            val stream = TrivialNameDirectory::class.java.getResourceAsStream("/chemmod/names/trivial.json")
                ?: error("Bundled trivial-name directory is missing")
            return stream.use(::fromJson)
        }

        const val CURRENT_SCHEMA = 1
        private val JSON = Json { ignoreUnknownKeys = false; isLenient = false }
    }
}

/** A label and aliases for an identity which is defined solely by its graph. */
data class NamedMolecule(
    val canonicalName: String,
    val aliases: Set<String>,
    val structure: String,
    val canonicalKey: String,
) {
    companion object {
        fun fromStructure(canonicalName: String, aliases: Set<String>, structure: String): NamedMolecule {
            require(canonicalName.matches(Regex("[a-z][a-z0-9_]*"))) {
                "Trivial canonical name must use lowercase snake case"
            }
            val molecule = Molecule.fromSMILESlike(structure)
            require(molecule.validate().isEmpty()) { "Invalid structure for trivial name $canonicalName" }
            return NamedMolecule(canonicalName, aliases + canonicalName, structure, molecule.canonicalKey())
        }
    }
}

@Serializable
private data class DirectoryDocument(val schemaVersion: Int, val entries: List<NameEntryDocument>)

@Serializable
private data class NameEntryDocument(
    val canonicalName: String,
    val aliases: Set<String> = emptySet(),
    val structure: String,
) {
    fun toDomain() = NamedMolecule.fromStructure(canonicalName, aliases, structure)
}

private fun normalize(value: String): String = value.trim().lowercase(Locale.ROOT).replace(' ', '_')
