package org.gwfx.universaltool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * S2C payload carrying a server-sampled compressed RGB24 live destination vista for a wormhole.
 */
public record WormholeLiveVistaPayload(
        String vistaId,
        int width,
        int height,
        byte[] compressedRgb
) implements CustomPacketPayload {

    public static final Type<WormholeLiveVistaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "wormhole_live_vista"));

    public static final StreamCodec<FriendlyByteBuf, WormholeLiveVistaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WormholeLiveVistaPayload::vistaId,
            ByteBufCodecs.VAR_INT, WormholeLiveVistaPayload::width,
            ByteBufCodecs.VAR_INT, WormholeLiveVistaPayload::height,
            ByteBufCodecs.byteArray(65536), WormholeLiveVistaPayload::compressedRgb,
            WormholeLiveVistaPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
