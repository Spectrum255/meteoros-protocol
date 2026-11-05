package com.spectrum255.meteorosprotocol.client.tooltip;

import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

/**
 * Draws a {@link ParticleTooltip}: a 100x8 capacity bar with the phase colours
 * stacked left to right, the numeric total on its right, then at most six
 * per-phase rows. Used by the canister, the infinite canister and the filter
 * cartridge.
 */
public final class ParticleTooltipRenderer implements ClientTooltipComponent {
    private static final int BAR_WIDTH = 100;
    private static final int BAR_HEIGHT = 8;
    private static final int MIN_WIDTH = 108;
    private static final int MAX_ROWS = 6;
    private static final int ROW_HEIGHT = 10;
    private static final int COLOR_BORDER = 0xFF000000;
    private static final int COLOR_TRACK = 0xFF8B8B8B;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_ROW_TEXT = 0xFF555555;
    private static final int COLOR_DIM_TEXT = 0xFF777777;

    private final ParticleTooltip tooltip;
    private final long[] amounts;
    private final List<Phase> rows;
    private final int hidden;

    public ParticleTooltipRenderer(ParticleTooltip tooltip) {
        this.tooltip = tooltip;
        this.amounts = tooltip.amounts();
        List<Phase> registered = Phase.all();
        List<Phase> listed = new ArrayList<>();
        int nonZero = 0;
        int limit = Math.min(registered.size(), amounts.length);
        for (int index = 0; index < limit; index++) {
            if (amounts[index] > 0L) {
                nonZero++;
                if (listed.size() < MAX_ROWS) {
                    listed.add(registered.get(index));
                }
            }
        }
        // Amounts past the current registry size (client registry not synced yet) are ignored.
        this.rows = List.copyOf(listed);
        this.hidden = Math.max(0, nonZero - listed.size());
    }

    @Override
    public int getHeight() {
        int visibleRows = rows.isEmpty() ? 1 : rows.size() + (hidden > 0 ? 1 : 0);
        return 9 + visibleRows * ROW_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return Math.max(MIN_WIDTH, BAR_WIDTH + 4 + font.width(barText()));
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        drawBar(font, x, y, graphics);
        int rowY = y + 9;
        if (rows.isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable("tooltip.meteorosprotocol.bar.empty").getString(),
                    x,
                    rowY,
                    COLOR_DIM_TEXT,
                    false
            );
            return;
        }
        for (Phase phase : rows) {
            graphics.fill(x, rowY, x + 6, rowY + 6, phase.argb());
            graphics.drawString(font, rowText(phase), x + 9, rowY, COLOR_ROW_TEXT, false);
            rowY += ROW_HEIGHT;
        }
        if (hidden > 0) {
            String more = Component.translatable("tooltip.meteorosprotocol.bar.more", hidden).getString();
            graphics.drawString(font, more, x, rowY, COLOR_DIM_TEXT, false);
        }
    }

    private void drawBar(Font font, int x, int y, GuiGraphics graphics) {
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_BORDER);
        int innerX = x + 1;
        int innerY = y + 1;
        int innerWidth = BAR_WIDTH - 2;
        int innerHeight = BAR_HEIGHT - 2;
        graphics.fill(innerX, innerY, innerX + innerWidth, innerY + innerHeight, COLOR_TRACK);

        List<Phase> registered = Phase.all();
        int limit = Math.min(registered.size(), amounts.length);
        if (tooltip.infinite()) {
            for (int index = 0; index < limit; index++) {
                if (amounts[index] > 0L) {
                    graphics.fill(innerX, innerY, innerX + innerWidth, innerY + innerHeight, registered.get(index).argb());
                    break;
                }
            }
        } else if (tooltip.capacity() > 0L) {
            long cumulative = 0L;
            int previous = 0;
            for (int index = 0; index < limit; index++) {
                cumulative += amounts[index];
                int filled = (int) Math.min(innerWidth, cumulative * innerWidth / tooltip.capacity());
                if (filled > previous) {
                    graphics.fill(innerX + previous, innerY, innerX + filled, innerY + innerHeight, registered.get(index).argb());
                    previous = filled;
                }
            }
        }

        graphics.drawString(font, barText(), x + BAR_WIDTH + 2, y, COLOR_TEXT, false);
    }

    private String barText() {
        if (tooltip.infinite()) {
            return "∞";
        }
        if (tooltip.selection()) {
            return tooltip.total() + " / " + tooltip.capacity();
        }
        return String.format(Locale.ROOT, "%,d / %,d mPU", tooltip.total(), tooltip.capacity());
    }

    private String rowText(Phase phase) {
        String name = phase.shortName().getString();
        if (tooltip.selection()) {
            return name;
        }
        int index = phase.index();
        long amount = index >= 0 && index < amounts.length ? amounts[index] : 0L;
        if (tooltip.infinite()) {
            return name + " ∞";
        }
        double percent = tooltip.capacity() > 0L ? amount * 100.0 / tooltip.capacity() : 0.0;
        return String.format(Locale.ROOT, "%s %,d (%.1f%%)", name, amount, percent);
    }
}
