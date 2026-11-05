package com.spectrum255.meteorosprotocol.particle;

/**
 * A per-phase tank. All amounts are milli-PU (mPU, 1 PU = 1000 mPU).
 *
 * <p>Capabilities expose this interface to pipes and other machines, so the
 * unit must stay consistent: callers pass and receive mPU, never whole PU.</p>
 */
public interface PhaseStorage {
    long getAmount(Phase phase);

    long getCapacity(Phase phase);

    long insert(Phase phase, long amount, boolean simulate);

    long extract(Phase phase, long amount, boolean simulate);

    default boolean isEmpty() {
        for (Phase phase : Phase.all()) {
            if (getAmount(phase) > 0L) {
                return false;
            }
        }
        return true;
    }

    default long getSpace(Phase phase) {
        return Math.max(0L, getCapacity(phase) - getAmount(phase));
    }
}
