package org.gwfx.universaltool.klein;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

/** Client-chosen concrete ingredient stacks for the Klein crafting drawer (JEI transfer). */
public record KleinCraftFillPayload(List<ItemStack> grid, int sequence, boolean maxTransfer) implements CustomPacketPayload {
    public static final int GRID_SIZE = 9;
    public static final Type<KleinCraftFillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_craft_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KleinCraftFillPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public KleinCraftFillPayload decode(RegistryFriendlyByteBuf buffer) {
            int size = ByteBufCodecs.VAR_INT.decode(buffer);
            if (size < 0 || size > GRID_SIZE) throw new IllegalArgumentException("Invalid Klein crafting grid");
            List<ItemStack> grid = new ArrayList<>(GRID_SIZE);
            for (int i = 0; i < GRID_SIZE; i++) {
                grid.add(i < size ? ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer) : ItemStack.EMPTY);
            }
            return new KleinCraftFillPayload(grid, ByteBufCodecs.VAR_INT.decode(buffer), buffer.readBoolean());
        }

        @Override public void encode(RegistryFriendlyByteBuf buffer, KleinCraftFillPayload value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.grid.size());
            value.grid.forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack));
            ByteBufCodecs.VAR_INT.encode(buffer, value.sequence);
            buffer.writeBoolean(value.maxTransfer);
        }
    };

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KleinCraftFillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof KleinMenu menu) {
                menu.handleCraftFill(payload);
            }
        });
    }
}
