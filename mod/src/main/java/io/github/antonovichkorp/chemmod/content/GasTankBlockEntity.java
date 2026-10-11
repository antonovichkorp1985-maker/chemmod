package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.gas.ClosedVessel;
import io.github.antonovichkorp.chemmod.gas.ChemAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A rigid sealed gas tank in the world. The physics lives in the chemistry core's
 * {@link ClosedVessel}; this entity only stores the gas, exposes fill/ignite, and turns an
 * over-pressure into a physical event: the wall fails, the tank drops, and a flammable contents
 * bursts with an explosion (the Hindenburg case).
 */
public class GasTankBlockEntity extends BlockEntity {
    public static final double VOLUME_CUBIC_METERS = 0.05;
    public static final double WALL_STRENGTH_PASCALS = 500_000.0;

    private final ClosedVessel vessel = new ClosedVessel(VOLUME_CUBIC_METERS);

    public GasTankBlockEntity(BlockPos pos, BlockState state) {
        super(ChemBlockEntities.GAS_TANK.get(), pos, state);
    }

    public ClosedVessel vessel() {
        return vessel;
    }

    /** Whether the gas is currently pressing harder than the tank wall can take. */
    public boolean isOverPressure() {
        return vessel.bursts(WALL_STRENGTH_PASCALS);
    }

    public double pressurePascals() {
        return vessel.pressurePascals();
    }

    public void addMoles(String species, double amount) {
        vessel.addMoles(species, amount);
        maybeBurst();
        setChanged();
    }

    public double ignite() {
        double released = vessel.ignite();
        maybeBurst();
        setChanged();
        return released;
    }

    private void maybeBurst() {
        if (level == null || level.isClientSide() || !vessel.bursts(WALL_STRENGTH_PASCALS)) return;
        boolean fiery = vessel.species().stream()
            .anyMatch(s -> s.equals("H2") || s.equals("CH4") || s.equals("CO"));
        // The wall fails, so the contents stop being sealed and become world gas: they go into the
        // level's gas field and diffuse, settle and ventilate from there instead of disappearing
        // together with the block.
        Map<String, Double> contents = new LinkedHashMap<>();
        for (String species : vessel.species()) contents.put(species, vessel.molesOf(species));
        level.getData(ChemAttachments.GAS_FIELD.get()).release(worldPosition, contents);
        Block.popResource(level, worldPosition, new ItemStack(ChemItems.GAS_TANK.get()));
        level.removeBlock(worldPosition, false);
        if (fiery) {
            level.explode(null, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5, 2.5F, Level.ExplosionInteraction.BLOCK);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag moles = new CompoundTag();
        for (String species : vessel.species()) moles.putDouble(species, vessel.molesOf(species));
        tag.put("moles", moles);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        CompoundTag moles = tag.getCompound("moles");
        for (String species : moles.getAllKeys()) vessel.addMoles(species, moles.getDouble(species));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
