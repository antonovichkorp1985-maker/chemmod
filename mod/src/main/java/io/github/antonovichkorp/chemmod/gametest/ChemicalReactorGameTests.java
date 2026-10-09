package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlockEntity;
import io.github.antonovichkorp.chemmod.content.MixtureContents;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import io.github.antonovichkorp.chemmod.content.VialContentsState;
import io.github.antonovichkorp.chemmod.core.mixture.MolecularMixture;
import io.github.antonovichkorp.chemmod.core.reaction.MolecularPortion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless in-game guard for the M3 happy path. This supplements, but never
 * substitutes for, the documented player client/server acceptance checklist.
 */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalReactorGameTests {
    private static final BlockPos HEAT_SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos REACTOR = new BlockPos(1, 2, 1);
    private static final long VIAL_MICROMOLES = 1_000_000L;

    private ChemicalReactorGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void reactorStatesUseBlockModelRenderingWithoutReplacingInventory(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setupBatch(helper, Blocks.AIR.defaultBlockState(), true);
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REACTOR);
        var before = reactor.saveWithoutMetadata(level.registryAccess());
        for (boolean active : new boolean[] { false, true, false }) {
            var state = ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState()
                .setValue(ChemicalReactorBlock.ACTIVE, active);
            level.setBlock(pos, state, 3);
            helper.assertTrue(level.getBlockState(pos).getRenderShape() == RenderShape.MODEL,
                "reactor state selects invisible rendering instead of its block model");
            helper.assertTrue(level.getBlockEntity(pos) == reactor,
                "visual state change replaced the physical reactor");
            helper.assertTrue(before.equals(reactor.saveWithoutMetadata(level.registryAccess())),
                "visual state change modified the reactor contents");
        }
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void ethanolBatchProducesSeparateProductsAndRetainsCatalyst(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setupBatch(
            helper,
            Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true),
            true
        );

        // At 900 K the bundled Arrhenius model completes within the first
        // one-second reactor tick. The delay also leaves time for an arbitrary
        // test-server game-time phase before that tick.
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT)
                .get(ChemComponents.SUBSTANCE.get()) == null, "ethanol input was not consumed");
            assertCopperRetained(helper, reactor);

            Set<String> productKeys = new HashSet<>();
            productKeys.add(pureCanonicalKey(reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT)));
            productKeys.add(pureCanonicalKey(reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT)));
            helper.assertTrue(
                productKeys.equals(Set.of(canonicalKey("CC=O"), canonicalKey("[H][H]"))),
                "reactor did not create separate acetaldehyde and hydrogen vials"
            );
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void missingHeatRefusesBatchWithoutConsumingInputs(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setupBatch(helper, Blocks.AIR.defaultBlockState(), true);

        helper.runAfterDelay(45, () -> {
            assertEthanolRetained(helper, reactor, "missing heat consumed ethanol");
            assertEmptyOutputs(helper, reactor, "missing heat created a product");
            helper.assertTrue(reactor.reactionProgress() == 0.0, "missing heat accumulated reactor progress");
            assertCopperRetained(helper, reactor);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void missingCatalystRefusesBatchWithoutConsumingInputs(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setupBatch(
            helper,
            Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true),
            false
        );

        helper.runAfterDelay(45, () -> {
            assertEthanolRetained(helper, reactor, "missing catalyst consumed ethanol");
            assertEmptyOutputs(helper, reactor, "missing catalyst created a product");
            helper.assertTrue(reactor.reactionProgress() == 0.0, "missing catalyst accumulated reactor progress");
            helper.assertTrue(reactor.getItem(ChemicalReactorBlockEntity.CATALYST_SLOT).isEmpty(),
                "a missing catalyst was unexpectedly introduced");
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void impureVialRefusesBatchWithoutCrashingOrLosingMatter(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setupBatch(
            helper,
            Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true),
            true
        );
        reactor.setItem(ChemicalReactorBlockEntity.TARGET_SLOT, filledVial("CCO", 999_999));

        helper.runAfterDelay(45, () -> {
            VialContentsState target = VialContentsState.fromStack(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT));
            helper.assertTrue(
                target.substance() != null
                    && target.mixture() == null
                    && target.substance().purityPpm() == 999_999
                    && canonicalKey("CCO").equals(target.substance().canonicalKey()),
                "impure ethanol was consumed or rewritten"
            );
            assertEmptyOutputs(helper, reactor, "impure ethanol created a product");
            helper.assertTrue(reactor.reactionProgress() == 0.0, "impure ethanol accumulated reactor progress");
            assertCopperRetained(helper, reactor);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void explicitMixtureRefusesBatchWithoutExtractingAComponent(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setupBatch(
            helper,
            Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true),
            true
        );
        reactor.setItem(ChemicalReactorBlockEntity.TARGET_SLOT, ethanolWaterMixtureVial());

        helper.runAfterDelay(45, () -> {
            VialContentsState target = VialContentsState.fromStack(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT));
            helper.assertTrue(
                target.substance() == null
                    && target.mixture() != null
                    && target.mixture().mixture().portions().size() == 2,
                "reactor extracted or rewrote a component from an explicit mixture"
            );
            assertEmptyOutputs(helper, reactor, "explicit mixture created a product");
            helper.assertTrue(reactor.reactionProgress() == 0.0, "explicit mixture accumulated reactor progress");
            assertCopperRetained(helper, reactor);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 100)
    public static void removingOutputVialCancelsPartialBatchWithoutConsumingEthanol(GameTestHelper helper) {
        // A regular lit furnace is intentionally valid but slow (650 K), so a
        // real partial Arrhenius batch exists before the output is removed.
        ChemicalReactorBlockEntity reactor = setupBatch(
            helper,
            Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true),
            true
        );

        helper.runAfterDelay(25, () -> {
            helper.assertTrue(
                reactor.reactionProgress() > 0.0 && reactor.reactionProgress() < 1.0,
                "lit furnace did not create a partial physical reactor batch"
            );
            ItemStack removed = reactor.removeItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, 1);
            helper.assertTrue(!removed.isEmpty(), "expected to remove the reserved output vial");
            helper.assertTrue(reactor.reactionProgress() == 0.0, "output mutation did not cancel reactor progress immediately");
        });

        helper.runAfterDelay(50, () -> {
            helper.assertTrue(
                canonicalKey("CCO").equals(pureCanonicalKey(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT))),
                "cancelling a partial batch consumed its ethanol input"
            );
            helper.assertTrue(reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT).isEmpty(),
                "removed output vial was unexpectedly replaced or filled");
            helper.assertTrue(VialContentsState.fromStack(
                reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT)).isEmpty(),
                "an interrupted batch wrote a partial product");
            assertCopperRetained(helper, reactor);
            helper.succeed();
        });
    }

    private static ChemicalReactorBlockEntity setupBatch(
        GameTestHelper helper,
        net.minecraft.world.level.block.state.BlockState heatSource,
        boolean includeCopperCatalyst
    ) {
        var level = helper.getLevel();
        level.setBlock(helper.absolutePos(HEAT_SOURCE), heatSource, 3);
        level.setBlock(helper.absolutePos(REACTOR), ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), 3);

        BlockEntity blockEntity = level.getBlockEntity(helper.absolutePos(REACTOR));
        helper.assertTrue(blockEntity instanceof ChemicalReactorBlockEntity, "chemical reactor block entity was not created");
        ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) blockEntity;
        reactor.setItem(ChemicalReactorBlockEntity.TARGET_SLOT, filledVial("CCO"));
        if (includeCopperCatalyst) reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemStack(copperItem()));
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, emptyVial());
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT, emptyVial());
        return reactor;
    }

    private static void assertEthanolRetained(GameTestHelper helper, ChemicalReactorBlockEntity reactor, String message) {
        helper.assertTrue(
            canonicalKey("CCO").equals(pureCanonicalKey(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT))),
            message
        );
    }

    private static void assertEmptyOutputs(GameTestHelper helper, ChemicalReactorBlockEntity reactor, String message) {
        boolean bothEmpty = VialContentsState.fromStack(reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT)).isEmpty()
            && VialContentsState.fromStack(reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT)).isEmpty();
        helper.assertTrue(bothEmpty, message);
    }

    private static void assertCopperRetained(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        helper.assertTrue(
            reactor.getItem(ChemicalReactorBlockEntity.CATALYST_SLOT).is(copperItem()),
            "copper catalyst was not retained"
        );
    }

    private static net.minecraft.world.item.Item copperItem() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot"));
    }

    private static ItemStack emptyVial() {
        return new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
    }

    private static ItemStack ethanolWaterMixtureVial() {
        SubstanceContents ethanol = new SubstanceContents("CCO", VIAL_MICROMOLES / 2, 1_000_000);
        SubstanceContents water = new SubstanceContents("O", VIAL_MICROMOLES / 2, 1_000_000);
        MolecularMixture mixture = new MolecularMixture(List.of(
            new MolecularPortion(ethanol.molecule(), ethanol.micromoles()),
            new MolecularPortion(water.molecule(), water.micromoles())
        ));
        ItemStack vial = emptyVial();
        vial.set(ChemComponents.MIXTURE.get(), MixtureContents.fromMolecularMixture(mixture));
        return vial;
    }

    private static ItemStack filledVial(String structure) {
        return filledVial(structure, 1_000_000);
    }

    private static ItemStack filledVial(String structure, int purityPpm) {
        ItemStack vial = emptyVial();
        vial.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, VIAL_MICROMOLES, purityPpm));
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
