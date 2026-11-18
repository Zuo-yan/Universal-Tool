package org.gwfx.universaltool.naiwa;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Client-to-server payload issued from the "滴滴代打" MCphone app to summon or command Naiwa bodyguards.
 */
public record DidiOrderPayload(int action) implements CustomPacketPayload {

    public static final int ACTION_SUMMON_ONE = 0;
    public static final int ACTION_SUMMON_THREE = 1;
    public static final int ACTION_MODE_FOLLOW = 2;
    public static final int ACTION_MODE_PATROL = 3;
    public static final int ACTION_ULTIMATE_LAUGH = 4;
    public static final int ACTION_DISMISS_ALL = 5;
    public static final int ACTION_STATUS = 6;

    public static final Type<DidiOrderPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "didi_order"));

    public static final StreamCodec<ByteBuf, DidiOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DidiOrderPayload::action,
            DidiOrderPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DidiOrderPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PacketDistributor.sendToPlayer(player, executeOrder(player, payload.action()));
            }
        });
    }

    static DidiStatusPayload executeOrder(ServerPlayer player, int action) {
        return DidiOrderService.execute(player, action);
    }
}
