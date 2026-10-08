package io.github.antonovichkorp.chemmod.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;

/** Placeable canonical storage form; its batch component is transferred to the block entity on placement. */
public final class MaterialBlockItem extends BlockItem {
    private final String materialPath;
    private final String formName;

    public MaterialBlockItem(Properties properties, Block block, String materialId, String formName) {
        super(block, properties);
        this.materialPath = materialId.substring(materialId.indexOf(':') + 1);
        this.formName = formName.toLowerCase(Locale.ROOT);
    }

    @Override
    public Component getName(ItemStack stack) {
        return MaterialItemPresentation.name(materialPath, formName);
    }

    @Override
    public void appendHoverText(
        ItemStack stack,
        TooltipContext context,
        List<Component> tooltip,
        TooltipFlag flag
    ) {
        MaterialItemPresentation.appendHoverText(stack, context, tooltip, flag);
    }
}
