package com.spectrum255.meteorosprotocol.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * Client tooltip payload for particle-storing items: the total/capacity bar
 * plus the per-phase breakdown. Deliberately free of client-only classes so it
 * can be built from common code and shipped through the tooltip element list.
 */
public final class ParticleTooltip implements TooltipComponent {
    private final long total;
    private final long capacity;
    private final long[] amounts;
    private final boolean infinite;
    private final boolean selection;

    public ParticleTooltip(long total, long capacity, long[] amounts, boolean infinite) {
        this(total, capacity, amounts, infinite, false);
    }

    /**
     * @param selection true for the filter cartridge: {@code amounts} are 1/0
     *                  flags and the rows list selected phases without numbers
     */
    public ParticleTooltip(long total, long capacity, long[] amounts, boolean infinite, boolean selection) {
        this.total = Math.max(0L, total);
        this.capacity = Math.max(0L, capacity);
        this.amounts = amounts == null ? new long[0] : amounts.clone();
        this.infinite = infinite;
        this.selection = selection;
    }

    public long total() {
        return total;
    }

    public long capacity() {
        return capacity;
    }

    /** Copy of the per-phase amounts, in {@code Phase.all()} order. */
    public long[] amounts() {
        return amounts.clone();
    }

    public boolean infinite() {
        return infinite;
    }

    public boolean selection() {
        return selection;
    }
}
