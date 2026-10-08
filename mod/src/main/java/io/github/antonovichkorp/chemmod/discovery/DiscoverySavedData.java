package io.github.antonovichkorp.chemmod.discovery;

import io.github.antonovichkorp.chemmod.core.Molecule;
import io.github.antonovichkorp.chemmod.core.discovery.DiscoveryRecord;
import io.github.antonovichkorp.chemmod.core.discovery.DiscoveryRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft persistence boundary for the pure-core discovery ledger.
 *
 * The ledger is deliberately attached to the overworld's data storage so a
 * server has one world-wide discovery history rather than divergent records for
 * every dimension. No command or item creation writes it: only a future real
 * synthesis machine may call {@link #recordSynthesis}.
 */
public final class DiscoverySavedData extends SavedData {
    private static final String DATA_FILE = "chemmod_discoveries";
    private static final int CURRENT_SCHEMA = 1;
    private static final String RECORDS_KEY = "records";
    private static final String SCHEMA_KEY = "schema_version";

    private static final SavedData.Factory<DiscoverySavedData> FACTORY = new SavedData.Factory<>(
        DiscoverySavedData::new,
        DiscoverySavedData::load
    );

    private final DiscoveryRegistry ledger;

    private DiscoverySavedData() {
        this(new DiscoveryRegistry());
    }

    private DiscoverySavedData(DiscoveryRegistry ledger) {
        this.ledger = ledger;
    }

    /** Get the single discovery ledger for a server world. */
    public static DiscoverySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_FILE);
    }

    /**
     * Store first-synthesis facts if this is a newly discovered graph. Replays
     * intentionally preserve the existing record and do not dirty the save.
     */
    public synchronized DiscoveryRecord recordSynthesis(
        Molecule molecule,
        String discoverer,
        Instant discoveredAt,
        String trivialName
    ) {
        boolean firstDiscovery = ledger.find(molecule.canonicalKey()) == null;
        DiscoveryRecord record = ledger.recordSynthesis(molecule, discoverer, discoveredAt, trivialName);
        if (firstDiscovery) setDirty();
        return record;
    }

    public synchronized DiscoveryRecord find(String canonicalKey) {
        return ledger.find(canonicalKey);
    }

    public synchronized List<DiscoveryRecord> records() {
        return List.copyOf(ledger.records());
    }

    private static DiscoverySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        int schema = tag.contains(SCHEMA_KEY, Tag.TAG_INT) ? tag.getInt(SCHEMA_KEY) : 1;
        if (schema != CURRENT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported ChemMod discovery schema: " + schema);
        }

        List<DiscoveryRecord> records = new ArrayList<>();
        ListTag serialized = tag.getList(RECORDS_KEY, Tag.TAG_COMPOUND);
        for (Tag rawRecord : serialized) {
            CompoundTag record = (CompoundTag) rawRecord;
            String trivialName = record.contains("trivial_name", Tag.TAG_STRING)
                ? record.getString("trivial_name")
                : null;
            records.add(new DiscoveryRecord(
                record.getString("canonical_key"),
                record.getString("discoverer"),
                Instant.ofEpochMilli(record.getLong("discovered_at_epoch_millis")),
                trivialName
            ));
        }
        return new DiscoverySavedData(new DiscoveryRegistry(records));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(SCHEMA_KEY, CURRENT_SCHEMA);
        ListTag serialized = new ListTag();
        for (DiscoveryRecord record : ledger.records()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("canonical_key", record.getCanonicalKey());
            entry.putString("discoverer", record.getDiscoverer());
            entry.putLong("discovered_at_epoch_millis", record.getDiscoveredAt().toEpochMilli());
            if (record.getTrivialName() != null) entry.putString("trivial_name", record.getTrivialName());
            serialized.add(entry);
        }
        tag.put(RECORDS_KEY, serialized);
        return tag;
    }
}
