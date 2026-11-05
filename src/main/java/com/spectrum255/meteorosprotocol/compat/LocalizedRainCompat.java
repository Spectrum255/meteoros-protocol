package com.spectrum255.meteorosprotocol.compat;

import java.util.LinkedHashSet;
import java.util.Set;

import com.spectrum255.localizedrain.weather.BiomePatches;
import com.spectrum255.localizedrain.weather.WeatherData;
import com.spectrum255.localizedrain.weather.WeatherManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Thin adapter around Localized rain's public API.
 *
 * <p>All methods must be called on the server main thread. Weather is stored per
 * connected biome patch, not per rectangle; {@link #setWeatherInArea} samples
 * patches on a coarse grid and sets each sampled patch.</p>
 */
public final class LocalizedRainCompat {
    private LocalizedRainCompat() {
    }

    public static boolean isRainingAt(ServerLevel level, BlockPos pos) {
        return WeatherManager.isRainingAt(level, pos);
    }

    public static boolean isPrecipitatingAt(ServerLevel level, BlockPos pos) {
        return WeatherManager.isPrecipitatingAt(level, pos);
    }

    public static boolean isThunderingAt(ServerLevel level, BlockPos pos) {
        return WeatherManager.isThunderingAt(level, pos);
    }

    public static boolean isThunderingRegionAt(ServerLevel level, BlockPos pos) {
        return WeatherManager.isThunderingRegionAt(level, pos);
    }

    /** 0–1 local thunder intensity; does not force precipitation or create a patch. */
    public static float thunderLevelAt(ServerLevel level, BlockPos pos) {
        return WeatherManager.thunderLevelAt(level, pos);
    }

    public static int skyDarkenAt(ServerLevel level, BlockPos pos) {
        return WeatherManager.skyDarkenAt(level, pos);
    }

    /** Computes the patch if needed; may trigger a flood fill. Use sparingly. */
    public static String patchAt(ServerLevel level, BlockPos pos) {
        return WeatherManager.patchAt(level, pos);
    }

    /** Cache-only lookup; returns {@code null} if this patch has not been computed yet. */
    public static String cachedPatchAt(ServerLevel level, BlockPos pos) {
        return BiomePatches.cachedPatchAt(level, pos);
    }

    public static Set<String> allPatches(ServerLevel level) {
        return WeatherManager.allPatches(level);
    }

    /**
     * Sets weather for every biome patch sampled inside the rectangle.
     *
     * <p>Samples every 8 section cells (128 blocks), matching the prerequisite's
     * command implementation. Call from a command, machine trigger, or other
     * low-frequency path, never from a per-tick hot path.</p>
     */
    public static void setWeatherInArea(ServerLevel level, BlockPos from, BlockPos to,
                                        WeatherData.WeatherType type, int durationTicks) {
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
            WeatherManager.setWeather(level, patches, type, durationTicks);
        }
    }

    public static void setAll(ServerLevel level, boolean raining, boolean thundering, int durationTicks) {
        WeatherManager.setAll(level, raining, thundering, durationTicks);
    }

    public static void clearAll(ServerLevel level) {
        WeatherManager.clearAll(level);
    }
}
