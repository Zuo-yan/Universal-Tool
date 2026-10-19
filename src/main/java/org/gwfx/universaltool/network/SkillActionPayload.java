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

import java.util.ArrayList;
import java.util.List;

public record SkillActionPayload(Action action, String skillId, List<String> loadout) implements CustomPacketPayload {
    public enum Action {
        LEARN,
        SELECT_ACTIVE,
        SET_ACTIVE,
        SET_PASSIVE,
        UNSET_ACTIVE,
        UNSET_PASSIVE,
        RESET
    }

    public static final Type<SkillActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "skill_action"));
    public static final StreamCodec<ByteBuf, SkillActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(SkillActionPayload::actionFromIndex, Action::ordinal),
            SkillActionPayload::action,
            ByteBufCodecs.STRING_UTF8, SkillActionPayload::skillId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SkillActionPayload::loadout,
            SkillActionPayload::new);

    public SkillActionPayload {
        loadout = List.copyOf(loadout);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SkillManager.handleAction(player, payload.action(), payload.skillId(), new ArrayList<>(payload.loadout()));
            }
        });
    }

    private static Action actionFromIndex(int index) {
        Action[] values = Action.values();
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
