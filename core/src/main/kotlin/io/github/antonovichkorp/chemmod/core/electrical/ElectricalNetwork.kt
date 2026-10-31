package io.github.antonovichkorp.chemmod.core.electrical

import kotlin.math.abs

/**
 * A Kirchhoff DC circuit network solved by nodal analysis: G * V = I.
 *
 * Nodes hold electrical potential (V). Branches carry current between nodes.
 * Reference ground node is node 0 with V = 0.0 V fixed.
 * All computations in SI units: Volts, Amperes, Ohms, Siemens, Watts, Kelvins.
 * No redstone anywhere: this is pure electrodynamics.
 */
class ElectricalNetwork internal constructor(
    val nodeCount: Int,
    val groundNodeId: Int,
) {
    /** One value per node: current injected at the node (A). Positive = injected. */
    private val injectedCurrent = DoubleArray(nodeCount) { 0.0 }

    /** Branches between node pairs with their conductance/resistance. */
    private val branches = mutableListOf<Branch>()

    /** Add current injection at a node (A). */
    fun injectCurrent(nodeId: Int, amperes: Double) {
        require(nodeId in 0 until nodeCount) { "Node id $nodeId out of range" }
        injectedCurrent[nodeId] += amperes
    }

    /** Add a resistive branch between two nodes (resistor or cable segment). */
    fun addResistor(fromNode: Int, toNode: Int, resistanceOhms: Double) {
        require(fromNode in 0 until nodeCount && toNode in 0 until nodeCount) { "Node id out of range" }
        require(fromNode != toNode) { "Branch cannot connect a node to itself" }
        require(resistanceOhms > 0.0) { "Resistance must be positive" }
        branches += ResistorBranch(fromNode, toNode, resistanceOhms)
    }

    /** Add a transformer branch between primary and secondary windings. */
    fun addTransformer(
        pFrom: Int, pTo: Int, sFrom: Int, sTo: Int,
        turnsRatio: Double, rPrimaryOhm: Double, rSecondaryOhm: Double,
        gCoreSiemens: Double, sRatedVA: Double,
    ) {
        require(pFrom in 0 until nodeCount && pTo in 0 until nodeCount && sFrom in 0 until nodeCount && sTo in 0 until nodeCount) { "Node id out of range" }
        require(pFrom != pTo) { "Primary branch cannot connect a node to itself" }
        require(sFrom != sTo) { "Secondary branch cannot connect a node to itself" }
        require(turnsRatio > 0.0) { "Turns ratio must be positive" }
        require(rPrimaryOhm >= 0.0) { "Primary resistance must be non-negative" }
        require(rSecondaryOhm >= 0.0) { "Secondary resistance must be non-negative" }
        require(gCoreSiemens >= 0.0) { "Core loss conductance must be non-negative" }
        require(sRatedVA > 0.0) { "Rated VA must be positive" }
        branches += TransformerBranch(pFrom, pTo, sFrom, sTo, turnsRatio, rPrimaryOhm, rSecondaryOhm, gCoreSiemens, sRatedVA)
    }

    /** Add an ideal voltage source branch from node A to node B with EMF and internal resistance. */
    fun addVoltageSource(fromNode: Int, toNode: Int, emfVolts: Double, internalResistanceOhms: Double) {
        require(fromNode in 0 until nodeCount && toNode in 0 until nodeCount) { "Node id out of range" }
        require(fromNode != toNode) { "Branch cannot connect a node to itself" }
        require(emfVolts.isFinite()) { "EMF must be finite" }
        require(internalResistanceOhms > 0.0) { "Internal resistance must be positive" }
        branches += VoltageSourceBranch(fromNode, toNode, emfVolts, internalResistanceOhms)
    }

    /** Solve the network and return node voltages (V) indexed by node id. Ground node = 0.0 V. */
    fun solve(): DoubleArray {
        val n = nodeCount
        val baseG = Array(n) { DoubleArray(n) { 0.0 } }
        val baseI = DoubleArray(n) { injectedCurrent[it] }

        // Add resistor and voltage source branches to base matrices
        for (b in branches) {
            when (b) {
                is ResistorBranch -> {
                    val g = 1.0 / b.resistance
                    baseG[b.fromNode][b.fromNode] += g
                    baseG[b.toNode][b.toNode] += g
                    baseG[b.fromNode][b.toNode] -= g
                    baseG[b.toNode][b.fromNode] -= g
                }
                is VoltageSourceBranch -> {
                    val g = 1.0 / b.internalResistance
                    baseG[b.fromNode][b.fromNode] += g
                    baseG[b.toNode][b.toNode] += g
                    baseG[b.fromNode][b.toNode] -= g
                    baseG[b.toNode][b.fromNode] -= g
                    baseI[b.fromNode] += b.emf / b.internalResistance
                    baseI[b.toNode] -= b.emf / b.internalResistance
                }
                is TransformerBranch -> {
                    // Transformer branches handled in fixed-point iteration below
                }
                is CircuitBreakerBranch -> {
                    // Circuit breaker handled in fixed-point iteration and update()
                }
                else -> {
                    // SwitchBranch, MultiTapTransformerBranch, etc. - handle in fixed-point or ignore
                }
            }
        }

        // Ground row (fixed for all iterations)
        for (j in 0 until n) baseG[groundNodeId][j] = 0.0
        baseG[groundNodeId][groundNodeId] = 1.0
        baseI[groundNodeId] = 0.0

        // Fixed-point iteration for transformer branches (G * V = I with coupling)
        var V = solveLinearSystem(baseG, baseI)
        val tol = 1e-9
        val maxIter = 100

        for (iter in 0 until maxIter) {
            val G = baseG.map { it.copyOf() }.toTypedArray()
            val I = baseI.copyOf()

            for (t in branches.filterIsInstance<TransformerBranch>()) {
                val p1 = t.fromNode
                val p2 = t.toNode
                val s1 = t.secondaryFromNode
                val s2 = t.secondaryToNode
                val n = t.turnsRatio
                val r1 = t.rPrimaryOhm
                val r2 = t.rSecondaryOhm
                val gCore = t.gCoreSiemens

                if (r1 <= 0.0 || r2 <= 0.0) continue

                val A = 1.0 + gCore * r1
                val C = r2 + n * n * r1 / A
                val dIp_dVp = n * n / (A * A * C) + gCore / A
                val dIp_dVs = -n / (A * C)
                val dIs_dVp = -n / (A * C)
                val dIs_dVs = 1.0 / C

                val Vp = V[p1] - V[p2]
                val Vs = V[s1] - V[s2]
                val Ip = Vp * dIp_dVp + Vs * dIp_dVs
                val Is = Vp * dIs_dVp + Vs * dIs_dVs

                G[p1][p1] += dIp_dVp
                G[p1][p2] -= dIp_dVp
                G[p2][p1] -= dIp_dVp
                G[p2][p2] += dIp_dVp

                G[p1][s1] += dIp_dVs
                G[p1][s2] -= dIp_dVs
                G[p2][s1] -= dIp_dVs
                G[p2][s2] += dIp_dVs

                G[s1][p1] += dIs_dVp
                G[s1][p2] -= dIs_dVp
                G[s2][p1] -= dIs_dVp
                G[s2][p2] += dIs_dVp

                G[s1][s1] += dIs_dVs
                G[s1][s2] -= dIs_dVs
                G[s2][s1] -= dIs_dVs
                G[s2][s2] += dIs_dVs
            }

// Circuit breaker state update
            for (cb in branches.filterIsInstance<CircuitBreakerBranch>()) {
                cb.update(V[cb.fromNode] - V[cb.toNode])
                // When closed, add very low conductance; when tripped, conductance = 0
                when (cb.getState()) {
                    is CircuitBreakerBranchStateClosed -> {
                        val g = 1e6  // very low resistance ~ 0.000001 ohms
                        G[cb.fromNode][cb.fromNode] += g
                        G[cb.toNode][cb.toNode] += g
                        G[cb.fromNode][cb.toNode] -= g
                        G[cb.toNode][cb.fromNode] -= g
                    }
                    is CircuitBreakerBranchStateTripped -> { /* high resistance */ }
                    is CircuitBreakerBranchStateOpenTripped -> { /* open circuit */ }
                }
            }

            val newV = solveLinearSystem(G, I)
            var maxDiff = 0.0
            for (i in 0 until n) {
                val diff = abs(newV[i] - V[i])
                if (diff > maxDiff) maxDiff = diff
            }
            V = newV
            if (maxDiff < tol) break
        }

        // Post-solve: update circuit breaker states based on branch currents
        for (b in branches.filterIsInstance<CircuitBreakerBranch>()) {
            b.update(V[b.fromNode] - V[b.toNode])
        }

        return V
    }

    /** Current through a resistive branch after solve, given node voltages. */
    fun branchCurrent(fromNode: Int, toNode: Int, resistanceOhms: Double): Double {
        return (solve()[fromNode] - solve()[toNode]) / resistanceOhms
    }
}

/** Base branch type. */
sealed class Branch {
    abstract val fromNode: Int
    abstract val toNode: Int
}

/** Resistive branch (cable segment or load resistor). */
data class ResistorBranch(
    override val fromNode: Int,
    override val toNode: Int,
    val resistance: Double,
) : Branch()

/** Voltage source branch with series internal resistance. */
data class VoltageSourceBranch(
    override val fromNode: Int,
    override val toNode: Int,
    val emf: Double,
    val internalResistance: Double,
) : Branch()

/** Transformer branch: two-winding transformer integrated into G*V=I solver.
 * Primary nodes pFrom..pTo, secondary nodes sFrom..sTo.
 * Turns ratio n = N2/N1 > 0. V_secondary = n * V_primary.
 * Winding resistances rPrimaryOhm, rSecondaryOhm.
 * Core loss conductance gCoreSiemens (no-load losses).
 * Rated apparent power sRatedVA (for thermal limit). */
data class TransformerBranch(
    override val fromNode: Int,   // pFrom (primary input)
    override val toNode: Int,     // pTo (primary return / ground reference)
    val secondaryFromNode: Int,   // sFrom
    val secondaryToNode: Int,     // sTo
    val turnsRatio: Double,       // n = N2/N1 > 0
    val rPrimaryOhm: Double,      // primary winding resistance
    val rSecondaryOhm: Double,    // secondary winding resistance (referred to secondary)
    val gCoreSiemens: Double,     // core loss conductance (primary side)
    val sRatedVA: Double,         // rated apparent power (thermal limit)
) : Branch() {
    init {
        require(turnsRatio > 0.0) { "Turns ratio must be positive" }
        require(rPrimaryOhm >= 0.0) { "Primary resistance must be non-negative" }
        require(rSecondaryOhm >= 0.0) { "Secondary resistance must be non-negative" }
        require(gCoreSiemens >= 0.0) { "Core loss conductance must be non-negative" }
        require(sRatedVA > 0.0) { "Rated VA must be positive" }
    }
}

/** Circuit breaker branch added to the ElectricalNetwork. */
data class CircuitBreakerBranch(
    override val fromNode: Int,
    override val toNode: Int,
    val ratedA: Double,
    val iTripInstantA: Double,
    val thermalConstantA2S: Double,
) : Branch() {

    /** Internal thermal state integrating I^2 * t. */
    private var thermalIntegralA2S = 0.0

    /** Current flowing through the breaker (A). */
    private var currentA: Double? = null

    /** Breaker state machine. */
    private var state: CircuitBreakerBranchState = CircuitBreakerBranchStateClosed(0.0)

    /** Rated current (A). */
    val ratedCurrentA = ratedA

    /** Instant trip current (A). */
    val instantTripCurrentA = iTripInstantA

    /** Thermal trip constant I^2 * t (A^2 * s). */
    val thermalTripConstantA2S = thermalConstantA2S

    /** Tripping has occurred. */
    private var tripped = false

    /** Circuit breaker state object from CircuitBreakerBranchState. */
    private val breakerState = CircuitBreakerBranchStateClosed(0.0)

/** Update current and evaluate trip condition.
 *  Called after network solve with branch voltage difference.
 */
    fun update(voltageDeltaV: Double) {
        // Instant electromagnetic trip
        if (!state.isOpenTripped && !state.isTripped && abs(voltageDeltaV) >= iTripInstantA) {
            thermalIntegralA2S = thermalTripConstantA2S  // jump to trip
            state = CircuitBreakerBranchStateTripped(voltageDeltaV)
            tripped = true
            return
        }

        // Thermal trip: I^2 * t integration (using voltage as proxy for current)
        if (!state.isOpenTripped && !state.isTripped) {
            thermalIntegralA2S += voltageDeltaV * voltageDeltaV
            if (thermalIntegralA2S >= thermalTripConstantA2S) {
                state = CircuitBreakerBranchStateOpenTripped(voltageDeltaV)
                tripped = true
                return
            }
        }

        // No trip yet
        state = CircuitBreakerBranchStateClosed(voltageDeltaV)
        tripped = false
    }

    /** Manual reset: closes the breaker, resets thermal integral. */
    fun reset() {
        thermalIntegralA2S = 0.0
        tripped = false
        state = CircuitBreakerBranchStateClosed(0.0)
    }

    /** Get current state. */
    fun getState(): CircuitBreakerBranchState = state

    /** Get current (A) - approximated from voltage delta. */
    fun getCurrentA(): Double = currentA ?: 0.0
}

/** Circuit breaker state types. */
sealed class CircuitBreakerBranchState {
    abstract val isClosed: Boolean
    abstract val isTripped: Boolean
    abstract val isOpenTripped: Boolean
    abstract val currentA: Double?
}

data class CircuitBreakerBranchStateClosed(override val currentA: Double?) : CircuitBreakerBranchState() {
    override val isClosed = true
    override val isTripped = false
    override val isOpenTripped = false
}

data class CircuitBreakerBranchStateTripped(override val currentA: Double?) : CircuitBreakerBranchState() {
    override val isClosed = false
    override val isTripped = true
    override val isOpenTripped = false
}

data class CircuitBreakerBranchStateOpenTripped(override val currentA: Double? = null) : CircuitBreakerBranchState() {
    override val isClosed = false
    override val isTripped = true
    override val isOpenTripped = true
}

/** Factory to build an ElectricalNetwork. */
fun electricalNetwork(nodeCount: Int, groundNodeId: Int = 0): ElectricalNetwork {
    require(nodeCount > 0) { "Network must have at least one node" }
    require(groundNodeId in 0 until nodeCount) { "Ground node id out of range" }
    return ElectricalNetwork(nodeCount, groundNodeId)
}

/** Solve a square linear system A * x = b via Gaussian elimination with partial pivoting. */
internal fun solveLinearSystem(A: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    // Augmented matrix
    val M = Array(n) { DoubleArray(n + 1) }
    for (i in 0 until n) {
        for (j in 0 until n) M[i][j] = A[i][j]
        M[i][n] = b[i]
    }

    for (col in 0 until n) {
        // Partial pivoting: find row with max |M[row][col]|
        var maxRow = col
        var maxVal = abs(M[col][col])
        for (row in col + 1 until n) {
            val v = abs(M[row][col])
            if (v > maxVal) { maxVal = v; maxRow = row }
        }
        if (maxVal < 1e-15) continue // singular column; skip

        // Swap rows
        if (maxRow != col) {
            val temp = M[col]; M[col] = M[maxRow]; M[maxRow] = temp
        }

        // Eliminate below
        for (row in col + 1 until n) {
            val factor = M[row][col] / M[col][col]
            for (k in col until n + 1) {
                M[row][k] -= factor * M[col][k]
            }
        }
    }

    // Back substitution
    val x = DoubleArray(n)
    for (i in n - 1 downTo 0) {
        var sum = M[i][n]
        for (j in i + 1 until n) sum -= M[i][j] * x[j]
        val diag = M[i][i]
        x[i] = if (abs(diag) > 1e-15) sum / diag else 0.0
    }
    return x
}