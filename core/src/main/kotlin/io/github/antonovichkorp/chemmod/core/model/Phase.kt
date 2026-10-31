package io.github.antonovichkorp.chemmod.core.model

/**
 * Physical phase of an ionic or molecular sample, derived from its temperature and pressure
 * against its own melting/boiling behaviour rather than from a per-substance table.
 *
 * Only phases with mobile ions permit metathesis: in SOLID ionic lattices ions are locked in place,
 * and gases have no ions at all to exchange. AQUEOUS and MOLTEN phases both have free mobile ions.
 */
enum class Phase {
    SOLID,
    LIQUID,
    AQUEOUS,
    MOLTEN,
    GAS;

    /** True when ions can move freely enough to exchange partners in a double displacement. */
    val hasFreeMobileIons: Boolean
        get() = this == Phase.AQUEOUS || this == Phase.MOLTEN
}
