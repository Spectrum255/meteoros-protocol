package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.machine.CreativeParticleBlockEntity;
import com.spectrum255.meteorosprotocol.menu.InfiniteParticleCellMenu;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Texture-free six-face config and scrollable phase picker for the creative cell. */
public class InfiniteParticleCellScreen extends AbstractContainerScreen<InfiniteParticleCellMenu> {
    private static final int PANEL = 0xFFE8E8E8;
    private static final int HEADER = 0xFFC8C8C8;
    private static final int TEXT = 0xFF303030;
    private static final int BUTTON = 0xFFB8B8B8;
    private static final int BUTTON_HOVER = 0xFFD8D8D8;
    private static final int BUTTON_SELECTED = 0xFF3FA9F5;
    private static final int BUTTON_BORDER = 0xFF6E6E6E;
    private static final int BUTTON_DISABLED = 0xFFD0D0D0;
    private static final int ROW_TINT = 0x28FFFFFF;

    private static final int MAIN_ROW_Y = 24;
    private static final int MAIN_ROW_H = 19;
    private static final int MAIN_BUTTON_H = 16;
    private static final int SIDE_LABEL_X = 8;
    private static final int MODE_X = 30;
    private static final int MODE_W = 50;
    private static final int PHASE_X = 84;
    private static final int PHASE_W = 54;

    private static final int SUB_ROW_Y = 28;
    private static final int SUB_ROW_H = 18;
    private static final int SUB_BUTTON_X = 12;
    private static final int SUB_BUTTON_W = 124;
    private static final int SUB_BUTTON_H = 16;
    private static final int SUB_VISIBLE_ROWS = 6;
    private static final int BACK_X = 12;
    private static final int BACK_Y = 140;
    private static final int BACK_W = 54;
    private static final int BACK_H = 16;
    private static final int SCROLL_X = 148;
    private static final int SCROLL_Y = 26;
    private static final int SCROLL_W = 10;
    private static final int SCROLL_H = 108;

    private int activeSide = -1;
    private int scroll;
    private boolean draggingScrollbar;

    public InfiniteParticleCellScreen(InfiniteParticleCellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        graphics.fill(x, y, x + this.imageWidth, y + 20, HEADER);

        if (this.activeSide < 0) {
            for (int side = 0; side < InfiniteParticleCellMenu.SIDE_COUNT; side++) {
                int rowY = y + MAIN_ROW_Y + side * MAIN_ROW_H;
                graphics.fill(x + 4, rowY - 1, x + 172, rowY + MAIN_BUTTON_H + 1, ROW_TINT);
                drawButton(graphics, x + MODE_X, rowY, MODE_W, MAIN_BUTTON_H, mouseX, mouseY, true);
                boolean output = this.menu.sideMode(side) == CreativeParticleBlockEntity.SideMode.OUTPUT;
                drawButton(graphics, x + PHASE_X, rowY, PHASE_W, MAIN_BUTTON_H, mouseX, mouseY, output);
            }
            return;
        }

        // Phase picker subpage.
        int maxScroll = maxScroll();
        for (int visible = 0; visible < SUB_VISIBLE_ROWS; visible++) {
            int phaseIndex = this.scroll + visible;
            if (phaseIndex >= Phase.all().size()) {
                break;
            }
            int rowY = y + SUB_ROW_Y + visible * SUB_ROW_H;
            boolean selected = phaseIndex == this.menu.outputPhase(this.activeSide).index();
            graphics.fill(x + SUB_BUTTON_X, rowY, x + SUB_BUTTON_X + SUB_BUTTON_W,
                    rowY + SUB_BUTTON_H, selected ? BUTTON_SELECTED : ROW_TINT);
        }
        drawButton(graphics, x + BACK_X, y + BACK_Y, BACK_W, BACK_H, mouseX, mouseY, true);
        graphics.fill(x + SCROLL_X, y + SCROLL_Y, x + SCROLL_X + SCROLL_W, y + SCROLL_Y + SCROLL_H, 0xFF8A8A8A);
        int thumbHeight = thumbHeight();
        graphics.fill(x + SCROLL_X, y + thumbY(), x + SCROLL_X + SCROLL_W, y + thumbY() + thumbHeight, 0xFF4A4A4A);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.activeSide < 0) {
            graphics.drawString(this.font, Component.translatable("gui.meteorosprotocol.cell.title"), 8, 6, TEXT, false);
            for (int side = 0; side < InfiniteParticleCellMenu.SIDE_COUNT; side++) {
                int rowY = MAIN_ROW_Y + side * MAIN_ROW_H;
                graphics.drawString(this.font, sideName(side), SIDE_LABEL_X, rowY + 4, TEXT, false);
                Component mode = Component.translatable(modeKey(this.menu.sideMode(side)));
                drawCentered(graphics, mode, MODE_X, rowY, MODE_W, TEXT);
                boolean output = this.menu.sideMode(side) == CreativeParticleBlockEntity.SideMode.OUTPUT;
                Component phase = output
                        ? this.menu.outputPhase(side).displayName()
                        : Component.literal("-");
                drawCentered(graphics, phase, PHASE_X, rowY, PHASE_W, output ? TEXT : 0xFF808080);
            }
            return;
        }

        graphics.drawString(this.font,
                Component.translatable("gui.meteorosprotocol.cell.select_phase"),
                8, 6, TEXT, false);
        for (int visible = 0; visible < SUB_VISIBLE_ROWS; visible++) {
            int phaseIndex = this.scroll + visible;
            if (phaseIndex >= Phase.all().size()) {
                break;
            }
            int rowY = SUB_ROW_Y + visible * SUB_ROW_H;
            Component phase = Phase.all().get(phaseIndex).displayName();
            boolean selected = phaseIndex == this.menu.outputPhase(this.activeSide).index();
            drawCentered(graphics, phase, SUB_BUTTON_X, rowY, SUB_BUTTON_W, selected ? 0xFFFFFFFF : TEXT);
        }
        drawCentered(graphics, Component.translatable("gui.meteorosprotocol.cell.back"),
                BACK_X, BACK_Y, BACK_W, TEXT);
        graphics.drawString(this.font,
                Component.translatable("gui.meteorosprotocol.cell.scroll_hint"),
                8, 158, 0xFF707070, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (this.activeSide < 0) {
            for (int side = 0; side < InfiniteParticleCellMenu.SIDE_COUNT; side++) {
                int rowY = this.topPos + MAIN_ROW_Y + side * MAIN_ROW_H;
                if (inside(mouseX, mouseY, this.leftPos + MODE_X, rowY, MODE_W, MAIN_BUTTON_H)) {
                    sendButton(side);
                    return true;
                }
                if (this.menu.sideMode(side) == CreativeParticleBlockEntity.SideMode.OUTPUT
                        && inside(mouseX, mouseY, this.leftPos + PHASE_X, rowY, PHASE_W, MAIN_BUTTON_H)) {
                    this.activeSide = side;
                    this.scroll = 0;
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (inside(mouseX, mouseY, this.leftPos + BACK_X, this.topPos + BACK_Y, BACK_W, BACK_H)) {
            this.activeSide = -1;
            this.draggingScrollbar = false;
            return true;
        }
        for (int visible = 0; visible < SUB_VISIBLE_ROWS; visible++) {
            int phaseIndex = this.scroll + visible;
            if (phaseIndex >= Phase.all().size()) {
                break;
            }
            int rowY = this.topPos + SUB_ROW_Y + visible * SUB_ROW_H;
            if (inside(mouseX, mouseY, this.leftPos + SUB_BUTTON_X, rowY, SUB_BUTTON_W, SUB_BUTTON_H)) {
                sendButton(InfiniteParticleCellMenu.PHASE_BUTTON_BASE
                        + this.activeSide * Phase.all().size()
                        + phaseIndex);
                this.activeSide = -1;
                return true;
            }
        }
        if (inside(mouseX, mouseY, this.leftPos + SCROLL_X, this.topPos + SCROLL_Y, SCROLL_W, SCROLL_H)) {
            int thumbY = this.topPos + thumbY();
            int thumbHeight = thumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbHeight) {
                this.draggingScrollbar = true;
            } else if (mouseY < thumbY) {
                this.scroll = Math.max(0, this.scroll - SUB_VISIBLE_ROWS);
            } else {
                this.scroll = Math.min(maxScroll(), this.scroll + SUB_VISIBLE_ROWS);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingScrollbar && this.activeSide >= 0) {
            int maxScroll = maxScroll();
            if (maxScroll > 0) {
                int track = SCROLL_H - thumbHeight();
                if (track > 0) {
                    double ratio = (mouseY - this.topPos - SCROLL_Y - thumbHeight() / 2.0) / track;
                    this.scroll = (int) Math.round(Math.max(0.0, Math.min(1.0, ratio)) * maxScroll);
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.activeSide >= 0) {
            if (scrollY > 0) {
                this.scroll = Math.max(0, this.scroll - 1);
            } else if (scrollY < 0) {
                this.scroll = Math.min(maxScroll(), this.scroll + 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int maxScroll() {
        return Math.max(0, Phase.all().size() - SUB_VISIBLE_ROWS);
    }

    private int thumbHeight() {
        int total = Math.max(1, Phase.all().size());
        return Math.max(18, SCROLL_H * SUB_VISIBLE_ROWS / total);
    }

    private int thumbY() {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) {
            return SCROLL_Y;
        }
        int track = SCROLL_H - thumbHeight();
        return SCROLL_Y + (int) Math.round((double) track * this.scroll / maxScroll);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static void drawButton(GuiGraphics graphics, int x, int y, int width, int height,
                                   int mouseX, int mouseY, boolean enabled) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        int fill = enabled ? (hovered ? BUTTON_HOVER : BUTTON) : BUTTON_DISABLED;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, BUTTON_BORDER);
        graphics.fill(x, y, x + 1, y + height, BUTTON_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, BUTTON_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, BUTTON_BORDER);
    }

    private void drawCentered(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        graphics.drawString(this.font, text, x + (width - this.font.width(text)) / 2, y + 4, color, false);
    }

    private static Component sideName(int side) {
        return Component.translatable("gui.meteorosprotocol.cell.side."
                + CreativeParticleBlockEntity.SIDES[side].getSerializedName());
    }

    private static String modeKey(CreativeParticleBlockEntity.SideMode mode) {
        return switch (mode) {
            case INPUT -> "gui.meteorosprotocol.cell.mode.input";
            case OUTPUT -> "gui.meteorosprotocol.cell.mode.output";
            default -> "gui.meteorosprotocol.cell.mode.off";
        };
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }
}
