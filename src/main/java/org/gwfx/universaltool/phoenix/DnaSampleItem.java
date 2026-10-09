package org.gwfx.universaltool.phoenix;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;
import java.util.UUID;

public class DnaSampleItem extends Item {

    public DnaSampleItem(Properties properties) {
        super(properties);
    }

    public static UUID getOwnerUUID(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains("OwnerUUID")) {
            return data.copyTag().getUUID("OwnerUUID");
        }
        return null;
    }

    public static String getOwnerName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains("OwnerName")) {
            return data.copyTag().getString("OwnerName");
        }
        return "Unknown";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String name = getOwnerName(stack);
        tooltip.add(Component.translatable("item.universal_tool.dna_sample.owner", name).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.universal_tool.dna_sample.desc").withStyle(ChatFormatting.GRAY));
    }
}
