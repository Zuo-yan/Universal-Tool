package org.gwfx.universaltool.anvil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;

/**
 * Adds a full-durability anvil repair path without replacing vanilla repair or renaming behavior.
 */
@EventBusSubscriber(modid = UniversalToolMod.MODID)
public final class UniversalRepairAnvilHandler {

    public static final int EXPERIENCE_COST = 3;
    public static final int MATERIAL_COST = 1;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (!right.is(ModItems.UNIVERSAL_REPAIR_MATERIAL.get())
                || !left.isDamageableItem()
                || left.getDamageValue() <= 0) {
            return;
        }

        ItemStack output = left.copy();
        output.setDamageValue(0);

        if (event.getName() != null) {
            String name = event.getName();
            if (name.isBlank()) {
                output.remove(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            } else if (!name.equals(left.getHoverName().getString())) {
                output.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                        Component.literal(name).withStyle(ChatFormatting.ITALIC));
            }
        }

        event.setOutput(output);
        event.setCost(EXPERIENCE_COST);
        event.setMaterialCost(MATERIAL_COST);
    }

    private UniversalRepairAnvilHandler() {
    }
}
