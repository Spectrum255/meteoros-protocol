package com.spectrum255.meteorosprotocol.compat;

import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.particle.FieldSample;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Weather adjustment for the v2 field.
 *
 * <p>Weather only modifies a sampled copy. It never writes back to the biome
 * cache and never renormalises the six reservoirs.</p>
 *
 * <ul>
 *   <li>Rain: {@code dT = min(4, T); T -= dT; C += dT; Aq += 6} — {@code D_th}
 *       is unchanged.</li>
 *   <li>Thunder: continuous storm intensity {@code s} adds {@code 12*s} to
 *       {@code D_ch} (the same term as the mapping formula), then recomputes
 *       {@code q} and the {@code V+/V-} split from height. It is deliberately
 *       not a boolean threshold and does not add both {@code 12*s} and the old
 *       fixed {@code +4}.</li>
 * </ul>
 */
public final class WeatherParticleModifier {
    public static final float RAIN_THERMION_DELTA = 4.0F;
    public static final float RAIN_AQUANTUM_DELTA = 6.0F;
    public static final float THUNDER_CHARGE_SCALE = 12.0F;
    public static final float THUNDER_HEIGHT_SCALE = 0.65F;

    private WeatherParticleModifier() {
    }

    public static Result apply(ServerLevel level, BlockPos pos, FieldSample base) {
        if (base == null) {
            throw new IllegalArgumentException("base");
        }
        // Prefer the registered weather type over the biome: since Localized rain 0.2.x a
        // patch carries a WeatherType, so add-on types (for example sandstorms) and vanilla
        // rain take the same code path. Snow stays inert, matching the v2 field table.
        boolean raining = LocalizedRainCompat.isRainFallingAt(level, pos);
        float storm = Mth.clamp(LocalizedRainCompat.thunderLevelAt(level, pos), 0.0F, 1.0F);
        float h = (float) Mth.clamp(
                (pos.getY() - Config.PF_H_BASE.get()) / Config.PF_H_RANGE.get(),
                -1.0,
                1.0
        );
        return apply(base, raining, storm, h);
    }

    /**
     * Pure variant used by the self-test and by callers that already sampled
     * weather. {@code h} is the clamped height coordinate in [-1, 1].
     */
    public static Result apply(FieldSample base, boolean raining, float storm, float h) {
        if (base == null) {
            throw new IllegalArgumentException("base");
        }
        float s = Mth.clamp(storm, 0.0F, 1.0F);
        if (!raining && s <= 0.0F) {
            return new Result(base, false, false);
        }

        float t = base.thermion();
        float c = base.cryon();
        float aq = base.aquantum();
        if (raining) {
            float dT = Math.min(RAIN_THERMION_DELTA, t);
            t -= dT;
            c += dT;
            aq += RAIN_AQUANTUM_DELTA;
        }

        float dCh = base.chargeDensity() + THUNDER_CHARGE_SCALE * s;
        dCh = Math.min(dCh, (float) (double) Config.PF_CHARGE_MAX.get());
        float q = (float) Mth.clamp(
                Config.PF_CHARGE_HEIGHT_GRADIENT.get() * h + THUNDER_HEIGHT_SCALE * s * h,
                Config.PF_CHARGE_IMBALANCE_MIN.get(),
                Config.PF_CHARGE_IMBALANCE_MAX.get()
        );
        float vPlus = dCh * (1.0F + q) * 0.5F;
        float vMinus = dCh - vPlus;

        return new Result(
                new FieldSample(t, c, vPlus, vMinus, aq, base.aetheron()),
                raining,
                s > 0.0F
        );
    }

    public record Result(FieldSample sample, boolean raining, boolean thundering) {
        public Result {
            if (sample == null) {
                throw new IllegalArgumentException("sample");
            }
        }
    }
}
