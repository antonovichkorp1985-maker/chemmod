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
import net.minecraft.gametest.framework.GameTestHolder;
import net.minecraft.gametest.framework.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
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

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 80)
    public static void ethanolBatchProducesSeparateProductsAndRetainsCatalyst(GameTestHelper helper) {
        var level = helper.getLevel();
        level.setBlock(
            helper.absolutePos(HEAT_SOURCE),
            Blocks.BLAST_FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true),
            3
        );
        level.setBlock(helper.absolutePos(REACTOR), ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), 3);

        BlockEntity blockEntity = level.getBlockEntity(helper.absolutePos(REACTOR));
        helper.assertTrue(blockEntity instanceof ChemicalReactorBlockEntity, "chemical reactor block entity was not created");
        ChemicalReactorBlockEntity reactor = (ChemicalReactorBlockEntity) blockEntity;
        reactor.setItem(ChemicalReactorBlockEntity.TARGET_SLOT, filledVial("CCO"));
        reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemStack(
            BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot"))
        ));
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, emptyVial());
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT, emptyVial());

        // At 900 K the bundled Arrhenius model completes within the first
        // one-second reactor tick. The delay also leaves time for an arbitrary
        // test-server game-time phase before that tick.
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT)
                .get(ChemComponents.SUBSTANCE.get()) == null, "ethanol input was not consumed");
            helper.assertTrue(
                reactor.getItem(ChemicalReactorBlockEntity.CATALYST_SLOT).is(
                    BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot"))
                ),
                "copper catalyst was not retained"
            );

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
