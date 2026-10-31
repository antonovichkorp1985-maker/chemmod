package io.github.antonovichkorp.chemmod.gas;

import com.mojang.serialization.Codec;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.gas.Atmosphere;
import io.github.antonovichkorp.chemmod.core.gas.BlastShockwaveModel;
import io.github.antonovichkorp.chemmod.core.gas.GasAtmosphere;
import io.github.antonovichkorp.chemmod.core.gas.GasCell;
import io.github.antonovichkorp.chemmod.core.gas.GasField;
import io.github.antonovichkorp.chemmod.core.gas.GasMolecularAmount;
import io.github.antonovichkorp.chemmod.core.gas.GasPhase;
import io.github.antonovichkorp.chemmod.core.gas.VolumetricGasCloud;
import io.github.antonovichkorp.chemmod.core.gas.VolumetricGasCloudManager;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEngine;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The world side of the gas field: one {@link GasField} per level, held as a level attachment and
 * saved with the world.
 *
 * <p>This is the piece that turns the core solver into something a player can act on. Gas released
 * into it stays in the world instead of vanishing with the block that held it; it diffuses, it
 * settles by density, and a cell that can see the sky ventilates to the atmosphere. Ventilation is
 * decided from the world rather than configured, because "is this open to the outside" is a fact
 * about the terrain and not a parameter.
 */
public final class WorldGasField {
    /** Bench cell of §5 of the plan, in metres; a Minecraft block is one metre. */
    public static final double CELL_SIZE_METRES = 4.0;
    public static final double AMBIENT_TEMPERATURE_KELVIN = 293.15;
    /** Effective reaction gate supplied only when the cell contains a real fire/lava block. */
    private static final double IGNITED_CELL_TEMPERATURE_KELVIN = 500.0;

    /** Air exchange rate per second for a cell that can see the sky. */
    public static final double OPEN_AIR_EXCHANGES_PER_SECOND = 0.25;

    public static final Codec<WorldGasField> CODEC =
        CompoundTag.CODEC.xmap(WorldGasField::fromTag, WorldGasField::toTag);

    private final GasField field = new GasField(CELL_SIZE_METRES);
    private final GasSpeciesProperties properties = new GasSpeciesProperties(AMBIENT_TEMPERATURE_KELVIN);

    /** Volumetric gas clouds (explosions, large releases). */
    private final VolumetricGasCloudManager cloudManager = new VolumetricGasCloudManager();

    /** Active explosive sources for blast wave computation. */
    private final java.util.List<BlastShockwaveModel.ExplosiveSource> blastSources = new java.util.ArrayList<>();

    /** Current simulation time (seconds). */
    private double simulationTimeSeconds = 0.0;

    /** Ambient gas API for barometric altitude correction and vacuum queries. */
    public final AmbientGasApi AMBIENT = new AmbientGasApi();

    public GasField field() {
        return field;
    }

    public GasCell cellOf(BlockPos pos) {
        return field.cellOf(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0);
    }

    /** Put gas into the world at a position - what a bursting tank or a leaking pipe does. */
    public void release(BlockPos pos, Map<String, Double> molesBySpecies) {
        GasCell cell = cellOf(pos);
        for (Map.Entry<String, Double> entry : molesBySpecies.entrySet()) {
            if (entry.getValue() > 0.0) field.addMoles(cell, entry.getKey(), entry.getValue());
        }
    }

    /** Structure-preserving release API for pipes, vessels and future molecular sources. */
    public void releaseMolecules(BlockPos pos, Map<Molecule, Double> molesByMolecule) {
        GasCell cell = cellOf(pos);
        for (Map.Entry<Molecule, Double> entry : molesByMolecule.entrySet()) {
            if (entry.getValue() > 0.0) field.addMoles(cell, entry.getKey(), entry.getValue());
        }
    }

    public double molesAt(BlockPos pos, String species) {
        return field.molesOf(cellOf(pos), species);
    }

    public double molesAt(BlockPos pos, Molecule molecule) {
        return field.molesOf(cellOf(pos), molecule);
    }

    /** Display formulas present at a position; chemistry must use molecularContentsIn instead. */
    public Set<String> speciesAt(BlockPos pos) {
        GasCell cell = cellOf(pos);
        Set<String> displaySpecies = new LinkedHashSet<>();
        for (String species : field.speciesIn(cell)) {
            if (!species.startsWith("mol:")) displaySpecies.add(species);
        }
        for (GasMolecularAmount amount : field.molecularContents(cell)) {
            displaySpecies.add(amount.getMolecule().formula());
        }
        return displaySpecies;
    }

    /** Contents of one cell by display formula, for rendering only; identity remains graph-keyed. */
    public Map<String, Double> contentsIn(GasCell cell) {
        Map<String, Double> contents = new LinkedHashMap<>();
        for (String species : field.speciesIn(cell)) {
            if (!species.startsWith("mol:")) contents.put(species, field.molesOf(cell, species));
        }
        for (GasMolecularAmount amount : field.molecularContents(cell)) {
            contents.merge(amount.getMolecule().formula(), amount.getMoles(), Double::sum);
        }
        return contents;
    }

    /** Graph-identified cell composition, used by chemistry and structural persistence. */
    public java.util.List<GasMolecularAmount> molecularContentsIn(GasCell cell) {
        return field.molecularContents(cell);
    }

    /**
     * Dry-air reference mixture plus the cell's actual gas inventory, for concentration readouts.
     * The background is calculated rather than stored in the sparse field.
     */
    public Map<String, Double> atmosphereCompositionAt(BlockPos pos) {
        return GasAtmosphere.moleFractions(
            contentsIn(cellOf(pos)),
            CELL_SIZE_METRES * CELL_SIZE_METRES * CELL_SIZE_METRES,
            AMBIENT_TEMPERATURE_KELVIN,
            GasAtmosphere.STANDARD_PRESSURE_PASCALS);
    }

    /** Total moles of every species at a position, for readouts and rendering. */
    public double totalMolesAt(BlockPos pos) {
        GasCell cell = cellOf(pos);
        double total = 0.0;
        for (String species : field.speciesIn(cell)) total += field.molesOf(cell, species);
        return total;
    }

    /**
     * Advance the field. A cell ventilates when its representative block is loaded and can see the
     * sky; unloaded cells are left alone rather than assumed open.
     */
    public void advance(Level level, double seconds) {
        simulationTimeSeconds += seconds;
        cloudManager.advanceAll(seconds, Atmosphere.EARTH, 0.0, 0.0);
        WorldCellFeatures cellFeatures = scanCellFeatures(level);
        field.advance(
            seconds,
            properties,
            cell -> {
                BlockPos pos = representativeBlock(cell);
                return level.isLoaded(pos) && level.canSeeSky(pos);
            },
            OPEN_AIR_EXCHANGES_PER_SECOND,
            (cell, species) -> cellFeatures.exhaustRatesPerSecond().getOrDefault(cell, 0.0D),
            GasField.DEFAULT_BUOYANT_DRIFT_METRES_PER_SECOND);

        field.applyEnvironmentalReactions(seconds, (cell, contents) -> {
            if (!cellFeatures.ignitedCells().contains(cell)) return null;

            double totalMoles = contents.stream().mapToDouble(GasMolecularAmount::getMoles).sum();
            if (totalMoles <= 0.0) return null;
            double pressureKilopascals = totalMoles * GasPhase.UNIVERSAL_GAS_CONSTANT *
                IGNITED_CELL_TEMPERATURE_KELVIN / (CELL_SIZE_METRES * CELL_SIZE_METRES * CELL_SIZE_METRES * 1000.0);
            return new ReactionEnvironment(IGNITED_CELL_TEMPERATURE_KELVIN, pressureKilopascals);
        });
    }

    /**
     * Scan the loaded portion of each occupied gas cell for environmental ignition and kinetic
     * ventilation. This deliberately never force-loads neighbouring chunks. The common gas-field
     * code sees only the small GasExhaustSource contract, not Create's optional kinetic API.
     */
    private WorldCellFeatures scanCellFeatures(Level level) {
        Set<GasCell> ignitedCells = new LinkedHashSet<>();
        Map<GasCell, Double> exhaustRatesPerSecond = new LinkedHashMap<>();
        int size = (int) CELL_SIZE_METRES;

        for (GasCell cell : field.occupiedCells()) {
            BlockPos corner = representativeBlock(cell);
            boolean ignited = false;
            double exhaustRate = 0.0D;
            for (int dx = 0; dx < size; dx++) {
                for (int dy = 0; dy < size; dy++) {
                    for (int dz = 0; dz < size; dz++) {
                        BlockPos pos = corner.offset(dx, dy, dz);
                        if (!level.isLoaded(pos)) continue;
                        var state = level.getBlockState(pos);
                        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)) {
                            ignited = true;
                        }
                        if (state.hasBlockEntity()
                            && level.getBlockEntity(pos) instanceof GasExhaustSource source) {
                            double rate = source.gasRemovalRatePerSecond();
                            if (Double.isFinite(rate) && rate > 0.0D) exhaustRate += rate;
                        }
                    }
                }
            }
            if (ignited) ignitedCells.add(cell);
            if (exhaustRate > 0.0D) exhaustRatesPerSecond.put(cell, exhaustRate);
        }
        return new WorldCellFeatures(ignitedCells, exhaustRatesPerSecond);
    }

    private record WorldCellFeatures(Set<GasCell> ignitedCells, Map<GasCell, Double> exhaustRatesPerSecond) {}

    /** The block at the minimum corner of a cell; cell coordinates are in cells, not blocks. */
    public static BlockPos representativeBlock(GasCell cell) {
        int scale = (int) CELL_SIZE_METRES;
        return new BlockPos(cell.getX() * scale, cell.getY() * scale, cell.getZ() * scale);
    }

    public Set<GasCell> occupiedCells() {
        return new LinkedHashSet<>(field.occupiedCells());
    }

    /**
     * Ignite a volumetric gas cloud at the given position with a spark.
     * Consumes the fuel gas and stoichiometric O2 from the cell, adds combustion products (CO2, H2O),
     * and registers an explosive source for blast wave computation.
     *
     * @param pos position of the spark/ignition
     * @param cloud the volumetric gas cloud to ignite
     * @return true if ignition occurred, false if no fuel or no oxidizer
     */
    public boolean igniteVolumetricCloud(BlockPos pos, VolumetricGasCloud cloud) {
        GasCell cell = cellOf(pos);
        double fuelMoles = cloud.getTotalMoles();
        if (fuelMoles <= 0.0) return false;

        // Get available O2 in the cell
        double oxygenMoles = field.molesOf(cell, "O2");
        if (oxygenMoles <= 0.0) return false;

        // Estimate stoichiometric O2 needed based on fuel formula
        // For simple hydrocarbons CxHy: CxHy + (x + y/4) O2 -> x CO2 + (y/2) H2O
        String formula = cloud.getSpeciesKey();
        Map<String, Integer> elements = parseFormula(formula);
        int c = elements.getOrDefault("C", 0);
        int h = elements.getOrDefault("H", 0);
        int o = elements.getOrDefault("O", 0);
        double o2NeededPerFuelMole = c + h / 4.0 - o / 2.0;
        if (o2NeededPerFuelMole < 0) o2NeededPerFuelMole = 0;

        double o2Needed = fuelMoles * o2NeededPerFuelMole;
        double o2Available = Math.min(oxygenMoles, o2Needed);
        double fuelConsumed = o2Available / o2NeededPerFuelMole;
        if (fuelConsumed <= 0.0) return false;

        // Consume fuel from cloud (reduce cloud totalMoles)
        // Note: VolumetricGasCloud is immutable, so we need to remove and re-add with updated moles
        cloudManager.removeCloud(cloud.getId());
        if (fuelMoles - fuelConsumed > 1e-6) {
            VolumetricGasCloud remaining = new VolumetricGasCloud(
                cloud.getId(), cloud.getCenterX(), cloud.getCenterY(), cloud.getCenterZ(),
                cloud.getRadiusMetres(), fuelMoles - fuelConsumed,
                cloud.getMolarMassKgPerMol(), cloud.getReleaseTemperatureKelvin(),
                cloud.getReleaseTimeSeconds(), cloud.getSpeciesKey()
            );
            cloudManager.addCloud(remaining);
        }

        // Consume O2 from cell
        field.setMoles(cell, "O2", oxygenMoles - o2Available);

        // Add combustion products: CO2 and H2O
        double co2Produced = fuelConsumed * c;
        double h2oProduced = fuelConsumed * h / 2.0;
        if (co2Produced > 0.0) field.addMoles(cell, "CO2", co2Produced);
        if (h2oProduced > 0.0) field.addMoles(cell, "H2O", h2oProduced);

        // Estimate heat of combustion (rough approximation for hydrocarbons)
        // ~47 MJ/kg for typical hydrocarbons, convert to J/mol
        double molarMassKg = cloud.getMolarMassKgPerMol();
        double heatOfCombustionJPerMol = 47e6 * molarMassKg; // J/mol
        double energyReleased = fuelConsumed * heatOfCombustionJPerMol;

        // Create explosive source for blast wave
        BlastShockwaveModel.ExplosiveSource source = BlastShockwaveModel.INSTANCE.fromFuelAir(
            fuelConsumed, heatOfCombustionJPerMol,
            Atmosphere.EARTH.pressurePascals(0.0),
            Atmosphere.EARTH.temperatureKelvin(0.0)
        );
        blastSources.add(source);

        return true;
    }

    /** Simple formula parser for stoichiometry estimation. */
    private Map<String, Integer> parseFormula(String formula) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int i = 0;
        while (i < formula.length()) {
            char c = formula.charAt(i);
            if (!Character.isUpperCase(c)) { i++; continue; }
            StringBuilder element = new StringBuilder();
            element.append(c);
            i++;
            if (i < formula.length() && Character.isLowerCase(formula.charAt(i))) {
                element.append(formula.charAt(i));
                i++;
            }
            int count = 0;
            while (i < formula.length() && Character.isDigit(formula.charAt(i))) {
                count = count * 10 + (formula.charAt(i) - '0');
                i++;
            }
            if (count == 0) count = 1;
            counts.merge(element.toString(), count, Integer::sum);
        }
        return counts;
    }

    // ------------------------------------------------------------ persistence

    public CompoundTag toTag() {
        CompoundTag root = new CompoundTag();
        ListTag cells = new ListTag();
        for (GasCell cell : field.occupiedCells()) {
            Set<String> species = field.speciesIn(cell);
            if (species.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", cell.getX());
            entry.putInt("y", cell.getY());
            entry.putInt("z", cell.getZ());

            // Legacy formula labels remain loadable as inert identities; new molecular entries
            // persist an explicit structural witness and its full canonical graph key.
            CompoundTag moles = new CompoundTag();
            for (String name : species) {
                if (!name.startsWith("mol:")) moles.putDouble(name, field.molesOf(cell, name));
            }
            entry.put("moles", moles);

            ListTag molecularMoles = new ListTag();
            for (GasMolecularAmount amount : field.molecularContents(cell)) {
                CompoundTag molecular = new CompoundTag();
                molecular.putString("key", GasField.identityKey(amount.getMolecule()));
                molecular.putString("witness", amount.getMolecule().structuralWitness());
                molecular.putDouble("moles", amount.getMoles());
                molecularMoles.add(molecular);
            }
            entry.put("molecular_moles", molecularMoles);
            cells.add(entry);
        }
        root.put("cells", cells);
        return root;
    }

    public static WorldGasField fromTag(CompoundTag tag) {
        WorldGasField gas = new WorldGasField();
        ListTag cells = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cells.size(); i++) {
            CompoundTag entry = cells.getCompound(i);
            GasCell cell = new GasCell(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            CompoundTag moles = entry.getCompound("moles");
            for (String name : moles.getAllKeys()) {
                double amount = moles.getDouble(name);
                if (amount > 0.0) gas.field.addMoles(cell, name, amount);
            }

            ListTag molecularMoles = entry.getList("molecular_moles", Tag.TAG_COMPOUND);
            for (int j = 0; j < molecularMoles.size(); j++) {
                CompoundTag molecular = molecularMoles.getCompound(j);
                Molecule graph = Molecule.Companion.fromSMILESlike(molecular.getString("witness"));
                String storedKey = molecular.getString("key");
                if (!GasField.identityKey(graph).equals(storedKey)) {
                    throw new IllegalArgumentException("Gas molecular witness does not match its canonical key");
                }
                double amount = molecular.getDouble("moles");
                if (amount > 0.0) gas.field.addMoles(cell, graph, amount);
            }
        }
        return gas;
    }

    /**
     * Ambient gas environment query API for Aeronautics Plus and other consumers.
     * Provides pressure, temperature, density, and mole fractions at a world position,
     * corrected for barometric altitude decrease and vacuum.
     */
    public final class AmbientGasApi {
        /** Standard surface pressure at sea level. */
        public static final double SURFACE_PRESSURE_PASCALS = 101325.0;
        /** Standard surface temperature at sea level. */
        public static final double SURFACE_TEMPERATURE_KELVIN = 288.15;
        /** Vacuum threshold: below this pressure the medium is effectively vacuum. */
        public static final double VACUUM_THRESHOLD_PASCALS = 1000.0;
        /** Ambient gas mass at altitude for a given cell volume (4m cube = 64 m3). */
        private static final double CELL_VOLUME_CUBIC_METRES = CELL_SIZE_METRES * CELL_SIZE_METRES * CELL_SIZE_METRES;

        private AmbientGasApi() {
        }

        /** Pressure in pascals at geometric altitude above surface, with vacuum below threshold. */
        public double pressurePascalsAt(double altitudeMetres) {
            double pressure = Atmosphere.EARTH.pressurePascals(altitudeMetres);
            return (pressure < VACUUM_THRESHOLD_PASCALS) ? 0.0 : pressure;
        }

        /** Temperature in kelvin at geometric altitude above surface. */
        public double temperatureKelvinAt(double altitudeMetres) {
            return Atmosphere.EARTH.temperatureKelvin(altitudeMetres);
        }

        /** Mass density in kg/m3 of ambient air at geometric altitude above surface. */
        public double densityKilogramsPerCubicMetreAt(double altitudeMetres) {
            return Atmosphere.EARTH.densityKilogramsPerCubicMetre(altitudeMetres);
        }

        /** Ambient gas mass in kilograms of air filling one gas cell at the given altitude. */
        public double ambientMassKilogramsPerCell(double altitudeMetres) {
            return densityKilogramsPerCubicMetreAt(altitudeMetres) * CELL_VOLUME_CUBIC_METRES;
        }

        /** Peak blast dynamic pressure at altitude from last explosion simulation step (Pa). */
        public double peakBlastPressurePascalsAt(BlockPos pos) {
            double maxP = 0.0;
            for (BlastShockwaveModel.ExplosiveSource source : blastSources) {
                // Compute distance to source (assuming center for now)
                double dist = Math.sqrt(pos.distSqr(new BlockPos(0, 0, 0))); // FIXME: source location
                maxP = Math.max(maxP, BlastShockwaveModel.INSTANCE.compute(source, dist).getPeakOverpressurePascals());
            }
            return maxP;
        }

        /** Peak blast wind velocity at a position from active clouds/blasts (m/s). */
        public double peakBlastWindMetersPerSecondAt(BlockPos pos) {
            return cloudManager.blastWindAt(pos.getX(), pos.getY(), pos.getZ(), blastSources, simulationTimeSeconds);
        }

        /** Total gas density including volatiles from all active gas clouds (kg/m3). */
        public double totalGasDensityAt(BlockPos pos) {
            double ambient = densityKilogramsPerCubicMetreAt(pos.getY());
            double cloud = cloudManager.totalDensityAt(pos.getX(), pos.getY(), pos.getZ());
            return ambient + cloud;
        }

        /** Whether the given altitude is in vacuum (below threshold pressure). */
        public boolean isVacuum(double altitudeMetres) {
            return pressurePascalsAt(altitudeMetres) <= 0.0;
        }
    }
}
