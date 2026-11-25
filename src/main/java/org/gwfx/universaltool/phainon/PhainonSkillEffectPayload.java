package org.gwfx.universaltool.phainon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 侵晨战技、终结技与觉醒普攻的服务端表现事件。
 */
public record PhainonSkillEffectPayload(
        int casterId,
        Action action,
        boolean mainHand,
        double originX,
        double originY,
        double originZ,
        float yawDeg,
        float pitchDeg,
        float hoverHeight,
        float dashDistance,
        float strikeReach,
        int totalTicks,
        long sequence
) implements CustomPacketPayload {

    public enum Action {
        START,
        SLASH,
        CANCEL,
        ULTIMATE_START,
        ULTIMATE_CANCEL,
        ENHANCED_BASIC
    }

    private static final AtomicLong EFFECT_SEQUENCE = new AtomicLong();

    public static final Type<PhainonSkillEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "phainon_skill_effect")
    );

    public static final StreamCodec<ByteBuf, PhainonSkillEffectPayload> STREAM_CODEC =
            StreamCodec.of(PhainonSkillEffectPayload::encode, PhainonSkillEffectPayload::decode);

    private static void encode(ByteBuf buf, PhainonSkillEffectPayload payload) {
        buf.writeInt(payload.casterId);
        buf.writeByte(payload.action.ordinal());
        buf.writeBoolean(payload.mainHand);
        buf.writeDouble(payload.originX);
        buf.writeDouble(payload.originY);
        buf.writeDouble(payload.originZ);
        buf.writeFloat(payload.yawDeg);
        buf.writeFloat(payload.pitchDeg);
        buf.writeFloat(payload.hoverHeight);
        buf.writeFloat(payload.dashDistance);
        buf.writeFloat(payload.strikeReach);
        buf.writeInt(payload.totalTicks);
        buf.writeLong(payload.sequence);
    }

    private static PhainonSkillEffectPayload decode(ByteBuf buf) {
        int casterId = buf.readInt();
        Action action = actionFromIndex(buf.readUnsignedByte());
        boolean mainHand = buf.readBoolean();
        double originX = buf.readDouble();
        double originY = buf.readDouble();
        double originZ = buf.readDouble();
        float yawDeg = buf.readFloat();
        float pitchDeg = buf.readFloat();
        float hoverHeight = buf.readFloat();
        float dashDistance = buf.readFloat();
        float strikeReach = buf.readFloat();
        int totalTicks = buf.readInt();
        long sequence = buf.readLong();
        return new PhainonSkillEffectPayload(
                casterId, action, mainHand, originX, originY, originZ, yawDeg, pitchDeg,
                hoverHeight, dashDistance, strikeReach, totalTicks, sequence
        );
    }

    private static Action actionFromIndex(int index) {
        Action[] values = Action.values();
        return values[Math.floorMod(index, values.length)];
    }

    static void broadcast(ServerPlayer caster, Action action, InteractionHand hand, int totalTicks) {
        broadcast(caster, action, hand, totalTicks, 0.0F);
    }

    static void broadcast(ServerPlayer caster, Action action, InteractionHand hand, int totalTicks, float hoverHeight) {
        broadcast(caster, action, hand, totalTicks, hoverHeight, 0.0F, 0.0F);
    }

    static void broadcastEnhancedBasic(ServerPlayer caster, float dashDistance, float strikeReach) {
        broadcast(caster, Action.ENHANCED_BASIC, InteractionHand.MAIN_HAND,
                PhainonEnhancedBasic.EFFECT_TICKS, 0.0F, dashDistance, strikeReach);
    }

    private static void broadcast(ServerPlayer caster, Action action, InteractionHand hand, int totalTicks,
                                  float hoverHeight, float dashDistance, float strikeReach) {
        var payload = new PhainonSkillEffectPayload(
                caster.getId(), action, hand == InteractionHand.MAIN_HAND,
                caster.getX(), caster.getY(), caster.getZ(), caster.getYRot(), caster.getXRot(),
                hoverHeight, dashDistance, strikeReach, totalTicks, EFFECT_SEQUENCE.incrementAndGet());
        sendNearby(caster, payload);
    }

    private static void sendNearby(ServerPlayer caster, PhainonSkillEffectPayload payload) {
        for (ServerPlayer recipient : caster.serverLevel().players()) {
            if (recipient == caster || recipient.distanceToSqr(caster) <= 64.0 * 64.0) {
                PacketDistributor.sendToPlayer(recipient, payload);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
