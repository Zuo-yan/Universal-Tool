package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.ClientWaypointCache;
import org.gwfx.universaltool.waypoint.Waypoint;

import java.util.List;

// 5. 同步传送点与最大容量到客户端
public record SyncWaypointsPayload(List<Waypoint> waypoints, int maxCapacity) implements CustomPacketPayload {
    public static final Type<SyncWaypointsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "sync_waypoints"));
    public static final StreamCodec<ByteBuf, SyncWaypointsPayload> STREAM_CODEC = StreamCodec.composite(
            Waypoint.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncWaypointsPayload::waypoints,
            ByteBufCodecs.VAR_INT, SyncWaypointsPayload::maxCapacity,
            SyncWaypointsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncWaypointsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientWaypointCache.update(payload.waypoints(), payload.maxCapacity());
        });
    }
}
