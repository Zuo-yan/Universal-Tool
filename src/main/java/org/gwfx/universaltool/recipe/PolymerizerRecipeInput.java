package org.gwfx.universaltool.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

public class PolymerizerRecipeInput implements RecipeInput {
    private final List<ItemStack> items;

    public PolymerizerRecipeInput(List<ItemStack> items) {
        this.items = items;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return items.size();
    }
}
