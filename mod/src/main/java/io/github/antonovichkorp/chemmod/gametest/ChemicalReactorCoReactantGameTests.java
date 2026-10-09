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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/** Existing JSON combustion rule, actual slots, exact residuals; no new recipe or discovery shortcut. */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalReactorCoReactantGameTests {
    private static final BlockPos REACTOR = new BlockPos(1, 2, 1);
    private ChemicalReactorCoReactantGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 100)
    public static void oxygenLimitedBatchPreservesNonDivisibleRemainders(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, "CCO", 1_000_001L);
        reactor.setItem(1, vial("O=O", 2_000_002L));
        CompoundTag[] completed = new CompoundTag[1];
        helper.runAfterDelay(45, () -> {
            // C2H6O + 3 O2 -> 2 CO2 + 3 H2O; extent = floor(2,000,002 / 3).
            assertPortion(helper, reactor.getItem(0), "CCO", 333_334L);
            assertPortion(helper, reactor.getItem(1), "O=O", 1L);
            helper.assertTrue(reactor.getItem(2).isEmpty(), "unused co-reactant slot changed");
            assertProducts(helper, reactor, 1_333_334L, 2_000_001L);
            helper.assertTrue(reactor.lastCompletedRule().equals("chemmod:complete_combustion"),
                "reactor selected a different rule");
            completed[0] = inventory(helper, reactor);
        });
        helper.runAfterDelay(75, () -> {
            helper.assertTrue(inventory(helper, reactor).equals(completed[0]),
                "completed batch repeated or consumed its quantization remainder");
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void targetLimitedBatchUsesSecondCoReactantSlotWithoutMovingItsRemainder(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, "CCO", 7L);
        reactor.setItem(2, vial("O=O", 24L));
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(reactor.getItem(0).is(ChemItems.SUBSTANCE_VIAL.get())
                && VialContentsState.fromStack(reactor.getItem(0)).isEmpty(), "consumed target did not leave an empty vial");
            helper.assertTrue(reactor.getItem(1).isEmpty(), "second co-reactant was remapped into the first slot");
            assertPortion(helper, reactor.getItem(2), "O=O", 3L);
            assertProducts(helper, reactor, 14L, 21L);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void insufficientQuantizedOxygenCannotBeRoundedIntoAReaction(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, "CCO", 5L);
        reactor.setItem(1, vial("O=O", 2L));
        assertRefusal(helper, reactor);
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void missingPhysicalOxygenCannotBeInventedByTheRule(GameTestHelper helper) {
        assertRefusal(helper, setup(helper, "CCO", 1_000_000L));
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void unrelatedExtraCoReactantIsNotSilentlyDiscarded(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, "CCO", 1_000_000L);
        reactor.setItem(1, vial("O=O", 3_000_000L));
        reactor.setItem(2, vial("O", 1_000_000L));
        assertRefusal(helper, reactor);
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void occupiedSecondOutputCannotPartiallyConsumeEitherInput(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, "CCO", 1_000_000L);
        reactor.setItem(1, vial("O=O", 3_000_000L));
        reactor.setItem(4, vial("O", 123L));
        assertRefusal(helper, reactor);
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void unrepresentableProductQuantityRefusesWithoutCrashingTheServer(GameTestHelper helper) {
        // Synthetic storage-boundary fixture, not a realistic vial capacity.
        // 2 C2H6O2 + 5 O2 -> 4 CO2 + 6 H2O. All inputs fit in long, but
        // 6 * floor(Long.MAX_VALUE / 5) micromoles of water cannot be stored.
        ChemicalReactorBlockEntity reactor = setup(helper, "OCCO", Long.MAX_VALUE);
        reactor.setItem(1, vial("O=O", Long.MAX_VALUE));
        CompoundTag before = inventory(helper, reactor);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(inventory(helper, reactor).equals(before), "overflow changed physical input/output contents");
            helper.assertTrue(reactor.reactionProgress() == 0.0 && reactor.lastCompletedRule().isEmpty(),
                "overflowing batch progressed or committed");
            helper.assertTrue(ChemicalReactorBlockEntity.statusComponent(reactor.menuData().get(1))
                .equals(Component.translatable("reactor_status.chemmod.amount_too_large")),
                "overflow was not reported as a storage-range refusal");
            helper.succeed();
        });
    }

    private static void assertRefusal(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        CompoundTag before = inventory(helper, reactor);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(inventory(helper, reactor).equals(before), "refusal changed an item, count or component");
            helper.assertTrue(reactor.reactionProgress() == 0.0 && reactor.lastCompletedRule().isEmpty(),
                "invalid multi-input batch progressed or completed");
            helper.succeed();
        });
    }

    private static ChemicalReactorBlockEntity setup(GameTestHelper helper, String target, long amount) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REACTOR);
        level.setBlock(pos.below(), Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true), 3);
        level.setBlock(pos, ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), 3);
        ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) level.getBlockEntity(pos);
        reactor.setItem(0, vial(target, amount));
        reactor.setItem(3, new ItemStack(ChemItems.SUBSTANCE_VIAL.get()));
        reactor.setItem(4, new ItemStack(ChemItems.SUBSTANCE_VIAL.get()));
        return reactor;
    }

    private static ItemStack vial(String structure, long amount) {
        ItemStack stack = new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
        stack.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, amount, 1_000_000));
        return stack;
    }

    private static void assertPortion(GameTestHelper helper, ItemStack stack, String structure, long amount) {
        SubstanceContents contents = VialContentsState.fromStack(stack).substance();
        helper.assertTrue(stack.is(ChemItems.SUBSTANCE_VIAL.get()) && stack.getCount() == 1 && contents != null
            && contents.canonicalKey().equals(key(structure)) && contents.micromoles() == amount
            && contents.purityPpm() == 1_000_000, "unexpected molecular identity, purity or exact quantity");
    }

    private static void assertProducts(GameTestHelper helper, ChemicalReactorBlockEntity reactor, long carbonDioxide, long water) {
        Map<String, Long> products = new HashMap<>();
        for (int slot = 3; slot <= 4; slot++) {
            ItemStack stack = reactor.getItem(slot);
            SubstanceContents contents = VialContentsState.fromStack(stack).substance();
            helper.assertTrue(stack.is(ChemItems.SUBSTANCE_VIAL.get()) && stack.getCount() == 1 && contents != null
                && contents.purityPpm() == 1_000_000, "missing single pure product vial");
            helper.assertTrue(products.put(contents.canonicalKey(), contents.micromoles()) == null,
                "duplicate product instead of separate CO2 and water");
        }
        helper.assertTrue(products.equals(Map.of(key("O=C=O"), carbonDioxide, key("O"), water)),
            "product quantities do not match exact stoichiometry");
    }

    private static String key(String structure) {
        return new SubstanceContents(structure, 1L, 1_000_000).canonicalKey();
    }

    private static CompoundTag inventory(GameTestHelper helper, ChemicalReactorBlockEntity reactor) {
        NonNullList<ItemStack> items = NonNullList.withSize(ChemicalReactorBlockEntity.SLOT_COUNT, ItemStack.EMPTY);
        for (int index = 0; index < items.size(); index++) items.set(index, reactor.getItem(index).copy());
        CompoundTag snapshot = new CompoundTag();
        ContainerHelper.saveAllItems(snapshot, items, helper.getLevel().registryAccess());
        return snapshot;
    }
}
