package com.spectrum255.meteorosprotocol.particle;

import com.spectrum255.meteorosprotocol.Config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of optional {@link PhaseSampler}s. A sampler may be attached in the
 * {@link Phase.Builder} or registered directly with
 * {@link #register(Phase, PhaseSampler)}.
 */
public final class PhaseSamplers {
    private static final Map<ResourceLocation, PhaseSampler> SAMPLERS = new ConcurrentHashMap<>();
    private static final double DEFAULT_SAMPLER_RATIO = 0.10D;
    private static final double DEFAULT_UPGRADE_FACTOR = 0.25D;

    private PhaseSamplers() {
    }

    public static void register(Phase phase, PhaseSampler sampler) {
        if (phase == null) {
            throw new IllegalArgumentException("phase");
        }
        if (sampler == null) {
            throw new IllegalArgumentException("sampler");
        }
        SAMPLERS.put(phase.key(), sampler);
    }

    @Nullable
    public static PhaseSampler sampler(Phase phase) {
        if (phase == null) {
            return null;
        }
        PhaseSampler registered = SAMPLERS.get(phase.key());
        return registered != null ? registered : phase.sampler();
    }

    public static boolean hasSampler(Phase phase) {
        return sampler(phase) != null;
    }

    /**
     * The impure Aetheron sampler: a fraction of the ambient pure Aetheron decays
     * into its impure form. Both factors are configuration-driven; the upgrade
     * multiplier stays at its low default until a machine upgrade raises it.
     */
    public static PhaseSampler impureAetheron() {
        return (level, pos, base) -> base.aetheron() * (float) (samplerRatio() * upgradeFactor());
    }

    private static double samplerRatio() {
        try {
            return Config.PF_IMPURE_SAMPLER_RATIO.get();
        } catch (RuntimeException exception) {
            return DEFAULT_SAMPLER_RATIO;
        }
    }

    private static double upgradeFactor() {
        try {
            return Config.PF_IMPURE_UPGRADE_FACTOR.get();
        } catch (RuntimeException exception) {
            return DEFAULT_UPGRADE_FACTOR;
        }
    }

    /** Clears registered samplers; only used by tests. */
    static void clearForTests() {
        SAMPLERS.clear();
    }
}
