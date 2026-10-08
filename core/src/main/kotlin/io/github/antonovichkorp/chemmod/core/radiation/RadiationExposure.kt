package io.github.antonovichkorp.chemmod.core.radiation

import kotlin.math.exp
import kotlin.math.max

/** The transport class of radiation at the field boundary. */
enum class RadiationKind {
    ALPHA,
    BETA,
    GAMMA,
    NEUTRON,
}

/**
 * Calibrated ambient-dose contribution of one source at one metre with no
 * shielding. The conversion from decay activity to an ambient dose equivalent
 * is deliberately supplied by data: it depends on isotope, emission spectrum,
 * source geometry, and the future game balancing policy.
 */
data class RadiationEmission(
    val kind: RadiationKind,
    val doseRateMicrosievertPerHourAtOneMetre: Double,
) {
    init {
        require(doseRateMicrosievertPerHourAtOneMetre >= 0.0 &&
            doseRateMicrosievertPerHourAtOneMetre.isFinite()
        ) { "Ambient dose rate must be finite and non-negative" }
    }
}

/**
 * A source profile may have multiple transport classes. It contains no world
 * coordinates or Minecraft state and can therefore be validated and simulated
 * identically on a dedicated server and in core tests.
 */
data class RadiationSourceProfile(
    val id: String,
    val emissions: List<RadiationEmission>,
) {
    init {
        require(id.matches(Regex("[a-z0-9_.-]+"))) { "Invalid radiation source id: $id" }
        require(emissions.isNotEmpty()) { "A radiation source needs at least one emission" }
    }
}

/**
 * Material coefficients used by a later world adapter. Gamma and neutron
 * attenuation are exponential (I = I₀ × exp(-μx)); beta uses the same smooth,
 * configurable approximation. Alpha uses a finite range and is stopped once a
 * layer reaches that range.
 */
data class ShieldingMaterial(
    val id: String,
    val alphaStoppingThicknessCentimetres: Double,
    val betaLinearAttenuationPerCentimetre: Double,
    val gammaLinearAttenuationPerCentimetre: Double,
    val neutronLinearAttenuationPerCentimetre: Double,
) {
    init {
        require(id.matches(Regex("[a-z0-9_.-]+"))) { "Invalid shielding material id: $id" }
        require(alphaStoppingThicknessCentimetres > 0.0 && alphaStoppingThicknessCentimetres.isFinite()) {
            "Alpha stopping thickness must be finite and positive"
        }
        listOf(
            betaLinearAttenuationPerCentimetre,
            gammaLinearAttenuationPerCentimetre,
            neutronLinearAttenuationPerCentimetre,
        ).forEach { coefficient ->
            require(coefficient >= 0.0 && coefficient.isFinite()) {
                "Attenuation coefficients must be finite and non-negative"
            }
        }
    }
}

data class ShieldingLayer(
    val material: ShieldingMaterial,
    val thicknessCentimetres: Double,
) {
    init {
        require(thicknessCentimetres >= 0.0 && thicknessCentimetres.isFinite()) {
            "Shielding thickness must be finite and non-negative"
        }
    }
}

/**
 * Stateless physical arithmetic for the radiation field. The Minecraft layer
 * is responsible only for ray/block traversal and supplies those blocks as
 * [ShieldingLayer] values; it must not implement a competing attenuation rule.
 */
object RadiationExposure {
    const val REFERENCE_DISTANCE_METRES: Double = 1.0
    private const val SECONDS_PER_HOUR: Double = 3_600.0

    fun transmission(kind: RadiationKind, layers: Iterable<ShieldingLayer>): Double {
        var transmission = 1.0
        for (layer in layers) {
            if (transmission == 0.0) return 0.0
            transmission *= when (kind) {
                RadiationKind.ALPHA -> {
                    max(
                        0.0,
                        1.0 - layer.thicknessCentimetres / layer.material.alphaStoppingThicknessCentimetres,
                    )
                }
                RadiationKind.BETA -> exp(-layer.material.betaLinearAttenuationPerCentimetre * layer.thicknessCentimetres)
                RadiationKind.GAMMA -> exp(-layer.material.gammaLinearAttenuationPerCentimetre * layer.thicknessCentimetres)
                RadiationKind.NEUTRON -> exp(-layer.material.neutronLinearAttenuationPerCentimetre * layer.thicknessCentimetres)
            }
        }
        return transmission.coerceIn(0.0, 1.0)
    }

    /**
     * Dose rate at a target. The profile is calibrated at one metre; geometric
     * dilution follows an inverse-square law outside [minimumDistanceMetres].
     * The lower distance clamp describes the finite size of a source and keeps
     * block-centre sampling deterministic rather than singular.
     */
    fun doseRateMicrosievertPerHour(
        profile: RadiationSourceProfile,
        distanceMetres: Double,
        layers: Iterable<ShieldingLayer> = emptyList(),
        minimumDistanceMetres: Double = 0.25,
    ): Double {
        require(distanceMetres >= 0.0 && distanceMetres.isFinite()) {
            "Distance must be finite and non-negative"
        }
        require(minimumDistanceMetres > 0.0 && minimumDistanceMetres.isFinite()) {
            "Minimum distance must be finite and positive"
        }
        val effectiveDistance = max(distanceMetres, minimumDistanceMetres)
        val geometry = (REFERENCE_DISTANCE_METRES / effectiveDistance) *
            (REFERENCE_DISTANCE_METRES / effectiveDistance)
        val shielding = layers.toList()
        return profile.emissions.sumOf { emission ->
            emission.doseRateMicrosievertPerHourAtOneMetre * geometry * transmission(emission.kind, shielding)
        }
    }

    fun accumulatedDoseMicrosievert(
        doseRateMicrosievertPerHour: Double,
        elapsedSeconds: Double,
    ): Double {
        require(doseRateMicrosievertPerHour >= 0.0 && doseRateMicrosievertPerHour.isFinite()) {
            "Dose rate must be finite and non-negative"
        }
        require(elapsedSeconds >= 0.0 && elapsedSeconds.isFinite()) {
            "Elapsed time must be finite and non-negative"
        }
        return doseRateMicrosievertPerHour * elapsedSeconds / SECONDS_PER_HOUR
    }
}

/** Immutable accumulated dose, kept in µSv to avoid lossy tick-by-tick rounding. */
data class RadiationDose(val microsievert: Double = 0.0) {
    init {
        require(microsievert >= 0.0 && microsievert.isFinite()) {
            "Dose must be finite and non-negative"
        }
    }

    fun addExposure(doseRateMicrosievertPerHour: Double, elapsedSeconds: Double): RadiationDose =
        RadiationDose(microsievert + RadiationExposure.accumulatedDoseMicrosievert(doseRateMicrosievertPerHour, elapsedSeconds))
}
