package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

public record KleinSortPayload(int containerId, int mode, boolean descending) implements CustomPacketPayload {
    public static final Type<KleinSortPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_sort"));
    public static final StreamCodec<ByteBuf, KleinSortPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, KleinSortPayload::containerId,
            ByteBufCodecs.VAR_INT, KleinSortPayload::mode,
            ByteBufCodecs.BOOL, KleinSortPayload::descending,
            KleinSortPayload::new);

    @Override public Type<KleinSortPayload> type() { return TYPE; }

    public static void handle(KleinSortPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof KleinMenu menu
                    && menu.containerId == payload.containerId() && menu.stillValid(player)) {
                menu.saveSort(new KleinSortOrder(payload.mode(), payload.descending()));
            }
        });
    }
}
