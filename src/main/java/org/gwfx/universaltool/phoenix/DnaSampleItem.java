package org.gwfx.universaltool.phoenix;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.gwfx.universaltool.init.ModDataComponents;

import java.util.List;
import java.util.UUID;

public class DnaSampleItem extends Item {

    public DnaSampleItem(Properties properties) {
        super(properties);
    }

    public static UUID getOwnerUUID(ItemStack stack) {
        ModDataComponents.DnaOwnerData data = ModDataComponents.getDnaOwner(stack);
        return data != null ? data.ownerUuid() : null;
    }

    public static String getOwnerName(ItemStack stack) {
        ModDataComponents.DnaOwnerData data = ModDataComponents.getDnaOwner(stack);
        return data != null ? data.ownerName() : "Unknown";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String name = getOwnerName(stack);
        tooltip.add(Component.translatable("item.universal_tool.dna_sample.owner", name).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.universal_tool.dna_sample.desc").withStyle(ChatFormatting.GRAY));
    }
}
