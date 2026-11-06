package com.spectrum255.meteorosprotocol;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Particle-field formula configuration.
 *
 * <p>These values are read by {@code SimpleBiomeField} on every sample, so the
 * NeoForge config screen (Mods -> Meteoros Protocol -> Config) edits the live
 * formula without a restart. The section is deliberately separate from the
 * machine/energy settings so world-gen tuning and machine tuning can evolve
 * independently.</p>
 *
 * <p>Defaults reproduce the v2 four-reservoir P0 formula documented in
 * {@code docs/biome-field-mapping-v2.md}.</p>
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment(
                "Particle field formula (v2 four reservoirs: D_th, D_ch, D_aq, D_ae).",
                "Changing these values updates the live biome field; no restart is needed.",
                "Weather bonuses are not part of this section yet (S2 waits for Localized rain 1.0.1)."
        ).push("particleField");

        BUILDER.comment("Vertical coordinate terms: alt, low, deep and h.").push("height");
        PF_ALT_BASE = BUILDER
                .comment("Y at which the altitude term starts (default 64).")
                .defineInRange("altBase", 64.0, -128.0, 512.0);
        PF_ALT_RANGE = BUILDER
                .comment("Y span of the altitude term; alt = clamp((Y - altBase) / altRange, 0, 1).")
                .defineInRange("altRange", 128.0, 1.0, 1024.0);
        PF_LOW_BASE = BUILDER
                .comment("Y at which the low-altitude term starts (default 80).")
                .defineInRange("lowBase", 80.0, -128.0, 512.0);
        PF_LOW_RANGE = BUILDER
                .comment("Y span of the low-altitude term; low = clamp((lowBase - Y) / lowRange, 0, 1).")
                .defineInRange("lowRange", 80.0, 1.0, 512.0);
        PF_DEEP_BASE = BUILDER
                .comment("Y at which the depth term starts (default -32).")
                .defineInRange("deepBase", -32.0, -512.0, 128.0);
        PF_DEEP_RANGE = BUILDER
                .comment("Y span of the depth term; deep = clamp((deepBase - Y) / deepRange, 0, 1).")
                .defineInRange("deepRange", 64.0, 1.0, 512.0);
        PF_H_BASE = BUILDER
                .comment("Y at which the signed height term h is zero (default 64).")
                .defineInRange("hBase", 64.0, -128.0, 512.0);
        PF_H_RANGE = BUILDER
                .comment("Y span of h; h = clamp((Y - hBase) / hRange, -1, 1).")
                .defineInRange("hRange", 96.0, 1.0, 512.0);
        PF_END_PEAK_BASE = BUILDER
                .comment("Y at which the End peak term starts (default 64).")
                .defineInRange("endPeakBase", 64.0, -128.0, 512.0);
        PF_END_PEAK_RANGE = BUILDER
                .comment("Y span of the End peak term; endPeak = clamp((Y - endPeakBase) / endPeakRange, 0, 1).")
                .defineInRange("endPeakRange", 128.0, 1.0, 512.0);
        BUILDER.pop();

        BUILDER.comment("Biome temperature to normalized temperature, and the altitude correction.").push("temperature");
        PF_TEMP_OFFSET = BUILDER
                .comment("Offset added to the biome base temperature before normalization (default 0.5).")
                .defineInRange("baseOffset", 0.5, -2.0, 2.0);
        PF_TEMP_DIVISOR = BUILDER
                .comment("Divisor for normalized temperature: temp = clamp((base + offset) / divisor, 0, 1).")
                .defineInRange("divisor", 2.5, 0.1, 10.0);
        PF_TEMP_ALT_LOSS = BUILDER
                .comment("Altitude cooling: temp_eff = clamp(temp - altitudeLoss * alt, 0, 1).")
                .defineInRange("altitudeLoss", 0.10, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment("Thermal reservoir (D_th), split into Thermion and Cryon.").push("thermal");
        PF_THERMAL_BASE = BUILDER
                .comment("Base thermal density before height/dimension modifiers (default 55).")
                .defineInRange("baseDensity", 55.0, 0.0, 200.0);
        PF_THERMAL_ALT_LOSS = BUILDER
                .comment("Fraction lost at alt = 1 (default 0.20).")
                .defineInRange("altitudeLoss", 0.20, 0.0, 1.0);
        PF_THERMAL_DEEP_LOSS = BUILDER
                .comment("Fraction lost at deep = 1 (default 0.15).")
                .defineInRange("deepLoss", 0.15, 0.0, 1.0);
        PF_THERMAL_MIN = BUILDER
                .comment("Lower clamp for D_th (default 5).")
                .defineInRange("minDensity", 5.0, 0.0, 100.0);
        PF_THERMAL_MAX = BUILDER
                .comment("Upper clamp for D_th (default 100).")
                .defineInRange("maxDensity", 100.0, 0.0, 200.0);
        PF_THERMAL_RATIO_BASE = BUILDER
                .comment("Thermion/Cryon split base; r_T = clamp(base + scale * (temp_eff - 0.5), min, max).")
                .defineInRange("ratioBase", 0.5, -2.0, 2.0);
        PF_THERMAL_RATIO_SCALE = BUILDER
                .comment("Thermion/Cryon split scale (default 1.4).")
                .defineInRange("ratioScale", 1.4, -5.0, 5.0);
        PF_THERMAL_RATIO_MIN = BUILDER
                .comment("Lower clamp for r_T (default 0.05).")
                .defineInRange("ratioMin", 0.05, 0.0, 1.0);
        PF_THERMAL_RATIO_MAX = BUILDER
                .comment("Upper clamp for r_T (default 0.95).")
                .defineInRange("ratioMax", 0.95, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment("Charge reservoir (D_ch), split into V+ and V-.").push("charge");
        PF_CHARGE_BASE = BUILDER
                .comment("Base charge density before the wet bonus (default 0.5).")
                .defineInRange("base", 0.5, 0.0, 10.0);
        PF_CHARGE_WET_SCALE = BUILDER
                .comment("Charge density per wet unit: (base + wetScale * wet) * dimension multiplier.")
                .defineInRange("wetScale", 1.0, 0.0, 10.0);
        PF_CHARGE_MAX = BUILDER
                .comment("Upper clamp for D_ch (default 30).")
                .defineInRange("maxDensity", 30.0, 0.0, 100.0);
        PF_CHARGE_HEIGHT_GRADIENT = BUILDER
                .comment("Charge imbalance gradient: q = clamp(heightGradient * h + storm/bonus terms, min, max).")
                .defineInRange("heightGradient", 0.25, -2.0, 2.0);
        PF_CHARGE_IMBALANCE_MIN = BUILDER
                .comment("Lower clamp for q (default -0.9).")
                .defineInRange("imbalanceMin", -0.9, -1.0, 1.0);
        PF_CHARGE_IMBALANCE_MAX = BUILDER
                .comment("Upper clamp for q (default 0.9).")
                .defineInRange("imbalanceMax", 0.9, -1.0, 1.0);
        BUILDER.pop();

        BUILDER.comment("Aqueous reservoir (D_aq).").push("aqueous");
        PF_AQUEOUS_BASE = BUILDER
                .comment("Base aqueous density (default 2).")
                .defineInRange("base", 2.0, 0.0, 20.0);
        PF_AQUEOUS_WATER_SCALE = BUILDER
                .comment("Aqueous density per water unit (default 45).")
                .defineInRange("waterScale", 45.0, 0.0, 100.0);
        PF_AQUEOUS_LOW_SCALE = BUILDER
                .comment("Aqueous density per low-altitude unit (default 20).")
                .defineInRange("lowScale", 20.0, 0.0, 100.0);
        PF_AQUEOUS_MAX = BUILDER
                .comment("Upper clamp for D_aq (default 90).")
                .defineInRange("maxDensity", 90.0, 0.0, 200.0);
        BUILDER.pop();

        BUILDER.comment("Aether reservoir (D_ae), with per-dimension formulas.").push("aether");
        PF_AETHER_OVERWORLD_BASE = BUILDER
                .comment("Overworld base Aetheron (default 0.5).")
                .defineInRange("overworldBase", 0.5, 0.0, 200.0);
        PF_AETHER_OVERWORLD_DEEP_SCALE = BUILDER
                .comment("Overworld Aetheron per deep unit (default 1.5).")
                .defineInRange("overworldDeepScale", 1.5, 0.0, 20.0);
        PF_AETHER_NETHER_BASE = BUILDER
                .comment("Nether base before the deep term (default 0.4).")
                .defineInRange("netherBase", 0.4, 0.0, 200.0);
        PF_AETHER_NETHER_DEEP_SCALE = BUILDER
                .comment("Nether deep scale (default 0.6).")
                .defineInRange("netherDeepScale", 0.6, 0.0, 20.0);
        PF_AETHER_NETHER_MULTIPLIER = BUILDER
                .comment("Outer Nether multiplier (default 0.8).")
                .defineInRange("netherMultiplier", 0.8, 0.0, 10.0);
        PF_AETHER_END_BASE = BUILDER
                .comment("End base Aetheron (default 70).")
                .defineInRange("endBase", 70.0, 0.0, 200.0);
        PF_AETHER_END_PEAK_SCALE = BUILDER
                .comment("End Aetheron per endPeak unit (default 15).")
                .defineInRange("endPeakScale", 15.0, 0.0, 100.0);
        BUILDER.pop();

        BUILDER.comment("Water availability from biome tags, downfall and altitude.").push("water");
        PF_WATER_OCEAN = BUILDER
                .comment("Ocean-tag weight in water availability (default 0.75).")
                .defineInRange("oceanWeight", 0.75, 0.0, 1.0);
        PF_WATER_WET = BUILDER
                .comment("Downfall weight (default 0.45).")
                .defineInRange("wetWeight", 0.45, 0.0, 1.0);
        PF_WATER_LOW = BUILDER
                .comment("Low-altitude weight (default 0.15).")
                .defineInRange("lowWeight", 0.15, 0.0, 1.0);
        PF_WATER_RIVER = BUILDER
                .comment("River-tag weight (default 0.10).")
                .defineInRange("riverWeight", 0.10, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment("Per-dimension multipliers for thermal, charge and aqueous density.").push("dimensions");
        PF_DIM_OVERWORLD_THERMAL = BUILDER.defineInRange("overworldThermal", 1.0, 0.0, 10.0);
        PF_DIM_OVERWORLD_CHARGE = BUILDER.defineInRange("overworldCharge", 1.0, 0.0, 10.0);
        PF_DIM_OVERWORLD_AQUEOUS = BUILDER.defineInRange("overworldAqueous", 1.0, 0.0, 10.0);
        PF_DIM_NETHER_THERMAL = BUILDER.defineInRange("netherThermal", 1.5, 0.0, 10.0);
        PF_DIM_NETHER_CHARGE = BUILDER.defineInRange("netherCharge", 0.5, 0.0, 10.0);
        PF_DIM_NETHER_AQUEOUS = BUILDER.defineInRange("netherAqueous", 0.35, 0.0, 10.0);
        PF_DIM_END_THERMAL = BUILDER.defineInRange("endThermal", 0.2, 0.0, 10.0);
        PF_DIM_END_CHARGE = BUILDER.defineInRange("endCharge", 0.3, 0.0, 10.0);
        PF_DIM_END_AQUEOUS = BUILDER.defineInRange("endAqueous", 0.1, 0.0, 10.0);
        BUILDER.pop();

        BUILDER.comment("Light heat (secondary): block light and sky light add a small amount of heat;"
                        + " temperature is the primary driver.")
                .push("light");
        PF_LIGHT_HEAT_ARTIFICIAL = BUILDER
                .comment("Thermion added per level of block light (default 0.3, weak bonus).")
                .defineInRange("heatPerArtificial", 0.30, 0.0, 4.0);
        PF_LIGHT_HEAT_SKY = BUILDER
                .comment("Thermion added per level of effective sky light after sky darkening and rain"
                        + " (default 0.15, weak bonus).")
                .defineInRange("heatPerSky", 0.15, 0.0, 4.0);
        PF_LIGHT_HEAT_CRYON_SHARE = BUILDER
                .comment("Fraction of the total light-heat gain deducted from Cryon (default 0.5).")
                .defineInRange("cryonShare", 0.5, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment("Intake tower capture: the tower loses a fraction of the heat, cold and"
                        + " aether phases it pulls in; charge and aqueous phases are lossless.")
                .push("intake");
        PF_INTAKE_CAPTURE_LOSS = BUILDER
                .comment("Fraction of captured Thermion/Cryon/Aetheron/Impure Aetheron lost"
                        + " (default 0.20).")
                .defineInRange("captureLoss", 0.20, 0.0, 0.9);
        BUILDER.pop();

        BUILDER.comment("Impure Aetheron sampling: ambient Aetheron decays into its impure form at a"
                        + " small rate, scaled by the future machine upgrade multiplier.")
                .push("impureAetheron");
        PF_IMPURE_SAMPLER_RATIO = BUILDER
                .comment("Base fraction of ambient Aetheron that becomes impure Aetheron (default 0.10).")
                .defineInRange("samplerRatio", 0.10, 0.0, 1.0);
        PF_IMPURE_UPGRADE_FACTOR = BUILDER
                .comment("Upgrade multiplier on the impure Aetheron sampler (default 0.25; a future"
                        + " machine upgrade raises it to 1.0).")
                .defineInRange("upgradeFactor", 0.25, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.comment("Temperature (primary): biome temperature plus nearby heat sources/cold blocks"
                        + " drive Thermion and Cryon.")
                .push("temperature");
        PF_TEMP_ENABLED = BUILDER
                .comment("Sample local temperature and apply it to the particle field (default true).")
                .define("enabled", true);
        PF_TEMP_HEAT_SLOPE = BUILDER
                .comment("Thermion added per Kelvin above ambient (default 0.05).")
                .defineInRange("heatSlope", 0.05, 0.0, 1.0);
        PF_TEMP_HEAT_MIN = BUILDER
                .comment("Lowest Thermion change from a cold delta, i.e. the floor when colder than"
                        + " ambient (default -5.0).")
                .defineInRange("heatMin", -5.0, -50.0, 0.0);
        PF_TEMP_HEAT_MAX = BUILDER
                .comment("Highest Thermion gain from a hot delta (default 20.0).")
                .defineInRange("heatMax", 20.0, 0.0, 100.0);
        PF_TEMP_CHILL_SLOPE = BUILDER
                .comment("Cryon added per Kelvin below ambient (default 0.03).")
                .defineInRange("chillSlope", 0.03, 0.0, 1.0);
        PF_TEMP_CHILL_MAX = BUILDER
                .comment("Highest Cryon gain from a cold delta (default 8.0).")
                .defineInRange("chillMax", 8.0, 0.0, 50.0);
        PF_TEMP_CRYON_SHARE = BUILDER
                .comment("Fraction of the heat gain deducted from Cryon before the chill bonus"
                        + " (default 0.5).")
                .defineInRange("cryonShare", 0.5, 0.0, 1.0);
        PF_TEMP_RADIUS = BUILDER
                .comment("Block radius scanned for heat sources and cold blocks (default 5, 1-8).")
                .defineInRange("radius", 5, 1, 8);
        BUILDER.pop();

        BUILDER.pop();
    }

    // Height terms
    public static final ModConfigSpec.DoubleValue PF_ALT_BASE;
    public static final ModConfigSpec.DoubleValue PF_ALT_RANGE;
    public static final ModConfigSpec.DoubleValue PF_LOW_BASE;
    public static final ModConfigSpec.DoubleValue PF_LOW_RANGE;
    public static final ModConfigSpec.DoubleValue PF_DEEP_BASE;
    public static final ModConfigSpec.DoubleValue PF_DEEP_RANGE;
    public static final ModConfigSpec.DoubleValue PF_H_BASE;
    public static final ModConfigSpec.DoubleValue PF_H_RANGE;
    public static final ModConfigSpec.DoubleValue PF_END_PEAK_BASE;
    public static final ModConfigSpec.DoubleValue PF_END_PEAK_RANGE;

    // Temperature
    public static final ModConfigSpec.DoubleValue PF_TEMP_OFFSET;
    public static final ModConfigSpec.DoubleValue PF_TEMP_DIVISOR;
    public static final ModConfigSpec.DoubleValue PF_TEMP_ALT_LOSS;

    // Thermal
    public static final ModConfigSpec.DoubleValue PF_THERMAL_BASE;
    public static final ModConfigSpec.DoubleValue PF_THERMAL_ALT_LOSS;
    public static final ModConfigSpec.DoubleValue PF_THERMAL_DEEP_LOSS;
    public static final ModConfigSpec.DoubleValue PF_THERMAL_MIN;
    public static final ModConfigSpec.DoubleValue PF_THERMAL_MAX;
    public static final ModConfigSpec.DoubleValue PF_THERMAL_RATIO_BASE;
    public static final ModConfigSpec.DoubleValue PF_THERMAL_RATIO_SCALE;
    public static final ModConfigSpec.DoubleValue PF_THERMAL_RATIO_MIN;
    public static final ModConfigSpec.DoubleValue PF_THERMAL_RATIO_MAX;

    // Charge
    public static final ModConfigSpec.DoubleValue PF_CHARGE_BASE;
    public static final ModConfigSpec.DoubleValue PF_CHARGE_WET_SCALE;
    public static final ModConfigSpec.DoubleValue PF_CHARGE_MAX;
    public static final ModConfigSpec.DoubleValue PF_CHARGE_HEIGHT_GRADIENT;
    public static final ModConfigSpec.DoubleValue PF_CHARGE_IMBALANCE_MIN;
    public static final ModConfigSpec.DoubleValue PF_CHARGE_IMBALANCE_MAX;

    // Aqueous
    public static final ModConfigSpec.DoubleValue PF_AQUEOUS_BASE;
    public static final ModConfigSpec.DoubleValue PF_AQUEOUS_WATER_SCALE;
    public static final ModConfigSpec.DoubleValue PF_AQUEOUS_LOW_SCALE;
    public static final ModConfigSpec.DoubleValue PF_AQUEOUS_MAX;

    // Aether
    public static final ModConfigSpec.DoubleValue PF_AETHER_OVERWORLD_BASE;
    public static final ModConfigSpec.DoubleValue PF_AETHER_OVERWORLD_DEEP_SCALE;
    public static final ModConfigSpec.DoubleValue PF_AETHER_NETHER_BASE;
    public static final ModConfigSpec.DoubleValue PF_AETHER_NETHER_DEEP_SCALE;
    public static final ModConfigSpec.DoubleValue PF_AETHER_NETHER_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue PF_AETHER_END_BASE;
    public static final ModConfigSpec.DoubleValue PF_AETHER_END_PEAK_SCALE;

    // Water
    public static final ModConfigSpec.DoubleValue PF_WATER_OCEAN;
    public static final ModConfigSpec.DoubleValue PF_WATER_WET;
    public static final ModConfigSpec.DoubleValue PF_WATER_LOW;
    public static final ModConfigSpec.DoubleValue PF_WATER_RIVER;

    // Dimension multipliers
    public static final ModConfigSpec.DoubleValue PF_DIM_OVERWORLD_THERMAL;
    public static final ModConfigSpec.DoubleValue PF_DIM_OVERWORLD_CHARGE;
    public static final ModConfigSpec.DoubleValue PF_DIM_OVERWORLD_AQUEOUS;
    public static final ModConfigSpec.DoubleValue PF_DIM_NETHER_THERMAL;
    public static final ModConfigSpec.DoubleValue PF_DIM_NETHER_CHARGE;
    public static final ModConfigSpec.DoubleValue PF_DIM_NETHER_AQUEOUS;
    public static final ModConfigSpec.DoubleValue PF_DIM_END_THERMAL;
    public static final ModConfigSpec.DoubleValue PF_DIM_END_CHARGE;
    public static final ModConfigSpec.DoubleValue PF_DIM_END_AQUEOUS;

    // Light heat
    public static final ModConfigSpec.DoubleValue PF_LIGHT_HEAT_ARTIFICIAL;
    public static final ModConfigSpec.DoubleValue PF_LIGHT_HEAT_SKY;
    public static final ModConfigSpec.DoubleValue PF_LIGHT_HEAT_CRYON_SHARE;

    // Intake tower capture
    public static final ModConfigSpec.DoubleValue PF_INTAKE_CAPTURE_LOSS;

    // Impure Aetheron sampling
    public static final ModConfigSpec.DoubleValue PF_IMPURE_SAMPLER_RATIO;
    public static final ModConfigSpec.DoubleValue PF_IMPURE_UPGRADE_FACTOR;

    // Temperature
    public static final ModConfigSpec.BooleanValue PF_TEMP_ENABLED;
    public static final ModConfigSpec.DoubleValue PF_TEMP_HEAT_SLOPE;
    public static final ModConfigSpec.DoubleValue PF_TEMP_HEAT_MIN;
    public static final ModConfigSpec.DoubleValue PF_TEMP_HEAT_MAX;
    public static final ModConfigSpec.DoubleValue PF_TEMP_CHILL_SLOPE;
    public static final ModConfigSpec.DoubleValue PF_TEMP_CHILL_MAX;
    public static final ModConfigSpec.DoubleValue PF_TEMP_CRYON_SHARE;
    public static final ModConfigSpec.IntValue PF_TEMP_RADIUS;

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {
    }
}
