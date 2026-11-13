package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Opens the gate terminal on the client.
 *
 * <p>Carries the owner's display name, the landscape behind the gate, and the space dimension path
 * so the VISIT / CHANGE_PASSWORD pages can show a live local snapshot or the realm preview.
 */
public record PersonalSpaceScreenPayload(BlockPos gatePos, int mode, String ownerName,
                                         String environmentId, String spaceDimension)
        implements CustomPacketPayload {
    public static final Type<PersonalSpaceScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "personal_space_screen"));
    public static final StreamCodec<ByteBuf, PersonalSpaceScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PersonalSpaceScreenPayload::gatePos,
            ByteBufCodecs.VAR_INT, PersonalSpaceScreenPayload::mode,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceScreenPayload::ownerName,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceScreenPayload::environmentId,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceScreenPayload::spaceDimension,
            PersonalSpaceScreenPayload::new);

    public PersonalSpaceScreenPayload(BlockPos gatePos, int mode) {
        this(gatePos, mode, "", "", "");
    }

    public PersonalSpaceScreenPayload(BlockPos gatePos, int mode, String ownerName, String environmentId) {
        this(gatePos, mode, ownerName, environmentId, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
