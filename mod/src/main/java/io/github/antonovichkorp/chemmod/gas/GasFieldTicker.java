package io.github.antonovichkorp.chemmod.gas;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.core.gas.GasAppearance;
import io.github.antonovichkorp.chemmod.core.gas.GasCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.joml.Vector3f;

/**
 * Advances the world gas field on the server and shows it.
 *
 * <p>The plan diffuses every N ticks rather than every tick, so one step covers a whole second of
 * simulated time; the solver takes real seconds, so the step length is a physical quantity and not
 * a per-tick fudge. Clients never advance the field - they read what the server saved.
 *
 * <p>The cloud is drawn from its own composition, per ADR-0004 decision 7: the tint comes from the
 * species actually in the cell and its opacity from how much of the volume is coloured. A colourless
 * gas is drawn as nothing at all, because inventing a hue for hydrogen would be a lie about what is
 * in the room.
 */
@EventBusSubscriber(modid = ChemMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class GasFieldTicker {
    /** One field step per second of game time. */
    public static final int TICKS_PER_STEP = 20;

    /** Only draw cells this close to a player; the field itself is world-wide. */
    private static final double DRAW_RADIUS = 32.0;
    private static final int PARTICLES_PER_CELL = 14;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide() || level.getGameTime() % TICKS_PER_STEP != 0) {
            return;
        }
        WorldGasField gas = level.getData(ChemAttachments.GAS_FIELD.get());
        gas.advance(level, TICKS_PER_STEP / 20.0);
        if (level instanceof ServerLevel server) {
            draw(server, gas);
        }
    }

    private static void draw(ServerLevel level, WorldGasField gas) {
        for (GasCell cell : gas.occupiedCells()) {
            Integer tint = GasAppearance.INSTANCE.tintArgb(gas.contentsIn(cell));
            if (tint == null) {
                continue;
            }
            BlockPos corner = WorldGasField.representativeBlock(cell);
            double centreX = corner.getX() + WorldGasField.CELL_SIZE_METRES / 2.0;
            double centreY = corner.getY() + WorldGasField.CELL_SIZE_METRES / 2.0;
            double centreZ = corner.getZ() + WorldGasField.CELL_SIZE_METRES / 2.0;
            if (level.getNearestPlayer(centreX, centreY, centreZ, DRAW_RADIUS, false) == null) {
                continue;
            }
            int alpha = (tint >>> 24) & 0xFF;
            float opacity = alpha / 255.0F;
            Vector3f colour = new Vector3f(
                ((tint >> 16) & 0xFF) / 255.0F,
                ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F);
            level.sendParticles(
                new DustParticleOptions(colour, 0.8F + 2.2F * opacity),
                centreX, centreY, centreZ,
                Math.max(1, Math.round(PARTICLES_PER_CELL * opacity)),
                WorldGasField.CELL_SIZE_METRES / 2.0,
                WorldGasField.CELL_SIZE_METRES / 2.0,
                WorldGasField.CELL_SIZE_METRES / 2.0,
                0.0);
        }
    }

    private GasFieldTicker() {
    }
}
