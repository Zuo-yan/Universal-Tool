package org.gwfx.universaltool.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModRecipes;
import org.gwfx.universaltool.recipe.PolymerizerRecipe;

import java.util.List;

@JeiPlugin
public class UniversalToolJeiPlugin implements IModPlugin {
    public static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "jei_plugin");

    public static final RecipeType<PolymerizerRecipe> POLYMERIZING_JEI_TYPE =
            RecipeType.create(UniversalToolMod.MODID, "polymerizing", PolymerizerRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new PolymerizerRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level != null) {
            List<PolymerizerRecipe> recipes = Minecraft.getInstance().level.getRecipeManager()
                    .getAllRecipesFor(ModRecipes.POLYMERIZING_TYPE.get())
                    .stream()
                    .map(RecipeHolder::value)
                    .toList();
            registration.addRecipes(POLYMERIZING_JEI_TYPE, recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.UNIVERSAL_POLYMERIZER.get()), POLYMERIZING_JEI_TYPE);
    }
}
