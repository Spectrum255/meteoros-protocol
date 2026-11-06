package com.spectrum255.meteorosprotocol.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Shared drawing kit for Meteoros Protocol machine screens.
 *
 * <p>All primitives now mimic the vanilla container look (the {@code #C6C6C6}
 * panel, sunken {@code #8B8B8B} slots, {@code #373737} frames), so screens keep
 * the familiar inventory styling without depending on custom GUI textures. The
 * texture constants below are kept for the art pipeline and for any future
 * textured variant, but the vanilla-style screens no longer blit them.
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

    /** Vanilla container colours. */
    public static final int PANEL_BODY = 0xFFC6C6C6;
    public static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
    public static final int PANEL_SHADOW = 0xFF555555;
    public static final int SLOT_DARK = 0xFF373737;
    public static final int SLOT_LIGHT = 0xFFFFFFFF;
    public static final int SLOT_BODY = 0xFF8B8B8B;
    public static final int LABEL_TEXT = 0xFF404040;

    /** Slot hover wash, same value the vanilla container uses. */
    public static final int SLOT_HOVER = 0x80FFFFFF;
    /** Tooltip body / border colours. */
    private static final int TOOLTIP_BG = 0xF0100010;
    private static final int TOOLTIP_BORDER_LIGHT = 0x505000FF;
    private static final int TOOLTIP_BORDER_DARK = 0x5028007F;
    public static final int BUTTON_TEXT = 0xFF303030;
    public static final int BUTTON_TEXT_DISABLED = 0xFF9A9A9A;

    /** Reused shades for the vanilla-style extras (muted outlines, row hover, scrollbar). */
    public static final int SLOT_MUTED = 0xFF6E6E6E;
    public static final int ROW_HIGHLIGHT = 0x20FFFFFF;
    public static final int SELECTED_FRAME = 0xFFFFFFFF;
    public static final int TEXT_DIM = 0xFF808080;

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

    /**
     * Vanilla container panel: 1 px black outline, {@code #C6C6C6} body, white
     * inner highlight on the top/left edges and {@code #555555} shading on the
     * bottom/right edges.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, 0xFF000000);
        if (w <= 2 || h <= 2) {
            return;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PANEL_BODY);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, PANEL_HIGHLIGHT);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, PANEL_HIGHLIGHT);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, PANEL_SHADOW);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, PANEL_SHADOW);
    }

    /** 18 x 18 sunken slot drawn at (x - 1, y - 1) around a 16 x 16 item cell. */
    public static void slot(GuiGraphics g, int x, int y) {
        int sx = x - 1;
        int sy = y - 1;
        g.fill(sx, sy, sx + 18, sy + 18, SLOT_BODY);
        g.fill(sx, sy, sx + 18, sy + 1, SLOT_DARK);
        g.fill(sx, sy, sx + 1, sy + 18, SLOT_DARK);
        g.fill(sx, sy + 17, sx + 18, sy + 18, SLOT_LIGHT);
        g.fill(sx + 17, sy, sx + 18, sy + 18, SLOT_LIGHT);
    }

    public static void slot(GuiGraphics g, int x, int y, boolean hovered) {
        slot(g, x, y);
        if (hovered) {
            g.fill(x, y, x + 16, y + 16, SLOT_HOVER);
        }
    }

    /** 200 x 60 three-state button texture, 20 x 4 corners (unused by vanilla-style screens). */
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

    /** Horizontal bar: 1 px {@code #373737} frame, {@code #8B8B8B} track, coloured fill. */
    public static void barH(GuiGraphics g, int x, int y, int w, int h, float level, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        if (w <= 2 || h <= 2) {
            return;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BODY);
        int innerW = w - 2;
        int fill = Math.round(Mth.clamp(level, 0f, 1f) * innerW);
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, color);
        }
    }

    /** Vertical bar, filling from the bottom. */
    public static void barV(GuiGraphics g, int x, int y, int w, int h, float level, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        if (w <= 2 || h <= 2) {
            return;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BODY);
        int innerH = h - 2;
        int fill = Math.round(Mth.clamp(level, 0f, 1f) * innerH);
        if (fill > 0) {
            g.fill(x + 1, y + h - 1 - fill, x + w - 1, y + h - 1, color);
        }
    }

    /**
     * Hollow vertical phase bar: the whole cell is the phase's dim colour, a 1 px
     * sunken border (dark top/left, white bottom/right) sits on top, and the
     * bright fill rises from the bottom. Unlike {@link #barV} there is no outer
     * frame or grey track, so a row of bars reads like empty inventory slots.
     *
     * @param fillArgb  bright phase colour for the filled part
     * @param emptyArgb dim phase colour ({@link com.spectrum255.meteorosprotocol.particle.Phase#darkArgb()})
     */
    public static void phaseBarV(GuiGraphics g, int x, int y, int w, int h, float level,
                                 int fillArgb, int emptyArgb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, emptyArgb);
        if (w <= 2 || h <= 2) {
            return;
        }
        g.fill(x, y, x + w, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + h, SLOT_DARK);
        g.fill(x, y + h - 1, x + w, y + h, SLOT_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, SLOT_LIGHT);
        int innerH = h - 2;
        int fill = Math.round(Mth.clamp(level, 0f, 1f) * innerH);
        if (fill > 0) {
            g.fill(x + 1, y + h - 1 - fill, x + w - 1, y + h - 1, fillArgb);
        }
    }

    /**
     * Hollow horizontal bar, mirroring {@link #phaseBarV}: the whole cell is the
     * empty colour, a 1 px sunken border (dark left/top, white right/bottom) sits
     * on top, and the fill grows from the left edge.
     *
     * @param fillArgb  colour of the filled part
     * @param emptyArgb dim colour of the unfilled part
     */
    public static void phaseBarH(GuiGraphics g, int x, int y, int w, int h, float level,
                                 int fillArgb, int emptyArgb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        g.fill(x, y, x + w, y + h, emptyArgb);
        if (w <= 2 || h <= 2) {
            return;
        }
        g.fill(x, y, x + w, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + h, SLOT_DARK);
        g.fill(x, y + h - 1, x + w, y + h, SLOT_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, SLOT_LIGHT);
        int innerW = w - 2;
        int fill = Math.round(Mth.clamp(level, 0f, 1f) * innerW);
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, fillArgb);
        }
    }

    /**
     * Horizontal composition strip: segments proportional to {@code amounts},
     * the last non-empty phase takes the rounding remainder so the total width
     * never drifts. The caller owns the frame/background.
     */
    public static void strip(GuiGraphics g, int x, int y, int w, int h,
                             long[] amounts, long total, int[] colors) {
        if (w <= 0 || h <= 0 || total <= 0L || amounts == null || colors == null) {
            return;
        }
        int count = Math.min(amounts.length, colors.length);
        int last = lastNonEmpty(amounts, count);
        if (last < 0) {
            return;
        }
        int used = 0;
        for (int index = 0; index <= last; index++) {
            long amount = amounts[index];
            if (amount <= 0L) {
                continue;
            }
            int seg = index == last
                    ? w - used
                    : (int) Math.round((double) amount / total * w);
            seg = Math.max(0, Math.min(seg, w - used));
            if (seg > 0) {
                g.fill(x + used, y, x + used + seg, y + h, colors[index]);
                used += seg;
            }
        }
    }

    /** Vertical composition strip: segments stack upwards from the bottom edge. */
    public static void stripV(GuiGraphics g, int x, int y, int w, int h,
                              long[] amounts, long total, int[] colors) {
        if (w <= 0 || h <= 0 || total <= 0L || amounts == null || colors == null) {
            return;
        }
        int count = Math.min(amounts.length, colors.length);
        int last = lastNonEmpty(amounts, count);
        if (last < 0) {
            return;
        }
        int used = 0;
        for (int index = 0; index <= last; index++) {
            long amount = amounts[index];
            if (amount <= 0L) {
                continue;
            }
            int seg = index == last
                    ? h - used
                    : (int) Math.round((double) amount / total * h);
            seg = Math.max(0, Math.min(seg, h - used));
            if (seg > 0) {
                int bottom = y + h - used;
                g.fill(x, bottom - seg, x + w, bottom, colors[index]);
                used += seg;
            }
        }
    }

    private static int lastNonEmpty(long[] amounts, int count) {
        for (int index = count - 1; index >= 0; index--) {
            if (amounts[index] > 0L) {
                return index;
            }
        }
        return -1;
    }

    /** Vanilla container label: {@code #404040} without a drop shadow. */
    public static void label(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x, y, LABEL_TEXT, false);
    }

    /** Gauge: coloured 5 x 5 frame, solid fill, then the tick overlay on top (unused by vanilla-style screens). */
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
