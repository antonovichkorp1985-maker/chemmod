package io.github.antonovichkorp.chemmod.gas;

import com.mojang.serialization.Codec;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.gas.GasCell;
import io.github.antonovichkorp.chemmod.core.gas.GasField;
import io.github.antonovichkorp.chemmod.core.gas.GasMolecularAmount;
import io.github.antonovichkorp.chemmod.core.gas.GasPhase;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment;
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
        field.advance(
            seconds,
            properties,
            cell -> {
                BlockPos pos = representativeBlock(cell);
                return level.isLoaded(pos) && level.canSeeSky(pos);
            },
            OPEN_AIR_EXCHANGES_PER_SECOND,
            (cell, species) -> 0.0,
            GasField.DEFAULT_BUOYANT_DRIFT_METRES_PER_SECOND);

        field.applyEnvironmentalReactions(seconds, (cell, contents) -> {
            if (!hasIgnitionSource(level, cell)) return null;

            double totalMoles = contents.stream().mapToDouble(GasMolecularAmount::getMoles).sum();
            if (totalMoles <= 0.0) return null;
            double pressureKilopascals = totalMoles * GasPhase.UNIVERSAL_GAS_CONSTANT *
                IGNITED_CELL_TEMPERATURE_KELVIN / (CELL_SIZE_METRES * CELL_SIZE_METRES * CELL_SIZE_METRES * 1000.0);
            return new ReactionEnvironment(IGNITED_CELL_TEMPERATURE_KELVIN, pressureKilopascals);
        });
    }

    /**
     * A cell reacts when any loaded block in its 4 m cube is an ignition source. Checking only the
     * minimum-corner representative made ordinary player-placed fires elsewhere in the same cell
     * chemically invisible. Stop at the first source and never force-load a neighbouring chunk.
     */
    private static boolean hasIgnitionSource(Level level, GasCell cell) {
        BlockPos corner = representativeBlock(cell);
        int size = (int) CELL_SIZE_METRES;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    BlockPos pos = corner.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) continue;
                    var state = level.getBlockState(pos);
                    if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The block at the minimum corner of a cell; cell coordinates are in cells, not blocks. */
    public static BlockPos representativeBlock(GasCell cell) {
        int scale = (int) CELL_SIZE_METRES;
        return new BlockPos(cell.getX() * scale, cell.getY() * scale, cell.getZ() * scale);
    }

    public Set<GasCell> occupiedCells() {
        return new LinkedHashSet<>(field.occupiedCells());
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
}
