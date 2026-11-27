package org.gwfx.universaltool.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.client.KleinScreen;
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
        registerIngredientInfo(registration);
    }

    private static void registerIngredientInfo(IRecipeRegistration registration) {
        addInfo(registration, ModItems.UNIVERSAL_POLYMERIZER.get(), "jei.universal_tool.info.universal_polymerizer");
        addInfo(registration, ModItems.OPERATION_PHOENIX_POD.get(), "jei.universal_tool.info.operation_phoenix_pod");
        addInfo(registration, ModItems.PERSONAL_SPACE_GATE.get(), "jei.universal_tool.info.personal_space_gate");
        addInfo(registration, ModItems.KLEIN_BOTTLE.get(), "jei.universal_tool.info.klein_bottle");
        addInfo(registration, ModItems.UNIVERSAL_LOTTERY_MACHINE.get(), "jei.universal_tool.info.universal_lottery_machine");
        addInfo(registration, ModItems.UNIVERSAL_REPAIR_MATERIAL.get(), "jei.universal_tool.info.universal_repair_material");
        addInfo(registration, ModItems.TELEPORT_CODEX.get(), "jei.universal_tool.info.teleport_codex");
    }

    private static void addInfo(IRecipeRegistration registration, net.minecraft.world.level.ItemLike item, String translationKey) {
        registration.addIngredientInfo(
                new ItemStack(item),
                VanillaTypes.ITEM_STACK,
                Component.translatable(translationKey)
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.UNIVERSAL_POLYMERIZER.get()), POLYMERIZING_JEI_TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new KleinRecipeTransferHandler(registration.getTransferHelper()),
                mezz.jei.api.constants.RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(KleinScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(KleinScreen screen) {
                return screen.getJeiExtraAreas();
            }
        });
    }
}
