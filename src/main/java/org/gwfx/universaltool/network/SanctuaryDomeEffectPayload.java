package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.concurrent.atomic.AtomicLong;

/** Optional cosmetic cue; older clients keep the ordinary particles. Duration zero fades the scene. */
public record SanctuaryDomeEffectPayload(long id, double x, double y, double z, float radius, int duration)
        implements CustomPacketPayload {
    public static final Type<SanctuaryDomeEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "sanctuary_dome_effect"));
    public static final StreamCodec<ByteBuf, SanctuaryDomeEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.id);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.radius);
                buf.writeInt(p.duration);
            },
            buf -> new SanctuaryDomeEffectPayload(buf.readLong(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readInt()));
    private static final AtomicLong LABEL = new AtomicLong();
    private static final double SEND_DISTANCE_SQR = 72.0 * 72.0;

    public static long open(ServerLevel level, Vec3 center, double radius, int duration) {
        long id = LABEL.incrementAndGet();
        dispatch(level, center, id, radius, duration);
        return id;
    }

    public static void fade(ServerLevel level, Vec3 center, long id) {
        dispatch(level, center, id, 0, 0);
    }

    private static void dispatch(ServerLevel level, Vec3 center, long id, double radius, int duration) {
        var payload = new SanctuaryDomeEffectPayload(id, center.x, center.y, center.z, (float) radius, duration);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= SEND_DISTANCE_SQR
                    && NetworkRegistry.hasChannel(player.connection, TYPE.id())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
