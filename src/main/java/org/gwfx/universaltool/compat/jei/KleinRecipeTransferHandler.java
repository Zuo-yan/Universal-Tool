package org.gwfx.universaltool.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.gwfx.universaltool.init.ModMenuTypes;
import org.gwfx.universaltool.klein.KleinCraftFillPayload;
import org.gwfx.universaltool.klein.KleinMenu;

/** Fills the Klein crafting drawer from the infinite warehouse when JEI's + button is pressed. */
public final class KleinRecipeTransferHandler
        implements IRecipeTransferHandler<KleinMenu, RecipeHolder<CraftingRecipe>> {
    private final IRecipeTransferHandlerHelper helper;

    public KleinRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override public Class<? extends KleinMenu> getContainerClass() { return KleinMenu.class; }

    @Override public Optional<net.minecraft.world.inventory.MenuType<KleinMenu>> getMenuType() {
        @SuppressWarnings("unchecked")
        net.minecraft.world.inventory.MenuType<KleinMenu> type =
                (net.minecraft.world.inventory.MenuType<KleinMenu>) ModMenuTypes.KLEIN_MENU.get();
        return Optional.of(type);
    }

    @Override public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() { return RecipeTypes.CRAFTING; }

    @Override
    public IRecipeTransferError transferRecipe(KleinMenu menu, RecipeHolder<CraftingRecipe> holder,
                                               IRecipeSlotsView slots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        CraftingRecipe recipe = holder.value();
        List<ItemStack> grid = new ArrayList<>(java.util.Collections.nCopies(KleinCraftFillPayload.GRID_SIZE, ItemStack.EMPTY));
        List<Ingredient> ingredients = recipe.getIngredients();
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
        if (recipe instanceof ShapedRecipe && (width > 3 || height > 3)) {
            return helper.createUserErrorWithTooltip(
                    net.minecraft.network.chat.Component.translatable("gui.universal_tool.klein.jei_too_large"));
        }
        int index = 0;
        for (int row = 0; row < height && index < ingredients.size(); row++) {
            for (int col = 0; col < width && index < ingredients.size(); col++, index++) {
                Ingredient ingredient = ingredients.get(index);
                if (ingredient.isEmpty()) continue;
                ItemStack picked = menu.pickIngredientStack(ingredient);
                if (picked.isEmpty()) continue;
                grid.set(col + row * 3, picked);
            }
        }
        if (doTransfer) {
            menu.setDrawerMode(KleinMenu.DRAWER_CRAFTING);
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new org.gwfx.universaltool.klein.KleinDrawerPayload(KleinMenu.DRAWER_CRAFTING));
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new KleinCraftFillPayload(grid, menu.nextSequence(), maxTransfer));
        }
        return null;
    }
}
