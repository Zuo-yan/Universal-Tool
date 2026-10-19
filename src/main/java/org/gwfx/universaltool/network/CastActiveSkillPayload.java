package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.skill.SkillManager;

/**
 * Cast intent from the client.
 *
 * <p>{@code releasing} distinguishes the key release that finishes a charge from the initial press
 * that starts one, so charged skills do not fire on the same edge as instant skills.
 */
public record CastActiveSkillPayload(boolean releasing) implements CustomPacketPayload {
    public static final CastActiveSkillPayload PRESS = new CastActiveSkillPayload(false);
    public static final CastActiveSkillPayload RELEASE = new CastActiveSkillPayload(true);

    public static final Type<CastActiveSkillPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "cast_active_skill"));
    public static final StreamCodec<ByteBuf, CastActiveSkillPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CastActiveSkillPayload::releasing,
            CastActiveSkillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CastActiveSkillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillManager.handleCast(player, payload.releasing());
            }
        });
    }
}
