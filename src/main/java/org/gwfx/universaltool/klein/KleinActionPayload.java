package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

public record KleinActionPayload(long entryId, int operation, int sourceSlot, int sequence) implements CustomPacketPayload {
    public static final int EXTRACT = 0, EXTRACT_HALF = 1, EXTRACT_INVENTORY = 2, INSERT_CARRIED = 3, INSERT_ONE = 4, EXTRACT_ONE = 5;
    public static final Type<KleinActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_action"));
    public static final StreamCodec<ByteBuf, KleinActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, KleinActionPayload::entryId,
            ByteBufCodecs.VAR_INT, KleinActionPayload::operation,
            ByteBufCodecs.VAR_INT, KleinActionPayload::sourceSlot,
            ByteBufCodecs.VAR_INT, KleinActionPayload::sequence,
            KleinActionPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(KleinActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof KleinMenu menu) {
                menu.handleAction(payload);
            }
        });
    }
}
