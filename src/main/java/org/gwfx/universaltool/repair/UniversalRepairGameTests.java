package org.gwfx.universaltool.repair;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.anvil.UniversalRepairAnvilHandler;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModRecipes;
import org.gwfx.universaltool.recipe.PolymerizerRecipe;
import org.gwfx.universaltool.recipe.PolymerizerRecipeInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side checks for the universal anvil repair contract.
 */
@PrefixGameTestTemplate(false)
public final class UniversalRepairGameTests {

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void repairsToFullAndPreservesComponents(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "repair-test"),
                net.minecraft.server.level.ClientInformation.createDefault());

        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.setDamageValue(tool.getMaxDamage() - 1);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("Kept Name"));
        tool.enchant(level.registryAccess().holderOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 3);
        int enchantments = tool.getEnchantments().size();

        AnvilUpdateEvent event = new AnvilUpdateEvent(tool,
                new ItemStack(ModItems.UNIVERSAL_REPAIR_MATERIAL.get()), "Kept Name", 0, player);
        UniversalRepairAnvilHandler.onAnvilUpdate(event);

        helper.assertTrue(!event.getOutput().isEmpty(), "A damaged item and repair material must produce output");
        helper.assertTrue(event.getOutput().getDamageValue() == 0, "Repair must restore full durability");
        helper.assertTrue(event.getOutput().getEnchantments().size() == enchantments,
                "Repair must preserve enchantments");
        helper.assertTrue(event.getOutput().has(DataComponents.CUSTOM_NAME),
                "Repair must preserve a custom name");
        helper.assertTrue(event.getCost() == UniversalRepairAnvilHandler.EXPERIENCE_COST,
                "Repair must cost three levels");
        helper.assertTrue(event.getMaterialCost() == UniversalRepairAnvilHandler.MATERIAL_COST,
                "Repair must consume exactly one material");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void rejectsUndamagedAndUnbreakableItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "repair-edge"),
                net.minecraft.server.level.ClientInformation.createDefault());

        ItemStack undamaged = new ItemStack(Items.DIAMOND_SWORD);
        AnvilUpdateEvent first = new AnvilUpdateEvent(undamaged,
                new ItemStack(ModItems.UNIVERSAL_REPAIR_MATERIAL.get()), "", 0, player);
        UniversalRepairAnvilHandler.onAnvilUpdate(first);
        helper.assertTrue(first.getOutput().isEmpty(), "Undamaged items must not be repairable");

        ItemStack unbreakable = new ItemStack(Items.DIAMOND_SWORD);
        unbreakable.setDamageValue(3);
        unbreakable.set(DataComponents.UNBREAKABLE, new net.minecraft.world.item.component.Unbreakable(true));
        AnvilUpdateEvent second = new AnvilUpdateEvent(unbreakable,
                new ItemStack(ModItems.UNIVERSAL_REPAIR_MATERIAL.get()), "", 0, player);
        UniversalRepairAnvilHandler.onAnvilUpdate(second);
        helper.assertTrue(second.getOutput().isEmpty(), "Unbreakable items must not be repairable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void polymerizerRecipeProducesFourMaterials(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<ItemStack> ingredients = new ArrayList<>(12);
        for (int i = 0; i < 4; i++) ingredients.add(new ItemStack(Items.NETHERITE_SCRAP));
        for (int i = 0; i < 4; i++) ingredients.add(new ItemStack(Items.AMETHYST_SHARD));
        for (int i = 0; i < 4; i++) ingredients.add(new ItemStack(Items.DIAMOND));

        Optional<RecipeHolder<PolymerizerRecipe>> match = level.getRecipeManager().getRecipeFor(
                ModRecipes.POLYMERIZING_TYPE.get(), new PolymerizerRecipeInput(ingredients), level);
        helper.assertTrue(match.isPresent(), "The 4+4+4 polymerizer recipe must match");

        ItemStack output = match.orElseThrow().value().assemble(
                new PolymerizerRecipeInput(ingredients), level.registryAccess());
        helper.assertTrue(output.is(ModItems.UNIVERSAL_REPAIR_MATERIAL.get()),
                "Polymerizer output must be the universal repair material");
        helper.assertTrue(output.getCount() == 4, "Polymerizer output count must be four");
        helper.succeed();
    }


    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void creativeTabContainsRepairMaterial(GameTestHelper helper) {
        net.minecraft.world.item.CreativeModeTab tab = org.gwfx.universaltool.init.ModCreativeTabs.UNIVERSAL_TOOL_TAB.get();
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess()));
        boolean found = false;
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack.is(ModItems.UNIVERSAL_REPAIR_MATERIAL.get())) {
                found = true;
                break;
            }
        }
        helper.assertTrue(found, "UNIVERSAL_TOOL_TAB must contain the universal repair material");
        helper.succeed();
    }


    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void kleinBottleAnvilRepairsWithUniversalMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "klein-anvil-test");
        ServerPlayer player = new ServerPlayer(level.getServer(), level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), connection,
                player, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {}
        };
        player.giveExperienceLevels(30);
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));

        org.gwfx.universaltool.klein.KleinMenu menu = new org.gwfx.universaltool.klein.KleinMenu(
                20, player.getInventory(), net.minecraft.world.InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        menu.setDrawerMode(org.gwfx.universaltool.klein.KleinMenu.DRAWER_ANVIL);

        ItemStack damagedTool = new ItemStack(org.gwfx.universaltool.init.ModItems.NETHERITE_UNIVERSAL_TOOL.get());
        damagedTool.setDamageValue(500);
        menu.slots.get(org.gwfx.universaltool.klein.KleinMenu.ANVIL_LEFT_SLOT).set(damagedTool);
        menu.slots.get(org.gwfx.universaltool.klein.KleinMenu.ANVIL_RIGHT_SLOT).set(
                new ItemStack(org.gwfx.universaltool.init.ModItems.UNIVERSAL_REPAIR_MATERIAL.get()));

        ItemStack result = menu.slots.get(org.gwfx.universaltool.klein.KleinMenu.ANVIL_RESULT_SLOT).getItem();
        helper.assertTrue(!result.isEmpty(), "Klein bottle anvil must produce repaired output with universal repair material");
        helper.assertTrue(result.getDamageValue() == 0, "Klein bottle anvil output must be 100% repaired");

        // Take the item
        menu.clicked(org.gwfx.universaltool.klein.KleinMenu.ANVIL_RESULT_SLOT, 0,
                net.minecraft.world.inventory.ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getDamageValue() == 0, "Taken item on cursor must be repaired");
        helper.assertTrue(player.experienceLevel == 27, "Must consume 3 experience levels from player in survival");
        helper.assertTrue(menu.slots.get(org.gwfx.universaltool.klein.KleinMenu.ANVIL_RIGHT_SLOT).getItem().isEmpty(),
                "Must consume repair material");
        helper.succeed();
    }

    private UniversalRepairGameTests() {
    }
}
