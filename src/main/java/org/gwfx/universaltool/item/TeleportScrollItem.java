package org.gwfx.universaltool.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.network.SyncWaypointsPayload;
import org.gwfx.universaltool.waypoint.TeleportManager;
import org.gwfx.universaltool.waypoint.Waypoint;
import org.gwfx.universaltool.waypoint.WaypointData;

import java.util.List;

public class TeleportScrollItem extends Item {

    public TeleportScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createScroll(Waypoint waypoint) {
        ItemStack stack = new ItemStack(ModItems.TELEPORT_SCROLL.get());
        ModDataComponents.setTeleportTarget(stack, waypoint);
        return stack;
    }

    public static Waypoint readScroll(ItemStack stack) {
        return ModDataComponents.getTeleportTarget(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Waypoint wp = readScroll(stack);

        if (wp == null) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                // 潜行右键：收录进自己的传送书
                WaypointData data = serverPlayer.getData(WaypointData.ATTACHMENT.get());
                if (data.addWaypoint(wp)) {
                    PacketDistributor.sendToPlayer(serverPlayer, new SyncWaypointsPayload(data.getWaypoints(), data.getMaxCapacity()));
                    serverPlayer.displayClientMessage(Component.translatable("message.universal_tool.scroll_recorded", wp.name()), true);
                    stack.shrink(1);
                    return InteractionResultHolder.sidedSuccess(stack, false);
                } else {
                    serverPlayer.displayClientMessage(Component.translatable("message.universal_tool.codex_full"), true);
                }
            } else {
                // 普通右键：瞬移传送
                if (!TeleportManager.startTeleport(serverPlayer, wp, true)) {
                    return InteractionResultHolder.fail(stack);
                }
                stack.shrink(1);
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Waypoint wp = readScroll(stack);
        if (wp != null) {
            tooltip.add(Component.translatable("tooltip.universal_tool.scroll.dest", wp.name()).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.universal_tool.scroll.pos", String.format("%.0f, %.0f, %.0f", wp.x(), wp.y(), wp.z())).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.universal_tool.scroll.use_tip").withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.universal_tool.scroll.record_tip").withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("tooltip.universal_tool.scroll.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
