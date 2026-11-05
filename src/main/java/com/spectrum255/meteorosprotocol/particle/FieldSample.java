package com.spectrum255.meteorosprotocol.particle;

import java.util.List;

/**
 * One sample of the v2 particle field: six core concentrations plus optional
 * addon-phase concentrations, not a density plus shares.
 *
 * <p>Record component order for the core fields is the constructor order from
 * {@code docs/particle-field-v2.md} §6.1: T, C, V+, V-, Aq, Ae.
 * {@link #concentrations()} returns {@link Phase#all()} order (the six core
 * phases first: Ae, T, C, V+, V-, Aq, then addon phases); the two orders are
 * intentionally different and must not be mixed.</p>
 *
 * <p>The sample is not normalized and not capped: rainwater may push
 * {@code Aq} above 90, and {@code D_total} may exceed 100. The only
 * constructor invariant is that every concentration is finite and
 * non-negative.</p>
 */
public record FieldSample(
        float thermion,
        float cryon,
        float voltixPos,
        float voltixNeg,
        float aquantum,
        float aetheron,
        float[] extra
) {
    private static final float[] NO_EXTRA = new float[0];

    public static final FieldSample EMPTY = new FieldSample(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    /** Core-only sample; addon phases default to zero. */
    public FieldSample(
            float thermion,
            float cryon,
            float voltixPos,
            float voltixNeg,
            float aquantum,
            float aetheron
    ) {
        this(thermion, cryon, voltixPos, voltixNeg, aquantum, aetheron, NO_EXTRA);
    }

    public FieldSample {
        requireConcentration("thermion", thermion);
        requireConcentration("cryon", cryon);
        requireConcentration("voltixPos", voltixPos);
        requireConcentration("voltixNeg", voltixNeg);
        requireConcentration("aquantum", aquantum);
        requireConcentration("aetheron", aetheron);
        if (extra == null) {
            extra = NO_EXTRA;
        } else {
            for (int index = 0; index < extra.length; index++) {
                requireConcentration("extra[" + index + "]", extra[index]);
            }
            extra = extra.clone();
        }
    }

    private static void requireConcentration(String name, float value) {
        if (!Float.isFinite(value) || value < 0.0F) {
            throw new IllegalArgumentException(name + " must be finite and non-negative, got " + value);
        }
    }

    /** Defensive copy: the record stays immutable. */
    @Override
    public float[] extra() {
        return extra.clone();
    }

    /** Same sample with addon-phase concentrations replaced. */
    public FieldSample withExtra(float[] addonConcentrations) {
        return new FieldSample(thermion, cryon, voltixPos, voltixNeg, aquantum, aetheron, addonConcentrations);
    }

    /** {@code D_th}: T + C. */
    public float thermalDensity() {
        return thermion + cryon;
    }

    /** {@code D_ch}: V+ + V-. */
    public float chargeDensity() {
        return voltixPos + voltixNeg;
    }

    /** {@code D_aq}. */
    public float aqueousDensity() {
        return aquantum;
    }

    /** {@code D_ae}. */
    public float primordialDensity() {
        return aetheron;
    }

    /** Concentration of one phase, core or addon. */
    public float concentration(Phase phase) {
        if (phase == null) {
            return 0.0F;
        }
        if (phase.core()) {
            if (phase == Phase.AETHERON) {
                return aetheron;
            }
            if (phase == Phase.THERMION) {
                return thermion;
            }
            if (phase == Phase.CRYON) {
                return cryon;
            }
            if (phase == Phase.VOLTIX_POS) {
                return voltixPos;
            }
            if (phase == Phase.VOLTIX_NEG) {
                return voltixNeg;
            }
            return aquantum;
        }
        int slot = PhaseRegistry.extraPhases().indexOf(phase);
        return slot >= 0 && slot < extra.length ? extra[slot] : 0.0F;
    }

    /** {@code D_total}; may exceed 100 and is never normalized. */
    public float totalDensity() {
        float total = thermalDensity() + chargeDensity() + aqueousDensity() + primordialDensity();
        for (float value : extra) {
            total += value;
        }
        return total;
    }

    /**
     * Net charge bias {@code q = (V+ - V-) / (V+ + V- + epsilon)}.
     * Returns 0 for an empty charge reservoir.
     */
    public float chargeImbalance() {
        return (voltixPos - voltixNeg) / (chargeDensity() + 1.0E-6F);
    }

    /**
     * Absolute concentrations in {@link Phase#all()} order: the six core phases
     * (Ae, T, C, V+, V-, Aq) followed by registered addon phases.
     */
    public float[] concentrations() {
        List<Phase> phases = Phase.all();
        float[] result = new float[phases.size()];
        for (int index = 0; index < result.length; index++) {
            result[index] = concentration(phases.get(index));
        }
        return result;
    }

    /**
     * Container/UI composition in percent (0-100), derived from all
     * concentrations. Not used by the field model; returns all zeros for an
     * empty sample.
     */
    public float[] composition() {
        float total = totalDensity();
        List<Phase> phases = Phase.all();
        float[] result = new float[phases.size()];
        if (total <= 0.0F) {
            return result;
        }
        for (int index = 0; index < result.length; index++) {
            result[index] = concentration(phases.get(index)) * 100.0F / total;
        }
        return result;
    }

    /**
     * Phase with the highest absolute concentration, in {@link Phase#all()}
     * tie-break order. An all-zero sample returns {@link Phase#AETHERON}.
     */
    public Phase dominant() {
        List<Phase> phases = Phase.all();
        Phase best = phases.get(0);
        float bestValue = concentration(best);
        for (int index = 1; index < phases.size(); index++) {
            Phase candidate = phases.get(index);
            float value = concentration(candidate);
            if (value > bestValue) {
                best = candidate;
                bestValue = value;
            }
        }
        return best;
    }

    /**
     * Builds a sample from an array in {@link Phase#all()} order (Ae, T, C, V+,
     * V-, Aq, then addon phases).
     */
    public static FieldSample ofConcentrations(float[] concentrations) {
        List<Phase> phases = Phase.all();
        if (concentrations == null || concentrations.length != phases.size()) {
            throw new IllegalArgumentException(phases.size() + " concentrations required");
        }
        float[] extras = new float[PhaseRegistry.extraPhases().size()];
        for (int index = 0; index < extras.length; index++) {
            extras[index] = concentrations[phases.size() - extras.length + index];
        }
        return new FieldSample(
                concentrations[Phase.THERMION.index()],
                concentrations[Phase.CRYON.index()],
                concentrations[Phase.VOLTIX_POS.index()],
                concentrations[Phase.VOLTIX_NEG.index()],
                concentrations[Phase.AQUANTUM.index()],
                concentrations[Phase.AETHERON.index()],
                extras
        );
    }
}
