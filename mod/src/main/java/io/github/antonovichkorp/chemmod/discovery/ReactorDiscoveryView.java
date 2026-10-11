package io.github.antonovichkorp.chemmod.discovery;

import io.github.antonovichkorp.chemmod.content.ChemItems;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlockEntity;
import io.github.antonovichkorp.chemmod.content.SubstanceContents;
import io.github.antonovichkorp.chemmod.content.VialContentsState;
import io.github.antonovichkorp.chemmod.network.ReactorDiscoveryPayload.Entry;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Pure projection of existing server records onto physical outputs. Reading never records synthesis. */
public final class ReactorDiscoveryView {
    private ReactorDiscoveryView() {}

    public static List<Entry> capture(Container reactor, DiscoverySavedData ledger) {
        return List.of(entry(reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT), ledger),
            entry(reactor.getItem(ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT), ledger));
    }

    public static SubstanceContents singleContents(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ChemItems.SUBSTANCE_VIAL.get()) || stack.getCount() != 1) return null;
        try {
            VialContentsState state = VialContentsState.fromStack(stack);
            return state.mixture() == null ? state.substance() : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static Entry entry(ItemStack stack, DiscoverySavedData ledger) {
        SubstanceContents contents = singleContents(stack);
        if (contents == null) return Entry.EMPTY;
        String key = contents.canonicalKey();
        var record = ledger.find(key);
        if (record == null) return new Entry(key, "", 0L);
        // Actual Minecraft player names fit comfortably. Bound imported legacy
        // metadata for the wire without changing the persisted discovery record.
        String author = record.getDiscoverer().strip();
        if (author.length() > 256) author = author.substring(0, author.offsetByCodePoints(0, 128));
        return new Entry(key, author, record.getDiscoveredAt().toEpochMilli());
    }
}
