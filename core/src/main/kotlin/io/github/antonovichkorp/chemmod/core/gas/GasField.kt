package io.github.antonovichkorp.chemmod.core.gas

import io.github.antonovichkorp.chemmod.core.Molecule
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp

/** One cell of the sparse world gas field. Indices are cell coordinates, not block coordinates. */
data class GasCell(val x: Int, val y: Int, val z: Int)

/**
 * Per-species transport data the field needs. The field deliberately holds no substance table:
 * molar mass comes from the molecular graph and the diffusion coefficient from Chapman–Enskog
 * ([GasTransport]), so the caller supplies both. A `null` diffusion coefficient means "unknown",
 * and an unknown coefficient must not be silently read as a small one.
 */
interface GasFieldProperties {
    /** Legacy display/formula identity, retained for already-created non-structural field entries. */
    fun molarMassGramsPerMole(species: String): Double

    /** Binary diffusion coefficient against air, or null when the model has no coverage. */
    fun diffusionSquareMetersPerSecond(species: String): Double?

    /** Structure-preserving properties for new field entries. */
    fun molarMassGramsPerMole(species: Molecule): Double

    /** Structure-preserving transport lookup; unsupported structures remain explicitly unknown. */
    fun diffusionSquareMetersPerSecond(species: Molecule): Double?
}

/**
 * Occupancy hook. ADR-0004 makes the gas field the owner of transported radiation sources: when a
 * radioactive species enters a cell the field announces it so a source can be registered, and when
 * it leaves the source is removed. A source is created and destroyed, never moved.
 */
interface GasFieldOccupancyListener {
    fun speciesEntered(cell: GasCell, species: String)
    fun speciesLeft(cell: GasCell, species: String)
}

/** Supplies physical conditions for the existing data-defined reaction engine at one gas cell. */
fun interface GasCellReactionEnvironmentProvider {
    fun environment(cell: GasCell, contents: List<GasMolecularAmount>): ReactionEnvironment?
}

/**
 * The sparse world gas field of ПЛАН-ДРУГА §5.
 *
 * Gas lives in a sparse map from cell to species to moles, so empty space costs nothing and the
 * coordinate span is free: 200 occupied cells cost the same whether they sit in one room or are
 * scattered across a continent. The cell size is configurable and coarse cells are aggregations of
 * fine ones, the same LOD scheme [io.github.antonovichkorp.chemmod.core.radiation.RadiationField]
 * uses.
 *
 * One [advance] applies, per species, the mechanisms the plan names for the field:
 *
 * 1. **Diffusion by concentration gradient.** Explicit Fick exchange between the six neighbours
 *    with `k = D·Δt/Δx²`. All cells share a volume, so concentration is proportional to moles and
 *    the flux is `k·(nᵢ − nⱼ)`. The explicit scheme is only stable up to `k ≤ 1/6` in 3D, so a
 *    larger `k` is taken as several smaller sub-steps rather than allowed to oscillate.
 * 2. **A vertical buoyancy bias.** Gas denser than air settles, lighter gas rises, at a drift speed
 *    proportional to its buoyancy excess `|M/M_air − 1|` — the plan's bias, from a property of the
 *    molecule rather than a tuned per-gas number.
 * 3. **Ventilation**, which exchanges cells open to the outside with the atmosphere, and
 *    **sinks**, which are how filtration and scrubbing are expressed: a first-order removal rate
 *    per cell and species, so a filter is data and not a special case in the solver.
 *
 * Diffusion and drift only move moles between cells, so a field with no ventilation and no sinks
 * conserves every species exactly; ventilation and sinks remove monotonically. Both properties are
 * asserted in tests.
 *
 * [workUnits] counts cell/species visits in transport and reaction evaluation. It is a scaling
 * proxy, not a timing claim: the plan's <2 ms per tick for 200 cells still requires profiling on
 * the target pack and hardware.
 */
class GasField(val cellSizeMetres: Double = DEFAULT_CELL_SIZE_METRES) {
    companion object {
        /** Stable, collision-resistant key for a molecular gas identity. */
        @JvmStatic
        fun identityKey(molecule: Molecule): String = "mol:${molecule.canonicalKey()}"

        /** Bench cell of §5: 4 m, refined locally down to a block, 16 m as the far LOD. */
        const val DEFAULT_CELL_SIZE_METRES = 4.0

        /** Stability limit of the explicit scheme in three dimensions. */
        const val MAX_EXPLICIT_EXCHANGE = 1.0 / 6.0

        /** Default buoyant drift speed in m/s per unit of buoyancy excess. */
        const val DEFAULT_BUOYANT_DRIFT_METRES_PER_SECOND = 0.5

        private const val TRACE_MOLES = 1e-12

        /** +x/+y/+z only, so each unordered neighbour pair is exchanged exactly once per step. */
        private val HALF_NEIGHBOURS = listOf(
            GasCell(1, 0, 0),
            GasCell(0, 1, 0),
            GasCell(0, 0, 1),
        )
    }

    init {
        require(cellSizeMetres > 0.0) { "Cell size must be positive" }
    }

    private val cells = linkedMapOf<GasCell, MutableMap<String, Double>>()
    /** Structural witnesses keyed by the full canonical digest; formulas are never identity. */
    private val molecularWitnesses = linkedMapOf<String, Molecule>()
    private val listeners = mutableListOf<GasFieldOccupancyListener>()

    /** Cell/species visits in transport plus environmental-reaction steps; a budget proxy, not milliseconds. */
    var workUnits: Long = 0
        private set

    fun resetWorkUnits() {
        workUnits = 0
    }

    fun addOccupancyListener(listener: GasFieldOccupancyListener) {
        listeners += listener
    }

    // ---------------------------------------------------------------- state

    /** Add a legacy formula-labelled quantity. This path cannot participate in graph reactions. */
    fun addMoles(cell: GasCell, species: String, amount: Double) {
        require(species.isNotBlank()) { "Gas identity cannot be blank" }
        require(amount.isFinite() && amount >= 0.0) { "Gas amount must be finite and non-negative" }
        if (amount == 0.0) return
        addMolesByIdentity(cell, species, amount)
    }

    /** Add a graph-identified gas quantity; isomers remain separate even when their formula matches. */
    fun addMoles(cell: GasCell, molecule: Molecule, amount: Double) {
        require(molecule.validate().isEmpty()) { "Gas species must be a valid molecular graph" }
        require(amount.isFinite() && amount >= 0.0) { "Gas amount must be finite and non-negative" }
        if (amount == 0.0) return
        val key = identityKey(molecule)
        molecularWitnesses[key]?.let { existing ->
            require(existing.canonicalKey() == molecule.canonicalKey()) { "Molecular identity collision" }
        }
        molecularWitnesses[key] = molecule
        addMolesByIdentity(cell, key, amount)
    }

    fun molesOf(cell: GasCell, species: String): Double = cells[cell]?.get(species) ?: 0.0

    fun molesOf(cell: GasCell, molecule: Molecule): Double = molesOf(cell, identityKey(molecule))

    /** Graph-aware contents only. Unresolved legacy formula entries are deliberately excluded. */
    fun molecularContents(cell: GasCell): List<GasMolecularAmount> = cells[cell].orEmpty()
        .mapNotNull { (key, amount) -> molecularWitnesses[key]?.let { GasMolecularAmount(it, amount) } }
        .filter { it.moles > TRACE_MOLES }
        .sortedBy { it.molecule.canonicalKey() }

    fun speciesIn(cell: GasCell): Set<String> =
        cells[cell]?.filterValues { it > TRACE_MOLES }?.keys ?: emptySet()

    fun occupiedCells(): Set<GasCell> = cells.keys.toSet()

    fun allSpecies(): Set<String> =
        cells.values.flatMapTo(linkedSetOf()) { it.keys }.filter { molesOfSpecies(it) > TRACE_MOLES }.toSet()

    fun totalMoles(species: String): Double = molesOfSpecies(species)

    fun totalMoles(): Double = cells.values.sumOf { contents -> contents.values.sum() }

    /**
     * Apply at most one applicable ordinary environmental reaction per occupied cell. Only graph-
     * identified entries are considered; legacy formula labels are preserved but never guessed into
     * molecular structures. Every plan is balanced by the shared ReactionEngine and committed to a
     * cell only after the complete replacement composition has been calculated.
     */
    @JvmOverloads
    fun applyEnvironmentalReactions(
        seconds: Double,
        environmentProvider: GasCellReactionEnvironmentProvider,
        planner: GasCellReactionPlanner = GasCellReactionPlanner(),
    ): Map<GasCell, GasCellReactionPlan> {
        require(seconds.isFinite() && seconds > 0.0) { "Reaction step must be finite and positive" }
        val plans = linkedMapOf<GasCell, GasCellReactionPlan>()
        for (cell in occupiedCells().sortedWith(compareBy<GasCell> { it.x }.thenBy { it.y }.thenBy { it.z })) {
            workUnits++
            val before = molecularContents(cell)
            if (before.isEmpty()) continue
            workUnits += before.size
            val environment = environmentProvider.environment(cell, before) ?: continue
            val plan = planner.plan(before, environment, seconds) ?: continue
            val after = plan.applyTo(before)
            val afterByKey = after.associateBy { identityKey(it.molecule) }
            val beforeKeys = before.mapTo(linkedSetOf()) { identityKey(it.molecule) }

            before.forEach { amount ->
                val key = identityKey(amount.molecule)
                setMoles(cell, key, afterByKey[key]?.moles ?: 0.0)
            }
            after.filter { identityKey(it.molecule) !in beforeKeys }.forEach { amount ->
                val key = identityKey(amount.molecule)
                molecularWitnesses[key] = amount.molecule
                setMoles(cell, key, amount.moles)
            }
            plans[cell] = plan
        }
        return plans
    }

    // ------------------------------------------------------------------ LOD

    fun cellSize(level: Int): Double {
        require(level >= 0) { "LOD level must not be negative" }
        return cellSizeMetres * (1 shl level)
    }

    /** The cell of [level] containing the given world position in metres. */
    fun cellOf(x: Double, y: Double, z: Double, level: Int = 0): GasCell {
        val size = cellSize(level)
        return GasCell(
            kotlin.math.floor(x / size).toInt(),
            kotlin.math.floor(y / size).toInt(),
            kotlin.math.floor(z / size).toInt(),
        )
    }

    /** Moles of [species] aggregated over the level-[level] cell containing [cell] - a coarse query. */
    fun totalMolesAround(cell: GasCell, level: Int, species: String): Double {
        if (level == 0) return molesOf(cell, species)
        val factor = 1 shl level
        val base = GasCell(cell.x * factor, cell.y * factor, cell.z * factor)
        var sum = 0.0
        for (dx in 0 until factor) for (dy in 0 until factor) for (dz in 0 until factor) {
            sum += molesOf(GasCell(base.x + dx, base.y + dy, base.z + dz), species)
        }
        return sum
    }

    // ---------------------------------------------------------------- step

    /**
     * Advance the field by [seconds].
     *
     * @param openToAtmosphere which cells exchange air with the outside world.
     * @param airChangesPerSecond ventilation rate for those cells, in volume exchanges per second.
     * @param sinkPerSecond first-order removal rate per cell and species - filtration/scrubbing.
     * @param buoyantDriftMetresPerSecond drift speed per unit of buoyancy excess.
     */
    fun advance(
        seconds: Double,
        properties: GasFieldProperties,
        openToAtmosphere: (GasCell) -> Boolean = { false },
        airChangesPerSecond: Double = 0.0,
        sinkPerSecond: (GasCell, String) -> Double = { _, _ -> 0.0 },
        buoyantDriftMetresPerSecond: Double = DEFAULT_BUOYANT_DRIFT_METRES_PER_SECOND,
    ) {
        require(seconds > 0.0) { "Time step must be positive" }
        require(airChangesPerSecond >= 0.0) { "Ventilation rate must not be negative" }
        require(buoyantDriftMetresPerSecond >= 0.0) { "Drift speed must not be negative" }

        for (species in allSpecies()) {
            workUnits++

            // 1. Diffusion by concentration gradient, sub-stepped to stay inside the stability limit.
            val molecule = molecularWitnesses[species]
            val diffusion = if (molecule == null) {
                properties.diffusionSquareMetersPerSecond(species)
            } else {
                properties.diffusionSquareMetersPerSecond(molecule)
            }
            if (diffusion != null && diffusion > 0.0) {
                val k = diffusion * seconds / (cellSizeMetres * cellSizeMetres)
                val subSteps = if (k <= MAX_EXPLICIT_EXCHANGE) 1 else ceil(k / MAX_EXPLICIT_EXCHANGE).toInt()
                val exchange = k / subSteps
                repeat(subSteps) { diffuse(species, exchange) }
            }

            // 2. Vertical buoyancy bias: denser than air settles, lighter rises.
            val molarMass = if (molecule == null) {
                properties.molarMassGramsPerMole(species)
            } else {
                properties.molarMassGramsPerMole(molecule)
            }
            val ratio = GasPhase.buoyancyRelativeToAir(molarMass)
            if (ratio != 1.0 && buoyantDriftMetresPerSecond > 0.0) {
                val fraction = buoyantDriftMetresPerSecond * abs(ratio - 1.0) * seconds / cellSizeMetres
                drift(species, fraction.coerceAtMost(0.5), rises = ratio < 1.0)
            }

            // 3. Ventilation exchanges open cells with the atmosphere.
            if (airChangesPerSecond > 0.0) {
                val exchanged = 1.0 - exp(-airChangesPerSecond * seconds)
                ventilate(species, exchanged, openToAtmosphere)
            }

            // 4. Sinks: filtration and scrubbing as a first-order removal rate.
            filter(species, seconds, sinkPerSecond)
        }
    }

    // ----------------------------------------------------------- mechanisms

    private fun addMolesByIdentity(cell: GasCell, species: String, amount: Double) {
        val contents = cells.getOrPut(cell) { linkedMapOf() }
        val before = contents[species] ?: 0.0
        contents[species] = before + amount
        if (before <= TRACE_MOLES && before + amount > TRACE_MOLES) announce(cell, species, entered = true)
    }

    private fun setMoles(cell: GasCell, species: String, amount: Double) {
        val contents = cells.getOrPut(cell) { linkedMapOf() }
        val before = contents[species] ?: 0.0
        if (amount > TRACE_MOLES) {
            contents[species] = amount
            if (before <= TRACE_MOLES) announce(cell, species, entered = true)
        } else {
            contents.remove(species)
            if (before > TRACE_MOLES) announce(cell, species, entered = false)
        }
        if (contents.isEmpty()) cells.remove(cell)
    }

    private fun diffuse(species: String, exchange: Double) {
        if (exchange <= 0.0) return
        val deltas = HashMap<GasCell, Double>()
        for ((cell, contents) in cells) {
            val n = contents[species] ?: continue
            workUnits++
            for (offset in HALF_NEIGHBOURS) {
                val neighbour = GasCell(cell.x + offset.x, cell.y + offset.y, cell.z + offset.z)
                val flux = exchange * (n - molesOf(neighbour, species))
                if (flux == 0.0) continue
                workUnits++
                deltas[cell] = (deltas[cell] ?: 0.0) - flux
                deltas[neighbour] = (deltas[neighbour] ?: 0.0) + flux
            }
        }
        applyDeltas(species, deltas)
    }

    private fun drift(species: String, fraction: Double, rises: Boolean) {
        if (fraction <= 0.0) return
        val deltas = HashMap<GasCell, Double>()
        for ((cell, contents) in cells) {
            val n = contents[species] ?: continue
            workUnits++
            val moved = fraction * n
            val target = GasCell(cell.x, cell.y + if (rises) 1 else -1, cell.z)
            deltas[cell] = (deltas[cell] ?: 0.0) - moved
            deltas[target] = (deltas[target] ?: 0.0) + moved
        }
        applyDeltas(species, deltas)
    }

    private fun ventilate(species: String, exchanged: Double, openToAtmosphere: (GasCell) -> Boolean) {
        if (exchanged <= 0.0) return
        val deltas = HashMap<GasCell, Double>()
        for ((cell, contents) in cells) {
            val n = contents[species] ?: continue
            workUnits++
            if (openToAtmosphere(cell)) deltas[cell] = (deltas[cell] ?: 0.0) - exchanged * n
        }
        applyDeltas(species, deltas)
    }

    private fun filter(species: String, seconds: Double, sinkPerSecond: (GasCell, String) -> Double) {
        val deltas = HashMap<GasCell, Double>()
        for ((cell, contents) in cells) {
            val n = contents[species] ?: continue
            workUnits++
            val rate = sinkPerSecond(cell, species)
            if (rate <= 0.0) continue
            deltas[cell] = (deltas[cell] ?: 0.0) - (1.0 - exp(-rate * seconds)) * n
        }
        applyDeltas(species, deltas)
    }

    private fun applyDeltas(species: String, deltas: Map<GasCell, Double>) {
        for ((cell, delta) in deltas) {
            val contents = cells.getOrPut(cell) { linkedMapOf() }
            val updated = (contents[species] ?: 0.0) + delta
            if (updated > TRACE_MOLES) {
                val wasEmpty = (contents[species] ?: 0.0) <= TRACE_MOLES
                contents[species] = updated
                if (wasEmpty) announce(cell, species, entered = true)
            } else {
                contents.remove(species)
                announce(cell, species, entered = false)
            }
        }
        cells.entries.removeAll { it.value.isEmpty() }
    }

    private fun announce(cell: GasCell, species: String, entered: Boolean) {
        for (listener in listeners) {
            if (entered) listener.speciesEntered(cell, species) else listener.speciesLeft(cell, species)
        }
    }

    private fun molesOfSpecies(species: String): Double =
        cells.values.sumOf { it[species] ?: 0.0 }
}
