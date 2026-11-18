package org.gwfx.universaltool.naiwa;

import com.mojang.authlib.GameProfile;
import com.november.mcphone.api.client.app.IPhoneApp;
import com.november.mcphone.core.PhonePlayerData;
import com.november.mcphone.feature.store.AppPriceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.init.ModCreativeTabs;
import org.gwfx.universaltool.init.ModEntities;
import org.gwfx.universaltool.init.ModItems;

import java.util.List;
import java.util.ServiceLoader;
import java.util.UUID;

/**
 * Server-side GameTests for Naiwa ("奶蛙") summoning, combat sonic-boom waves,
 * Didi order mode toggles/ultimate/dismissal, MCphone integration, and SPI registration.
 */
@PrefixGameTestTemplate(false)
public final class NaiwaGameTests {

    private NaiwaGameTests() {
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void didiOrdersChargeOnlySuccessfulPaidDispatches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 12, 1));
        ServerPlayer player = mockPlayer(level.getServer(), level, "didi-summon-test");
        player.setPos(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 28));

        DidiStatusPayload locked = order(player, DidiOrderPayload.ACTION_SUMMON_ONE);
        helper.assertTrue(locked.result().equals("purchase_required") && emeralds(player) == 28,
                "A player without the app must not place a paid order");
        grantDidiAccess(player);

        BlockPos drop = BlockPos.containing(player.getX(), player.getY(), player.getZ() + 3);
        for (int height = 0; height <= 8; height++) {
            level.setBlockAndUpdate(drop.above(height), Blocks.STONE.defaultBlockState());
        }
        DidiStatusPayload failed = order(player, DidiOrderPayload.ACTION_SUMMON_ONE);
        helper.assertTrue(failed.result().equals("dispatch_failed") && emeralds(player) == 28,
                "A blocked drop site must leave the player's emeralds and order unchanged");
        for (int height = 0; height <= 8; height++) {
            level.setBlockAndUpdate(drop.above(height), Blocks.AIR.defaultBlockState());
        }

        DidiStatusPayload single = order(player, DidiOrderPayload.ACTION_SUMMON_ONE);
        List<NaiwaEntity> afterOne = findOwned(level, player, 24.0);
        helper.assertTrue(single.remainingTicks() == DidiOrderBook.DURATION && afterOne.size() == 1
                        && emeralds(player) == 20 && afterOne.getFirst().isLaughing(),
                "A single dispatch must charge 8 emeralds and start one two-minute rental: " + single);
        DidiStatusPayload duplicate = order(player, DidiOrderPayload.ACTION_SUMMON_THREE);
        helper.assertTrue(duplicate.result().equals("active_order") && emeralds(player) == 20,
                "A second dispatch must leave the active order and inventory unchanged");

        afterOne.getFirst().die(level.damageSources().generic());
        helper.assertTrue(order(player, DidiOrderPayload.ACTION_STATUS).remainingTicks() == 0,
                "The order must end when its last member dies");
        afterOne.getFirst().discard();

        DidiStatusPayload three = order(player, DidiOrderPayload.ACTION_SUMMON_THREE);
        List<NaiwaEntity> squad = findOwned(level, player, 24.0);
        helper.assertTrue(three.squadSize() == 3 && squad.size() == 3 && emeralds(player) == 0,
                "The squad dispatch must charge 20 emeralds for three owned Naiwa: " + three);
        order(player, DidiOrderPayload.ACTION_DISMISS_ALL);
        helper.assertTrue(squad.stream().allMatch(NaiwaEntity::isRemoved) && emeralds(player) == 0,
                "Closing a completed dispatch must recall the squad without a refund");
        DidiStatusPayload unaffordable = order(player, DidiOrderPayload.ACTION_SUMMON_ONE);
        helper.assertTrue(unaffordable.result().equals("cannot_afford"), "An empty inventory must reject a new order");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 140)
    public static void naiwaLocksOntoZombieLaughsDamagesKnocksBackSlowsAndProtectsOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Elevated arena (y=12) keeps Naiwa and Zombie away from ground perimeter barriers
        for (int x = -3; x <= 5; x++) {
            for (int z = -3; z <= 5; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 12, z)), Blocks.STONE.defaultBlockState());
                for (int y = 13; y <= 16; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState());
                }
            }
        }

        BlockPos naiwaBlock = helper.absolutePos(new BlockPos(1, 13, 1));
        BlockPos zombieBlock = helper.absolutePos(new BlockPos(1, 13, 5));
        BlockPos ownerBlock = helper.absolutePos(new BlockPos(1, 13, 3));

        ServerPlayer owner = mockPlayer(level.getServer(), level, "naiwa-combat-owner");
        owner.setPos(ownerBlock.getX() + 0.5D, ownerBlock.getY(), ownerBlock.getZ() + 0.5D);
        // Add owner to server level so damage checks/target filters can test owner safety cleanly
        float ownerStartHp = owner.getHealth();

        NaiwaEntity naiwa = ModEntities.NAIWA.get().create(level);
        helper.assertTrue(naiwa != null, "NaiwaEntity must instantiate");
        naiwa.setPos(naiwaBlock.getX() + 0.5D, naiwaBlock.getY(), naiwaBlock.getZ() + 0.5D);
        naiwa.setOwnerUuid(owner.getUUID());
        naiwa.setOrderMode(NaiwaEntity.MODE_PATROL);
        level.addFreshEntity(naiwa);

        Zombie zombie = EntityType.ZOMBIE.create(level);
        helper.assertTrue(zombie != null, "Zombie must instantiate");
        zombie.setPos(zombieBlock.getX() + 0.5D, zombieBlock.getY(), zombieBlock.getZ() + 0.5D);
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);

        helper.assertTrue(!naiwa.canAttackTarget(owner), "Naiwa must never consider its owner attackable");
        helper.assertTrue(naiwa.isAlliedTo(owner), "Naiwa must report allied to its owner");

        float zombieStartHp = zombie.getHealth();
        Vec3 zombieStartPos = zombie.position();

        observeCombatTick(helper, naiwa, owner, ownerStartHp, zombie, zombieStartHp, zombieStartPos,
                false, 0);
    }

    private static void observeCombatTick(GameTestHelper helper,
                                          NaiwaEntity naiwa,
                                          ServerPlayer owner,
                                          float ownerStartHp,
                                          Zombie zombie,
                                          float zombieStartHp,
                                          Vec3 zombieStartPos,
                                          boolean sawLaughing,
                                          int tick) {
        helper.runAfterDelay(1, () -> {
            boolean laughingNow = sawLaughing || naiwa.isLaughing();
            helper.assertTrue(owner.getHealth() >= ownerStartHp,
                    "Naiwa sonic wave must never damage its owner: before=" + ownerStartHp
                            + ", after=" + owner.getHealth());

            boolean damaged = !zombie.isAlive() || zombie.getHealth() < zombieStartHp;
            boolean slowed = zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
            boolean knocked = zombie.position().distanceToSqr(zombieStartPos) > 0.25D
                    || zombie.getDeltaMovement().lengthSqr() > 0.04D;

            if (laughingNow && damaged && slowed && knocked) {
                naiwa.discard();
                if (zombie.isAlive()) {
                    zombie.discard();
                }
                helper.succeed();
                return;
            }

            if (tick >= 90) {
                helper.fail("Naiwa combat check timed out: sawLaughing=" + laughingNow
                        + ", target=" + naiwa.getTarget()
                        + ", damaged=" + damaged + " (hp=" + zombie.getHealth() + "/" + zombieStartHp + ")"
                        + ", slowed=" + slowed
                        + ", knocked=" + knocked + " (pos=" + zombie.position() + ", vel=" + zombie.getDeltaMovement() + ")");
                return;
            }

            observeCombatTick(helper, naiwa, owner, ownerStartHp, zombie, zombieStartHp, zombieStartPos,
                    laughingNow, tick + 1);
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void didiOrdersUseOneUltimateAndPreserveOldNaiwa(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 12, 1));
        ServerPlayer player = mockPlayer(level.getServer(), level, "didi-commands-test");
        player.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        grantDidiAccess(player);
        player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 20));

        NaiwaEntity pet = ModEntities.NAIWA.get().create(level);
        helper.assertTrue(pet != null, "An existing owned Naiwa must instantiate");
        pet.setPos(player.getX(), player.getY(), player.getZ());
        pet.setOwnerUuid(player.getUUID());
        level.addFreshEntity(pet);

        DidiStatusPayload dispatch = order(player, DidiOrderPayload.ACTION_SUMMON_THREE);
        helper.assertTrue(dispatch.squadSize() == 3, "A squad must dispatch: " + dispatch);
        List<NaiwaEntity> squad = findOwned(level, player, 24.0).stream()
                .filter(naiwa -> naiwa.getDidiOrderId() != null).toList();

        order(player, DidiOrderPayload.ACTION_MODE_PATROL);
        helper.assertTrue(squad.stream().allMatch(naiwa -> naiwa.getOrderMode() == NaiwaEntity.MODE_PATROL)
                        && pet.getOrderMode() == NaiwaEntity.MODE_FOLLOW,
                "Patrol control must target only this order's squad");
        order(player, DidiOrderPayload.ACTION_MODE_FOLLOW);
        helper.assertTrue(squad.stream().allMatch(naiwa -> naiwa.getOrderMode() == NaiwaEntity.MODE_FOLLOW),
                "Follow control must target the whole squad");

        DidiStatusPayload ultimate = order(player, DidiOrderPayload.ACTION_ULTIMATE_LAUGH);
        helper.assertTrue(ultimate.ultimateUsed() && squad.stream().allMatch(NaiwaEntity::isSonicCombatActive),
                "The first ultimate must start the squad's sonic attack");
        helper.assertTrue(order(player, DidiOrderPayload.ACTION_ULTIMATE_LAUGH).result().equals("ultimate_used"),
                "An order must reject a second ultimate");

        player.die(level.damageSources().generic());
        helper.assertTrue(squad.stream().allMatch(NaiwaEntity::isRemoved) && pet.isAlive(),
                "Player death must recall the rented squad and preserve old companions");
        pet.discard();
        helper.succeed();
    }

    private static DidiStatusPayload order(ServerPlayer player, int action) {
        return DidiOrderPayload.executeOrder(player, action);
    }

    private static int emeralds(ServerPlayer player) {
        return player.getInventory().countItem(Items.EMERALD);
    }

    private static void grantDidiAccess(ServerPlayer player) {
        PhonePlayerData data = PhonePlayerData.of(player);
        data.setPurchasedApps(data.purchasedApps().with(DidiAppPurchase.APP_ID));
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 60)
    public static void didiDaidaSpiAndMcphoneItemRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation didiId = ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "didi_daida");
        ResourceLocation snakeId = ResourceLocation.fromNamespaceAndPath(
                UniversalToolMod.MODID, "snake");

        IPhoneApp didiApp = null;
        IPhoneApp snakeApp = null;
        for (var it = ServiceLoader.load(IPhoneApp.class).stream().iterator(); it.hasNext(); ) {
            ServiceLoader.Provider<IPhoneApp> provider = it.next();
            if (!provider.type().getName().startsWith("org.gwfx.universaltool.")) {
                continue;
            }
            IPhoneApp app = provider.get();
            if (didiId.equals(app.getId())) {
                didiApp = app;
            } else if (snakeId.equals(app.getId())) {
                snakeApp = app;
            }
            if (didiApp != null && snakeApp != null) {
                break;
            }
        }
        helper.assertTrue(didiApp != null,
                "ServiceLoader.load(IPhoneApp.class) must discover " + didiId);
        helper.assertTrue(AppPriceRegistry.isPaid(didiId), "Didi must be marked paid by the native MCphone store");
        helper.assertTrue(!didiApp.isPreinstalled(),
                "DidiDaidaApp must have isPreinstalled() == false (App Store installable)");
        helper.assertTrue(snakeApp != null,
                "ServiceLoader.load(IPhoneApp.class) must discover " + snakeId);
        helper.assertTrue(snakeApp.isPreinstalled(),
                "SnakeApp must have isPreinstalled() == true");

        // Confirm MCphone's separate creative tab is NOT registered
        ResourceLocation mcphoneTabId = ResourceLocation.fromNamespaceAndPath("mcphone", "mcphone_tab");
        helper.assertTrue(!BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(mcphoneTabId),
                "MCphone separate creative tab " + mcphoneTabId + " must not be registered");
        helper.assertTrue(!com.november.mcphone.core.ModCreativeTabs.MCPHONE_TAB.isBound(),
                "MCphone ModCreativeTabs.MCPHONE_TAB must remain unbound");

        Item phoneItem = com.november.mcphone.core.ModItems.PHONE.get();
        Item tabletItem = com.november.mcphone.core.ModItems.TABLET.get();
        helper.assertTrue(phoneItem != null, "MCphone ModItems.PHONE must be registered");
        helper.assertTrue(tabletItem != null, "MCphone ModItems.TABLET must be registered");

        CreativeModeTab tab = ModCreativeTabs.UNIVERSAL_TOOL_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(), false, level.registryAccess()));
        boolean hasSpawnEgg = tab.getDisplayItems().stream()
                .anyMatch(stack -> stack.is(ModItems.NAIWA_SPAWN_EGG.get()));
        boolean hasPhone = tab.getDisplayItems().stream()
                .anyMatch(stack -> stack.is(phoneItem));
        boolean hasTablet = tab.getDisplayItems().stream()
                .anyMatch(stack -> stack.is(tabletItem));
        helper.assertTrue(hasSpawnEgg, "UNIVERSAL_TOOL_TAB must contain NAIWA_SPAWN_EGG");
        helper.assertTrue(hasPhone, "UNIVERSAL_TOOL_TAB must contain MCphone PHONE item");
        helper.assertTrue(hasTablet, "UNIVERSAL_TOOL_TAB must contain MCphone TABLET item");

        helper.succeed();
    }

    private static List<NaiwaEntity> findOwned(ServerLevel level, ServerPlayer owner, double radius) {
        AABB area = owner.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(NaiwaEntity.class, area,
                n -> n.isAlive() && owner.getUUID().equals(n.getOwnerUuid()));
    }

    private static ServerPlayer mockPlayer(MinecraftServer server, ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection = new ServerGamePacketListenerImpl(server, connection, player,
                CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {
            }
        };
        return player;
    }
}
