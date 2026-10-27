package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

public record KleinRenamePayload(String name) implements CustomPacketPayload {
    public static final Type<KleinRenamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_rename"));
    public static final StreamCodec<ByteBuf, KleinRenamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), KleinRenamePayload::name,
            KleinRenamePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KleinRenamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof KleinMenu menu) {
                menu.setAnvilItemName(payload.name());
            }
        });
    }
}
