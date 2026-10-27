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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class KleinBottleItem extends Item {
    public KleinBottleItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("item.universal_tool.klein_bottle.desc.hotkey",
                Component.keybind("key.universal_tool.klein.open"))
                .withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            open(serverPlayer, hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    public static void open(ServerPlayer player, int slot) {
        if ((slot < 0 || (slot >= 36 && slot != Inventory.SLOT_OFFHAND))
                || !(player.getInventory().getItem(slot).getItem() instanceof KleinBottleItem)) return;
        player.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("item.universal_tool.klein_bottle"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player opener) {
                KleinMenu menu = new KleinMenu(id, inventory, slot);
                KleinStorageSavedData.get(player.server).watch(player.getUUID(), menu);
                return menu;
            }
        }, buffer -> buffer.writeVarInt(slot));
    }
}
