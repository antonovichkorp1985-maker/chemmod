package io.github.antonovichkorp.chemmod.gas;

import io.github.antonovichkorp.chemmod.core.gas.Atmosphere;

/**
 * The background air column, exposed for other mods.
 *
 * <p>This is the edge Aeronautics Plus asked for: density and pressure as a function of
 * <em>geometric</em> altitude - metres above the surface of a sphere - which is the axis flight
 * lives on and is not Minecraft Y. The methods are public and static and take and return only
 * primitives, so a consumer can reach them through {@code ModList} and reflection in the TFC style
 * without importing a single ChemMod class.
 *
 * <p>It answers a different question from the gas field, and the two must not be mixed. The field
 * is indexed by block and holds the <em>excess</em> somebody leaked; the column is indexed by
 * altitude and holds the planet's air. An empty cell of the field on a launch pad with nothing
 * wrong is not a vacuum - it is this, 1.225 kg/m3 and 101325 Pa.
 *
 * <p>Boundary as recorded in ADR-0004 and the test.10 release notes: combustion, gas release and
 * oxidation are ChemMod; the nozzle and thrust are the consumer's. What follows is the medium, not
 * the engine.
 */
public final class ChemAtmosphere {
    private static final Atmosphere ATMOSPHERE = Atmosphere.EARTH;

    /** Ambient pressure in pascals at a geometric altitude in metres. */
    public static double pressurePascalsAtAltitudeMetres(double altitudeMetres) {
        return ATMOSPHERE.pressurePascals(altitudeMetres);
    }

    /** Ambient mass density in kg/m3 at a geometric altitude in metres. */
    public static double densityKilogramsPerCubicMetreAtAltitudeMetres(double altitudeMetres) {
        return ATMOSPHERE.densityKilogramsPerCubicMetre(altitudeMetres);
    }

    /** Ambient temperature in kelvin at a geometric altitude in metres. */
    public static double temperatureKelvinAtAltitudeMetres(double altitudeMetres) {
        return ATMOSPHERE.temperatureKelvin(altitudeMetres);
    }

    /**
     * Geometric altitude converted to the geopotential altitude the standard atmosphere is defined
     * on. Exposed because a consumer doing its own integration needs the same axis we use.
     */
    public static double geopotentialMetres(double geometricAltitudeMetres) {
        return ATMOSPHERE.geopotentialMetres(geometricAltitudeMetres);
    }

    private ChemAtmosphere() {
    }
}
