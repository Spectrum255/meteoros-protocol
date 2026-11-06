package com.spectrum255.meteorosprotocol.compat.jade;

import com.spectrum255.meteorosprotocol.particle.ParticleFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.IElement;

/**
 * One compact Jade element that shows every phase in a single strip: segments
 * are laid out left to right in {@code Phase.all()} order and their widths are
 * proportional to each phase's share of the stored total. The part of the bar
 * beyond the filled fraction (total / capacity) stays grey.
 *
 * <p>Jade's {@code IElementHelper.progress(...)} can only draw one colour, so
 * this element implements {@link IElement} directly. Jade has no public base
 * class for elements; the remaining methods are plain value setters/getters.</p>
 *
 * <p>The bar mirrors vanilla/Jade energy meters: it is 14 px tall (the same as
 * {@code ProgressElement} with a text line) and its width is derived from the
 * compact {@code total / capacity} label so the number sits inside the bar
 * instead of taking a separate tooltip row.</p>
 */
public final class MixedStripElement implements IElement {
    private static final int BORDER_COLOR = 0xFF373737;
    private static final int TRACK_COLOR = 0xFF555555;
    /** Same height as {@code ProgressElement} when it carries a text line. */
    private static final float DEFAULT_HEIGHT = 14.0F;
    /** Jade's own progress bar never gets narrower than this. */
    private static final float MIN_WIDTH = 20.0F;
    private static final int TEXT_COLOR = 0xFFFFFF;

    private final int[] colors;
    private final long[] amounts;
    private final long total;
    private final long capacity;
    private final Component text;
    private final float preferredWidth;

    private Vec2 size;
    private Vec2 translation = Vec2.ZERO;
    private IElement.Align alignment = IElement.Align.LEFT;
    private ResourceLocation tag;
    private String message;

    /**
     * @param colors   one ARGB colour per phase, in {@code Phase.all()} order
     * @param amounts  stored amount per phase, same order
     * @param total    sum of {@code amounts}; the segment widths use it as 100%
     * @param capacity displayed fill ceiling (sum of the phase tank capacities,
     *                 or the mixed-buffer capacity when falling back to that)
     */
    public MixedStripElement(int[] colors, long[] amounts, long total, long capacity) {
        this.colors = colors;
        this.amounts = amounts;
        this.total = total;
        this.capacity = capacity;
        this.text = Component.literal(ParticleFormat.formatMPU(total) + " / "
                + ParticleFormat.formatMPU(capacity));
        Font font = Minecraft.getInstance().font;
        // Same geometry as Jade's own bar: 2 px border, 3 px padding right of the label.
        this.preferredWidth = Math.max(MIN_WIDTH, font.width(this.text) + 5.0F);
        this.size = new Vec2(preferredWidth, DEFAULT_HEIGHT);
    }

    @Override
    public IElement size(Vec2 size) {
        this.size = size;
        return this;
    }

    @Override
    public Vec2 getSize() {
        return size;
    }

    @Override
    public Vec2 getCachedSize() {
        return size;
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float w, float h) {
        // Jade hands elements the full remaining row width for stretchy content
        // (see ProgressElement, which sizes itself from getCachedSize()). Cap the
        // drawn width at our own preferred size so the bar stays text-sized.
        int width = Math.min(Math.round(size.x), Math.round(preferredWidth));
        int height = Math.min(Math.round(size.y), Math.round(DEFAULT_HEIGHT));
        if (width <= 0 || height <= 0) {
            return;
        }
        int left = Math.round(x);
        int top = Math.round(y);
        graphics.fill(left, top, left + width, top + height, BORDER_COLOR);

        int innerX = left + 1;
        int innerY = top + 1;
        int innerWidth = width - 2;
        int innerHeight = height - 2;
        if (innerWidth <= 0 || innerHeight <= 0) {
            return;
        }
        graphics.fill(innerX, innerY, innerX + innerWidth, innerY + innerHeight, TRACK_COLOR);
        if (total > 0L) {
            drawSegments(graphics, innerX, innerY, innerWidth, innerHeight);
        }
        drawText(graphics, left, top, width, height);
    }

    private void drawSegments(GuiGraphics graphics, int innerX, int innerY,
                              int innerWidth, int innerHeight) {
        // The strip is a composition view: the segments always span the whole inner
        // width (proportional to each phase's share of `total`). The absolute
        // total/capacity ratio lives in the embedded label instead of shrinking the
        // fill to a sliver, which used to look like an empty grey box when a machine
        // only held a few thousand mPU out of 48,000,000.
        int[] widths = new int[amounts.length];
        long cumulative = 0L;
        int previousEdge = 0;
        for (int index = 0; index < amounts.length; index++) {
            if (amounts[index] <= 0L) {
                continue;
            }
            cumulative += amounts[index];
            int edge = (int) Math.min(innerWidth, Math.round(innerWidth * (double) cumulative / total));
            widths[index] = edge - previousEdge;
            previousEdge = edge;
        }

        // Any non-zero phase must stay visible; borrow one pixel from the widest
        // segment. Skip when there is simply not enough room for every phase.
        for (int index = 0; index < widths.length; index++) {
            if (amounts[index] <= 0L || widths[index] > 0) {
                continue;
            }
            int donor = -1;
            for (int candidate = 0; candidate < widths.length; candidate++) {
                if (widths[candidate] > 1 && (donor < 0 || widths[candidate] > widths[donor])) {
                    donor = candidate;
                }
            }
            if (donor < 0) {
                break;
            }
            widths[donor]--;
            widths[index] = 1;
        }

        int segmentX = innerX;
        for (int index = 0; index < widths.length; index++) {
            int segmentWidth = widths[index];
            if (segmentWidth <= 0) {
                continue;
            }
            graphics.fill(segmentX, innerY, segmentX + segmentWidth, innerY + innerHeight, colors[index]);
            segmentX += segmentWidth;
        }
    }

    private void drawText(GuiGraphics graphics, int x, int y, int width, int height) {
        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(this.text);
        if (textWidth <= 0) {
            return;
        }
        float scale = Math.min(1.0F, (width - 4) / (float) textWidth);
        float textX = x + width / 2.0F;
        float textY = y + (height - 8.0F * scale) / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, this.text, -textWidth / 2, 0, TEXT_COLOR, true);
        graphics.pose().popPose();
    }

    @Override
    public IElement align(IElement.Align align) {
        this.alignment = align;
        return this;
    }

    @Override
    public IElement.Align getAlignment() {
        return alignment;
    }

    @Override
    public IElement translate(Vec2 translation) {
        this.translation = translation;
        return this;
    }

    @Override
    public Vec2 getTranslation() {
        return translation;
    }

    @Override
    public IElement tag(ResourceLocation tag) {
        this.tag = tag;
        return this;
    }

    @Override
    public ResourceLocation getTag() {
        return tag;
    }

    @Override
    public String getCachedMessage() {
        return message;
    }

    @Override
    public IElement clearCachedMessage() {
        message = null;
        return this;
    }

    @Override
    public IElement message(String message) {
        this.message = message;
        return this;
    }
}
