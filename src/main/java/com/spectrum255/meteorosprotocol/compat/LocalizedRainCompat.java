package com.spectrum255.meteorosprotocol.compat;

import java.util.LinkedHashSet;
import java.util.Set;

import com.spectrum255.localizedrain.api.WeatherApi;
import com.spectrum255.localizedrain.api.WeatherType;
import com.spectrum255.localizedrain.weather.BiomePatches;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/**
 * Thin adapter around Localized rain's public API.
 *
 * <p>Localized rain 0.2.x replaced the hard-coded clear/rain/thunder states with a
 * data-driven registry of {@link WeatherType}s ({@code localizedrain:weather_type}).
 * Queries now report the type registered for the biome patch, so a patch can carry
 * precipitation in a dry biome (for example a sandstorm over a desert). This adapter
 * therefore asks for the <em>type</em> instead of re-deriving precipitation from the
 * biome: {@link #isRainFallingAt} is the one place that decides whether a type counts
 * as rain for the v2 particle field.</p>
 *
 * <p>All methods must be called on the server main thread. Weather is stored per
 * connected biome patch, not per rectangle; {@link #setWeatherInArea} samples
 * patches on a coarse grid and sets each sampled patch.</p>
 *
 * <p>Weather queries are biome-gated: inside {@code meteorosprotocol:stratosphere}
 * every query reports clear weather. That biome only exists above the stratosphere
 * floor (default y &gt; 720), so the gate follows the floor automatically. The
 * prerequisite itself treats precipitation-free biomes (caves, floating islands,
 * the stratosphere) as clear, and its forced-type rules may still override that —
 * the gate here is ours and wins for particle purposes.</p>
 */
public final class LocalizedRainCompat {
    /** The stratosphere biome; the weather lock keys on this registry entry. */
    public static final ResourceKey<Biome> STRATOSPHERE_BIOME = ResourceKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath("meteorosprotocol", "stratosphere"));

    private LocalizedRainCompat() {
    }

    /**
     * Whether weather is suppressed at this position because the position sits in the
     * stratosphere biome.
     *
     * <p>The lock keys on the biome in the registry at the queried position
     * ({@code meteorosprotocol:stratosphere}), not on a height constant: the biome only
     * exists above the stratosphere floor, so any future change to that floor follows
     * automatically. This is also why the prerequisite's biome blacklist cannot express
     * it — Localized rain resolves a patch's biome from a noise sample at y=128, where
     * the stratosphere biome never appears.</p>
     *
     * <p>Only the query side is gated: patches still exist and can still be set through
     * commands, they simply do not rain where nobody can see the weather.</p>
     */
    public static boolean weatherLockedAt(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(STRATOSPHERE_BIOME);
    }

    /**
     * The registered weather type covering this position, or the built-in clear type
     * when the position has no patch. Never {@code null}.
     */
    public static WeatherType weatherTypeAt(ServerLevel level, BlockPos pos) {
        return WeatherApi.typeAt(level, pos);
    }

    public static boolean isRainingAt(ServerLevel level, BlockPos pos) {
        return !weatherLockedAt(level, pos) && WeatherApi.isRainingAt(level, pos);
    }

    public static boolean isPrecipitatingAt(ServerLevel level, BlockPos pos) {
        return !weatherLockedAt(level, pos) && WeatherApi.isPrecipitatingAt(level, pos);
    }

    public static boolean isThunderingAt(ServerLevel level, BlockPos pos) {
        return !weatherLockedAt(level, pos) && WeatherApi.isThunderingAt(level, pos);
    }

    public static boolean isThunderingRegionAt(ServerLevel level, BlockPos pos) {
        return !weatherLockedAt(level, pos) && WeatherApi.isThunderingRegionAt(level, pos);
    }

    /**
     * Whether the type over this position counts as rain for the particle field:
     * it must produce precipitation and that precipitation must be {@link Biome.Precipitation#RAIN}.
     *
     * <p>This replaces the old "biome precipitation is RAIN" check. With 0.2.x the type
     * is authoritative, so a custom rainy type registered by an add-on behaves exactly
     * like vanilla rain, while snow and precipitation-free types stay inert.</p>
     */
    public static boolean isRainFallingAt(ServerLevel level, BlockPos pos) {
        if (weatherLockedAt(level, pos)) {
            return false;
        }
        WeatherType type = WeatherApi.typeAt(level, pos);
        return type != null
                && type.hasPrecipitation()
                && type.precipitation() == Biome.Precipitation.RAIN;
    }

    /** 0–1 local thunder intensity; does not force precipitation or create a patch. */
    public static float thunderLevelAt(ServerLevel level, BlockPos pos) {
        return weatherLockedAt(level, pos) ? 0.0F : WeatherApi.thunderLevelAt(level, pos);
    }

    public static int skyDarkenAt(ServerLevel level, BlockPos pos) {
        return weatherLockedAt(level, pos) ? 0 : WeatherApi.skyDarkenAt(level, pos);
    }

    /** Computes the patch if needed; may trigger a flood fill. Use sparingly. */
    public static String patchAt(ServerLevel level, BlockPos pos) {
        return WeatherApi.patchAt(level, pos);
    }

    /** Cache-only lookup; returns {@code null} if this patch has not been computed yet. */
    public static String cachedPatchAt(ServerLevel level, BlockPos pos) {
        return BiomePatches.cachedPatchAt(level, pos);
    }

    public static Set<String> allPatches(ServerLevel level) {
        return WeatherApi.patches(level);
    }

    /**
     * Sets weather for every biome patch sampled inside the rectangle.
     *
     * <p>Samples every 8 section cells (128 blocks), matching the prerequisite's
     * command implementation. Call from a command, machine trigger, or other
     * low-frequency path, never from a per-tick hot path.</p>
     *
     * @param typeId one of {@code WeatherApi}/{@code WeatherTypes} ids, or a type
     *               registered by an add-on
     */
    public static void setWeatherInArea(ServerLevel level, BlockPos from, BlockPos to,
                                        ResourceLocation typeId, int durationTicks) {
        if (typeId == null) {
            throw new IllegalArgumentException("typeId");
        }
        int minCellX = SectionPos.blockToSectionCoord(Math.min(from.getX(), to.getX()));
        int maxCellX = SectionPos.blockToSectionCoord(Math.max(from.getX(), to.getX()));
        int minCellZ = SectionPos.blockToSectionCoord(Math.min(from.getZ(), to.getZ()));
        int maxCellZ = SectionPos.blockToSectionCoord(Math.max(from.getZ(), to.getZ()));

        Set<String> patches = new LinkedHashSet<>();
        for (int cellX = minCellX; cellX <= maxCellX; cellX += 8) {
            for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ += 8) {
                patches.add(BiomePatches.patchAtCell(level, cellX, cellZ));
            }
        }
        if (!patches.isEmpty()) {
            WeatherApi.setWeather(level, patches, typeId, durationTicks);
        }
    }
}
