package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.skill.SkillManager;

public record RequestSkillScreenPayload() implements CustomPacketPayload {
    public static final RequestSkillScreenPayload INSTANCE = new RequestSkillScreenPayload();
    public static final Type<RequestSkillScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "request_skill_screen"));
    public static final StreamCodec<ByteBuf, RequestSkillScreenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestSkillScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillManager.sync(player);
                PacketDistributor.sendToPlayer(player, OpenSkillScreenPayload.INSTANCE);
            }
        });
    }
}
