package com.spectrum255.meteorosprotocol.network;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client snapshot of the particle field at the detector owner's
 * position. Sent only while a detector is in the player's inventory.
 */
public record DetectorPayload(
        float thermion,
        float cryon,
        float voltixPos,
        float voltixNeg,
        float aquantum,
        float aetheron,
        boolean raining,
        boolean thundering
) implements CustomPacketPayload {
    public static final Type<DetectorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MeteorosProtocol.MODID, "detector"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DetectorPayload> CODEC =
            StreamCodec.of(DetectorPayload::encode, DetectorPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, DetectorPayload payload) {
        buffer.writeFloat(payload.thermion);
        buffer.writeFloat(payload.cryon);
        buffer.writeFloat(payload.voltixPos);
        buffer.writeFloat(payload.voltixNeg);
        buffer.writeFloat(payload.aquantum);
        buffer.writeFloat(payload.aetheron);
        buffer.writeBoolean(payload.raining);
        buffer.writeBoolean(payload.thundering);
    }

    private static DetectorPayload decode(RegistryFriendlyByteBuf buffer) {
        return new DetectorPayload(
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
