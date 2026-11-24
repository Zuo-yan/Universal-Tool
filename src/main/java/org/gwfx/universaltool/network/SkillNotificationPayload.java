package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * 服务端推送到客户端的星辉仪式感通知（触发动态 Toast 横幅、和弦音效与微弱 HUD 经验脉动）。
 */
public record SkillNotificationPayload(Kind kind, String key, int value) implements CustomPacketPayload {
    public enum Kind {
        XP_GAIN,
        LEVEL_UP,
        STAR_RANK_UP,
        SKILL_UNLOCKED,
        SKILL_AWAKENED
    }

    public static final Type<SkillNotificationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "skill_notification"));

    public static final StreamCodec<ByteBuf, SkillNotificationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(SkillNotificationPayload::kindFromIndex, Kind::ordinal),
            SkillNotificationPayload::kind,
            ByteBufCodecs.STRING_UTF8,
            SkillNotificationPayload::key,
            ByteBufCodecs.VAR_INT,
            SkillNotificationPayload::value,
            SkillNotificationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static Kind kindFromIndex(int index) {
        Kind[] values = Kind.values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
