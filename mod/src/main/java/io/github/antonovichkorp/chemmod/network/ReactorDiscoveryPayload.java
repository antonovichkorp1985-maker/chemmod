package io.github.antonovichkorp.chemmod.network;

import io.github.antonovichkorp.chemmod.ChemMod;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server-to-client, read-only snapshot of exactly two output slots; never a discovery write request. */
public record ReactorDiscoveryPayload(int containerId, List<Entry> entries) implements CustomPacketPayload {
    public static final Type<ReactorDiscoveryPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(ChemMod.MOD_ID, "reactor_discovery"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ReactorDiscoveryPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> {
            buffer.writeVarInt(payload.containerId());
            // Fixed arity avoids unbounded collection allocation from the wire.
            for (Entry entry : payload.entries()) {
                buffer.writeUtf(entry.canonicalKey(), 64);
                buffer.writeUtf(entry.discoverer(), 256);
                buffer.writeLong(entry.epochMillis());
            }
        },
        buffer -> new ReactorDiscoveryPayload(buffer.readVarInt(), List.of(readEntry(buffer), readEntry(buffer)))
    );

    public ReactorDiscoveryPayload {
        if (containerId < 0) throw new IllegalArgumentException("Negative reactor menu id");
        entries = List.copyOf(entries);
        if (entries.size() != 2) throw new IllegalArgumentException("Exactly two reactor outputs are required");
    }

    private static Entry readEntry(RegistryFriendlyByteBuf buffer) {
        return new Entry(buffer.readUtf(64), buffer.readUtf(256), buffer.readLong());
    }

    @Override
    public Type<ReactorDiscoveryPayload> type() {
        return TYPE;
    }

    public static void handle(ReactorDiscoveryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ChemicalReactorMenu menu) {
                menu.acceptDiscoverySnapshot(payload);
            }
        });
    }

    /** Empty key = no single molecular content; empty author = no registered first synthesis. */
    public record Entry(String canonicalKey, String discoverer, long epochMillis) {
        public static final Entry EMPTY = new Entry("", "", 0L);

        public Entry {
            if (canonicalKey == null || (!canonicalKey.isEmpty() && !canonicalKey.matches("[0-9a-f]{64}"))) {
                throw new IllegalArgumentException("Invalid discovery key");
            }
            if (discoverer == null || discoverer.length() > 256
                || (!discoverer.isEmpty() && discoverer.isBlank())) {
                throw new IllegalArgumentException("Invalid discovery display name");
            }
            if (canonicalKey.isEmpty() && !discoverer.isEmpty()) {
                throw new IllegalArgumentException("Discovery needs a molecular key");
            }
            if (discoverer.isEmpty() && epochMillis != 0L) {
                throw new IllegalArgumentException("Undiscovered content cannot have a synthesis date");
            }
        }

        public boolean discovered() {
            return !discoverer.isEmpty();
        }
    }
}
