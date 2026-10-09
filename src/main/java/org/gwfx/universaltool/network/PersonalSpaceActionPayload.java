package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.space.PrivateSpaceManager;

public record PersonalSpaceActionPayload(BlockPos gatePos, int mode, String environment,
                                         String password, String confirmation) implements CustomPacketPayload {
    public static final Type<PersonalSpaceActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "personal_space_action"));
    public static final StreamCodec<ByteBuf, PersonalSpaceActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PersonalSpaceActionPayload::gatePos,
            ByteBufCodecs.VAR_INT, PersonalSpaceActionPayload::mode,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceActionPayload::environment,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceActionPayload::password,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceActionPayload::confirmation,
            PersonalSpaceActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PersonalSpaceActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PrivateSpaceManager.handleAction(player, payload);
            }
        });
    }
}
