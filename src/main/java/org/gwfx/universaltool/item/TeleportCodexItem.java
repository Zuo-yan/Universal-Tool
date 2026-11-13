package org.gwfx.universaltool.item;

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
import java.util.function.IntSupplier;

public class TeleportCodexItem extends Item {

    private static Runnable openScreenHook = () -> {};
    private static IntSupplier waypointCountSupplier = () -> 0;
    private static IntSupplier maxCapacitySupplier = () -> WaypointData.DEFAULT_CAPACITY;

    public TeleportCodexItem(Properties properties) {
        super(properties);
    }

    /**
     * Registered by client setup so common item code never links client-only classes on a dedicated server.
     */
    public static void registerClientHooks(Runnable openScreen, IntSupplier countSupplier, IntSupplier maxSupplier) {
        openScreenHook = openScreen != null ? openScreen : () -> {};
        waypointCountSupplier = countSupplier != null ? countSupplier : () -> 0;
        maxCapacitySupplier = maxSupplier != null ? maxSupplier : () -> WaypointData.DEFAULT_CAPACITY;
    }

    public static boolean hasCodex(Player player) {
        if (player == null) {
            return false;
        }
        if (player.getOffhandItem().is(ModItems.TELEPORT_CODEX.get())) {
            return true;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.TELEPORT_CODEX.get())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack offhand = player.getOffhandItem();

        // 检查是否副手拿空间书页进行装订升级
        if (hand == InteractionHand.MAIN_HAND && offhand.is(ModItems.SPATIAL_PAGE.get())) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                WaypointData data = serverPlayer.getData(WaypointData.ATTACHMENT.get());
                if (data.canExpand()) {
                    data.expandCapacity();
                    offhand.shrink(1);
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

        // 常态单手使用：翻开手记界面
        if (level.isClientSide) {
            openScreenHook.run();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (context.level() != null && context.level().isClientSide()) {
            int count = waypointCountSupplier.getAsInt();
            int max = maxCapacitySupplier.getAsInt();
            tooltip.add(Component.translatable("tooltip.universal_tool.teleport_codex.count", count, max));
        }
        tooltip.add(Component.translatable("tooltip.universal_tool.teleport_codex.usage"));
    }
}
