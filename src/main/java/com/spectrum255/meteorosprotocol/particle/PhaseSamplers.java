package com.spectrum255.meteorosprotocol.particle;

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

    /** Clears registered samplers; only used by tests. */
    static void clearForTests() {
        SAMPLERS.clear();
    }
}
