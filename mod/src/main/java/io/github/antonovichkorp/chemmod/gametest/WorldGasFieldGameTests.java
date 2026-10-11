package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.core.gas.GasCell;
import io.github.antonovichkorp.chemmod.gas.ChemAttachments;
import io.github.antonovichkorp.chemmod.gas.WorldGasField;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.Map;

/**
 * World-adapter gametests for the gas field.
 *
 * <p>These prove the part that the core solver cannot: gas released into a level actually stays in
 * that level, it moves under the world's own physics, and it survives the world being saved. The
 * full in-world behaviour - filling a room with chlorine, watching it settle into a pit - is the
 * owner's acceptance run on the real pack, as ADR-0004 requires for world-side changes.
 */
@GameTestHolder(ChemMod.MOD_ID)
public class WorldGasFieldGameTests {
    private static final BlockPos HERE = new BlockPos(3, 1, 1);

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void gasReleasedIntoTheWorldIsInTheWorld(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD);
        gas.release(helper.absolutePos(HERE), Map.of("CO2", 2.0));

        helper.assertTrue(gas.molesAt(helper.absolutePos(HERE), "CO2") > 0.0,
            "gas released into the level was not there to be read back");
        helper.assertTrue(gas.speciesAt(helper.absolutePos(HERE)).contains("CO2"),
            "the released species was not reported at its position");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void heavyGasReleasedIntoTheWorldSettles(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD);
        BlockPos pos = helper.absolutePos(HERE.above(8));
        gas.release(pos, Map.of("CO2", 4.0));

        GasCell cell = gas.cellOf(pos);
        GasCell below = new GasCell(cell.getX(), cell.getY() - 1, cell.getZ());
        helper.assertTrue(gas.field().molesOf(below, "CO2") == 0.0,
            "the cell below started out with carbon dioxide, so the test proves nothing");

        gas.advance(helper.getLevel(), 1.0);

        helper.assertTrue(gas.field().molesOf(below, "CO2") > 0.0,
            "carbon dioxide, which is denser than air, did not settle into the cell below");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void theWorldGasFieldSurvivesASaveRoundTrip(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD);
        BlockPos pos = helper.absolutePos(HERE.above(16));
        gas.release(pos, Map.of("SF6", 1.5));
        double before = gas.molesAt(pos, "SF6");

        WorldGasField restored = WorldGasField.fromTag(gas.toTag());

        helper.assertTrue(before > 0.0, "the release did not register, so the test proves nothing");
        helper.assertTrue(Math.abs(restored.molesAt(pos, "SF6") - before) < 1e-9,
            "saving and loading the world changed how much gas is in it");
        helper.succeed();
    }
}
