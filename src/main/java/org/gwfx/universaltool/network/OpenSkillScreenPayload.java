package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

public record OpenSkillScreenPayload() implements CustomPacketPayload {
    public static final OpenSkillScreenPayload INSTANCE = new OpenSkillScreenPayload();
    public static final Type<OpenSkillScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "open_skill_screen"));
    public static final StreamCodec<ByteBuf, OpenSkillScreenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenSkillScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(org.gwfx.universaltool.client.ClientScreens::openSkills);
    }
}
