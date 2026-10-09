package org.gwfx.universaltool.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.recipe.PolymerizerRecipe;

import java.util.List;

public class PolymerizerRecipeCategory implements IRecipeCategory<PolymerizerRecipe> {

    public static final int WIDTH = 150;
    public static final int HEIGHT = 124;
    public static final int CENTER_X = 75;
    public static final int CENTER_Y = 62;
    public static final int RADIUS = 46; // 舒展无重叠半径

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotDrawable;

    public PolymerizerRecipeCategory(IGuiHelper guiHelper) {
        this.background = guiHelper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.UNIVERSAL_POLYMERIZER.get()));
        this.slotDrawable = guiHelper.getSlotDrawable();
    }

    @Override
    public RecipeType<PolymerizerRecipe> getRecipeType() {
        return UniversalToolJeiPlugin.POLYMERIZING_JEI_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.universal_tool.universal_polymerizer");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PolymerizerRecipe recipe, IFocusGroup focuses) {
        List<Ingredient> ingredients = recipe.getIngredientsList();

        // 1. 舒展 12 环时钟方位槽位 (R = 46，完全不拥挤)
        for (int i = 0; i < 12; i++) {
            double angleDeg = i * 30.0 - 90.0;
            double rad = Math.toRadians(angleDeg);
            int slotX = (int) Math.round(CENTER_X + RADIUS * Math.cos(rad)) - 9;
            int slotY = (int) Math.round(CENTER_Y + RADIUS * Math.sin(rad)) - 9;

            var slotBuilder = builder.addSlot(RecipeIngredientRole.INPUT, slotX, slotY)
                    .setBackground(this.slotDrawable, -1, -1);

            if (i < ingredients.size()) {
                slotBuilder.addIngredients(ingredients.get(i));
            }
        }

        // 2. 中心产物槽
        builder.addSlot(RecipeIngredientRole.OUTPUT, CENTER_X - 9, CENTER_Y - 9)
                .setBackground(this.slotDrawable, -1, -1)
                .addItemStack(recipe.getResult());
    }
}
