package io.github.antonovichkorp.chemmod.content;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

public final class MaterialFormItem extends Item {
    private final String materialId;
    private final String materialPath;
    private final String formName;

    public MaterialFormItem(Properties properties, String materialId, String formName) {
        super(properties);
        this.materialId = materialId;
        this.materialPath = materialId.substring(materialId.indexOf(':') + 1);
        this.formName = formName.toLowerCase(Locale.ROOT);
    }

    /** Content ID of the bulk material this form represents, e.g. {@code chemmod:copper}. */
    public String materialId() {
        return materialId;
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
