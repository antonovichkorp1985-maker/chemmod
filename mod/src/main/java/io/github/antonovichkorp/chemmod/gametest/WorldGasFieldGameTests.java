package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.gas.GasCell;
import io.github.antonovichkorp.chemmod.core.gas.GasMolecularAmount;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.GasExhaustBlock;
import io.github.antonovichkorp.chemmod.gas.ChemAttachments;
import io.github.antonovichkorp.chemmod.gas.GasExposureEffects;
import io.github.antonovichkorp.chemmod.gas.WorldGasField;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EntityType;
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
    public static void oxygenDeficiencyAppliesAWarningEffect(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
        BlockPos pos = helper.absolutePos(HERE);
        var entity = helper.spawn(EntityType.ZOMBIE, HERE);

        helper.assertTrue(!GasExposureEffects.applyOxygenDeficiency(entity, gas, pos),
            "normal dry air was classified as oxygen deficient");
        helper.assertTrue(!entity.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),
            "normal dry air applied the oxygen-deficiency warning");

        // One 4 m cell holds about 2,660 mol of dry air at 293.15 K and 1 atm;
        // 400 mol of nitrogen lowers the calculated oxygen fraction below 19.5%.
        gas.release(pos, Map.of("N2", 400.0));
        double oxygenFraction = gas.atmosphereCompositionAt(pos).getOrDefault("O2", 0.0);
        helper.assertTrue(oxygenFraction < 0.195,
            "the test mixture did not cross the oxygen-deficient-atmosphere boundary");
        helper.assertTrue(GasExposureEffects.applyOxygenDeficiency(entity, gas, pos),
            "oxygen-deficient air did not activate the living-entity warning");
        helper.assertTrue(entity.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),
            "oxygen-deficient air did not apply the short Weakness warning effect");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void poweredExhaustRemovesGasFromItsCoarseWorldCell(GameTestHelper helper) {
        WorldGasField gas = helper.getLevel().getData(ChemAttachments.GAS_FIELD.get());
        BlockPos pos = helper.absolutePos(HERE);
        gas.release(pos, Map.of("CO2", 100.0));
        GasCell cell = gas.cellOf(pos);
        BlockPos corner = WorldGasField.representativeBlock(cell);
        helper.getLevel().setBlock(corner.above(), Blocks.STONE.defaultBlockState(), 3);
        BlockPos ventPos = corner.offset(1, 0, 1);
        helper.getLevel().setBlock(ventPos, ChemBlocks.GAS_EXHAUST.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(ventPos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().getBlockState(ventPos).getValue(GasExhaustBlock.POWERED),
            "the exhaust did not detect adjacent redstone power");
        helper.assertTrue(!helper.getLevel().canSeeSky(corner),
            "the exhaust test cell is still open to the sky, so powered extraction is ambiguous");

        double before = gas.field().totalMoles("CO2");
        gas.advance(helper.getLevel(), 1.0);
        double after = gas.field().totalMoles("CO2");

        helper.assertTrue(after < before,
            "the powered exhaust did not remove gas from the world-field boundary");
        helper.assertTrue(after > 0.0,
            "one coarse-cell exhaust step removed the entire gas inventory");
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

        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = WorldGasField.CODEC.encodeStart(ops, gas).getOrThrow();
        WorldGasField restored = WorldGasField.CODEC.parse(ops, encoded).getOrThrow();
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
