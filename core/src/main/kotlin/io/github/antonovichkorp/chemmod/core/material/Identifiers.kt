package io.github.antonovichkorp.chemmod.core.material

private val ID_PATTERN = Regex("[a-z][a-z0-9_.-]*:[a-z0-9_./-]+")

private fun checkedId(value: String): String {
    require(ID_PATTERN.matches(value)) {
        "Definition ID must use namespace:path with lowercase safe characters, got '$value'"
    }
    return value
}

@JvmInline value class SpeciesId private constructor(val value: String) {
    companion object { fun of(value: String) = SpeciesId(checkedId(value)) }
    override fun toString(): String = value
}

@JvmInline value class MineralId private constructor(val value: String) {
    companion object { fun of(value: String) = MineralId(checkedId(value)) }
    override fun toString(): String = value
}

@JvmInline value class DepositId private constructor(val value: String) {
    companion object { fun of(value: String) = DepositId(checkedId(value)) }
    override fun toString(): String = value
}

@JvmInline value class MaterialId private constructor(val value: String) {
    companion object { fun of(value: String) = MaterialId(checkedId(value)) }
    override fun toString(): String = value
}

@JvmInline value class ProcessId private constructor(val value: String) {
    companion object { fun of(value: String) = ProcessId(checkedId(value)) }
    override fun toString(): String = value
}
