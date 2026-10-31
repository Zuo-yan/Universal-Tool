package org.gwfx.universaltool.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A single-use anvil material that restores the full durability of any damageable item.
 */
public final class UniversalRepairMaterialItem extends Item {

    public UniversalRepairMaterialItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.universal_tool.universal_repair_material.usage")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.universal_tool.universal_repair_material.detail")
                .withStyle(ChatFormatting.GRAY));
    }
}
