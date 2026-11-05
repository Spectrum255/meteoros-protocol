package com.spectrum255.meteorosprotocol.particle;

/**
 * Reservoir-based saturation snapshot from {@code docs/particle-field-v2.md}
 * §6.3. It is derived from a {@link FieldSample}; it is no longer stored
 * inside the sample and no longer uses a "max share" field.
 */
public record Saturation(
        boolean lowDensity,
        boolean highDensity,
        boolean thermalSaturated,
        boolean chargeSaturated,
        boolean aqueousSaturated,
        boolean primordialSaturated,
        boolean oversaturated,
        boolean chargeImbalanced,
        Phase maxPhase
) {
    public static Saturation of(FieldSample sample) {
        if (sample == null) {
            throw new IllegalArgumentException("sample");
        }
        float total = sample.totalDensity();
        boolean anyPhaseOver = false;
        for (float concentration : sample.concentrations()) {
            if (concentration > 85.0F) {
                anyPhaseOver = true;
                break;
            }
        }
        float q = sample.chargeImbalance();
        return new Saturation(
                total < 20.0F,
                total > 120.0F,
                sample.thermalDensity() > 85.0F,
                sample.chargeDensity() > 25.0F,
                sample.aqueousDensity() > 80.0F,
                sample.primordialDensity() > 80.0F,
                anyPhaseOver,
                Math.abs(q) > 0.75F && sample.chargeDensity() >= 1.0F,
                sample.dominant()
        );
    }
}
