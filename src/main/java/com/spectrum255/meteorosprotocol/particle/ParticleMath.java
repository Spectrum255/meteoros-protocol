package com.spectrum255.meteorosprotocol.particle;

/**
 * Array helpers for the phase model. All phase arrays are indexed by
 * {@link Phase#index()} and therefore follow {@link Phase#all()} order.
 *
 * <p>{@link #normalize(float[])} is a <em>container-only</em> helper: field
 * samples are absolute concentrations and must never be normalized. A
 * zero-input container stays zero (it is not distributed evenly).</p>
 */
public final class ParticleMath {
    private ParticleMath() {
    }

    private static void requirePhaseLength(int length) {
        if (length != Phase.all().size()) {
            throw new IllegalArgumentException("expected " + Phase.all().size() + " phases, got " + length);
        }
    }

    /**
     * Normalizes container weights to sum 1. Negative weights are treated as 0.
     * A zero-weight input returns an all-zero array, not a uniform fill.
     */
    public static float[] normalize(float[] weights) {
        requirePhaseLength(weights.length);
        float[] result = weights.clone();
        double sum = 0.0;
        for (int index = 0; index < result.length; index++) {
            result[index] = Math.max(0.0F, result[index]);
            sum += result[index];
        }
        if (sum <= 1.0E-9) {
            return new float[result.length];
        }
        for (int index = 0; index < result.length; index++) {
            result[index] = (float) (result[index] / sum);
        }
        return result;
    }

    /**
     * Splits {@code total} milli-PU into six long amounts proportional to
     * {@code shares}. Shares do not need to sum to 100; they are normalized
     * first. A zero/empty composition returns all zeros (nothing is moved).
     *
     * <p>Uses the largest-remainder method so the returned amounts always sum
     * exactly to {@code total} (when at least one share is positive).</p>
     */
    public static long[] amountsFromShares(long total, float[] shares) {
        if (total < 0L) {
            throw new IllegalArgumentException("negative total");
        }
        requirePhaseLength(shares.length);
        long[] result = new long[shares.length];
        if (total == 0L) {
            return result;
        }

        double shareSum = 0.0;
        for (float share : shares) {
            shareSum += Math.max(0.0F, share);
        }
        if (shareSum <= 0.0) {
            return result;
        }

        float[] normalized = normalize(shares);
        double[] exact = new double[result.length];
        double[] fractions = new double[result.length];
        long assigned = 0L;
        for (int index = 0; index < result.length; index++) {
            exact[index] = total * (double) normalized[index];
            result[index] = (long) Math.floor(exact[index]);
            fractions[index] = exact[index] - result[index];
            assigned += result[index];
        }

        long remainder = total - assigned;
        for (long step = 0L; step < remainder; step++) {
            int best = 0;
            for (int index = 1; index < fractions.length; index++) {
                if (fractions[index] > fractions[best]) {
                    best = index;
                }
            }
            result[best]++;
            // Consume this phase's fractional slot so the next remainder goes elsewhere.
            fractions[best] -= 1.0;
        }
        return result;
    }

    /**
     * Converts six amounts into a 0-100 composition. A zero total returns all
     * zeros (not an even split).
     */
    public static float[] sharesFromAmounts(long[] amounts) {
        requirePhaseLength(amounts.length);
        double total = 0.0;
        for (long amount : amounts) {
            total += Math.max(0L, amount);
        }
        float[] result = new float[amounts.length];
        if (total <= 0.0) {
            return result;
        }
        for (int index = 0; index < result.length; index++) {
            result[index] = (float) (Math.max(0L, amounts[index]) * 100.0 / total);
        }
        return result;
    }

    public static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
