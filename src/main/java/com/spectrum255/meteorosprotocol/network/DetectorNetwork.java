package com.spectrum255.meteorosprotocol.network;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers the client-bound detector snapshot payload. */
@EventBusSubscriber(modid = MeteorosProtocol.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DetectorNetwork {
    private DetectorNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                DetectorPayload.TYPE,
                DetectorPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> DetectorData.accept(payload))
        );
    }
}
