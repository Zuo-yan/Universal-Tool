package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Opens the dual-mode Star Gate terminal on the client.
 *
 * <p>Page {@code 0} lets the player freely choose any of the 8 shared exploration realms;
 * page {@code 1} is the secondary menu for entering the clicking player's own 25x25x25 Private Starry Space.
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
