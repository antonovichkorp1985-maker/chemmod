package io.github.antonovichkorp.chemmod.client;

import io.github.antonovichkorp.chemmod.content.ChemicalReactorBlockEntity;
import io.github.antonovichkorp.chemmod.content.ChemicalReactorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Lightweight localized M3 screen: real restricted slots plus synced reaction progress. */
public final class ChemicalReactorScreen extends AbstractContainerScreen<ChemicalReactorMenu> {
    private static final int PANEL = 0xFFE1E1E1;
    private static final int PANEL_BORDER = 0xFF4A4A4A;
    private static final int PROGRESS_EMPTY = 0xFF4B4B4B;
    private static final int PROGRESS_FULL = 0xFFB55B25;
    private static final int TEXT = 0xFF404040;

    private boolean showingRuleHelp;

    public ChemicalReactorScreen(ChemicalReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 222;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = 128;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.literal("?"), button -> showingRuleHelp = !showingRuleHelp)
            .bounds(leftPos + 157, topPos + 4, 12, 12)
            .tooltip(Tooltip.create(Component.translatable("gui.chemmod.reactor.help")))
            .build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_BORDER);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, PANEL);

        drawSlot(graphics, 26, 34);
        drawSlot(graphics, 48, 34);
        drawSlot(graphics, 66, 34);
        drawSlot(graphics, 110, 34);
        drawSlot(graphics, 128, 34);
        drawSlot(graphics, 150, 34);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) drawSlot(graphics, 8 + column * 18, 140 + row * 18);
        }
        for (int column = 0; column < 9; column++) drawSlot(graphics, 8 + column * 18, 198);

        // This bar is the synchronized data-owned Arrhenius process progress.
        int barX = leftPos + 8;
        int barY = topPos + 69;
        int width = 160;
        graphics.fill(barX, barY, barX + width, barY + 5, PROGRESS_EMPTY);
        int completed = width * menu.progressPermille() / 1_000;
        if (completed > 0) graphics.fill(barX, barY, barX + completed, barY + 5, PROGRESS_FULL);
    }

    private void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, PANEL_BORDER);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Do not use the opening packet's status-bearing title as a live label.
        drawBounded(graphics, title, 8, 6, 143);
        drawBounded(graphics, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 160);
        drawSlotLabel(graphics, "gui.chemmod.reactor.target", 34, 40);
        drawSlotLabel(graphics, "gui.chemmod.reactor.co_reactants", 65, 28);
        drawSlotLabel(graphics, "gui.chemmod.reactor.products", 127, 40);
        drawSlotLabel(graphics, "gui.chemmod.reactor.catalyst", 158, 24);
        drawBounded(graphics, Component.translatable("gui.chemmod.reactor.progress_value",
            menu.progressPermille() / 10), 8, 57, 160);
        drawBounded(graphics, statusText(), 8, 80, 160);
        drawBounded(graphics, ruleText(), 8, 94, 160);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Full text is wrapped by Minecraft's screen-bounded tooltip renderer,
        // never painted over the inventory as an unbounded help paragraph.
        if (showingRuleHelp) {
            drawFullText(graphics, ChemicalReactorBlockEntity.ruleDescriptionComponent(menu.ruleDisplayIndex()), mouseX, mouseY);
        } else if (overLabel(mouseX, mouseY, 80)) {
            drawFullText(graphics, statusText(), mouseX, mouseY);
        } else if (overLabel(mouseX, mouseY, 94)) {
            drawFullText(graphics, ruleText(), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private Component statusText() {
        return Component.translatable("gui.chemmod.reactor.status",
            ChemicalReactorBlockEntity.statusComponent(menu.statusCode()));
    }

    private Component ruleText() {
        return Component.translatable("gui.chemmod.reactor.rule",
            ChemicalReactorBlockEntity.ruleNameComponent(menu.ruleDisplayIndex()));
    }

    private boolean overLabel(int mouseX, int mouseY, int y) {
        return mouseX >= leftPos + 8 && mouseX < leftPos + 168
            && mouseY >= topPos + y && mouseY < topPos + y + font.lineHeight;
    }

    private void drawFullText(GuiGraphics graphics, Component text, int mouseX, int mouseY) {
        graphics.renderTooltip(font, font.split(text, Math.max(40, Math.min(240, width - 20))), mouseX, mouseY);
    }

    private String fitted(Component text, int maxWidth) {
        String plain = text.getString();
        if (font.width(plain) <= maxWidth) return plain;
        String ellipsis = "…";
        return font.plainSubstrByWidth(plain, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    private void drawBounded(GuiGraphics graphics, Component text, int x, int y, int maxWidth) {
        graphics.drawString(font, fitted(text, maxWidth), x, y, TEXT, false);
    }

    private void drawSlotLabel(GuiGraphics graphics, String key, int centerX, int maxWidth) {
        String text = fitted(Component.translatable(key), maxWidth);
        graphics.drawString(font, text, centerX - font.width(text) / 2, 21, TEXT, false);
    }
}
