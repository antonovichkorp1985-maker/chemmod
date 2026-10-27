package io.github.antonovichkorp.chemmod.integration.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.antonovichkorp.chemmod.gas.GasExhaustSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Supplies the world gas field with a ventilation rate derived from Create shaft speed. Volumetric
 * throughput is linear in rotor speed, as for a fixed-displacement fan. The reference coefficient
 * is a gameplay-scale provisional value, not a measured fan curve.
 */
public final class KineticGasExhaustBlockEntity extends KineticBlockEntity implements GasExhaustSource {
    /** Reference exchange rate at 16 RPM; see docs/testing/m4-graph-gas-reactions.md. */
    public static final double REFERENCE_EXCHANGE_RATE_PER_SECOND = 0.02D;
    public static final double REFERENCE_SPEED_RPM = 16.0D;
    private static final double MAX_EXCHANGE_RATE_PER_SECOND = 0.25D;

    public KineticGasExhaustBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCompatBlockEntities.KINETIC_GAS_EXHAUST.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    protected boolean isNoisy() {
        return false;
    }

    @Override
    public double gasRemovalRatePerSecond() {
        if (level == null || level.isClientSide() || !isSpeedRequirementFulfilled()) return 0.0D;
        double rate = REFERENCE_EXCHANGE_RATE_PER_SECOND * Math.abs(getSpeed()) / REFERENCE_SPEED_RPM;
        return Math.min(MAX_EXCHANGE_RATE_PER_SECOND, rate);
    }
}
