package com.spectrum255.meteorosprotocol.compat;

import com.spectrum255.localizedrain.api.WeatherType;
import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.compat.WeatherBonusRules.Rules;
import com.spectrum255.meteorosprotocol.particle.FieldSample;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Weather adjustment for the v2 field, driven by the weather type registry.
 *
 * <p>Since Localized rain 0.2.x a biome patch carries a {@link WeatherType}, and add-ons
 * may register their own types. Per-type deltas therefore live in
 * {@code config/meteorosprotocol-weather.toml} (see {@link WeatherBonusConfig}) instead
 * of being hard-coded here. The file is parsed once at server start; the hot path only
 * performs identity lookups.</p>
 *
 * <p>Weather only modifies a sampled copy. It never writes back to the biome cache and
 * never renormalises the six reservoirs.</p>
 *
 * <ul>
 *   <li>Rain-like types default to {@code T -4 / C +4 / Aq +6}.</li>
 *   <li>Snow defaults to {@code T -5 / C +5 / Aq +4} (slightly less aqueous vapour).</li>
 *   <li>Thunder adds {@code charge_density * s} to {@code D_ch} (clamped), then
 *       recomputes {@code q} and the {@code V+/V-} split from height.</li>
 *   <li>Types without precipitation only apply while the sky is visible, so a sandstorm
 *       does not leak into caves.</li>
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
        if (!WeatherBonusConfig.enabled() || LocalizedRainCompat.weatherLockedAt(level, pos)) {
            return new Result(base, false, false);
        }
        WeatherType type = LocalizedRainCompat.weatherTypeAt(level, pos);
        if (type == null) {
            return new Result(base, false, false);
        }
        Rules rules = WeatherBonusConfig.rulesFor(type);
        if (rules == null || rules.isZero()) {
            return new Result(base, false, false);
        }

        // Precipitation types key on the patch state; types without precipitation (for
        // example an add-on sandstorm) key on sky visibility so they do not apply indoors.
        boolean active = type.hasPrecipitation()
                ? LocalizedRainCompat.isPrecipitatingAt(level, pos)
                : level.canSeeSky(pos);
        if (!active) {
            return new Result(base, false, false);
        }

        float storm = type.allowsThunder()
                ? Mth.clamp(LocalizedRainCompat.thunderLevelAt(level, pos), 0.0F, 1.0F)
                : 0.0F;
        float h = (float) Mth.clamp(
                (pos.getY() - Config.PF_H_BASE.get()) / Config.PF_H_RANGE.get(),
                -1.0,
                1.0
        );
        return apply(base, rules, active, storm, h);
    }

    /**
     * Pure rule application used by the hot path and the self-test.
     *
     * <p>All four reservoir deltas are applied to the sampled copy and clamped to
     * non-negative values. The charge term scales with storm intensity and is then
     * clamped to {@code particleField.chargeMax}; the imbalance uses the same height
     * gradient as thunder previously did.</p>
     */
    public static Result apply(FieldSample base, Rules rules, boolean active, float storm, float h) {
        if (base == null) {
            throw new IllegalArgumentException("base");
        }
        if (rules == null) {
            throw new IllegalArgumentException("rules");
        }
        float s = Mth.clamp(storm, 0.0F, 1.0F);
        if (!active) {
            return new Result(base, false, false);
        }

        float t = Math.max(0.0F, base.thermion() + rules.thermion());
        float c = Math.max(0.0F, base.cryon() + rules.cryon());
        float aq = Math.max(0.0F, base.aquantum() + rules.aquantum());
        float ae = Math.max(0.0F, base.aetheron() + rules.aetheron());

        float dCh = base.chargeDensity() + rules.chargeDensity() * s;
        dCh = Math.min(dCh, (float) (double) Config.PF_CHARGE_MAX.get());
        float q = (float) Mth.clamp(
                Config.PF_CHARGE_HEIGHT_GRADIENT.get() * h + THUNDER_HEIGHT_SCALE * s * h,
                Config.PF_CHARGE_IMBALANCE_MIN.get(),
                Config.PF_CHARGE_IMBALANCE_MAX.get()
        );
        float vPlus = dCh * (1.0F + q) * 0.5F;
        float vMinus = dCh - vPlus;

        return new Result(new FieldSample(t, c, vPlus, vMinus, aq, ae), true, s > 0.0F);
    }

    /**
     * Legacy pure variant: {@code raining} means "rain defaults apply". Kept for the
     * self-test and for callers that already know the weather; new code should use the
     * rules overload.
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
