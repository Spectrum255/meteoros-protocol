package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.network.DetectorData;
import com.spectrum255.meteorosprotocol.network.DetectorPayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Top-left HUD shown while a {@code particle_detector} is anywhere in the
 * player's inventory. Data is supplied by {@code DetectorServerEvents} every
 * ten ticks; the HUD only reads the latest client-side snapshot.
 */
@EventBusSubscriber(modid = MeteorosProtocol.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DetectorHud {
    private static final int[] PHASE_COLORS = {
            0xFFFFFFFF, 0xFFFF4A3F, 0xFF5FE8FF, 0xFFFFE14A, 0xFF9A5CFF, 0xFF3A54E0
    };
    private static final String[] PHASE_NAMES = {"Ae", "T", "C", "V+", "V-", "Aq"};
    private static final int PANEL_BG = 0xB0000000;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int TEXT = 0xFFD8D8D8;

    private DetectorHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !hasDetector(player)) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int x = 6;
        int y = 6;
        int width = 158;
        int height = 74;
        graphics.fill(x - 2, y - 2, x + width, y + height, PANEL_BG);

        graphics.drawString(minecraft.font,
                Component.translatable("hud.meteorosprotocol.detector.title"),
                x + 4, y + 4, TITLE, false);

        DetectorPayload payload = DetectorData.latest();
        if (payload == null) {
            graphics.drawString(minecraft.font,
                    Component.translatable("hud.meteorosprotocol.detector.waiting"),
                    x + 4, y + 18, TEXT, false);
            return;
        }

        float total = payload.thermion() + payload.cryon() + payload.voltixPos()
                + payload.voltixNeg() + payload.aquantum() + payload.aetheron();
        graphics.drawString(minecraft.font,
                Component.translatable("hud.meteorosprotocol.detector.total", format(total)),
                x + 4, y + 16, TEXT, false);

        float[] values = {
                payload.aetheron(), payload.thermion(), payload.cryon(),
                payload.voltixPos(), payload.voltixNeg(), payload.aquantum()
        };
        for (int index = 0; index < values.length; index++) {
            int column = index % 2;
            int row = index / 2;
            int px = x + 4 + column * 76;
            int py = y + 28 + row * 11;
            graphics.fill(px, py + 3, px + 3, py + 6, PHASE_COLORS[index]);
            graphics.drawString(minecraft.font, PHASE_NAMES[index], px + 6, py, TEXT, false);
            graphics.drawString(minecraft.font, format(values[index]), px + 26, py, phaseTextColor(index), false);
        }

        String weatherKey = payload.thundering()
                ? "hud.meteorosprotocol.detector.weather.thunder"
                : payload.raining()
                        ? "hud.meteorosprotocol.detector.weather.rain"
                        : "hud.meteorosprotocol.detector.weather.clear";
        graphics.drawString(minecraft.font, Component.translatable(weatherKey),
                x + 4, y + 62, TEXT, false);
    }

    private static int phaseTextColor(int index) {
        return PHASE_COLORS[index];
    }

    private static String format(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static boolean hasDetector(Player player) {
        Item detector = MeteorosProtocol.PARTICLE_DETECTOR.get();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(detector)) {
                return true;
            }
        }
        return player.getOffhandItem().is(detector);
    }
}
