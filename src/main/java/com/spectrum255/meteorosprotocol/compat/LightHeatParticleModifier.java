package com.spectrum255.meteorosprotocol.compat;

import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.particle.FieldSample;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;

/**
 * Artificial-light heat: local lighting that is brighter than the sky light
 * warms the particle field.
 *
 * <p>Artificial light is {@code max(0, blockLight - skyLight)}, so an open field
 * in daylight (15/15) and an unlit night spot (0/0) both add nothing, while a
 * torch in a cave (14/0) or lava in the Nether (15/0) add heat. Under water the
 * sky light is already attenuated by the engine, which is used as-is.</p>
 *
 * <p>Heat raises Thermion and is paid for by Cryon up to a configurable share;
 * the other phases and any addon-phase {@code extra} values are preserved.</p>
 */
public final class LightHeatParticleModifier {
    /** Thermion added per level of artificial light when config is unavailable. */
    public static final float HEAT_PER_LEVEL = 1.0F;
    /** Fraction of the Thermion gain deducted from Cryon when config is unavailable. */
    public static final float CRYON_SHARE = 0.5F;

    private LightHeatParticleModifier() {
    }

    /** Configured Thermion gain per artificial light level (default {@value #HEAT_PER_LEVEL}). */
    public static float heatPerLevel() {
        try {
            return Config.PF_LIGHT_HEAT_PER_LEVEL.get().floatValue();
        } catch (RuntimeException exception) {
            return HEAT_PER_LEVEL;
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

    /** Thermion gain for a given artificial light level, after applying the config. */
    public static float heatFor(int artificialLight) {
        if (artificialLight <= 0) {
            return 0.0F;
        }
        return artificialLight * heatPerLevel();
    }

    /** Block light above sky light, floored at zero. */
    public static int artificialLight(BlockAndTintGetter level, BlockPos pos) {
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = level.getBrightness(LightLayer.SKY, pos);
        return Math.max(0, block - sky);
    }

    /** Server-side convenience overload. */
    public static int artificialLight(ServerLevel level, BlockPos pos) {
        return artificialLight((BlockAndTintGetter) level, pos);
    }

    /** Applies the artificial-light heat for the given position. */
    public static FieldSample apply(ServerLevel level, BlockPos pos, FieldSample base) {
        return apply(base, artificialLight(level, pos));
    }

    /**
     * Pure variant used by the self-test: no world access, only the configured
     * heat and Cryon share.
     */
    public static FieldSample apply(FieldSample base, int artificialLight) {
        float heat = heatFor(artificialLight);
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
