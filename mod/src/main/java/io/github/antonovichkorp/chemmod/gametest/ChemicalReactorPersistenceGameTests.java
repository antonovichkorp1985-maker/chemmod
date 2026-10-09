package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlockEntity;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import io.github.antonovichkorp.chemmod.content.VialContentsState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** Actual block-entity NBT round trips, not a replacement for a full server restart/GUI test. */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalReactorPersistenceGameTests {
    private static final BlockPos HEAT_SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos REACTOR = new BlockPos(1, 2, 1);
    private static final long AMOUNT = 1_000_000L;

    private ChemicalReactorPersistenceGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 180)
    public static void partialBatchResumesAndCompletedProductsSurviveReload(GameTestHelper helper) {
        ChemicalReactorBlockEntity original = setupSlowBatch(helper);
        CompoundTag[] completedInventory = new CompoundTag[1];
        helper.runAfterDelay(25, () -> {
            assertPartial(helper, original);
            double progress = original.reactionProgress();
            CompoundTag before = inventory(helper, original);
            ChemicalReactorBlockEntity restored = reload(helper, save(helper, original));
            helper.assertTrue(restored != original, "reload reused the old block entity");
            helper.assertTrue(restored.reactionProgress() == progress, "partial progress was lost on load");
            assertInventory(helper, restored, before);
            // A second save before the first tick must not lose the operation witness.
            restored = reload(helper, save(helper, restored));
            helper.assertTrue(restored.reactionProgress() == progress, "second load lost pending progress");
        });
        helper.runAfterDelay(45, () -> {
            ChemicalReactorBlockEntity restored = reactor(helper);
            // 650 K gives about 0.218 progress/second. By now at least two
            // evaluations occurred: resetting during load would leave only one.
            helper.assertTrue(restored.reactionProgress() > 0.4 && restored.reactionProgress() < 1.0,
                "valid saved operation did not continue from its previous progress");
            helper.getLevel().setBlock(helper.absolutePos(HEAT_SOURCE),
                Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true), 3);
        });
        helper.runAfterDelay(70, () -> {
            ChemicalReactorBlockEntity finished = reactor(helper);
            assertProducts(helper, finished);
            assertCompletedDisplay(helper, finished);
            String rule = finished.lastCompletedRule();
            helper.assertTrue(!rule.isBlank(), "completed batch has no rule record");
            completedInventory[0] = inventory(helper, finished);
            ChemicalReactorBlockEntity restored = reload(helper, save(helper, finished));
            helper.assertTrue(restored.reactionProgress() == 0.0, "completed operation retained progress");
            helper.assertTrue(rule.equals(restored.lastCompletedRule()), "completed rule was lost on load");
            assertInventory(helper, restored, completedInventory[0]);
        });
        helper.runAfterDelay(115, () -> {
            assertInventory(helper, reactor(helper), completedInventory[0]);
            assertProducts(helper, reactor(helper));
            assertCompletedDisplay(helper, reactor(helper));
            ItemStack taken = reactor(helper).removeItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, 1);
            helper.assertTrue(!taken.isEmpty() && reactor(helper).menuData().get(0) == 0,
                "taking a product did not immediately clear the completed display");
            helper.assertTrue(reactor(helper).menuData().get(2) == 0, "stale rule still displayed after taking output");
        });
        helper.runAfterDelay(155, () -> {
            helper.assertTrue(ChemicalReactorBlockEntity.statusComponent(reactor(helper).menuData().get(1))
                .equals(Component.translatable("reactor_status.chemmod.idle")),
                "the empty source vial should wait for input, not report an impure vial");
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void completedDisplayRejectsChangedSavedProducts(GameTestHelper helper) {
        ChemicalReactorBlockEntity original = setupSlowBatch(helper);
        helper.getLevel().setBlock(helper.absolutePos(HEAT_SOURCE),
            Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true), 3);
        CompoundTag[] expected = new CompoundTag[1];
        helper.runAfterDelay(25, () -> {
            assertCompletedDisplay(helper, original);
            CompoundTag saved = save(helper, original);
            replaceSavedSlot(helper, saved, ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, vial("O", AMOUNT));
            ChemicalReactorBlockEntity restored = reload(helper, saved);
            expected[0] = inventory(helper, restored);
        });
        helper.runAfterDelay(50, () -> {
            ChemicalReactorBlockEntity restored = reactor(helper);
            helper.assertTrue(restored.menuData().get(0) == 0 && restored.menuData().get(2) == 0,
                "changed saved products retained an unrelated success display");
            assertInventory(helper, restored, expected[0]);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void changedSavedAmountCannotInheritPreviousBatchProgress(GameTestHelper helper) {
        ChemicalReactorBlockEntity original = setupSlowBatch(helper);
        helper.runAfterDelay(25, () -> {
            assertPartial(helper, original);
            CompoundTag saved = save(helper, original);
            // Keep the witness for the original million-micromole operation,
            // but substitute a different, still perfectly valid ethanol input.
            replaceSavedSlot(helper, saved, ChemicalReactorBlockEntity.TARGET_SLOT, vial("CCO", AMOUNT / 2));
            saved.putDouble("reaction_progress", 0.99);
            reload(helper, saved);
        });
        helper.runAfterDelay(45, () -> {
            ChemicalReactorBlockEntity restored = reactor(helper);
            assertFreshPartial(helper, restored);
            SubstanceContents target = VialContentsState.fromStack(
                restored.getItem(ChemicalReactorBlockEntity.TARGET_SLOT)).substance();
            helper.assertTrue(target != null && target.micromoles() == AMOUNT / 2,
                "stale progress consumed the substituted ethanol");
            assertEmptyProducts(helper, restored);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void changedSavedRuleCannotInheritPreviousBatchProgress(GameTestHelper helper) {
        ChemicalReactorBlockEntity original = setupSlowBatch(helper);
        helper.runAfterDelay(25, () -> {
            assertPartial(helper, original);
            CompoundTag saved = save(helper, original);
            saved.getCompound("processing_operation").putString("rule", "chemmod:carbonyl_hydrogenation");
            saved.putDouble("reaction_progress", 0.99);
            reload(helper, saved);
        });
        helper.runAfterDelay(45, () -> {
            assertFreshPartial(helper, reactor(helper));
            assertEmptyProducts(helper, reactor(helper));
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void legacyProgressRestartsWithoutChangingInventory(GameTestHelper helper) {
        ChemicalReactorBlockEntity original = setupSlowBatch(helper);
        helper.runAfterDelay(25, () -> {
            assertPartial(helper, original);
            CompoundTag saved = save(helper, original);
            CompoundTag before = inventory(helper, original);
            saved.remove("processing_operation"); // test.4 and older saves
            saved.putDouble("reaction_progress", 0.99);
            ChemicalReactorBlockEntity restored = reload(helper, saved);
            helper.assertTrue(restored.reactionProgress() == 0.0, "legacy progress has no trustworthy provenance");
            assertInventory(helper, restored, before);
        });
        helper.runAfterDelay(45, () -> {
            assertFreshPartial(helper, reactor(helper));
            assertEmptyProducts(helper, reactor(helper));
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void invalidNumericProgressCannotCompleteBatch(GameTestHelper helper) {
        ChemicalReactorBlockEntity original = setupSlowBatch(helper);
        helper.runAfterDelay(25, () -> {
            assertPartial(helper, original);
            CompoundTag saved = save(helper, original);
            CompoundTag before = inventory(helper, original);
            for (double value : new double[] { -0.5, 1.5, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN }) {
                CompoundTag damaged = saved.copy();
                damaged.putDouble("reaction_progress", value);
                ChemicalReactorBlockEntity restored = reload(helper, damaged);
                helper.assertTrue(restored.reactionProgress() == 0.0, "invalid saved progress was accepted: " + value);
                assertInventory(helper, restored, before);
            }
        });
        helper.runAfterDelay(45, () -> {
            assertFreshPartial(helper, reactor(helper));
            assertEmptyProducts(helper, reactor(helper));
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void resumedBatchRechecksActualHeatBeforeConsumingInputs(GameTestHelper helper) {
        ChemicalReactorBlockEntity original = setupSlowBatch(helper);
        CompoundTag[] before = new CompoundTag[1];
        helper.runAfterDelay(25, () -> {
            assertPartial(helper, original);
            CompoundTag saved = save(helper, original);
            saved.putDouble("reaction_progress", 0.99);
            before[0] = inventory(helper, original);
            helper.getLevel().setBlock(helper.absolutePos(HEAT_SOURCE), Blocks.AIR.defaultBlockState(), 3);
            reload(helper, saved);
        });
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(reactor(helper).reactionProgress() == 0.0, "reload bypassed the heat requirement");
            assertInventory(helper, reactor(helper), before[0]);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void filledOutputRefusesBatchWithoutOverwritingContents(GameTestHelper helper) {
        assertBlockedOutput(helper, vial("O", AMOUNT));
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void nonVialOutputRefusesBatchWithoutLosingItems(GameTestHelper helper) {
        assertBlockedOutput(helper, new ItemStack(Items.STONE));
    }

    private static void assertBlockedOutput(GameTestHelper helper, ItemStack output) {
        ChemicalReactorBlockEntity reactor = setupSlowBatch(helper);
        helper.getLevel().setBlock(helper.absolutePos(HEAT_SOURCE),
            Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true), 3);
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, output);
        CompoundTag before = inventory(helper, reactor);
        helper.runAfterDelay(45, () -> {
            assertInventory(helper, reactor, before);
            helper.assertTrue(reactor.reactionProgress() == 0.0, "blocked output allowed progress");
            helper.succeed();
        });
    }

    private static ChemicalReactorBlockEntity setupSlowBatch(GameTestHelper helper) {
        helper.getLevel().setBlock(helper.absolutePos(HEAT_SOURCE),
            Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true), 3);
        helper.getLevel().setBlock(helper.absolutePos(REACTOR), ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), 3);
        ChemicalReactorBlockEntity reactor = reactor(helper);
        reactor.setItem(ChemicalReactorBlockEntity.TARGET_SLOT, vial("CCO", AMOUNT));
        reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot"))));
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, new ItemStack(ChemItems.SUBSTANCE_VIAL.get()));
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT, new ItemStack(ChemItems.SUBSTANCE_VIAL.get()));
        return reactor;
    }

    private static ChemicalReactorBlockEntity reactor(GameTestHelper helper) {
        return (ChemicalReactorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(REACTOR));
    }

    private static CompoundTag save(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        return reactor.saveWithoutMetadata(helper.getLevel().registryAccess());
    }

    private static ChemicalReactorBlockEntity reload(GameTestHelper helper, CompoundTag saved) {
        BlockPos pos = helper.absolutePos(REACTOR);
        ChemicalReactorBlockEntity restored = new ChemicalReactorBlockEntity(pos, helper.getLevel().getBlockState(pos));
        restored.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.getLevel().removeBlockEntity(pos);
        helper.getLevel().setBlockEntity(restored);
        return restored;
    }

    private static CompoundTag inventory(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        NonNullList<ItemStack> slots = NonNullList.withSize(ChemicalReactorBlockEntity.SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < slots.size(); slot++) slots.set(slot, reactor.getItem(slot).copy());
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, slots, helper.getLevel().registryAccess());
        return tag;
    }

    private static void replaceSavedSlot(GameTestHelper helper, CompoundTag saved, int slot, ItemStack item) {
        NonNullList<ItemStack> slots = NonNullList.withSize(ChemicalReactorBlockEntity.SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(saved, slots, helper.getLevel().registryAccess());
        slots.set(slot, item);
        ContainerHelper.saveAllItems(saved, slots, helper.getLevel().registryAccess());
    }

    private static ItemStack vial(String structure, long amount) {
        ItemStack stack = new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
        stack.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, amount, 1_000_000));
        return stack;
    }

    private static void assertCompletedDisplay(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        helper.assertTrue(reactor.reactionProgress() == 0.0 && reactor.menuData().get(0) == 1_000,
            "completed UI should show 100 percent without keeping an active operation");
        helper.assertTrue(ChemicalReactorBlockEntity.statusComponent(reactor.menuData().get(1))
            .equals(Component.translatable("reactor_status.chemmod.complete")), "completed status was lost");
        helper.assertTrue(reactor.menuData().get(2) > 0, "completed reaction rule is no longer displayed");
    }

    private static void assertPartial(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        helper.assertTrue(reactor.reactionProgress() > 0.0 && reactor.reactionProgress() < 1.0,
            "expected a real unfinished reaction before serialization");
    }

    private static void assertFreshPartial(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        helper.assertTrue(reactor.reactionProgress() > 0.0 && reactor.reactionProgress() < 0.4,
            "a changed or invalid save inherited old progress instead of starting a fresh batch");
    }

    private static void assertInventory(GameTestHelper helper, ChemicalReactorBlockEntity reactor, CompoundTag expected) {
        helper.assertTrue(inventory(helper, reactor).equals(expected), "inventory item/count/component data changed");
    }

    private static void assertEmptyProducts(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        for (int slot = ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT; slot <= ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT; slot++) {
            helper.assertTrue(VialContentsState.fromStack(reactor.getItem(slot)).isEmpty(), "unexpected partial product");
        }
    }

    private static void assertProducts(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        helper.assertTrue(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT).is(ChemItems.SUBSTANCE_VIAL.get())
            && VialContentsState.fromStack(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT)).isEmpty(),
            "consumed ethanol must leave an empty physical vial");
        Set<String> keys = new HashSet<>();
        for (int slot = ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT; slot <= ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT; slot++) {
            SubstanceContents contents = VialContentsState.fromStack(reactor.getItem(slot)).substance();
            helper.assertTrue(contents != null && contents.micromoles() == AMOUNT && contents.purityPpm() == 1_000_000,
                "product amount or purity differs from the exact ethanol batch");
            keys.add(contents.canonicalKey());
        }
        helper.assertTrue(keys.equals(Set.of(
            new SubstanceContents("CC=O", 1, 1_000_000).canonicalKey(),
            new SubstanceContents("[H][H]", 1, 1_000_000).canonicalKey())), "unexpected product identities");
        helper.assertTrue(reactor.getItem(ChemicalReactorBlockEntity.CATALYST_SLOT).getCount() == 1
            && reactor.getItem(ChemicalReactorBlockEntity.CATALYST_SLOT).is(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot"))), "copper catalyst was changed");
    }
}
