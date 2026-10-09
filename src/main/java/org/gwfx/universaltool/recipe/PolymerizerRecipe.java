package org.gwfx.universaltool.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.gwfx.universaltool.init.ModRecipes;

import java.util.ArrayList;
import java.util.List;

/**
 * 万能聚合台配方（type: universal_tool:polymerizing）。
 * 12 环槽无序匹配（Shapeless matching），原料 1~12 种。
 */
public class PolymerizerRecipe implements Recipe<PolymerizerRecipeInput> {

    public static final MapCodec<PolymerizerRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(
            inst -> inst.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(PolymerizerRecipe::getGroup),
                    Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").forGetter(r -> r.ingredients),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
                    Codec.INT.optionalFieldOf("duration", 30).forGetter(r -> r.duration)
            ).apply(inst, PolymerizerRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PolymerizerRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PolymerizerRecipe::getGroup,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), r -> r.ingredients,
            ItemStack.STREAM_CODEC, r -> r.result,
            ByteBufCodecs.VAR_INT, r -> r.duration,
            PolymerizerRecipe::new
    );

    private final String group;
    private final List<Ingredient> ingredients;
    private final ItemStack result;
    private final int duration;

    public PolymerizerRecipe(String group, List<Ingredient> ingredients, ItemStack result, int duration) {
        this.group = group;
        this.ingredients = ingredients;
        this.result = result;
        this.duration = duration;
    }

    public List<Ingredient> getIngredientsList() {
        return ingredients;
    }

    public ItemStack getResult() {
        return result;
    }

    public int getDuration() {
        return duration;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public boolean matches(PolymerizerRecipeInput input, Level level) {
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                inputs.add(stack);
            }
        }
        if (inputs.size() != ingredients.size()) {
            return false;
        }

        // 无序匹配回溯
        return matchShapeless(inputs, ingredients, 0, new boolean[inputs.size()]);
    }

    private static boolean matchShapeless(List<ItemStack> inputs, List<Ingredient> ingredients, int ingIndex, boolean[] used) {
        if (ingIndex >= ingredients.size()) {
            return true;
        }
        Ingredient target = ingredients.get(ingIndex);
        for (int i = 0; i < inputs.size(); i++) {
            if (!used[i] && target.test(inputs.get(i))) {
                used[i] = true;
                if (matchShapeless(inputs, ingredients, ingIndex + 1, used)) {
                    return true;
                }
                used[i] = false;
            }
        }
        return false;
    }

    @Override
    public ItemStack assemble(PolymerizerRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.POLYMERIZING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.POLYMERIZING_TYPE.get();
    }
}
