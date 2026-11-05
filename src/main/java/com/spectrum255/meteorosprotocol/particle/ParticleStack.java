package com.spectrum255.meteorosprotocol.particle;

/**
 * A stack of one particle phase. {@code amount} is milli-PU (mPU, 1 PU = 1000 mPU).
 */
public record ParticleStack(Phase phase, long amount) {
    public ParticleStack {
        if (phase == null) {
            throw new IllegalArgumentException("phase");
        }
        if (amount < 0L) {
            throw new IllegalArgumentException("negative amount");
        }
    }

    public boolean isEmpty() {
        return amount == 0L;
    }

    public ParticleStack copy() {
        return new ParticleStack(phase, amount);
    }
}
