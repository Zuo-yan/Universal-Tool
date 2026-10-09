package org.gwfx.universaltool.init;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.recipe.PolymerizerRecipe;

public class ModRecipes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, UniversalToolMod.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, UniversalToolMod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<PolymerizerRecipe>> POLYMERIZING_TYPE =
            RECIPE_TYPES.register("polymerizing", () -> RecipeType.simple(ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "polymerizing")));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PolymerizerRecipe>> POLYMERIZING_SERIALIZER =
            RECIPE_SERIALIZERS.register("polymerizing", () -> new RecipeSerializer<PolymerizerRecipe>() {
                @Override
                public MapCodec<PolymerizerRecipe> codec() {
                    return PolymerizerRecipe.MAP_CODEC;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, PolymerizerRecipe> streamCodec() {
                    return PolymerizerRecipe.STREAM_CODEC;
                }
            });
}
