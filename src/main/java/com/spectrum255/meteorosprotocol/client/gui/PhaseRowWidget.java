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
 * One scrolling horizontal phase row inside the intake tower panel.
 *
 * <p>Rows are real widgets so vanilla container dispatch handles the click (the
 * 0.0.5 bug was a hand-rolled {@code mouseClicked} hit test that the container
 * screen swallowed). The intake screen positions each row from the current
 * scroll offset and hides the ones outside the four-row window, which also
 * removes their hit box automatically.</p>
 *
 * <p>Render order: 1 px sunken border (dark top/left, white bottom/right), the
 * phase's dim colour as the empty track, then its bright colour filling from the
 * left by tank ratio, a hover wash and finally a white selection frame when this
 * phase is the current extraction target. Rows intentionally draw no text — the
 * numbers live in the tooltip only, so they can never cover the panel.</p>
 */
public class PhaseRowWidget extends AbstractWidget {
    private final int index;
    private final DoubleSupplier fill;
    private final IntSupplier color;
    private final IntSupplier darkColor;
    private final BooleanSupplier selected;
    private final IntConsumer onSelect;

    /**
     * @param x         left edge of the row in screen coordinates
     * @param y         top edge of the row in screen coordinates
     * @param width     row width ({@link IntakeLayout#PHASE_ROW_WIDTH})
     * @param height    row height ({@link IntakeLayout#PHASE_ROW_HEIGHT})
     * @param index     phase index sent as button id {@code 2 + index}
     * @param fill      tank fill ratio in {@code [0, 1]}
     * @param color     ARGB fill colour (normally {@code phase.argb()})
     * @param darkColor ARGB empty-track colour (normally {@code phase.darkArgb()})
     * @param selected  whether this phase is the current extraction target
     * @param onSelect  invoked with {@code index} when the row is clicked
     */
    public PhaseRowWidget(int x, int y, int width, int height, int index,
                          DoubleSupplier fill, IntSupplier color, IntSupplier darkColor,
                          BooleanSupplier selected, IntConsumer onSelect) {
        super(x, y, width, height, Component.empty());
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
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        // Sunken well: white base, then a dark top/left border one pixel in from
        // the right/bottom edge, leaving the white rim visible on those sides.
        graphics.fill(x, y, x + width, y + height, MeteorosGui.SLOT_LIGHT);
        graphics.fill(x, y, x + width - 1, y + height - 1, MeteorosGui.SLOT_DARK);
        float level = Mth.clamp((float) this.fill.getAsDouble(), 0.0F, 1.0F);
        int innerWidth = Math.max(1, width - 2);
        int innerHeight = Math.max(1, height - 2);
        graphics.fill(x + 1, y + 1, x + 1 + innerWidth, y + 1 + innerHeight, this.darkColor.getAsInt());
        int filled = Mth.clamp(Math.round(innerWidth * level), 0, innerWidth);
        if (filled > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filled, y + 1 + innerHeight, this.color.getAsInt());
        }
        if (this.isHovered()) {
            graphics.fill(x, y, x + width, y + height, MeteorosGui.ROW_HIGHLIGHT);
        }
        if (this.selected.getAsBoolean()) {
            drawSelectionFrame(graphics, x, y, width, height);
        }
    }

    /** 1 px white frame on all four edges, drawn inside the row so it never spills. */
    private static void drawSelectionFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + 1, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x, y + height - 1, x + width, y + height, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x, y, x + 1, y + height, MeteorosGui.SELECTED_FRAME);
        graphics.fill(x + width - 1, y, x + width, y + height, MeteorosGui.SELECTED_FRAME);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // AbstractWidget.onClick is deprecated on 1.21.1; override mouseClicked and
        // rely on clicked() to reject invisible rows.
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
