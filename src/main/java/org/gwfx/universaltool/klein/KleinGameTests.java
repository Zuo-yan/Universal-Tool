package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class KleinGameTests {
    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void aggregateAndComponentRules(GameTestHelper helper) {
        KleinWarehouse warehouse = new KleinWarehouse();
        BigInteger huge = new BigInteger("123456789012345678901234567890");
        helper.assertTrue(warehouse.insert(new ItemStack(Items.DIAMOND), huge).equals(huge), "Large insert");
        KleinWarehouse.Entry entry = warehouse.entries().iterator().next();
        ItemStack extracted = warehouse.extract(entry.id(), 64);
        helper.assertTrue(extracted.getCount() == 64, "Extract is legal stack size");
        helper.assertTrue(entry.amount().equals(huge.subtract(BigInteger.valueOf(64))), "BigInteger conservation");
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named"));
        warehouse.insert(named, BigInteger.ONE);
        helper.assertTrue(warehouse.size() == 2, "Components define distinct types");
        ItemStack first = new ItemStack(Items.DIAMOND_SWORD);
        first.setDamageValue(1);
        ItemStack second = new ItemStack(Items.DIAMOND_SWORD);
        second.setDamageValue(2);
        warehouse.insert(first, BigInteger.ONE);
        warehouse.insert(second, BigInteger.ONE);
        helper.assertTrue(warehouse.size() == 4, "Damaged instances do not merge");
        helper.assertTrue(warehouse.insert(new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()), BigInteger.ONE).signum() == 0,
                "Bottle self nesting rejected");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void menuProtectionAndItemTransfer(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-test-player");
        ItemStack bottle = new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get());
        player.getInventory().setItem(0, bottle);
        player.getInventory().setItem(1, new ItemStack(Items.EMERALD, 64));

        KleinMenu menu = new KleinMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        KleinWarehouse warehouse = KleinStorageSavedData.get(player.server).warehouse(player.getUUID());

        // Slot 0 (hotbar slot 0) is protected against pickup
        Slot hotbar0 = menu.slots.stream().filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 0).findFirst().orElseThrow();
        helper.assertTrue(!hotbar0.mayPickup(player), "Opening bottle slot cannot be picked up");
        menu.clicked(hotbar0.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "Clicking locked bottle slot leaves cursor empty");

        // Quick move slot 1 (emeralds) into warehouse
        Slot emeraldSlot = menu.slots.stream().filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 1).findFirst().orElseThrow();
        menu.quickMoveStack(player, emeraldSlot.index);
        helper.assertTrue(emeraldSlot.getItem().isEmpty(), "Inventory emerald stack transferred into warehouse");
        helper.assertTrue(warehouse.entries().stream().anyMatch(e -> e.template().is(Items.EMERALD) && e.amount().intValue() == 64),
                "Warehouse holds transferred emeralds");

        // Action payload: extract to cursor
        long emeraldId = warehouse.entries().stream().filter(e -> e.template().is(Items.EMERALD)).findFirst().orElseThrow().id();
        menu.handleAction(new KleinActionPayload(emeraldId, KleinActionPayload.EXTRACT, -1, 0));
        helper.assertTrue(menu.getCarried().is(Items.EMERALD) && menu.getCarried().getCount() == 64, "Extracted to cursor");
        helper.assertTrue(warehouse.amount(emeraldId).signum() == 0, "Warehouse decremented on extract");

        // Action payload: insert carried back
        menu.handleAction(new KleinActionPayload(emeraldId, KleinActionPayload.INSERT_CARRIED, -1, 1));
        helper.assertTrue(menu.getCarried().isEmpty(), "Carried stack emptied after insert");
        helper.assertTrue(warehouse.entries().stream().anyMatch(e -> e.template().is(Items.EMERALD) && e.amount().intValue() == 64),
                "Warehouse restored after insert");

        // Action payload: quick deposit matching inventory items
        player.getInventory().setItem(2, new ItemStack(Items.EMERALD, 32));
        player.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 16));
        menu.handleAction(new KleinActionPayload(-1, KleinActionPayload.DEPOSIT_MATCHING, -1, 2));
        helper.assertTrue(player.getInventory().getItem(2).isEmpty(), "Matching emeralds deposited from inventory");
        helper.assertTrue(player.getInventory().getItem(3).getCount() == 16, "Non-matching diamonds kept in inventory");
        helper.assertTrue(warehouse.entries().stream().filter(e -> e.template().is(Items.EMERALD)).findFirst().orElseThrow().amount().intValue() == 96, "Warehouse holds 64 + 32 = 96 emeralds");

        // Client search filtering tests (@mod prefix and sorting)
        menu.applySync(new KleinSyncPayload(java.util.List.of(
                new KleinEntryData(emeraldId, new ItemStack(Items.EMERALD), BigInteger.valueOf(96)),
                new KleinEntryData(2L, new ItemStack(Items.DIAMOND), BigInteger.valueOf(10))), true));
        menu.setView("@minecraft", KleinMenu.SORT_QUANTITY, true);
        helper.assertTrue(menu.filtered().size() == 2, "@minecraft prefix matches all vanilla entries");
        menu.setView("@universal_tool", KleinMenu.SORT_QUANTITY, true);
        helper.assertTrue(menu.filtered().isEmpty(), "@universal_tool matches nothing in this sample");
        menu.setView("", KleinMenu.SORT_NAME, false);
        helper.assertTrue(menu.filtered().size() == 2, "Empty query matches all");

        helper.succeed();
    }

    private static ServerPlayer mockPlayer(MinecraftServer server, ServerLevel level, String name) {
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(server, level, profile,
                net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new net.minecraft.network.Connection(
                net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection,
                player, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) {
            }
        };
        return player;
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void saveRoundTripAndPlayerIsolation(GameTestHelper helper) {
        KleinStorageSavedData data = new KleinStorageSavedData();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        BigInteger huge = new BigInteger("999999999999999999999999999999999999");
        data.warehouse(first).insert(new ItemStack(Items.STONE), huge);
        data.warehouse(second).insert(new ItemStack(Items.DIRT), BigInteger.ONE);
        CompoundTag serialized = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        KleinStorageSavedData loaded = KleinStorageSavedData.load(serialized, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.warehouse(first).entries().iterator().next().amount().equals(huge), "Large quantity round trip");
        helper.assertTrue(loaded.warehouse(second).size() == 1, "Second warehouse independent");
        helper.assertTrue(loaded.warehouse(first).entries().iterator().next().template().is(Items.STONE), "Owner item preserved");
        helper.succeed();
    }

    private KleinGameTests() {}
}
