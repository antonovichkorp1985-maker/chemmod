package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.discovery.DiscoverySavedData;
import io.github.antonovichkorp.chemmod.discovery.ReactorDiscoveryView;
import io.github.antonovichkorp.chemmod.network.ReactorDiscoveryPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Server-authoritative six-slot view of a Chemical Reactor. Unlike a generic
 * chest menu, every slot has an explicit placement predicate, so a client can
 * never turn unrelated inventory items into a hidden reactor input.
 */
public final class ChemicalReactorMenu extends AbstractContainerMenu {
    private static final int MACHINE_SLOT_COUNT = ChemicalReactorBlockEntity.SLOT_COUNT;
    private static final int PLAYER_SLOT_START = MACHINE_SLOT_COUNT;
    private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36;
    private static final int PROGRESS_SCALE = 1_000;

    private final Container reactorInventory;
    private final ServerPlayer serverViewer;
    private List<ReactorDiscoveryPayload.Entry> discoverySnapshot = List.of();
    private List<ReactorDiscoveryPayload.Entry> lastSentSnapshot;
    private long lastSentTime = Long.MIN_VALUE;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Client constructor: menu slots receive their authoritative stacks through vanilla synchronization. */
    public ChemicalReactorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(
            containerId,
            playerInventory,
            new SimpleContainer(MACHINE_SLOT_COUNT),
            new SimpleContainerData(3),
            ContainerLevelAccess.NULL,
            null
        );
        // The client does not read the block entity directly, but consuming the
        // position keeps this factory's wire format explicit and extensible.
        extraData.readBlockPos();
    }

    /** Server constructor bound to the persistent reactor inventory and progress data. */
    public ChemicalReactorMenu(int containerId, Inventory playerInventory, ChemicalReactorBlockEntity reactor) {
        this(
            containerId,
            playerInventory,
            reactor,
            reactor.menuData(),
            ContainerLevelAccess.create(reactor.getLevel(), reactor.getBlockPos()),
            playerInventory.player instanceof ServerPlayer player ? player : null
        );
    }

    private ChemicalReactorMenu(
        int containerId,
        Inventory playerInventory,
        Container reactor,
        ContainerData data,
        ContainerLevelAccess access,
        ServerPlayer serverViewer
    ) {
        super(ChemMenus.CHEMICAL_REACTOR.get(), containerId);
        checkContainerSize(reactor, MACHINE_SLOT_COUNT);
        checkContainerDataCount(data, 3);
        this.data = data;
        this.access = access;
        this.reactorInventory = reactor;
        this.serverViewer = serverViewer;

        // Target, co-reactants, product vials, then reusable catalyst.
        addSlot(new ReactorSlot(reactor, ChemicalReactorBlockEntity.TARGET_SLOT, 26, 34));
        addSlot(new ReactorSlot(reactor, ChemicalReactorBlockEntity.CO_REACTANT_FIRST_SLOT, 48, 34));
        addSlot(new ReactorSlot(reactor, ChemicalReactorBlockEntity.CO_REACTANT_SECOND_SLOT, 66, 34));
        addSlot(new ReactorSlot(reactor, ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT, 110, 34));
        addSlot(new ReactorSlot(reactor, ChemicalReactorBlockEntity.OUTPUT_SECOND_SLOT, 128, 34));
        addSlot(new ReactorSlot(reactor, ChemicalReactorBlockEntity.CATALYST_SLOT, 150, 34));

        addPlayerInventory(playerInventory);
        addDataSlots(data);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (serverViewer == null) return;
        List<ReactorDiscoveryPayload.Entry> current = ReactorDiscoveryView.capture(
            reactorInventory, DiscoverySavedData.get(serverViewer.getServer()));
        long time = serverViewer.level().getGameTime();
        // Changed facts are sent immediately; a small one-second heartbeat also
        // resynchronizes initial menu opening without any client query channel.
        if (!current.equals(lastSentSnapshot) || time - lastSentTime >= 20L) {
            PacketDistributor.sendToPlayer(serverViewer, new ReactorDiscoveryPayload(containerId, current));
            lastSentSnapshot = current;
            lastSentTime = time;
        }
    }

    /** Only the client view of the addressed menu accepts this server-owned projection. */
    public void acceptDiscoverySnapshot(ReactorDiscoveryPayload payload) {
        if (serverViewer == null && payload.containerId() == containerId) {
            discoverySnapshot = payload.entries();
        }
    }

    /** Refuse stale facts when vanilla slot synchronization changes a vial's identity. */
    public Optional<ReactorDiscoveryPayload.Entry> discoveryForOutput(int outputIndex) {
        if (outputIndex < 0 || outputIndex >= 2 || discoverySnapshot.size() != 2) return Optional.empty();
        SubstanceContents contents = ReactorDiscoveryView.singleContents(
            reactorInventory.getItem(ChemicalReactorBlockEntity.OUTPUT_FIRST_SLOT + outputIndex));
        if (contents == null) return Optional.empty();
        ReactorDiscoveryPayload.Entry entry = discoverySnapshot.get(outputIndex);
        return entry.canonicalKey().equals(contents.canonicalKey()) ? Optional.of(entry) : Optional.empty();
    }

    public int progressPermille() {
        return Math.max(0, Math.min(PROGRESS_SCALE, data.get(0)));
    }

    public int statusCode() {
        return data.get(1);
    }

    public int ruleDisplayIndex() {
        return data.get(2);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ChemBlocks.CHEMICAL_REACTOR.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int quickMovedSlotIndex) {
        ItemStack quickMoved = ItemStack.EMPTY;
        if (quickMovedSlotIndex < 0 || quickMovedSlotIndex >= slots.size()) return quickMoved;
        Slot slot = slots.get(quickMovedSlotIndex);
        if (!slot.hasItem()) return quickMoved;

        ItemStack raw = slot.getItem();
        quickMoved = raw.copy();
        if (quickMovedSlotIndex < MACHINE_SLOT_COUNT) {
            if (!moveItemStackTo(raw, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(raw, 0, MACHINE_SLOT_COUNT, false)) {
            if (quickMovedSlotIndex < PLAYER_SLOT_START + 27) {
                if (!moveItemStackTo(raw, PLAYER_SLOT_START + 27, PLAYER_SLOT_END, false)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(raw, PLAYER_SLOT_START, PLAYER_SLOT_START + 27, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (raw.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, raw);
        return quickMoved;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 198));
        }
    }

    private static final class ReactorSlot extends Slot {
        private ReactorSlot(Container reactor, int index, int x, int y) {
            super(reactor, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getSlotIndex(), stack);
        }
    }
}
