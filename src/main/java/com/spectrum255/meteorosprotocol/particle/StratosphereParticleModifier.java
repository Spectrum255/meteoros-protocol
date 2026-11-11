package com.spectrum255.meteorosprotocol.particle;

import com.spectrum255.meteorosprotocol.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Stratosphere particle field, v1.1: the thin-air and aqueous penalties ramp in
 * continuously from {@code transitionStartY} (default 320) to {@code startY}
 * (default 720), Aetheron grows across the same band, and above the start
 * height the Aetheron and Voltix gains additionally grow with height.
 *
 * <p>The Aetheron gain and the Voltix ionisation only apply when the position can
 * see the sky ({@code requireSky}, default true); inside a cave or under a roof
 * the penalties remain but the bonuses are zeroed. Only the six core fields are
 * touched; addon-phase {@code extra} values pass through unchanged.</p>
 */
public final class StratosphereParticleModifier {
    /** First stratosphere block when config is unavailable. */
    public static final int DEFAULT_START_Y = 720;
    /** First block of the continuous transition band when config is unavailable. */
    public static final int DEFAULT_TRANSITION_START_Y = 320;
    /** Thin-air multiplier at the top of the transition band when config is unavailable. */
    public static final float THIN_AIR = 0.60F;
    /** Aetheron added per block above the start height when config is unavailable. */
    public static final float AETHERON_PER_BLOCK = 0.08F;
    /** Extra Aqueous multiplier at the top of the transition band. */
    public static final float AQUA_FACTOR = 0.10F;
    /** Voltix added per block above the start height when config is unavailable. */
    public static final float ION_PER_BLOCK = 0.03F;
    /** Aetheron gained across the whole transition band, as a fraction of the linear band. */
    public static final float BAND_GAIN_FRACTION = 0.20F;
    /** {@code Level#getSkyDarken()} at night; also the day/night divisor. */
    public static final int MAX_SKY_DARKEN = 11;

    private StratosphereParticleModifier() {
    }

    /** Whether the modifier is enabled (default {@code true}). */
    public static boolean enabled() {
        try {
            return Config.PF_STRATOSPHERE_ENABLED.get();
        } catch (RuntimeException exception) {
            return true;
        }
    }

    /** Configured first block Y of the stratosphere (default {@value #DEFAULT_START_Y}). */
    public static int startY() {
        try {
            return Config.PF_STRATOSPHERE_START_Y.get();
        } catch (RuntimeException exception) {
            return DEFAULT_START_Y;
        }
    }

    /** Configured start of the transition band, clamped below {@link #startY()}. */
    public static int transitionStartY() {
        int start = startY();
        int fallback = Math.min(DEFAULT_TRANSITION_START_Y, Math.max(0, start - 1));
        int transition;
        try {
            transition = Config.PF_STRATOSPHERE_TRANSITION_START_Y.get();
        } catch (RuntimeException exception) {
            return fallback;
        }
        return Mth.clamp(transition, 0, Math.max(0, start - 1));
    }

    /** Configured thin-air multiplier (default {@value #THIN_AIR}). */
    public static float thinAirFactor() {
        try {
            return Config.PF_STRATOSPHERE_THIN_AIR.get().floatValue();
        } catch (RuntimeException exception) {
            return THIN_AIR;
        }
    }

    /** Configured Aetheron gain per block (default {@value #AETHERON_PER_BLOCK}). */
    public static float aetheronPerBlock() {
        try {
            return Config.PF_STRATOSPHERE_AETHERON_PER_BLOCK.get().floatValue();
        } catch (RuntimeException exception) {
            return AETHERON_PER_BLOCK;
        }
    }

    /** Configured Aqueous multiplier at the top of the band (default {@value #AQUA_FACTOR}). */
    public static float aquaFactor() {
        try {
            return Config.PF_STRATOSPHERE_AQUA_FACTOR.get().floatValue();
        } catch (RuntimeException exception) {
            return AQUA_FACTOR;
        }
    }

    /** Configured Voltix gain per block (default {@value #ION_PER_BLOCK}). */
    public static float ionPerBlock() {
        try {
            return Config.PF_STRATOSPHERE_ION_PER_BLOCK.get().floatValue();
        } catch (RuntimeException exception) {
            return ION_PER_BLOCK;
        }
    }

    /** Whether bonuses require an unobstructed sky (default {@code true}). */
    public static boolean requireSky() {
        try {
            return Config.PF_STRATOSPHERE_REQUIRE_SKY.get();
        } catch (RuntimeException exception) {
            return true;
        }
    }

    /** Whether the given block Y is strictly above the configured start height. */
    public static boolean isStratosphere(int y) {
        return isStratosphere(y, enabled(), startY());
    }

    /** Pure variant of {@link #isStratosphere(int)} for the self-test. */
    public static boolean isStratosphere(int y, boolean enabled, int startY) {
        return enabled && y > startY;
    }

    /** Whether the given Y is inside the stratosphere or its transition band. */
    public static boolean isAtmosphere(float y) {
        return enabled() && y > transitionStartY();
    }

    /** Blocks above the start height, or zero below it. */
    public static float heightAbove(float y, int startY) {
        return Math.max(0.0F, y - startY);
    }

    /**
     * Continuous ramp position: 0 at {@code transitionStartY}, 1 at {@code startY},
     * clamped outside the band.
     */
    public static float transitionFactor(float y, int startY, int transitionStartY) {
        if (startY <= transitionStartY) {
            return y > startY ? 1.0F : 0.0F;
        }
        return Mth.clamp((y - transitionStartY) / (float) (startY - transitionStartY), 0.0F, 1.0F);
    }

    /** Aetheron already accumulated by the top of the transition band. */
    public static float bandTop(float aetheronPerBlock, int startY, int transitionStartY) {
        return aetheronPerBlock * Math.max(0, startY - transitionStartY) * BAND_GAIN_FRACTION;
    }

    /** Applies the stratosphere field for the given position. */
    public static FieldSample apply(ServerLevel level, BlockPos pos, FieldSample base) {
        if (!enabled()) {
            return base;
        }
        int y = pos.getY();
        if (y <= transitionStartY()) {
            return base;
        }
        boolean skyVisible = !requireSky() || level.canSeeSky(pos);
        return apply(base, y, startY(), transitionStartY(), level.getSkyDarken(), skyVisible,
                requireSky(), thinAirFactor(), aetheronPerBlock(), aquaFactor(), ionPerBlock());
    }

    /**
     * Pure variant used by the self-test and by the command output.
     *
     * <p>{@code y == startY} still behaves like the top of the transition band
     * (full thin/Aqueous penalty, {@code Ae += bandTop}, no ionisation), so the
     * field is continuous when {@code y} crosses {@code startY}.</p>
     */
    public static FieldSample apply(FieldSample base, float y, int startY, int transitionStartY,
                                    int skyDarken, boolean skyVisible, boolean requireSky,
                                    float thinAir, float aetheronPer, float aqua, float ionPer) {
        float t = transitionFactor(y, startY, transitionStartY);
        if (t <= 0.0F) {
            return base;
        }
        boolean aboveStart = y > startY;
        float height = heightAbove(y, startY);
        float thinEffective = 1.0F - (1.0F - thinAir) * t;
        float aquaEffective = 1.0F - (1.0F - aqua) * t;
        float top = bandTop(aetheronPer, startY, transitionStartY);
        float aetheronGain = aboveStart ? top + height * aetheronPer : top * t;
        float ionGain = aboveStart ? height * ionPer : 0.0F;
        if (requireSky && !skyVisible) {
            aetheronGain = 0.0F;
            ionGain = 0.0F;
        }
        float darkness = Mth.clamp(skyDarken, 0, MAX_SKY_DARKEN) / (float) MAX_SKY_DARKEN;
        float dayFactor = 1.0F - darkness;

        return new FieldSample(
                base.thermion() * thinEffective,
                base.cryon() * thinEffective,
                base.voltixPos() * thinEffective + ionGain * dayFactor,
                base.voltixNeg() * thinEffective + ionGain * darkness,
                base.aquantum() * thinEffective * aquaEffective,
                base.aetheron() + aetheronGain,
                base.extra());
    }
}
