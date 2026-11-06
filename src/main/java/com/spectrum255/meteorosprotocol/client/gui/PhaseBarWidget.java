package com.spectrum255.meteorosprotocol.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * One hollow vertical phase-tank bar inside the intake tower tank.
 *
 * <p>The visible bar is drawn as an inventory-slot-like well: the phase's dim
 * colour fills the cell, a sunken 1 px border sits on it, and the bright phase
 * colour rises from the bottom. The hit box spans the full 11 px step, so the
 * 1 px gaps between bars never swallow a click. Fill level, colours and
 * selection are live suppliers, which keeps the widget list stable while the
 * server data updates every tick.</p>
 */
public class PhaseBarWidget extends AbstractWidget {
    private static final int HIT_PADDING =
            (IntakeLayout.PHASE_BAR_HIT_WIDTH - IntakeLayout.PHASE_BAR_WIDTH) / 2;

    private final int index;
    private final DoubleSupplier fill;
    private final IntSupplier color;
    private final IntSupplier darkColor;
    private final BooleanSupplier selected;
    private final IntConsumer onSelect;

    /**
     * @param barX      left edge of the visible bar (the hit box may start earlier)
     * @param barY      top edge of the visible bar
     * @param index     phase index sent as button id {@code 2 + index}
     * @param fill      fill ratio in {@code [0, 1]}
     * @param color     ARGB bar colour (normally {@code phase.argb()})
     * @param darkColor ARGB dim track colour (normally {@code phase.darkArgb()})
     * @param selected  whether this phase is the current extraction target
     * @param onSelect  invoked with {@code index} when the bar is clicked
     */
    public PhaseBarWidget(int barX, int barY, int index, DoubleSupplier fill, IntSupplier color,
                          IntSupplier darkColor, BooleanSupplier selected, IntConsumer onSelect) {
        super(barX - HIT_PADDING, barY - HIT_PADDING,
                IntakeLayout.PHASE_BAR_HIT_WIDTH,
                IntakeLayout.PHASE_BAR_HEIGHT + HIT_PADDING * 2,
                Component.empty());
        this.index = index;
        this.fill = fill;
        this.color = color;
        this.darkColor = darkColor;
        this.selected = selected;
        this.onSelect = onSelect;
    }

    public int index() {
        return this.index;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int barX = getX() + HIT_PADDING;
        int barY = getY() + HIT_PADDING;
        int barW = IntakeLayout.PHASE_BAR_WIDTH;
        int barH = IntakeLayout.PHASE_BAR_HEIGHT;
        float level = Mth.clamp((float) this.fill.getAsDouble(), 0.0F, 1.0F);
        MeteorosGui.phaseBarV(graphics, barX, barY, barW, barH, level,
                this.color.getAsInt(), this.darkColor.getAsInt());
        if (this.isHovered()) {
            // Hover wash covers the visible bar only, not the wider hit box.
            graphics.fill(barX, barY, barX + barW, barY + barH, 0x30FFFFFF);
        }
        if (this.selected.getAsBoolean()) {
            drawSelectionFrame(graphics, barX, barY, barW, barH);
        }
    }

    /** 1 px white frame drawn over the bar's own sunken border (never spills into neighbours). */
    private static void drawSelectionFrame(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + 1, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x, y + h - 1, x + w, y + h, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x, y, x + 1, y + h, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x + w - 1, y, x + w, y + h, MeteorosGui.SELECTED_FRAME);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !this.clicked(mouseX, mouseY)) {
            return false;
        }
        this.onSelect.accept(this.index);
        this.playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
