package com.spectrum255.meteorosprotocol.client.gui;

import com.spectrum255.meteorosprotocol.machine.MachineBlockEntity;
import com.spectrum255.meteorosprotocol.particle.Phase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * One clickable phase gauge on the intake tower screen.
 *
 * <p>Making the gauge a real {@link AbstractWidget} hands hit-testing and click
 * dispatch back to vanilla ({@code AbstractWidget.mouseClicked} calls
 * {@link #onClick}), so a 6 px wide target no longer depends on a hand-rolled
 * {@code mouseClicked} branch that container screens can swallow. The widget is
 * as wide as {@code PHASE_STEP} (6 px), so the 1 px gap between gauges belongs
 * to a bar instead of being dead space.
 */
public class PhaseBarWidget extends AbstractWidget {
    private static final int SELECTED_FRAME_THICKNESS = 2;
    private static final int HOVER_WASH = 0x30FFFFFF;

    private final int index;
    private final LongSupplier amount;
    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;

    public PhaseBarWidget(int index, int x, int y, int width, int height,
                          LongSupplier amount, IntSupplier selectedIndex, IntConsumer onSelect) {
        super(x, y, width, height, Phase.all().get(index).displayName());
        this.index = index;
        this.amount = amount;
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
    }

    public int index() {
        return this.index;
    }

    public long amount() {
        return Math.max(0L, this.amount.getAsLong());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Phase phase = Phase.all().get(this.index);
        long value = this.amount();
        float level = (float) Math.min(1.0, (double) value / MachineBlockEntity.MIXED_CAPACITY);
        MeteorosGui.barV(graphics, getX(), getY(), getWidth(), getHeight(), level, phase.argb());

        if (this.selectedIndex.getAsInt() == this.index) {
            drawSelectedFrame(graphics);
        }
        if (isHovered) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), HOVER_WASH);
        }
    }

    /** 2 px white frame around the gauge chosen as the extraction phase. */
    private void drawSelectedFrame(GuiGraphics graphics) {
        int left = getX() - SELECTED_FRAME_THICKNESS;
        int top = getY() - SELECTED_FRAME_THICKNESS;
        int right = getX() + getWidth() + SELECTED_FRAME_THICKNESS;
        int bottom = getY() + getHeight() + SELECTED_FRAME_THICKNESS;
        int thickness = SELECTED_FRAME_THICKNESS;

        graphics.fill(left, top, right, top + thickness, MeteorosGui.SELECTED_FRAME);
        graphics.fill(left, bottom - thickness, right, bottom, MeteorosGui.SELECTED_FRAME);
        graphics.fill(left, top, left + thickness, bottom, MeteorosGui.SELECTED_FRAME);
        graphics.fill(right - thickness, top, right, bottom, MeteorosGui.SELECTED_FRAME);
    }

    /**
     * Vanilla's {@code onClick(double, double)} hook is deprecated in 1.21.1, so
     * the widget handles its own primary click and leaves the rest to vanilla.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && clicked(mouseX, mouseY)) {
            this.onSelect.accept(this.index);
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE, Component.translatable("narration.button.usage"));
    }
}
