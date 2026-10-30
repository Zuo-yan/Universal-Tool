package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
    public static void fullWarehouseSyncResetsThenContinues(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-sync-player");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinMenu menu = new KleinMenu(9, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        var entries = new java.util.ArrayList<KleinEntryData>();
        for (int i = 0; i < 65; i++) {
            entries.add(new KleinEntryData(i + 1L, new ItemStack(Items.STONE), BigInteger.ONE));
        }
        menu.applySync(new KleinSyncPayload(entries.subList(0, 64), true, false));
        helper.assertTrue(menu.filtered().isEmpty(), "Partial snapshot is not published");
        menu.applySync(new KleinSyncPayload(entries.subList(64, 65), false));
        helper.assertTrue(menu.filtered().size() == 65,
                "Reset page followed by continuation preserves all 65 synced entries");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void pagedSyncPreservesDeepScroll(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-deep-scroll");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinMenu menu = new KleinMenu(12, player.getInventory(), InteractionHand.MAIN_HAND);
        var entries = new java.util.ArrayList<KleinEntryData>();
        for (int i = 0; i < 513; i++) {
            ItemStack stack = new ItemStack(Items.STONE);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Type " + i));
            entries.add(new KleinEntryData(i + 1L, stack, BigInteger.ONE));
        }
        menu.applySync(new KleinSyncPayload(entries, true));
        helper.assertTrue(menu.maxScroll() == 52, "All 57 rows are reachable");
        menu.scroll(52);
        for (int refresh = 0; refresh < 3; refresh++) {
            for (int start = 0; start < entries.size(); start += 64) {
                int end = Math.min(entries.size(), start + 64);
                var page = new KleinSyncPayload(entries.subList(start, end), start == 0, end == entries.size());
                var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(
                        io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
                try {
                    KleinSyncPayload.STREAM_CODEC.encode(buffer, page);
                    menu.applySync(KleinSyncPayload.STREAM_CODEC.decode(buffer));
                } finally {
                    buffer.release();
                }
                helper.assertTrue(menu.filtered().size() == 513, "No partial list is exposed between packets");
                helper.assertTrue(menu.scroll() == 52, "Repeated paged sync must not snap back from bottom");
            }
        }
        menu.setView("Type", KleinMenu.SORT_NAME, false);
        menu.scroll(40);
        menu.applySync(new KleinSyncPayload(entries.subList(0, 64), true, false));
        helper.assertTrue(menu.scroll() == 40, "Filtering during sync uses the complete visible snapshot");
        menu.applySync(new KleinSyncPayload(java.util.List.of(), false, true));
        helper.assertTrue(menu.filtered().size() == 64 && menu.scroll() == 3,
                "Actual removal clamps scroll only when the smaller snapshot completes");
        menu.applySync(new KleinSyncPayload(java.util.List.of(), true));
        helper.assertTrue(menu.filtered().isEmpty() && menu.scroll() == 0, "Empty warehouse resets scroll");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void forbiddenItemsShareStorageLockIndicator(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-lock-badges");
        ItemStack bottle = new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get());
        player.getInventory().setItem(0, bottle);
        player.getInventory().setItem(1, bottle.copy());
        player.getInventory().setItem(9, bottle.copy());
        player.getInventory().setItem(10, new ItemStack(Items.STONE));
        KleinMenu menu = new KleinMenu(13, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        KleinWarehouse warehouse = KleinStorageSavedData.get(player.server).warehouse(player.getUUID());
        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory()) continue;
            boolean isBottle = slot.hasItem() && slot.getItem().getItem() instanceof KleinBottleItem;
            helper.assertTrue(menu.isStorageLocked(slot) == isBottle, "Every bottle and only bottles have storage locks");
            if (isBottle) menu.quickMoveStack(player, slot.index);
        }
        helper.assertTrue(!warehouse.containsBottle(), "Shift-click never stores another bottle");
        menu.setCarried(bottle.copy());
        menu.handleAction(new KleinActionPayload(-1, KleinActionPayload.INSERT_CARRIED, -1, 0));
        helper.assertTrue(menu.getCarried().getItem() instanceof KleinBottleItem, "Rejected bottle stays on cursor");
        menu.setCarried(ItemStack.EMPTY);
        Slot otherBottle = menu.slots.get(28);
        helper.assertTrue(otherBottle.mayPickup(player), "Storage lock does not prevent moving other bottles in inventory");
        menu.clicked(otherBottle.index, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getItem() instanceof KleinBottleItem, "Other bottle remains movable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void warehouseScrollAndMouseExtraction(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-scroll-player");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinMenu menu = new KleinMenu(6, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        KleinWarehouse warehouse = KleinStorageSavedData.get(player.server).warehouse(player.getUUID());
        warehouse.insert(new ItemStack(Items.STONE), BigInteger.valueOf(64));
        for (var item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (warehouse.size() >= 54) break;
            if (item == Items.STONE || item.getDefaultInstance().getMaxStackSize() <= 1) continue;
            warehouse.insert(item.getDefaultInstance(), BigInteger.ONE);
        }
        helper.assertTrue(warehouse.size() == 54, "Distinct item types create six warehouse rows");
        menu.setView("", KleinMenu.SORT_QUANTITY, true);
        var entry = warehouse.entries().stream()
                .filter(candidate -> candidate.template().is(Items.STONE))
                .findFirst().orElseThrow();
        menu.handleAction(new KleinActionPayload(entry.id(), KleinActionPayload.EXTRACT_HALF, -1, 0));
        helper.assertTrue(menu.getCarried().getCount() == 32,
                "Middle click extracts half a stack");
        menu.setCarried(ItemStack.EMPTY);

        menu.handleAction(new KleinActionPayload(entry.id(), KleinActionPayload.EXTRACT_CURSOR_ONE, -1, 1));
        helper.assertTrue(menu.getCarried().getCount() == 1, "Right click extracts one item");
        helper.succeed();
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

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void jeiFillProducesCraftableResult(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-jei-player");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinMenu menu = new KleinMenu(3, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        KleinWarehouse warehouse = KleinStorageSavedData.get(player.server).warehouse(player.getUUID());
        warehouse.insert(new ItemStack(Items.OAK_LOG), BigInteger.valueOf(2));
        menu.setDrawerMode(KleinMenu.DRAWER_CRAFTING);
        ItemStack staged = new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get());
        menu.slots.get(KleinMenu.CRAFT_GRID_START + 8).set(staged);

        var grid = new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(Items.OAK_LOG));
        menu.handleCraftFill(new KleinCraftFillPayload(grid, 0, false));
        helper.assertTrue(menu.slots.get(KleinMenu.CRAFT_RESULT_SLOT).getItem().is(Items.OAK_PLANKS),
                "JEI transfer calculates the resulting planks");
        helper.assertTrue(player.getInventory().contains(staged), "Existing unstoreable ingredient is preserved");
        menu.clicked(KleinMenu.CRAFT_RESULT_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.OAK_PLANKS) && menu.getCarried().getCount() == 4,
                "Crafting result can be taken after JEI transfer");
        helper.assertTrue(menu.slots.get(KleinMenu.CRAFT_GRID_START).getItem().isEmpty(),
                "Taking the result consumes the transferred log");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void jeiShiftFillUsesCompleteSets(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-jei-bulk");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        player.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 2));
        KleinMenu menu = new KleinMenu(4, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        KleinWarehouse warehouse = KleinStorageSavedData.get(player.server).warehouse(player.getUUID());
        warehouse.insert(new ItemStack(Items.OAK_PLANKS), BigInteger.valueOf(130));
        menu.setDrawerMode(KleinMenu.DRAWER_CRAFTING);
        var grid = new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < 3; i++) grid.set(i, new ItemStack(Items.OAK_PLANKS));
        menu.handleCraftFill(new KleinCraftFillPayload(grid, 0, true));
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(menu.slots.get(KleinMenu.CRAFT_GRID_START + i).getItem().getCount() == 44,
                    "Shift transfer distributes complete sets across matching ingredients");
        }
        helper.assertTrue(menu.slots.get(KleinMenu.CRAFT_RESULT_SLOT).getItem().is(Items.OAK_SLAB),
                "Shift transfer still produces a valid recipe result");
        helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "Shift transfer uses inventory after warehouse");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void jeiShiftFillNeverConsumesIncompleteIngredients(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-jei-incomplete");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        player.getInventory().setItem(1, new ItemStack(Items.ENDER_PEARL, 1));
        var saved = KleinStorageSavedData.get(player.server).warehouse(player.getUUID());
        saved.insert(new ItemStack(Items.ENDER_PEARL), BigInteger.valueOf(40));
        KleinMenu menu = new KleinMenu(7, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        menu.setDrawerMode(KleinMenu.DRAWER_CRAFTING);
        ItemStack before = player.getInventory().getItem(1).copy();
        var grid = new java.util.ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        for (int i = 0; i < 3; i++) grid.set(i, new ItemStack(Items.ENDER_PEARL));
        menu.handleCraftFill(new KleinCraftFillPayload(grid, 0, true));
        helper.assertTrue(player.getInventory().getItem(1).getCount() == before.getCount(),
                "Incomplete shift recipe leaves all player ingredients untouched");
        helper.assertTrue(menu.slots.get(KleinMenu.CRAFT_RESULT_SLOT).getItem().isEmpty(),
                "Incomplete shift recipe never produces a misleading result");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void inventoryBottleSelection(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-key");
        ItemStack bottle = new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get());
        helper.assertTrue(KleinOpenPayload.findBottle(player.getInventory()) == -1, "No bottle means no terminal");
        player.getInventory().setItem(17, bottle);
        helper.assertTrue(KleinOpenPayload.findBottle(player.getInventory()) == 17, "Bottle can be in the main inventory");
        KleinMenu menu = new KleinMenu(5, player.getInventory(), 17);
        helper.assertTrue(menu.stillValid(player), "Inventory bottle opens a valid menu");
        Slot protectedSlot = menu.slots.stream().filter(s -> s.container == player.getInventory()
                && s.getContainerSlot() == 17).findFirst().orElseThrow();
        helper.assertTrue(!protectedSlot.mayPickup(player), "Inventory bottle remains locked");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void drawerCraftingSmeltingAndAnvil(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-drawer-player");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinMenu menu = new KleinMenu(2, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        KleinWarehouse warehouse = KleinStorageSavedData.get(player.server).warehouse(player.getUUID());
        warehouse.insert(new ItemStack(Items.OAK_PLANKS), BigInteger.valueOf(8));

        menu.setDrawerMode(KleinMenu.DRAWER_CRAFTING);
        for (int cell : new int[]{0, 1, 3, 4}) {
            menu.slots.get(KleinMenu.CRAFT_GRID_START + cell).set(new ItemStack(Items.OAK_PLANKS));
        }
        ItemStack crafted = menu.slots.get(KleinMenu.CRAFT_RESULT_SLOT).getItem();
        helper.assertTrue(crafted.is(Items.CRAFTING_TABLE), "2x2 planks craft a crafting table in the drawer");

        menu.setDrawerMode(KleinMenu.DRAWER_SMELTING);
        menu.slots.get(KleinMenu.SMELT_INPUT_SLOT).set(new ItemStack(Items.OAK_LOG, 2));
        ItemStack smelted = menu.slots.get(KleinMenu.SMELT_OUTPUT_SLOT).getItem();
        helper.assertTrue(smelted.is(Items.CHARCOAL) && smelted.getCount() == 2,
                "Fuel-free smelting converts both logs instantly");
        helper.assertTrue(menu.slots.get(KleinMenu.SMELT_INPUT_SLOT).getItem().isEmpty(),
                "Smelting input is consumed");

        menu.setDrawerMode(KleinMenu.DRAWER_ANVIL);
        ItemStack left = new ItemStack(Items.DIAMOND_SWORD);
        left.setDamageValue(left.getMaxDamage() - 10);
        ItemStack right = new ItemStack(Items.DIAMOND_SWORD);
        right.setDamageValue(right.getMaxDamage() - 10);
        menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).set(left);
        menu.slots.get(KleinMenu.ANVIL_RIGHT_SLOT).set(right);
        ItemStack repaired = menu.slots.get(KleinMenu.ANVIL_RESULT_SLOT).getItem();
        helper.assertTrue(repaired.is(Items.DIAMOND_SWORD)
                        && repaired.getDamageValue() < left.getDamageValue(),
                "Anvil drawer combines two damaged swords into a more durable one");
        menu.clicked(KleinMenu.ANVIL_RESULT_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(player.containerMenu.getCarried().is(Items.DIAMOND_SWORD), "Anvil result can be taken");
        helper.assertTrue(menu.slots.get(KleinMenu.ANVIL_RIGHT_SLOT).getItem().isEmpty(),
                "Anvil consumes the secondary sword");
        player.containerMenu.setCarried(ItemStack.EMPTY);

        // Test Anvil rename
        menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
        menu.setAnvilItemName("Excalibur");
        ItemStack renamed = menu.slots.get(KleinMenu.ANVIL_RESULT_SLOT).getItem();
        helper.assertTrue("Excalibur".equals(renamed.getHoverName().getString()),
                "Anvil renames sword to Excalibur");
        menu.clicked(KleinMenu.ANVIL_RESULT_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).getItem().isEmpty(),
                "Renaming consumes the input item");
        player.containerMenu.setCarried(ItemStack.EMPTY);

        menu.setDrawerMode(KleinMenu.DRAWER_NONE);
        helper.assertTrue(menu.slots.get(KleinMenu.SMELT_OUTPUT_SLOT).getItem().isEmpty(),
                "Switching away returns pending drawer contents to the warehouse");
        helper.assertTrue(warehouse.entries().stream().anyMatch(e -> e.template().is(Items.CHARCOAL)),
                "Returned charcoal is stored in the warehouse");

        menu.removed(player);
        helper.assertTrue(warehouse.entries().stream().anyMatch(e -> e.template().is(Items.CHARCOAL)),
                "Closing the terminal returns drawer contents to the warehouse");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void anvilRenameHandlesBlankAndCustomNames(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-rename-player");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinMenu menu = new KleinMenu(10, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        menu.setDrawerMode(KleinMenu.DRAWER_ANVIL);
        ItemStack item = new ItemStack(Items.DIAMOND_SWORD);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("Old Name"));
        menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).set(item);
        menu.setAnvilItemName("");
        helper.assertTrue(!menu.slots.get(KleinMenu.ANVIL_RESULT_SLOT).getItem()
                        .has(DataComponents.CUSTOM_NAME),
                "Blank rename clears a prior custom name");
        menu.setAnvilItemName("Excalibur");
        helper.assertTrue(menu.slots.get(KleinMenu.ANVIL_RESULT_SLOT).getItem()
                        .getHoverName().getString().equals("Excalibur"),
                "Custom rename updates the result");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void anvilRenameServerResultIsTakeable(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-rename-take-player");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinMenu menu = new KleinMenu(11, player.getInventory(), InteractionHand.MAIN_HAND);
        player.containerMenu = menu;
        menu.setDrawerMode(KleinMenu.DRAWER_ANVIL);
        menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).set(new ItemStack(Items.DIAMOND_SWORD));
        menu.setAnvilItemName("Excalibur");

        ItemStack preview = menu.slots.get(KleinMenu.ANVIL_RESULT_SLOT).getItem();
        helper.assertTrue(preview.getHoverName().getString().equals("Excalibur"),
                "Rename result is materialized in the result slot");
        menu.clicked(KleinMenu.ANVIL_RESULT_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().getHoverName().getString().equals("Excalibur"),
                "Renamed result can be taken into the cursor");
        helper.assertTrue(menu.slots.get(KleinMenu.ANVIL_LEFT_SLOT).getItem().isEmpty(),
                "Taking the result consumes the input");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void autoDepositPreferencePersistsWithoutItems(GameTestHelper helper) {
        KleinStorageSavedData data = new KleinStorageSavedData();
        UUID owner = UUID.randomUUID();
        helper.assertTrue(!data.autoDeposit(owner), "Auto-deposit defaults off");
        data.setAutoDeposit(owner, true);
        CompoundTag serialized = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        KleinStorageSavedData loaded = KleinStorageSavedData.load(serialized, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.autoDeposit(owner), "Auto-deposit persists for an empty warehouse");
        loaded.setAutoDeposit(owner, false);
        helper.assertTrue(!loaded.autoDeposit(owner), "Auto-deposit can be disabled");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void shiftUseTransfersChestAndKeepsNestedItems(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-container-transfer");
        ItemStack bottle = new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get());
        player.getInventory().setItem(0, bottle);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        Vec3 center = Vec3.atCenterOf(pos);
        player.setPos(center.x, center.y, center.z);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity source = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
        source.setItem(0, new ItemStack(Items.DIAMOND, 12));
        source.setItem(1, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        source.setItem(2, new ItemStack(Items.SHULKER_BOX));
        source.getItem(2).set(DataComponents.CUSTOM_NAME, Component.literal("opaque container"));

        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(center, Direction.UP, pos, false));
        player.setShiftKeyDown(false);
        helper.assertTrue(bottle.getItem().onItemUseFirst(bottle, context) == InteractionResult.PASS,
                "Container transfer requires Shift-right-click");

        player.setShiftKeyDown(true);
        InteractionResult result = bottle.getItem().onItemUseFirst(bottle, context);
        KleinWarehouse warehouse = KleinStorageSavedData.get(player.server).warehouse(player.getUUID());
        helper.assertTrue(result.consumesAction(), "Shift-right-click consumes the container interaction");
        helper.assertTrue(source.getItem(0).isEmpty() && warehouse.containsType(new ItemStack(Items.DIAMOND)),
                "Storable container item transfers");
        helper.assertTrue(source.getItem(1).getItem() instanceof KleinBottleItem,
                "Container transfer leaves Klein Bottles in source");
        helper.assertTrue(warehouse.entries().stream().anyMatch(entry -> entry.template().is(Items.SHULKER_BOX)
                        && entry.template().has(DataComponents.CUSTOM_NAME)),
                "Nested container moves as one opaque item with components intact");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void pickupAutoDepositRequiresExistingTypeAndBottle(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-auto-pickup");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinStorageSavedData saved = KleinStorageSavedData.get(player.server);
        KleinWarehouse warehouse = saved.warehouse(player.getUUID());
        warehouse.insert(new ItemStack(Items.DIAMOND), BigInteger.valueOf(16));
        saved.setAutoDeposit(player.getUUID(), true);
        ItemStack pickup = new ItemStack(Items.DIAMOND, 8);
        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                helper.getLevel(), 0.5, 64.5, 0.5, pickup);
        helper.assertTrue(KleinPickupHandler.tryDeposit(player, entity),
                "Existing matching type accepts pickup deposit");
        helper.assertTrue(entity.isRemoved() && warehouse.amount(warehouse.entries().iterator().next().id())
                        .equals(BigInteger.valueOf(24)),
                "Pickup entity is consumed and exact quantity is stored");
        saved.setAutoDeposit(player.getUUID(), false);
        helper.assertTrue(!saved.autoDeposit(player.getUUID()), "Pickup preference can be disabled");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void pickupAutoDepositRejectsComponentMismatchAndBottleSource(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-auto-rules");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinStorageSavedData saved = KleinStorageSavedData.get(player.server);
        KleinWarehouse warehouse = saved.warehouse(player.getUUID());
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack named = plain.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Named"));
        warehouse.insert(plain, BigInteger.ONE);
        helper.assertTrue(!warehouse.containsType(named), "Component mismatch is not a matching pickup");
        helper.assertTrue(!KleinWarehouse.canStore(new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get())),
                "Klein Bottle source never enters warehouse");
        warehouse.extract(warehouse.entries().iterator().next().id(), 1);
        helper.assertTrue(!warehouse.containsType(plain), "Exhausted stock no longer matches pickups");
        saved.setAutoDeposit(player.getUUID(), true);
        net.minecraft.world.entity.item.ItemEntity mismatch = new net.minecraft.world.entity.item.ItemEntity(
                helper.getLevel(), 0.5, 64.5, 0.5, named);
        helper.assertTrue(!KleinPickupHandler.tryDeposit(player, mismatch),
                "Component mismatch remains an ordinary pickup");
        net.minecraft.world.entity.item.ItemEntity bottleEntity = new net.minecraft.world.entity.item.ItemEntity(
                helper.getLevel(), 0.5, 64.5, 0.5,
                new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        helper.assertTrue(!KleinPickupHandler.tryDeposit(player, bottleEntity),
                "Klein Bottle pickup is rejected");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void containerTransferExcludesProcessingBlocks(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-menu-guard");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 2));
        Vec3 center = Vec3.atCenterOf(pos);
        player.setPos(center.x, center.y, center.z);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.FURNACE.defaultBlockState());
        net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace =
                (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getLevel().getBlockEntity(pos);
        furnace.setItem(0, new ItemStack(Items.IRON_ORE, 8));

        helper.assertTrue(!KleinContainerTransfer.isSupported(
                        helper.getLevel(), pos, player, Direction.UP),
                "Processing containers must be excluded from direct transfer");
        helper.assertTrue(KleinContainerTransfer.transfer(player, pos, Direction.UP) == 0,
                "Excluded containers transfer no items");
        helper.assertTrue(furnace.getItem(0).getCount() == 8,
                "Excluded processing contents remain untouched");

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void clientMenuConstructsWithInitialAutoDeposit(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-client-sync");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinStorageSavedData saved = KleinStorageSavedData.get(player.server);
        saved.setAutoDeposit(player.getUUID(), true);

        net.minecraft.network.FriendlyByteBuf buffer = new net.minecraft.network.FriendlyByteBuf(
                io.netty.buffer.Unpooled.buffer());
        try {
            buffer.writeVarInt(0);
            buffer.writeBoolean(true);
            KleinMenu menu = new KleinMenu(30, player.getInventory(), buffer);
            helper.assertTrue(menu.autoDeposit(), "Client menu initialized with autoDeposit=true from extra buffer");
            menu.setAutoDeposit(false);
            helper.assertTrue(!menu.autoDeposit(), "Menu autoDeposit can be toggled to false");
            helper.assertTrue(!saved.autoDeposit(player.getUUID()), "SavedData updated on server");
        } finally {
            buffer.release();
        }

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void pickupAutoDepositRespectsDelayAndTarget(GameTestHelper helper) {
        ServerPlayer player = mockPlayer(helper.getLevel().getServer(), helper.getLevel(), "klein-pickup-guard");
        player.getInventory().setItem(0, new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));
        KleinStorageSavedData saved = KleinStorageSavedData.get(player.server);
        KleinWarehouse warehouse = saved.warehouse(player.getUUID());
        warehouse.insert(new ItemStack(Items.GOLD_INGOT), BigInteger.valueOf(10));
        saved.setAutoDeposit(player.getUUID(), true);

        // Item with active pickup delay
        net.minecraft.world.entity.item.ItemEntity delayed = new net.minecraft.world.entity.item.ItemEntity(
                helper.getLevel(), 0.5, 64.5, 0.5, new ItemStack(Items.GOLD_INGOT, 5));
        delayed.setPickUpDelay(40);
        helper.assertTrue(delayed.hasPickUpDelay(), "Entity has pickup delay");
        helper.assertTrue(!KleinPickupHandler.tryDeposit(player, delayed),
                "Items with active pickup delay must not be deposited");

        // Item targeted at a different player
        net.minecraft.world.entity.item.ItemEntity otherTarget = new net.minecraft.world.entity.item.ItemEntity(
                helper.getLevel(), 0.5, 64.5, 0.5, new ItemStack(Items.GOLD_INGOT, 5));
        otherTarget.setTarget(UUID.randomUUID());
        helper.assertTrue(!KleinPickupHandler.tryDeposit(player, otherTarget),
                "Items targeted at another player must not be deposited");

        helper.succeed();
    }

    private KleinGameTests() {}
}
