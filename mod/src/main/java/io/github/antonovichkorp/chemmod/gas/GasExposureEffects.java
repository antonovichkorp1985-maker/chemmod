package io.github.antonovichkorp.chemmod.gas;

import io.github.antonovichkorp.chemmod.core.gas.GasToxicity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Maps the one currently supported acute atmosphere hazard to a vanilla player effect.
 *
 * <p>This deliberately handles oxygen displacement only. The 19.5% oxygen boundary is the
 * established oxygen-deficient-atmosphere criterion already used by {@link GasToxicity}; this
 * short Weakness effect is a gameplay warning for that condition, not a toxic-dose calculation.
 * Other mechanisms remain informational until their exposure limits and time basis are modeled.
 */
public final class GasExposureEffects {
    /** Reapply once per gas-field step; the short duration clears promptly after leaving the cell. */
    private static final int EFFECT_DURATION_TICKS = 40;

    public static boolean applyOxygenDeficiency(LivingEntity entity, WorldGasField gas, BlockPos pos) {
        double oxygenFraction = gas.atmosphereCompositionAt(pos).getOrDefault("O2", 0.0);
        if (!Double.isFinite(oxygenFraction) || oxygenFraction >= GasToxicity.SAFE_OXYGEN_FRACTION) {
            return false;
        }
        entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_DURATION_TICKS, 0));
        return true;
    }

    private GasExposureEffects() {
    }
}
