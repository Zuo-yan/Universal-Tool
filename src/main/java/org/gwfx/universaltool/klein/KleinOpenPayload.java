package org.gwfx.universaltool.klein;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;

public record KleinOpenPayload() implements CustomPacketPayload {
    public static final KleinOpenPayload INSTANCE = new KleinOpenPayload();
    public static final Type<KleinOpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "klein_open"));
    public static final StreamCodec<ByteBuf, KleinOpenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(KleinOpenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.containerMenu != player.inventoryMenu) return;
            Inventory inventory = player.getInventory();
            int slot = findBottle(inventory);
            if (slot >= 0) KleinBottleItem.open(player, slot);
        });
    }

    static int findBottle(Inventory inventory) {
        if (inventory.getItem(inventory.selected).getItem() instanceof KleinBottleItem) return inventory.selected;
        if (inventory.getItem(Inventory.SLOT_OFFHAND).getItem() instanceof KleinBottleItem) return Inventory.SLOT_OFFHAND;
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).getItem() instanceof KleinBottleItem) return slot;
        }
        return -1;
    }
}
