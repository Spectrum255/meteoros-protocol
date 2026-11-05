package com.spectrum255.meteorosprotocol.client;

import net.minecraft.client.Minecraft;

/** Client-only screen opening, kept out of common item classes. */
public final class DetectorClientHooks {
    private DetectorClientHooks() {
    }

    public static void openMap() {
        Minecraft.getInstance().setScreen(new DetectorMapScreen());
    }
}
