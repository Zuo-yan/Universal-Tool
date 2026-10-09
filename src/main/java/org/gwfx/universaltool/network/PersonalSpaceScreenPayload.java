package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.PersonalSpaceScreen;

public record PersonalSpaceScreenPayload(BlockPos gatePos, int mode) implements CustomPacketPayload {
    public static final Type<PersonalSpaceScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "personal_space_screen"));
    public static final StreamCodec<ByteBuf, PersonalSpaceScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PersonalSpaceScreenPayload::gatePos,
            ByteBufCodecs.VAR_INT, PersonalSpaceScreenPayload::mode,
            PersonalSpaceScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PersonalSpaceScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> org.gwfx.universaltool.client.ClientScreens.openPersonalSpace(payload.gatePos(), payload.mode()));
    }
}
