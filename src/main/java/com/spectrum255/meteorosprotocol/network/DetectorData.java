package com.spectrum255.meteorosprotocol.network;

/**
 * Common-side holder for the latest detector snapshot. Keeping the payload
 * handler free of client classes lets the dedicated server load this package
 * safely; only {@code client.DetectorHud} reads the value.
 */
public final class DetectorData {
    private static volatile DetectorPayload latest;

    private DetectorData() {
    }

    public static void accept(DetectorPayload payload) {
        latest = payload;
    }

    public static DetectorPayload latest() {
        return latest;
    }

    public static void clear() {
        latest = null;
    }
}
