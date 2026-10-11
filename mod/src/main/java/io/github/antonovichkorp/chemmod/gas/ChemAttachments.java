package io.github.antonovichkorp.chemmod.gas;

import io.github.antonovichkorp.chemmod.ChemMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Level attachments. The gas field is per level so that dimensions do not share an atmosphere and
 * so it saves with the world it belongs to.
 */
public final class ChemAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
        DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ChemMod.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WorldGasField>> GAS_FIELD =
        ATTACHMENTS.register("gas_field",
            () -> AttachmentType.builder(WorldGasField::new).serialize(WorldGasField.CODEC).build());

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    private ChemAttachments() {
    }
}
