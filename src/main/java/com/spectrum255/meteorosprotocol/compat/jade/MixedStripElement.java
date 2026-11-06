package com.spectrum255.meteorosprotocol.compat.jade;

import net.minecraft.client.gui.GuiGraphics;
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
 */
public final class MixedStripElement implements IElement {
    private static final int BORDER_COLOR = 0xFF373737;
    private static final int TRACK_COLOR = 0xFF555555;
    private static final float DEFAULT_WIDTH = 100.0F;
    private static final float DEFAULT_HEIGHT = 8.0F;

    private final int[] colors;
    private final long[] amounts;
    private final long total;
    private final long capacity;

    private Vec2 size = new Vec2(DEFAULT_WIDTH, DEFAULT_HEIGHT);
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
        int width = Math.round(w > 0.0F ? w : size.x);
        int height = Math.round(h > 0.0F ? h : size.y);
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
        if (total <= 0L || capacity <= 0L) {
            return;
        }

        int filled = (int) Math.min(innerWidth, Math.round(innerWidth * (double) total / capacity));
        if (filled <= 0) {
            return;
        }

        // Proportional widths (largest-remainder style cumulative rounding) so the
        // segments always add up to exactly `filled`.
        int[] widths = new int[amounts.length];
        long cumulative = 0L;
        int previousEdge = 0;
        for (int index = 0; index < amounts.length; index++) {
            if (amounts[index] <= 0L) {
                continue;
            }
            cumulative += amounts[index];
            int edge = (int) Math.min(filled, Math.round(innerWidth * (double) cumulative / total));
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
