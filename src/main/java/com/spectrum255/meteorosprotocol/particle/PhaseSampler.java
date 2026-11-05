package com.spectrum255.meteorosprotocol.particle;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Optional addon hook that contributes an atmospheric concentration for a
 * non-core phase.
 *
 * <p>Called from {@code WeatherAdjustedField.sampleAt} for every non-core phase
 * that registered a sampler. The return value must be finite and non-negative;
 * anything else is treated as zero. There is deliberately no caching or
 * throttling here: the caller runs in the per-tick machine path, so implementors
 * are expected to cache their own expensive work (the design document states the
 * same rule).</p>
 */
@FunctionalInterface
public interface PhaseSampler {
    float sample(ServerLevel level, BlockPos pos, FieldSample base);
}
