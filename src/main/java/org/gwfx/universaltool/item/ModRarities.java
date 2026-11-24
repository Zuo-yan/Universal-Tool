package org.gwfx.universaltool.item;

import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

public final class ModRarities {
    public static final EnumProxy<Rarity> MYTHIC = new EnumProxy<>(
            Rarity.class, -1, "universal_tool:mythic", (UnaryOperator<Style>) style -> style.withColor(ChatFormatting.RED));

    private ModRarities() {}
}
