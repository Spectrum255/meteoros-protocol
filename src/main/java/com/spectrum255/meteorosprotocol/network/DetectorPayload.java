package com.spectrum255.meteorosprotocol.network;

import com.spectrum255.meteorosprotocol.MeteorosProtocol;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client snapshot of the particle field at the detector owner's
 * position. Sent only while a detector is in the player's inventory.
 *
 * <p>The six core phases stay at the head of the wire format; any addon phase
 * that produced a non-zero value is appended after them as
 * {@code varint count + (resource location, float) pairs}.</p>
 */
public record DetectorPayload(
        float thermion,
        float cryon,
        float voltixPos,
        float voltixNeg,
        float aquantum,
        float aetheron,
        boolean raining,
        boolean thundering,
        List<Extra> extra
) implements CustomPacketPayload {
    /** One addon-phase concentration carried after the six core values. */
    public record Extra(ResourceLocation id, float value) {
    }

    /** Core-only payload, kept for callers that do not know about addon phases. */
    public DetectorPayload(float thermion, float cryon, float voltixPos,
            float voltixNeg, float aquantum, float aetheron,
            boolean raining, boolean thundering) {
        this(thermion, cryon, voltixPos, voltixNeg, aquantum, aetheron, raining, thundering, List.of());
    }

    public DetectorPayload {
        extra = List.copyOf(extra);
    }

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
        buffer.writeVarInt(payload.extra.size());
        for (Extra entry : payload.extra) {
            buffer.writeResourceLocation(entry.id());
            buffer.writeFloat(entry.value());
        }
    }

    private static DetectorPayload decode(RegistryFriendlyByteBuf buffer) {
        float thermion = buffer.readFloat();
        float cryon = buffer.readFloat();
        float voltixPos = buffer.readFloat();
        float voltixNeg = buffer.readFloat();
        float aquantum = buffer.readFloat();
        float aetheron = buffer.readFloat();
        boolean raining = buffer.readBoolean();
        boolean thundering = buffer.readBoolean();
        int extraCount = buffer.readVarInt();
        List<Extra> extra = new ArrayList<>(extraCount);
        for (int index = 0; index < extraCount; index++) {
            extra.add(new Extra(buffer.readResourceLocation(), buffer.readFloat()));
        }
        return new DetectorPayload(thermion, cryon, voltixPos, voltixNeg, aquantum, aetheron,
                raining, thundering, extra);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
