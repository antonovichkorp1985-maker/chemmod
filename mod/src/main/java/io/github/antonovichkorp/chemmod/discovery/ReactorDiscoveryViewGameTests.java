package io.github.antonovichkorp.chemmod.discovery;

import com.mojang.authlib.GameProfile;
import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemComponents;
import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlockEntity;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorMenu;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import io.github.antonovichkorp.chemmod.content.VialContentsState;
import io.github.antonovichkorp.chemmod.content.VialMixingTransaction;
import io.github.antonovichkorp.chemmod.network.ReactorDiscoveryPayload;
import io.github.antonovichkorp.chemmod.network.ReactorDiscoveryPayload.Entry;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Projection, wire codec and menu-scope tests. These do not claim a real client networking run. */
@GameTestHolder(ChemMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReactorDiscoveryViewGameTests {
    private ReactorDiscoveryViewGameTests() {}

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void readingOutputDiscoveryNeverCreatesOrDirtiesFacts(GameTestHelper helper) {
        DiscoverySavedData ledger = DiscoverySavedData.load(new CompoundTag(), helper.getLevel().registryAccess());
        SimpleContainer inventory = new SimpleContainer(ChemicalReactorBlockEntity.SLOT_COUNT);
        inventory.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, vial("O"));
        var initial = ReactorDiscoveryView.capture(inventory, ledger);
        helper.assertTrue(initial.getFirst().canonicalKey().equals(key("O")) && !initial.getFirst().discovered()
            && initial.get(1).equals(Entry.EMPTY), "unregistered vial was misrepresented");
        helper.assertTrue(ledger.records().isEmpty() && !ledger.isDirty(), "opening a view awarded discovery");

        Instant when = Instant.parse("2026-10-09T12:34:56.789Z");
        ledger.recordSynthesis(new SubstanceContents("O", 1, 1_000_000).molecule(), "Исследователь", when, null);
        ledger.setDirty(false);
        var records = ledger.records();
        var known = ReactorDiscoveryView.capture(inventory, ledger);
        helper.assertTrue(known.getFirst().discovered() && known.getFirst().discoverer().equals("Исследователь")
            && known.getFirst().epochMillis() == when.toEpochMilli(), "projection lost first-synthesis facts");
        helper.assertTrue(ledger.records().equals(records) && !ledger.isDirty(), "reading existing facts dirtied the ledger");

        inventory.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, vial("CCO"));
        helper.assertTrue(!ReactorDiscoveryView.capture(inventory, ledger).getFirst().discovered(),
            "replaced vial inherited the previous substance's discovery");
        ItemStack mixture = new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
        VialMixingTransaction.combine(VialContentsState.fromStack(vial("CCO")),
            VialContentsState.fromStack(vial("O"))).applyTo(mixture);
        inventory.setItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, mixture);
        helper.assertTrue(ReactorDiscoveryView.capture(inventory, ledger).getFirst().equals(Entry.EMPTY),
            "mixture was falsely displayed as a single discovered molecule");
        helper.assertTrue(ledger.records().equals(records) && !ledger.isDirty(), "mixture inspection created discovery");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void discoveryWireRoundTripAndMenuScopeRejectStaleFacts(GameTestHelper helper) {
        ReactorDiscoveryPayload payload = new ReactorDiscoveryPayload(12,
            List.of(new Entry(key("O"), "Исследователь", 123456789L), Entry.EMPTY));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ReactorDiscoveryPayload.STREAM_CODEC.encode(buffer, payload);
            ReactorDiscoveryPayload decoded = ReactorDiscoveryPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(payload.equals(decoded) && !buffer.isReadable(), "discovery packet failed a complete round trip");
        } finally {
            buffer.release();
        }
        var viewer = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
            UUID.fromString("45e5626b-77dc-4f8c-a18b-1fe1e27c1bb2"), "ChemViewTest"));
        RegistryFriendlyByteBuf opening = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ChemicalReactorMenu clientView;
        try {
            opening.writeBlockPos(BlockPos.ZERO);
            // Explicitly exercise the client menu constructor with its independent
            // SimpleContainer, without loading any net.minecraft.client classes.
            clientView = new ChemicalReactorMenu(12, viewer.getInventory(), opening);
        } finally {
            opening.release();
        }
        clientView.getSlot(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT).set(vial("O"));
        clientView.acceptDiscoverySnapshot(new ReactorDiscoveryPayload(13, payload.entries()));
        helper.assertTrue(clientView.discoveryForOutput(0).isEmpty(), "packet for a different menu was accepted");
        clientView.acceptDiscoverySnapshot(payload);
        helper.assertTrue(clientView.discoveryForOutput(0).orElseThrow().equals(payload.entries().getFirst()),
            "matching output did not receive its discovery facts");
        clientView.getSlot(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT).set(vial("CCO"));
        helper.assertTrue(clientView.discoveryForOutput(0).isEmpty(), "new molecular identity displayed stale discovery");
        clientView.acceptDiscoverySnapshot(new ReactorDiscoveryPayload(12,
            List.of(new Entry(key("CCO"), "", 0L), Entry.EMPTY)));
        helper.assertTrue(!clientView.discoveryForOutput(0).orElseThrow().discovered(),
            "server's explicit unregistered state was not displayed");
        helper.assertTrue(clientView.discoveryForOutput(-1).isEmpty() && clientView.discoveryForOutput(2).isEmpty(),
            "out-of-range output index accepted");
        helper.succeed();
    }

    @GameTest(template = "chemical_reactor_batch", timeoutTicks = 20)
    public static void discoveryPayloadRejectsInvalidIdentityAndUnboundedFields(GameTestHelper helper) {
        assertRejected(helper, () -> new Entry("not-a-canonical-key", "", 0L));
        assertRejected(helper, () -> new Entry("", "Someone", 0L));
        assertRejected(helper, () -> new Entry(key("O"), "", 42L));
        assertRejected(helper, () -> new Entry(key("O"), "x".repeat(257), 0L));
        assertRejected(helper, () -> new ReactorDiscoveryPayload(-1, List.of(Entry.EMPTY, Entry.EMPTY)));
        assertRejected(helper, () -> new ReactorDiscoveryPayload(1, List.of(Entry.EMPTY)));
        assertRejected(helper, () -> new ReactorDiscoveryPayload(1, List.of(Entry.EMPTY, Entry.EMPTY, Entry.EMPTY)));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            buffer.writeVarInt(1);
            buffer.writeUtf("x".repeat(65));
            boolean rejected = false;
            try {
                ReactorDiscoveryPayload.STREAM_CODEC.decode(buffer);
            } catch (RuntimeException invalidWire) {
                rejected = true;
            }
            helper.assertTrue(rejected, "oversized wire identity was accepted");
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, Runnable constructor) {
        boolean rejected = false;
        try {
            constructor.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "invalid discovery payload accepted");
    }

    private static ItemStack vial(String structure) {
        ItemStack stack = new ItemStack(ChemItems.SUBSTANCE_VIAL.get());
        stack.set(ChemComponents.SUBSTANCE.get(), new SubstanceContents(structure, 1_000_000L, 1_000_000));
        return stack;
    }

    private static String key(String structure) {
        return new SubstanceContents(structure, 1, 1_000_000).canonicalKey();
    }
}
