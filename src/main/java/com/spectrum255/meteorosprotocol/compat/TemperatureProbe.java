package com.spectrum255.meteorosprotocol.compat;

import java.util.LinkedHashMap;
import java.util.Map;

import com.spectrum255.meteorosprotocol.Config;
import com.spectrum255.meteorosprotocol.particle.FieldSample;
import com.spectrum255.meteorosprotocol.particle.StratosphereParticleModifier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Temperature-driven particle change.
 *
 * <p>Ambient temperature follows the biome base temperature and altitude.
 * Local temperature adds nearby heat sources and cold blocks from
 * {@link TemperatureSources}, weighted by {@code 1 / (1 + manhattanDistance)}
 * inside a configurable radius. The difference drives Thermion (heat) and
 * Cryon (chill); light heat stays a separate, weaker channel.</p>
 *
 * <p>Local temperature is cached per dimension/position for 20 game ticks so a
 * player standing still does not rescan the neighbourhood every tick.</p>
 */
public final class TemperatureProbe {
    public static final int CACHE_TICKS = 20;
    public static final int MAX_CACHE_ENTRIES = 1024;

    public static final double AMBIENT_MIN = 240.0;
    public static final double AMBIENT_MAX = 360.0;
    /** Kelvin gained per block above the stratosphere start (v1.1 temperature inversion). */
    public static final double STRATOSPHERE_WARMING_PER_BLOCK = 0.15;
    public static final double LOCAL_MIN = 200.0;
    public static final double LOCAL_MAX = 1500.0;

    public static final double DEFAULT_HEAT_SLOPE = 0.05;
    public static final double DEFAULT_HEAT_MIN = -5.0;
    public static final double DEFAULT_HEAT_MAX = 20.0;
    public static final double DEFAULT_CHILL_SLOPE = 0.03;
    public static final double DEFAULT_CHILL_MAX = 8.0;
    public static final double DEFAULT_CRYON_SHARE = 0.5;
    public static final int DEFAULT_RADIUS = 5;

    private static final Map<CacheKey, CacheEntry> CACHE = new LinkedHashMap<>(256, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, CacheEntry> eldest) {
            return size() > MAX_CACHE_ENTRIES;
        }
    };

    private TemperatureProbe() {
    }

    /** Ambient temperature in Kelvin for a biome base temperature at a height. */
    public static double ambientFor(float biomeBaseTemperature, int y) {
        // v1.1 temperature inversion: above the stratosphere start the air warms with
        // height again (ozone layer), and the biome term no longer applies.
        if (StratosphereParticleModifier.enabled()) {
            int startY = StratosphereParticleModifier.startY();
            if (y > startY) {
                return ambientForStratosphere(y, startY);
            }
        }
        return Mth.clamp(300.0 + 25.0 * (biomeBaseTemperature - 0.8) - 0.15 * (y - 64),
                AMBIENT_MIN, AMBIENT_MAX);
    }

    /** Pure stratosphere ambient: 240 K at {@code startY}, +0.15 K per block above it. */
    public static double ambientForStratosphere(int y, int startY) {
        return AMBIENT_MIN + (y - startY) * STRATOSPHERE_WARMING_PER_BLOCK;
    }

    /** Ambient temperature at the position, using the biome's base temperature. */
    public static double ambient(Level level, BlockPos pos) {
        return ambientFor(level.getBiome(pos).value().getBaseTemperature(), pos.getY());
    }

    /** Local temperature including nearby heat sources and cold blocks. */
    public static double local(Level level, BlockPos pos) {
        long epoch = level.getGameTime() / CACHE_TICKS;
        CacheKey key = new CacheKey(level.dimension(), pos.asLong());
        synchronized (CACHE) {
            CacheEntry cached = CACHE.get(key);
            if (cached != null && cached.epoch == epoch) {
                return cached.value;
            }
        }
        double value = computeLocal(level, pos);
        synchronized (CACHE) {
            CACHE.put(key, new CacheEntry(epoch, value));
        }
        return value;
    }

    /** Difference between local and ambient temperature, in Kelvin. */
    public static double delta(Level level, BlockPos pos) {
        return local(level, pos) - ambient(level, pos);
    }

    /** Thermion change for a temperature delta; negative when colder than ambient. */
    public static float heatOf(double deltaK) {
        return (float) Mth.clamp(deltaK * heatSlope(), heatMin(), heatMax());
    }

    /** Cryon change for a temperature delta; only cold adds chill. */
    public static float chillOf(double deltaK) {
        return (float) Mth.clamp(-deltaK * chillSlope(), 0.0, chillMax());
    }

    /** Applies the temperature heat/chill for the given position. */
    public static FieldSample apply(ServerLevel level, BlockPos pos, FieldSample base) {
        if (!enabled()) {
            return base;
        }
        double ambient = ambient(level, pos);
        double local = local(level, pos);
        double deltaK = local - ambient;
        float heat = heatOf(deltaK);
        float chill = chillOf(deltaK);
        if (heat == 0.0F && chill == 0.0F) {
            return base;
        }
        float cryon = Math.max(0.0F, base.cryon() - heat * (float) cryonShare()) + chill;
        return new FieldSample(
                base.thermion() + heat,
                cryon,
                base.voltixPos(),
                base.voltixNeg(),
                base.aquantum(),
                base.aetheron(),
                base.extra());
    }

    /** Clears the local-temperature cache (self-test / reload support). */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static double computeLocal(Level level, BlockPos pos) {
        double ambient = ambient(level, pos);
        int radius = radius();
        double contribution = 0.0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos sample = pos.offset(dx, dy, dz);
                    if (!level.hasChunkAt(sample)) {
                        continue;
                    }
                    Float temperature = TemperatureSources.temperatureOf(level.getBlockState(sample));
                    if (temperature == null) {
                        continue;
                    }
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    contribution += (temperature - ambient) / (1.0 + distance);
                }
            }
        }
        return Mth.clamp(ambient + contribution, LOCAL_MIN, LOCAL_MAX);
    }

    private static boolean enabled() {
        try {
            return Config.PF_TEMP_ENABLED.get();
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static int radius() {
        try {
            return Mth.clamp(Config.PF_TEMP_RADIUS.get(), 1, 8);
        } catch (RuntimeException exception) {
            return DEFAULT_RADIUS;
        }
    }

    private static double heatSlope() {
        try {
            return Config.PF_TEMP_HEAT_SLOPE.get();
        } catch (RuntimeException exception) {
            return DEFAULT_HEAT_SLOPE;
        }
    }

    private static double heatMin() {
        try {
            return Config.PF_TEMP_HEAT_MIN.get();
        } catch (RuntimeException exception) {
            return DEFAULT_HEAT_MIN;
        }
    }

    private static double heatMax() {
        try {
            return Config.PF_TEMP_HEAT_MAX.get();
        } catch (RuntimeException exception) {
            return DEFAULT_HEAT_MAX;
        }
    }

    private static double chillSlope() {
        try {
            return Config.PF_TEMP_CHILL_SLOPE.get();
        } catch (RuntimeException exception) {
            return DEFAULT_CHILL_SLOPE;
        }
    }

    private static double chillMax() {
        try {
            return Config.PF_TEMP_CHILL_MAX.get();
        } catch (RuntimeException exception) {
            return DEFAULT_CHILL_MAX;
        }
    }

    private static double cryonShare() {
        try {
            return Config.PF_TEMP_CRYON_SHARE.get();
        } catch (RuntimeException exception) {
            return DEFAULT_CRYON_SHARE;
        }
    }

    private record CacheKey(ResourceKey<Level> dimension, long pos) {
    }

    private record CacheEntry(long epoch, double value) {
    }
}
