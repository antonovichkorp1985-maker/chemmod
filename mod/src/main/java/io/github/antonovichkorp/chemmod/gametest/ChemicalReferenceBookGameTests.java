package io.github.antonovichkorp.chemmod.gametest;

import com.mojang.authlib.GameProfile;
import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Headless M2/A2 coverage for the finite-catalog reference-book interaction. */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalReferenceBookGameTests {
    private ChemicalReferenceBookGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void sneakingWithBookAndFilledReferenceVialPrintsReportWithoutConsumingEither(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
            new GameProfile(UUID.randomUUID(), "reference_book_reader"));
        ItemStack book = new ItemStack(ChemItems.REFERENCE_BOOK.get());
        ItemStack vial = ChemItems.vial("ethanol");
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.setItemInHand(InteractionHand.OFF_HAND, vial);
        player.setShiftKeyDown(true);

        var result = ChemItems.REFERENCE_BOOK.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(), "reference-book interaction was not handled");
        helper.assertTrue(book.getCount() == 1 && vial.getCount() == 1,
            "reading consumed the book or vial");
        helper.assertTrue(vial.get(ChemComponents.SUBSTANCE.get()) != null,
            "reading modified or emptied the sample");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void bookDoesNotOpenWithoutSneaking(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
            new GameProfile(UUID.randomUUID(), "reference_book_not_sneaking"));
        ItemStack book = new ItemStack(ChemItems.REFERENCE_BOOK.get());
        ItemStack vial = ChemItems.vial("water");
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.setItemInHand(InteractionHand.OFF_HAND, vial);
        player.setShiftKeyDown(false);

        var result = ChemItems.REFERENCE_BOOK.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult() == InteractionResult.PASS,
            "book consumed the normal use action without the shift modifier");
        helper.assertTrue(book.getCount() == 1 && vial.getCount() == 1
            && vial.get(ChemComponents.SUBSTANCE.get()) != null,
            "non-interaction changed either held item");
        helper.succeed();
    }
}
