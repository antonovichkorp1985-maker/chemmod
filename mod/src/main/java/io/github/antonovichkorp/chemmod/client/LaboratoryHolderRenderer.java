package io.github.antonovichkorp.chemmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.antonovichkorp.chemmod.content.LaboratoryHolderBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;

/** Render the exact synchronized stacks, not a count-only decorative set of tubes. */
public final class LaboratoryHolderRenderer implements BlockEntityRenderer<LaboratoryHolderBlockEntity> {
    private final ItemRenderer items;
    public LaboratoryHolderRenderer(BlockEntityRendererProvider.Context context) { items = context.getItemRenderer(); }
    @Override public void render(LaboratoryHolderBlockEntity holder, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        for (int slot = 0; slot < 10; slot++) {
            var stack = holder.contents(slot);
            if (stack.isEmpty()) continue;
            boolean rack = slot < 6;
            pose.pushPose();
            pose.translate((rack ? 4.0 : 12.0) / 16, (rack ? 5.5 : 3.2) / 16,
                (rack ? 2.5 + slot * 2.2 : 3.0 + (slot - 6) * 3.3) / 16);
            float scale = rack ? 0.5F : 0.35F;
            pose.scale(scale, scale, scale);
            items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, holder.getLevel(), slot);
            pose.popPose();
        }
    }
}
