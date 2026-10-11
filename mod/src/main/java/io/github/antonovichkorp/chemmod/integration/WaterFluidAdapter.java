package io.github.antonovichkorp.chemmod.integration;

import io.github.antonovichkorp.chemmod.content.WaterSampleProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

import java.util.Optional;

/** One-way optional adapter from an external fluid identity to ChemMod's canonical water profiles. */
public interface WaterFluidAdapter {
    Optional<WaterSampleProfile> classify(Level level, BlockPos position, FluidState fluid);
}
