package io.github.antonovichkorp.chemmod.core.chemistry

import io.github.antonovichkorp.chemmod.core.CommonSubstances
import io.github.antonovichkorp.chemmod.core.Molecule

/** A snapshot of properties the current models actually return for one finite catalog sample. */
data class ReferenceBookReport(
    val canonicalName: String,
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
 * Builds reference-book data only for the owner's finite named project catalog. Values come from
 * the same graph-derived property/catalog models used elsewhere; no substance-specific overrides
 * or classifications are introduced here.
 */
object ReferenceBookReports {
    fun forReference(molecule: Molecule): ReferenceBookReport? {
        val reference = CommonSubstances.findByCanonicalKey(molecule.canonicalKey()) ?: return null
        val properties = molecule.properties()
        val descriptor = ReferenceMoleculeCatalog.describe(molecule.graph)
        val solubility = descriptor.solubility.takeUnless { it == CatalogSolubility.UNKNOWN }
        return ReferenceBookReport(
            canonicalName = reference.canonicalName,
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
