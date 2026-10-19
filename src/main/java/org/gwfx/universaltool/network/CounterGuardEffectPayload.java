package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.CounterGuardEffectRenderer;

/** Client presentation state for Counter Guard's orbiting ward shields. */
public record CounterGuardEffectPayload(int playerId, Action action, long sequence)
        implements CustomPacketPayload {
    public enum Action {
        EXPAND,
        TRIGGER,
        CLEAR
    }

    public static final Type<CounterGuardEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    UniversalToolMod.MODID, "counter_guard_effect"));
    public static final StreamCodec<ByteBuf, CounterGuardEffectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CounterGuardEffectPayload::playerId,
                    ByteBufCodecs.VAR_INT.map(CounterGuardEffectPayload::actionFromIndex,
                            Action::ordinal), CounterGuardEffectPayload::action,
                    ByteBufCodecs.VAR_LONG, CounterGuardEffectPayload::sequence,
                    CounterGuardEffectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CounterGuardEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CounterGuardEffectRenderer.apply(payload));
    }

    private static Action actionFromIndex(int index) {
        Action[] values = Action.values();
        return values[Math.floorMod(index, values.length)];
    }
}
