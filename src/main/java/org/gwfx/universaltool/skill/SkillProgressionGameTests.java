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
        helper.assertTrue(data.activeSlotCount() == 6 && data.passiveSlotCount() == 8
                        && data.starRank().maxResonanceTier() == 3,
                "Max rank must grant 6 active / 8 passive slots and tier-3 (6-piece) resonance");
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

        // 2. 星锻时运 Lv.3：保护精准采集、耐久减耗，且挖掘经验 +30%
        data.addExperience(100_000);
        data.unlockAllSkills(null);
        for (String req : java.util.List.of(ModSkills.FOCUS_MINING, ModSkills.ITEM_MAGNET,
                ModSkills.EXPLORERS_EYE, ModSkills.HYPER_CONDUIT)) {
            helper.assertTrue(data.learnOrUpgrade(req), "Should learn " + req);
        }
        helper.assertTrue(data.learnOrUpgrade(ModSkills.HYPER_CONDUIT), "Upgrade Starforged Fortune to Lv.2");
        helper.assertTrue(data.learnOrUpgrade(ModSkills.HYPER_CONDUIT), "Upgrade Starforged Fortune to Lv.3");
        data.addToLoadout(false, ModSkills.HYPER_CONDUIT);

        BlockPos dropPos = helper.absolutePos(new BlockPos(2, 3, 2));
        var dropEntity2 = new net.minecraft.world.entity.item.ItemEntity(world,
                dropPos.getX(), dropPos.getY(), dropPos.getZ(), new ItemStack(Items.DIAMOND, 1));
        var drops2 = new java.util.ArrayList<>(java.util.List.of(dropEntity2));
        var blockEvent2 = new net.neoforged.neoforge.event.level.BlockDropsEvent(
                world, dropPos, Blocks.DIAMOND_ORE.defaultBlockState(), null, drops2, player, ItemStack.EMPTY);
        blockEvent2.setDroppedExperience(10);
        SkillManager.onBlockDrops(blockEvent2);
        helper.assertTrue(blockEvent2.getDroppedExperience() >= 13,
                "Lv.3 Starforged Fortune must boost dropped mining XP by +30% (10 -> >=13), got "
                        + blockEvent2.getDroppedExperience());

        // 3. 反击护体 Lv.1 实际反弹 150% 伤害（4.0 -> 6.0）
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
        helper.assertTrue(Math.abs((skelBefore - skeleton.getHealth()) - 6.0F) < 0.01F,
                "Lv.1 Counter Guard must reflect 150% of 4.0 = 6.0 damage, actual=" + (skelBefore - skeleton.getHealth()));
        skeleton.discard();

        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void bugAuditRegressionTests(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockPlayer(server, world, "audit-bug-regression");
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 2, 2));
        world.setBlockAndUpdate(basePos.below(), Blocks.STONE.defaultBlockState());
        player.setPos(basePos.getCenter());

        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();

        // 1. 铁血体魄 Lv.3 卸下后生命值正确钳制到当前最大生命值
        data.addToLoadout(false, ModSkills.IRON_PHYSIQUE);
        SkillManager.applyPassiveEffects(player);
        player.setHealth(player.getMaxHealth());
        helper.assertTrue(player.getMaxHealth() >= 39.5F, "Lv.3 Iron Physique must raise max health to 40");
        data.removeFromLoadout(false, 0);
        SkillManager.applyPassiveEffects(player);
        helper.assertTrue(player.getMaxHealth() <= 20.5F && player.getHealth() <= player.getMaxHealth() + 0.01F,
                "Unsetting Iron Physique must clamp player health down to maxHealth");

        // 2. 反击护体完全取消事件、不自伤消耗、且不误触发绝境求生
        data.addToLoadout(false, ModSkills.LAST_STAND);
        data.addToLoadout(true, ModSkills.COUNTER_GUARD);
        SkillManager.applyPassiveEffects(player);
        player.setHealth(4.0F);
        player.setAbsorptionAmount(0.0F);
        SkillManager.castSelected(player);

        var selfHit = new LivingIncomingDamageEvent(player,
                new DamageContainer(player.damageSources().playerAttack(player), 4.0F));
        SkillManager.onIncomingDamage(selfHit);

        var zombie = EntityType.ZOMBIE.create(world);
        helper.assertTrue(zombie != null, "Zombie must exist");
        zombie.setPos(player.getX() + 1.2, player.getY(), player.getZ());
        world.addFreshEntity(zombie);
        float zBefore = zombie.getHealth();
        var mobHit = new LivingIncomingDamageEvent(player,
                new DamageContainer(player.damageSources().mobAttack(zombie), 4.0F));
        SkillManager.onIncomingDamage(mobHit);
        helper.assertTrue(mobHit.isCanceled(), "Counter Guard must cancel incoming damage event");
        helper.assertTrue(player.getAbsorptionAmount() == 0.0F,
                "Counter Guard negated hit must not trigger Last Stand shield");
        helper.assertTrue(zombie.getHealth() < zBefore, "Counter Guard must reflect damage to hostile attacker");
        zombie.discard();
        player.setHealth(20.0F);
        data.removeFromLoadout(false, 0);
        data.clearCombat();

        // 3. 虚空折跃 Lv.3 锁敌回溯窗内卸下主动时必须补结算冷却
        var blinkTarget = EntityType.ZOMBIE.create(world);
        helper.assertTrue(blinkTarget != null, "Blink target must exist");
        var lookBlink = player.getLookAngle().normalize();
        blinkTarget.setPos(player.getX() + lookBlink.x * 12.0, player.getY(), player.getZ() + lookBlink.z * 12.0);
        world.addFreshEntity(blinkTarget);
        data.addToLoadout(true, ModSkills.VOID_BLINK);
        data.setSelectedActive(data.activeLoadout().indexOf(ModSkills.VOID_BLINK));
        SkillManager.castSelected(player);
        blinkTarget.discard();
        data.clearCombat();
        helper.assertTrue(!data.onCooldown(ModSkills.VOID_BLINK), "Blink Lv.3 enters recall window without immediate CD");
        int blinkIdx = data.activeLoadout().indexOf(ModSkills.VOID_BLINK);
        SkillManager.handleAction(player, org.gwfx.universaltool.network.SkillActionPayload.Action.UNSET_ACTIVE,
                String.valueOf(blinkIdx), java.util.List.of());
        helper.assertTrue(data.onCooldown(ModSkills.VOID_BLINK),
                "Unsetting Void Blink during recall window must apply cooldown");

        // 4. 裂空星斩 Lv.3 首段击杀当 tick 立即返还 40% 冷却
        var fragile = EntityType.ZOMBIE.create(world);
        helper.assertTrue(fragile != null, "Fragile zombie must exist");
        var look = player.getLookAngle().normalize();
        fragile.setPos(player.getX() + look.x * 3.0, player.getY(), player.getZ() + look.z * 3.0);
        fragile.setHealth(1.0F);
        world.addFreshEntity(fragile);
        data.addToLoadout(true, ModSkills.ASTRAL_CLEAVE);
        SkillManager.castSelected(player);
        helper.assertTrue(!fragile.isAlive(), "Astral Cleave must kill fragile zombie");
        helper.assertTrue(data.cooldownRemainingTicks(ModSkills.ASTRAL_CLEAVE) <= 65,
                "Astral Cleave Lv.3 kill must refund 40% CD immediately on cast");
        fragile.discard();

        // 5. 弓箭（投射物伤害）击杀普通敌怪计入 ranged_or_skill_kills
        int rangedBefore = data.stat("ranged_or_skill_kills");
        var target = EntityType.ZOMBIE.create(world);
        helper.assertTrue(target != null, "Target zombie must exist");
        target.setPos(player.getX() + 2.0, player.getY(), player.getZ());
        world.addFreshEntity(target);
        Arrow arrow = new Arrow(world, player.getX(), player.getY() + 1.0, player.getZ(),
                new ItemStack(Items.ARROW), null);
        arrow.setOwner(player);
        target.hurt(world.damageSources().arrow(arrow, player), 100.0F);
        helper.assertTrue(data.stat("ranged_or_skill_kills") == rangedBefore + 1,
                "Projectile kill by player bow/arrow must advance ranged_or_skill_kills trial");
        arrow.discard();

        // 6. 天罚雷域对着正上方天空释放时落点吸附地面/附近，不得悬在高空
        player.setXRot(-90.0F);
        data.addToLoadout(true, ModSkills.THUNDER_JUDGMENT);
        SkillManager.castSelected(player);
        var zoneCenter = SkillNewActivesHelper.latestThunderZoneCenterForTest(player.getUUID());
        helper.assertTrue(zoneCenter != null && zoneCenter.y < player.getY() + 4.0D,
                "Thunder Judgment cast into sky must snap to ground/near player, actual y="
                        + (zoneCenter != null ? zoneCenter.y : "null"));

        // 7. 巧匠 4 星共鸣不得复制玩家放置的钻石块，且零硬度方块不得白嫖修复工具耐久
        data.switchPreset(2);
        data.addToLoadout(true, ModSkills.ORE_SIGHT);
        data.addToLoadout(true, ModSkills.VEIN_RESONANCE);
        data.addToLoadout(false, ModSkills.FOCUS_MINING);
        data.addToLoadout(false, ModSkills.ITEM_MAGNET);
        data.addToLoadout(false, ModSkills.EXPLORERS_EYE);
        data.addToLoadout(false, ModSkills.HYPER_CONDUIT);
        BlockPos dupePos = helper.absolutePos(new BlockPos(4, 3, 4));
        world.setBlockAndUpdate(dupePos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        ItemStack damagedPick = new ItemStack(Items.DIAMOND_PICKAXE);
        damagedPick.setDamageValue(100);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, damagedPick);
        for (int attempt = 0; attempt < 20; attempt++) {
            SkillManager.onBlockPlaced(new BlockEvent.EntityPlaceEvent(
                    BlockSnapshot.create(ResourceKey.create(Registries.DIMENSION, Level.OVERWORLD.location()), world, dupePos),
                    Blocks.AIR.defaultBlockState(), player));
            SkillManager.onBlockBreak(new BlockEvent.BreakEvent(world, dupePos, Blocks.DIAMOND_BLOCK.defaultBlockState(), player));
            var dropList = new java.util.ArrayList<>(java.util.List.of(
                    new net.minecraft.world.entity.item.ItemEntity(world, dupePos.getX(), dupePos.getY(), dupePos.getZ(),
                            new ItemStack(Items.DIAMOND_BLOCK, 1))));
            var dropEv = new net.neoforged.neoforge.event.level.BlockDropsEvent(
                    world, dupePos, Blocks.DIAMOND_BLOCK.defaultBlockState(), null, dropList, player, damagedPick);
            SkillManager.onBlockDrops(dropEv);
            helper.assertTrue(dropEv.getDrops().size() == 1 && dropEv.getDrops().get(0).getItem().getCount() == 1,
                    "Qiaojiang 4-star must NEVER duplicate diamond blocks or player-placed blocks");
        }
        // 零硬度方块（火把）破坏 20 次不得恢复受损工具耐久（测试前先重置耐久值，隔离上方钻石块测试的合法返还）
        damagedPick.setDamageValue(100);
        world.setBlockAndUpdate(dupePos, Blocks.TORCH.defaultBlockState());
        for (int attempt = 0; attempt < 20; attempt++) {
            SkillManager.onBlockBreak(new BlockEvent.BreakEvent(world, dupePos, Blocks.TORCH.defaultBlockState(), player));
            var torchDrops = new java.util.ArrayList<net.minecraft.world.entity.item.ItemEntity>();
            var torchEv = new net.neoforged.neoforge.event.level.BlockDropsEvent(
                    world, dupePos, Blocks.TORCH.defaultBlockState(), null, torchDrops, player, damagedPick);
            SkillManager.onBlockDrops(torchEv);
        }
        helper.assertTrue(damagedPick.getDamageValue() == 100,
                "Breaking zero-hardness blocks must not repair damaged tools, got damage=" + damagedPick.getDamageValue());

        // 8. 狂战爆发 Lv.3 吸血按实际造成伤害的 10% 回复（非目标血量）
        data.switchPreset(1);
        data.addToLoadout(true, ModSkills.BERSERK);
        data.setSelectedActive(data.activeLoadout().indexOf(ModSkills.BERSERK));
        SkillManager.castSelected(player);
        SkillActiveExecutor.onBerserkKill(player);
        player.setHealth(10.0F);
        var dummy = EntityType.ZOMBIE.create(world);
        helper.assertTrue(dummy != null, "Dummy zombie must exist");
        dummy.setPos(player.getX() + 2.0, player.getY(), player.getZ());
        world.addFreshEntity(dummy);
        SkillActiveExecutor.onBerserkHit(player, dummy, 40.0F);
        helper.assertTrue(Math.abs(player.getHealth() - 14.0F) < 0.05F,
                "Berserk Lv.3 bonus lifesteal must heal 10% of dealt damage (40 -> +4 HP), got " + player.getHealth());
        dummy.discard();

        SkillManager.clearRuntimeEffectsForPlayer(player);
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty", timeoutTicks = 100)
    public static void v1110ReworkAndResonanceTests(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel world = helper.getLevel();
        ServerPlayer player = mockPlayer(server, world, "v1110-rework-test");
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 2, 2));
        world.setBlockAndUpdate(basePos.below(), Blocks.STONE.defaultBlockState());
        player.setPos(basePos.getCenter());

        PlayerSkillData data = SkillManager.data(player);
        data.maximizeAllSkills();

        // 1. 玄武 6 星终极共鸣：合计 90% 击退抗性
        data.addToLoadout(true, ModSkills.COUNTER_GUARD);
        data.addToLoadout(true, ModSkills.STARLIGHT_SANCTUARY);
        data.addToLoadout(false, ModSkills.IRON_PHYSIQUE);
        data.addToLoadout(false, ModSkills.SURVIVAL_FLOW);
        data.addToLoadout(false, ModSkills.LAST_STAND);
        data.addToLoadout(false, ModSkills.DIAMOND_BULWARK);
        helper.assertTrue(data.resonanceTier(SkillConstellation.XUANWU) == 3,
                "6 Xuanwu skills at Lv.50 must activate 6-piece resonance (tier 3)");
        SkillManager.applyPassiveEffects(player);
        double kbResist = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(kbResist >= 0.899D && kbResist <= 0.91D,
                "Xuanwu 6-piece resonance must grant 90% knockback resistance, actual=" + kbResist);

        // 2. 「点石成矿」将周围自然石头点化为真实矿石
        BlockPos stonePos = helper.absolutePos(new BlockPos(3, 2, 2));
        world.setBlockAndUpdate(stonePos, Blocks.STONE.defaultBlockState());
        BlockPos scanCenter = player.blockPosition();
        int oresBefore = countNearbyOres(world, scanCenter, 12, 7);
        data.addToLoadout(true, ModSkills.VEIN_RESONANCE);
        data.setSelectedActive(data.activeLoadout().indexOf(ModSkills.VEIN_RESONANCE));
        SkillManager.castSelected(player);
        int oresAfter = countNearbyOres(world, scanCenter, 12, 7);
        helper.assertTrue(oresAfter > oresBefore,
                "Lithomancy must transmute nearby natural stone into real ore blocks, before="
                        + oresBefore + ", after=" + oresAfter);

        // 3. 新版「虚空折跃」：锁敌瞬移至背后 + 10 秒内二段返回原点
        data.switchPreset(1);
        player.setXRot(0.0F);
        var target = EntityType.ZOMBIE.create(world);
        helper.assertTrue(target != null, "Target zombie must exist");
        var dir = player.getLookAngle().normalize();
        target.setPos(player.getX() + dir.x * 16.0, player.getY(), player.getZ() + dir.z * 16.0);
        world.addFreshEntity(target);
        net.minecraft.world.phys.Vec3 originPos = player.position();

        data.addToLoadout(true, ModSkills.VOID_BLINK);
        data.setSelectedActive(data.activeLoadout().indexOf(ModSkills.VOID_BLINK));
        SkillManager.castSelected(player);
        helper.assertTrue(player.position().distanceTo(target.position()) < 5.0D,
                "Void Blink must warp player behind locked distant enemy");
        helper.assertTrue(!data.onCooldown(ModSkills.VOID_BLINK),
                "Void Blink must remain ready to recall for 10 seconds");

        SkillManager.castSelected(player);
        helper.assertTrue(player.position().distanceTo(originPos) < 0.5D,
                "Recasting Void Blink within 10s must return player to origin");
        helper.assertTrue(data.onCooldown(ModSkills.VOID_BLINK),
                "Recalling must put Void Blink on cooldown");

        // 4. 百分比伤害验证：面对 1000 血强敌时威压领域造成超过 40 点百分比伤害
        var bossMob = EntityType.ZOMBIE.create(world);
        helper.assertTrue(bossMob != null, "Boss-HP zombie must exist");
        bossMob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        bossMob.setHealth(1000.0F);
        bossMob.setPos(player.getX() + 2.0, player.getY(), player.getZ());
        world.addFreshEntity(bossMob);
        data.addToLoadout(true, ModSkills.OPPRESSIVE_FIELD);
        data.setSelectedActive(data.activeLoadout().indexOf(ModSkills.OPPRESSIVE_FIELD));
        SkillManager.castSelected(player);
        helper.assertTrue(bossMob.getHealth() <= 920.0F,
                "Oppressive Field percentage damage must scale against 1000-HP mob, remaining=" + bossMob.getHealth());

        target.discard();
        bossMob.discard();
        SkillManager.clearRuntimeEffectsForPlayer(player);
        helper.succeed();
    }

    @GameTest(templateNamespace = "universal_tool", template = "empty")
    public static void oreSightFindsColoredOresAndLevelThreeSpecials(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = mockPlayer(level.getServer(), level, "ore-sight-test");
        BlockPos origin = helper.absolutePos(new BlockPos(0, 14, 0));
        player.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, 0, 0);
        BlockPos ore = origin.offset(17, -3, 0);
        BlockPos spawner = origin.offset(1, 0, 0);
        level.setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
        level.setBlockAndUpdate(spawner, Blocks.SPAWNER.defaultBlockState());
        var lowerLevel = new SkillOreSight.Scan(player, 20, 10, false);
        var maxLevel = new SkillOreSight.Scan(player, 24, 11, true);
        while (!lowerLevel.scanNextSection()) {}
        while (!maxLevel.scanNextSection()) {}
        helper.assertTrue(lowerLevel.ores.containsKey(ore) && lowerLevel.specials.isEmpty(),
                "Lower levels must find ores across chunk boundaries and omit special blocks");
        helper.assertTrue(maxLevel.ores.getOrDefault(ore, 0) == SkillActiveExecutor.oreColor(Blocks.DIAMOND_ORE.defaultBlockState())
                        && maxLevel.specials.contains(spawner),
                "Level three must keep ore colors and special markers");
        level.setBlockAndUpdate(ore, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(spawner, Blocks.AIR.defaultBlockState());
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

    private static int countNearbyOres(ServerLevel world, BlockPos center, int radius, int vertical) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -vertical; dy <= vertical; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radius * radius) continue;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (world.isLoaded(pos) && SkillCombatTracker.isOre(world.getBlockState(pos))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private SkillProgressionGameTests() {
    }
}
