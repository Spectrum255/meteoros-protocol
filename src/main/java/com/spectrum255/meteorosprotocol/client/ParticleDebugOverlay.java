package com.spectrum255.meteorosprotocol.client;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.network.DetectorData;
import com.spectrum255.meteorosprotocol.network.DetectorPayload;
import com.spectrum255.meteorosprotocol.particle.FieldSample;
import com.spectrum255.meteorosprotocol.particle.Saturation;
import com.spectrum255.meteorosprotocol.particle.SimpleBiomeField;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

/**
 * Adds a structured particle-field block to the F3 debug screen.
 *
 * <p>The field is sampled on the client at most once per {@value
 * #SAMPLE_INTERVAL_TICKS} ticks or after the player crosses a block boundary,
 * whichever comes first. The overlay itself only reads the cache, so holding
 * F3 does not sample the field every frame.</p>
 */
@EventBusSubscriber(modid = MeteorosProtocol.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ParticleDebugOverlay {
    private static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final int MAX_SATURATION_RULES = 3;

    private static FieldSample cachedSample;
    private static ResourceKey<Level> cachedDimension;
    private static BlockPos cachedPos;
    private static long sampledAtTick = Long.MIN_VALUE;

    private ParticleDebugOverlay() {
    }

    @SubscribeEvent
    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return;
        }

        BlockPos pos = player.blockPosition();
        FieldSample sample = sample(level, pos);

        List<String> lines = event.getLeft();
        lines.add(String.format(Locale.ROOT, "Meteoros | %s %d %d %d | %s",
                level.dimension().location(),
                pos.getX(), pos.getY(), pos.getZ(),
                level.getBiome(pos).unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("unknown")));

        lines.add(String.format(Locale.ROOT,
                "  Ae=%.1f T=%.1f C=%.1f V+=%.1f V-=%.1f Aq=%.1f",
                sample.aetheron(), sample.thermion(), sample.cryon(),
                sample.voltixPos(), sample.voltixNeg(), sample.aquantum()));

        lines.add(String.format(Locale.ROOT,
                "  D_th=%.1f D_ch=%.1f D_aq=%.1f D_ae=%.1f q=%.2f D_total=%.1f",
                sample.thermalDensity(), sample.chargeDensity(),
                sample.aqueousDensity(), sample.primordialDensity(),
                sample.chargeImbalance(), sample.totalDensity()));

        lines.add("  sat: " + saturationRules(sample));
        lines.add("  weather: " + weatherState());
    }

    /**
     * Returns a cached field sample for {@code pos}, refreshing it when the
     * player changed block, changed dimension, or {@value
     * #SAMPLE_INTERVAL_TICKS} ticks have elapsed.
     */
    private static FieldSample sample(Level level, BlockPos pos) {
        long now = level.getGameTime();
        boolean stale = cachedSample == null
                || !level.dimension().equals(cachedDimension)
                || !pos.equals(cachedPos)
                || now < sampledAtTick
                || now - sampledAtTick >= SAMPLE_INTERVAL_TICKS;
        if (stale) {
            cachedSample = SimpleBiomeField.sampleAt(level, pos);
            cachedDimension = level.dimension();
            cachedPos = pos.immutable();
            sampledAtTick = now;
        }
        return cachedSample;
    }

    /**
     * Names of the saturation rules currently triggered, at most
     * {@value #MAX_SATURATION_RULES} entries, or {@code normal}.
     */
    private static String saturationRules(FieldSample sample) {
        Saturation saturation = Saturation.of(sample);
        List<String> rules = new ArrayList<>(8);
        if (saturation.lowDensity()) {
            rules.add("low_density");
        }
        if (saturation.highDensity()) {
            rules.add("high_density");
        }
        if (saturation.thermalSaturated()) {
            rules.add("thermal");
        }
        if (saturation.chargeSaturated()) {
            rules.add("charge");
        }
        if (saturation.aqueousSaturated()) {
            rules.add("aqueous");
        }
        if (saturation.primordialSaturated()) {
            rules.add("primordial");
        }
        if (saturation.oversaturated()) {
            rules.add("phase_over");
        }
        if (saturation.chargeImbalanced()) {
            rules.add("charge_imbalance");
        }
        if (rules.isEmpty()) {
            return "normal";
        }
        return String.join(", ", rules.subList(0, Math.min(MAX_SATURATION_RULES, rules.size())));
    }

    /** Local weather state as last reported by the detector payload. */
    private static String weatherState() {
        DetectorPayload weather = DetectorData.latest();
        if (weather == null) {
            return "unknown";
        }
        if (weather.thundering()) {
            return "thunder";
        }
        return weather.raining() ? "rain" : "clear";
    }
}
