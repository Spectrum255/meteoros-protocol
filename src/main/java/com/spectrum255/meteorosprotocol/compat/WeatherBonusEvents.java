package com.spectrum255.meteorosprotocol.compat;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Loads {@code config/meteorosprotocol-weather.toml} once the registries are frozen.
 *
 * <p>The weather type registry from Localized rain is only available after the server
 * exists, so this cannot run in mod construction. Loading here also makes
 * {@code /meteoros weather reload} cheap: it simply calls the same loader again.</p>
 */
@EventBusSubscriber(modid = MeteorosProtocol.MODID)
public final class WeatherBonusEvents {
    private WeatherBonusEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            WeatherBonusConfig.load(event.getServer());
        } catch (RuntimeException exception) {
            MeteorosProtocol.LOGGER.warn("Failed to load weather particle bonuses", exception);
        }
    }
}
