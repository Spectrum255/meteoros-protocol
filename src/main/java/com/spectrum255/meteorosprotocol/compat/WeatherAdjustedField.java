package com.spectrum255.meteorosprotocol.compat;

import com.spectrum255.meteorosprotocol.particle.FieldSample;
import com.spectrum255.meteorosprotocol.particle.ParticleField;
import com.spectrum255.meteorosprotocol.particle.Phase;
import com.spectrum255.meteorosprotocol.particle.PhaseSampler;
import com.spectrum255.meteorosprotocol.particle.PhaseSamplers;
import com.spectrum255.meteorosprotocol.particle.PhaseRegistry;
import com.spectrum255.meteorosprotocol.particle.SimpleBiomeField;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The single field entry point used by machines and commands: base field,
 * local weather copy, then the optional addon-phase samplers.
 *
 * <p>Core phases keep their v2 formulas untouched. Registered addon phases
 * default to zero; a {@link PhaseSampler} may fill them in. Samplers run in the
 * per-tick machine path, so implementations must cache their own expensive
 * work.</p>
 */
public final class WeatherAdjustedField implements ParticleField {
    private final ParticleField base;

    public WeatherAdjustedField() {
        this(SimpleBiomeField::sampleAt);
    }

    public WeatherAdjustedField(ParticleField base) {
        this.base = base;
    }

    public static FieldSample sampleAt(ServerLevel level, BlockPos pos) {
        return new WeatherAdjustedField().sample(level, pos);
    }

    @Override
    public FieldSample sample(ServerLevel level, BlockPos pos) {
        FieldSample original = base.sample(level, pos);
        FieldSample weathered = WeatherParticleModifier.apply(level, pos, original).sample();
        FieldSample lit = LightHeatParticleModifier.apply(level, pos, weathered);
        FieldSample tempered = TemperatureProbe.apply(level, pos, lit);
        return applySamplers(level, pos, tempered);
    }

    /** Runs every registered addon-phase sampler; returns the base sample when there are none. */
    public static FieldSample applySamplers(ServerLevel level, BlockPos pos, FieldSample base) {
        if (!PhaseRegistry.isBootstrapped()) {
            return base;
        }
        java.util.List<Phase> extras = PhaseRegistry.extraPhases();
        if (extras.isEmpty()) {
            return base;
        }
        float[] values = new float[extras.size()];
        boolean sampled = false;
        for (int index = 0; index < extras.size(); index++) {
            PhaseSampler sampler = PhaseSamplers.sampler(extras.get(index));
            if (sampler == null) {
                continue;
            }
            float value = sampler.sample(level, pos, base);
            if (!Float.isFinite(value) || value < 0.0F) {
                continue;
            }
            values[index] = value;
            sampled = true;
        }
        return sampled ? base.withExtra(values) : base;
    }
}
