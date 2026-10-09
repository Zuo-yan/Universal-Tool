package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.waypoint.WaypointData;

// 2. 删除传送点
public record DeleteWaypointPayload(String waypointId) implements CustomPacketPayload {
    public static final Type<DeleteWaypointPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "delete_waypoint"));
    public static final StreamCodec<ByteBuf, DeleteWaypointPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DeleteWaypointPayload::waypointId,
            DeleteWaypointPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DeleteWaypointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WaypointData data = player.getData(WaypointData.ATTACHMENT.get());
                if (data.removeWaypoint(payload.waypointId())) {
                    PacketDistributor.sendToPlayer(player, new SyncWaypointsPayload(data.getWaypoints(), data.getMaxCapacity()));
                }
            }
        });
    }
}
