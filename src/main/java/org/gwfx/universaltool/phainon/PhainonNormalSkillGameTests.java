package org.gwfx.universaltool.phainon;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.gwfx.universaltool.init.ModDataComponents;
import org.gwfx.universaltool.init.ModItems;
import org.gwfx.universaltool.init.ModSounds;
import org.gwfx.universaltool.item.QinchenSwordItem;

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

        InteractionResultHolder<ItemStack> directNormal =
                PhainonNormalSkillHandler.tryStartCast(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(directNormal.getResult() == InteractionResult.PASS,
                "Normal skill handler must pass when weapon is transformed, got " + directNormal.getResult());

        InteractionResultHolder<ItemStack> result = sword.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.getResult() == InteractionResult.FAIL,
                "Transformed weapon outside active ultimate must fail awakened cast, got " + result.getResult());
        helper.assertTrue(!PhainonNormalSkillHandler.isCasting(player.getUUID()),
                "Transformed weapon state must not enter normal skill cast");

        helper.succeed();
    }

    // The two positioned targets extend beyond the empty template's bounds.
    // A separate batch keeps neighboring tests' entity cleanup away from this arena.
    @GameTest(templateNamespace = "universal_tool", template = "empty", batch = "phainon_counter", timeoutTicks = 260)
    public static void counterGrantsEightyPercentReductionAndScalesWithIncomingDamage(GameTestHelper helper) {
        helper.assertTrue(ModSounds.PHAINON_METEOR_VOICE.get() != null
                        && ModSounds.PHAINON_COUNTER_GUARD_VOICE.get() != null
                        && ModSounds.PHAINON_COUNTER_STRIKE_VOICE.get() != null,
                "All 3 awakened voice SoundEvents must be registered");

        ServerLevel level = helper.getLevel();
        buildStonePlatform(level, helper, -9, 9, 2, -6, 6);
        BlockPos p1Pos = helper.absolutePos(new BlockPos(-6, 3, 0));
        BlockPos p2Pos = helper.absolutePos(new BlockPos(6, 3, 0));

        ServerPlayer lowHitPlayer = mockPlayer(level.getServer(), level, "counter-low");
        ServerPlayer highHitPlayer = mockPlayer(level.getServer(), level, "counter-high");
        lowHitPlayer.moveTo(p1Pos.getX() + 0.5, p1Pos.getY(), p1Pos.getZ() + 0.5, 0, 0);
        highHitPlayer.moveTo(p2Pos.getX() + 0.5, p2Pos.getY(), p2Pos.getZ() + 0.5, 0, 0);
        lowHitPlayer.setGameMode(GameType.SURVIVAL);
        highHitPlayer.setGameMode(GameType.SURVIVAL);
        lowHitPlayer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        lowHitPlayer.setHealth(100);
        highHitPlayer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        highHitPlayer.setHealth(100);
        // 消耗新建 ServerPlayer 默认 60 tick 出生无敌保护（在 tick() 而非 doTick() 中递减）
        for (int i = 0; i < 65; i++) {
            lowHitPlayer.tick();
            highHitPlayer.tick();
        }
        lowHitPlayer.setHealth(100);
        highHitPlayer.setHealth(100);
        lowHitPlayer.invulnerableTime = 0;
        highHitPlayer.invulnerableTime = 0;

        var genericDamageType = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.GENERIC);

        ItemStack lowSword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        ItemStack highSword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        ModDataComponents.setPhainonWeaponState(lowSword, new PhainonWeaponState(12, false, 0));
        ModDataComponents.setPhainonWeaponState(highSword, new PhainonWeaponState(12, false, 0));
        lowHitPlayer.setItemInHand(InteractionHand.MAIN_HAND, lowSword);
        highHitPlayer.setItemInHand(InteractionHand.MAIN_HAND, highSword);

        Husk lowAttacker = spawnHusk(level, p1Pos.getX() + 0.5, p1Pos.getY(), p1Pos.getZ() + 3.0);
        Husk highAttacker = spawnHusk(level, p2Pos.getX() + 0.5, p2Pos.getY(), p2Pos.getZ() + 3.0);
        lowAttacker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        lowAttacker.setHealth(400);
        highAttacker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(400);
        highAttacker.setHealth(400);

        helper.assertTrue(lowSword.use(level, lowHitPlayer, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "Low-hit player must start ultimate");
        helper.assertTrue(highSword.use(level, highHitPlayer, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "High-hit player must start ultimate");

        helper.startSequence()
                .thenExecuteAfter(PhainonUltimateHandler.AWAKEN_TICK, () -> {
                    PhainonUltimateHandler.tick(new PlayerTickEvent.Post(lowHitPlayer));
                    PhainonUltimateHandler.tick(new PlayerTickEvent.Post(highHitPlayer));
                })
                .thenExecuteAfter(PhainonUltimateHandler.OPENING_TICKS - PhainonUltimateHandler.AWAKEN_TICK + 1, () -> {
                    PhainonUltimateHandler.tick(new PlayerTickEvent.Post(lowHitPlayer));
                    PhainonUltimateHandler.tick(new PlayerTickEvent.Post(highHitPlayer));
                    lowHitPlayer.getCooldowns().removeCooldown(lowSword.getItem());
                    highHitPlayer.getCooldowns().removeCooldown(highSword.getItem());
                    lowHitPlayer.setShiftKeyDown(true);
                    highHitPlayer.setShiftKeyDown(true);
                    helper.assertTrue(PhainonCounter.use(lowHitPlayer, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                            "Low-hit player must enter counter guard");
                    helper.assertTrue(PhainonCounter.use(highHitPlayer, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                            "High-hit player must enter counter guard");
                })
                .thenExecuteAfter(PhainonCounter.READY + 1, () -> {
                    float lowBefore = lowHitPlayer.getHealth();
                    float highBefore = highHitPlayer.getHealth();

                    // 使用不受难度缩放影响但携带攻击者实体的 DamageSource，确保各难度下精准校验 80% 免伤与受击增伤
                    lowHitPlayer.hurt(new DamageSource(genericDamageType, lowAttacker), 10.0F);
                    highHitPlayer.hurt(new DamageSource(genericDamageType, highAttacker), 40.0F);

                    float lowTaken = lowBefore - lowHitPlayer.getHealth();
                    float highTaken = highBefore - highHitPlayer.getHealth();
                    helper.assertTrue(Math.abs(lowTaken - 2.0F) < 0.05F,
                            "Counter guard must apply 80% reduction (10 -> 2), got " + lowTaken);
                    helper.assertTrue(Math.abs(highTaken - 8.0F) < 0.05F,
                            "Counter guard must apply 80% reduction (40 -> 8), got " + highTaken);
                })
                .thenExecuteAfter(PhainonCounter.IMPACT, () -> {
                    PhainonCounter.tick(new PlayerTickEvent.Post(lowHitPlayer));
                    PhainonCounter.tick(new PlayerTickEvent.Post(highHitPlayer));

                    helper.assertTrue(level.getEntity(lowAttacker.getUUID()) == lowAttacker,
                            "Low-damage attacker missing from the test world: " + lowAttacker.getRemovalReason());
                    helper.assertTrue(level.getEntity(highAttacker.getUUID()) == highAttacker,
                            "High-damage attacker missing from the test world: " + highAttacker.getRemovalReason());
                    float lowDealt = 400.0F - lowAttacker.getHealth();
                    float highDealt = 400.0F - highAttacker.getHealth();
                    helper.assertTrue(lowDealt > PhainonCounter.BASE_COUNTER_DAMAGE + 10.0F,
                            "Counter damage after taking 10 dmg must exceed base 90, got " + lowDealt);
                    helper.assertTrue(highDealt > lowDealt + 50.0F,
                            "Higher incoming damage must yield significantly higher counter damage: low="
                                    + lowDealt + ", high=" + highDealt);
                })
                .thenExecuteAfter(PhainonCounter.RECOVERY - PhainonCounter.IMPACT + 2, () -> {
                    PhainonCounter.tick(new PlayerTickEvent.Post(lowHitPlayer));
                    // 架势结束后免伤必须失效，避免永久免伤
                    lowHitPlayer.invulnerableTime = 0;
                    float beforePostGuard = lowHitPlayer.getHealth();
                    lowHitPlayer.hurt(new DamageSource(genericDamageType, lowAttacker), 10.0F);
                    float postGuardTaken = beforePostGuard - lowHitPlayer.getHealth();
                    helper.assertTrue(Math.abs(postGuardTaken - 10.0F) < 0.05F,
                            "Damage reduction must end after counter recovery, got " + postGuardTaken);

                    PhainonUltimateHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(lowHitPlayer));
                    PhainonUltimateHandler.onLogout(new PlayerEvent.PlayerLoggedOutEvent(highHitPlayer));
                    lowAttacker.discard();
                    highAttacker.discard();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 90)
    public static void killGrowthIncreasesBaseAndSkillDamageEveryTwelveKills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        buildStonePlatform(level, helper, -10, 10, 2, -10, 10);
        BlockPos playerPos = helper.absolutePos(new BlockPos(0, 3, 0));

        ServerPlayer player = mockPlayer(level.getServer(), level, "kill-growth-tester");
        player.moveTo(playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5, 0.0F, 0.0F);
        ItemStack sword = new ItemStack(ModItems.QINCHEN_SWORD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);

        double baseDmgMod = mainHandModifierAmount(sword, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
        double baseSpeedMod = mainHandModifierAmount(sword, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID);
        helper.assertTrue(Math.abs(baseDmgMod - QinchenSwordItem.BASE_DAMAGE_MODIFIER) < 0.001D,
                "Base attack damage modifier must be 6.0 (total 7), got " + baseDmgMod);

        // 模拟使用侵晨击杀 11 个生物：尚未达到 12 杀门槛，伤害加成仍为 0
        for (int i = 0; i < 11; i++) {
            Husk mob = spawnHusk(level, playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 6.0);
            PhainonKillGrowthHandler.onLivingDeath(new LivingDeathEvent(mob, player.damageSources().playerAttack(player)));
            mob.discard();
        }
        helper.assertTrue(ModDataComponents.getPhainonWeaponState(sword).bonusDamage() == 0,
                "11 kills must still give +0 bonus damage");

        // 第 12 次击杀：基础攻击伤害 +1（7 -> 8），攻速保持不变
        Husk twelfth = spawnHusk(level, playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 6.0);
        PhainonKillGrowthHandler.onLivingDeath(new LivingDeathEvent(twelfth, player.damageSources().playerAttack(player)));
        twelfth.discard();

        PhainonWeaponState grownState = ModDataComponents.getPhainonWeaponState(sword);
        helper.assertTrue(grownState.kills() == 12 && grownState.bonusDamage() == 1,
                "12 kills must grant +1 bonus damage, got kills=" + grownState.kills()
                        + ", bonus=" + grownState.bonusDamage());

        double grownDmgMod = mainHandModifierAmount(sword, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
        double grownSpeedMod = mainHandModifierAmount(sword, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID);
        helper.assertTrue(Math.abs(grownDmgMod - (QinchenSwordItem.BASE_DAMAGE_MODIFIER + 1.0D)) < 0.001D,
                "Attack damage modifier must grow by +1 after 12 kills, got " + grownDmgMod);
        helper.assertTrue(Math.abs(grownSpeedMod - baseSpeedMod) < 0.001D,
                "Attack speed modifier must remain unchanged after kill growth");

        // 校验觉醒切换保留击杀成长，且陨石/反击伤害同步增长
        PhainonWeaponState awakened = grownState.withAwakened();
        helper.assertTrue(awakened.kills() == 12 && awakened.bonusDamage() == 1,
                "Awakening must preserve accumulated kill growth");
        helper.assertTrue(PhainonCounter.computeCounterDamage(20.0F, grownState)
                        > PhainonCounter.computeCounterDamage(20.0F, PhainonWeaponState.DEFAULT),
                "Counter damage must scale up with kill growth");
        helper.assertTrue(grownState.scaleSkillDamage(PhainonMeteorDamage.GIANT_DAMAGE)
                        > PhainonMeteorDamage.GIANT_DAMAGE,
                "Meteor damage must scale up with kill growth");

        // 校验普通战技实战伤害确实比基础 63.0 更高（12 杀时应为 63 * 8 / 7 = 72.0）
        Husk target = spawnHusk(level, playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 4.5);
        float maxHp = target.getHealth();
        helper.assertTrue(sword.use(level, player, InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "Normal skill must cast with grown sword");

        tickKillGrowthSkillCheck(helper, player, target, maxHp, 0);
    }

    private static void tickKillGrowthSkillCheck(GameTestHelper helper,
                                                 ServerPlayer player,
                                                 Husk target,
                                                 float maxHp,
                                                 int step) {
        if (step >= PhainonNormalSkillHandler.IMPACT_TICK + 2) {
            try {
                float dealt = maxHp - target.getHealth();
                float expected = PhainonNormalSkillHandler.PRIMARY_DAMAGE * (8.0F / 7.0F);
                helper.assertTrue(dealt > PhainonNormalSkillHandler.PRIMARY_DAMAGE + 5.0F
                                && Math.abs(dealt - expected) < 3.0F,
                        "Normal skill primary damage with +1 growth must scale up (~" + expected + "), got " + dealt);
                helper.succeed();
            } finally {
                target.discard();
            }
            return;
        }
        helper.runAfterDelay(1, () -> {
            player.doTick();
            tickKillGrowthSkillCheck(helper, player, target, maxHp, step + 1);
        });
    }

    private static double mainHandModifierAmount(ItemStack stack,
                                                 net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                                                 net.minecraft.resources.ResourceLocation id) {
        double[] found = {Double.NaN};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (holder, modifier) -> {
            if (holder.equals(attr) && modifier.id().equals(id)) {
                found[0] = modifier.amount();
            }
        });
        return found[0];
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
