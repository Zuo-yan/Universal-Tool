package org.gwfx.universaltool.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 虚空精华 (Void Essence)
 * 凝聚纯粹虚空能量的宝物，由 12 枚虚空残片在万能聚合台提炼而成。
 */
public final class VoidEssenceItem extends Item {

    public VoidEssenceItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.universal_tool.void_essence.desc")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.universal_tool.void_essence.usage")
                .withStyle(ChatFormatting.GRAY));
    }
}
