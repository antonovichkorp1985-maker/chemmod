package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.thermal.ThermalBody;
import io.github.antonovichkorp.chemmod.core.thermal.ThermalTransfer;
import org.jetbrains.annotations.Nullable;

/**
 * Thermal inertia of one laboratory apparatus body.
 *
 * <p>The vessel is a single lumped body with a constant heat capacity. A lit
 * heat source below it is a <em>maintained</em> temperature: the source holds
 * its own temperature by burning its own fuel, so heat flows across the contact
 * instead of the vessel adopting the source temperature instantly. The room
 * drains heat at the same time, which is why the reachable plateau stays below
 * the source temperature.
 *
 * <p>The capacity is that of an empty vessel of roughly 150 g of borosilicate
 * glass. Contents mass and substance heat capacities are deliberately not
 * guessed here; when the catalogue carries that data, the capacity should become
 * vessel plus contents rather than a single constant.
 */
public final class ApparatusThermalModel {
    public static final double AMBIENT_TEMPERATURE_KELVIN = 293.15;
    public static final double HEAT_CAPACITY_JOULES_PER_KELVIN = 120.0;
    /** Direct flame/surface contact: the vessel is within a few percent of the plateau in ~2 s. */
    public static final double SOURCE_CONDUCTANCE_WATTS_PER_KELVIN = 240.0;
    /** Free convection and radiation to the room; much weaker than the flame contact. */
    public static final double AMBIENT_CONDUCTANCE_WATTS_PER_KELVIN = 6.0;

    private ApparatusThermalModel() {}

    public static double warmingTimeConstantSeconds() {
        return HEAT_CAPACITY_JOULES_PER_KELVIN / SOURCE_CONDUCTANCE_WATTS_PER_KELVIN;
    }

    public static double coolingTimeConstantSeconds() {
        return HEAT_CAPACITY_JOULES_PER_KELVIN / AMBIENT_CONDUCTANCE_WATTS_PER_KELVIN;
    }

    /** Temperature the vessel settles at while the given source stays lit. */
    public static double equilibriumTemperatureKelvin(double sourceTemperatureKelvin) {
        validate(sourceTemperatureKelvin, "source temperature");
        double total = SOURCE_CONDUCTANCE_WATTS_PER_KELVIN + AMBIENT_CONDUCTANCE_WATTS_PER_KELVIN;
        return (SOURCE_CONDUCTANCE_WATTS_PER_KELVIN * sourceTemperatureKelvin
            + AMBIENT_CONDUCTANCE_WATTS_PER_KELVIN * AMBIENT_TEMPERATURE_KELVIN) / total;
    }

    /**
     * Advances the body temperature over one exact exponential step.
     *
     * @param temperatureKelvin current body temperature
     * @param elapsedSeconds elapsed wall time; zero leaves the body untouched
     * @param sourceTemperatureKelvin maintained temperature of the lit source
     *     below the apparatus, or {@code null} when nothing is heating it
     * @throws IllegalArgumentException for non-finite or negative inputs
     */
    public static double advance(
        double temperatureKelvin,
        double elapsedSeconds,
        @Nullable Double sourceTemperatureKelvin
    ) {
        validate(temperatureKelvin, "temperature");
        validate(elapsedSeconds, "elapsed seconds");
        if (elapsedSeconds == 0.0) return temperatureKelvin;
        double sourceConductance = 0.0;
        double sourceTemperature = AMBIENT_TEMPERATURE_KELVIN;
        if (sourceTemperatureKelvin != null) {
            validate(sourceTemperatureKelvin, "source temperature");
            sourceConductance = SOURCE_CONDUCTANCE_WATTS_PER_KELVIN;
            sourceTemperature = sourceTemperatureKelvin;
        }
        double totalConductance = sourceConductance + AMBIENT_CONDUCTANCE_WATTS_PER_KELVIN;
        double equilibrium = (sourceConductance * sourceTemperature
            + AMBIENT_CONDUCTANCE_WATTS_PER_KELVIN * AMBIENT_TEMPERATURE_KELVIN) / totalConductance;
        ThermalBody body = new ThermalBody(HEAT_CAPACITY_JOULES_PER_KELVIN, temperatureKelvin);
        return ThermalTransfer.reservoir(body, equilibrium, totalConductance, elapsedSeconds)
            .getBody()
            .getTemperatureKelvin();
    }

    /** Rejects damaged or damaged-looking values instead of clamping them silently. */
    public static void validate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Apparatus " + name + " must be finite and nonnegative, was " + value);
        }
    }
}
