package io.github.antonovichkorp.chemmod.discovery;

import com.mojang.authlib.GameProfile;
import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemBlocks;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlockEntity;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import io.github.antonovichkorp.chemmod.content.VialContentsState;
import io.github.antonovichkorp.chemmod.core.discovery.DiscoveryRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Headless attribution/codec checks; fake players do not substitute for two real clients. */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiscoveryGameTests {
    private static final BlockPos HEAT = new BlockPos(1, 1, 1);
    private static final BlockPos REACTOR = new BlockPos(1, 2, 1);
    private static final long AMOUNT = 1_000_000L;

    private DiscoveryGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 180)
    public static void onlyCommittedSynthesisAwardsOriginalOperatorAcrossReload(GameTestHelper helper) {
        var level = helper.getLevel();
        DiscoverySavedData ledger = DiscoverySavedData.get(level.getServer());
        FakePlayer initiator = FakePlayerFactory.get(level,
            new GameProfile(UUID.fromString("edb8e5c0-b881-4bda-b74b-82637983c6de"), "ChemInitiator"));
        FakePlayer observer = FakePlayerFactory.get(level,
            new GameProfile(UUID.fromString("b67c6767-e23e-4933-a731-82f8a9bbd345"), "ChemObserver"));
        // The other reactor tests have no operator, so do not write discovery.
        // Explicitly require a fresh GameTest world rather than erasing history.
        for (String structure : List.of("CC=O", "[H][H]")) {
            helper.assertTrue(ledger.find(key(structure)) == null, "use a fresh GameTest world for first-synthesis checks");
        }
        List<DiscoveryRecord> before = ledger.records();
        // Exercise the real sneak-use mixing item path, not a planner shortcut.
        initiator.setItemInHand(InteractionHand.MAIN_HAND, vial("CCO"));
        initiator.setItemInHand(InteractionHand.OFF_HAND, vial("O"));
        initiator.setShiftKeyDown(true);
        try {
            ChemItems.SUBSTANCE_VIAL.get().use(level, initiator, InteractionHand.MAIN_HAND);
            helper.assertTrue(VialContentsState.fromStack(initiator.getMainHandItem()).mixture() != null,
                "physical hand mixing did not produce an explicit mixture");
            helper.assertTrue(ledger.records().equals(before), "hand mixing awarded discovery");
        } finally {
            initiator.setShiftKeyDown(false);
            initiator.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            initiator.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }

        level.setBlock(helper.absolutePos(HEAT), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(helper.absolutePos(REACTOR), ChemBlocks.CHEMICAL_REACTOR.get().defaultBlockState(), 3);
        ChemicalReactorBlockEntity initial = reactor(helper);
        initial.setLastOperator(initiator); // same hook used by placement/opening
        supplyBatch(initial);
        helper.assertTrue(ledger.records().equals(before), "creating samples or selecting an operator awarded discovery");
        DiscoveryRecord[] first = new DiscoveryRecord[2];
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(initial.reactionProgress() == 0.0, "unheated reactor progressed");
            helper.assertTrue(ledger.records().equals(before), "refused reaction awarded discovery");
            setHeat(helper, false);
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(initial.reactionProgress() > 0.0 && initial.reactionProgress() < 1.0,
                "expected an unfinished physical batch");
            helper.assertTrue(ledger.records().equals(before), "partial progress awarded discovery before commit");
            ItemStack removed = initial.removeItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, 1);
            helper.assertTrue(!removed.isEmpty() && initial.reactionProgress() == 0.0,
                "removing an output failed to cancel the batch");
        });
        helper.runAfterDelay(75, () -> {
            helper.assertTrue(initial.reactionProgress() == 0.0, "canceled batch progressed without an output");
            helper.assertTrue(ledger.records().equals(before), "canceled first synthesis awarded discovery");
            helper.assertTrue(VialContentsState.fromStack(initial.getItem(ChemicalReactorBlockEntity.TARGET_SLOT))
                .substance().micromoles() == AMOUNT, "cancellation consumed source material");
            initial.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, emptyVial());
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(initial.reactionProgress() > 0.0 && initial.reactionProgress() < 1.0,
                "restored output did not allow a new partial batch");
            helper.assertTrue(ledger.records().equals(before), "restarted partial batch awarded discovery");
            initial.setLastOperator(observer); // another player looks at an already-running batch
            CompoundTag saved = initial.saveWithoutMetadata(level.registryAccess());
            helper.assertTrue(saved.getString("processing_operator").equals("ChemInitiator"),
                "observer stole attribution before saving");
            ChemicalReactorBlockEntity restored = new ChemicalReactorBlockEntity(
                helper.absolutePos(REACTOR), level.getBlockState(helper.absolutePos(REACTOR)));
            restored.loadWithComponents(saved, level.registryAccess());
            level.removeBlockEntity(helper.absolutePos(REACTOR));
            level.setBlockEntity(restored);
            setHeat(helper, true);
        });
        helper.runAfterDelay(125, () -> {
            assertExactProducts(helper);
            first[0] = ledger.find(key("CC=O"));
            first[1] = ledger.find(key("[H][H]"));
            for (DiscoveryRecord record : first) {
                helper.assertTrue(record != null && record.getDiscoverer().equals("ChemInitiator"),
                    "committed products were not attributed to the original operator after reload");
            }
            helper.assertTrue(ledger.isDirty(), "first synthesis did not mark SavedData dirty");
            assertLedgerRoundTrip(helper, ledger);
            // A later batch by another player must not overwrite first-discovery facts.
            ChemicalReactorBlockEntity current = reactor(helper);
            current.setLastOperator(observer);
            supplyBatch(current);
        });
        helper.runAfterDelay(150, () -> {
            assertExactProducts(helper);
            assertFirstRecords(helper, ledger, first);
            assertLedgerRoundTrip(helper, ledger);
            helper.succeed();
        });
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void savedLedgerRoundTripPreservesFactsAndDoesNotDirtyOnReplay(GameTestHelper helper) {
        // Isolated serializer test: deliberately does NOT insert synthetic facts
        // into the world's ledger or purport to be a physical discovery test.
        DiscoverySavedData isolated = DiscoverySavedData.load(new CompoundTag(), helper.getLevel().registryAccess());
        SubstanceContents ethanol = new SubstanceContents("CCO", AMOUNT, 1_000_000);
        Instant timestamp = Instant.parse("2026-10-09T10:11:12.345Z");
        isolated.recordSynthesis(ethanol.molecule(), "Первый исследователь", timestamp, "Пробный спирт");
        helper.assertTrue(isolated.isDirty(), "first record must mark the ledger dirty");
        DiscoverySavedData restored = roundTrip(helper, isolated);
        helper.assertTrue(!restored.isDirty(), "loading a ledger dirtied it");
        DiscoveryRecord record = restored.find(ethanol.canonicalKey());
        helper.assertTrue(record != null && record.getDiscoverer().equals("Первый исследователь")
            && record.getDiscoveredAt().equals(timestamp) && "Пробный спирт".equals(record.getTrivialName()),
            "saved discovery lost author, timestamp or optional name");
        DiscoveryRecord replay = restored.recordSynthesis(ethanol.molecule(), "Другой игрок",
            timestamp.plusSeconds(60), "Другое имя");
        helper.assertTrue(replay.equals(record) && restored.records().size() == 1 && !restored.isDirty(),
            "repeat synthesis overwrote the first record or dirtied the save");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void incompatibleOrDuplicateLedgerDataIsRejectedInsteadOfErased(GameTestHelper helper) {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 999);
        assertRejected(helper, future);
        DiscoverySavedData isolated = DiscoverySavedData.load(new CompoundTag(), helper.getLevel().registryAccess());
        isolated.recordSynthesis(new SubstanceContents("O", 1, 1_000_000).molecule(),
            "CodecTest", Instant.EPOCH, null);
        CompoundTag duplicate = isolated.save(new CompoundTag(), helper.getLevel().registryAccess());
        var records = duplicate.getList("records", Tag.TAG_COMPOUND);
        records.add(records.getCompound(0).copy());
        assertRejected(helper, duplicate);
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, CompoundTag tag) {
        boolean rejected = false;
        try {
            DiscoverySavedData.load(tag, helper.getLevel().registryAccess());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "invalid ledger silently loaded or discarded history");
    }

    private static DiscoverySavedData roundTrip(GameTestHelper helper, DiscoverySavedData ledger) {
        return DiscoverySavedData.load(ledger.save(new CompoundTag(), helper.getLevel().registryAccess()),
            helper.getLevel().registryAccess());
    }

    private static void assertLedgerRoundTrip(GameTestHelper helper, DiscoverySavedData ledger) {
        CompoundTag saved = ledger.save(new CompoundTag(), helper.getLevel().registryAccess());
        DiscoverySavedData restored = DiscoverySavedData.load(saved, helper.getLevel().registryAccess());
        helper.assertTrue(saved.equals(restored.save(new CompoundTag(), helper.getLevel().registryAccess())),
            "discovery NBT changed across its production save/load codec");
    }

    private static void assertFirstRecords(GameTestHelper helper, DiscoverySavedData ledger, DiscoveryRecord[] first) {
        for (DiscoveryRecord record : first) {
            helper.assertTrue(record.equals(ledger.find(record.getCanonicalKey())), "first-discovery metadata was overwritten");
        }
    }

    private static void setHeat(GameTestHelper helper, boolean fast) {
        helper.getLevel().setBlock(helper.absolutePos(HEAT),
            (fast ? Blocks.BLAST_FURNACE : Blocks.FURNACE).defaultBlockState()
                .setValue(AbstractFurnaceBlock.LIT, true), 3);
    }

    private static ChemicalReactorBlockEntity reactor(GameTestHelper helper) {
        return (ChemicalReactorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(REACTOR));
    }

    private static void supplyBatch(ChemicalReactorBlockEntity reactor) {
        reactor.setItem(ChemicalReactorBlockEntity.TARGET_SLOT, vial("CCO"));
        reactor.setItem(ChemicalReactorBlockEntity.CATALYST_SLOT, new ItemStack(BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "copper_ingot"))));
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, emptyVial());
        reactor.setItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT, emptyVial());
    }

    private static ItemStack emptyVial() {
        return new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
    }

    private static ItemStack vial(String structure) {
        ItemStack vial = emptyVial();
        vial.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, AMOUNT, 1_000_000));
        return vial;
    }

    private static String key(String structure) {
        return new SubstanceContents(structure, 1, 1_000_000).canonicalKey();
    }

    private static void assertExactProducts(GameTestHelper helper) {
        ChemicalReactorBlockEntity reactor = reactor(helper);
        helper.assertTrue(VialContentsState.fromStack(reactor.getItem(ChemicalReactorBlockEntity.TARGET_SLOT)).isEmpty(),
            "reaction did not consume the physical target");
        java.util.Set<String> products = new java.util.HashSet<>();
        for (int slot = ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT; slot <= ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT; slot++) {
            SubstanceContents contents = VialContentsState.fromStack(reactor.getItem(slot)).substance();
            helper.assertTrue(contents != null && contents.micromoles() == AMOUNT, "missing physical product or incorrect amount");
            products.add(contents.canonicalKey());
        }
        helper.assertTrue(products.equals(java.util.Set.of(key("CC=O"), key("[H][H]"))), "unexpected reaction products");
    }
}
