package org.gwfx.universaltool.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.network.SyncWaypointsPayload;
import org.gwfx.universaltool.waypoint.WaypointData;

import java.util.List;

public class SpatialPageItem extends Item {

    public SpatialPageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack otherStack = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);

        // 如果另一只手持有传送书，触发装订
        if (otherStack.is(ModItems.TELEPORT_CODEX.get())) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                WaypointData data = serverPlayer.getData(WaypointData.ATTACHMENT.get());
                if (data.canExpand()) {
                    data.expandCapacity();
                    stack.shrink(1);
                    serverPlayer.serverLevel().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                            SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
                    PacketDistributor.sendToPlayer(serverPlayer, new SyncWaypointsPayload(data.getWaypoints(), data.getMaxCapacity()));
                    serverPlayer.displayClientMessage(Component.translatable("message.universal_tool.expanded", data.getMaxCapacity(), WaypointData.ULTIMATE_CAPACITY), true);
                    return InteractionResultHolder.sidedSuccess(stack, false);
                } else {
                    serverPlayer.displayClientMessage(Component.translatable("message.universal_tool.max_capacity", WaypointData.ULTIMATE_CAPACITY), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.universal_tool.spatial_page.usage").withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.universal_tool.spatial_page.tip").withStyle(ChatFormatting.GRAY));
    }
}
