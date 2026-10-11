package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.GasTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * The world adapter for the sealed-vessel physics: a tank whose pressure is real bursts when the
 * wall cannot carry it, and a flammable charge inside its envelope burns (the Hindenburg case)
 * while a lean one does not.
 */
@GameTestHolder(ChemMod.MOD_ID)
public class GasTankGameTests {
    private static final BlockPos TANK = new BlockPos(3, 1, 1);
    private static final double AMBIENT = 293.15;

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void overpressureBurstsTheTank(GameTestHelper helper) {
        GasTankBlockEntity tank = place(helper);
        tank.addMoles("N2", 20.0);   // ~970 kPa in a 50 L tank against a 500 kPa wall
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(TANK)).isAir(),
            "an over-pressured tank did not burst");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void aFlammableChargeInsideTheEnvelopeBurns(GameTestHelper helper) {
        GasTankBlockEntity tank = place(helper);
        tank.addMoles("H2", 0.5);
        tank.addMoles("O2", 0.25);
        double released = tank.ignite();
        helper.assertTrue(released > 0.0, "a stoichiometric H2/O2 charge did not burn");
        helper.assertTrue(tank.vessel().molesOf("H2") < 1e-9, "hydrogen was not consumed");
        helper.assertTrue(Math.abs(tank.vessel().molesOf("H2O") - 0.5) < 1e-9, "water was not produced");
        helper.assertTrue(tank.vessel().temperatureKelvin() > AMBIENT + 100.0, "combustion did not heat the gas");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void aLeanChargeDoesNotBurn(GameTestHelper helper) {
        GasTankBlockEntity tank = place(helper);
        tank.addMoles("H2", 0.05);
        tank.addMoles("O2", 0.95);
        double released = tank.ignite();
        helper.assertTrue(released == 0.0, "a too-lean mixture burned");
        helper.assertTrue(Math.abs(tank.vessel().molesOf("H2") - 0.05) < 1e-9, "lean hydrogen was consumed");
        helper.succeed();
    }

    private static GasTankBlockEntity place(GameTestHelper helper) {
        helper.getLevel().setBlock(helper.absolutePos(TANK),
            ChemBlocks.GAS_TANK.get().defaultBlockState(), 3);
        return (GasTankBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(TANK));
    }
}
