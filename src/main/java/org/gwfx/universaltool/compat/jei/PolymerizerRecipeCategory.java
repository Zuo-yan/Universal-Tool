package org.gwfx.universaltool.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
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
    public static final int RADIUS = 44; // 舒展无重叠半径

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

    @Override
    public void draw(PolymerizerRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        // 1. 外环紫水晶/铜导管环 (匹配 PolymerizerScreen)
        for (int py = CENTER_Y - 46; py <= CENTER_Y + 46; py++) {
            for (int px = CENTER_X - 46; px <= CENTER_X + 46; px++) {
                double dist = Math.hypot(px - CENTER_X, py - CENTER_Y);
                if (dist >= 43.0 && dist <= 45.2) {
                    int color = (dist >= 43.5 && dist <= 44.6)
                            ? (((px + py) & 1) == 0 ? 0xFFB87333 : 0xFF9C6FD6)
                            : 0xFF555555;
                    guiGraphics.fill(px, py, px + 1, py + 1, color);
                } else if (dist >= 12.2 && dist <= 14.5) {
                    int color = ((px + py) % 3 == 0) ? 0xFF9C6FD6 : 0xFFB87333;
                    guiGraphics.fill(px, py, px + 1, py + 1, color);
                }
            }
        }

        // 2. 12 条辐射导管连线与动态紫水晶能量脉冲
        int pulsePhase = (int) ((System.currentTimeMillis() / 180L) % 6);
        for (int i = 0; i < 12; i++) {
            double rad = Math.toRadians(i * 30.0 - 90.0);
            int ox = (int) Math.round(CENTER_X + 35.0 * Math.cos(rad));
            int oy = (int) Math.round(CENTER_Y + 35.0 * Math.sin(rad));
            int ix = (int) Math.round(CENTER_X + 14.0 * Math.cos(rad));
            int iy = (int) Math.round(CENTER_Y + 14.0 * Math.sin(rad));
            int coreColor = (i % 2 == 0) ? 0xFF9C6FD6 : 0xFFDA9258;
            drawConduitLine(guiGraphics, ox, oy, ix, iy, coreColor, pulsePhase);
        }

        // 3. 中心产物槽铜/紫水晶角标装饰
        int sx = CENTER_X - 10;
        int sy = CENTER_Y - 10;
        guiGraphics.fill(sx, sy, sx + 2, sy + 2, 0xFFDA9258);
        guiGraphics.fill(sx + 18, sy, sx + 20, sy + 2, 0xFFDA9258);
        guiGraphics.fill(sx, sy + 18, sx + 2, sy + 20, 0xFFDA9258);
        guiGraphics.fill(sx + 18, sy + 18, sx + 20, sy + 20, 0xFFDA9258);
    }

    private static void drawConduitLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int coreColor, int pulsePhase) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) return;
        for (int s = 0; s <= steps; s++) {
            int px = (int) Math.round(x0 + (x1 - x0) * (s / (double) steps));
            int py = (int) Math.round(y0 + (y1 - y0) * (s / (double) steps));
            guiGraphics.fill(px - 1, py, px + 2, py + 1, 0xFF555555);
            guiGraphics.fill(px, py - 1, px + 1, py + 2, 0xFF555555);
        }
        for (int s = 0; s <= steps; s++) {
            int px = (int) Math.round(x0 + (x1 - x0) * (s / (double) steps));
            int py = (int) Math.round(y0 + (y1 - y0) * (s / (double) steps));
            int c = (s % 6 == pulsePhase) ? 0xFFC49BFF : coreColor;
            guiGraphics.fill(px, py, px + 1, py + 1, c);
        }
    }
}
