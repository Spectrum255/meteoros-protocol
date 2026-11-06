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
 * One vertical phase-tank bar inside the intake tower tank.
 *
 * <p>The widget's hit box is 2 px wider than the visible bar (1 px on each
 * side), so the 2 px gaps between bars never swallow a click. Fill level,
 * colour and selection are live suppliers, which keeps the widget list stable
 * while the server data updates every tick.</p>
 */
public class PhaseBarWidget extends AbstractWidget {
    private static final int HIT_PADDING =
            (IntakeLayout.PHASE_BAR_HIT_WIDTH - IntakeLayout.PHASE_BAR_WIDTH) / 2;

    private final int index;
    private final DoubleSupplier fill;
    private final IntSupplier color;
    private final BooleanSupplier selected;
    private final IntConsumer onSelect;

    /**
     * @param barX   left edge of the visible bar (the hit box starts 1 px earlier)
     * @param barY   top edge of the visible bar (the hit box starts 1 px earlier)
     * @param index  phase index sent as button id {@code 2 + index}
     * @param fill   fill ratio in {@code [0, 1]}
     * @param color  ARGB bar colour (normally {@code phase.argb()})
     * @param selected whether this phase is the current extraction target
     * @param onSelect invoked with {@code index} when the bar is clicked
     */
    public PhaseBarWidget(int barX, int barY, int index, DoubleSupplier fill, IntSupplier color,
                          BooleanSupplier selected, IntConsumer onSelect) {
        super(barX - HIT_PADDING, barY - HIT_PADDING,
                IntakeLayout.PHASE_BAR_HIT_WIDTH,
                IntakeLayout.PHASE_BAR_HEIGHT + HIT_PADDING * 2,
                Component.empty());
        this.index = index;
        this.fill = fill;
        this.color = color;
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
        float level = Mth.clamp((float) this.fill.getAsDouble(), 0.0F, 1.0F);
        MeteorosGui.barV(graphics, barX, barY, IntakeLayout.PHASE_BAR_WIDTH,
                IntakeLayout.PHASE_BAR_HEIGHT, level, this.color.getAsInt());
        if (this.isHovered()) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x30FFFFFF);
        }
        if (this.selected.getAsBoolean()) {
            drawSelectionFrame(graphics);
        }
    }

    /** 2 px white frame around the hit box, matching the old band highlight. */
    private void drawSelectionFrame(GuiGraphics graphics) {
        int left = getX();
        int top = getY();
        int right = left + getWidth();
        int bottom = top + getHeight();
        graphics.fill(left, top, right, top + 2, MeteorosGui.SELECTED_FRAME);
        graphics.fill(left, bottom - 2, right, bottom, MeteorosGui.SELECTED_FRAME);
        graphics.fill(left, top, left + 2, bottom, MeteorosGui.SELECTED_FRAME);
        graphics.fill(right - 2, top, right, bottom, MeteorosGui.SELECTED_FRAME);
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
