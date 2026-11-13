package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.item.TeleportCodexItem;
import org.gwfx.universaltool.waypoint.Waypoint;
import org.gwfx.universaltool.waypoint.WaypointData;

import java.util.regex.Pattern;

// 1. 保存新传送点
public record SaveWaypointPayload(Waypoint waypoint) implements CustomPacketPayload {
    private static final Pattern SAFE_PHOTO_ID = Pattern.compile("^[a-z0-9_.-]+$");

    public static final Type<SaveWaypointPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "save_waypoint"));
    public static final StreamCodec<ByteBuf, SaveWaypointPayload> STREAM_CODEC = StreamCodec.composite(
            Waypoint.STREAM_CODEC, SaveWaypointPayload::waypoint,
            SaveWaypointPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SaveWaypointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && TeleportCodexItem.hasCodex(player)) {
                Waypoint clientWp = payload.waypoint();
                String rawName = clientWp.name() == null ? "" : clientWp.name().trim();
                if (rawName.isEmpty()) {
                    rawName = "Waypoint";
                } else if (rawName.length() > 32) {
                    rawName = rawName.substring(0, 32);
                }

                String rawPhoto = clientWp.photoId() == null ? "" : clientWp.photoId();
                String safePhoto = (!rawPhoto.isEmpty()
                        && !rawPhoto.contains("..")
                        && !rawPhoto.contains("/")
                        && !rawPhoto.contains("\\")
                        && SAFE_PHOTO_ID.matcher(rawPhoto).matches()) ? rawPhoto : "";

                // Enforce server-authoritative dimension, coordinates, and orientation
                Waypoint serverWaypoint = Waypoint.of(
                        rawName,
                        player.serverLevel().dimension().location(),
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        player.getYRot(),
                        player.getXRot(),
                        safePhoto
                );

                WaypointData data = player.getData(WaypointData.ATTACHMENT.get());
                if (data.addWaypoint(serverWaypoint)) {
                    PacketDistributor.sendToPlayer(player, new SyncWaypointsPayload(data.getWaypoints(), data.getMaxCapacity()));
                }
            }
        });
    }
}
