package io.github.antonovichkorp.chemmod.gametest;

import io.github.antonovichkorp.chemmod.ChemMod;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A catalyst earns its capability from its own composition, so an ordinary copper ingot
 * catalyses a rule that names no item at all. Physical slots and a heated vessel only.
 */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChemicalReactorCatalystGameTests {
    private static final BlockPos REACTOR = new BlockPos(1, 2, 1);
    private static final long AMOUNT = 1_000_000L;

    private ChemicalReactorCatalystGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 200)
    public static void copperCatalysesNitrileHydrogenationWithoutBeingNamedByTheRule(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, "CC#N");
        reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, copperIngot());
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(VialContentsState.fromStack(reactor.getItem(0)).isEmpty(),
                "nitrile was not consumed");
            helper.assertTrue(VialContentsState.fromStack(reactor.getItem(1)).isEmpty(),
                "hydrogen was not consumed");
            SubstanceContents product = VialContentsState.fromStack(reactor.getItem(3)).substance();
            helper.assertTrue(product != null
                    && product.canonicalKey().equals(key("CC=N"))
                    && product.micromoles() == AMOUNT,
                "imine product missing or not stoichiometric");
            helper.assertTrue(VialContentsState.fromStack(reactor.getItem(4)).isEmpty(),
                "second output slot was used by a one-product rule");
            helper.assertTrue(reactor.lastCompletedRule().equals("chemmod:nitrile_hydrogenation"),
                "unexpected rule completed: " + reactor.lastCompletedRule());
            helper.assertTrue(!reactor.getItem(ChemicalReactorBlockEntity.CATALYST_SLOT).isEmpty(),
                "the catalyst was consumed");
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 200)
    public static void nitrileWithoutAMetalCatalystAsksForOneInsteadOfReacting(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = setup(helper, "CC#N");
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(reactor.reactionProgress() == 0.0 && reactor.lastCompletedRule().isEmpty(),
                "a capability-gated rule ran without any catalyst");
            helper.assertTrue(ChemicalReactorBlockEntity.statusComponent(reactor.menuData().get(1))
                    .equals(Component.translatable("reactor_status.chemmod.needs_catalyst")),
                "reactor did not report a missing catalyst");
            SubstanceContents untouched = VialContentsState.fromStack(reactor.getItem(0)).substance();
            helper.assertTrue(untouched != null
                    && untouched.canonicalKey().equals(key("CC#N"))
                    && untouched.micromoles() == AMOUNT,
                "refusal changed the physical target");
            helper.assertTrue(VialContentsState.fromStack(reactor.getItem(3)).isEmpty(),
                "refusal still produced a product");
            helper.succeed();
        });
    }

    private static ChemicalReactorBlockEntity setup(GameTestHelper helper, String target) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(REACTOR);
        level.setBlock(pos.below(), Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true), 3);
        level.setBlock(pos, ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), 3);
        ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) level.getBlockEntity(pos);
        reactor.setItem(0, vial(target, AMOUNT));
        reactor.setItem(1, vial("[H][H]", AMOUNT));
        reactor.setItem(3, new ItemStack(ChemItems.SUBSTANCE_VIAL.get()));
        reactor.setItem(4, new ItemStack(ChemItems.SUBSTANCE_VIAL.get()));
        return reactor;
    }

    private static ItemStack vial(String structure, long amount) {
        ItemStack stack = new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
        stack.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, amount, 1_000_000));
        return stack;
    }

    private static ItemStack copperIngot() {
        return new ItemStack(BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot")));
    }

    private static String key(String structure) {
        return new SubstanceContents(structure, 1L, 1_000_000).canonicalKey();
    }
}
