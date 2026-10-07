package io.github.antonovichkorp.chemmod.client;

import io.github.antonovichkorp.chemmod.content.ChemicalReactorMenu;
import net.minecraft.client.gui.GuiGraphics;
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

    public ChemicalReactorScreen(ChemicalReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        titleLabelX = 8;
        titleLabelY = 6;
        inventoryLabelX = 8;
        inventoryLabelY = 73;
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
            for (int column = 0; column < 9; column++) drawSlot(graphics, 8 + column * 18, 84 + row * 18);
        }
        for (int column = 0; column < 9; column++) drawSlot(graphics, 8 + column * 18, 142);

        // This bar is the synchronized data-owned Arrhenius process progress.
        int barX = leftPos + 26;
        int barY = topPos + 59;
        int width = 124;
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
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, Component.translatable("gui.chemmod.reactor.target"), 24, 21, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.chemmod.reactor.co_reactants"), 47, 21, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.chemmod.reactor.products"), 108, 21, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.chemmod.reactor.catalyst"), 147, 21, TEXT, false);
        graphics.drawString(font, Component.translatable("gui.chemmod.reactor.progress"), 8, 57, TEXT, false);
    }
}
