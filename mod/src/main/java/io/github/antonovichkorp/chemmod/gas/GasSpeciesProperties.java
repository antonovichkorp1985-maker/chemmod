package io.github.antonovichkorp.chemmod.gas;

import io.github.antonovichkorp.chemmod.core.gas.GasFieldProperties;
import io.github.antonovichkorp.chemmod.core.gas.GasTransport;
import io.github.antonovichkorp.chemmod.core.model.ElementTable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transport data for the world gas field.
 *
 * <p>Nothing here is a per-substance row. The molar mass is summed from the element table over the
 * species formula, so a species the table has never heard of fails loudly rather than getting a
 * guessed mass, and the diffusion coefficient comes from Chapman–Enskog over Lennard-Jones
 * parameters. Air is represented by nitrogen for the diffusion pair, which is the 78% constituent;
 * where a species has no Lennard-Jones entry the coefficient is null and the field honestly does
 * not diffuse it instead of reading a missing value as a small one.
 */
public final class GasSpeciesProperties implements GasFieldProperties {
    /** Nitrogen stands in for air as the diffusion partner. */
    private static final String AIR_PROXY = "N2";
    private static final double AIR_PROXY_MOLAR_MASS = 28.014;
    private static final double STANDARD_PRESSURE_PASCALS = 101_325.0;

    private final double temperatureKelvin;
    private final ElementTable elements = ElementTable.defaultTable();
    private final Map<String, Double> molarMasses = new LinkedHashMap<>();

    public GasSpeciesProperties(double temperatureKelvin) {
        this.temperatureKelvin = temperatureKelvin;
    }

    @Override
    public double molarMassGramsPerMole(String species) {
        Double cached = molarMasses.get(species);
        if (cached != null) return cached;
        double mass = 0.0;
        for (Map.Entry<String, Integer> entry : parse(species).entrySet()) {
            mass += elements.require(entry.getKey()).getAtomicMass() * entry.getValue();
        }
        molarMasses.put(species, mass);
        return mass;
    }

    @Override
    public Double diffusionSquareMetersPerSecond(String species) {
        double molarMass;
        try {
            molarMass = molarMassGramsPerMole(species);
        } catch (RuntimeException unknownElementOrFormula) {
            return null;
        }
        return GasTransport.INSTANCE.binaryDiffusionSquareMetersPerSecond(
            species, AIR_PROXY, molarMass, AIR_PROXY_MOLAR_MASS,
            temperatureKelvin, STANDARD_PRESSURE_PASCALS);
    }

    /** Splits a plain formula such as {@code Cl2} or {@code H2O} into element counts. */
    static Map<String, Integer> parse(String formula) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int index = 0;
        while (index < formula.length()) {
            char first = formula.charAt(index);
            if (!Character.isUpperCase(first)) {
                throw new IllegalArgumentException("Not a formula: " + formula);
            }
            StringBuilder symbol = new StringBuilder().append(first);
            index++;
            while (index < formula.length() && Character.isLowerCase(formula.charAt(index))) {
                symbol.append(formula.charAt(index));
                index++;
            }
            int start = index;
            while (index < formula.length() && Character.isDigit(formula.charAt(index))) index++;
            int count = start == index ? 1 : Integer.parseInt(formula.substring(start, index));
            if (count < 1) throw new IllegalArgumentException("Not a formula: " + formula);
            counts.merge(symbol.toString(), count, Integer::sum);
        }
        if (counts.isEmpty()) throw new IllegalArgumentException("Not a formula: " + formula);
        return counts;
    }
}
