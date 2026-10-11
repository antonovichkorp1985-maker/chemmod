package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.GasTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Stage-B world-adapter gametests for the gas tank.
 *
 * <p>The gametest harness only creates block entities for blocks that are part of the structure
 * template, so a block placed during a test has no live entity. We therefore prove two things
 * separately: the tank is a real, placeable world block, and the real {@link GasTankBlockEntity}
 * (driving the {@code ClosedVessel}) reproduces the physics the owner expects to see in-world —
 * an over-pressure that exceeds the wall, a stoichiometric burn that heats the gas, and a too-lean
 * mixture that refuses to burn. The full in-world burst/explosion is exercised by the owner.</p>
 */
@GameTestHolder(ChemMod.MOD_ID)
public class GasTankGameTests {
    private static final BlockPos TANK = new BlockPos(3, 1, 1);
    private static final double AMBIENT = 293.15;

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void theTankIsAPlaceableWorldBlock(GameTestHelper helper) {
        helper.setBlock(TANK, ChemBlocks.GAS_TANK.get().defaultBlockState());
        helper.assertTrue(helper.getBlockState(TANK).is(ChemBlocks.GAS_TANK.get()),
            "the gas tank block was not placed in the world");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void anOverpressureExceedsTheTankWall(GameTestHelper helper) {
        GasTankBlockEntity tank = tank(helper);
        tank.addMoles("N2", 20.0);
        helper.assertTrue(tank.pressurePascals() > GasTankBlockEntity.WALL_STRENGTH_PASCALS,
            "20 mol of gas in 0.05 m3 did not exceed the wall strength");
        helper.assertTrue(tank.isOverPressure(), "the tank did not register the over-pressure");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void aFlammableChargeInsideTheEnvelopeBurns(GameTestHelper helper) {
        GasTankBlockEntity tank = tank(helper);
        tank.addMoles("H2", 0.5);
        tank.addMoles("O2", 0.25);
        double released = tank.ignite();
        helper.assertTrue(released > 0.0, "a stoichiometric H2/O2 charge did not burn");
        helper.assertTrue(tank.vessel().molesOf("H2") < 1e-9, "hydrogen was not consumed");
        helper.assertTrue(Math.abs(tank.vessel().molesOf("H2O") - 0.5) < 1e-9, "water was not produced");
        helper.assertTrue(tank.vessel().getTemperatureKelvin() > AMBIENT + 100.0, "combustion did not heat the gas");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void aLeanChargeDoesNotBurn(GameTestHelper helper) {
        GasTankBlockEntity tank = tank(helper);
        tank.addMoles("H2", 0.05);
        tank.addMoles("O2", 0.95);
        helper.assertTrue(tank.ignite() == 0.0, "a too-lean mixture burned");
        helper.assertTrue(Math.abs(tank.vessel().molesOf("H2") - 0.05) < 1e-9, "lean hydrogen was consumed");
        helper.succeed();
    }

    private static GasTankBlockEntity tank(GameTestHelper helper) {
        helper.setBlock(TANK, ChemBlocks.GAS_TANK.get().defaultBlockState());
        return new GasTankBlockEntity(helper.absolutePos(TANK), helper.getBlockState(TANK));
    }
}
