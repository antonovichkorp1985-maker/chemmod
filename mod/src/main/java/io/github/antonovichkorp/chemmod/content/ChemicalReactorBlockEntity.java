package io.github.antonovichkorp.chemmod.content;

import io.github.antonovichkorp.chemmod.core.reaction.ReactionEnvironment;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionKinetics;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRule;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleId;
import io.github.antonovichkorp.chemmod.core.reaction.ReactionRuleSet;
import io.github.antonovichkorp.chemmod.discovery.DiscoverySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The first real chemistry machine. It owns physical vial/catalyst slots,
 * evaluates the data-driven reaction rules against its actual heat and catalyst
 * state once a second, and performs a checked {@link VialReactionTransaction}
 * only after enough Arrhenius progress has accumulated.
 *
 * <p>The first machine intentionally has room for the bundled M3-scale routes:
 * one target vial, up to two co-reactant vials, up to two product vials, and
 * one reusable catalyst. It refuses an ambiguous rule match instead of making
 * an undocumented recipe choice. Mixture routes and higher-arity equipment can
 * use the same transaction boundary later without weakening this machine's
 * physical inventory invariants.</p>
 */
public final class ChemicalReactorBlockEntity extends BlockEntity implements Container {
    public static final int TARGET_SLOT = 0;
    public static final int CO_REACTANT_FIRST_SLOT = 1;
    public static final int CO_REACTANT_SECOND_SLOT = 2;
    public static final int OUTPUT_FIRST_SLOT = 3;
    public static final int OUTPUT_SECOND_SLOT = 4;
    public static final int CATALYST_SLOT = 5;
    public static final int SLOT_COUNT = 6;
    private static final double ATMOSPHERIC_PRESSURE_KILOPASCALS = 101.325;

    private static final String ITEMS_KEY = "items";
    private static final String PROGRESS_KEY = "reaction_progress";
    private static final String OPERATOR_KEY = "last_operator";
    private static final String PROCESSING_OPERATOR_KEY = "processing_operator";
    private static final String LAST_RULE_KEY = "last_completed_rule";
    private static final String LAST_RULE_NAME_KEY = "last_completed_rule_name";

    private static final ReactionRuleSet RULE_SET = ReactionRuleSet.bundled();
    private static final PureSubstanceReactionPlanner PLANNER = PureSubstanceReactionPlanner.bundled();
    private static final List<ReactionRule> TIMED_RULES = RULE_SET.getRules().values().stream()
        .filter(rule -> rule.getKinetics() != null)
        .sorted(Comparator.comparing(rule -> rule.getId().getValue()))
        .toList();
    private static final List<String> CATALYST_TAG_IDS = TIMED_RULES.stream()
        .flatMap(rule -> rule.getConditions().getCatalystTags().stream())
        .distinct()
        .sorted()
        .toList();

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private double reactionProgress;
    private OperationSignature progressingOperation;
    private String lastOperator = "";
    private String progressingOperator = "";
    private String lastCompletedRule = "";
    private String lastCompletedRuleName = "";
    private ReactorStatus status = ReactorStatus.IDLE;
    private final ContainerData menuData = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? (int) Math.round(reactionProgress * 1_000.0) : 0;
        }

        @Override
        public void set(int index, int value) {
            // The server owns progress; this client synchronization view is read-only.
        }

        @Override
        public int getCount() {
            return 1;
        }
    };

    public ChemicalReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ChemBlockEntities.CHEMICAL_REACTOR.get(), pos, state);
    }

    /** One server calculation each second, as specified for chemical machines. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ChemicalReactorBlockEntity reactor) {
        if (level.getGameTime() % 20L != 0L) return;
        reactor.tickOneSecond();
    }

    /** The standard inventory GUI uses this title and the current server-side status. */
    public Component menuTitle() {
        if (status == ReactorStatus.COMPLETE && !lastCompletedRuleName.isBlank()) {
            return Component.translatable(
                "container.chemmod.chemical_reactor.complete",
                Component.translatable(lastCompletedRuleName)
            );
        }
        return Component.translatable(
            "container.chemmod.chemical_reactor",
            Component.translatable(status.translationKey)
        );
    }

    /** Records the most recent human operator; only a committed process can create discovery facts. */
    public void setLastOperator(Player player) {
        if (player == null || player.getGameProfile().getName().isBlank()) return;
        String name = player.getGameProfile().getName();
        if (!Objects.equals(lastOperator, name)) {
            lastOperator = name;
            setChanged();
        }
    }

    public double reactionProgress() {
        return reactionProgress;
    }

    /** One synchronized integer (0–1000) for the reactor UI progress bar. */
    public ContainerData menuData() {
        return menuData;
    }

    public String lastCompletedRule() {
        return lastCompletedRule;
    }

    private void tickOneSecond() {
        if (level == null || level.isClientSide()) return;

        Candidate candidate = findCandidate();
        if (candidate == null) {
            resetProgress(statusWithoutCandidate());
            return;
        }
        if (!outputsReady(candidate)) {
            resetProgress(ReactorStatus.NEEDS_OUTPUT_VIALS);
            return;
        }

        OperationSignature signature = OperationSignature.from(candidate, this);
        // The signature itself is reconstructed from persisted physical slots.
        // A non-zero saved progress can therefore resume only when those slots
        // still describe the same valid operation after a world reload.
        if (progressingOperation == null && reactionProgress > 0.0) {
            progressingOperation = signature;
            if (progressingOperator.isBlank()) progressingOperator = lastOperator;
        } else if (!signature.equals(progressingOperation)) {
            reactionProgress = 0.0;
            progressingOperation = signature;
            progressingOperator = lastOperator;
        }

        ReactionKinetics kinetics = candidate.rule().getKinetics();
        // TIMED_RULES only contains non-null kinetics; retain an explicit guard
        // for data reloads or future callers which construct a candidate directly.
        if (kinetics == null) {
            resetProgress(ReactorStatus.NO_TIMED_MODEL);
            return;
        }
        reactionProgress = Math.min(1.0, reactionProgress + kinetics.ratePerSecond(environment().getTemperatureKelvin()));
        status = ReactorStatus.PROCESSING;
        setVisualActive(true);
        setChanged();

        if (reactionProgress < 1.0) return;
        commit(candidate);
    }

    private void commit(Candidate candidate) {
        try {
            List<ItemStack> coReactants = occupiedCoReactantSlots();
            List<ItemStack> productSlots = productSlots(candidate.plan().products().size());
            VialReactionTransaction transaction = VialReactionTransaction.preparePure(
                items.get(TARGET_SLOT), coReactants, candidate.plan(), productSlots
            );
            transaction.commit();
            if (!transaction.isCommitted()) throw new IllegalStateException("Reactor transaction did not commit");

            recordDiscoveries(candidate);
            lastCompletedRule = candidate.rule().getId().getValue();
            lastCompletedRuleName = candidate.rule().getDisplayNameKey();
            reactionProgress = 0.0;
            progressingOperation = null;
            progressingOperator = "";
            status = ReactorStatus.COMPLETE;
            setVisualActive(false);
            setChanged();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            // VialReactionTransaction verifies every slot again immediately before
            // mutation. A concurrent menu/automation change therefore fails without
            // partial consumption, and the reactor simply waits for a new plan.
            resetProgress(ReactorStatus.INPUT_CHANGED);
        }
    }

    private void recordDiscoveries(Candidate candidate) {
        if (level == null || level.getServer() == null || progressingOperator.isBlank()) return;
        DiscoverySavedData discoveries = DiscoverySavedData.get(level.getServer());
        for (SubstanceContents product : candidate.plan().products()) {
            discoveries.recordSynthesis(product.molecule(), progressingOperator, Instant.now(), null);
        }
    }

    /**
     * Selects a machine operation from the data set. The physical contents are
     * supplied to the core planner; no item or GUI claim can invent a
     * co-reactant. An ambiguous structural match is deliberately not selected.
     */
    private Candidate findCandidate() {
        SubstanceContents target = pureContents(items.get(TARGET_SLOT));
        if (target == null) return null;

        List<SubstanceContents> coReactants = new ArrayList<>();
        for (ItemStack slot : occupiedCoReactantSlots()) {
            SubstanceContents contents = pureContents(slot);
            if (contents == null) return null;
            coReactants.add(contents);
        }

        ReactionEnvironment environment = environment();
        List<Candidate> candidates = new ArrayList<>();
        for (ReactionRule rule : TIMED_RULES) {
            List<PureSubstanceReactionPlanner.PlannedSubstanceReaction> plans = PLANNER.plan(
                target, coReactants, rule.getId(), environment
            );
            for (PureSubstanceReactionPlanner.PlannedSubstanceReaction plan : plans) {
                if (plan.products().size() <= outputCapacity()) candidates.add(new Candidate(rule, plan));
            }
        }
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private ReactionEnvironment environment() {
        return new ReactionEnvironment(
            temperatureKelvin(),
            ATMOSPHERIC_PRESSURE_KILOPASCALS,
            catalystTags(),
            java.util.Set.of()
        );
    }

    /**
     * M3 has no global thermal or pressure field yet. The reactor therefore
     * reads only an actual adjacent heat source and the fixed open-vessel
     * atmospheric pressure; later fields can replace this narrow adapter
     * without changing core rule evaluation or vial transactions.
     */
    private double temperatureKelvin() {
        if (level == null) return 293.15;
        BlockState below = level.getBlockState(worldPosition.below());
        if (below.is(Blocks.LAVA)) return 1_200.0;
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE)) return 900.0;
        if ((below.is(Blocks.CAMPFIRE) || below.is(Blocks.SOUL_CAMPFIRE))
            && below.hasProperty(CampfireBlock.LIT)
            && below.getValue(CampfireBlock.LIT)) {
            return 800.0;
        }
        if ((below.is(Blocks.FURNACE) || below.is(Blocks.BLAST_FURNACE) || below.is(Blocks.SMOKER))
            && below.hasProperty(AbstractFurnaceBlock.LIT)
            && below.getValue(AbstractFurnaceBlock.LIT)) {
            return 900.0;
        }
        return 293.15;
    }

    private java.util.Set<String> catalystTags() {
        ItemStack catalyst = items.get(CATALYST_SLOT);
        if (catalyst.isEmpty()) return java.util.Set.of();
        return CATALYST_TAG_IDS.stream()
            .filter(tag -> catalyst.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag))))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private ReactorStatus statusWithoutCandidate() {
        if (items.get(TARGET_SLOT).isEmpty()) return ReactorStatus.IDLE;
        if (pureContents(items.get(TARGET_SLOT)) == null) return ReactorStatus.PURE_VIALS_ONLY;
        if (temperatureKelvin() < 500.0) return ReactorStatus.NEEDS_HEAT;
        if (items.get(CATALYST_SLOT).isEmpty()) return ReactorStatus.NO_MATCH;
        return ReactorStatus.NO_MATCH;
    }

    private boolean outputsReady(Candidate candidate) {
        for (ItemStack stack : productSlots(candidate.plan().products().size())) {
            if (!isEmptyVial(stack)) return false;
        }
        return true;
    }

    private List<ItemStack> occupiedCoReactantSlots() {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = CO_REACTANT_FIRST_SLOT; slot <= CO_REACTANT_SECOND_SLOT; slot++) {
            if (!items.get(slot).isEmpty()) result.add(items.get(slot));
        }
        return List.copyOf(result);
    }

    private List<ItemStack> productSlots(int count) {
        if (count < 1 || count > outputCapacity()) {
            throw new IllegalArgumentException("The first chemical reactor has insufficient product-vial slots");
        }
        List<ItemStack> result = new ArrayList<>();
        for (int slot = OUTPUT_FIRST_SLOT; slot < OUTPUT_FIRST_SLOT + count; slot++) {
            result.add(items.get(slot));
        }
        return List.copyOf(result);
    }

    private static int outputCapacity() {
        return OUTPUT_SECOND_SLOT - OUTPUT_FIRST_SLOT + 1;
    }

    private static SubstanceContents pureContents(ItemStack stack) {
        if (!isFilledVial(stack)) return null;
        VialContentsState state;
        try {
            state = VialContentsState.fromStack(stack);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return state.mixture() == null ? state.substance() : null;
    }

    private static boolean isFilledVial(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ChemItems.SUBSTANCE_VIAL.get()) || stack.getCount() != 1) {
            return false;
        }
        try {
            return !VialContentsState.fromStack(stack).isEmpty();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isEmptyVial(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ChemItems.SUBSTANCE_VIAL.get()) || stack.getCount() != 1) {
            return false;
        }
        try {
            return VialContentsState.fromStack(stack).isEmpty();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isCatalyst(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() != 1) return false;
        return CATALYST_TAG_IDS.stream()
            .anyMatch(tag -> stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag))));
    }

    private void resetProgress(ReactorStatus newStatus) {
        boolean changed = reactionProgress != 0.0 || progressingOperation != null || !progressingOperator.isBlank() || status != newStatus;
        reactionProgress = 0.0;
        progressingOperation = null;
        progressingOperator = "";
        status = newStatus;
        setVisualActive(false);
        if (changed) setChanged();
    }

    private void setVisualActive(boolean active) {
        if (level == null || level.isClientSide() || !getBlockState().hasProperty(ChemicalReactorBlock.ACTIVE)) return;
        if (getBlockState().getValue(ChemicalReactorBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(ChemicalReactorBlock.ACTIVE, active), 3);
        }
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return validSlot(slot) ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (!validSlot(slot)) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return validSlot(slot) ? ContainerHelper.takeItem(items, slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!validSlot(slot)) return;
        items.set(slot, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case TARGET_SLOT, CO_REACTANT_FIRST_SLOT, CO_REACTANT_SECOND_SLOT -> isFilledVial(stack);
            case OUTPUT_FIRST_SLOT, OUTPUT_SECOND_SLOT -> isEmptyVial(stack);
            case CATALYST_SLOT -> isCatalyst(stack);
            default -> false;
        };
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < items.size(); slot++) items.set(slot, ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(
            worldPosition.getX() + 0.5,
            worldPosition.getY() + 0.5,
            worldPosition.getZ() + 0.5
        ) <= 64.0;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putDouble(PROGRESS_KEY, reactionProgress);
        tag.putString(OPERATOR_KEY, lastOperator);
        tag.putString(PROCESSING_OPERATOR_KEY, progressingOperator);
        tag.putString(LAST_RULE_KEY, lastCompletedRule);
        tag.putString(LAST_RULE_NAME_KEY, lastCompletedRuleName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        reactionProgress = Math.max(0.0, Math.min(1.0, tag.getDouble(PROGRESS_KEY)));
        progressingOperation = null; // Re-plan from current physical slots after loading.
        lastOperator = tag.getString(OPERATOR_KEY);
        progressingOperator = tag.getString(PROCESSING_OPERATOR_KEY);
        lastCompletedRule = tag.getString(LAST_RULE_KEY);
        lastCompletedRuleName = tag.getString(LAST_RULE_NAME_KEY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    private record Candidate(
        ReactionRule rule,
        PureSubstanceReactionPlanner.PlannedSubstanceReaction plan
    ) {}

    private record OperationSignature(
        String ruleId,
        VialContentsState target,
        List<VialContentsState> coReactants,
        String catalystItemId
    ) {
        private static OperationSignature from(Candidate candidate, ChemicalReactorBlockEntity reactor) {
            List<VialContentsState> coReactants = reactor.occupiedCoReactantSlots().stream()
                .map(VialContentsState::fromStack)
                .toList();
            ItemStack catalyst = reactor.items.get(CATALYST_SLOT);
            return new OperationSignature(
                candidate.rule().getId().getValue(),
                VialContentsState.fromStack(reactor.items.get(TARGET_SLOT)),
                List.copyOf(coReactants),
                catalyst.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(catalyst.getItem()).toString()
            );
        }
    }

    private enum ReactorStatus {
        IDLE("reactor_status.chemmod.idle"),
        PURE_VIALS_ONLY("reactor_status.chemmod.pure_vials_only"),
        NEEDS_HEAT("reactor_status.chemmod.needs_heat"),
        NEEDS_OUTPUT_VIALS("reactor_status.chemmod.needs_output_vials"),
        NO_MATCH("reactor_status.chemmod.no_match"),
        NO_TIMED_MODEL("reactor_status.chemmod.no_timed_model"),
        PROCESSING("reactor_status.chemmod.processing"),
        COMPLETE("reactor_status.chemmod.complete"),
        INPUT_CHANGED("reactor_status.chemmod.input_changed");

        private final String translationKey;

        ReactorStatus(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
