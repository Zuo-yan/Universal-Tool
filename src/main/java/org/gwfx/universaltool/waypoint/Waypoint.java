package org.gwfx.universaltool.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record Waypoint(
        String id,
        String name,
        ResourceLocation dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        long timestamp,
        String photoId
) {
    public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(Waypoint::id),
            Codec.STRING.fieldOf("name").forGetter(Waypoint::name),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(Waypoint::dimension),
            Codec.DOUBLE.fieldOf("x").forGetter(Waypoint::x),
            Codec.DOUBLE.fieldOf("y").forGetter(Waypoint::y),
            Codec.DOUBLE.fieldOf("z").forGetter(Waypoint::z),
            Codec.FLOAT.fieldOf("yaw").forGetter(Waypoint::yaw),
            Codec.FLOAT.fieldOf("pitch").forGetter(Waypoint::pitch),
            Codec.LONG.fieldOf("timestamp").forGetter(Waypoint::timestamp),
            Codec.STRING.fieldOf("photoId").forGetter(Waypoint::photoId)
    ).apply(inst, Waypoint::new));

    public static final StreamCodec<ByteBuf, Waypoint> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public Waypoint decode(ByteBuf buffer) {
            String id = ByteBufCodecs.STRING_UTF8.decode(buffer);
            String name = ByteBufCodecs.STRING_UTF8.decode(buffer);
            ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buffer);
            double x = ByteBufCodecs.DOUBLE.decode(buffer);
            double y = ByteBufCodecs.DOUBLE.decode(buffer);
            double z = ByteBufCodecs.DOUBLE.decode(buffer);
            float yaw = ByteBufCodecs.FLOAT.decode(buffer);
            float pitch = ByteBufCodecs.FLOAT.decode(buffer);
            long timestamp = ByteBufCodecs.VAR_LONG.decode(buffer);
            String photoId = ByteBufCodecs.STRING_UTF8.decode(buffer);
            return new Waypoint(id, name, dimension, x, y, z, yaw, pitch, timestamp, photoId);
        }

        @Override
        public void encode(ByteBuf buffer, Waypoint wp) {
            ByteBufCodecs.STRING_UTF8.encode(buffer, wp.id());
            ByteBufCodecs.STRING_UTF8.encode(buffer, wp.name());
            ResourceLocation.STREAM_CODEC.encode(buffer, wp.dimension());
            ByteBufCodecs.DOUBLE.encode(buffer, wp.x());
            ByteBufCodecs.DOUBLE.encode(buffer, wp.y());
            ByteBufCodecs.DOUBLE.encode(buffer, wp.z());
            ByteBufCodecs.FLOAT.encode(buffer, wp.yaw());
            ByteBufCodecs.FLOAT.encode(buffer, wp.pitch());
            ByteBufCodecs.VAR_LONG.encode(buffer, wp.timestamp());
            ByteBufCodecs.STRING_UTF8.encode(buffer, wp.photoId());
        }
    };

    public static Waypoint of(String name, ResourceLocation dimension, double x, double y, double z, float yaw, float pitch, String photoId) {
        return new Waypoint(
                UUID.randomUUID().toString(),
                name,
                dimension,
                x, y, z,
                yaw, pitch,
                System.currentTimeMillis(),
                photoId
        );
    }
}
