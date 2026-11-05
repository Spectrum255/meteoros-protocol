package com.spectrum255.meteorosprotocol.network;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import com.spectrum255.meteorosprotocol.compat.WeatherParticleModifier;
import com.spectrum255.meteorosprotocol.particle.FieldSample;
import com.spectrum255.meteorosprotocol.particle.SimpleBiomeField;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Samples the field for players carrying a detector and syncs it to their HUD. */
@EventBusSubscriber(modid = MeteorosProtocol.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class DetectorServerEvents {
    private static final int SEND_INTERVAL = 10;

    private DetectorServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (serverPlayer.tickCount % SEND_INTERVAL != 0 || !hasDetector(serverPlayer)) {
            return;
        }
        ServerLevel level = serverPlayer.serverLevel();
        FieldSample base = SimpleBiomeField.sampleAt(level, serverPlayer.blockPosition());
        WeatherParticleModifier.Result weather =
                WeatherParticleModifier.apply(level, serverPlayer.blockPosition(), base);
        FieldSample sample = weather.sample();
        PacketDistributor.sendToPlayer(serverPlayer, new DetectorPayload(
                sample.thermion(),
                sample.cryon(),
                sample.voltixPos(),
                sample.voltixNeg(),
                sample.aquantum(),
                sample.aetheron(),
                weather.raining(),
                weather.thundering()
        ));
    }

    static boolean hasDetector(Player player) {
        Item detector = MeteorosProtocol.PARTICLE_DETECTOR.get();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(detector)) {
                return true;
            }
        }
        return player.getOffhandItem().is(detector);
    }
}
