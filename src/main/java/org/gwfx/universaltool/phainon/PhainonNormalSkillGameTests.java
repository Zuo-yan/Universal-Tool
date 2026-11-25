package org.gwfx.universaltool.phainon;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModSounds;

import java.util.UUID;

/**
 * 白厄武器（侵晨）普通状态右键战技「黎明创世，地辟天开」服务端轻量 GameTest。
 */
@PrefixGameTestTemplate(false)
public final class PhainonNormalSkillGameTests {

    private PhainonNormalSkillGameTests() {
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 150)
    public static void ultimateSuspendsMovementAndResumesAfterOpening(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildStonePlatform(level, helper, -1, 1, 2, -1, 1);
        BlockPos start = helper.absolutePos(new BlockPos(0, 3, 0));
        ServerPlayer player = mockPlayer(level.getServer(), level, "ultimate-motion");
        player.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        var origin = player.position();
        var sword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        ModDataComponents.setPhainonWeaponState(sword, new PhainonWeaponState(12, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        double normalGravity = player.getAttributeValue(Attributes.GRAVITY);
        helper.assertTrue(sword.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "Full coreflame must start the ultimate");
        helper.assertTrue(ModSounds.PHAINON_ULTIMATE_ACTIVATE.get() != null
                        && ModSounds.PHAINON_ULTIMATE_RELEASE.get() != null, "Both voice events must be registered");

        helper.startSequence()
                .thenExecuteAfter(32, () -> {
                    PhainonUltimateHandler.tick(new PlayerTickEvent.Post(player));
                    helper.assertTrue(player.getY() - origin.y > 1.0 && player.getAttributeValue(Attributes.GRAVITY) == 0,
                            "The server must physically lift the player with gravity suspended");
                    var held = player.position();
                    player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                            held.x + 2.0, held.y + 0.5, held.z + 1.0, 45, 0, false));
                    helper.assertTrue(player.position().equals(held) && player.getYRot() == 45,
                            "Movement positions must be ignored while camera rotation is still accepted");
                })
                .thenExecuteAfter(PhainonUltimateHandler.AWAKEN_TICK - 32, () -> {
                    PhainonUltimateHandler.tick(new PlayerTickEvent.Post(player));
                    helper.assertTrue(ModDataComponents.getPhainonWeaponState(sword).transformed(),
                            "The sword must awaken during suspension");
                })
                .thenExecuteAfter(PhainonUltimateHandler.OPENING_TICKS - PhainonUltimateHandler.AWAKEN_TICK, () -> {
                    PhainonUltimateHandler.tick(new PlayerTickEvent.Post(player));
                    helper.assertTrue(!PhainonUltimateHandler.isMovementLocked(player)
                                    && player.position().equals(origin)
                                    && player.getAttributeValue(Attributes.GRAVITY) == normalGravity,
                            "Movement and gravity must resume at the end of the opening");
                    PhainonUltimateHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 48)
    public static void ultimateRespectsCeilingAndReleasesOnWeaponSwap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildStonePlatform(level, helper, -1, 1, 2, -1, 1);
        BlockPos start = helper.absolutePos(new BlockPos(0, 3, 0));
        level.setBlockAndUpdate(start.above(2), Blocks.STONE.defaultBlockState());
        ServerPlayer player = mockPlayer(level.getServer(), level, "ultimate-ceiling");
        player.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
        var sword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        ModDataComponents.setPhainonWeaponState(sword, new PhainonWeaponState(12, false));
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        double normalGravity = player.getAttributeValue(Attributes.GRAVITY);
        helper.assertTrue(sword.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "The ultimate should accept a cast below a low ceiling");

        helper.runAfterDelay(30, () -> {
            PhainonUltimateHandler.tick(new PlayerTickEvent.Post(player));
            double lift = player.getY() - start.getY();
            helper.assertTrue(lift >= 0 && lift < 0.21 && !player.isInWall()
                            && PhainonUltimateHandler.isMovementLocked(player),
                    "Lift must stay below the ceiling while keeping the cast locked");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            PhainonUltimateHandler.tick(new PlayerTickEvent.Post(player));
            helper.assertTrue(!PhainonUltimateHandler.isMovementLocked(player)
                            && player.getAttributeValue(Attributes.GRAVITY) == normalGravity
                            && ModDataComponents.getPhainonWeaponState(sword).canAwaken(),
                    "An interrupted windup must release motion and leave the unspent coreflame");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 80)
    public static void normalSkillDealsBlastDamageAndGrantsCoreflame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildStonePlatform(level, helper, -10, 10, 2, -10, 10);

        BlockPos playerPos = helper.absolutePos(new BlockPos(0, 3, 0));
        ServerPlayer player = mockPlayer(level.getServer(), level, "phainon-skill-tester");
        player.moveTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D, 0.0F, 0.0F);

        ItemStack sword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);

        // yaw = 0 时玩家正前方为 +Z 方向
        Husk primaryTarget = spawnHusk(level, playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 4.5D);
        Husk adjacentTarget = spawnHusk(level, playerPos.getX() + 3.0D, playerPos.getY(), playerPos.getZ() + 4.5D);
        Husk outOfRangeTarget = spawnHusk(level, playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() - 8.0D);

        float primaryMaxHp = primaryTarget.getHealth();
        float adjacentMaxHp = adjacentTarget.getHealth();
        float outOfRangeMaxHp = outOfRangeTarget.getHealth();

        helper.assertTrue(ModSounds.PHAINON_NORMAL_SKILL_VOICE.get() != null,
                "Phainon normal skill voice SoundEvent must be registered");
        InteractionResultHolder<ItemStack> result = sword.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult().consumesAction(),
                "Normal skill right-click must consume action, got " + result.getResult());
        helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.QINCHEN_SWORD.get()),
                "Dawnmaker must enter cooldown immediately after starting normal skill");
        helper.assertTrue(PhainonNormalSkillHandler.isCasting(player.getUUID()),
                "Player must enter active normal skill cast state");

        tickCastAndAssert(helper, player, sword, primaryTarget, adjacentTarget, outOfRangeTarget,
                primaryMaxHp, adjacentMaxHp, outOfRangeMaxHp, 0);
    }

    private static void tickCastAndAssert(GameTestHelper helper,
                                          ServerPlayer player,
                                          ItemStack sword,
                                          Husk primaryTarget,
                                          Husk adjacentTarget,
                                          Husk outOfRangeTarget,
                                          float primaryMaxHp,
                                          float adjacentMaxHp,
                                          float outOfRangeMaxHp,
                                          int step) {
        int checkStep = PhainonNormalSkillHandler.IMPACT_TICK + 4;
        if (step >= checkStep) {
            try {
                float primaryHp = primaryTarget.getHealth();
                float adjacentHp = adjacentTarget.getHealth();
                float outHp = outOfRangeTarget.getHealth();

                helper.assertTrue(primaryHp < primaryMaxHp,
                        "Primary target must take damage: before=" + primaryMaxHp + ", after=" + primaryHp);
                helper.assertTrue(adjacentHp < adjacentMaxHp,
                        "Adjacent Blast target must take splash damage: before=" + adjacentMaxHp + ", after=" + adjacentHp);
                helper.assertTrue(primaryHp < adjacentHp,
                        "Primary target must take heavier damage than adjacent target: primary="
                                + primaryHp + ", adjacent=" + adjacentHp);
                helper.assertTrue(Math.abs(outHp - outOfRangeMaxHp) < 0.01F,
                        "Out-of-range rear target must remain unharmed: expected="
                                + outOfRangeMaxHp + ", got=" + outHp);

                PhainonWeaponState state = ModDataComponents.getPhainonWeaponState(sword);
                helper.assertTrue(state.coreflame() == PhainonWeaponState.NORMAL_SKILL_COREFLAME_GAIN,
                        "Normal skill must grant 2 Coreflame, got " + state.coreflame());
                helper.succeed();
            } finally {
                primaryTarget.discard();
                adjacentTarget.discard();
                outOfRangeTarget.discard();
            }
            return;
        }

        helper.runAfterDelay(1, () -> {
            player.doTick();
            tickCastAndAssert(helper, player, sword, primaryTarget, adjacentTarget, outOfRangeTarget,
                    primaryMaxHp, adjacentMaxHp, outOfRangeMaxHp, step + 1);
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 40)
    public static void coreflameCapsAtTwelveAndTransformedBlocksNormalCast(GameTestHelper helper) {
        PhainonWeaponState capped = PhainonWeaponState.DEFAULT.withAddedCoreflame(20);
        helper.assertTrue(capped.coreflame() == PhainonWeaponState.MAX_COREFLAME,
                "Coreflame must cap at 12, got " + capped.coreflame());

        ServerLevel level = helper.getLevel();
        BlockPos playerPos = helper.absolutePos(new BlockPos(0, 3, 0));
        ServerPlayer player = mockPlayer(level.getServer(), level, "phainon-transformed-tester");
        player.moveTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D, 0.0F, 0.0F);

        ItemStack sword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        ModDataComponents.setPhainonWeaponState(sword, new PhainonWeaponState(12, true));
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);

        InteractionResultHolder<ItemStack> result = sword.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult() == InteractionResult.PASS,
                "Transformed weapon state must pass normal skill use, got " + result.getResult());
        helper.assertTrue(!PhainonNormalSkillHandler.isCasting(player.getUUID()),
                "Transformed weapon state must not enter normal skill cast");

        helper.succeed();
    }

    private static Husk spawnHusk(ServerLevel level, double x, double y, double z) {
        Husk husk = EntityType.HUSK.create(level);
        if (husk == null) {
            throw new IllegalStateException("Failed to create Husk entity");
        }
        husk.moveTo(x, y, z, 180.0F, 0.0F);
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(120);
        husk.setHealth(120);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        level.addFreshEntity(husk);
        return husk;
    }

    private static void buildStonePlatform(ServerLevel level, GameTestHelper helper,
                                           int minX, int maxX, int floorY, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, floorY, z)),
                        Blocks.STONE.defaultBlockState());
                for (int y = floorY + 1; y <= floorY + 4; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
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
