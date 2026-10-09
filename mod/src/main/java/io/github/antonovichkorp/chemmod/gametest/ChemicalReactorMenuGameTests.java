package io.github.antonovichkorp.chemmod.gametest;

import com.mojang.authlib.GameProfile;
import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlockEntity;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorMenu;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Server menu actions, not graphical clients. All timed fixtures stop before synthesizing products. */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalReactorMenuGameTests {
    private static final BlockPos REACTOR = new BlockPos(1, 2, 1);
    private ChemicalReactorMenuGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void viewerAndRejectedClicksCannotStealPendingBatch(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, true);
        FakePlayer editor = player(helper, "ChemMenuEditor");
        FakePlayer viewer = player(helper, "ChemMenuViewer");
        ChemicalReactorMenu editing = new ChemicalReactorMenu(21, editor.getInventory(), reactor);
        editing.setCarried(copper(1));
        editing.clicked(ChemicalReactorBlockEntity.CATALYST_SLOT, 0, ClickType.PICKUP, editor);
        assertOperator(helper, reactor, "last_operator", editor);

        // Neither placement nor constructing/opening a read-only view supplies a batch.
        ChemBlocks.CHEMICAL_REACTOR.get().setPlacedBy(helper.getLevel(), reactor.getBlockPos(),
            reactor.getBlockState(), viewer, new ItemStack(ChemBlocks.CHEMICAL_REACTOR.get()));
        ChemicalReactorMenu watching = new ChemicalReactorMenu(22, viewer.getInventory(), reactor);
        watching.clicked(-999, 0, ClickType.PICKUP, viewer);
        assertOperator(helper, reactor, "last_operator", editor);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(reactor.reactionProgress() > 0.0, "physical batch did not start");
            double progress = reactor.reactionProgress();
            watching.setCarried(new ItemStack(Items.STONE));
            watching.clicked(ChemicalReactorBlockEntity.TARGET_SLOT, 0, ClickType.PICKUP, viewer);
            helper.assertTrue(watching.getCarried().is(Items.STONE), "invalid item entered a reactor slot");
            helper.assertTrue(reactor.reactionProgress() == progress, "rejected click canceled the active batch");
            // Move the cursor item into the viewer's own inventory, not the machine.
            watching.clicked(6, 0, ClickType.PICKUP, viewer);
            assertOperator(helper, reactor, "last_operator", editor);
            assertOperator(helper, reactor, "processing_operator", editor);
        });
        helper.runAfterDelay(50, () -> {
            assertOperator(helper, reactor, "processing_operator", editor);
            reactor.clearContent(); // prevent this fixture from altering global discovery later
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void realSecondEditorCancelsAndOwnsRestartedBatch(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, true);
        FakePlayer first = player(helper, "ChemMenuFirst");
        FakePlayer second = player(helper, "ChemMenuSecond");
        ChemicalReactorMenu firstMenu = new ChemicalReactorMenu(23, first.getInventory(), reactor);
        firstMenu.setCarried(copper(1));
        firstMenu.clicked(ChemicalReactorBlockEntity.CATALYST_SLOT, 0, ClickType.PICKUP, first);
        ChemicalReactorMenu secondMenu = new ChemicalReactorMenu(24, second.getInventory(), reactor);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(reactor.reactionProgress() > 0.0, "first actor's batch did not start");
            secondMenu.clicked(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, 0, ClickType.PICKUP, second);
            helper.assertTrue(reactor.reactionProgress() == 0.0 && secondMenu.getCarried().getCount() == 1,
                "taking the output did not cancel the old operation");
            assertOperator(helper, reactor, "last_operator", second);
            secondMenu.clicked(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, 0, ClickType.PICKUP, second);
            helper.assertTrue(secondMenu.getCarried().isEmpty(), "output vial was not restored");
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(reactor.reactionProgress() > 0.0, "new operation did not start");
            assertOperator(helper, reactor, "processing_operator", second);
            reactor.clearContent();
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void shiftClickSplitsStacksAndFullInventoryDoesNotLoseOutput(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, false);
        reactor.clearContent();
        FakePlayer editor = player(helper, "ChemShiftEditor");
        FakePlayer blocked = player(helper, "ChemShiftBlocked");
        ChemicalReactorMenu menu = new ChemicalReactorMenu(25, editor.getInventory(), reactor);
        // Menu slots 6..32 map to inventory 9..35; reactor slots have capacity one.
        editor.getInventory().setItem(9, new ItemStack(ChemItems.SUBSTANCE_VIAL.get(), 16));
        menu.quickMoveStack(editor, 6);
        helper.assertTrue(reactor.getItem(3).getCount() == 1 && reactor.getItem(4).getCount() == 1
            && editor.getInventory().getItem(9).getCount() == 14,
            "shift-click failed to split empty vials into two single-capacity outputs");
        editor.getInventory().setItem(10, copper(64));
        menu.quickMoveStack(editor, 7);
        helper.assertTrue(reactor.getItem(5).getCount() == 1 && editor.getInventory().getItem(10).getCount() == 63,
            "shift-click catalyst count was not conserved");
        assertOperator(helper, reactor, "last_operator", editor);

        for (int index = 0; index < 36; index++) blocked.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
        ChemicalReactorMenu blockedMenu = new ChemicalReactorMenu(26, blocked.getInventory(), reactor);
        ItemStack before = reactor.getItem(3).copy();
        helper.assertTrue(blockedMenu.quickMoveStack(blocked, 3).isEmpty()
            && ItemStack.matches(before, reactor.getItem(3)), "full player inventory consumed or duplicated an output");
        assertOperator(helper, reactor, "last_operator", editor);
        helper.assertTrue(menu.quickMoveStack(editor, -1).isEmpty() && menu.quickMoveStack(editor, 999).isEmpty(),
            "out-of-range quick-move accepted");
        reactor.clearContent();
        helper.succeed();
    }

    private static ChemicalReactorBlockEntity setup(GameTestHelper helper, boolean heat) {
        BlockPos pos = helper.absolutePos(REACTOR);
        helper.getLevel().setBlock(pos.below(), heat
            ? Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true)
            : Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), 3);
        ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) helper.getLevel().getBlockEntity(pos);
        ItemStack ethanol = new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
        ethanol.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents("CCO", 1_000_000L, 1_000_000));
        reactor.setItem(0, ethanol);
        reactor.setItem(3, new ItemStack(ChemItems.SUBSTANCE_VIAL.get()));
        reactor.setItem(4, new ItemStack(ChemItems.SUBSTANCE_VIAL.get()));
        return reactor;
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
            UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        player.getInventory().clearContent();
        return player;
    }

    private static ItemStack copper(int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot")), count);
    }

    private static void assertOperator(GameTestHelper helper, ChemicalReactorBlockEntity reactor, String field, FakePlayer expected) {
        helper.assertTrue(reactor.saveWithoutMetadata(helper.getLevel().registryAccess()).getString(field)
            .equals(expected.getGameProfile().getName()), "incorrect actor for " + field);
    }
}
