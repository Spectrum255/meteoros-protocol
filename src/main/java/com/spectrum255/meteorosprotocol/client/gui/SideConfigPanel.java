package com.spectrum255.meteorosprotocol.client.gui;

import com.spectrum255.meteorosprotocol.machine.SideMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Side-configuration overlay shared by the machine screens.
 *
 * <p>The panel is a pure {@code fill}-drawn 152 x 108 sheet anchored inside the
 * container panel. It shows one channel at a time (mixed buffer or phase tanks),
 * two tabs on top and six face cells laid out like a folded box: top, bottom,
 * front (north), back (south), left (west) and right (east). Each cell colour
 * encodes the current {@link SideMode}; left click cycles forward, right click
 * backwards. The panel keeps no copy of the modes: it reads the two masks the
 * menu already synchronises, so the server stays authoritative.</p>
 */
public final class SideConfigPanel {
    public static final int WIDTH = 152;
    public static final int HEIGHT = 108;

    private static final int TAB_Y = 4;
    private static final int TAB_WIDTH = 60;
    private static final int TAB_HEIGHT = 14;
    private static final int TAB_MIXED_X = 8;
    private static final int TAB_PHASE_X = 72;

    private static final int CELL_WIDTH = 34;
    private static final int CELL_HEIGHT = 26;
    private static final int CELL_STEP_X = 38;
    private static final int CELL_STEP_Y = 28;
    private static final int GRID_X = 21;
    private static final int GRID_Y = 22;

    private static final int NONE_COLOR = 0xFF555555;
    private static final int IN_COLOR = 0xFF4A7DD6;
    private static final int OUT_COLOR = 0xFFD68A4A;
    private static final int BOTH_COLOR = 0xFF8B5CD6;
    private static final int TAB_SELECTED = 0xFF8B8B8B;
    private static final int TAB_UNSELECTED = 0xFFC6C6C6;
    private static final int CELL_BORDER = 0xFF373737;
    private static final int HOVER_FRAME = 0xFFFFFFFF;
    private static final int MODE_TEXT = 0xFFFFFFFF;
    private static final int TAB_TEXT = 0xFF404040;

    private static final int CHANNEL_MIXED = 0;
    private static final int CHANNEL_PHASE = 1;

    /** A face cell: world direction plus its column/row in the 3 x 3 grid. */
    private record FaceCell(Direction direction, int column, int row, String key) {
    }

    private static final List<FaceCell> CELLS = List.of(
            new FaceCell(Direction.UP, 1, 0, "top"),
            new FaceCell(Direction.SOUTH, 2, 0, "back"),
            new FaceCell(Direction.WEST, 0, 1, "left"),
            new FaceCell(Direction.NORTH, 1, 1, "front"),
            new FaceCell(Direction.EAST, 2, 1, "right"),
            new FaceCell(Direction.DOWN, 1, 2, "bottom"));

    private boolean visible;
    private int channel = CHANNEL_MIXED;

    public boolean visible() {
        return this.visible;
    }

    public void toggle() {
        this.visible = !this.visible;
    }

    public void close() {
        this.visible = false;
    }

    /** Draws the dim backdrop, the sheet, both tabs and the six face cells. */
    public void render(GuiGraphics graphics, Font font, int originX, int originY,
                       int mixedMask, int phaseMask, int mouseX, int mouseY) {
        int x = originX + 12;
        int y = originY + 30;
        MeteorosGui.panel(graphics, x, y, WIDTH, HEIGHT);

        drawTab(graphics, font, x + TAB_MIXED_X, y + TAB_Y, channel == CHANNEL_MIXED,
                Component.translatable("gui.meteorosprotocol.side.mixed"));
        drawTab(graphics, font, x + TAB_PHASE_X, y + TAB_Y, channel == CHANNEL_PHASE,
                Component.translatable("gui.meteorosprotocol.side.phase"));

        int mask = channel == CHANNEL_MIXED ? mixedMask : phaseMask;
        for (FaceCell cell : CELLS) {
            int cellX = x + GRID_X + cell.column * CELL_STEP_X;
            int cellY = y + GRID_Y + cell.row * CELL_STEP_Y;
            SideMode mode = modeAt(mask, cell.direction());
            drawCell(graphics, font, cellX, cellY, cell, mode,
                    inside(mouseX, mouseY, cellX, cellY, CELL_WIDTH, CELL_HEIGHT));
        }
    }

    /** Tooltip for the hovered face cell; returns false when nothing is hovered. */
    public boolean renderTooltip(GuiGraphics graphics, Font font, int originX, int originY,
                                 int mixedMask, int phaseMask, int mouseX, int mouseY) {
        int x = originX + 12;
        int y = originY + 30;
        int mask = this.channel == CHANNEL_MIXED ? mixedMask : phaseMask;
        for (FaceCell cell : CELLS) {
            int cellX = x + GRID_X + cell.column * CELL_STEP_X;
            int cellY = y + GRID_Y + cell.row * CELL_STEP_Y;
            if (inside(mouseX, mouseY, cellX, cellY, CELL_WIDTH, CELL_HEIGHT)) {
                Component line = Component.literal(
                        Component.translatable("gui.meteorosprotocol.side." + cell.key()).getString()
                                + ": " + modeName(modeAt(mask, cell.direction())).getString());
                MeteorosGui.tooltip(graphics, font,
                        List.of(line, Component.translatable("gui.meteorosprotocol.side.hint")),
                        mouseX, mouseY, 0xFF9A9A9A);
                return true;
            }
        }
        return false;
    }

    /**
     * Handles a click while the panel is visible. Returns true when the click was
     * consumed (tabs and face cells), so screens can give the overlay priority
     * over the widgets underneath.
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int originX, int originY, int mixedMask, int phaseMask,
                                IntConsumer sendButton) {
        if (!this.visible) {
            return false;
        }
        int x = originX + 12;
        int y = originY + 30;
        if (!inside(mouseX, mouseY, x, y, WIDTH, HEIGHT)) {
            return false;
        }
        if (inside(mouseX, mouseY, x + TAB_MIXED_X, y + TAB_Y, TAB_WIDTH, TAB_HEIGHT)) {
            this.channel = CHANNEL_MIXED;
            return true;
        }
        if (inside(mouseX, mouseY, x + TAB_PHASE_X, y + TAB_Y, TAB_WIDTH, TAB_HEIGHT)) {
            this.channel = CHANNEL_PHASE;
            return true;
        }
        int mask = this.channel == CHANNEL_MIXED ? mixedMask : phaseMask;
        int base = this.channel == CHANNEL_MIXED ? 100 : 108;
        for (FaceCell cell : CELLS) {
            int cellX = x + GRID_X + cell.column * CELL_STEP_X;
            int cellY = y + GRID_Y + cell.row * CELL_STEP_Y;
            if (inside(mouseX, mouseY, cellX, cellY, CELL_WIDTH, CELL_HEIGHT)) {
                if (button == 0) {
                    sendButton.accept(base + cell.direction().ordinal());
                } else if (button == 1) {
                    sendButton.accept(base + 100 + cell.direction().ordinal());
                }
                return true;
            }
        }
        return true;
    }

    private void drawTab(GuiGraphics graphics, Font font, int x, int y, boolean selected, Component text) {
        graphics.fill(x, y, x + TAB_WIDTH, y + TAB_HEIGHT, CELL_BORDER);
        graphics.fill(x + 1, y + 1, x + TAB_WIDTH - 1, y + TAB_HEIGHT - 1,
                selected ? TAB_SELECTED : TAB_UNSELECTED);
        int textY = y + (TAB_HEIGHT - font.lineHeight) / 2 + 1;
        graphics.drawString(font, text, x + (TAB_WIDTH - font.width(text)) / 2, textY, TAB_TEXT, false);
    }

    private void drawCell(GuiGraphics graphics, Font font, int x, int y, FaceCell cell,
                          SideMode mode, boolean hovered) {
        graphics.fill(x, y, x + CELL_WIDTH, y + CELL_HEIGHT, CELL_BORDER);
        graphics.fill(x + 1, y + 1, x + CELL_WIDTH - 1, y + CELL_HEIGHT - 1, modeColor(mode));
        if (hovered) {
            graphics.fill(x, y, x + CELL_WIDTH, y + 1, HOVER_FRAME);
            graphics.fill(x, y + CELL_HEIGHT - 1, x + CELL_WIDTH, y + CELL_HEIGHT, HOVER_FRAME);
            graphics.fill(x, y, x + 1, y + CELL_HEIGHT, HOVER_FRAME);
            graphics.fill(x + CELL_WIDTH - 1, y, x + CELL_WIDTH, y + CELL_HEIGHT, HOVER_FRAME);
        }
        Component name = Component.translatable("gui.meteorosprotocol.side." + cell.key());
        graphics.drawString(font, name, x + (CELL_WIDTH - font.width(name)) / 2, y + 4, MODE_TEXT, false);
        Component symbol = Component.literal(modeSymbol(mode));
        graphics.drawString(font, symbol, x + (CELL_WIDTH - font.width(symbol)) / 2, y + 14, MODE_TEXT, false);
    }

    private static SideMode modeAt(int mask, Direction direction) {
        return SideMode.byId((mask >> (direction.ordinal() * 2)) & 3);
    }

    private static int modeColor(SideMode mode) {
        return switch (mode) {
            case NONE -> NONE_COLOR;
            case IN -> IN_COLOR;
            case OUT -> OUT_COLOR;
            case BOTH -> BOTH_COLOR;
        };
    }

    private static String modeSymbol(SideMode mode) {
        return switch (mode) {
            case NONE -> "\u2014";
            case IN -> "IN";
            case OUT -> "OUT";
            case BOTH -> "\u2194";
        };
    }

    private static Component modeName(SideMode mode) {
        return Component.translatable("gui.meteorosprotocol.side.mode."
                + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
