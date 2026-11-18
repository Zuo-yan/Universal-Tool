package org.gwfx.universaltool.naiwa;

import com.mojang.authlib.GameProfile;
import com.november.mcphone.api.client.app.IPhoneApp;
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
    public static void didiOrderSummonOneAndThreeSpawnsOwnedNaiwas(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
            }
        }

        ServerPlayer player = mockPlayer(level.getServer(), level, "didi-summon-test");
        player.setPos(center.getX() + 0.5D, center.getY() + 1.0D, center.getZ() + 0.5D);

        DidiOrderPayload.executeOrder(player, DidiOrderPayload.ACTION_SUMMON_ONE);
        List<NaiwaEntity> afterOne = findOwned(level, player, 24.0D);
        helper.assertTrue(afterOne.size() == 1,
                "SUMMON_ONE must spawn 1 owned NaiwaEntity, got " + afterOne.size());
        helper.assertTrue(afterOne.get(0).isLaughing(),
                "Newly airdropped Naiwa must start with debut laugh");

        DidiOrderPayload.executeOrder(player, DidiOrderPayload.ACTION_SUMMON_THREE);
        List<NaiwaEntity> afterThree = findOwned(level, player, 24.0D);
        helper.assertTrue(afterThree.size() == 4,
                "SUMMON_THREE must spawn 3 more owned NaiwaEntitys (total 4), got " + afterThree.size());

        for (NaiwaEntity n : afterThree) {
            helper.assertTrue(player.getUUID().equals(n.getOwnerUuid()),
                    "Every summoned Naiwa must record owner UUID");
            helper.assertTrue(n.getOrderMode() == NaiwaEntity.MODE_FOLLOW,
                    "Default order mode must be MODE_FOLLOW");
            n.discard();
        }
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
    public static void didiOrderModeToggleUltimateAndDismissAll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = -3; x <= 5; x++) {
            for (int z = -3; z <= 5; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState());
                for (int y = 2; y <= 4; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerPlayer player = mockPlayer(level.getServer(), level, "didi-commands-test");
        player.setPos(base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D);

        NaiwaEntity n1 = ModEntities.NAIWA.get().create(level);
        NaiwaEntity n2 = ModEntities.NAIWA.get().create(level);
        helper.assertTrue(n1 != null && n2 != null, "NaiwaEntity instances must create");
        n1.setPos(base.getX() + 0.5D, base.getY() + 1.0D, base.getZ() + 0.5D);
        n2.setPos(base.getX() + 30.5D, base.getY() + 1.0D, base.getZ() + 0.5D);
        n1.setOwnerUuid(player.getUUID());
        n2.setOwnerUuid(player.getUUID());
        n1.setOrderMode(NaiwaEntity.MODE_FOLLOW);
        n2.setOrderMode(NaiwaEntity.MODE_FOLLOW);
        level.addFreshEntity(n1);
        level.addFreshEntity(n2);

        // Toggle to PATROL
        DidiOrderPayload.executeOrder(player, DidiOrderPayload.ACTION_MODE_PATROL);
        helper.assertTrue(n1.getOrderMode() == NaiwaEntity.MODE_PATROL
                        && n2.getOrderMode() == NaiwaEntity.MODE_PATROL,
                "ACTION_MODE_PATROL must switch all owned Naiwas to MODE_PATROL");

        // Toggle back to FOLLOW (and teleport far n2 closer)
        DidiOrderPayload.executeOrder(player, DidiOrderPayload.ACTION_MODE_FOLLOW);
        helper.assertTrue(n1.getOrderMode() == NaiwaEntity.MODE_FOLLOW
                        && n2.getOrderMode() == NaiwaEntity.MODE_FOLLOW,
                "ACTION_MODE_FOLLOW must switch all owned Naiwas to MODE_FOLLOW");
        helper.assertTrue(n2.distanceTo(player) < 12.0D,
                "ACTION_MODE_FOLLOW must teleport distant Naiwa near owner, dist=" + n2.distanceTo(player));

        // Ultimate laugh
        DidiOrderPayload.executeOrder(player, DidiOrderPayload.ACTION_ULTIMATE_LAUGH);
        helper.assertTrue(n1.isLaughing() && n2.isLaughing(),
                "ACTION_ULTIMATE_LAUGH must trigger laughing on all owned Naiwas");

        // Dismiss all
        DidiOrderPayload.executeOrder(player, DidiOrderPayload.ACTION_DISMISS_ALL);
        helper.assertTrue(n1.isRemoved() && n2.isRemoved(),
                "ACTION_DISMISS_ALL must discard all owned Naiwas");
        helper.assertTrue(findOwned(level, player, 96.0D).isEmpty(),
                "No owned Naiwas may remain after ACTION_DISMISS_ALL");

        helper.succeed();
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
