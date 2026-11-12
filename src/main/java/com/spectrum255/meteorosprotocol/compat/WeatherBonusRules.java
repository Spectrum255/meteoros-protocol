package com.spectrum255.meteorosprotocol.compat;

import com.spectrum255.localizedrain.api.WeatherType;

import net.minecraft.world.level.biome.Biome;

/**
 * Per-weather-type particle bonuses loaded from {@code config/meteorosprotocol-weather.toml}.
 *
 * <p>The rules are applied on top of the sampled field; they never touch the biome
 * cache. Values are additive deltas in the same units as {@code FieldSample}.</p>
 */
public final class WeatherBonusRules {
    /** No bonus at all. */
    public static final Rules ZERO = new Rules(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    /** Defaults for a normal rainy type (matches the pre-0.0.43 rain table). */
    public static final Rules DEFAULT_RAIN = new Rules(-4.0F, 4.0F, 6.0F, 0.0F, 12.0F);
    /** Snow keeps the thermal shift but adds a little less aqueous vapour than rain. */
    public static final Rules DEFAULT_SNOW = new Rules(-5.0F, 5.0F, 4.0F, 0.0F, 12.0F);

    private WeatherBonusRules() {
    }

    /**
     * The rules written when a type first appears in the file.
     *
     * <p>Rain and snow get the v2 table (snow's {@code aquantum} is 4.0 instead of
     * 6.0). Everything else — including the built-in clear type and add-on types
     * without precipitation — defaults to zero so a new type does not silently
     * change the field; players can opt in per type.</p>
     */
    public static Rules defaultsFor(WeatherType type) {
        if (type == null || type.isClear()) {
            return ZERO;
        }
        if (type.hasPrecipitation()) {
            switch (type.precipitation()) {
                case RAIN:
                    return new Rules(-4.0F, 4.0F, 6.0F, 0.0F,
                            type.allowsThunder() ? 12.0F : 0.0F);
                case SNOW:
                    return new Rules(-5.0F, 5.0F, 4.0F, 0.0F,
                            type.allowsThunder() ? 12.0F : 0.0F);
                default:
                    break;
            }
        }
        return new Rules(0.0F, 0.0F, 0.0F, 0.0F, type.allowsThunder() ? 12.0F : 0.0F);
    }

    /** The four field deltas plus the storm-driven charge density term. */
    public record Rules(float thermion, float cryon, float aquantum, float aetheron, float chargeDensity) {
        public Rules {
            if (!Float.isFinite(thermion) || !Float.isFinite(cryon)
                    || !Float.isFinite(aquantum) || !Float.isFinite(aetheron)
                    || !Float.isFinite(chargeDensity)) {
                throw new IllegalArgumentException("weather bonus rules must be finite");
            }
        }

        /** True when this rule cannot change a sample even while active. */
        public boolean isZero() {
            return thermion == 0.0F && cryon == 0.0F && aquantum == 0.0F
                    && aetheron == 0.0F && chargeDensity == 0.0F;
        }
    }

    /**
     * One TOML entry: the read-only description mirrored from the registry plus the
     * player-editable rules.
     */
    public record Entry(String displayKey, boolean hasPrecipitation, String precipitation,
                        boolean allowsThunder, Rules rules) {
        public Entry {
            if (displayKey == null || precipitation == null || rules == null) {
                throw new IllegalArgumentException("weather bonus entry fields");
            }
        }

        public static Entry fromType(WeatherType type, Rules rules) {
            return new Entry(type.displayKey(), type.hasPrecipitation(),
                    type.hasPrecipitation() ? type.precipitation().name() : Biome.Precipitation.NONE.name(),
                    type.allowsThunder(), rules);
        }

        /** Whether the read-only fields still match the registry object. */
        public boolean matchesType(WeatherType type) {
            return displayKey.equals(type.displayKey())
                    && hasPrecipitation == type.hasPrecipitation()
                    && allowsThunder == type.allowsThunder()
                    && precipitation.equals(precipitationName(type));
        }
    }

    static String precipitationName(WeatherType type) {
        return type.hasPrecipitation() ? type.precipitation().name() : Biome.Precipitation.NONE.name();
    }
}
