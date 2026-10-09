package org.gwfx.universaltool.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.item.TeleportScrollItem;
import org.gwfx.universaltool.waypoint.Waypoint;
import org.gwfx.universaltool.waypoint.WaypointData;

// 4. 誊写卷轴请求
public record WriteScrollPayload(String waypointId) implements CustomPacketPayload {
    public static final Type<WriteScrollPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "write_scroll"));
    public static final StreamCodec<ByteBuf, WriteScrollPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WriteScrollPayload::waypointId,
            WriteScrollPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(WriteScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WaypointData data = player.getData(WaypointData.ATTACHMENT.get());
                for (Waypoint wp : data.getWaypoints()) {
                    if (wp.id().equals(payload.waypointId())) {
                        // 检查是否有纸
                        if (player.getInventory().contains(new ItemStack(Items.PAPER))) {
                            // 扣除 1 张纸
                            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                                ItemStack st = player.getInventory().getItem(i);
                                if (st.is(Items.PAPER)) {
                                    st.shrink(1);
                                    break;
                                }
                            }
                            // 给予传送卷轴
                            ItemStack scroll = TeleportScrollItem.createScroll(wp);
                            if (!player.getInventory().add(scroll)) {
                                player.drop(scroll, false);
                            }
                            player.displayClientMessage(Component.translatable("message.universal_tool.scroll_created", wp.name()), true);
                        } else {
                            player.displayClientMessage(Component.translatable("message.universal_tool.no_paper"), true);
                        }
                        break;
                    }
                }
            }
        });
    }
}
