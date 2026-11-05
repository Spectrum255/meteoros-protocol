package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.network.DetectorData;
import com.spectrum255.meteorosprotocol.network.DetectorPayload;
import com.spectrum255.meteorosprotocol.particle.Phase;
import java.util.ArrayList;
import java.util.List;
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
 *
 * <p>Rows iterate {@link Phase#all()}. Core phases are always listed; addon
 * phases appear only when their concentration is positive. At most
 * {@value #MAX_ROWS} phases fit the fixed panel; the rest are folded into a
 * {@code +N} counter next to the title.</p>
 */
@EventBusSubscriber(modid = MeteorosProtocol.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class DetectorHud {
    private static final int MAX_ROWS = 6;
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

        List<Phase> phases = Phase.all();
        List<Phase> visible = new ArrayList<>();
        float total = 0.0F;
        for (Phase phase : phases) {
            float value = concentration(payload, phase);
            total += value;
            if (phase.core() || value > 0.0F) {
                visible.add(phase);
            }
        }

        graphics.drawString(minecraft.font,
                Component.translatable("hud.meteorosprotocol.detector.total", format(total)),
                x + 4, y + 16, TEXT, false);

        int shown = Math.min(MAX_ROWS, visible.size());
        for (int index = 0; index < shown; index++) {
            Phase phase = visible.get(index);
            float value = concentration(payload, phase);
            int column = index % 2;
            int row = index / 2;
            int px = x + 4 + column * 76;
            int py = y + 28 + row * 11;
            graphics.fill(px, py + 3, px + 3, py + 6, phase.argb());
            graphics.drawString(minecraft.font, shortLabel(phase), px + 6, py, TEXT, false);
            graphics.drawString(minecraft.font, format(value), px + 52, py, phase.argb(), false);
        }
        if (visible.size() > shown) {
            graphics.drawString(minecraft.font, "+" + (visible.size() - shown),
                    x + width - 16, y + 4, TEXT, false);
        }

        String weatherKey = payload.thundering()
                ? "hud.meteorosprotocol.detector.weather.thunder"
                : payload.raining()
                        ? "hud.meteorosprotocol.detector.weather.rain"
                        : "hud.meteorosprotocol.detector.weather.clear";
        graphics.drawString(minecraft.font, Component.translatable(weatherKey),
                x + 4, y + 62, TEXT, false);
    }

    private static float concentration(DetectorPayload payload, Phase phase) {
        switch (phase.key().getPath()) {
            case "aetheron":
                return payload.aetheron();
            case "thermion":
                return payload.thermion();
            case "cryon":
                return payload.cryon();
            case "voltix_pos":
                return payload.voltixPos();
            case "voltix_neg":
                return payload.voltixNeg();
            case "aquantum":
                return payload.aquantum();
            default:
                for (DetectorPayload.Extra entry : payload.extra()) {
                    if (entry.id().equals(phase.key())) {
                        return entry.value();
                    }
                }
                return 0.0F;
        }
    }

    /** Compact labels for the core phases; addon phases use their id path. */
    private static String shortLabel(Phase phase) {
        return switch (phase.key().getPath()) {
            case "aetheron" -> "Ae";
            case "thermion" -> "T";
            case "cryon" -> "C";
            case "voltix_pos" -> "V+";
            case "voltix_neg" -> "V-";
            case "aquantum" -> "Aq";
            default -> phase.key().getPath();
        };
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
