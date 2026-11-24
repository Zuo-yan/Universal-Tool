package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * 技能模块重构全量服务端 GameTest 套件：
 * 覆盖 110 点预算、5 大星阶、双轨解锁、3 套预设、星座共鸣、全局开关休眠、历史缺陷回归与新技能执行。
 */
@PrefixGameTestTemplate(false)
public final class SkillProgressionGameTests {

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void pointBudgetExactlyCoversEverySkill(GameTestHelper helper) {
        int budget = PlayerSkillData.totalPointBudget();
        int cost = ModSkills.totalCost();
        helper.assertTrue(ModSkills.all().size() == 32, "Catalog must contain 32 skills");
        helper.assertTrue(budget == 110 && budget == cost,
                "Max-level budget must equal cost of all 32 skills (110): " + budget + " vs " + cost);
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void starRankAndSlotExpansion(GameTestHelper helper) {
        PlayerSkillData data = new PlayerSkillData();
        helper.assertTrue(data.starRank() == StarRank.STARDUST, "Lv.1 must start as STARDUST");
        helper.assertTrue(data.activeSlotCount() == 2 && data.passiveSlotCount() == 2,
                "Lv.1 slots must be 2 active / 2 passive");

        // Lv.1 -> Lv.10（不跳过 5 级与 10 级里程碑）
        int needed = 0;
        for (int i = 0; i < 9; i++) {
            needed += 200 + i * 50;
        }
        int gained = data.addExperience(needed);
        helper.assertTrue(gained == 9 && data.level() == 10, "Must reach Lv.10, got " + data.level());
        helper.assertTrue(data.starRank() == StarRank.ORBITER, "Lv.10 must promote to ORBITER");
        helper.assertTrue(data.activeSlotCount() == 3 && data.passiveSlotCount() == 4,
                "Lv.10 slots must expand to 3/4");

        int expectedPts = PlayerSkillData.INITIAL_POINTS + 9
                + PlayerSkillData.milestoneBonus(5) + PlayerSkillData.milestoneBonus(10);
        helper.assertTrue(data.skillPoints() == expectedPts,
                "Milestone bonus mismatch: expected " + expectedPts + ", got " + data.skillPoints());

        data.addExperience(5_000_000);
        helper.assertTrue(data.level() == PlayerSkillData.MAX_LEVEL && data.starRank() == StarRank.SOVEREIGN,
                "Must reach Lv.50 SOVEREIGN");
        helper.assertTrue(data.activeSlotCount() == 6 && data.passiveSlotCount() == 8,
                "Max rank must grant 6 active / 8 passive slots");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void passiveAndActiveDualTrackUnlockAndCascadeReset(GameTestHelper helper) {
        PlayerSkillData data = new PlayerSkillData();
        data.addExperience(30_000);

        // 被动技能不再默认全部解锁
        helper.assertTrue(!data.isUnlocked(ModSkills.FOCUS_MINING), "Passive must start locked until trial met");
        helper.assertTrue(!data.learnOrUpgrade(ModSkills.FOCUS_MINING), "Locked passive cannot be learned");
        helper.assertTrue(!data.isUnlocked(ModSkills.ITEM_MAGNET), "Child passive must start locked");

        // 即使完成子节点试炼，未点亮前置节点依然不能学习
        data.incrementStat("blocks_mined", 100);
        helper.assertTrue(data.isUnlocked(ModSkills.FOCUS_MINING), "Root passive trial must unlock at 20+ blocks");
        helper.assertTrue(data.isUnlocked(ModSkills.ITEM_MAGNET), "Child trial must unlock at 80+ blocks");
        helper.assertTrue(!data.learnOrUpgrade(ModSkills.ITEM_MAGNET), "Cannot learn child before prerequisite learned");

        // 点亮根节点后再学习子节点，然后测试级联洗点
        int ptsBefore = data.skillPoints();
        helper.assertTrue(data.learnOrUpgrade(ModSkills.FOCUS_MINING), "Root should now be learnable");
        helper.assertTrue(data.learnOrUpgrade(ModSkills.ITEM_MAGNET), "Child should now be learnable");
        helper.assertTrue(data.learnOrUpgrade(ModSkills.ITEM_MAGNET), "Child can upgrade to Lv.2");

        var resetRes = data.resetSkillCascade(ModSkills.FOCUS_MINING);
        helper.assertTrue(resetRes.refundedPoints() == 3, "Cascade reset must refund all 3 points");
        helper.assertTrue(!data.learned(ModSkills.FOCUS_MINING) && !data.learned(ModSkills.ITEM_MAGNET),
                "Cascade reset must clear both parent and dependent child");
        helper.assertTrue(data.skillPoints() == ptsBefore, "Point balance must return to exact prior value");
        helper.assertTrue(data.isUnlocked(ModSkills.FOCUS_MINING), "Trial unlocks must persist across respec");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void presetsAndConstellationResonance(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel world = server.overworld();
        ServerPlayer player = mockPlayer(server, world, "preset-resonance");
        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();

        // 预设 0 装配 4 个巧匠（触发 4 件共鸣）
        data.switchPreset(0);
        helper.assertTrue(data.addToLoadout(true, ModSkills.ORE_SIGHT), "Equip active 1");
        helper.assertTrue(data.addToLoadout(true, ModSkills.VEIN_RESONANCE), "Equip active 2");
        helper.assertTrue(data.addToLoadout(false, ModSkills.FOCUS_MINING), "Equip passive 1");
        helper.assertTrue(data.addToLoadout(false, ModSkills.ITEM_MAGNET), "Equip passive 2");
        helper.assertTrue(data.resonanceTier(SkillConstellation.QIAOJIANG) == 2,
                "4 Qiaojiang skills at Lv.50 must activate tier 2 resonance");

        SkillManager.applyPassiveEffects(player);
        double reachWithQiaojiang = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);

        // 切换到预设 1（空星轨）
        data.switchPreset(1);
        helper.assertTrue(data.resonanceTier(SkillConstellation.QIAOJIANG) == 0, "Preset 1 should have no resonance");
        SkillManager.applyPassiveEffects(player);
        double reachPlain = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        helper.assertTrue(reachWithQiaojiang > reachPlain + 0.5,
                "Qiaojiang 2-piece resonance must grant +1.0 reach");

        // 切回预设 0 完整保留
        data.switchPreset(0);
        helper.assertTrue(data.activeLoadout().size() == 2 && data.passiveLoadout().size() == 2,
                "Preset 0 loadout must persist across switches");
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void worldToggleDormantBehavior(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel world = server.overworld();
        ServerPlayer player = mockPlayer(server, world, "toggle-test");
        try {
            SkillManager.setSystemEnabled(server, true);
            PlayerSkillData data = SkillManager.data(player);
            data.maximizeAllSkills();
            data.addToLoadout(false, ModSkills.DIAMOND_BULWARK);
            SkillManager.applyPassiveEffects(player);
            helper.assertTrue(player.getArmorValue() == 20, "Enabled state must apply 20 armor");

            long xpBeforeOff = data.lifetimeExperience();
            SkillManager.setSystemEnabled(server, false);
            SkillManager.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(!SkillManager.isEnabled(server), "System must report disabled");
            helper.assertTrue(player.getArmorValue() == 0, "Disabling must clear passive armor immediately");

            // 关闭期间挖矿不增经验、不推进试炼
            BlockPos orePos = helper.absolutePos(new BlockPos(2, 3, 2));
            world.setBlockAndUpdate(orePos, Blocks.DIAMOND_ORE.defaultBlockState());
            SkillManager.onBlockBreak(new BlockEvent.BreakEvent(world, orePos, Blocks.DIAMOND_ORE.defaultBlockState(), player));
            helper.assertTrue(data.lifetimeExperience() == xpBeforeOff, "Disabled state must pause XP gain");

            // 重新开启后无缝恢复
            SkillManager.setSystemEnabled(server, true);
            SkillManager.applyPassiveEffects(player);
            helper.assertTrue(player.getArmorValue() == 20, "Re-enabling must seamlessly restore passive armor");
            helper.assertTrue(data.learnedLevel(ModSkills.DIAMOND_BULWARK) == 3, "Saved skill data must remain intact");
        } finally {
            SkillManager.setSystemEnabled(server, true);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 120)
    public static void historicalBugRegressionTests(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockPlayer(server, world, "bug-regression");
        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();

        // 1. 重复冻结不得破坏原 NoAI=false 状态
        var zombie = EntityType.ZOMBIE.create(world);
        helper.assertTrue(zombie != null, "Zombie must spawn");
        zombie.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        zombie.setNoAi(false);
        world.addFreshEntity(zombie);
        SkillActiveExecutor.freeze(player, zombie, world, 10);
        SkillActiveExecutor.freeze(player, zombie, world, 20);
        helper.assertTrue(zombie.isNoAi(), "Zombie must be NoAI while frozen");

        // 2. 友好目标保护：村民、动物、自己的狼不受影响
        Villager villager = EntityType.VILLAGER.create(world);
        Cow cow = EntityType.COW.create(world);
        Wolf wolf = EntityType.WOLF.create(world);
        helper.assertTrue(villager != null && cow != null && wolf != null, "Friendly entities must exist");
        wolf.tame(player);
        helper.assertTrue(!SkillManager.canAffect(player, villager), "Villager must be protected");
        helper.assertTrue(!SkillManager.canAffect(player, cow), "Cow must be protected");
        helper.assertTrue(!SkillManager.canAffect(player, wolf), "Own tamed wolf must be protected");
        helper.assertTrue(SkillManager.canAffect(player, zombie), "Hostile zombie must be affectable");

        // 3. 冻结玩家不得施放主动技能
        data.addToLoadout(true, ModSkills.BERSERK);
        SkillActiveExecutor.freeze(player, player, world, 20);
        SkillManager.handleCast(player, false);
        helper.assertTrue(player.getEffect(MobEffects.DAMAGE_BOOST) == null,
                "Frozen player must not be able to cast Berserk");
        SkillActiveExecutor.clearTransient(player);

        // 4. 洗点不得误删外部（药水/信标）急迫效果
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 1));
        SkillManager.handleAction(player, org.gwfx.universaltool.network.SkillActionPayload.Action.RESET,
                "", java.util.List.of());
        var haste = player.getEffect(MobEffects.DIG_SPEED);
        helper.assertTrue(haste != null && haste.getAmplifier() == 1 && haste.getDuration() > 400,
                "Reset must preserve external Haste II");

        // 5. 放置方块记录不会因时间流逝而重新给经验
        data.maximizeAllSkills();
        long xpBefore = data.lifetimeExperience();
        BlockPos placedPos = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockState stone = Blocks.STONE.defaultBlockState();
        world.setBlockAndUpdate(placedPos, stone);
        SkillManager.onBlockPlaced(new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(ResourceKey.create(Registries.DIMENSION, Level.OVERWORLD.location()), world, placedPos),
                Blocks.AIR.defaultBlockState(), player));
        SkillManager.onBlockBreak(new BlockEvent.BreakEvent(world, placedPos, stone, player));
        helper.assertTrue(data.lifetimeExperience() == xpBefore,
                "Re-mining player-placed block must grant zero XP");

        villager.discard();
        cow.discard();
        wolf.discard();

        // 推进 tick 验证到期恢复 NoAI
        helper.runAfterDelay(25, () -> {
            SkillActiveExecutor.tickServer(server);
            helper.assertTrue(!zombie.isNoAi(), "Repeated freeze must restore original NoAI=false upon expiry");
            zombie.discard();
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void newSkillsAndCombatExecution(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockPlayer(world.getServer(), world, "new-skills-test");
        player.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();

        // 1. 炼金脉冲：烧炼铁矿石并修复工具
        ItemStack ironTool = new ItemStack(Items.IRON_PICKAXE);
        ironTool.setDamageValue(50);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ironTool);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.RAW_IRON, 4));
        data.addToLoadout(true, ModSkills.ARCANE_TRANSMUTE);
        SkillManager.castSelected(player);
        helper.assertTrue(player.getOffhandItem().isEmpty(), "Offhand raw iron must be consumed by Arcane Transmute");
        helper.assertTrue(ironTool.getDamageValue() < 50, "Arcane Transmute must repair held tool durability");

        // 2. 反击护体：弹开弹射物并反伤近战
        data.addToLoadout(true, ModSkills.COUNTER_GUARD);
        SkillManager.castSelected(player);
        Arrow arrow = new Arrow(world, player.getX(), player.getY() + 1, player.getZ() + 3, new ItemStack(Items.ARROW), null);
        arrow.setDeltaMovement(0, 0, -1.0);
        world.addFreshEntity(arrow);
        SkillManager.deflectProjectile(player, arrow);
        helper.assertTrue(arrow.getDeltaMovement().z > 0 && arrow.getOwner() == player,
                "Counter Guard must deflect arrow back");

        // 3. 裂空星斩：直接对前方敌怪造成伤害
        var zombie = EntityType.ZOMBIE.create(world);
        helper.assertTrue(zombie != null, "Zombie must exist");
        var look = player.getLookAngle().normalize();
        zombie.setPos(player.getX() + look.x * 4.0, player.getY(), player.getZ() + look.z * 4.0);
        world.addFreshEntity(zombie);
        float zHpBefore = zombie.getHealth();
        data.addToLoadout(true, ModSkills.ASTRAL_CLEAVE);
        SkillManager.castSelected(player);
        helper.assertTrue(zombie.getHealth() < zHpBefore, "Astral Cleave must damage enemy in front of player");
        zombie.discard();

        // 4. 离线冷却恢复
        long now = System.currentTimeMillis();
        data.startCooldown(ModSkills.BERSERK, 200);
        CompoundTag saved = data.serialize();
        long stored = saved.getCompound("Cooldowns").getLong(ModSkills.BERSERK);
        helper.assertTrue(Math.abs(stored - (now + 10_000L)) < 5_000L, "Cooldown must use wall-clock millis");

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void hyperConduitAndAuditedSkillEffects(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockPlayer(world.getServer(), world, "conduit-audit-test");
        player.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getCenter());
        PlayerSkillData data = SkillManager.data(player);

        // 1. 校验全部 32 技能的 Lv.1 / Lv.2 / Lv.3 数值格式化文案互不相同
        for (SkillDefinition def : ModSkills.all()) {
            String s1 = SkillStatFormatter.format(def.id(), 1);
            String s2 = SkillStatFormatter.format(def.id(), 2);
            String s3 = SkillStatFormatter.format(def.id(), 3);
            helper.assertTrue(!s1.equals(s2) && !s2.equals(s3) && !s1.equals(s3),
                    "Skill levels 1/2/3 must differ for " + def.id() + ": " + s1 + " | " + s2 + " | " + s3);
        }

        // 2. 超维直达 Lv.1：无克莱因瓶时，挖矿掉落直接入背包
        data.addExperience(100_000);
        data.unlockAllSkills(null);
        for (String req : java.util.List.of(ModSkills.FOCUS_MINING, ModSkills.ITEM_MAGNET,
                ModSkills.EXPLORERS_EYE, ModSkills.HYPER_CONDUIT)) {
            helper.assertTrue(data.learnOrUpgrade(req), "Should learn " + req);
        }
        data.addToLoadout(false, ModSkills.HYPER_CONDUIT);

        BlockPos dropPos = helper.absolutePos(new BlockPos(2, 3, 2));
        var dropEntity1 = new net.minecraft.world.entity.item.ItemEntity(world,
                dropPos.getX(), dropPos.getY(), dropPos.getZ(), new ItemStack(Items.RAW_GOLD, 3));
        var drops1 = new java.util.ArrayList<>(java.util.List.of(dropEntity1));
        var blockEvent1 = new net.neoforged.neoforge.event.level.BlockDropsEvent(
                world, dropPos, Blocks.GOLD_ORE.defaultBlockState(), null, drops1, player, ItemStack.EMPTY);
        SkillManager.onBlockDrops(blockEvent1);
        helper.assertTrue(drops1.isEmpty(), "Lv.1 Hyper Conduit must absorb block drop entity");
        helper.assertTrue(player.getInventory().countItem(Items.RAW_GOLD) == 3,
                "Lv.1 Hyper Conduit without bottle must put 3 raw gold into inventory");

        // 3. 超维直达 Lv.2 + Lv.3：携克莱因瓶直接入超维仓储，且 Lv.3 吸收 +25% 经验
        helper.assertTrue(data.learnOrUpgrade(ModSkills.HYPER_CONDUIT), "Upgrade Hyper Conduit to Lv.2");
        helper.assertTrue(data.learnOrUpgrade(ModSkills.HYPER_CONDUIT), "Upgrade Hyper Conduit to Lv.3");
        player.getInventory().add(new ItemStack(org.gwfx.universaltool.init.ModItems.KLEIN_BOTTLE.get()));

        var dropEntity2 = new net.minecraft.world.entity.item.ItemEntity(world,
                dropPos.getX(), dropPos.getY(), dropPos.getZ(), new ItemStack(Items.DIAMOND, 2));
        var drops2 = new java.util.ArrayList<>(java.util.List.of(dropEntity2));
        var blockEvent2 = new net.neoforged.neoforge.event.level.BlockDropsEvent(
                world, dropPos, Blocks.DIAMOND_ORE.defaultBlockState(), null, drops2, player, ItemStack.EMPTY);
        blockEvent2.setDroppedExperience(8);
        int xpBefore = player.totalExperience;
        SkillManager.onBlockDrops(blockEvent2);
        helper.assertTrue(drops2.isEmpty(), "Lv.3 Hyper Conduit must route diamond drop");
        helper.assertTrue(blockEvent2.getDroppedExperience() == 0, "Lv.3 Hyper Conduit must absorb XP directly");
        helper.assertTrue(player.totalExperience >= xpBefore + 10, "Lv.3 must grant +25% XP (8 -> 10)");

        var warehouse = org.gwfx.universaltool.klein.KleinStorageSavedData.get(world.getServer())
                .warehouse(player.getUUID());
        helper.assertTrue(warehouse.containsType(new ItemStack(Items.DIAMOND)),
                "Lv.2+ Hyper Conduit with Klein Bottle must insert diamonds into warehouse");

        // 4. 反击护体 Lv.1 实际反弹 125% 伤害（使用基础护甲为 0 的骷髅验证精确数值）
        data.learnOrUpgrade(ModSkills.IRON_PHYSIQUE);
        data.learnOrUpgrade(ModSkills.COUNTER_GUARD);
        data.addToLoadout(true, ModSkills.COUNTER_GUARD);
        SkillManager.castSelected(player);
        var skeleton = EntityType.SKELETON.create(world);
        helper.assertTrue(skeleton != null, "Skeleton must exist");
        skeleton.setPos(player.getX() + 1, player.getY(), player.getZ());
        world.addFreshEntity(skeleton);
        float skelBefore = skeleton.getHealth();
        var dmg = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().mobAttack(skeleton), 4.0F));
        SkillManager.onIncomingDamage(dmg);
        helper.assertTrue(Math.abs((skelBefore - skeleton.getHealth()) - 5.0F) < 0.01F,
                "Lv.1 Counter Guard must reflect 125% of 4.0 = 5.0 damage, actual=" + (skelBefore - skeleton.getHealth()));
        skeleton.discard();

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

    private SkillProgressionGameTests() {
    }
}
