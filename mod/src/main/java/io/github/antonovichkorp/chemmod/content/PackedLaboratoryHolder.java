package io.github.antonovichkorp.chemmod.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Immutable vanilla container snapshot: empty positions are significant, contents never pool. */
public record PackedLaboratoryHolder(boolean rack, UUID holderId, ItemContainerContents vessels) {
    public static final Codec<PackedLaboratoryHolder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.BOOL.fieldOf("rack").forGetter(PackedLaboratoryHolder::rack),
        UUIDUtil.CODEC.fieldOf("holder_id").forGetter(PackedLaboratoryHolder::holderId),
        ItemContainerContents.CODEC.fieldOf("vessels").forGetter(PackedLaboratoryHolder::vessels)
    ).apply(instance, PackedLaboratoryHolder::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PackedLaboratoryHolder> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, PackedLaboratoryHolder::rack,
        ByteBufCodecs.fromCodec(UUIDUtil.CODEC), PackedLaboratoryHolder::holderId,
        ItemContainerContents.STREAM_CODEC, PackedLaboratoryHolder::vessels,
        PackedLaboratoryHolder::new
    );

    public PackedLaboratoryHolder {
        if (holderId == null || vessels == null) throw new IllegalArgumentException("Missing holder data");
        List<ItemStack> stacks = vessels.stream().toList();
        if (stacks.size() > (rack ? 6 : 4)) throw new IllegalArgumentException("Too many holder positions");
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && (stack.getCount() != 1 || !stack.is(ChemItems.SUBSTANCE_VIAL.get())
                || stack.has(ChemComponents.PACKED_HOLDER.get()) || stack.has(DataComponents.CONTAINER)
                || stack.has(DataComponents.BUNDLE_CONTENTS)))
                throw new IllegalArgumentException("Packed holders accept only individual non-container vials");
        }
    }
    public NonNullList<ItemStack> copySlots() {
        NonNullList<ItemStack> result = NonNullList.withSize(rack ? 6 : 4, ItemStack.EMPTY);
        vessels.copyInto(result);
        return result;
    }
    public int occupiedCount() { return (int) vessels.stream().filter(stack -> !stack.isEmpty()).count(); }
}
