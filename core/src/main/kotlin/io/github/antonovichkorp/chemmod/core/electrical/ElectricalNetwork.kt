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
        // Build conductance matrix G (n x n) and current vector I (n x 1)
        val n = nodeCount
        val G = Array(n) { DoubleArray(n) { 0.0 } }
        val I = DoubleArray(n) { injectedCurrent[it] }

        // Add resistor branches: G_ii += 1/R, G_jj += 1/R, G_ij -= 1/R, G_ji -= 1/R
        for (b in branches) {
            when (b) {
                is ResistorBranch -> {
                    val g = 1.0 / b.resistance
                    G[b.fromNode][b.fromNode] += g
                    G[b.toNode][b.toNode] += g
                    G[b.fromNode][b.toNode] -= g
                    G[b.toNode][b.fromNode] -= g
                }
                is VoltageSourceBranch -> {
                    // Voltage source with internal resistance R_int:
                    // Model as: ideal voltage source in series with resistor.
                    // Current from fromNode to toNode: I = (V_from - V_to - emf) / R_int
                    // Contribute to nodal equations as a resistor plus a current source.
                    val g = 1.0 / b.internalResistance
                    G[b.fromNode][b.fromNode] += g
                    G[b.toNode][b.toNode] += g
                    G[b.fromNode][b.toNode] -= g
                    G[b.toNode][b.fromNode] -= g
                    // EMF acts as current injection: I_from += emf/R_int, I_to -= emf/R_int
                    I[b.fromNode] += b.emf / b.internalResistance
                    I[b.toNode] -= b.emf / b.internalResistance
                }
            }
        }

        // Ground row: V_ground = 0
        for (j in 0 until n) G[groundNodeId][j] = 0.0
        G[groundNodeId][groundNodeId] = 1.0
        I[groundNodeId] = 0.0

        // Solve G * V = I via Gaussian elimination with partial pivoting
        val V = solveLinearSystem(G, I)
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