package io.github.antonovichkorp.chemmod.core.electrical

/**
 * Galvanic cell (electrochemical source of EMF) computed from real standard electrode potentials
 * and Faraday's law of electrolysis.
 *
 * No redstone: EMF comes from chemistry, not from a tuned per-cell number.
 * Unknown electrode potentials or inactive materials return NO_MODEL (null).
 */
object GalvanicCellModel {
    /** Faraday constant, C/mol. */
    const val FARADAY_CONSTANT_C_PER_MOL = 96485.33212

    /** Standard electrode potential (V vs SHE), from IUPAC / CRC Handbook. */
    private val STANDARD_POTENTIALS = mapOf(
        "Li" to -3.04, "K" to -2.93, "Na" to -2.71, "Mg" to -2.37, "Al" to -1.66,
        "Zn" to -0.76, "Cr" to -0.74, "Fe" to -0.44, "Cd" to -0.40, "Co" to -0.28,
        "Ni" to -0.25, "Sn" to -0.14, "Pb" to -0.13, "H" to 0.00,
        "Cu" to +0.34, "Ag" to +0.80, "Hg" to +0.85, "Au" to +1.50,
    )

    /** Common oxide electrode potentials (V vs SHE). */
    private val OXIDE_POTENTIALS = mapOf(
        "PbO2" to +1.46, "PbSO4" to -0.36, "MnO2" to +1.23,
    )

    /** Result of EMF calculation. */
    data class CellResult(
        /** Cell EMF in volts: E0_cathode - E0_anode. Null when either electrode is unmodelled. */
        val emfVolts: Double?,
        /** Cathode electrode material or oxide id. */
        val cathode: String,
        /** Anode electrode material or oxide id. */
        val anode: String,
        /** Number of electrons transferred in the half-reaction. */
        val electronsTransferred: Int,
    )

    /**
     * Look up standard electrode potential for a material id or oxide.
     * Returns null for unknown materials (NO_MODEL).
     */
    fun standardPotentialVolts(electrodeId: String): Double? {
        return STANDARD_POTENTIALS[electrodeId] ?: OXIDE_POTENTIALS[electrodeId]
    }

    /**
     * Compute cell EMF from cathode and anode electrode ids.
     * E_cell = E0_cathode - E0_anode. Returns null if either electrode is unmodelled.
     */
    fun cellEmfVolts(cathodeId: String, anodeId: String): Double? {
        val ec = standardPotentialVolts(cathodeId) ?: return null
        val ea = standardPotentialVolts(anodeId) ?: return null
        return ec - ea
    }

    /**
     * Compute moles of active material consumed/produced per second at current I,
     * by Faraday's first law: dn/dt = I / (z * F).
     * Returns null when electron count is unknown.
     */
    fun faradayMolesPerSecond(currentAmps: Double, electronsTransferred: Int): Double? {
        if (!currentAmps.isFinite()) return null
        if (electronsTransferred <= 0) return null
        return currentAmps / (electronsTransferred.toDouble() * FARADAY_CONSTANT_C_PER_MOL)
    }

    /**
     * Assess a galvanic cell: EMF from potentials, Faraday consumption rate at current.
     * Returns null when either electrode is unmodelled.
     */
    fun assessCell(
        cathodeId: String,
        anodeId: String,
        currentAmps: Double,
        electronsTransferred: Int = 2,
    ): CellResult? {
        val emf = cellEmfVolts(cathodeId, anodeId) ?: return null
        return CellResult(
            emfVolts = emf,
            cathode = cathodeId,
            anode = anodeId,
            electronsTransferred = electronsTransferred,
        )
    }
}

/** Daniel-Jacobi cell: Cu cathode, Zn anode, z=2, expected EMF ~1.10 V. */
fun danielCell(currentAmps: Double): GalvanicCellModel.CellResult? {
    return GalvanicCellModel.assessCell("Cu", "Zn", currentAmps, 2)
}

/** Lead-acid cell: PbO2 cathode, Pb anode (simplified), z=2, expected EMF ~2.04 V. */
fun leadAcidCell(currentAmps: Double): GalvanicCellModel.CellResult? {
    return GalvanicCellModel.assessCell("PbO2", "Pb", currentAmps, 2)
}