package io.github.antonovichkorp.chemmod.core.gas

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Volumetric gas cloud and blast shockwave physics.
 * Rankine-Hugoniot relations for detonation overpressure, arrival time, impulse.
 * Wilson cloud condensation ring. Zero redstone.
 */

/** Result of a blast shockwave at a given distance. */
data class BlastWaveResult(
    /** Peak overpressure above ambient (Pascals). */
    val peakOverpressurePascals: Double,
    /** Arrival time of shock front (seconds). */
    val arrivalTimeSeconds: Double,
    /** Positive phase duration (seconds). */
    val positivePhaseDurationSeconds: Double,
    /** Impulse (Pa*s) - integral of overpressure over positive phase. */
    val impulsePaS: Double,
    /** Dynamic pressure (wind) at peak (Pascals). */
    val dynamicPressurePascals: Double,
    /** Whether Wilson condensation cloud forms. */
    val wilsonCloud: Boolean,
    /** Wilson cloud radius (metres) if formed. */
    val wilsonCloudRadiusMetres: Double,
)

/** Rankine-Hugoniot blast shockwave model.
 * Computes peak overpressure, arrival time, impulse, dynamic pressure, Wilson cloud.
 * Based on Kinney-Graham and Brode scaling laws for TNT equivalence. */
object BlastShockwaveModel {

    /** Explosive source parameters for blast calculation. */
    public data class ExplosiveSource(
        /** Total energy released (Joules). Equivalent TNT mass * 4.184e6 J/kg. */
        val energyJoules: Double,
        /** Ambient pressure (Pa) at source altitude. */
        val ambientPressurePascals: Double,
        /** Ambient temperature (K) at source altitude. */
        val ambientTemperatureKelvin: Double,
        /** Ratio of specific heats (gamma) for ambient air. */
        val gamma: Double = 1.4,
        /** Molecular mass of ambient air (kg/mol). */
        val molarMassKgPerMol: Double = 0.0289647,
    ) {
        init {
            require(energyJoules > 0.0) { "Energy must be positive" }
            require(ambientPressurePascals > 0.0) { "Ambient pressure must be positive" }
            require(ambientTemperatureKelvin > 0.0) { "Ambient temperature must be positive" }
            require(gamma > 1.0 && gamma < 2.0) { "Gamma must be in (1, 2)" }
        }
    }

    private const val GAMMA = 1.4
    private const val R_GAS = 8.314462618
    private const val TNT_ENERGY_PER_KG = 4.184e6

    /** Compute blast wave parameters at a given distance from explosive source. */
    fun compute(source: ExplosiveSource, distanceMetres: Double): BlastWaveResult {
        require(distanceMetres > 0.0) { "Distance must be positive" }

        // Scaled distance Z = R / E^(1/3) (m / J^(1/3))
        val e13 = Math.pow(source.energyJoules, 1.0 / 3.0)
        val z = distanceMetres / e13

        // Peak overpressure (Kinney-Graham approximation for TNT in air)
        // log10(P/Po) = a - b*log10(Z) + c*log10(Z)^2 + d*log10(Z)^3
        // Valid for 0.1 < Z < 100 m/J^(1/3)
        val logZ = Math.log10(z)
        val a = 1.7814
        val b = 1.9633
        val c = 0.1481
        val d = 0.0082
        val logPoverPo = a - b * logZ + c * logZ * logZ + d * logZ * logZ * logZ
        val peakOverpressureRatio = Math.pow(10.0, logPoverPo)
        val peakOverpressurePascals: Double = (peakOverpressureRatio - 1.0) * source.ambientPressurePascals

        // Arrival time (Brode approximation)
        // t = (R / c0) * (1 + 0.5*(gamma-1)*M) where M is shock Mach number
        // Simplified: t ≈ Z * (E^(1/3) / c0) * f(M)
        val c0 = sqrt(source.gamma * R_GAS * source.ambientTemperatureKelvin / source.molarMassKgPerMol)
        val mach = shockMachFromOverpressure(peakOverpressureRatio, source.gamma)
        val arrivalTime = (distanceMetres / c0) * (1.0 + 0.5 * (source.gamma - 1.0) * mach)

        // Positive phase duration (Kinney-Graham)
        // td = E^(1/3) * f(Z) / c0
        val tdScaled = positivePhaseDurationScaled(z)
        val positivePhaseDuration: Double = (tdScaled * e13) / c0

        // Impulse (Pa*s) - integral of overpressure over positive phase
        // I = P_peak * td * shape_factor (typically 0.5-0.7 for Friedlander waveform)
        val shapeFactor = 0.6
        val impulse: Double = peakOverpressurePascals * positivePhaseDuration * shapeFactor

        // Dynamic pressure (wind) at peak: q = 0.5 * rho * u^2
        // From Rankine-Hugoniot: u = 2*c0*(M^2-1)/((gamma+1)*M)
        val rho0 = source.ambientPressurePascals * source.molarMassKgPerMol / (R_GAS * source.ambientTemperatureKelvin)
        val particleVelocity = 2.0 * c0 * (mach * mach - 1.0) / ((source.gamma + 1.0) * mach)
        val dynamicPressure = 0.5 * rho0 * particleVelocity * particleVelocity

        // Wilson condensation cloud check
        // Forms when adiabatic expansion behind shock cools air below dew point
        // Simplified: forms if peak overpressure > ~20 kPa and humidity > 50%
        val wilsonCloud = peakOverpressurePascals > 20000.0 && source.ambientTemperatureKelvin > 273.0
        val wilsonCloudRadius = if (wilsonCloud) {
            // Rough scaling: cloud radius ~ 0.3 * distance for strong shocks
            distanceMetres * 0.3
        } else 0.0

        return BlastWaveResult(
            peakOverpressurePascals = max(0.0, peakOverpressurePascals),
            arrivalTimeSeconds = arrivalTime,
            positivePhaseDurationSeconds = positivePhaseDuration,
            impulsePaS = impulse,
            dynamicPressurePascals = dynamicPressure,
            wilsonCloud = wilsonCloud,
            wilsonCloudRadiusMetres = wilsonCloudRadius,
        )
    }

    /** Shock Mach number from pressure ratio across shock. */
    fun shockMachFromOverpressure(pRatio: Double, gamma: Double): Double {
        // Rankine-Hugoniot: P2/P1 = 1 + 2*gamma/(gamma+1) * (M^2 - 1)
        val term = (pRatio - 1.0) * (gamma + 1.0) / (2.0 * gamma)
        return sqrt(max(1.0, 1.0 + term))
    }

    /**
     * Normal reflection coefficient for shock wave in air (gamma = 1.4).
     * Cr = (14*P0 + 8*ΔP) / (7*P0 + ΔP)
     * - As ΔP -> 0 (acoustic limit): Cr -> 2.0
     * - As ΔP -> ∞ (strong shock): Cr -> 8.0
     * - Monotonically increasing from 2.0 to 8.0
     */
    fun normalReflectionCoefficient(ambientPressurePascals: Double, overpressurePascals: Double): Double {
        require(ambientPressurePascals > 0.0) { "Ambient pressure must be positive" }
        require(overpressurePascals >= 0.0) { "Overpressure must be non-negative" }
        return (14.0 * ambientPressurePascals + 8.0 * overpressurePascals) /
               (7.0 * ambientPressurePascals + overpressurePascals)
    }

    /** Scaled positive phase duration (Brode). */
    private fun positivePhaseDurationScaled(z: Double): Double {
        // Empirical fit: td_scaled = A * z^B for different Z ranges
        return when {
            z < 1.0 -> 0.1 * z
            z < 10.0 -> 0.1 * z * sqrt(z)
            else -> 0.3 * Math.pow(z, 0.3)
        }
    }

    private fun log10(x: Double): Double = Math.log10(x)

    /** Create explosive source from TNT equivalent mass (kg). */
    fun fromTntMass(tntKg: Double, ambientPressure: Double = 101325.0, ambientTemp: Double = 288.15): ExplosiveSource {
        return ExplosiveSource(
            energyJoules = tntKg * TNT_ENERGY_PER_KG,
            ambientPressurePascals = ambientPressure,
            ambientTemperatureKelvin = ambientTemp,
        )
    }

    /** Create explosive source from fuel-air mixture energy. */
    fun fromFuelAir(
        fuelMoles: Double,
        heatOfCombustionJPerMol: Double,
        ambientPressure: Double = 101325.0,
        ambientTemp: Double = 288.15,
    ): ExplosiveSource {
        return ExplosiveSource(
            energyJoules = fuelMoles * heatOfCombustionJPerMol,
            ambientPressurePascals = ambientPressure,
            ambientTemperatureKelvin = ambientTemp,
        )
    }
}

/** Volumetric gas cloud expanding from a release point.
 * Models buoyant rise, atmospheric diffusion, and ground-hugging behavior. */
data class VolumetricGasCloud(
    val id: String,
    val centerX: Double,
    val centerY: Double,
    val centerZ: Double,
    val radiusMetres: Double,
    val totalMoles: Double,
    val molarMassKgPerMol: Double,
    val releaseTemperatureKelvin: Double,
    val releaseTimeSeconds: Double,
    val speciesKey: String,
) {
    /** Cloud volume (m^3) assuming spherical cap or sphere. */
    fun volume(): Double = 4.0 / 3.0 * 3.14159 * radiusMetres * radiusMetres * radiusMetres

    /** Average density (kg/m^3). */
    fun densityKgPerM3(): Double = totalMoles * molarMassKgPerMol / volume()

    /** Buoyancy ratio relative to ambient air (M_air = 0.02896 kg/mol). */
    fun buoyancyRatio(): Double = molarMassKgPerMol / 0.0289647

    /** Terminal rise/settle velocity (m/s) for a spherical cloud. */
    fun terminalVelocity(ambientDensity: Double, dynamicViscosity: Double = 1.8e-5): Double {
        val rhoCloud = densityKgPerM3()
        val deltaRho = ambientDensity - rhoCloud
        if (deltaRho <= 0.0) return 0.0 // Neutrally buoyant or heavier
        // Stokes regime for small clouds, turbulent for large
        val re = 2.0 * radiusMetres * kotlin.math.abs(deltaRho) * 9.80665 / dynamicViscosity
        return if (re < 1.0) {
            // Stokes: v = 2/9 * g * r^2 * delta_rho / mu
            2.0 / 9.0 * 9.80665 * radiusMetres * radiusMetres * deltaRho / dynamicViscosity
        } else {
            // Turbulent: v = sqrt(8/3 * g * r * delta_rho / rho_ambient / Cd)
            sqrt(8.0 / 3.0 * 9.80665 * radiusMetres * deltaRho / ambientDensity / 0.47)
        }
    }

    /** Expand cloud by diffusion and buoyancy over time dt. */
    fun advance(
        dtSeconds: Double,
        ambient: Atmosphere,
        altitudeMetres: Double,
        windVelocity: Double = 0.0,
    ): VolumetricGasCloud {
        val ambTemp = ambient.temperatureKelvin(altitudeMetres)
        val ambPress = ambient.pressurePascals(altitudeMetres)
        val ambDensity = ambPress * 0.0289647 / (8.314462618 * ambTemp)

        // Diffusive growth: r^2 = r0^2 + 2*D*t
        val diffCoeff = 0.1 // m^2/s typical for atmospheric turbulence
        val newRadius = sqrt(radiusMetres * radiusMetres + 2.0 * diffCoeff * dtSeconds)

        // Buoyant displacement
        val vTerm = terminalVelocity(ambDensity)
        val newY = centerY + vTerm * dtSeconds

        // Wind advection
        val newX = centerX + windVelocity * dtSeconds

        return copy(
            centerX = newX,
            centerY = newY,
            centerZ = centerZ,
            radiusMetres = newRadius,
        )
    }

    /** Concentration at a point (mol/m^3) assuming Gaussian distribution. */
    fun concentrationAt(x: Double, y: Double, z: Double): Double {
        val dx = x - centerX
        val dy = y - centerY
        val dz = z - centerZ
        val dist2 = dx * dx + dy * dy + dz * dz
        val sigma = radiusMetres / 2.0
        val peakConcentration = totalMoles / (volume() * 0.5) // Peak is ~2x average for Gaussian
        return peakConcentration * exp(-dist2 / (2.0 * sigma * sigma))
    }
}

/** Gas cloud manager for world-scale volumetric clouds. */
class VolumetricGasCloudManager {
    private val clouds = mutableListOf<VolumetricGasCloud>()

    fun addCloud(cloud: VolumetricGasCloud) {
        clouds.add(cloud)
    }

    fun removeCloud(id: String): Boolean {
        val idx = clouds.indexOfFirst { it.id == id }
        if (idx >= 0) {
            clouds.removeAt(idx)
            return true
        }
        return false
    }

    fun advanceAll(dtSeconds: Double, ambient: Atmosphere, baseAltitude: Double, wind: Double = 0.0) {
        for (i in clouds.indices) {
            clouds[i] = clouds[i].advance(dtSeconds, ambient, baseAltitude + clouds[i].centerY, wind)
        }
        // Remove dissipated clouds (radius > 100m or moles < 1e-6)
        clouds.removeAll { it.radiusMetres > 100.0 || it.totalMoles < 1e-6 }
    }

    /** Total gas density at a point from all clouds (kg/m^3). */
    fun totalDensityAt(x: Double, y: Double, z: Double): Double {
        var sum = 0.0
        for (cloud in clouds) {
            val conc = cloud.concentrationAt(x, y, z)
            sum += conc * cloud.molarMassKgPerMol
        }
        return sum
    }

    /** Peak blast wind velocity at a point from all active blast waves (m/s). */
    fun blastWindAt(
        x: Double, y: Double, z: Double,
        sources: List<BlastShockwaveModel.ExplosiveSource>,
        currentTime: Double,
    ): Double {
        var maxWind = 0.0
        for (source in sources) {
            for (cloud in clouds) {
                val dist = sqrt(Math.pow(x - cloud.centerX, 2.0) + Math.pow(y - cloud.centerY, 2.0) + Math.pow(z - cloud.centerZ, 2.0))
                val blast = BlastShockwaveModel.compute(source, dist)
                if (currentTime >= blast.arrivalTimeSeconds &&
                    currentTime <= blast.arrivalTimeSeconds + blast.positivePhaseDurationSeconds) {
                    // Particle velocity during positive phase
                    val c0 = sqrt(1.4 * 8.314462618 * source.ambientTemperatureKelvin / 0.0289647)
                    val mach = BlastShockwaveModel.shockMachFromOverpressure(
                        1.0 + blast.peakOverpressurePascals / source.ambientPressurePascals, 1.4)
                    val u = 2.0 * c0 * (mach * mach - 1.0) / (2.4 * mach)
                    maxWind = max(maxWind, u)
                }
            }
        }
        return maxWind
    }

    fun getClouds(): List<VolumetricGasCloud> = clouds.toList()
}