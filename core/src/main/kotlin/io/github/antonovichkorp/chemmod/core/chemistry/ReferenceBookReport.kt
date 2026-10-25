package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.CommonSubstances
import io.github.antonovichkorp.chemmod.core.Molecule

/** A snapshot of properties the current models actually return for one finite catalog sample. */
data class ReferenceBookReport(
    /** Null for a valid rule-generated structure without a bundled trivial-name alias. */
    val canonicalName: String?,
    val formula: String,
    val molarMassGramsPerMole: Double,
    val boilingPointCelsius: Double?,
    val boilingPointBasis: String,
    val combustionEnthalpyKilojoulesPerMole: Double?,
    /** Null means the current model has no solubility result; it must not be displayed as insoluble. */
    val solubility: CatalogSolubility?,
    /** Structural/model flags, not regulatory toxicity ratings or a safety assessment. */
    val flags: List<String>,
    val propertyModel: String,
)

/**
 * Builds reference-book data only for finite participants of the authored reaction network.
 * Values come from the same graph-derived property/catalog models used elsewhere; no
 * substance-specific overrides or classifications are introduced here.
 */
object ReferenceBookReports {
    fun forReference(molecule: Molecule): ReferenceBookReport? {
        if (!ReactionSpeciesCatalog.contains(molecule.canonicalKey())) return null
        val reference = CommonSubstances.findByCanonicalKey(molecule.canonicalKey())
        val properties = molecule.properties()
        val descriptor = ReferenceMoleculeCatalog.describe(molecule.graph)
        val solubility = descriptor.solubility.takeUnless { it == CatalogSolubility.UNKNOWN }
        return ReferenceBookReport(
            canonicalName = reference?.canonicalName,
            formula = molecule.formula(),
            molarMassGramsPerMole = properties.molarMass,
            boilingPointCelsius = properties.boilingPointC,
            boilingPointBasis = properties.boilingPointBasis,
            combustionEnthalpyKilojoulesPerMole = properties.combustionEnthalpyKilojoulesPerMole,
            solubility = solubility,
            flags = properties.flags.sorted(),
            propertyModel = properties.model,
        )
    }
}
