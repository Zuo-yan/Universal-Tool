package org.gwfx.universaltool.naiwa;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Server-to-client payload synchronizing Naiwa's belly-laugh MP3 playback with entity tracking.
 * Kept free of client-only imports; {@code ClientPayloadHandlers} dispatches to {@code NaiwaLaughSoundPlayer}.
 */
public record NaiwaLaughSoundPayload(
        int entityId,
        int durationTicks,
        boolean stop
) implements CustomPacketPayload {

    public static final Type<NaiwaLaughSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "naiwa_laugh_sound"));

    public static final StreamCodec<ByteBuf, NaiwaLaughSoundPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NaiwaLaughSoundPayload::entityId,
            ByteBufCodecs.VAR_INT, NaiwaLaughSoundPayload::durationTicks,
            ByteBufCodecs.BOOL, NaiwaLaughSoundPayload::stop,
            NaiwaLaughSoundPayload::new
    );

    public static NaiwaLaughSoundPayload start(int entityId, int durationTicks) {
        return new NaiwaLaughSoundPayload(entityId, durationTicks, false);
    }

    public static NaiwaLaughSoundPayload stop(int entityId) {
        return new NaiwaLaughSoundPayload(entityId, 0, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
