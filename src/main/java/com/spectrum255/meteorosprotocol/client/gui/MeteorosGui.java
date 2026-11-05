package com.spectrum255.meteorosprotocol.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Shared drawing kit for Meteoros Protocol machine screens, following the
 * Mekanism layout conventions documented in {@code docs/reference/mekanism-gui-notes.md}.
 *
 * <p>All textures live under {@code assets/meteorosprotocol/textures/gui/} and are
 * exported by the art task; until they exist the screen will show the missing
 * texture placeholder, but every call site stays texture-path correct.
 */
public final class MeteorosGui {
    private static final String MOD_ID = "meteorosprotocol";

    public static final ResourceLocation PANEL = gui("panel");
    public static final ResourceLocation SLOT = gui("slot");
    public static final ResourceLocation BUTTON = gui("button");
    public static final ResourceLocation BAR_FRAME = gui("bar_frame");
    public static final ResourceLocation BAR_FILL_H = gui("bar_fill_h");
    public static final ResourceLocation BAR_FILL_V = gui("bar_fill_v");
    public static final ResourceLocation GAUGE_FRAME = gui("gauge_frame");
    public static final ResourceLocation TICKS = gui("ticks");
    public static final ResourceLocation PROGRESS = gui("progress");
    public static final ResourceLocation TAB = gui("tab");
    public static final ResourceLocation TAB_HOLDER = gui("tab_holder");
    public static final ResourceLocation INNER_SCREEN = gui("inner_screen");

    /** Slot hover wash, same value Mekanism uses. */
    public static final int SLOT_HOVER = 0x80FFFFFF;
    /** Tooltip body / border colours. */
    private static final int TOOLTIP_BG = 0xF0100010;
    private static final int TOOLTIP_BORDER_LIGHT = 0x505000FF;
    private static final int TOOLTIP_BORDER_DARK = 0x5028007F;
    public static final int BUTTON_TEXT = 0xFF303030;
    public static final int BUTTON_TEXT_DISABLED = 0xFF9A9A9A;

    private MeteorosGui() {
    }

    private static ResourceLocation gui(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/gui/" + name + ".png");
    }

    /** Region 9-slice: corners 1:1, edges stretched, centre stretched. */
    public static void nineSlice(GuiGraphics g, ResourceLocation tex,
                                 int x, int y, int w, int h,
                                 int sw, int sh,
                                 int u, int v, int uw, int vh,
                                 int tw, int th) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int cw = Math.min(sw, w / 2);
        int ch = Math.min(sh, h / 2);
        int iw = uw - sw * 2;
        int ih = vh - sh * 2;

        blit(g, tex, x, y, cw, ch, u, v, cw, ch, tw, th);
        blit(g, tex, x + w - cw, y, cw, ch, u + uw - cw, v, cw, ch, tw, th);
        blit(g, tex, x, y + h - ch, cw, ch, u, v + vh - ch, cw, ch, tw, th);
        blit(g, tex, x + w - cw, y + h - ch, cw, ch, u + uw - cw, v + vh - ch, cw, ch, tw, th);

        blit(g, tex, x + cw, y, w - cw * 2, ch, u + sw, v, iw, ch, tw, th);
        blit(g, tex, x + cw, y + h - ch, w - cw * 2, ch, u + sw, v + vh - ch, iw, ch, tw, th);
        blit(g, tex, x, y + ch, cw, h - ch * 2, u, v + sh, cw, ih, tw, th);
        blit(g, tex, x + w - cw, y + ch, cw, h - ch * 2, u + uw - cw, v + sh, cw, ih, tw, th);

        blit(g, tex, x + cw, y + ch, w - cw * 2, h - ch * 2, u + sw, v + sh, iw, ih, tw, th);
    }

    /** 256 x 256 window background with 4 px corners. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        nineSlice(g, PANEL, x, y, w, h, 4, 4, 0, 0, 256, 256, 256, 256);
    }

    /** 18 x 18 slot texture drawn at (x - 1, y - 1) around a 16 x 16 item cell. */
    public static void slot(GuiGraphics g, int x, int y) {
        g.blit(SLOT, x - 1, y - 1, 0f, 0f, 18, 18, 18, 18);
    }

    public static void slot(GuiGraphics g, int x, int y, boolean hovered) {
        slot(g, x, y);
        if (hovered) {
            g.fill(x, y, x + 16, y + 16, SLOT_HOVER);
        }
    }

    /** 200 x 60 three-state button texture, 20 x 4 corners. */
    public static void button(GuiGraphics g, int x, int y, int w, int h, boolean active, boolean hovered) {
        int state = !active ? 0 : (hovered ? 2 : 1);
        nineSlice(g, BUTTON, x, y, w, h, 20, 4, 0, state * 20, 200, 20, 200, 60);
    }

    /** Centered button caption; shrinks slightly when the label is too wide. */
    public static void buttonLabel(GuiGraphics g, Font font, Component text,
                                   int x, int y, int w, int h, boolean active) {
        int color = active ? BUTTON_TEXT : BUTTON_TEXT_DISABLED;
        int textWidth = font.width(text);
        int maxWidth = Math.max(1, w - 4);
        float scale = Math.min(1f, maxWidth / (float) Math.max(1, textWidth));
        float textX = x + w / 2f - textWidth * scale / 2f;
        float textY = y + (h - 8f * scale) / 2f;

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, Math.round(textX / scale), Math.round(textY / scale), color, false);
        g.pose().popPose();
    }

    /** Horizontal bar: 5 x 5 frame with 2 px 9-slice, 52 x 4 fill cropped by source width. */
    public static void barH(GuiGraphics g, int x, int y, int w, int h, float level) {
        nineSlice(g, BAR_FRAME, x, y, w, h, 2, 2, 0, 0, 5, 5, 5, 5);
        if (w <= 2 || h <= 2) {
            return;
        }
        int innerW = w - 2;
        int innerH = h - 2;
        int src = (int) (Mth.clamp(level, 0f, 1f) * 52f);
        if (src <= 0) {
            return;
        }
        int dst = Math.max(1, Math.round(src * (innerW / 52f)));
        g.blit(BAR_FILL_H, x + 1, y + 1, dst, innerH, 0f, 0f, src, 4, 52, 4);
    }

    /** Vertical bar, filling from the bottom; 4 x 52 fill cropped by source height. */
    public static void barV(GuiGraphics g, int x, int y, int w, int h, float level) {
        nineSlice(g, BAR_FRAME, x, y, w, h, 2, 2, 0, 0, 5, 5, 5, 5);
        if (w <= 2 || h <= 2) {
            return;
        }
        int innerW = w - 2;
        int innerH = h - 2;
        int src = (int) (Mth.clamp(level, 0f, 1f) * 52f);
        if (src <= 0) {
            return;
        }
        int dst = Math.max(1, Math.round(src * (innerH / 52f)));
        g.blit(BAR_FILL_V, x + 1, y + 1 + innerH - dst, innerW, dst, 0f, 0f, 4, src, 4, 52);
    }

    /** Gauge: coloured 5 x 5 frame, solid fill, then the tick overlay on top. */
    public static void gauge(GuiGraphics g, int x, int y, int w, int h, float level, int color) {
        nineSlice(g, GAUGE_FRAME, x, y, w, h, 2, 2, 0, 0, 5, 5, 5, 5);
        if (w <= 2 || h <= 2) {
            return;
        }
        int innerW = w - 2;
        int innerH = h - 2;
        int fill = Math.round(Mth.clamp(level, 0f, 1f) * innerH);
        if (fill > 0) {
            g.fill(x + 1, y + 1 + innerH - fill, x + 1 + innerW, y + 1 + innerH, color);
        }
        g.blit(TICKS, x + 1, y + 1, 0f, 0f, innerW, innerH, 16, 58);
    }

    /** Compact one-line tooltip drawn at the cursor. */
    public static void tooltip(GuiGraphics g, Font font, Component text, int mouseX, int mouseY) {
        tooltip(g, font, List.of(text), mouseX, mouseY, 0xFFFFFFFF);
    }

    /** Multi-line tooltip: the first line is white, the rest use {@code secondaryColor}. */
    public static void tooltip(GuiGraphics g, Font font, List<Component> lines, int mouseX, int mouseY,
                               int secondaryColor) {
        if (lines.isEmpty()) {
            return;
        }
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int lineHeight = 10;
        int x = mouseX + 12;
        int y = mouseY - 12;
        int right = x + width + 4;
        int bottom = y + lines.size() * lineHeight;

        g.fill(x - 4, y - 4, right, bottom, TOOLTIP_BG);
        g.fill(x - 4, y - 4, right, y - 3, TOOLTIP_BORDER_LIGHT);
        g.fill(x - 4, bottom - 1, right, bottom, TOOLTIP_BORDER_DARK);
        g.fill(x - 4, y - 4, x - 3, bottom, TOOLTIP_BORDER_LIGHT);
        g.fill(right - 1, y - 4, right, bottom, TOOLTIP_BORDER_DARK);
        for (int index = 0; index < lines.size(); index++) {
            int color = index == 0 ? 0xFFFFFFFF : secondaryColor;
            g.drawString(font, lines.get(index), x, y + index * lineHeight, color, true);
        }
    }

    private static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
                             int u, int v, int uw, int vh, int tw, int th) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.blit(tex, x, y, w, h, (float) u, (float) v, uw, vh, tw, th);
    }
}
