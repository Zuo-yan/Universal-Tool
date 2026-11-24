package org.gwfx.universaltool.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.gwfx.universaltool.UniversalToolMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 被动技能运行时处理器（涵盖全部 18 项被动、属性修饰符、伤害减免/钳制、连击、吸血、磁吸与模组联动）。
 */
final class SkillPassiveHandler {
    private static final ResourceLocation IRON_PHYSIQUE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "iron_physique");
    private static final ResourceLocation SWIFT_PACE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "swift_pace");
    private static final ResourceLocation SWIFT_STEP_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "swift_pace_step");
    private static final ResourceLocation BULWARK_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "diamond_bulwark");
    private static final ResourceLocation TOOL_RESONANCE_ATK_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "tool_resonance_atk");
    private static final ResourceLocation TOOL_RESONANCE_SPD_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "tool_resonance_spd");
    private static final ResourceLocation SPATIAL_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "spatial_attunement_speed");
    private static final ResourceLocation SPATIAL_REACH_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "spatial_attunement_reach");
    private static final ResourceLocation VOID_FORTUNE_LUCK_ID =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "void_fortune_luck");

    private static final Map<UUID, Momentum> MOMENTUM = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> TOOL_COMBO_HITS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_STAND_SHIELD_CD = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PHOENIX_EMBER_CD = new ConcurrentHashMap<>();

    private record Momentum(int stacks, long lastHitGameTime) {}

    private SkillPassiveHandler() {}

    static void clearAll() {
        MOMENTUM.clear();
        TOOL_COMBO_HITS.clear();
        LAST_STAND_SHIELD_CD.clear();
        PHOENIX_EMBER_CD.clear();
    }

    static void clearMomentum(UUID playerId) {
        MOMENTUM.remove(playerId);
        TOOL_COMBO_HITS.remove(playerId);
    }

    static int equippedLevel(ServerPlayer player, String id) {
        if (!SkillManager.isEnabled(player.serverLevel())) {
            return 0;
        }
        PlayerSkillData data = SkillManager.data(player);
        if (!data.passiveLoadout().contains(id)) {
            return 0;
        }
        return data.learnedLevel(id);
    }

    static double bulwarkArmor(int level) {
        return level <= 0 ? 0 : level == 1 ? 6 : level == 2 ? 12 : 20;
    }

    static double bulwarkToughness(int level) {
        return level <= 1 ? 0 : level == 2 ? 4 : 8;
    }

    static float damageClampRatio(int level) {
        return level <= 0 ? 1 : level == 1 ? 0.5F : level == 2 ? 0.4F : 0.3F;
    }

    static void applyPassiveEffects(ServerPlayer player) {
        if (!SkillManager.isEnabled(player.serverLevel())) {
            clearAllPassives(player);
            return;
        }

        updateBulwark(player, equippedLevel(player, ModSkills.DIAMOND_BULWARK));

        int mining = equippedLevel(player, ModSkills.FOCUS_MINING);
        if (mining > 0) {
            // 只在未持有更高级外部急迫时刷新短时长急迫，洗点或卸下时不强删外部药水/信标急迫（修复历史缺陷 #9）
            MobEffectInstance currentHaste = player.getEffect(MobEffects.DIG_SPEED);
            if (currentHaste == null || currentHaste.getAmplifier() <= mining - 1) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 45, mining - 1, true, false, true));
            }
        }

        int healthLvl = equippedLevel(player, ModSkills.IRON_PHYSIQUE);
        var healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(IRON_PHYSIQUE_MODIFIER_ID);
            if (healthLvl > 0) {
                double bonusHp = healthLvl == 1 ? 6.0 : healthLvl == 2 ? 12.0 : 20.0;
                healthAttr.addTransientModifier(new AttributeModifier(
                        IRON_PHYSIQUE_MODIFIER_ID, bonusHp, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        int paceLvl = equippedLevel(player, ModSkills.SWIFT_PACE);
        var speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(SWIFT_PACE_MODIFIER_ID);
            if (paceLvl > 0) {
                double speedBonus = paceLvl == 1 ? 0.10 : paceLvl == 2 ? 0.18 : 0.25;
                speedAttr.addTransientModifier(new AttributeModifier(
                        SWIFT_PACE_MODIFIER_ID, speedBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
        var stepAttr = player.getAttribute(Attributes.STEP_HEIGHT);
        if (stepAttr != null) {
            stepAttr.removeModifier(SWIFT_STEP_MODIFIER_ID);
            if (paceLvl >= 3) {
                stepAttr.addTransientModifier(new AttributeModifier(
                        SWIFT_STEP_MODIFIER_ID, 0.45, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        // 兵刃共鸣（万能工具联动）
        updateToolResonance(player);

        // 时空锚感（口袋界域联动）
        updateSpatialAttunement(player);

        // 虚空眷顾（原版幸运加成）
        int fortuneLvl = equippedLevel(player, ModSkills.VOID_FORTUNE);
        var luckAttr = player.getAttribute(Attributes.LUCK);
        if (luckAttr != null) {
            luckAttr.removeModifier(VOID_FORTUNE_LUCK_ID);
            if (fortuneLvl > 0) {
                luckAttr.addTransientModifier(new AttributeModifier(
                        VOID_FORTUNE_LUCK_ID, fortuneLvl, AttributeModifier.Operation.ADD_VALUE));
            }
        }

        SkillResonanceHandler.applyResonanceAttributes(player);
    }

    private static void updateToolResonance(ServerPlayer player) {
        int resLvl = equippedLevel(player, ModSkills.TOOL_RESONANCE);
        int tier = SkillModSynergyHandler.universalToolTierRank(player.getMainHandItem());
        var atk = player.getAttribute(Attributes.ATTACK_DAMAGE);
        var spd = player.getAttribute(Attributes.ATTACK_SPEED);
        if (atk != null) atk.removeModifier(TOOL_RESONANCE_ATK_ID);
        if (spd != null) spd.removeModifier(TOOL_RESONANCE_SPD_ID);

        if (resLvl > 0 && tier > 0) {
            if (atk != null) {
                double dmgBonus = tier * (0.4 + 0.3 * resLvl);
                atk.addTransientModifier(new AttributeModifier(
                        TOOL_RESONANCE_ATK_ID, dmgBonus, AttributeModifier.Operation.ADD_VALUE));
            }
            if (spd != null) {
                double spdBonus = 0.06 * resLvl + 0.02 * tier;
                spd.addTransientModifier(new AttributeModifier(
                        TOOL_RESONANCE_SPD_ID, spdBonus, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    private static void updateSpatialAttunement(ServerPlayer player) {
        int lv = equippedLevel(player, ModSkills.SPATIAL_ATTUNEMENT);
        boolean inPocket = SkillModSynergyHandler.isInPocketOrRealmDimension(player);
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        var reach = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (speed != null) speed.removeModifier(SPATIAL_SPEED_ID);
        if (reach != null) reach.removeModifier(SPATIAL_REACH_ID);

        if (lv > 0 && inPocket) {
            if (speed != null) {
                speed.addTransientModifier(new AttributeModifier(
                        SPATIAL_SPEED_ID, 0.08 * lv, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            if (reach != null) {
                reach.addTransientModifier(new AttributeModifier(
                        SPATIAL_REACH_ID, (double) lv, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    static void clearAllPassives(ServerPlayer player) {
        updateBulwark(player, 0);
        MOMENTUM.remove(player.getUUID());
        TOOL_COMBO_HITS.remove(player.getUUID());
        removeAttr(player, Attributes.MAX_HEALTH, IRON_PHYSIQUE_MODIFIER_ID);
        removeAttr(player, Attributes.MOVEMENT_SPEED, SWIFT_PACE_MODIFIER_ID);
        removeAttr(player, Attributes.STEP_HEIGHT, SWIFT_STEP_MODIFIER_ID);
        removeAttr(player, Attributes.ATTACK_DAMAGE, TOOL_RESONANCE_ATK_ID);
        removeAttr(player, Attributes.ATTACK_SPEED, TOOL_RESONANCE_SPD_ID);
        removeAttr(player, Attributes.MOVEMENT_SPEED, SPATIAL_SPEED_ID);
        removeAttr(player, Attributes.BLOCK_INTERACTION_RANGE, SPATIAL_REACH_ID);
        removeAttr(player, Attributes.LUCK, VOID_FORTUNE_LUCK_ID);
        SkillResonanceHandler.clearAllResonances(player);
    }

    private static void removeAttr(ServerPlayer player, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id) {
        var inst = player.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    private static void updateBulwark(ServerPlayer player, int rank) {
        var armor = player.getAttribute(Attributes.ARMOR);
        var toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (armor != null) {
            armor.removeModifier(BULWARK_ID);
            if (rank > 0) {
                armor.addTransientModifier(new AttributeModifier(
                        BULWARK_ID, bulwarkArmor(rank), AttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (toughness != null) {
            toughness.removeModifier(BULWARK_ID);
            if (rank > 0) {
                toughness.addTransientModifier(new AttributeModifier(
                        BULWARK_ID, bulwarkToughness(rank), AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    static void clampFinalDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!SkillManager.isEnabled(player.serverLevel())) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)
                || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        int rank = equippedLevel(player, ModSkills.DAMAGE_CLAMP);
        if (rank > 0) {
            float cap = player.getMaxHealth() * damageClampRatio(rank);
            if (event.getNewDamage() > cap) {
                event.setNewDamage(cap);
                if (rank >= 3) {
                    player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 0, true, false, true));
                }
            }
        }
    }

    static void applyIncomingVictimPassives(ServerPlayer victim, LivingIncomingDamageEvent event) {
        applyLastStand(victim, event);
        applyLightFeather(victim, event);
        applyBulwarkThorns(victim, event);
        SkillResonanceHandler.onIncomingVictim(victim, event);
    }

    static void applyIncomingAttackerPassives(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        applyMomentum(attacker, event);
        applyArmorPiercing(attacker, event);
        SkillResonanceHandler.onIncomingAttacker(attacker, event);
    }

    static void onLivingHeal(ServerPlayer player, LivingHealEvent event) {
        int physique = equippedLevel(player, ModSkills.IRON_PHYSIQUE);
        if (physique >= 3) {
            event.setAmount(event.getAmount() * 1.20F);
        }
        SkillResonanceHandler.onLivingHeal(player, event);
    }

    /** 凤凰余烬兜底保命（优先让位于 PhoenixRebirthHandler，仅在未被培养仓拦截时生效）。 */
    static void tryPhoenixEmberRevive(ServerPlayer player, LivingDeathEvent event) {
        if (event.isCanceled() || !SkillManager.isEnabled(player.serverLevel())) return;
        if (event.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || event.getSource().is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        int lv = equippedLevel(player, ModSkills.PHOENIX_EMBER);
        if (lv <= 0) return;
        long now = System.currentTimeMillis();
        long cdMillis = switch (lv) {
            case 1 -> 360_000L;
            case 2 -> 300_000L;
            default -> 240_000L;
        };
        Long last = PHOENIX_EMBER_CD.get(player.getUUID());
        if (last != null && now - last < cdMillis) {
            return;
        }
        PHOENIX_EMBER_CD.put(player.getUUID(), now);
        event.setCanceled(true);
        float targetHp = lv >= 3 ? Math.max(4.0F, player.getMaxHealth() * 0.30F) : 1.0F;
        player.setHealth(Math.min(player.getMaxHealth(), targetHp));
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), 4.0F + 2.0F * lv));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 3, false, true, true));

        var world = player.serverLevel();
        for (LivingEntity enemy : world.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(5.0), e -> SkillActiveExecutor.canAffect(player, e))) {
            Vec3 push = enemy.position().subtract(player.position()).normalize().scale(1.3);
            enemy.setDeltaMovement(push.x, 0.45, push.z);
            enemy.hurtMarked = true;
            enemy.igniteForSeconds(3);
        }
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1.0, player.getZ(),
                45, 0.5, 0.8, 0.5, 0.25);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9F, 1.1F);
    }

    static void trackMomentum(ServerPlayer player, LivingEntity target, long gameTime) {
        if (target == player) return;
        int momLv = equippedLevel(player, ModSkills.BATTLE_MOMENTUM);
        if (momLv > 0) {
            Momentum previous = MOMENTUM.get(player.getUUID());
            int window = SkillResonanceHandler.momentumWindowTicks(player);
            int stacks = previous != null && gameTime - previous.lastHitGameTime() <= window
                    ? Math.min(SkillManager.MOMENTUM_MAX_STACKS, previous.stacks() + 1)
                    : 1;
            MOMENTUM.put(player.getUUID(), new Momentum(stacks, gameTime));

            // Lv.3 觉醒：叠满 5 层时触发星辉顺劈波
            if (momLv >= 3 && stacks >= SkillManager.MOMENTUM_MAX_STACKS) {
                player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK,
                        target.getX(), target.getY() + 0.8, target.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
                for (LivingEntity extra : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(2.5),
                        e -> e != target && SkillActiveExecutor.canAffect(player, e))) {
                    extra.hurt(player.serverLevel().damageSources().indirectMagic(player, player), 4.0F);
                }
            }
        }

        // 兵刃共鸣 Lv.3 觉醒：每第 4 次手持万能工具攻击触发额外星辉斩击
        int toolResLv = equippedLevel(player, ModSkills.TOOL_RESONANCE);
        int tier = SkillModSynergyHandler.universalToolTierRank(player.getMainHandItem());
        if (toolResLv >= 3 && tier > 0) {
            int hits = TOOL_COMBO_HITS.getOrDefault(player.getUUID(), 0) + 1;
            if (hits >= 4) {
                TOOL_COMBO_HITS.put(player.getUUID(), 0);
                target.hurt(player.serverLevel().damageSources().indirectMagic(player, player), 4.0F + tier);
                player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                        target.getX(), target.getY() + 1.0, target.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            } else {
                TOOL_COMBO_HITS.put(player.getUUID(), hits);
            }
        }
    }

    static void applyBloodthirst(ServerPlayer player, LivingEntity target, float damageDealt) {
        int blood = equippedLevel(player, ModSkills.BLOODTHIRST);
        if (blood <= 0 || damageDealt <= 0) return;
        float heal = Math.max(0.2F, damageDealt * (blood * 0.05F));
        float missing = Math.max(0.0F, player.getMaxHealth() - player.getHealth());
        player.heal(heal);
        if (blood >= 3 && heal > missing) {
            float overflow = heal - missing;
            float nextShield = Math.min(6.0F, player.getAbsorptionAmount() + overflow * 0.5F);
            player.setAbsorptionAmount(nextShield);
        }
    }

    static void tickPassives(ServerPlayer player, long gameTime) {
        if (!SkillManager.isEnabled(player.serverLevel())) {
            return;
        }
        if (gameTime % 2 == 0) {
            pullNearbyItems(player, equippedLevel(player, ModSkills.ITEM_MAGNET));
        }
        if (player.tickCount % 20 == 0) {
            applyPassiveEffects(player);
            tickToolRepairAndExplorer(player, gameTime);

            // 生存回流：脱战状态下每秒恢复 0.5 / 1.0 / 1.5 生命值
            int flow = equippedLevel(player, ModSkills.SURVIVAL_FLOW);
            PlayerSkillData data = SkillManager.data(player);
            if (flow > 0 && !data.inCombat(gameTime)) {
                if (player.getHealth() < player.getMaxHealth()) {
                    player.heal(0.5F * flow);
                }
                if (flow >= 3) {
                    if (player.tickCount % 60 == 0 && player.getFoodData().needsFood()) {
                        player.getFoodData().eat(1, 0.2F);
                    }
                    if (player.tickCount % 180 == 0) {
                        for (MobEffectInstance inst : List.copyOf(player.getActiveEffects())) {
                            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                                player.removeEffect(inst.getEffect());
                                break;
                            }
                        }
                    }
                }
            }

            // 迅捷步伐：疾跑或跳跃时减免饥饿消耗
            int paceLvl = equippedLevel(player, ModSkills.SWIFT_PACE);
            if (paceLvl > 0 && (player.isSprinting() || !player.onGround())) {
                float reduction = paceLvl == 1 ? 0.25F : paceLvl == 2 ? 0.45F : 0.65F;
                float ex = player.getFoodData().getExhaustionLevel();
                if (ex > 0.0F) {
                    player.getFoodData().setExhaustion(ex * (1.0F - reduction));
                }
            }
        }
        SkillResonanceHandler.tickResonances(player, gameTime);
    }

    private static void tickToolRepairAndExplorer(ServerPlayer player, long gameTime) {
        // 兵刃共鸣：缓慢自愈手中的万能工具（Lv.1=3s, Lv.2=2s, Lv.3=1s 修复1点）
        int toolRes = equippedLevel(player, ModSkills.TOOL_RESONANCE);
        if (toolRes > 0) {
            int interval = toolRes == 1 ? 60 : toolRes == 2 ? 40 : 20;
            if (player.tickCount % interval == 0) {
                ItemStack main = player.getMainHandItem();
                if (SkillModSynergyHandler.universalToolTierRank(main) > 0 && main.isDamaged()) {
                    main.setDamageValue(Math.max(0, main.getDamageValue() - 1));
                }
            }
        }

        // 秘境直觉：地下低光环境自动给予柔和夜视
        int expEye = equippedLevel(player, ModSkills.EXPLORERS_EYE);
        if (expEye > 0) {
            BlockPos pos = player.blockPosition();
            var level = player.serverLevel();
            int sky = level.getBrightness(LightLayer.SKY, pos);
            int block = level.getBrightness(LightLayer.BLOCK, pos);
            if (!level.canSeeSky(pos) && sky <= 2 && block <= 6) {
                MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
                if (nv == null || nv.getDuration() < 240) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
                }
            }
            if (expEye >= 3 && player.isUnderWater()) {
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, false));
            }
        }
    }

    private static void applyLastStand(ServerPlayer victim, LivingIncomingDamageEvent event) {
        int level = equippedLevel(victim, ModSkills.LAST_STAND);
        if (level <= 0) return;
        float max = victim.getMaxHealth();
        if (max <= 0 || victim.getHealth() / max >= 0.35F) return;
        float reduction = Math.min(0.6F, 0.15F * level);
        event.setAmount(event.getAmount() * (1.0F - reduction));

        if (level >= 3) {
            long now = victim.serverLevel().getGameTime();
            Long cd = LAST_STAND_SHIELD_CD.get(victim.getUUID());
            if (cd == null || now >= cd) {
                LAST_STAND_SHIELD_CD.put(victim.getUUID(), now + 600);
                victim.setAbsorptionAmount(Math.max(victim.getAbsorptionAmount(), 4.0F));
            }
        }
    }

    private static void applyLightFeather(ServerPlayer player, LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.FALL)) return;
        int feather = equippedLevel(player, ModSkills.LIGHT_FEATHER);
        if (feather <= 0) return;
        if (feather >= 3) {
            event.setCanceled(true);
            player.fallDistance = 0.0F;
            if (event.getAmount() >= 4.0F) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, true, false, true));
            }
        } else {
            float reduction = feather == 1 ? 0.40F : 0.75F;
            event.setAmount(Math.max(0.0F, event.getAmount() * (1.0F - reduction)));
        }
    }

    private static void applyBulwarkThorns(ServerPlayer victim, LivingIncomingDamageEvent event) {
        int lv = equippedLevel(victim, ModSkills.DIAMOND_BULWARK);
        if (lv < 3) return;
        if (event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity living && SkillActiveExecutor.canAffect(victim, living)) {
            float reflect = Math.max(1.0F, event.getAmount() * 0.15F);
            living.hurt(victim.damageSources().thorns(victim), reflect);
        }
    }

    private static void applyMomentum(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        int level = equippedLevel(attacker, ModSkills.BATTLE_MOMENTUM);
        if (level <= 0) return;
        Momentum momentum = MOMENTUM.get(attacker.getUUID());
        if (momentum == null) return;
        long gameTime = attacker.serverLevel().getGameTime();
        if (gameTime - momentum.lastHitGameTime() > SkillResonanceHandler.momentumWindowTicks(attacker)) {
            return;
        }
        event.setAmount(event.getAmount() * (1.0F + 0.04F * momentum.stacks() * level));
    }

    private static void applyArmorPiercing(ServerPlayer attacker, LivingIncomingDamageEvent event) {
        int pierce = equippedLevel(attacker, ModSkills.ARMOR_PIERCING);
        if (pierce <= 0) return;
        float mult = 1.0F + pierce * 0.15F;
        if (pierce >= 3 && event.getEntity() instanceof LivingEntity target) {
            float armorBonus = target.getArmorValue() * 0.15F;
            event.setAmount(event.getAmount() * mult + armorBonus);
        } else {
            event.setAmount(event.getAmount() * mult);
        }
    }

    private static void pullNearbyItems(ServerPlayer player, int magnetLevel) {
        if (magnetLevel <= 0) return;
        double radius = magnetLevel >= 3 ? 12.0 : (5.0 + 2.0 * magnetLevel);
        AABB box = player.getBoundingBox().inflate(radius);
        Vec3 target = player.position().add(0, 0.5, 0);
        for (ItemEntity item : player.serverLevel().getEntitiesOfClass(
                ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay())) {
            Vec3 delta = target.subtract(item.position());
            if (delta.length() > 0.4) {
                item.setDeltaMovement(item.getDeltaMovement().scale(0.5).add(delta.normalize().scale(0.35)));
                item.hasImpulse = true;
            }
        }
        for (ExperienceOrb orb : player.serverLevel().getEntitiesOfClass(
                ExperienceOrb.class, box, e -> e.isAlive())) {
            Vec3 delta = target.subtract(orb.position());
            if (delta.length() > 0.4) {
                double pullSpeed = magnetLevel >= 3 ? 0.55 : 0.35;
                orb.setDeltaMovement(orb.getDeltaMovement().scale(0.5).add(delta.normalize().scale(pullSpeed)));
                orb.hasImpulse = true;
            }
        }
    }
}
