package com.spectrum255.meteorosprotocol.compat;

import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.particle.FieldSample;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;

/**
 * Light heat: block light and sky light each warm the local particle field.
 *
 * <p>Block light is the light emitted by torches, lava, glowstone and other
 * artificial sources. Sky light is the raw sky-light value reduced by
 * {@code Level#getSkyDarken()}, which the engine derives from the day/night
 * cycle and weather; in the rain the effective sky light is further reduced by
 * {@link #RAIN_SKY_MULTIPLIER}. Keeping the two channels separate avoids the
 * double counting of the old {@code max(0, blockLight - skyLight)} rule.</p>
 *
 * <p>Heat raises Thermion and is paid for by Cryon up to a configurable share;
 * the other phases and any addon-phase {@code extra} values are preserved.</p>
 */
public final class LightHeatParticleModifier {
    /** Thermion added per level of block light when config is unavailable. */
    public static final float HEAT_PER_ARTIFICIAL = 1.0F;
    /** Thermion added per level of effective sky light when config is unavailable. */
    public static final float HEAT_PER_SKY = 0.5F;
    /** Fraction of the total gain deducted from Cryon when config is unavailable. */
    public static final float CRYON_SHARE = 0.5F;
    /** Effective sky light is multiplied by this while it precipitates. */
    public static final float RAIN_SKY_MULTIPLIER = 0.6F;
    /** Highest light level, also the effective-sky-light cap. */
    public static final int MAX_LIGHT = 15;

    private LightHeatParticleModifier() {
    }

    /** Configured Thermion gain per block light level (default {@value #HEAT_PER_ARTIFICIAL}). */
    public static float heatPerArtificial() {
        try {
            return Config.PF_LIGHT_HEAT_ARTIFICIAL.get().floatValue();
        } catch (RuntimeException exception) {
            return HEAT_PER_ARTIFICIAL;
        }
    }

    /** Configured Thermion gain per effective sky light level (default {@value #HEAT_PER_SKY}). */
    public static float heatPerSky() {
        try {
            return Config.PF_LIGHT_HEAT_SKY.get().floatValue();
        } catch (RuntimeException exception) {
            return HEAT_PER_SKY;
        }
    }

    /** Configured share of the gain deducted from Cryon (default {@value #CRYON_SHARE}). */
    public static float cryonShare() {
        try {
            return Config.PF_LIGHT_HEAT_CRYON_SHARE.get().floatValue();
        } catch (RuntimeException exception) {
            return CRYON_SHARE;
        }
    }

    /** Raw block light at the position (0..15). */
    public static int blockLight(BlockAndTintGetter level, BlockPos pos) {
        return level.getBrightness(LightLayer.BLOCK, pos);
    }

    /** Raw sky light at the position (0..15), before the day/night reduction. */
    public static int skyLight(BlockAndTintGetter level, BlockPos pos) {
        return level.getBrightness(LightLayer.SKY, pos);
    }

    /**
     * Sky light after the day/night reduction, floored at zero and capped at
     * {@link #MAX_LIGHT}. Rain rounds the result down by
     * {@link #RAIN_SKY_MULTIPLIER} so a storm adds noticeably less heat.
     */
    public static int skyEffective(int skyLight, int skyDarken, boolean precipitating) {
        int effective = Mth.clamp(skyLight - skyDarken, 0, MAX_LIGHT);
        if (precipitating) {
            effective = Math.round(effective * RAIN_SKY_MULTIPLIER);
        }
        return effective;
    }

    /** Total Thermion gain from both light channels. */
    public static float heatFor(int blockLight, int skyEffective) {
        return blockLight * heatPerArtificial() + skyEffective * heatPerSky();
    }

    /** Applies the block-light and sky-light heat for the given position. */
    public static FieldSample apply(ServerLevel level, BlockPos pos, FieldSample base) {
        int block = blockLight(level, pos);
        int sky = skyLight(level, pos);
        int darken = level.getSkyDarken();
        boolean precipitating = LocalizedRainCompat.isPrecipitatingAt(level, pos);
        return apply(base, block, sky, darken, precipitating);
    }

    /**
     * Pure variant used by the self-test and by callers that already know the
     * light values: no world access, only the configured coefficients.
     */
    public static FieldSample apply(FieldSample base, int blockLight, int skyLight, int skyDarken,
                                    boolean precipitating) {
        return apply(base, blockLight, skyEffective(skyLight, skyDarken, precipitating));
    }

    /** Pure variant taking an already reduced effective sky-light value. */
    public static FieldSample apply(FieldSample base, int blockLight, int skyEffective) {
        float heat = heatFor(blockLight, skyEffective);
        if (heat <= 0.0F) {
            return base;
        }
        float cryon = Math.max(0.0F, base.cryon() - heat * cryonShare());
        return new FieldSample(
                base.thermion() + heat,
                cryon,
                base.voltixPos(),
                base.voltixNeg(),
                base.aquantum(),
                base.aetheron(),
                base.extra());
    }
}
