package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.ClientScreens;

/**
 * Answers a {@link PersonalSpaceActionPayload}.
 *
 * <p>Rejections used to go to the chat bar, which left the screen sitting there with no explanation.
 * The screen now stays open and shows the reason inline, so every outcome has to come back here.
 *
 * @param gatePos    gate the player acted on, used to route the answer to the right screen
 * @param mode       the originating {@code PersonalSpaceScreen} mode, so a stale reply cannot affect another mode
 * @param accepted   {@code true} when the action succeeded and the screen should close
 * @param messageKey translation key explaining a rejection; empty when accepted
 */
public record PersonalSpaceResultPayload(BlockPos gatePos, int mode, boolean accepted, String messageKey)
        implements CustomPacketPayload {
    public static final Type<PersonalSpaceResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "personal_space_result"));
    public static final StreamCodec<ByteBuf, PersonalSpaceResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, PersonalSpaceResultPayload::gatePos,
            ByteBufCodecs.VAR_INT, PersonalSpaceResultPayload::mode,
            ByteBufCodecs.BOOL, PersonalSpaceResultPayload::accepted,
            ByteBufCodecs.STRING_UTF8, PersonalSpaceResultPayload::messageKey,
            PersonalSpaceResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PersonalSpaceResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientScreens.handlePersonalSpaceResult(payload));
    }
}
