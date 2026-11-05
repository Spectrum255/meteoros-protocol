package com.spectrum255.meteorosprotocol.particle;

/**
 * A mixed particle buffer. All amounts are milli-PU (mPU, 1 PU = 1000 mPU).
 *
 * <p>Unlike {@link PhaseStorage}, the mixed buffer is a composition of every
 * registered phase; its total is the sum of the per-phase amounts. The capacity
 * returned by {@link #getCapacity(Phase)} is a single shared whole-buffer limit
 * (the same value for every phase), not a per-phase cap. Implementations must
 * clamp {@link #insert(Phase, long, boolean)} against the total already stored
 * so an external capability can never exceed that limit.</p>
 */
public interface MixedStorage {
    long getAmount(Phase phase);

    long getCapacity(Phase phase);

    long insert(Phase phase, long amount, boolean simulate);

    long extract(Phase phase, long amount, boolean simulate);

    default long getTotal() {
        long total = 0L;
        for (Phase phase : Phase.all()) {
            total += getAmount(phase);
        }
        return total;
    }

    /**
     * Container composition in percent (0-100), indexed by
     * {@link Phase#index()}. This is a UI/machine ratio, not a field sample
     * share vector; an empty buffer returns all zeros.
     */
    default float[] shares() {
        long[] amounts = new long[Phase.all().size()];
        for (Phase phase : Phase.all()) {
            amounts[phase.index()] = getAmount(phase);
        }
        return ParticleMath.sharesFromAmounts(amounts);
    }
}
