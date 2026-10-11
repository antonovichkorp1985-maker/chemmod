package io.github.antonovichkorp.chemmod.gas;

import io.github.antonovichkorp.chemmod.ChemMod;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Advances the world gas field on the server.
 *
 * <p>The plan diffuses every N ticks rather than every tick, so one step covers a whole second of
 * simulated time; the solver takes real seconds, so the step length is a physical quantity and not
 * a per-tick fudge. Clients never advance the field - they read what the server saved.
 */
@EventBusSubscriber(modid = ChemMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class GasFieldTicker {
    /** One field step per second of game time. */
    public static final int TICKS_PER_STEP = 20;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide() || level.getGameTime() % TICKS_PER_STEP != 0) {
            return;
        }
        level.getData(ChemAttachments.GAS_FIELD.get()).advance(level, TICKS_PER_STEP / 20.0);
    }

    private GasFieldTicker() {
    }
}
