package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.gas.GasCell;
import io.github.antonovichkorp.chemmod.core.gas.GasMolecularAmount;
import io.github.antonovichkorp.chemmod.gas.ChemAttachments;
import io.github.antonovichkorp.chemmod.gas.WorldGasField;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
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
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
        gas.release(helper.absolutePos(HERE), Map.of("CO2", 2.0));

        helper.assertTrue(gas.molesAt(helper.absolutePos(HERE), "CO2") > 0.0,
            "gas released into the level was not there to be read back");
        helper.assertTrue(gas.speciesAt(helper.absolutePos(HERE)).contains("CO2"),
            "the released species was not reported at its position");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void heavyGasReleasedIntoTheWorldSettles(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
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
    public static void molecularChlorineLeakSettlesAndKeepsItsCompositionTint(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
        BlockPos pos = helper.absolutePos(HERE.above(8));
        Molecule chlorine = Molecule.Companion.fromSMILESlike("ClCl");
        gas.releaseMolecules(pos, Map.of(chlorine, 4.0));
        GasCell cell = gas.cellOf(pos);
        GasCell below = new GasCell(cell.getX(), cell.getY() - 1, cell.getZ());

        gas.advance(helper.getLevel(), 1.0);

        helper.assertTrue(gas.molecularContentsIn(below).stream().anyMatch(amount ->
                amount.getMolecule().canonicalKey().equals(chlorine.canonicalKey())),
            "graph-identified chlorine did not settle into the lower gas cell");
        Integer tint = io.github.antonovichkorp.chemmod.core.gas.GasAppearance.INSTANCE
            .tintArgb(gas.contentsIn(cell));
        helper.assertTrue(tint != null, "the chlorine composition did not retain its visible gas tint");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void molecularGasKeepsIsomerIdentityAcrossWorldPersistence(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
        BlockPos pos = helper.absolutePos(HERE.above(12));
        Molecule ethanol = Molecule.Companion.fromSMILESlike("CCO");
        Molecule dimethylEther = Molecule.Companion.fromSMILESlike("COC");
        gas.releaseMolecules(pos, Map.of(ethanol, 0.25, dimethylEther, 0.75));
        GasCell cell = gas.cellOf(pos);
        var before = gas.molecularContentsIn(cell);

        WorldGasField restored = WorldGasField.fromTag(gas.toTag());
        var after = restored.molecularContentsIn(cell);

        helper.assertTrue(ethanol.formula().equals(dimethylEther.formula()),
            "the test is not using same-formula structural isomers");
        helper.assertTrue(before.size() == 2 && after.size() == 2,
            "molecular identity collapsed two structural isomers");
        helper.assertTrue(after.stream().anyMatch(amount ->
                amount.getMolecule().canonicalKey().equals(ethanol.canonicalKey()) && Math.abs(amount.getMoles() - 0.25) < 1e-12),
            "ethanol graph or its amount changed across world persistence");
        helper.assertTrue(after.stream().anyMatch(amount ->
                amount.getMolecule().canonicalKey().equals(dimethylEther.canonicalKey()) && Math.abs(amount.getMoles() - 0.75) < 1e-12),
            "dimethyl ether graph or its amount changed across world persistence");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void aWorldFireRunsTheOrdinaryHydrogenCombustionRule(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
        BlockPos pos = helper.absolutePos(HERE.above(12));
        Molecule hydrogen = Molecule.Companion.fromSMILESlike("[H][H]");
        Molecule oxygen = Molecule.Companion.fromSMILESlike("O=O");
        gas.releaseMolecules(pos, Map.of(hydrogen, 2.0, oxygen, 1.0));
        GasCell cell = gas.cellOf(pos);
        BlockPos ignition = WorldGasField.representativeBlock(cell).offset(1, 0, 1);
        helper.getLevel().setBlock(ignition.below(), Blocks.NETHERRACK.defaultBlockState(), 3);
        helper.getLevel().setBlock(ignition, Blocks.FIRE.defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockState(ignition).is(Blocks.FIRE),
            "the GameTest did not retain the supported in-cell ignition fire");

        gas.advance(helper.getLevel(), 1.0);
        var contents = gas.molecularContentsIn(cell);

        helper.assertTrue(contents.stream().anyMatch(amount -> amount.getMolecule().formula().equals("H2O")
                && amount.getMoles() > 0.0),
            "world ignition did not execute the data-defined hydrogen + oxygen reaction; contents: " +
                contents.stream().map(amount -> amount.getMolecule().formula() + "=" + amount.getMoles()).toList());
        helper.assertTrue(contents.stream().allMatch(amount -> !amount.getMolecule().formula().equals("H2O")
                || amount.getMolecule().validate().isEmpty()),
            "the environmental reaction created an invalid molecular graph");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void aFireInAnotherGasCellDoesNotIgniteThisCell(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
        BlockPos pos = helper.absolutePos(HERE.above(12));
        Molecule hydrogen = Molecule.Companion.fromSMILESlike("[H][H]");
        Molecule oxygen = Molecule.Companion.fromSMILESlike("O=O");
        gas.releaseMolecules(pos, Map.of(hydrogen, 2.0, oxygen, 1.0));
        GasCell cell = gas.cellOf(pos);
        BlockPos ignition = WorldGasField.representativeBlock(cell).offset(8, 0, 0);
        helper.getLevel().setBlock(ignition.below(), Blocks.NETHERRACK.defaultBlockState(), 3);
        helper.getLevel().setBlock(ignition, Blocks.FIRE.defaultBlockState(), 3);

        gas.advance(helper.getLevel(), 1.0);

        boolean waterProduced = gas.occupiedCells().stream()
            .flatMap(occupied -> gas.molecularContentsIn(occupied).stream())
            .anyMatch(amount -> amount.getMolecule().formula().equals("H2O"));
        helper.assertTrue(!waterProduced,
            "ignition outside the hydrogen/oxygen cell triggered its reaction");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void hydrogenAndOxygenDoNotReactInTheWorldWithoutAnIgnitionSource(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
        BlockPos pos = helper.absolutePos(HERE.above(12));
        Molecule hydrogen = Molecule.Companion.fromSMILESlike("[H][H]");
        Molecule oxygen = Molecule.Companion.fromSMILESlike("O=O");
        gas.releaseMolecules(pos, Map.of(hydrogen, 2.0, oxygen, 1.0));

        gas.advance(helper.getLevel(), 1.0);

        boolean waterProduced = gas.occupiedCells().stream()
            .flatMap(cell -> gas.molecularContentsIn(cell).stream())
            .anyMatch(amount -> amount.getMolecule().formula().equals("H2O"));
        helper.assertTrue(!waterProduced,
            "ambient hydrogen and oxygen reacted without a fire, soul fire, or lava block in their cell");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void theWorldGasFieldSurvivesASaveRoundTrip(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
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
