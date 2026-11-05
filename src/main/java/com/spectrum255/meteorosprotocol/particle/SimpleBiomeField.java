package com.spectrum255.meteorosprotocol.particle;

import com.spectrum255.meteorosprotocol.Config;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Minimal P0 biome field from {@code docs/biome-field-mapping-v2.md} §6.2:
 * biome base temperature and downfall, ocean/river tags, dimension constants
 * and height-derived terms. {@code cave} is 0 in S1 and weather is not applied
 * here (weather is a copy in {@code WeatherAdjustedField}).
 *
 * <p>Cache layout: climate inputs are cached per chunk (bounded LRU), while
 * vertical terms ({@code alt/low/deep/h}) are recomputed for every Y. This
 * fixes the old cache key that ignored Y entirely. The P0 cache does not
 * distinguish biomes inside one chunk; a later noise-sampler path replaces
 * this approximation.</p>
 */
public final class SimpleBiomeField implements ParticleField {
    private static final int CACHE_LIMIT = 512;
    private static final Map<Level, SimpleBiomeField> INSTANCES = new WeakHashMap<>();

    private final Map<Long, ClimateInputs> climateCache = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ClimateInputs> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    public static SimpleBiomeField forLevel(Level level) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(level, ignored -> new SimpleBiomeField());
        }
    }

    public static FieldSample sampleAt(Level level, BlockPos pos) {
        return forLevel(level).sample(level, pos);
    }

    @Override
    public FieldSample sample(ServerLevel level, BlockPos pos) {
        return sample((Level) level, pos);
    }

    public FieldSample sample(Level level, BlockPos pos) {
        return compute(climateAt(level, pos), pos.getY());
    }

    private ClimateInputs climateAt(Level level, BlockPos pos) {
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        ClimateInputs cached = climateCache.get(key);
        if (cached != null) {
            return cached;
        }

        var holder = level.getBiome(pos);
        Biome biome = holder.value();
        // 1.21.1 has no Biome#getClimateSettings()/getDownfall(); NeoForge exposes
        // the modified climate settings, which includes the downfall value.
        float downfall = safe(biome.getModifiedClimateSettings().downfall());
        ClimateInputs inputs = new ClimateInputs(
                safe(biome.getBaseTemperature()),
                downfall,
                holder.is(BiomeTags.IS_OCEAN),
                holder.is(BiomeTags.IS_RIVER),
                dimensionKind(level.dimension())
        );
        climateCache.put(key, inputs);
        return inputs;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static float safe(float value) {
        return Float.isFinite(value) ? value : 0.0F;
    }

    private static DimensionKind dimensionKind(ResourceKey<Level> dimension) {
        if (Level.NETHER.equals(dimension)) {
            return DimensionKind.NETHER;
        }
        if (Level.END.equals(dimension)) {
            return DimensionKind.END;
        }
        // Modded/custom dimensions use the overworld baseline until v2 defines
        // per-dimension overrides for them.
        return DimensionKind.OVERWORLD;
    }

    /**
     * Pure P0 formula, exposed for the self-test. {@code y} participates in
     * {@code alt/low/deep/h} and in the End's {@code end_peak}.
     */
    public static FieldSample compute(ClimateInputs inputs, int y) {
        Objects.requireNonNull(inputs, "inputs");

        float altBase = Config.PF_ALT_BASE.get().floatValue();
        float altRange = Config.PF_ALT_RANGE.get().floatValue();
        float lowBase = Config.PF_LOW_BASE.get().floatValue();
        float lowRange = Config.PF_LOW_RANGE.get().floatValue();
        float deepBase = Config.PF_DEEP_BASE.get().floatValue();
        float deepRange = Config.PF_DEEP_RANGE.get().floatValue();
        float hBase = Config.PF_H_BASE.get().floatValue();
        float hRange = Config.PF_H_RANGE.get().floatValue();

        float alt = ParticleMath.clamp((y - altBase) / altRange, 0.0F, 1.0F);
        float low = ParticleMath.clamp((lowBase - y) / lowRange, 0.0F, 1.0F);
        float deep = ParticleMath.clamp((deepBase - y) / deepRange, 0.0F, 1.0F);
        float h = ParticleMath.clamp((y - hBase) / hRange, -1.0F, 1.0F);

        float tempOffset = Config.PF_TEMP_OFFSET.get().floatValue();
        float tempDivisor = Config.PF_TEMP_DIVISOR.get().floatValue();
        float temp = ParticleMath.clamp((inputs.baseTemperature() + tempOffset) / tempDivisor, 0.0F, 1.0F);
        // P0 has valley = 0, so temp_eff only gets the altitude correction.
        float tempEff = ParticleMath.clamp(temp - Config.PF_TEMP_ALT_LOSS.get().floatValue() * alt, 0.0F, 1.0F);

        float wet = ParticleMath.clamp(inputs.downfall(), 0.0F, 1.0F);
        float ocean = inputs.ocean() ? 1.0F : 0.0F;
        float river = inputs.river() ? 1.0F : 0.0F;
        float water = ParticleMath.clamp(
                Config.PF_WATER_OCEAN.get().floatValue() * ocean
                        + Config.PF_WATER_WET.get().floatValue() * wet
                        + Config.PF_WATER_LOW.get().floatValue() * low
                        + Config.PF_WATER_RIVER.get().floatValue() * river,
                0.0F,
                1.0F
        );

        float dimThermal;
        float dimCharge;
        float dimAqueous;
        switch (inputs.dimension()) {
            case NETHER -> {
                dimThermal = Config.PF_DIM_NETHER_THERMAL.get().floatValue();
                dimCharge = Config.PF_DIM_NETHER_CHARGE.get().floatValue();
                dimAqueous = Config.PF_DIM_NETHER_AQUEOUS.get().floatValue();
            }
            case END -> {
                dimThermal = Config.PF_DIM_END_THERMAL.get().floatValue();
                dimCharge = Config.PF_DIM_END_CHARGE.get().floatValue();
                dimAqueous = Config.PF_DIM_END_AQUEOUS.get().floatValue();
            }
            default -> {
                dimThermal = Config.PF_DIM_OVERWORLD_THERMAL.get().floatValue();
                dimCharge = Config.PF_DIM_OVERWORLD_CHARGE.get().floatValue();
                dimAqueous = Config.PF_DIM_OVERWORLD_AQUEOUS.get().floatValue();
            }
        }

        // cave = 0 in P0, so its factor is 1; S2 adds the light/sampler cave term.
        float thermalMin = Config.PF_THERMAL_MIN.get().floatValue();
        float thermalMax = Config.PF_THERMAL_MAX.get().floatValue();
        float thermalDensity = ParticleMath.clamp(
                Config.PF_THERMAL_BASE.get().floatValue()
                        * (1.0F - Config.PF_THERMAL_ALT_LOSS.get().floatValue() * alt)
                        * (1.0F - Config.PF_THERMAL_DEEP_LOSS.get().floatValue() * deep)
                        * dimThermal,
                Math.min(thermalMin, thermalMax),
                Math.max(thermalMin, thermalMax)
        );
        float ratioMin = Config.PF_THERMAL_RATIO_MIN.get().floatValue();
        float ratioMax = Config.PF_THERMAL_RATIO_MAX.get().floatValue();
        float thermalRatio = ParticleMath.clamp(
                Config.PF_THERMAL_RATIO_BASE.get().floatValue()
                        + Config.PF_THERMAL_RATIO_SCALE.get().floatValue() * (tempEff - 0.5F),
                Math.min(ratioMin, ratioMax),
                Math.max(ratioMin, ratioMax)
        );
        float thermion = thermalDensity * thermalRatio;
        float cryon = thermalDensity - thermion;

        float chargeMax = Config.PF_CHARGE_MAX.get().floatValue();
        float chargeDensity = ParticleMath.clamp(
                (Config.PF_CHARGE_BASE.get().floatValue() + Config.PF_CHARGE_WET_SCALE.get().floatValue() * wet) * dimCharge,
                0.0F,
                Math.max(0.0F, chargeMax)
        );
        float qMin = Config.PF_CHARGE_IMBALANCE_MIN.get().floatValue();
        float qMax = Config.PF_CHARGE_IMBALANCE_MAX.get().floatValue();
        float q = ParticleMath.clamp(
                Config.PF_CHARGE_HEIGHT_GRADIENT.get().floatValue() * h,
                Math.min(qMin, qMax),
                Math.max(qMin, qMax)
        );
        float voltixPos = chargeDensity * (1.0F + q) / 2.0F;
        float voltixNeg = chargeDensity - voltixPos;

        float aqueousMax = Config.PF_AQUEOUS_MAX.get().floatValue();
        float aquantum = ParticleMath.clamp(
                dimAqueous * (Config.PF_AQUEOUS_BASE.get().floatValue()
                        + Config.PF_AQUEOUS_WATER_SCALE.get().floatValue() * water
                        + Config.PF_AQUEOUS_LOW_SCALE.get().floatValue() * low),
                0.0F,
                Math.max(0.0F, aqueousMax)
        );

        float aetheron;
        switch (inputs.dimension()) {
            case NETHER -> aetheron = Config.PF_AETHER_NETHER_MULTIPLIER.get().floatValue()
                    * (Config.PF_AETHER_NETHER_BASE.get().floatValue()
                    + Config.PF_AETHER_NETHER_DEEP_SCALE.get().floatValue() * deep);
            case END -> {
                float endPeakBase = Config.PF_END_PEAK_BASE.get().floatValue();
                float endPeakRange = Config.PF_END_PEAK_RANGE.get().floatValue();
                float endPeak = ParticleMath.clamp((y - endPeakBase) / endPeakRange, 0.0F, 1.0F);
                aetheron = Config.PF_AETHER_END_BASE.get().floatValue()
                        + Config.PF_AETHER_END_PEAK_SCALE.get().floatValue() * endPeak;
            }
            default -> aetheron = Config.PF_AETHER_OVERWORLD_BASE.get().floatValue()
                    + Config.PF_AETHER_OVERWORLD_DEEP_SCALE.get().floatValue() * deep;
        }

        return new FieldSample(thermion, cryon, voltixPos, voltixNeg, aquantum, aetheron);
    }

    public enum DimensionKind {
        OVERWORLD,
        NETHER,
        END
    }

    /** Climate inputs that do not depend on Y; cached per chunk. */
    public record ClimateInputs(
            float baseTemperature,
            float downfall,
            boolean ocean,
            boolean river,
            DimensionKind dimension
    ) {
        public ClimateInputs {
            if (!Float.isFinite(baseTemperature) || !Float.isFinite(downfall)) {
                throw new IllegalArgumentException("climate inputs must be finite");
            }
            downfall = ParticleMath.clamp(downfall, 0.0F, 1.0F);
            dimension = Objects.requireNonNull(dimension, "dimension");
        }
    }

}
