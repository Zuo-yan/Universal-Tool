package org.gwfx.universaltool.phainon;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.gwfx.universaltool.UniversalToolMod;

public record MeteorShowerPayload(int casterId, InteractionHand hand, long start, MeteorShower shower,
                                 boolean cancelled) implements CustomPacketPayload {
    public static final Type<MeteorShowerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "phainon_meteor"));
    public static final StreamCodec<ByteBuf, MeteorShowerPayload> STREAM_CODEC =
            StreamCodec.of(MeteorShowerPayload::encode, MeteorShowerPayload::decode);

    private static void encode(ByteBuf buf, MeteorShowerPayload p) {
        buf.writeInt(p.casterId);
        buf.writeBoolean(p.hand == InteractionHand.MAIN_HAND);
        buf.writeLong(p.start);
        buf.writeBoolean(p.cancelled);
        writePoint(buf, p.shower.center());
        p.shower.landings().forEach(point -> writePoint(buf, point));
    }

    private static MeteorShowerPayload decode(ByteBuf buf) {
        int caster = buf.readInt();
        var hand = buf.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        long start = buf.readLong();
        boolean cancelled = buf.readBoolean();
        Vec3 center = readPoint(buf);
        var landings = new ArrayList<Vec3>(MeteorShower.COUNT);
        for (int i = 0; i < MeteorShower.COUNT; i++) landings.add(readPoint(buf));
        return new MeteorShowerPayload(caster, hand, start, new MeteorShower(center, landings), cancelled);
    }

    private static Vec3 readPoint(ByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writePoint(ByteBuf buf, Vec3 point) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
