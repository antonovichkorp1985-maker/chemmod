package io.github.antonovichkorp.chemmod.gas;

import com.mojang.serialization.Codec;
import io.github.antonovichkorp.chemmod.core.gas.GasCell;
import io.github.antonovichkorp.chemmod.core.gas.GasField;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

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

    public double molesAt(BlockPos pos, String species) {
        return field.molesOf(cellOf(pos), species);
    }

    /** Species present at a position - what a sensor block or the renderer reads. */
    public Set<String> speciesAt(BlockPos pos) {
        return field.speciesIn(cellOf(pos));
    }

    /** Contents of one cell by species, in moles - what the renderer and the toxicology read. */
    public Map<String, Double> contentsIn(GasCell cell) {
        Map<String, Double> contents = new LinkedHashMap<>();
        for (String species : field.speciesIn(cell)) contents.put(species, field.molesOf(cell, species));
        return contents;
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
            CompoundTag moles = new CompoundTag();
            for (String name : species) moles.putDouble(name, field.molesOf(cell, name));
            entry.put("moles", moles);
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
        }
        return gas;
    }
}
