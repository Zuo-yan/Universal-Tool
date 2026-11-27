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

/**
 * 技能系统统一 3D 立体视觉特效网络包（S2C）。
 *
 * <p>durationTicks == 0 表示提前淡出指定 id 的持续类 3D 特效（如冰晶牢笼、虚空星锚）。
 */
public record Skill3dEffectPayload(
        int kind,
        long id,
        double x, double y, double z,
        double tx, double ty, double tz,
        float radius,
        int durationTicks,
        int color
) implements CustomPacketPayload {

    public enum EffectKind {
        ASTRAL_CLEAVE,
        THUNDER_ZONE,
        LIGHTNING_CHAIN,
        GRAVITY_BLACK_HOLE,
        ICE_PRISON,
        OPPRESSIVE_WAVE,
        VOID_ANCHOR,
        VOID_SLASH,
        PHOENIX_WINGS,
        SANCTUARY_BLAST,
        AIR_SHOCKWAVE,
        ORE_SONAR,
        STONE_TO_ORE_WAVE,
        ALCHEMY_CIRCLE,
        RESONANCE_BLADE,
        RESONANCE_AEGIS;

        public static EffectKind byOrdinal(int ordinal) {
            EffectKind[] values = values();
            return values[Math.max(0, Math.min(values.length - 1, ordinal))];
        }
    }

    public static final Type<Skill3dEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "skill_3d_effect"));

    public static final StreamCodec<ByteBuf, Skill3dEffectPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.kind);
                buf.writeLong(p.id);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeDouble(p.tx);
                buf.writeDouble(p.ty);
                buf.writeDouble(p.tz);
                buf.writeFloat(p.radius);
                buf.writeInt(p.durationTicks);
                buf.writeInt(p.color);
            },
            buf -> new Skill3dEffectPayload(
                    buf.readInt(),
                    buf.readLong(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readInt()
            ));

    private static final AtomicLong SEQ = new AtomicLong();
    private static final double BROADCAST_RANGE_SQR = 96.0 * 96.0;

    public static long nextId() {
        return SEQ.incrementAndGet();
    }

    /** 发射一次性或自管理寿命的 3D 特效，返回分配的场景 ID。 */
    public static long spawn(ServerLevel level, EffectKind kind, Vec3 origin, Vec3 targetOrDir,
                             float radius, int durationTicks, int color) {
        long id = nextId();
        dispatch(level, origin, new Skill3dEffectPayload(
                kind.ordinal(), id,
                origin.x, origin.y, origin.z,
                targetOrDir.x, targetOrDir.y, targetOrDir.z,
                radius, durationTicks, color));
        return id;
    }

    /** 使用显式 id 发送（可用于持续同步或 duration=0 提前关闭）。 */
    public static void sendWithId(ServerLevel level, long id, EffectKind kind, Vec3 origin, Vec3 targetOrDir,
                                  float radius, int durationTicks, int color) {
        dispatch(level, origin, new Skill3dEffectPayload(
                kind.ordinal(), id,
                origin.x, origin.y, origin.z,
                targetOrDir.x, targetOrDir.y, targetOrDir.z,
                radius, durationTicks, color));
    }

    /** 提前淡出消散持续性 3D 场景。 */
    public static void fade(ServerLevel level, Vec3 center, long id) {
        dispatch(level, center, new Skill3dEffectPayload(
                0, id, center.x, center.y, center.z, 0, 0, 0, 0.0F, 0, 0));
    }

    private static void dispatch(ServerLevel level, Vec3 center, Skill3dEffectPayload payload) {
        if (level == null) return;
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(center) <= BROADCAST_RANGE_SQR
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
