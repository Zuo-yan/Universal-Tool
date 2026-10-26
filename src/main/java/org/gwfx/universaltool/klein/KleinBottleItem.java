package org.gwfx.universaltool.klein;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class KleinBottleItem extends Item {
    public KleinBottleItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new MenuProvider() {
                @Override public Component getDisplayName() { return Component.translatable("item.universal_tool.klein_bottle"); }
                @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player opener) {
                    KleinMenu menu = new KleinMenu(id, inventory, hand);
                    KleinStorageSavedData.get(serverPlayer.server).watch(serverPlayer.getUUID(), menu);
                    return menu;
                }
            }, buffer -> buffer.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
