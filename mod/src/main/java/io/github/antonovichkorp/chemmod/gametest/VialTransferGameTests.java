package io.github.antonovichkorp.chemmod.gametest;

import com.mojang.authlib.GameProfile;
import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.MixtureContents;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Headless M2 regression: transferring a sample conserves its exact molecular contents. */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VialTransferGameTests {
    private VialTransferGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void halfFullVialTransfersItsExactAmountAndLeavesBothContainersValid(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
            new GameProfile(UUID.randomUUID(), "vial_transfer_amount"));
        ItemStack flask = new ItemStack(ChemItems.STEEL_FLASK.get());
        ItemStack vial = ChemItems.vialFromStructure("CCO", 500_000L, 1_000_000);
        player.setItemInHand(InteractionHand.MAIN_HAND, flask);
        player.setItemInHand(InteractionHand.OFF_HAND, vial);

        var result = ChemItems.STEEL_FLASK.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        SubstanceContents contents = flask.get(ChemComponents.SUBSTANCE.get());
        helper.assertTrue(result.getResult().consumesAction(), "valid transfer was refused");
        helper.assertTrue(contents != null && contents.micromoles() == 500_000L
            && contents.canonicalKey().equals(vialKey("CCO")), "flask did not receive the exact graph and amount");
        helper.assertTrue(vial.get(ChemComponents.SUBSTANCE.get()) == null
            && vial.get(ChemComponents.MIXTURE.get()) == null, "source vial was not emptied");
        helper.assertTrue(flask.getCount() == 1 && vial.getCount() == 1, "container item count changed");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void explicitMixtureTransferPreservesEveryPartExactly(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
            new GameProfile(UUID.randomUUID(), "vial_transfer_mixture"));
        ItemStack flask = new ItemStack(ChemItems.STEEL_FLASK.get());
        ItemStack vial = ChemItems.mixtureVial("вода", "этанол");
        MixtureContents before = vial.get(ChemComponents.MIXTURE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, flask);
        player.setItemInHand(InteractionHand.OFF_HAND, vial);

        ChemItems.STEEL_FLASK.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        MixtureContents after = flask.get(ChemComponents.MIXTURE.get());
        helper.assertTrue(before != null && before.equals(after), "mixture component identities or amounts changed");
        helper.assertTrue(vial.get(ChemComponents.MIXTURE.get()) == null
            && vial.get(ChemComponents.SUBSTANCE.get()) == null, "mixture remained in source vial");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void occupiedFlaskAndStackedVialRefuseWithoutChangingEitherStack(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
            new GameProfile(UUID.randomUUID(), "vial_transfer_refusal"));
        ItemStack flask = new ItemStack(ChemItems.STEEL_FLASK.get());
        flask.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents("O", 250_000L, 1_000_000));
        ItemStack vial = ChemItems.vialFromStructure("CCO", 500_000L, 1_000_000);
        var flaskBefore = flask.copy();
        var vialBefore = vial.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, flask);
        player.setItemInHand(InteractionHand.OFF_HAND, vial);
        var occupiedResult = ChemItems.STEEL_FLASK.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(occupiedResult.getResult() == InteractionResult.FAIL, "occupied flask accepted a transfer");
        helper.assertTrue(ItemStack.isSameItemSameComponents(flaskBefore, flask)
            && ItemStack.isSameItemSameComponents(vialBefore, vial), "refusal changed contents");

        flask.remove(ChemComponents.SUBSTANCE.get());
        vial.setCount(2);
        flaskBefore = flask.copy();
        vialBefore = vial.copy();
        var stackedResult = ChemItems.STEEL_FLASK.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(stackedResult.getResult() == InteractionResult.FAIL, "stacked vials were transferred");
        helper.assertTrue(ItemStack.isSameItemSameComponents(flaskBefore, flask)
            && ItemStack.isSameItemSameComponents(vialBefore, vial), "stack refusal changed contents");
        helper.succeed();
    }

    private static String vialKey(String structure) {
        return io.github.antonovichkorp.chemmod.core.Molecule.Companion.fromSMILESlike(structure).canonicalKey();
    }
}
