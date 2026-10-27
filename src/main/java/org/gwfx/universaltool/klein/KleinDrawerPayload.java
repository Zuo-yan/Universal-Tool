package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

/** Keeps the server's drawer mode in sync with the client's tab selection. */
public record KleinDrawerPayload(int mode) implements CustomPacketPayload {
    public static final Type<KleinDrawerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_drawer"));
    public static final StreamCodec<ByteBuf, KleinDrawerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KleinDrawerPayload::mode,
            KleinDrawerPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KleinDrawerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof KleinMenu menu) {
                menu.setDrawerMode(payload.mode());
            }
        });
    }
}
