package org.gwfx.universaltool.compat.jei;

import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.gwfx.universaltool.init.ModMenuTypes;
import org.gwfx.universaltool.klein.KleinMenu;
import org.gwfx.universaltool.klein.KleinRecipeTransfer;

/** Uses the same transfer planner for JEI and EMI's JEI bridge. */
public final class KleinRecipeTransferHandler<R extends Recipe<?>>
        implements IRecipeTransferHandler<KleinMenu, RecipeHolder<R>> {
    private final IRecipeTransferHandlerHelper helper;
    private final RecipeType<RecipeHolder<R>> recipeType;

    public KleinRecipeTransferHandler(IRecipeTransferHandlerHelper helper, RecipeType<RecipeHolder<R>> recipeType) {
        this.helper = helper;
        this.recipeType = recipeType;
    }

    @Override public Class<? extends KleinMenu> getContainerClass() { return KleinMenu.class; }

    @Override public Optional<net.minecraft.world.inventory.MenuType<KleinMenu>> getMenuType() {
        @SuppressWarnings("unchecked")
        net.minecraft.world.inventory.MenuType<KleinMenu> type =
                (net.minecraft.world.inventory.MenuType<KleinMenu>) ModMenuTypes.KLEIN_MENU.get();
        return Optional.of(type);
    }

    @Override public RecipeType<RecipeHolder<R>> getRecipeType() { return recipeType; }

    @Override
    @SuppressWarnings("removal") // EMI 1.1.24 calls this JEI overload directly.
    public IRecipeTransferError transferRecipe(KleinMenu menu, RecipeHolder<R> holder,
                                               IRecipeSlotsView slots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        Recipe<?> recipe = player.level().getRecipeManager().byKey(holder.id()).map(RecipeHolder::value).orElse(null);
        if (recipe == null || KleinRecipeTransfer.drawer(recipe) == KleinMenu.DRAWER_NONE) {
            return helper.createUserErrorWithTooltip(Component.translatable("gui.universal_tool.klein.recipe_unsupported"));
        }
        var plan = menu.planRecipe(recipe, doTransfer && maxTransfer);
        if (!plan.complete()) {
            List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT).stream()
                    .filter(slot -> !slot.isEmpty()).toList();
            List<IRecipeSlotView> missing = plan.missingIngredients().stream()
                    .filter(i -> i < inputs.size()).map(inputs::get).toList();
            Component message = Component.translatable("jei.tooltip.error.recipe.transfer.missing");
            return missing.isEmpty() ? helper.createUserErrorWithTooltip(message)
                    : helper.createUserErrorForMissingSlots(message, missing);
        }
        if (plan.grid().isEmpty()) {
            String key = recipe.canCraftInDimensions(3, 3) ? "recipe_unsupported" : "jei_too_large";
            return helper.createUserErrorWithTooltip(Component.translatable("gui.universal_tool.klein." + key));
        }

        if (doTransfer) {
            menu.submitRecipeFill(holder.id(), KleinRecipeTransfer.drawer(recipe), maxTransfer);
        }
        return null;
    }
}
