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

/**
 * Opens the gate terminal on the client.
 *
 * <p>Carries the owner's display name and the landscape behind the gate so the VISIT page can show
 * the visitor where they are about to go. Both are empty for a gate whose base does not exist yet.
 */
public record PersonalSpaceScreenPayload(BlockPos gatePos, int mode, String ownerName, String environmentId)
        implements CustomPacketPayload {
    public static final Type<PersonalSpaceScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "personal_space_screen"));
    public static final StreamCodec<ByteBuf, PersonalSpaceScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PersonalSpaceScreenPayload::gatePos,
            ByteBufCodecs.VAR_INT, PersonalSpaceScreenPayload::mode,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceScreenPayload::ownerName,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceScreenPayload::environmentId,
            PersonalSpaceScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PersonalSpaceScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> org.gwfx.universaltool.client.ClientScreens.openPersonalSpace(
                payload.gatePos(), payload.mode(), payload.ownerName(), payload.environmentId()));
    }
}
