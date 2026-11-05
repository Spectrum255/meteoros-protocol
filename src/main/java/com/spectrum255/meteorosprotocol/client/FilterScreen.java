package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.menu.FilterMenu;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Texture-free six-checkbox filter tuning screen. */
public class FilterScreen extends AbstractContainerScreen<FilterMenu> {
    private static final int PANEL = 0xFFE8E8E8;
    private static final int HEADER = 0xFFC8C8C8;
    private static final int TEXT = 0xFF303030;
    private static final int BOX = 0xFF373737;
    private static final int BORDER = 0xFF6E6E6E;
    private static final int CHECK = 0xFF3FA9F5;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_TOP = 24;

    public FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 140;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        graphics.fill(x, y, x + this.imageWidth, y + 20, HEADER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        long[] mask = this.menu.mask();
        for (int index = 0; index < Phase.all().size(); index++) {
            Phase phase = Phase.all().get(index);
            int rowY = ROW_TOP + index * ROW_HEIGHT;
            int boxX = 12;
            int word = index / Long.SIZE;
            boolean selected = word < mask.length && (mask[word] & (1L << (index % Long.SIZE))) != 0L;
            graphics.fill(boxX - 1, rowY - 1, boxX + 10, rowY + 10, BORDER);
            graphics.fill(boxX, rowY, boxX + 9, rowY + 9, BOX);
            if (selected) {
                graphics.fill(boxX + 2, rowY + 2, boxX + 7, rowY + 7, CHECK);
            }
            graphics.drawString(
                    this.font,
                    phase.displayName(),
                    26,
                    rowY + 1,
                    TEXT,
                    false
            );
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int index = 0; index < Phase.all().size(); index++) {
                int rowY = this.topPos + ROW_TOP + index * ROW_HEIGHT;
                if (mouseX >= this.leftPos + 10 && mouseX < this.leftPos + 166
                        && mouseY >= rowY - 2 && mouseY < rowY + 12) {
                    sendButton(index);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}
