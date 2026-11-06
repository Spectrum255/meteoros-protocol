package com.spectrum255.meteorosprotocol.particle;

import java.util.Locale;

/**
 * Compact number formatting shared by the machine GUIs and the Jade overlay.
 *
 * <p>Bars are narrow, so the player-facing values use short units: values below
 * 1,000 keep the base unit ({@code 999 mPU}, {@code 12 FE}), values below one
 * million use a kilo prefix with one decimal ({@code 1.5 kPU}), and anything
 * larger uses the mega prefix ({@code 1.2 MPU}). Tooltips keep the exact
 * thousand-separated form ({@code 12,345,678 mPU}) via {@link #formatPlain}.</p>
 */
public final class ParticleFormat {
    private static final long KILO = 1_000L;
    private static final long MEGA = 1_000_000L;

    private ParticleFormat() {
    }

    /** Compact particle amount, e.g. {@code 999 mPU}, {@code 1.5 kPU}, {@code 1.2 MPU}. */
    public static String formatMPU(long amount) {
        return compact(amount, "mPU", "kPU", "MPU");
    }

    /** Compact energy amount, e.g. {@code 999 FE}, {@code 12.5 kFE}, {@code 1 MFE}. */
    public static String formatFE(long amount) {
        return compact(amount, "FE", "kFE", "MFE");
    }

    /** Exact per-thousand-grouped form for tooltips, e.g. {@code 12,345,678 mPU}. */
    public static String formatPlain(long amount, String unit) {
        return String.format(Locale.ROOT, "%,d %s", Math.max(0L, amount), unit);
    }

    private static String compact(long amount, String small, String kilo, String mega) {
        long value = Math.max(0L, amount);
        if (value < KILO) {
            return value + " " + small;
        }
        if (value < MEGA) {
            double kiloValue = value / (double) KILO;
            // Values that round up to 1000.0 would read as "1000 kPU"; promote them
            // to the next unit so a nearly-full tank shows "1 MPU" instead.
            if (kiloValue < 999.95) {
                return trimOneDecimal(kiloValue) + " " + kilo;
            }
        }
        return trimOneDecimal(value / (double) MEGA) + " " + mega;
    }

    private static String trimOneDecimal(double value) {
        String formatted = String.format(Locale.ROOT, "%.1f", value);
        if (formatted.endsWith(".0")) {
            return formatted.substring(0, formatted.length() - 2);
        }
        return formatted;
    }
}
