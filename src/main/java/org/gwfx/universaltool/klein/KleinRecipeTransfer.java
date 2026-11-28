package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Plans complete recipe fills against an immutable view of the available stock. */
public final class KleinRecipeTransfer {
    public record Plan(List<ItemStack> grid, List<Integer> missingIngredients) {
        public boolean complete() { return missingIngredients.isEmpty(); }
    }

    record Stock(ItemStack template, BigInteger amount) {}

    private record Match(int[] types, List<Integer> missingIngredients) {}

    public static int drawer(Recipe<?> recipe) {
        RecipeType<?> type = recipe.getType();
        if (type == RecipeType.CRAFTING && recipe instanceof CraftingRecipe) return KleinMenu.DRAWER_CRAFTING;
        if (recipe instanceof AbstractCookingRecipe && (type == RecipeType.SMELTING || type == RecipeType.BLASTING
                || type == RecipeType.SMOKING || type == RecipeType.CAMPFIRE_COOKING)) return KleinMenu.DRAWER_SMELTING;
        return KleinMenu.DRAWER_NONE;
    }

    @SuppressWarnings("removal")
    static List<Ingredient> layout(Recipe<?> recipe) {
        int drawer = drawer(recipe);
        var ingredients = recipe.getIngredients();
        if (drawer == KleinMenu.DRAWER_SMELTING && ingredients.size() == 1 && !ingredients.getFirst().isEmpty()) {
            return List.of(ingredients.getFirst());
        }
        if (drawer != KleinMenu.DRAWER_CRAFTING || ingredients.size() > 9 || ingredients.stream().allMatch(Ingredient::isEmpty)
                || !recipe.canCraftInDimensions(3, 3)) return List.of();

        List<Ingredient> layout = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        for (int i = 0; i < ingredients.size(); i++) {
            layout.set(i / width * 3 + i % width, ingredients.get(i));
        }
        return layout;
    }

    static void addStock(Map<KleinWarehouse.ItemKey, Stock> stock, ItemStack stack, BigInteger amount) {
        if (stack.isEmpty() || amount.signum() <= 0) return;
        stock.merge(KleinWarehouse.ItemKey.of(stack), new Stock(stack.copyWithCount(1), amount),
                (left, right) -> new Stock(left.template(), left.amount().add(right.amount())));
    }

    static Plan plan(List<Ingredient> layout, Map<KleinWarehouse.ItemKey, Stock> available, boolean maxTransfer) {
        if (layout.isEmpty()) return new Plan(List.of(), List.of());
        List<Stock> stock = new ArrayList<>();
        List<List<Integer>> alternatives = new ArrayList<>();
        for (Ingredient ingredient : layout) {
            if (!ingredient.isEmpty()) alternatives.add(new ArrayList<>());
        }
        for (Stock candidate : available.values()) {
            boolean matches = false;
            int input = 0;
            for (Ingredient ingredient : layout) {
                if (ingredient.isEmpty()) continue;
                if (ingredient.test(candidate.template())) {
                    alternatives.get(input).add(stock.size());
                    matches = true;
                }
                input++;
            }
            if (matches) stock.add(candidate);
        }

        int amount = 1;
        Match match = assign(stock, alternatives, amount);
        if (!match.missingIngredients().isEmpty()) return new Plan(List.of(), match.missingIngredients());
        if (maxTransfer) {
            int upper = stock.stream().mapToInt(entry -> entry.template().getMaxStackSize()).max().orElse(1);
            while (amount < upper) {
                int attempt = (amount + upper + 1) / 2;
                Match tested = assign(stock, alternatives, attempt);
                if (tested.missingIngredients().isEmpty()) {
                    amount = attempt;
                    match = tested;
                } else {
                    upper = attempt - 1;
                }
            }
        }

        List<ItemStack> grid = new ArrayList<>(layout.size());
        int input = 0;
        for (Ingredient ingredient : layout) {
            grid.add(ingredient.isEmpty() ? ItemStack.EMPTY
                    : stock.get(match.types()[input++]).template().copyWithCount(amount));
        }
        return new Plan(List.copyOf(grid), List.of());
    }

    private static Match assign(List<Stock> stock, List<List<Integer>> alternatives, int sets) {
        BigInteger perSlot = BigInteger.valueOf(sets);
        BigInteger slots = BigInteger.valueOf(alternatives.size());
        int[] capacities = new int[stock.size()];
        List<List<Integer>> assigned = new ArrayList<>(stock.size());
        for (int i = 0; i < stock.size(); i++) {
            Stock entry = stock.get(i);
            if (entry.template().getMaxStackSize() >= sets) {
                capacities[i] = entry.amount().divide(perSlot).min(slots).intValue();
            }
            assigned.add(new ArrayList<>());
        }
        List<Integer> missing = new ArrayList<>();
        for (int input = 0; input < alternatives.size(); input++) {
            if (!assignInput(input, alternatives, capacities, assigned, new boolean[stock.size()])) missing.add(input);
        }
        int[] types = new int[alternatives.size()];
        for (int i = 0; i < assigned.size(); i++) {
            for (int input : assigned.get(i)) types[input] = i;
        }
        return new Match(types, List.copyOf(missing));
    }

    // An occupied variant can be reassigned when another ingredient has fewer valid choices.
    private static boolean assignInput(int input, List<List<Integer>> alternatives, int[] capacities,
                                       List<List<Integer>> assigned, boolean[] visited) {
        for (int type : alternatives.get(input)) {
            if (visited[type] || capacities[type] == 0) continue;
            visited[type] = true;
            List<Integer> owners = assigned.get(type);
            if (owners.size() < capacities[type]) {
                owners.add(input);
                return true;
            }
            for (int i = 0; i < owners.size(); i++) {
                if (assignInput(owners.get(i), alternatives, capacities, assigned, visited)) {
                    owners.set(i, input);
                    return true;
                }
            }
        }
        return false;
    }

    private KleinRecipeTransfer() {}
}
