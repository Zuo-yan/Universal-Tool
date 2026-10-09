package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.item.TeleportScrollItem;
import org.gwfx.universaltool.waypoint.TeleportManager;
import org.gwfx.universaltool.waypoint.Waypoint;
import org.gwfx.universaltool.waypoint.WaypointData;

import java.util.List;

// 1. 保存新传送点
public record SaveWaypointPayload(Waypoint waypoint) implements CustomPacketPayload {
    public static final Type<SaveWaypointPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "save_waypoint"));
    public static final StreamCodec<ByteBuf, SaveWaypointPayload> STREAM_CODEC = StreamCodec.composite(
            Waypoint.STREAM_CODEC, SaveWaypointPayload::waypoint,
            SaveWaypointPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SaveWaypointPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WaypointData data = player.getData(WaypointData.ATTACHMENT.get());
                if (data.addWaypoint(payload.waypoint())) {
                    PacketDistributor.sendToPlayer(player, new SyncWaypointsPayload(data.getWaypoints(), data.getMaxCapacity()));
                }
            }
        });
    }
}
