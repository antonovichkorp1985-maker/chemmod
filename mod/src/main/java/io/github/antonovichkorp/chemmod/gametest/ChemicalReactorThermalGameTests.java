package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ApparatusThermalModel;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlockEntity;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import io.github.antonovichkorp.chemmod.content.VialContentsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world checks for apparatus thermal inertia: the vessel is a body with
 * heat capacity, not a mirror of whatever block happens to be lit below it.
 */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalReactorThermalGameTests {
    private static final BlockPos HEAT_SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos REACTOR = new BlockPos(1, 2, 1);
    private static final long VIAL_MICROMOLES = 1_000_000L;
    private static final double AMBIENT = ApparatusThermalModel.AMBIENT_TEMPERATURE_KELVIN;
    private static final double BLAST_FURNACE_SOURCE = 900.0;
    private static final double FURNACE_SOURCE = 650.0;
    /** Minimum temperature of chemmod:alcohol_dehydrogenation, the bundled ethanol route. */
    private static final double ETHANOL_RULE_MINIMUM = 550.0;

    private ChemicalReactorThermalGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void coldReactorWarmsTowardTheSourceInsteadOfAdoptingIt(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, lit(Blocks.BLAST_FURNACE), false);
        helper.assertTrue(reactor.temperatureKelvin() == AMBIENT, "a freshly placed reactor was not at room temperature");

        helper.runAfterDelay(21, () -> {
            double temperature = reactor.temperatureKelvin();
            double plateau = ApparatusThermalModel.equilibriumTemperatureKelvin(BLAST_FURNACE_SOURCE);
            helper.assertTrue(temperature > AMBIENT + 300.0, "lit source did not warm the vessel: " + temperature);
            helper.assertTrue(temperature < BLAST_FURNACE_SOURCE - 5.0,
                "vessel adopted the source temperature instantly: " + temperature);
            helper.assertTrue(temperature <= plateau + 1e-9, "vessel overshot its own equilibrium: " + temperature);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 240)
    public static void reactorCoolsTowardTheRoomAfterTheSourceIsRemoved(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, lit(Blocks.BLAST_FURNACE), false);
        double[] hot = new double[1];
        double[] cooler = new double[1];

        helper.runAfterDelay(60, () -> {
            hot[0] = reactor.temperatureKelvin();
            helper.assertTrue(hot[0] > 850.0, "blast furnace did not heat the vessel: " + hot[0]);
            removeHeat(helper);
        });
        helper.runAfterDelay(100, () -> {
            cooler[0] = reactor.temperatureKelvin();
            helper.assertTrue(cooler[0] < hot[0], "removing the source did not cool the vessel");
            helper.assertTrue(cooler[0] > AMBIENT, "vessel cooled below room temperature: " + cooler[0]);
        });
        helper.runAfterDelay(200, () -> {
            double colder = reactor.temperatureKelvin();
            helper.assertTrue(colder < cooler[0], "vessel stopped cooling without a heat sink");
            helper.assertTrue(colder > AMBIENT, "vessel undershot room temperature: " + colder);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 260)
    public static void aStrongerSourceReachesAHigherPlateau(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, lit(Blocks.FURNACE), false);
        double[] furnacePlateau = new double[1];

        helper.runAfterDelay(100, () -> {
            furnacePlateau[0] = reactor.temperatureKelvin();
            double expected = ApparatusThermalModel.equilibriumTemperatureKelvin(FURNACE_SOURCE);
            helper.assertTrue(Math.abs(furnacePlateau[0] - expected) < 1.0,
                "regular furnace plateau was " + furnacePlateau[0] + ", expected about " + expected);
            helper.getLevel().setBlock(helper.absolutePos(HEAT_SOURCE), lit(Blocks.BLAST_FURNACE), 3);
        });
        helper.runAfterDelay(200, () -> {
            double blastPlateau = reactor.temperatureKelvin();
            helper.assertTrue(blastPlateau > furnacePlateau[0] + 200.0,
                "stronger source did not reach a higher plateau: " + blastPlateau);
            helper.assertTrue(blastPlateau < BLAST_FURNACE_SOURCE,
                "vessel exceeded its source temperature: " + blastPlateau);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void bodyTemperatureSurvivesSaveReloadAndRejectsDamagedValues(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, lit(Blocks.BLAST_FURNACE), false);

        helper.runAfterDelay(25, () -> {
            var level = helper.getLevel();
            double temperature = reactor.temperatureKelvin();
            helper.assertTrue(temperature > AMBIENT, "reactor was never heated");
            CompoundTag saved = reactor.saveWithoutMetadata(level.registryAccess());
            helper.assertTrue(saved.contains("body_temperature_kelvin", Tag.TAG_DOUBLE),
                "body temperature was not persisted");

            ChemicalReactorBlockEntity restored = new ChemicalReactorBlockEntity(
                helper.absolutePos(REACTOR), level.getBlockState(helper.absolutePos(REACTOR)));
            restored.loadWithComponents(saved, level.registryAccess());
            helper.assertTrue(Math.abs(restored.temperatureKelvin() - temperature) < 1e-9,
                "reload changed the body temperature");

            CompoundTag legacy = saved.copy();
            legacy.remove("body_temperature_kelvin");
            helper.assertTrue(reloaded(helper, legacy).temperatureKelvin() == AMBIENT,
                "a save without thermal state did not fall back to room temperature");
            for (double damaged : new double[] { Double.NaN, -50.0, Double.POSITIVE_INFINITY }) {
                CompoundTag tag = saved.copy();
                tag.putDouble("body_temperature_kelvin", damaged);
                helper.assertTrue(reloaded(helper, tag).temperatureKelvin() == AMBIENT,
                    "damaged body temperature " + damaged + " was accepted");
            }
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 300)
    public static void coolingBelowTheRuleMinimumCancelsTheBatchWithoutConsumingEthanol(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, lit(Blocks.FURNACE), true);

        helper.runAfterDelay(60, () -> {
            helper.assertTrue(reactor.reactionProgress() > 0.0 && reactor.reactionProgress() < 1.0,
                "lit furnace did not create a partial batch");
            removeHeat(helper);
        });
        helper.runAfterDelay(220, () -> {
            double temperature = reactor.temperatureKelvin();
            helper.assertTrue(temperature < ETHANOL_RULE_MINIMUM,
                "vessel was still hot enough to react eight seconds without a source: " + temperature);
            helper.assertTrue(reactor.reactionProgress() == 0.0, "a cooled vessel kept accumulating progress");
            helper.assertTrue(canonicalKey("CCO").equals(pureCanonicalKey(
                    reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT))),
                "cooling consumed the ethanol input");
            helper.assertTrue(VialContentsState.fromStack(
                    reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT)).isEmpty(),
                "a cancelled batch left a partial product");
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 60)
    public static void unheatedReactorStaysAtRoomTemperatureAndRefusesTheBatch(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, Blocks.AIR.defaultBlockState(), true);

        helper.runAfterDelay(45, () -> {
            helper.assertTrue(reactor.temperatureKelvin() == AMBIENT,
                "an unheated reactor drifted from room temperature: " + reactor.temperatureKelvin());
            helper.assertTrue(reactor.reactionProgress() == 0.0, "unheated reactor accumulated progress");
            helper.assertTrue(canonicalKey("CCO").equals(pureCanonicalKey(
                    reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT))),
                "unheated reactor consumed ethanol");
            helper.succeed();
        });
    }

    private static void removeHeat(GameTestHelper helper) {
        helper.getLevel().setBlock(helper.absolutePos(HEAT_SOURCE), Blocks.AIR.defaultBlockState(), 3);
    }

    private static ChemicalReactorBlockEntity reloaded(GameTestHelper helper, CompoundTag tag) {
        var level = helper.getLevel();
        ChemicalReactorBlockEntity restored = new ChemicalReactorBlockEntity(
            helper.absolutePos(REACTOR), level.getBlockState(helper.absolutePos(REACTOR)));
        restored.loadWithComponents(tag, level.registryAccess());
        return restored;
    }

    private static BlockState lit(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true);
    }

    private static ChemicalReactorBlockEntity setup(
        GameTestHelper helper,
        BlockState heatSource,
        boolean includeCopperCatalyst
    ) {
        var level = helper.getLevel();
        level.setBlock(helper.absolutePos(HEAT_SOURCE), heatSource, 3);
        level.setBlock(helper.absolutePos(REACTOR), ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), 3);

        var blockEntity = level.getBlockEntity(helper.absolutePos(REACTOR));
        helper.assertTrue(blockEntity instanceof ChemicalReactorBlockEntity, "reactor block entity was not created");
        ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) blockEntity;
        reactor.setItem(ChemicalReactorBlockEntity.TARGET_SLOT, filledVial("CCO"));
        if (includeCopperCatalyst) reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemStack(copperItem()));
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, emptyVial());
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT, emptyVial());
        return reactor;
    }

    private static net.minecraft.world.item.Item copperItem() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot"));
    }

    private static ItemStack emptyVial() {
        return new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
    }

    private static ItemStack filledVial(String structure) {
        ItemStack vial = emptyVial();
        vial.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, VIAL_MICROMOLES, 1_000_000));
        return vial;
    }

    private static String pureCanonicalKey(ItemStack vial) {
        VialContentsState state = VialContentsState.fromStack(vial);
        return state.mixture() == null && state.substance() != null ? state.substance().canonicalKey() : "";
    }

    private static String canonicalKey(String structure) {
        return new SubstanceContents(structure, 1L, 1_000_000).canonicalKey();
    }
}
