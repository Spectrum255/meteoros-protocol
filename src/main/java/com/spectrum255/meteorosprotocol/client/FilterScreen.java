package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.client.gui.MeteorosGui;
import com.spectrum255.meteorosprotocol.menu.FilterMenu;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Vanilla-styled filter cartridge screen: one 18 x 18 slot-style cell per phase,
 * each holding a 12 x 12 swatch in that phase's colour when selected, plus the
 * phase name. The panel grows with the registry: 8 px header + title band, one
 * 18 px row per phase and an 8 px footer, i.e. {@code 40 + 18 * N}.
 */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    private static final int WIDTH = 176;
    private static final int TITLE_Y = 6;
    private static final int ROW_TOP = 24;
    private static final int ROW_HEIGHT = 18;
    private static final int CELL_X = 12;
    private static final int LABEL_X = 34;
    private static final int LABEL_Y_OFFSET = 5;
    private static final int SWATCH = 12;

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = 40 + ROW_HEIGHT * Phase.all().size();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        MeteorosGui.panel(graphics, x, y, this.imageWidth, this.imageHeight);

        long[] mask = this.menu.mask();
        for (int index = 0; index < Phase.all().size(); index++) {
            Phase phase = Phase.all().get(index);
            int rowY = y + ROW_TOP + index * ROW_HEIGHT;
            int word = index / Long.SIZE;
            boolean selected = word < mask.length && (mask[word] & (1L << (index % Long.SIZE))) != 0L;

            if (inside(mouseX, mouseY, x + 8, rowY - 1, this.imageWidth - 16, ROW_HEIGHT)) {
                graphics.fill(x + 8, rowY - 1, x + this.imageWidth - 8, rowY + ROW_HEIGHT - 1,
                        MeteorosGui.ROW_HIGHLIGHT);
            }

            MeteorosGui.slot(graphics, x + CELL_X, rowY);
            int swatchX = x + CELL_X + 2;
            int swatchY = rowY + 2;
            if (selected) {
                graphics.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, phase.argb());
            } else {
                graphics.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, MeteorosGui.SLOT_BODY);
                graphics.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + 1, MeteorosGui.SLOT_MUTED);
                graphics.fill(swatchX, swatchY + SWATCH - 1, swatchX + SWATCH, swatchY + SWATCH,
                        MeteorosGui.SLOT_MUTED);
                graphics.fill(swatchX, swatchY, swatchX + 1, swatchY + SWATCH, MeteorosGui.SLOT_MUTED);
                graphics.fill(swatchX + SWATCH - 1, swatchY, swatchX + SWATCH, swatchY + SWATCH,
                        MeteorosGui.SLOT_MUTED);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Self-drawn title: the vanilla container title spot, no shadow.
        graphics.drawString(this.font, this.title, 8, TITLE_Y, MeteorosGui.LABEL_TEXT, false);

        long[] mask = this.menu.mask();
        for (int index = 0; index < Phase.all().size(); index++) {
            int word = index / Long.SIZE;
            boolean selected = word < mask.length && (mask[word] & (1L << (index % Long.SIZE))) != 0L;
            int rowY = ROW_TOP + index * ROW_HEIGHT;
            graphics.drawString(this.font,
                    Phase.all().get(index).displayName(),
                    LABEL_X, rowY + LABEL_Y_OFFSET,
                    selected ? MeteorosGui.LABEL_TEXT : MeteorosGui.TEXT_DIM,
                    false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        drawTooltips(graphics, mouseX, mouseY);
    }

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            return;
        }
        int index = rowAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        Phase phase = Phase.all().get(index);
        MeteorosGui.tooltip(graphics, this.font,
                List.of(phase.displayName(),
                        Component.translatable("gui.meteorosprotocol.filter.hint")),
                mouseX, mouseY, 0xFF9A9A9A);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int index = rowAt(mouseX, mouseY);
            if (index >= 0) {
                sendButton(index);
                return true;
            }
        }
        return false;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < this.leftPos + 8 || mouseX >= this.leftPos + this.imageWidth - 8) {
            return -1;
        }
        for (int index = 0; index < Phase.all().size(); index++) {
            int rowY = this.topPos + ROW_TOP + index * ROW_HEIGHT;
            if (mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 1) {
                return index;
            }
        }
        return -1;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}
