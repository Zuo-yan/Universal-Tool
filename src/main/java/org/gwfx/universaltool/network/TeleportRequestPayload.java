package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.waypoint.TeleportManager;
import org.gwfx.universaltool.waypoint.Waypoint;
import org.gwfx.universaltool.waypoint.WaypointData;

// 3. 传送请求
public record TeleportRequestPayload(String waypointId) implements CustomPacketPayload {
    public static final Type<TeleportRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "teleport_request"));
    public static final StreamCodec<ByteBuf, TeleportRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TeleportRequestPayload::waypointId,
            TeleportRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TeleportRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WaypointData data = player.getData(WaypointData.ATTACHMENT.get());
                for (Waypoint wp : data.getWaypoints()) {
                    if (wp.id().equals(payload.waypointId())) {
                        TeleportManager.startTeleport(player, wp, false);
                        break;
                    }
                }
            }
        });
    }
}
